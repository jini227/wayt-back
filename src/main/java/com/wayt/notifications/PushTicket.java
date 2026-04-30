package com.wayt.notifications;

public record PushTicket(String id, String status, String errorCode, String message) {
    public static PushTicket ok(String id) {
        return new PushTicket(id, "ok", null, null);
    }

    public static PushTicket error(String errorCode, String message) {
        return new PushTicket(null, "error", errorCode, message);
    }

    public boolean ok() {
        return "ok".equals(status);
    }
}
