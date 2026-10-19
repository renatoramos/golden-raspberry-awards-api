package com.outsera;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.server.context.WebServerInitializedEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;

import com.outsera.movies.ProducerWins;

/**
 * Prova que a carga do CSV termina ANTES de o Tomcat abrir a porta: no instante em que o evento
 * {@link WebServerInitializedEvent} é publicado (porta já aberta), as vitórias já estão no banco.
 * Com um ApplicationRunner (que roda depois do servidor) a contagem observada seria 0.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DataLoadedBeforeWebServerTest {

    @TestConfiguration
    static class Probe {
        final AtomicInteger winsWhenPortOpened = new AtomicInteger(-1);
        final AtomicInteger port = new AtomicInteger(-1);

        @Bean
        Probe probe() {
            return this;
        }

        @Autowired
        ProducerWins producerWins;

        @EventListener
        void onWebServerReady(WebServerInitializedEvent event) {
            port.set(event.getWebServer().getPort());
            winsWhenPortOpened.set(producerWins.distinctWins().size());
        }
    }

    @Autowired
    Probe probe;

    @Test
    void winsAreAlreadyQueryableWhenThePortOpens() {
        assertThat(probe.port.get()).isPositive();
        assertThat(probe.winsWhenPortOpened.get()).isEqualTo(98);
    }
}
