package com.outsera.awards;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.outsera.movies.ProducerWins;

/**
 * O resultado é calculado uma única vez, no aquecimento (antes de a porta abrir), e reaproveitado. Um
 * contador sobre o {@link ProducerWins} real (mesma consulta ao H2) conta quantas vezes a base é lida.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AwardIntervalsCacheTest {

    static final AtomicInteger QUERIES = new AtomicInteger();

    @TestConfiguration
    static class CountingConfig {
        @Bean
        static BeanPostProcessor countingProducerWins() {
            return new CountingProducerWins(QUERIES);
        }
    }

    @Autowired
    MockMvcTester mvc;

    @Test
    void resultIsComputedOnceDuringWarmUpBeforeAnyRequest() {
        assertThat(QUERIES).hasValue(1);
    }

    @Test
    void repeatedRequestsDoNotReadTheDatabaseAgain() {
        for (int i = 0; i < 5; i++) {
            assertThat(mvc.get().uri("/producers/award-intervals")).hasStatusOk();
        }

        assertThat(QUERIES).hasValue(1);
    }
}
