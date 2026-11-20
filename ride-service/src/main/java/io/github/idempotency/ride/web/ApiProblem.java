package io.github.idempotency.ride.web;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The shape of every error answer, for the API document only: the answers themselves are {@code ProblemDetail}s,
 * whose extra properties Jackson writes at the top level where springdoc would document a nested object.
 */
@Schema(name = "Problem", description = "Error answer, RFC 9457 problem details")
public record ApiProblem(
        @Schema(description = "URI naming the kind of failure, the field to branch on",
                example = "https://idempify.io/errors/operation-in-process",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String type,

        @Schema(description = "Short summary of the kind of failure", example = "Operation is already in process",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String title,

        @Schema(description = "HTTP status of the answer", example = "409", requiredMode = Schema.RequiredMode.REQUIRED)
        int status,

        @Schema(description = "What happened in this occurrence",
                example = "Operation (idempotencyKey=6f1c9f0a-2b3e-4a4b-9f0a-1234567890ab) is already in progress",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String detail,

        @Schema(description = "Path of the request", example = "/api/v1/rides",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String instance,

        @Schema(description = "Path of the request, for clients of Spring Boot's default error body",
                example = "/api/v1/rides", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String path,

        @Schema(description = "When the failure was answered", example = "2026-11-18T10:15:30Z",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Instant timestamp,

        @Schema(description = "Idempotency-Key of the request, on the refusals of the idempotency layer",
                example = "6f1c9f0a-2b3e-4a4b-9f0a-1234567890ab", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        UUID idempotencyKey,

        @Schema(description = "Fields that failed validation, on a validation failure only",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        List<ApiFieldError> errors
) {

    @Schema(name = "FieldError", description = "Field that failed validation")
    public record ApiFieldError(
            @Schema(description = "Path of the field in the request body", example = "origin.lat",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            String field,

            @Schema(description = "What is wrong with the field", example = "lat must be at most 90",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            String message
    ) {
    }
}
