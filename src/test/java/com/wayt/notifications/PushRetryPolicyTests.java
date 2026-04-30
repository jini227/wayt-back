package com.wayt.notifications;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class PushRetryPolicyTests {
    private final PushRetryPolicy retryPolicy = new PushRetryPolicy();

    @Test
    void retriesRateLimitsServerErrorsAndNetworkFailures() {
        assertThat(retryPolicy.shouldRetry(PushDeliveryFailure.http(HttpStatus.TOO_MANY_REQUESTS))).isTrue();
        assertThat(retryPolicy.shouldRetry(PushDeliveryFailure.http(HttpStatus.BAD_GATEWAY))).isTrue();
        assertThat(retryPolicy.shouldRetry(PushDeliveryFailure.network())).isTrue();
    }

    @Test
    void doesNotRetryBadPayloadErrors() {
        assertThat(retryPolicy.shouldRetry(PushDeliveryFailure.http(HttpStatus.BAD_REQUEST))).isFalse();
    }

    @Test
    void increasesBackoffWithRetryCountAndCapsIt() {
        assertThat(retryPolicy.backoffDelay(0)).isEqualTo(Duration.ofMinutes(1));
        assertThat(retryPolicy.backoffDelay(1)).isEqualTo(Duration.ofMinutes(2));
        assertThat(retryPolicy.backoffDelay(10)).isEqualTo(Duration.ofHours(1));
    }
}
