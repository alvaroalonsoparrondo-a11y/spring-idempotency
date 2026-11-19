package io.github.idempotency.ride.booking;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.idempotency.ride.audit.AuditAction;
import io.github.idempotency.ride.audit.AuditRecord;
import io.github.idempotency.ride.audit.AuditRepository;
import io.github.idempotency.ride.audit.AuditResourceType;
import io.github.idempotency.ride.stripe.StripeChargeClient;
import io.github.idempotency.ride.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/**
 * Books a ride and charges its fare in three steps: the ride and its audit record are stored together, Stripe is
 * charged outside any transaction, and the charge is recorded on the ride.
 * <p>
 * Every step can be run again under the same idempotency key without doing its work twice: the ride is found by its
 * key instead of being stored again, Stripe gets the same key and answers with the same charge, and a ride already
 * charged is not charged again. That is what makes it safe to repeat the whole booking once its idempotency lock
 * has expired.
 */
@Service
public class RideService {

    private static final Logger log = LoggerFactory.getLogger(RideService.class);

    private final RideRepository rideRepository;
    private final AuditRepository auditRepository;
    private final StripeChargeClient stripe;
    private final TransactionTemplate transaction;
    private final FareProperties fare;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public RideService(RideRepository rideRepository,
                       AuditRepository auditRepository,
                       StripeChargeClient stripe,
                       TransactionTemplate transaction,
                       FareProperties fare,
                       ObjectMapper objectMapper,
                       Clock clock) {
        this.rideRepository = Objects.requireNonNull(rideRepository, "rideRepository cannot be null");
        this.auditRepository = Objects.requireNonNull(auditRepository, "auditRepository cannot be null");
        this.stripe = Objects.requireNonNull(stripe, "stripe cannot be null");
        this.transaction = Objects.requireNonNull(transaction, "transaction cannot be null");
        this.fare = Objects.requireNonNull(fare, "fare cannot be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    /**
     * @param user           the user booking the ride
     * @param originIp       the address the request came from
     * @param idempotencyKey the key of the request
     * @param request        the ride to book
     * @return the booked and charged ride
     * @throws IdempotencyKeyReusedException when the key already booked a different ride
     * @throws io.github.idempotency.ride.stripe.PaymentDeclinedException when Stripe declined the card
     * @throws io.github.idempotency.ride.stripe.PaymentProviderException when Stripe could not complete the charge
     */
    public RideResponse create(User user, String originIp, UUID idempotencyKey, CreateRideRequest request) {
        Ride ride = transaction.execute(status -> findOrBook(user, originIp, idempotencyKey, request));
        if (ride.isCharged()) {
            return RideResponse.from(ride);
        }
        String chargeId = stripe.charge(
                user.stripeCustomerId(),
                ride.amount(),
                ride.currency(),
                "Ride %d".formatted(ride.id()),
                idempotencyKey
        );
        Ride charged = transaction.execute(status -> rideRepository.markCharged(ride.id(), chargeId, clock.instant()));
        log.info("Ride {} booked and chraged as {}", charged.id(), chargeId);
        return RideResponse.from(charged);
    }

    private Ride findOrBook(User user, String originIp, UUID idempotencyKey, CreateRideRequest request) {
        NewRide newRide = new NewRide(
                user.id(),
                idempotencyKey,
                request.origin(),
                request.target(),
                fare.amount(),
                fare.currency()
        );
        return rideRepository.insertIfAbsent(newRide)
                .map(booked -> audited(booked, originIp, request))
                .orElseGet(() -> sameRideOrRefuse(user, idempotencyKey, request));
    }

    private Ride audited(Ride ride, String originIp, CreateRideRequest request) {
        auditRepository.save(new AuditRecord(
                AuditAction.CREATE_RIDE,
                AuditResourceType.RIDE,
                ride.id(),
                ride.userId(),
                originIp,
                objectMapper.valueToTree(request)
        ));
        return ride;
    }

    private Ride sameRideOrRefuse(User user, UUID idempotencyKey, CreateRideRequest request) {
        Ride existing = rideRepository.findByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> new IllegalStateException(
                        "Ride with Idempotency-Key %s vanished after a conflicting insert".formatted(idempotencyKey)
                ));
        boolean sameRide = existing.userId() == user.id()
                && existing.origin().samePlaceAs(request.origin())
                && existing.target().samePlaceAs(request.target());
        if (!sameRide) {
            throw new IdempotencyKeyReusedException(idempotencyKey);
        }
        return existing;
    }
}
