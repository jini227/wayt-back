package com.wayt.notifications;

public class PushGatewayException extends RuntimeException {
    private final PushDeliveryFailure failure;

    public PushGatewayException(PushDeliveryFailure failure, String message) {
        super(message);
        this.failure = failure;
    }

    public PushDeliveryFailure failure() {
        return failure;
    }
}
