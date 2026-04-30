package com.wayt.notifications;

import com.wayt.domain.NotificationJob;
import com.wayt.domain.PushToken;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationReceiptHandlerTests {
    @Test
    void deviceNotRegisteredInvalidatesTokenAndMarksJobFailed() {
        PushToken token = new PushToken(null, "ExpoPushToken[dead]", "ios");
        NotificationJob job = NotificationJob.pending(
                null,
                token,
                "appointment-invite",
                "appointment-invite:appointment-1:user-1:token-1",
                "초대가 도착했어요",
                "테스트 약속",
                "{}",
                null
        );

        NotificationReceiptHandler handler = new NotificationReceiptHandler();
        handler.applyReceipt(job, PushReceipt.deviceNotRegistered());

        assertThat(token.getInvalidatedAt()).isNotNull();
        assertThat(job.getStatus()).isEqualTo(NotificationJobStatus.FAILED);
        assertThat(job.getErrorCode()).isEqualTo("DeviceNotRegistered");
    }
}
