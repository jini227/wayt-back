package com.wayt.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class HistoryDtos {
    private HistoryDtos() {
    }

    public record HistoryItemResponse(
            UUID appointmentId,
            String title,
            OffsetDateTime scheduledAt,
            String placeName,
            String myRole,
            String summary,
            String penalty,
            List<AppointmentDtos.ParticipantResponse> participants
    ) {
    }
}
