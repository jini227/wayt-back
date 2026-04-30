package com.wayt.notifications;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class ExpoPushGateway implements PushGateway {
    private final RestClient restClient = RestClient.builder()
            .baseUrl("https://exp.host/--/api/v2/push")
            .build();

    @Override
    public PushTicket send(PushMessage message) {
        JsonNode body;
        try {
            body = restClient.post()
                    .uri("/send")
                    .body(List.of(message))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException exception) {
            throw gatewayException(exception.getStatusCode(), exception.getResponseBodyAsString());
        } catch (RestClientException exception) {
            throw new PushGatewayException(PushDeliveryFailure.network(), exception.getMessage());
        }

        JsonNode ticket = body == null ? null : body.path("data").path(0);
        if (ticket == null || ticket.isMissingNode()) {
            return PushTicket.error("MalformedExpoResponse", "Expo Push API returned no ticket data.");
        }
        String status = text(ticket.path("status"));
        if ("ok".equals(status)) {
            return PushTicket.ok(text(ticket.path("id")));
        }
        return PushTicket.error(firstNonBlank(text(ticket.path("details").path("error")), "ExpoTicketError"), text(ticket.path("message")));
    }

    @Override
    public Map<String, PushReceipt> receipts(List<String> ticketIds) {
        if (ticketIds.isEmpty()) {
            return Map.of();
        }

        JsonNode body;
        try {
            body = restClient.post()
                    .uri("/getReceipts")
                    .body(Map.of("ids", ticketIds))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException exception) {
            throw gatewayException(exception.getStatusCode(), exception.getResponseBodyAsString());
        } catch (RestClientException exception) {
            throw new PushGatewayException(PushDeliveryFailure.network(), exception.getMessage());
        }

        JsonNode data = body == null ? null : body.path("data");
        return ticketIds.stream()
                .map(ticketId -> receiptEntry(ticketId, data == null ? null : data.path(ticketId)))
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private Map.Entry<String, PushReceipt> receiptEntry(String ticketId, JsonNode node) {
        if (node == null || node.isMissingNode()) {
            return null;
        }
        String status = text(node.path("status"));
        PushReceipt receipt = "ok".equals(status)
                ? PushReceipt.ok()
                : PushReceipt.error(firstNonBlank(text(node.path("details").path("error")), "ExpoReceiptError"), text(node.path("message")));
        return Map.entry(ticketId, receipt);
    }

    private PushGatewayException gatewayException(HttpStatusCode status, String message) {
        return new PushGatewayException(PushDeliveryFailure.http(status), message);
    }

    private String text(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText();
        return value == null || value.isBlank() ? null : value;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
