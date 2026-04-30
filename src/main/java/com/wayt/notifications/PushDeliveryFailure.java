package com.wayt.notifications;

import org.springframework.http.HttpStatusCode;

public record PushDeliveryFailure(String kind, Integer httpStatus) {
    public static PushDeliveryFailure http(HttpStatusCode status) {
        return new PushDeliveryFailure("http", status.value());
    }

    public static PushDeliveryFailure network() {
        return new PushDeliveryFailure("network", null);
    }

    public boolean isNetwork() {
        return "network".equals(kind);
    }
}
