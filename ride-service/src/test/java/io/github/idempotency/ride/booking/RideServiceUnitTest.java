package io.github.idempotency.ride.booking;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.idempotency.ride.audit.AuditAction;
import io.github.idempotency.ride.audit.AuditRecord;
import io.github.idempotency.ride.audit.AuditRepository;
import io.github.idempotency.ride.audit.AuditResourceType;
import io.github.idempotency.ride.stripe.PaymentDeclinedException;
import io.github.idempotency.ride.stripe.StripeChargeClient;
import io.github.idempotency.ride.support.RecordingTransactionManager;
import io.github.idempotency.ride.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Optional;

import static io.github.idempotency.ride.booking.TestRides.CHARGED_AT;
import static io.github.idempotency.ride.booking.TestRides.KEY;
import static io.github.idempotency.ride.booking.TestRides.ORIGIN;
import static io.github.idempotency.ride.booking.TestRides.TARGET;
import static io.github.idempotency.ride.booking.TestRides.USER;
import static io.github.idempotency.ride.booking.TestRides.charged;
import static io.github.idempotency.ride.booking.TestRides.request;
import static io.github.idempotency.ride.booking.TestRides.uncharged;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RideServiceUnitTest {

    private static final String ORIGIN_IP = "203.0.113.7";

    private final RecordingTransactionManager transactionManager = new RecordingTransactionManager();
    private final TransactionTemplate transaction = new TransactionTemplate(transactionManager);
    private final FareProperties fare = new FareProperties(2000L, "usd");
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Clock clock = Clock.fixed(CHARGED_AT, ZoneOffset.UTC);

    @Mock
    private RideRepository rideRepository;

    @Mock
    private AuditRepository auditRepository;

    @Mock
    private StripeChargeClient stripe;

    private RideService tested;

    @BeforeEach
    void setUp() {
        tested = new RideService(rideRepository, auditRepository, stripe, transaction, fare, objectMapper, clock);
    }

    @Test
    @DisplayName("UT create() when the key is new should book, audit and charge the ride once")
    void create_whenKeyIsNew_shouldBookAuditAndChargeRideOnce() {
        // given
        when(rideRepository.insertIfAbsent(new NewRide(USER.id(), KEY, ORIGIN, TARGET, 2000L, "usd")))
                .thenReturn(Optional.of(uncharged(7L)));
        when(stripe.charge("cus_rider", 2000L, "usd", "Ride 7", KEY)).thenReturn("ch_123");
        when(rideRepository.markCharged(7L, "ch_123", CHARGED_AT)).thenReturn(charged(7L, "ch_123"));

        // when
        RideResponse response = tested.create(USER, ORIGIN_IP, KEY, request());

        // then
        assertThat(response).isEqualTo(RideResponse.from(charged(7L, "ch_123")));
        ArgumentCaptor<AuditRecord> audit = ArgumentCaptor.forClass(AuditRecord.class);
        verify(auditRepository).save(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo(AuditAction.CREATE_RIDE);
        assertThat(audit.getValue().resourceType()).isEqualTo(AuditResourceType.RIDE);
        assertThat(audit.getValue().resourceId()).isEqualTo(7L);
        assertThat(audit.getValue().userId()).isEqualTo(USER.id());
        assertThat(audit.getValue().originIp()).isEqualTo(ORIGIN_IP);
        assertThat(audit.getValue().data()).isEqualTo(objectMapper.valueToTree(request()));
        assertThat(transactionManager.commits()).isEqualTo(2);
    }

    @Test
    @DisplayName("UT create() when the key already booked and charged this ride should answer it without charging")
    void create_whenKeyAlreadyBookedAndChargedThisRide_shouldAnswerItWithoutCharging() {
        // given
        when(rideRepository.insertIfAbsent(any(NewRide.class))).thenReturn(Optional.empty());
        when(rideRepository.findByIdempotencyKey(KEY)).thenReturn(Optional.of(charged(7L, "ch_123")));

        // when
        RideResponse response = tested.create(USER, ORIGIN_IP, KEY, request());

        // then
        assertThat(response).isEqualTo(RideResponse.from(charged(7L, "ch_123")));
        verifyNoInteractions(stripe, auditRepository);
        verify(rideRepository, never()).markCharged(anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("UT create() when the key booked this ride but its charge was not recorded should charge it again")
    void create_whenKeyBookedThisRideButChargeWasNotRecorded_shouldChargeItAgain() {
        // given
        when(rideRepository.insertIfAbsent(any(NewRide.class))).thenReturn(Optional.empty());
        when(rideRepository.findByIdempotencyKey(KEY)).thenReturn(Optional.of(uncharged(7L)));
        when(stripe.charge("cus_rider", 2000L, "usd", "Ride 7", KEY)).thenReturn("ch_123");
        when(rideRepository.markCharged(7L, "ch_123", CHARGED_AT)).thenReturn(charged(7L, "ch_123"));

        // when
        RideResponse response = tested.create(USER, ORIGIN_IP, KEY, request());

        // then
        assertThat(response.stripeChargeId()).isEqualTo("ch_123");
        verifyNoInteractions(auditRepository);
    }

    @Test
    @DisplayName("UT create() when the uncharged ride is older than the charge window should refuse to charge it")
    void create_whenUnchargedRideIsOlderThanChargeWindow_shouldRefuseToChargeIt() {
        // given
        Ride old = new Ride(7L, USER.id(), KEY, ORIGIN, TARGET, 2000L, "usd", null, null,
                CHARGED_AT.minus(RideService.CHARGE_WINDOW).minusSeconds(1));
        when(rideRepository.insertIfAbsent(any(NewRide.class))).thenReturn(Optional.empty());
        when(rideRepository.findByIdempotencyKey(KEY)).thenReturn(Optional.of(old));

        // when / then
        assertThatThrownBy(() -> tested.create(USER, ORIGIN_IP, KEY, request()))
                .isInstanceOf(BookingExpiredException.class)
                .hasMessage("Ride 7 was booked too long ago to be charged under the same Idempotency-Key");
        verifyNoInteractions(stripe);
    }

    @Test
    @DisplayName("UT create() when the uncharged ride is exactly at the end of the charge window should still charge it")
    void create_whenUnchargedRideIsAtEndOfChargeWindow_shouldStillChargeIt() {
        // given
        Ride atEdge = new Ride(7L, USER.id(), KEY, ORIGIN, TARGET, 2000L, "usd", null, null,
                CHARGED_AT.minus(RideService.CHARGE_WINDOW));
        when(rideRepository.insertIfAbsent(any(NewRide.class))).thenReturn(Optional.empty());
        when(rideRepository.findByIdempotencyKey(KEY)).thenReturn(Optional.of(atEdge));
        when(stripe.charge("cus_rider", 2000L, "usd", "Ride 7", KEY)).thenReturn("ch_123");
        when(rideRepository.markCharged(7L, "ch_123", CHARGED_AT)).thenReturn(charged(7L, "ch_123"));

        // when
        RideResponse response = tested.create(USER, ORIGIN_IP, KEY, request());

        // then
        assertThat(response.stripeChargeId()).isEqualTo("ch_123");
    }

    @Test
    @DisplayName("UT create() when the same route is written with another scale should count as the same ride")
    void create_whenSameRouteIsWrittenWithAnotherScale_shouldCountAsSameRide() {
        // given
        CreateRideRequest rescaled = new CreateRideRequest(
                new Coordinates(new BigDecimal("52.520006600"), new BigDecimal("13.404954")),
                TARGET,
                null
        );
        when(rideRepository.insertIfAbsent(any(NewRide.class))).thenReturn(Optional.empty());
        when(rideRepository.findByIdempotencyKey(KEY)).thenReturn(Optional.of(charged(7L, "ch_123")));

        // when
        RideResponse response = tested.create(USER, ORIGIN_IP, KEY, rescaled);

        // then
        assertThat(response.id()).isEqualTo(7L);
    }

    @Test
    @DisplayName("UT create() when the key booked a ride of another user should throw IdempotencyKeyReusedException")
    void create_whenKeyBookedRideOfAnotherUser_shouldThrowIdempotencyKeyReusedException() {
        // given
        User another = new User(4L, "another@example.com", "cus_another");
        when(rideRepository.insertIfAbsent(any(NewRide.class))).thenReturn(Optional.empty());
        when(rideRepository.findByIdempotencyKey(KEY)).thenReturn(Optional.of(charged(7L, "ch_123")));

        // when / then
        assertThatThrownBy(() -> tested.create(another, ORIGIN_IP, KEY, request()))
                .isInstanceOf(IdempotencyKeyReusedException.class)
                .hasMessage("Idempotency-Key %s already booked a different ride".formatted(KEY));
        verifyNoInteractions(stripe);
    }

    @Test
    @DisplayName("UT create() when the key booked a ride to another target should throw IdempotencyKeyReusedException")
    void create_whenKeyBookedRideToAnotherTarget_shouldThrowIdempotencyKeyReusedException() {
        // given
        CreateRideRequest elsewhere = new CreateRideRequest(ORIGIN, ORIGIN, null);
        when(rideRepository.insertIfAbsent(any(NewRide.class))).thenReturn(Optional.empty());
        when(rideRepository.findByIdempotencyKey(KEY)).thenReturn(Optional.of(uncharged(7L)));

        // when / then
        assertThatThrownBy(() -> tested.create(USER, ORIGIN_IP, KEY, elsewhere))
                .isInstanceOf(IdempotencyKeyReusedException.class);
        verifyNoInteractions(stripe);
    }

    @Test
    @DisplayName("UT create() when the key booked a ride from another origin should throw IdempotencyKeyReusedException")
    void create_whenKeyBookedRideFromAnotherOrigin_shouldThrowIdempotencyKeyReusedException() {
        // given
        CreateRideRequest elsewhere = new CreateRideRequest(TARGET, TARGET, null);
        when(rideRepository.insertIfAbsent(any(NewRide.class))).thenReturn(Optional.empty());
        when(rideRepository.findByIdempotencyKey(KEY)).thenReturn(Optional.of(uncharged(7L)));

        // when / then
        assertThatThrownBy(() -> tested.create(USER, ORIGIN_IP, KEY, elsewhere))
                .isInstanceOf(IdempotencyKeyReusedException.class);
    }

    @Test
    @DisplayName("UT create() when the conflicting ride is gone by the time it is read should throw IllegalStateException")
    void create_whenConflictingRideIsGone_shouldThrowIllegalStateException() {
        // given
        when(rideRepository.insertIfAbsent(any(NewRide.class))).thenReturn(Optional.empty());
        when(rideRepository.findByIdempotencyKey(KEY)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> tested.create(USER, ORIGIN_IP, KEY, request()))
                .isInstanceOf(IllegalStateException.class);
        assertThat(transactionManager.rollbacks()).isEqualTo(1);
    }

    @Test
    @DisplayName("UT create() when Stripe declines the card should leave the ride uncharged and pass the refusal on")
    void create_whenStripeDeclinesCard_shouldLeaveRideUnchargedAndPassRefusalOn() {
        // given
        PaymentDeclinedException declined = new PaymentDeclinedException("The card was declined");
        when(rideRepository.insertIfAbsent(any(NewRide.class))).thenReturn(Optional.of(uncharged(7L)));
        when(stripe.charge("cus_rider", 2000L, "usd", "Ride 7", KEY)).thenThrow(declined);

        // when / then
        assertThatThrownBy(() -> tested.create(USER, ORIGIN_IP, KEY, request())).isSameAs(declined);
        verify(rideRepository, never()).markCharged(anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("UT constructor when rideRepository is null should throw NullPointerException")
    void constructor_whenRideRepositoryIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new RideService(null, auditRepository, stripe, transaction, fare, objectMapper, clock))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("rideRepository cannot be null");
    }

    @Test
    @DisplayName("UT constructor when auditRepository is null should throw NullPointerException")
    void constructor_whenAuditRepositoryIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new RideService(rideRepository, null, stripe, transaction, fare, objectMapper, clock))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("auditRepository cannot be null");
    }

    @Test
    @DisplayName("UT constructor when stripe is null should throw NullPointerException")
    void constructor_whenStripeIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new RideService(
                rideRepository,
                auditRepository,
                null,
                transaction,
                fare,
                objectMapper,
                clock
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("stripe cannot be null");
    }

    @Test
    @DisplayName("UT constructor when transaction is null should throw NullPointerException")
    void constructor_whenTransactionIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new RideService(rideRepository, auditRepository, stripe, null, fare, objectMapper, clock))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("transaction cannot be null");
    }

    @Test
    @DisplayName("UT constructor when fare is null should throw NullPointerException")
    void constructor_whenFareIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new RideService(
                rideRepository,
                auditRepository,
                stripe,
                transaction,
                null,
                objectMapper,
                clock
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("fare cannot be null");
    }

    @Test
    @DisplayName("UT constructor when objectMapper is null should throw NullPointerException")
    void constructor_whenObjectMapperIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new RideService(rideRepository, auditRepository, stripe, transaction, fare, null, clock))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("objectMapper cannot be null");
    }

    @Test
    @DisplayName("UT constructor when clock is null should throw NullPointerException")
    void constructor_whenClockIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new RideService(
                rideRepository,
                auditRepository,
                stripe,
                transaction,
                fare,
                objectMapper,
                null
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("clock cannot be null");
    }
}
