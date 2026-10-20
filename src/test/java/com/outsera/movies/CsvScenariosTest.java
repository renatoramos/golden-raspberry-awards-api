package com.outsera.movies;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceInitializationAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

/**
 * Um contexto Spring real (H2 em memória, carga real) por CSV de fixture. Os resultados esperados vêm do oráculo.
 */
@ExtendWith(OutputCaptureExtension.class)
class CsvScenariosTest {

    @Configuration
    @ComponentScan(basePackageClasses = ProducerWins.class,
            excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = ".*Test.*"))
    static class ModuleUnderTest {
    }

    private static ApplicationContextRunner runnerFor(String classpathCsv) {
        return runner().withPropertyValues("app.movies.csv-location=classpath:" + classpathCsv);
    }

    private static ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        DataSourceAutoConfiguration.class,
                        DataSourceInitializationAutoConfiguration.class,
                        DataSourceTransactionManagerAutoConfiguration.class,
                        TransactionAutoConfiguration.class,
                        JdbcTemplateAutoConfiguration.class,
                        JdbcClientAutoConfiguration.class))
                .withUserConfiguration(ModuleUnderTest.class)
                .withPropertyValues("spring.sql.init.mode=always");
    }

    static Stream<String> validScenarios() throws IOException {
        return Stream.of(new PathMatchingResourcePatternResolver().getResources("classpath:scenarios/valid/*.csv"))
                .map(r -> r.getFilename().replace(".csv", ""))
                .sorted();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("validScenarios")
    void validCsvProducesTheWinsExpectedByTheOracle(String name) {
        List<ProducerWin> expected = ScenarioFiles.expectedWins("scenarios/valid/" + name + ".wins.tsv");

        runnerFor("scenarios/valid/" + name + ".csv").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(ProducerWins.class).distinctWins()).containsExactlyElementsOf(expected);
        });
    }

    static Stream<Arguments> invalidScenarios() {
        return Stream.of(
                Arguments.of("missing_year", new String[] {"colunas obrigatórias ausentes", "year"}),
                Arguments.of("missing_title", new String[] {"colunas obrigatórias ausentes", "title"}),
                Arguments.of("missing_producers", new String[] {"colunas obrigatórias ausentes", "producers"}),
                Arguments.of("duplicate_header", new String[] {"coluna duplicada", "year"}),
                Arguments.of("bad_year", new String[] {"linha 3", "year inválido", "abc"}),
                Arguments.of("empty_producers", new String[] {"linha 3", "producers vazio"}),
                Arguments.of("short_row", new String[] {"linha 3", "2 colunas", "esperado 5"}),
                Arguments.of("long_row", new String[] {"linha 3", "6 colunas", "esperado 5"}),
                Arguments.of("unterminated_quote", new String[] {"linha 3", "aspas"}),
                Arguments.of("junk_after_quote", new String[] {"linha 2", "aspas"}),
                Arguments.of("empty_file", new String[] {"cabeçalho"}));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidScenarios")
    void invalidCsvAbortsStartupWithLineNumberAndContent(String name, String[] messageParts) {
        runnerFor("scenarios/invalid/" + name + ".csv").run(context -> {
            assertThat(context).hasFailed();
            assertThat(mostSpecificCause(context.getStartupFailure()))
                    .isInstanceOf(InvalidCsvException.class).hasMessageContainingAll(messageParts);
        });
    }

    private static Throwable mostSpecificCause(Throwable failure) {
        return NestedExceptionUtils.getMostSpecificCause(failure);
    }

    @Test
    void invalidRowMessageShowsTheOffendingContent() {
        runnerFor("scenarios/invalid/bad_year.csv").run(context ->
                assertThat(mostSpecificCause(context.getStartupFailure())).hasMessageContaining("[abc, B, S, P, yes]"));
    }

    @Test
    void logsHowManyMoviesWereLoaded(CapturedOutput output) {
        runnerFor("Movielist.csv").run(context -> assertThat(context).hasNotFailed());

        assertThat(output).contains("206 filmes carregados");
    }

    @Test
    void movielistCsvLocationIsAnAliasForTheCsvPath() {
        runner().withPropertyValues("movielist.csv.location=classpath:scenarios/awards/ties_min_and_max.csv")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var jdbc = context.getBean(JdbcClient.class);
                    assertThat(jdbc.sql("select count(*) from movie").query(Long.class).single()).isEqualTo(8);
                });
    }

    @Test
    void aliasTakesPrecedenceWhenBothPropertiesAreSet() {
        runner().withPropertyValues(
                        "app.movies.csv-location=classpath:scenarios/valid/no_winners.csv",
                        "movielist.csv.location=classpath:scenarios/awards/ties_min_and_max.csv")
                .run(context -> assertThat(context.getBean(JdbcClient.class).sql("select count(*) from movie")
                        .query(Long.class).single()).isEqualTo(8));
    }

    @Test
    void moviesGetIdsFromCsvOrder() {
        runnerFor("scenarios/valid/adjacent_three_wins.csv").run(context -> {
            var rows = context.getBean(JdbcClient.class).sql("select id, title from movie order by id")
                    .query((rs, n) -> rs.getLong("id") + ":" + rs.getString("title")).list();
            assertThat(rows).containsExactly("1:A", "2:B", "3:C");
        });
    }

    @Test
    void failureWhileInsertingRollsBackEverything() {
        // O 3º filme tem um produtor maior que a coluna: os filmes entram no banco e a inserção de produtores falha.
        // A transação tem que desfazer os filmes já inseridos (tudo ou nada).
        DataSource dataSource = new EmbeddedDatabaseBuilder().generateUniqueName(true)
                .setType(EmbeddedDatabaseType.H2).build();

        runnerFor("scenarios/db_failure/producer_too_long.csv")
                .withBean(DataSource.class, () -> dataSource)
                .run(context -> {
                    assertThat(context).hasFailed();
                    var jdbc = JdbcClient.create(dataSource);
                    assertThat(jdbc.sql("select count(*) from movie").query(Long.class).single()).isZero();
                    assertThat(jdbc.sql("select count(*) from movie_producer").query(Long.class).single()).isZero();
                });
    }
}
