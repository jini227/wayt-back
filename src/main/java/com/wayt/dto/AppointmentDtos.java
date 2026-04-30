package com.wayt.dto;

import com.wayt.domain.ArrivalSource;
import com.wayt.domain.AppointmentCompletionReason;
import com.wayt.domain.EtaRefreshPolicy;
import com.wayt.domain.ParticipantMembershipStatus;
import com.wayt.domain.ParticipantRole;
import com.wayt.domain.ParticipantStatus;
import com.wayt.domain.Punctuality;
import com.wayt.domain.TravelMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class AppointmentDtos {
    private AppointmentDtos() {
    }

    public record AppointmentCreateRequest(
            String hostWaytId,
            @NotBlank @Size(max = AppointmentConstraints.TITLE_MAX_LENGTH, message = "약속 이름은 20자 이하로 입력해 주세요.") String title,
            @NotBlank @Size(max = AppointmentConstraints.PLACE_NAME_MAX_LENGTH, message = "장소 이름은 200자 이하로 입력해 주세요.") String placeName,
            double latitude,
            double longitude,
            @NotNull OffsetDateTime scheduledAt,
            Integer shareStartOffsetMinutes,
            @Size(max = AppointmentConstraints.PENALTY_MAX_LENGTH, message = "벌칙은 30자 이하로 입력해 주세요.") String penalty,
            Integer arrivalRadiusMeters,
            Integer graceMinutes,
            @Size(max = AppointmentConstraints.MEMO_MAX_LENGTH, message = "메모는 150자 이하로 입력해 주세요.") String memo,
            TravelMode hostTravelMode
    ) {
        public AppointmentCreateRequest(
                String hostWaytId,
                String title,
                String placeName,
                double latitude,
                double longitude,
                OffsetDateTime scheduledAt,
                Integer shareStartOffsetMinutes,
                String penalty,
                Integer arrivalRadiusMeters,
                Integer graceMinutes,
                String memo
        ) {
            this(
                    hostWaytId,
                    title,
                    placeName,
                    latitude,
                    longitude,
                    scheduledAt,
                    shareStartOffsetMinutes,
                    penalty,
                    arrivalRadiusMeters,
                    graceMinutes,
                    memo,
                    null
            );
        }
    }

    public record AppointmentListRequest(
            String scope,
            OffsetDateTime from,
            OffsetDateTime to,
            Integer limit
    ) {
    }

    public record AppointmentResponse(
            UUID id,
            String title,
            String placeName,
            double latitude,
            double longitude,
            OffsetDateTime scheduledAt,
            OffsetDateTime locationShareStartsAt,
            int shareStartOffsetMinutes,
            String penalty,
            int arrivalRadiusMeters,
            int graceMinutes,
            String memo,
            OffsetDateTime completedAt,
            AppointmentCompletionReason completionReason,
            ParticipantRole myRole,
            List<ParticipantResponse> participants,
            List<StatusLogResponse> statusLogs
    ) {
    }

    public record ParticipantResponse(
            UUID id,
            UUID userId,
            String name,
            String waytId,
            String avatarUrl,
            ParticipantRole role,
            ParticipantMembershipStatus membershipStatus,
            ParticipantStatus status,
            TravelMode travelMode,
            TravelMode etaModeUsed,
            boolean locationConsent,
            OffsetDateTime leftAt,
            OffsetDateTime removedAt,
            UUID removedByUserId,
            String removedByName,
            String removedByWaytId,
            Integer etaMinutes,
            String etaLabel,
            EtaRefreshPolicy etaRefreshPolicy,
            OffsetDateTime etaCalculatedAt,
            OffsetDateTime etaNextEligibleAt,
            int etaApiCallCount,
            Double latestLatitude,
            Double latestLongitude,
            Double latestAccuracyMeters,
            OffsetDateTime latestLocationCapturedAt,
            OffsetDateTime startedAt,
            OffsetDateTime arrivedAt,
            Punctuality punctuality,
            ArrivalSource arrivalSource,
            Long lateMinutes,
            OffsetDateTime manualEstimatedArrivalAt,
            OffsetDateTime manualEtaUpdatedAt
    ) {
    }

    public record LocationUpdateRequest(
            @NotNull UUID participantId,
            double latitude,
            double longitude,
            @Positive double accuracyMeters,
            OffsetDateTime capturedAt
    ) {
    }

    public record LocationUnavailableRequest(
            @NotNull UUID participantId
    ) {
    }

    public record ManualArrivalRequest(
            @NotNull UUID participantId,
            OffsetDateTime arrivedAt
    ) {
    }

    public enum StatusLogAction {
        STARTED,
        NEAR,
        LATE
    }

    public record StatusLogCreateRequest(
            @NotNull UUID participantId,
            StatusLogAction action,
            String message
    ) {
    }

    public record ParticipantTravelModeUpdateRequest(
            @NotNull UUID participantId,
            @NotNull TravelMode travelMode
    ) {
    }

    public record ParticipantManualEtaUpdateRequest(
            @NotNull UUID participantId,
            OffsetDateTime estimatedArrivalAt
    ) {
    }

    public record StatusLogResponse(
            UUID id,
            UUID participantId,
            String participantName,
            String message,
            OffsetDateTime createdAt
    ) {
    }
}
