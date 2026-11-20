package io.github.idempotency.ride.reaper;

import io.github.idempotency.ride.support.RequiresDocker;
import io.github.idempotency.ride.support.TestInfrastructure;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@RequiresDocker
@SpringBootTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class IdempotencyRecordReaperIntegrationTest {

    private final List<ScheduledTaskHolder> holders;

    IdempotencyRecordReaperIntegrationTest(List<ScheduledTaskHolder> holders) {
        this.holders = holders;
    }

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        TestInfrastructure.register(registry);
    }

    @Test
    @DisplayName("IT context when started should schedule the reaper every five minutes after a minute's delay")
    void context_whenStarted_shouldScheduleReaperEveryFiveMinutesAfterMinuteDelay() {
        // when
        List<FixedDelayTask> reaperTasks = holders.stream()
                .flatMap(holder -> holder.getScheduledTasks().stream())
                .map(ScheduledTask::getTask)
                .filter(FixedDelayTask.class::isInstance)
                .map(FixedDelayTask.class::cast)
                // the scheduler wraps the method's runnable, whose description names the method
                .filter(task -> task.toString().endsWith(IdempotencyRecordReaper.class.getName() + ".reap"))
                .toList();

        // then
        assertThat(reaperTasks).singleElement().satisfies(task -> {
            assertThat(task.getIntervalDuration()).isEqualTo(Duration.ofMinutes(5));
            assertThat(task.getInitialDelayDuration()).isEqualTo(Duration.ofMinutes(1));
        });
    }
}
