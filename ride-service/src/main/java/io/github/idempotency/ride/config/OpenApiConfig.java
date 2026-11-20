package io.github.idempotency.ride.config;

import io.github.idempotency.ride.web.ApiProblem;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    static final String CALLER_SCHEME = "caller";

    private static final String PROBLEM_JSON = org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE;

    @Bean
    public OpenAPI rideServiceOpenApi() {
        Components components = new Components()
                .addSecuritySchemes(CALLER_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name("Authorization")
                        .description("Email of a known user"));
        ModelConverters.getInstance().readAll(ApiProblem.class).forEach(components::addSchemas);
        return new OpenAPI()
                .info(new Info()
                        .title("ride-service API")
                        .description("Rides booked and charged once per Idempotency-Key")
                        .version("v1")
                        .license(new License().name("Apache 2.0").url("https://www.apache.org/licenses/LICENSE-2.0")))
                .components(components)
                .addSecurityItem(new SecurityRequirement().addList(CALLER_SCHEME));
    }

    /**
     * Every 4xx answer is a problem: its documented body is the problem schema, whatever springdoc inferred from the
     * method's return type.
     */
    @Bean
    public OperationCustomizer problemResponses() {
        Schema<?> problem = new Schema<>().$ref("#/components/schemas/Problem");
        return (operation, handlerMethod) -> {
            if (operation.getResponses() != null) {
                operation.getResponses().forEach((code, response) -> {
                    if (code.startsWith("4")) {
                        response.setContent(new Content().addMediaType(PROBLEM_JSON, new MediaType().schema(problem)));
                    }
                });
            }
            return operation;
        };
    }
}
