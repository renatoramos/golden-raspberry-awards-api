package com.outsera.awards;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * Contrato REST (Richardson nível 2) e resposta para o CSV oficial, comparada ao oráculo Python.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AwardIntervalsEndpointTest {

    private static final String PATH = "/producers/award-intervals";

    @Autowired
    MockMvcTester mvc;

    @Test
    void returnsTheOfficialIntervalsExactlyAsTheOracleComputed() throws Exception {
        var expected = new ClassPathResource("scenarios/official.intervals.json");

        assertThat(mvc.get().uri(PATH))
                .hasStatusOk()
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .bodyJson().isStrictlyEqualTo(expected);
    }

    @Test
    void officialIntervalsAreJoelSilverAndMatthewVaughn() {
        assertThat(mvc.get().uri(PATH)).bodyJson()
                .extractingPath("$.min[0].producer").isEqualTo("Joel Silver");
        assertThat(mvc.get().uri(PATH)).bodyJson()
                .extractingPath("$.max[0].producer").isEqualTo("Matthew Vaughn");
    }

    @Test
    void exposesOnlyTheDocumentedFields() {
        assertThat(mvc.get().uri(PATH)).bodyJson()
                .hasPathSatisfying("$.min[0]", path -> path.assertThat().isEqualTo(java.util.Map.of(
                        "producer", "Joel Silver", "interval", 1, "previousWin", 1990, "followingWin", 1991)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE"})
    void onlyGetIsAllowed(String method) {
        assertThat(mvc.method(HttpMethod.valueOf(method)).uri(PATH)).hasStatus(405);
    }

    @Test
    void unknownResourceIs404() {
        assertThat(mvc.get().uri("/producers/unknown")).hasStatus(404);
    }

    @Test
    void openApiDocumentsTheEndpointAndItsFields() {
        var docs = assertThat(mvc.get().uri("/v3/api-docs")).hasStatusOk().bodyJson();

        docs.extractingPath("$.paths['" + PATH + "'].get.responses['200'].content['application/json']").isNotNull();
        docs.extractingPath("$.components.schemas.ProducerInterval.properties").asMap()
                .containsOnlyKeys("producer", "interval", "previousWin", "followingWin");
    }

    @Test
    void swaggerUiIsServed() {
        assertThat(mvc.get().uri("/swagger-ui/index.html")).hasStatusOk();
    }
}
