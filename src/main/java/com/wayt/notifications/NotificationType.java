package com.wayt.notifications;

import java.util.Arrays;
import java.util.List;

public enum NotificationType {
    APPOINTMENT_INVITE("appointment-invite", true),
    APPOINTMENT_INVITE_CANCELLED("appointment-invite-cancelled", true),
    LOCATION_SHARE_START("location-share-start", true),
    ARRIVAL_STATUS("arrival-status", true),
    INVITE_RESPONSE("invite-response", true),
    APPOINTMENT_CHANGED("appointment-changed", true),
    APPOINTMENT_CANCELLED("appointment-cancelled", true),
    DEPARTURE_RECOMMENDATION("departure-recommendation", true),
    LATE_RISK("late-risk", true),
    NOT_DEPARTED("not-departed", true),
    NEAR_ARRIVAL("near-arrival", true),
    APPOINTMENT_REMINDER("appointment-reminder", false),
    ALL_ARRIVED("all-arrived", false),
    MANUAL_ARRIVAL("manual-arrival", false),
    LOCATION_CONSENT("location-consent", false),
    INVITE_PENDING("invite-pending", false),
    PENALTY_CONFIRMED("penalty-confirmed", false),
    DEPARTURE_STARTED("departure-started", false),
    ETA_UPDATED("eta-updated", false),
    HISTORY_SAVED("history-saved", false),
    REINVITE("reinvite", false),
    PREMIUM_FEATURE("premium-feature", false);

    private final String apiId;
    private final boolean defaultEnabled;

    NotificationType(String apiId, boolean defaultEnabled) {
        this.apiId = apiId;
        this.defaultEnabled = defaultEnabled;
    }

    public String apiId() {
        return apiId;
    }

    public boolean defaultEnabled() {
        return defaultEnabled;
    }

    public static NotificationType fromApiId(String apiId) {
        return Arrays.stream(values())
                .filter(type -> type.apiId.equals(apiId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown notification type: " + apiId));
    }

    public static boolean isKnownApiId(String apiId) {
        return Arrays.stream(values()).anyMatch(type -> type.apiId.equals(apiId));
    }

    public static List<String> allApiIds() {
        return Arrays.stream(values()).map(NotificationType::apiId).toList();
    }
}
