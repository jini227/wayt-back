package com.wayt.notifications;

import java.util.List;
import java.util.Map;

public interface PushGateway {
    PushTicket send(PushMessage message);

    Map<String, PushReceipt> receipts(List<String> ticketIds);
}
