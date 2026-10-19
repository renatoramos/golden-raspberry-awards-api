package com.outsera.awards;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import com.outsera.MoviesApplication;

import tools.jackson.databind.json.JsonMapper;

/**
 * Sobe a aplicação inteira (Tomcat real, H2 real, CSV da fixture) e chama o endpoint por HTTP. O JSON esperado de
 * cada cenário vem do oráculo Python (tools/oracle), não do código sob teste.
 */
class AwardIntervalsScenariosTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    static Stream<String> scenarios() throws IOException {
        return Stream.of(new PathMatchingResourcePatternResolver().getResources("classpath:scenarios/awards/*.csv"))
                .map(r -> r.getFilename().replace(".csv", ""))
                .sorted();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarios")
    void responseMatchesTheOracle(String scenario) throws Exception {
        String expected = new ClassPathResource("scenarios/awards/" + scenario + ".intervals.json")
                .getContentAsString(StandardCharsets.UTF_8);

        try (ConfigurableApplicationContext app = new SpringApplicationBuilder(MoviesApplication.class)
                .web(WebApplicationType.SERVLET)
                .properties("server.port=0", "spring.main.banner-mode=off")
                .run("--app.movies.csv-location=classpath:scenarios/awards/" + scenario + ".csv")) {
            String port = app.getEnvironment().getProperty("local.server.port");
            HttpResponse<String> response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/producers/award-intervals")).GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(JSON.readTree(response.body())).isEqualTo(JSON.readTree(expected));
        }
    }
}
