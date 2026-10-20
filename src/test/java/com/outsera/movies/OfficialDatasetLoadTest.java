package com.outsera.movies;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.test.ApplicationModuleTest;

/**
 * Sobe o módulo {@code movies} com o CSV padrão da aplicação (src/main/resources/Movielist.csv) e confere, pela API
 * pública, as vitórias esperadas calculadas pelo oráculo Python (tools/oracle).
 */
@ApplicationModuleTest
class OfficialDatasetLoadTest {

    @Autowired
    ProducerWins producerWins;

    @Autowired
    JdbcClient jdbc;

    @Test
    void exposesDistinctWinsMatchingTheOracle() {
        List<ProducerWin> expected = ScenarioFiles.expectedWins("scenarios/official.wins.tsv");

        assertThat(producerWins.distinctWins()).hasSize(98).containsExactlyElementsOf(expected);
    }

    @Test
    void loadsEveryRowOfTheOfficialCsv() {
        assertThat(jdbc.sql("select count(*) from movie").query(Long.class).single()).isEqualTo(206);
        assertThat(jdbc.sql("select count(*) from movie where winner").query(Long.class).single()).isEqualTo(42);
    }
}
