package com.wayt.notifications;

public record PushReceipt(String status, String errorCode, String message) {
    public static PushReceipt ok() {
        return new PushReceipt("ok", null, null);
    }

    public static PushReceipt error(String errorCode, String message) {
        return new PushReceipt("error", errorCode, message);
    }

    public static PushReceipt deviceNotRegistered() {
        return error("DeviceNotRegistered", "The device push token is no longer registered.");
    }

    public boolean isOk() {
        return "ok".equals(status);
    }
}
