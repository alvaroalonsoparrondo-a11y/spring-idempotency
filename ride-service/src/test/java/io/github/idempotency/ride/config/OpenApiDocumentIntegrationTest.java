package io.github.idempotency.ride.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.idempotency.ride.support.RequiresDocker;
import io.github.idempotency.ride.support.TestInfrastructure;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The generated API document, read the way a client generator reads it.
 */
@RequiresDocker
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class OpenApiDocumentIntegrationTest {

    private final TestRestTemplate rest;
    private final ObjectMapper objectMapper;
    private JsonNode document;

    OpenApiDocumentIntegrationTest(TestRestTemplate rest, ObjectMapper objectMapper) {
        this.rest = rest;
        this.objectMapper = objectMapper;
    }

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        TestInfrastructure.register(registry);
    }

    @BeforeEach
    void fetchDocument() throws Exception {
        ResponseEntity<String> response = rest.getForEntity("/v3/api-docs", String.class);
        assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
        document = objectMapper.readTree(response.getBody());
    }

    @Test
    @DisplayName("IT document should ask for the Idempotency-Key header and for nothing the server fills in")
    void document_whenRead_shouldAskForIdempotencyKeyHeaderAndNothingServerFillsIn() {
        // when
        JsonNode parameters = createRide().get("parameters");

        // then
        assertThat(parameters).hasSize(1);
        assertThat(parameters.get(0).get("name").asText()).isEqualTo("Idempotency-Key");
        assertThat(parameters.get(0).get("in").asText()).isEqualTo("header");
        assertThat(parameters.get(0).get("required").asBoolean()).isTrue();
        assertThat(parameters.get(0).get("schema").get("format").asText()).isEqualTo("uuid");
    }

    @Test
    @DisplayName("IT document should list 201 with the ride and every 4xx with the problem schema, and no 5xx")
    void document_whenRead_shouldList201WithRideAndEvery4xxWithProblemAndNo5xx() {
        // when
        JsonNode responses = createRide().get("responses");

        // then
        List<String> codes = new ArrayList<>();
        responses.fieldNames().forEachRemaining(codes::add);
        assertThat(codes).containsExactlyInAnyOrder("201", "400", "401", "402", "409", "422");
        assertThat(responses.at("/201/content/*~1*/schema/$ref").asText())
                .isEqualTo("#/components/schemas/RideResponse");
        for (String code : List.of("400", "401", "402", "409", "422")) {
            JsonNode content = responses.get(code).get("content");
            assertThat(content.has("application/problem+json")).as(code).isTrue();
            assertThat(content.size()).as(code).isEqualTo(1);
            assertThat(content.at("/application~1problem+json/schema/$ref").asText()).as(code)
                    .isEqualTo("#/components/schemas/Problem");
        }
    }

    @Test
    @DisplayName("IT document should state which request fields are required and the coordinate bounds")
    void document_whenRead_shouldStateRequiredRequestFieldsAndCoordinateBounds() {
        // when
        JsonNode schemas = document.get("components").get("schemas");

        // then
        assertThat(schemas.at("/CreateRideRequest/required")).extracting(JsonNode::asText)
                .containsExactlyInAnyOrder("origin", "target");
        assertThat(schemas.at("/Coordinates/properties/lat/minimum").asInt()).isEqualTo(-90);
        assertThat(schemas.at("/Coordinates/properties/lat/maximum").asInt()).isEqualTo(90);
        assertThat(schemas.at("/Coordinates/properties/lon/minimum").asInt()).isEqualTo(-180);
        assertThat(schemas.at("/Coordinates/properties/lon/maximum").asInt()).isEqualTo(180);
        assertThat(schemas.at("/Problem/properties").has("errors")).isTrue();
        assertThat(schemas.at("/Problem/properties").has("properties")).isFalse();
    }

    @Test
    @DisplayName("IT document should name the Authorization header the endpoints are called with")
    void document_whenRead_shouldNameAuthorizationHeaderEndpointsAreCalledWith() {
        // when
        JsonNode scheme = document.at("/components/securitySchemes/caller");

        // then
        assertThat(scheme.get("type").asText()).isEqualTo("apiKey");
        assertThat(scheme.get("in").asText()).isEqualTo("header");
        assertThat(scheme.get("name").asText()).isEqualTo("Authorization");
        assertThat(document.at("/security/0").has("caller")).isTrue();
    }

    @Test
    @DisplayName("IT swagger ui should be served without the Authorization header")
    void swaggerUi_whenRequested_shouldBeServedWithoutAuthorizationHeader() {
        // when
        ResponseEntity<String> response = rest.getForEntity("/swagger-ui/index.html", String.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private JsonNode createRide() {
        return document.at("/paths/~1api~1v1~1rides/post");
    }
}
