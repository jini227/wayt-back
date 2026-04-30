package com.wayt.notifications;

import com.wayt.domain.NotificationJob;

public class NotificationReceiptHandler {
    public void applyReceipt(NotificationJob job, PushReceipt receipt) {
        if (receipt.isOk()) {
            job.markReceiptOk();
            return;
        }

        if ("DeviceNotRegistered".equals(receipt.errorCode())) {
            job.getPushToken().invalidate();
        }
        job.markFailed(receipt.errorCode(), receipt.message());
    }
}
