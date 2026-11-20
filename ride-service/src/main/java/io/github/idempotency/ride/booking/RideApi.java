package io.github.idempotency.ride.booking;

import io.github.idempotency.ride.web.Caller;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import java.util.UUID;

@Tag(name = "Rides", description = "Booking rides, each charged once per Idempotency-Key")
public interface RideApi {

    @Operation(
            summary = "Book and charge a ride",
            description = "A repeat under the same Idempotency-Key is answered with the first answer, status and body "
                    + "as they were, without booking or charging again"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Ride successfully created",
                    content = @Content(schema = @Schema(implementation = RideResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Request validation failed or Idempotency-Key is missing or not a UUID"
            ),
            @ApiResponse(responseCode = "401", description = "Authorization header names no known user"),
            @ApiResponse(responseCode = "402", description = "Card declined, the same answer to every repeat"),
            @ApiResponse(
                    responseCode = "409",
                    description = "Request under the same Idempotency-Key still in process or booking too old to "
                            + "be charged"
            ),
            @ApiResponse(
                    responseCode = "422",
                    description = "Idempotency-Key already used with a different request"
            )
    })
    RideResponse create(@Parameter(hidden = true) Caller caller,
                        @Parameter(
                                name = "Idempotency-Key",
                                in = ParameterIn.HEADER,
                                required = true,
                                description = "Key of the booking, a new UUID per booking, the same on every retry",
                                example = "6f1c9f0a-2b3e-4a4b-9f0a-1234567890ab"
                        )
                        UUID idempotencyKey,
                        @Valid CreateRideRequest request);
}
