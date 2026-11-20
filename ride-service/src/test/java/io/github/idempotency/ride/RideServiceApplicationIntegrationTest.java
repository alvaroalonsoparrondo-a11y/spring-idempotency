package io.github.idempotency.ride;

import io.github.dmitriyiliyov.idempify.cache.redis.RedisCacheResponseRepositoryDecorator;
import io.github.dmitriyiliyov.idempify.core.response.ResponseRepository;
import io.github.dmitriyiliyov.idempify.core.response.ResponseRepositoryWrapper;
import io.github.idempotency.ride.support.RequiresDocker;
import io.github.idempotency.ride.support.TestInfrastructure;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@SpringBootTest
@RequiresDocker
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class RideServiceApplicationIntegrationTest {

    private final JdbcClient jdbc;
    private final List<ResponseRepositoryWrapper> responseRepositoryWrappers;

    RideServiceApplicationIntegrationTest(JdbcClient jdbc, List<ResponseRepositoryWrapper> responseRepositoryWrappers) {
        this.jdbc = jdbc;
        this.responseRepositoryWrappers = responseRepositoryWrappers;
    }

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) {
        TestInfrastructure.register(registry);
    }

    @Test
    @DisplayName("IT context when started should migrate the idempotency table beside the business tables")
    void context_whenStarted_shouldMigrateIdempotencyTableBesideBusinessTables() {
        // when
        Long tables = jdbc.sql("""
                        SELECT count(*) FROM information_schema.tables
                        WHERE table_schema = 'public'
                          AND table_name IN ('users', 'rides', 'audit_records', 'idempotent_operations',
                                             'idempify_schema_history', 'flyway_schema_history')
                        """)
                .query(Long.class)
                .single();

        // then
        assertThat(tables).isEqualTo(6L);
    }

    @Test
    @DisplayName("IT context when started should put the Redis cache in front of the recorded responses")
    void context_whenStarted_shouldPutRedisCacheInFrontOfRecordedResponses() {
        // when
        List<Object> wrapped = responseRepositoryWrappers.stream()
                .map(wrapper -> (Object) wrapper.wrap(mock(ResponseRepository.class)))
                .toList();

        // then
        assertThat(wrapped).hasAtLeastOneElementOfType(RedisCacheResponseRepositoryDecorator.class);
    }
}
