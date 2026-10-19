package com.outsera.awards;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.server.context.WebServerInitializedEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.web.client.RestClient;

/**
 * O aquecimento do cache termina ANTES de o servidor web abrir a porta: no instante do
 * {@link WebServerInitializedEvent} (porta já aberta) a base já foi lida uma vez, e depois da carga do CSV
 * (o resultado aquecido não pode ser o de uma base vazia).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AwardIntervalsWarmUpBeforePortTest {

    static final AtomicInteger QUERIES = new AtomicInteger();

    @TestConfiguration
    static class Config {
        final AtomicInteger queriesWhenPortOpened = new AtomicInteger(-1);

        @Bean
        static BeanPostProcessor countingProducerWins() {
            return new CountingProducerWins(QUERIES);
        }

        @EventListener
        void onPortOpened(WebServerInitializedEvent event) {
            queriesWhenPortOpened.set(QUERIES.get());
        }
    }

    @Autowired
    Config probe;

    @LocalServerPort
    int port;

    @Test
    void cacheIsAlreadyWarmWhenThePortOpens() {
        assertThat(probe.queriesWhenPortOpened).hasValue(1);
    }

    @Test
    void warmedResultIsTheOneComputedAfterTheCsvLoad() {
        String body = RestClient.create("http://localhost:" + port).get().uri("/producers/award-intervals")
                .retrieve().body(String.class);

        assertThat(body).contains("Joel Silver", "Matthew Vaughn");
        assertThat(QUERIES).hasValue(1);
    }
}
