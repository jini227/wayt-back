package com.wayt.notifications;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationTypeTests {
    @Test
    void defaultsMatchTheAppNotificationCatalog() {
        assertThat(NotificationType.fromApiId("appointment-invite").defaultEnabled()).isTrue();
        assertThat(NotificationType.fromApiId("appointment-invite-cancelled").defaultEnabled()).isTrue();
        assertThat(NotificationType.fromApiId("location-share-start").defaultEnabled()).isTrue();
        assertThat(NotificationType.fromApiId("history-saved").defaultEnabled()).isFalse();
        assertThat(NotificationType.allApiIds()).contains("appointment-reminder", "all-arrived");
    }
}
