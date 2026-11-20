package io.github.idempotency.receipt.sending;

import io.github.idempotency.contract.ReceiptJob;
import io.github.idempotency.receipt.support.ReceiptJobs;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class ReceiptSenderUnitTest {

    @Mock
    private ReceiptRepository receiptRepository;

    @InjectMocks
    private ReceiptSender tested;

    @Test
    @DisplayName("UT send() when the ride has no receipt yet should keep it and report it sent")
    void send_whenRideHasNoReceiptYet_shouldKeepItAndReportItSent(CapturedOutput output) {
        // given
        ReceiptJob job = ReceiptJobs.job(7L);
        when(receiptRepository.saveIfAbsent(job)).thenReturn(true);

        // when
        tested.send(job);

        // then
        assertThat(output).contains("Receipt sent for ride 7: 2000 usd");
    }

    @Test
    @DisplayName("UT send() when the ride already has a receipt should report it as already sent")
    void send_whenRideAlreadyHasReceipt_shouldReportItAsAlreadySent(CapturedOutput output) {
        // given
        ReceiptJob job = ReceiptJobs.job(7L);
        when(receiptRepository.saveIfAbsent(job)).thenReturn(false);

        // when
        tested.send(job);

        // then
        assertThat(output).contains("Receipt for ride 7 already sent").doesNotContain("Receipt sent for ride 7");
    }

    @Test
    @DisplayName("UT constructor when receiptRepository is null should throw NullPointerException")
    void constructor_whenReceiptRepositoryIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new ReceiptSender(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("receiptRepository cannot be null");
    }
}
