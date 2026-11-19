package io.github.idempotency.ride.audit;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Objects;

/**
 * One entry of the audit trail: who did what to which resource, from where, with the data of the request.
 *
 * @param action       what was done
 * @param resourceType the kind of resource it was done to
 * @param resourceId   the id of that resource
 * @param userId       the user who did it
 * @param originIp     the address the request came from, IPv4 or IPv6
 * @param data         the request data the action was taken with
 */
public record AuditRecord(AuditAction action,
                          AuditResourceType resourceType,
                          long resourceId,
                          long userId,
                          String originIp,
                          JsonNode data) {

    public AuditRecord {
        Objects.requireNonNull(action, "action cannot be null");
        Objects.requireNonNull(resourceType, "resourceType cannot be null");
        Objects.requireNonNull(originIp, "originIp cannot be null");
        Objects.requireNonNull(data, "data cannot be null");
    }
}
