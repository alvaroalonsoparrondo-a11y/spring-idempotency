package io.github.idempotency.ride.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditRecordUnitTest {

    private static final JsonNode DATA = JsonNodeFactory.instance.objectNode();

    @Test
    @DisplayName("UT constructor when action is null should throw NullPointerException")
    void constructor_whenActionIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new AuditRecord(null, AuditResourceType.RIDE, 1L, 3L, "203.0.113.7", DATA))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("action cannot be null");
    }

    @Test
    @DisplayName("UT constructor when resourceType is null should throw NullPointerException")
    void constructor_whenResourceTypeIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new AuditRecord(AuditAction.CREATE_RIDE, null, 1L, 3L, "203.0.113.7", DATA))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("resourceType cannot be null");
    }

    @Test
    @DisplayName("UT constructor when originIp is null should throw NullPointerException")
    void constructor_whenOriginIpIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new AuditRecord(AuditAction.CREATE_RIDE, AuditResourceType.RIDE, 1L, 3L, null, DATA))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("originIp cannot be null");
    }

    @Test
    @DisplayName("UT constructor when data is null should throw NullPointerException")
    void constructor_whenDataIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new AuditRecord(
                AuditAction.CREATE_RIDE,
                AuditResourceType.RIDE,
                1L,
                3L,
                "203.0.113.7",
                null
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("data cannot be null");
    }
}
