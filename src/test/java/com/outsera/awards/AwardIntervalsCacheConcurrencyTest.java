package com.outsera.awards;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceInitializationAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

/**
 * Caminho preguiçoso do cache: sem o aquecimento (o {@code AwardIntervalsWarmUp} fica fora deste contexto), várias
 * threads pedem o resultado ao mesmo tempo e a base só pode ser lida uma vez (dupla verificação sincronizada).
 */
class AwardIntervalsCacheConcurrencyTest {

    @Configuration
    @ComponentScan(basePackages = {"com.outsera.movies", "com.outsera.awards"},
            excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = ".*(Test|WarmUp).*"))
    static class Modules {
    }

    @Test
    void concurrentFirstCallsComputeOnlyOnce() {
        var queries = new AtomicInteger();

        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        DataSourceAutoConfiguration.class, DataSourceInitializationAutoConfiguration.class,
                        DataSourceTransactionManagerAutoConfiguration.class, TransactionAutoConfiguration.class,
                        JdbcTemplateAutoConfiguration.class, JdbcClientAutoConfiguration.class))
                .withUserConfiguration(Modules.class)
                .withBean("counting", BeanPostProcessor.class, () -> new CountingProducerWins(queries))
                .withPropertyValues("spring.sql.init.mode=always", "app.movies.csv-location=classpath:Movielist.csv")
                .run(context -> {
                    var cache = context.getBean(AwardIntervalsCache.class);
                    assertThat(queries).hasValue(0);

                    int threads = 32;
                    var start = new CountDownLatch(1);
                    List<CompletableFuture<AwardIntervals>> calls = new ArrayList<>();
                    for (int i = 0; i < threads; i++) {
                        calls.add(CompletableFuture.supplyAsync(() -> {
                            await(start);
                            return cache.get();
                        }));
                    }
                    start.countDown();
                    List<AwardIntervals> results = calls.stream().map(CompletableFuture::join).toList();

                    assertThat(queries).hasValue(1);
                    assertThat(results).allSatisfy(result -> assertThat(result).isSameAs(results.get(0)));
                    assertThat(results.get(0).min()).extracting(ProducerInterval::producer).containsExactly("Joel Silver");
                });
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
