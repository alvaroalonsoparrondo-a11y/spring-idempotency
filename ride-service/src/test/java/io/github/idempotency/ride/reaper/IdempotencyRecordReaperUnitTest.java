package io.github.idempotency.ride.reaper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static io.github.idempotency.ride.reaper.IdempotencyRecordReaper.BATCH_SIZE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotencyRecordReaperUnitTest {

    @Mock
    private IdempotencyRecordRepository repository;

    @InjectMocks
    private IdempotencyRecordReaper tested;

    @Test
    @DisplayName("UT reap() when nothing has expired should make one pass and delete nothing")
    void reap_whenNothingHasExpired_shouldMakeOnePassAndDeleteNothing() {
        // given
        when(repository.deleteExpired(BATCH_SIZE)).thenReturn(0);

        // when
        int deleted = tested.reap();

        // then
        assertThat(deleted).isZero();
        verify(repository, times(1)).deleteExpired(BATCH_SIZE);
    }

    @Test
    @DisplayName("UT reap() when full batches come back should keep deleting until a batch comes back short")
    void reap_whenFullBatchesComeBack_shouldKeepDeletingUntilBatchComesBackShort() {
        // given
        when(repository.deleteExpired(BATCH_SIZE)).thenReturn(BATCH_SIZE, BATCH_SIZE, 7);

        // when
        int deleted = tested.reap();

        // then
        assertThat(deleted).isEqualTo(2 * BATCH_SIZE + 7);
        verify(repository, times(3)).deleteExpired(BATCH_SIZE);
    }

    @Test
    @DisplayName("UT reap() when the last full batch emptied the table should stop after one empty pass")
    void reap_whenLastFullBatchEmptiedTable_shouldStopAfterOneEmptyPass() {
        // given
        when(repository.deleteExpired(BATCH_SIZE)).thenReturn(BATCH_SIZE, 0);

        // when
        int deleted = tested.reap();

        // then
        assertThat(deleted).isEqualTo(BATCH_SIZE);
        verify(repository, times(2)).deleteExpired(BATCH_SIZE);
    }

    @Test
    @DisplayName("UT constructor when repository is null should throw NullPointerException")
    void constructor_whenRepositoryIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new IdempotencyRecordReaper(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("repository cannot be null");
    }
}
