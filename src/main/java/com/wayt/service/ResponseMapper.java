package com.wayt.service;

import com.wayt.domain.Appointment;
import com.wayt.domain.ArrivalRecord;
import com.wayt.domain.LocationSample;
import com.wayt.domain.Participant;
import com.wayt.domain.ParticipantMembershipStatus;
import com.wayt.domain.StatusLog;
import com.wayt.domain.TravelMode;
import com.wayt.domain.UserAccount;
import com.wayt.dto.AppointmentDtos;
import com.wayt.dto.UserResponse;
import com.wayt.repository.ArrivalRecordRepository;
import com.wayt.repository.LocationSampleRepository;
import com.wayt.repository.ParticipantRepository;
import com.wayt.repository.StatusLogRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
public class ResponseMapper {
    private static final String UPLOADED_AVATAR_PATH = "/api/uploads/avatars/";

    private static final String STARTED_MESSAGE = "출발했어요";
    private static final Set<String> STATUS_BUTTON_MESSAGES = Set.of(
            STARTED_MESSAGE,
            "거의 다 왔어요",
            "조금 늦어요",
            "도착했어요"
    );

    private final ParticipantRepository participantRepository;
    private final ArrivalRecordRepository arrivalRecordRepository;
    private final LocationSampleRepository locationSampleRepository;
    private final StatusLogRepository statusLogRepository;

    @Value("${wayt.app.public-base-url}")
    private String publicBaseUrl;

    public ResponseMapper(
            ParticipantRepository participantRepository,
            ArrivalRecordRepository arrivalRecordRepository,
            LocationSampleRepository locationSampleRepository,
            StatusLogRepository statusLogRepository
    ) {
        this.participantRepository = participantRepository;
        this.arrivalRecordRepository = arrivalRecordRepository;
        this.locationSampleRepository = locationSampleRepository;
        this.statusLogRepository = statusLogRepository;
    }

    public UserResponse user(UserAccount user) {
        return new UserResponse(
                user.getId(),
                user.getWaytId(),
                user.getNickname(),
                publicAvatarUrl(user.getAvatarUrl()),
                user.getSubscriptionTier(),
                user.getDefaultTravelMode(),
                user.isTravelModeOnboardingCompleted()
        );
    }

    public AppointmentDtos.AppointmentResponse appointment(Appointment appointment, UserAccount viewer) {
        List<AppointmentDtos.ParticipantResponse> participants = participantRepository
                .findByAppointmentAndMembershipStatusOrderByJoinedAtAsc(
                        appointment,
                        ParticipantMembershipStatus.ACTIVE
                )
                .stream()
                .map(participant -> participant(appointment, participant))
                .toList();

        List<AppointmentDtos.StatusLogResponse> logs = statusLogRepository
                .findTop20ByAppointmentAndMessageInOrderByCreatedAtDesc(appointment, STATUS_BUTTON_MESSAGES)
                .stream()
                .sorted(Comparator.comparing(StatusLog::getCreatedAt))
                .map(this::statusLog)
                .toList();

        var myRole = participantRepository.findByAppointmentAndUserAccountAndMembershipStatus(
                        appointment,
                        viewer,
                        ParticipantMembershipStatus.ACTIVE
                )
                .map(Participant::getRole)
                .orElse(null);

        return new AppointmentDtos.AppointmentResponse(
                appointment.getId(),
                appointment.getTitle(),
                appointment.getPlaceName(),
                appointment.getLatitude(),
                appointment.getLongitude(),
                appointment.getScheduledAt(),
                appointment.locationShareStartsAt(),
                appointment.getShareStartOffsetMinutes(),
                appointment.getPenalty(),
                appointment.getArrivalRadiusMeters(),
                appointment.getGraceMinutes(),
                appointment.getMemo(),
                appointment.getCompletedAt(),
                appointment.getCompletionReason(),
                myRole,
                participants,
                logs
        );
    }

    public AppointmentDtos.ParticipantResponse participant(Appointment appointment, Participant participant) {
        Optional<ArrivalRecord> arrival = arrivalRecordRepository.findByParticipant(participant);
        Optional<LocationSample> latestLocation = locationSampleRepository.findFirstByParticipantOrderByCapturedAtDesc(participant);
        Optional<StatusLog> startedLog = statusLogRepository.findFirstByParticipantAndMessageOrderByCreatedAtAsc(participant, STARTED_MESSAGE);
        Integer etaMinutes = estimateMinutes(appointment);
        String etaLabel = arrival.isPresent() ? "ARRIVED" : etaMinutes + " min left";
        TravelMode etaModeUsed = participant.getTravelMode().resolved();
        UserAccount removedBy = participant.getRemovedBy();

        return new AppointmentDtos.ParticipantResponse(
                participant.getId(),
                participant.getUserAccount().getId(),
                participant.getUserAccount().getNickname(),
                participant.getUserAccount().getWaytId(),
                publicAvatarUrl(participant.getUserAccount().getAvatarUrl()),
                participant.getRole(),
                participant.getMembershipStatus(),
                participant.getStatus(),
                participant.getTravelMode(),
                etaModeUsed,
                participant.isLocationConsent(),
                participant.getLeftAt(),
                participant.getRemovedAt(),
                removedBy == null ? null : removedBy.getId(),
                removedBy == null ? null : removedBy.getNickname(),
                removedBy == null ? null : removedBy.getWaytId(),
                arrival.isPresent() ? null : etaMinutes,
                etaLabel,
                participant.getEtaRefreshPolicy(),
                participant.getEtaCalculatedAt(),
                participant.getEtaNextEligibleAt(),
                participant.getEtaApiCallCount(),
                latestLocation.map(LocationSample::getLatitude).orElse(null),
                latestLocation.map(LocationSample::getLongitude).orElse(null),
                latestLocation.map(LocationSample::getAccuracyMeters).orElse(null),
                latestLocation.map(LocationSample::getCapturedAt).orElse(null),
                startedLog.map(StatusLog::getCreatedAt).orElse(null),
                arrival.map(ArrivalRecord::getArrivedAt).orElse(null),
                arrival.map(ArrivalRecord::getPunctuality).orElse(null),
                arrival.map(ArrivalRecord::getSource).orElse(null),
                arrival.map(ArrivalRecord::getLateMinutes).orElse(null),
                participant.getManualEstimatedArrivalAt(),
                participant.getManualEtaUpdatedAt()
        );
    }

    public AppointmentDtos.StatusLogResponse statusLog(StatusLog log) {
        return new AppointmentDtos.StatusLogResponse(
                log.getId(),
                log.getParticipant().getId(),
                log.getParticipant().getUserAccount().getNickname(),
                log.getMessage(),
                log.getCreatedAt()
        );
    }

    private Integer estimateMinutes(Appointment appointment) {
        long minutes = Duration.between(OffsetDateTime.now(), appointment.getScheduledAt()).toMinutes();
        return Math.toIntExact(Math.max(1, Math.min(90, minutes)));
    }

    private String publicAvatarUrl(String avatarUrl) {
        if (avatarUrl == null || avatarUrl.isBlank()) {
            return null;
        }

        int uploadPathIndex = avatarUrl.indexOf(UPLOADED_AVATAR_PATH);
        if (uploadPathIndex < 0) {
            return avatarUrl;
        }

        return trimTrailingSlash(publicBaseUrl) + avatarUrl.substring(uploadPathIndex);
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
