package com.wayt.notifications;

import java.time.Duration;

public class PushRetryPolicy {
    private static final Duration MAX_BACKOFF = Duration.ofHours(1);

    public boolean shouldRetry(PushDeliveryFailure failure) {
        if (failure.isNetwork()) {
            return true;
        }
        Integer status = failure.httpStatus();
        return status != null && (status == 429 || status >= 500);
    }

    public Duration backoffDelay(int retryCount) {
        long minutes = (long) Math.pow(2, Math.max(0, retryCount));
        Duration delay = Duration.ofMinutes(minutes);
        return delay.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : delay;
    }
}
