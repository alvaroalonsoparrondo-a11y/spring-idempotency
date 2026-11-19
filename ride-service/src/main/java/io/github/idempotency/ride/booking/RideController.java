package io.github.idempotency.ride.booking;

import io.github.dmitriyiliyov.idempify.core.Idempotent;
import io.github.dmitriyiliyov.idempify.core.ProcessorTypeToggle;
import io.github.idempotency.ride.stripe.PaymentDeclinedException;
import io.github.idempotency.ride.stripe.PaymentFailedException;
import io.github.idempotency.ride.web.Caller;
import io.github.idempotency.ride.web.IdempotencyKey;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1/rides")
public class RideController {

    private final RideService rideService;

    public RideController(RideService rideService) {
        this.rideService = rideService;
    }

    /**
     * Books and charges a ride once per Idempotency-Key. The charge cannot be rolled back, so the booking runs
     * outside the idempotency transaction, under a {@link ChargeLock} held longer than the slowest booking the
     * configuration allows. A declined card and a charge Stripe failed for good
     * are kept on the record and answered to every repeat; any other failure releases the key for a retry.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Idempotent(
            processorType = ProcessorTypeToggle.EXTERNAL_NON_BLOCKING,
            lockTtl = ChargeLock.MINUTES,
            timeUnit = TimeUnit.MINUTES,
            recordFor = {PaymentDeclinedException.class, PaymentFailedException.class}
    )
    public RideResponse create(@RequestAttribute(Caller.ATTRIBUTE) Caller caller,
                               @IdempotencyKey UUID idempotencyKey,
                               @Valid @RequestBody CreateRideRequest request) {
        return rideService.create(caller.user(), caller.originIp(), idempotencyKey, request);
    }
}
