package com.wayt.dto;

import com.wayt.domain.InviteStatus;
import com.wayt.domain.InviteType;
import com.wayt.domain.TravelMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.UUID;

public final class InviteDtos {
    private InviteDtos() {
    }

    public record InviteByWaytIdRequest(
            @NotBlank String inviterWaytId,
            @NotBlank String targetWaytId
    ) {
    }

    public record AcceptInviteRequest(
            @NotNull UUID userId,
            TravelMode travelMode,
            boolean locationConsent
    ) {
    }

    public record InviteResponse(
            UUID id,
            UUID appointmentId,
            String appointmentTitle,
            String placeName,
            OffsetDateTime scheduledAt,
            String memo,
            String inviterNickname,
            String inviterWaytId,
            String inviterAvatarUrl,
            InviteType type,
            InviteStatus status,
            String token,
            String url,
            String targetWaytId,
            String targetNickname,
            String targetAvatarUrl,
            OffsetDateTime createdAt
    ) {
    }

    public record ReceivedInviteResponse(
            UUID id,
            UUID appointmentId,
            String appointmentTitle,
            String placeName,
            OffsetDateTime scheduledAt,
            InviteStatus status,
            String token,
            String inviterNickname,
            String inviterWaytId,
            String inviterAvatarUrl,
            OffsetDateTime createdAt
    ) {
    }
}
