package com.wayt.notifications;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wayt.domain.Appointment;
import com.wayt.domain.NotificationJob;
import com.wayt.domain.Participant;
import com.wayt.domain.ParticipantMembershipStatus;
import com.wayt.domain.PushToken;
import com.wayt.domain.UserAccount;
import com.wayt.repository.NotificationJobRepository;
import com.wayt.repository.ParticipantRepository;
import com.wayt.repository.PushTokenRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class NotificationJobService {
    private final NotificationJobRepository jobRepository;
    private final PushTokenRepository pushTokenRepository;
    private final ParticipantRepository participantRepository;
    private final NotificationPreferenceService preferenceService;
    private final ObjectMapper objectMapper;

    public NotificationJobService(
            NotificationJobRepository jobRepository,
            PushTokenRepository pushTokenRepository,
            ParticipantRepository participantRepository,
            NotificationPreferenceService preferenceService,
            ObjectMapper objectMapper
    ) {
        this.jobRepository = jobRepository;
        this.pushTokenRepository = pushTokenRepository;
        this.participantRepository = participantRepository;
        this.preferenceService = preferenceService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public int enqueueForUser(
            UserAccount recipient,
            Appointment appointment,
            NotificationType type,
            String dedupeKey,
            String title,
            String body,
            Map<String, Object> data,
            OffsetDateTime scheduledAt
    ) {
        if (recipient == null || !preferenceService.enabled(recipient, type)) {
            return 0;
        }

        int created = 0;
        for (PushToken token : pushTokenRepository.findByUserAccountAndInvalidatedAtIsNull(recipient)) {
            String eventKey = dedupeKey + ":" + token.getId();
            if (jobRepository.existsByEventKey(eventKey)) {
                continue;
            }
            try {
                jobRepository.save(NotificationJob.pendingForAppointment(
                        recipient,
                        token,
                        appointment,
                        type.apiId(),
                        eventKey,
                        title,
                        body,
                        dataJson(data),
                        scheduledAt
                ));
                created += 1;
            } catch (DataIntegrityViolationException ignored) {
                // Another scheduler/API node inserted the same event-key first.
            }
        }
        return created;
    }

    @Transactional
    public int enqueueForParticipantsExcept(
            Appointment appointment,
            UserAccount excludedUser,
            NotificationType type,
            String dedupeKey,
            String title,
            String body,
            Map<String, Object> data
    ) {
        int created = 0;
        for (Participant participant : participantRepository.findByAppointmentAndMembershipStatusOrderByJoinedAtAsc(
                appointment,
                ParticipantMembershipStatus.ACTIVE
        )) {
            UserAccount recipient = participant.getUserAccount();
            if (excludedUser != null && excludedUser.getId() != null && excludedUser.getId().equals(recipient.getId())) {
                continue;
            }
            created += enqueueForUser(recipient, appointment, type, dedupeKey + ":" + recipient.getId(), title, body, data, OffsetDateTime.now());
        }
        return created;
    }

    public Map<String, Object> appointmentData(Appointment appointment, String route) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("route", route);
        data.put("appointmentId", appointment.getId().toString());
        data.put("appointmentTitle", appointment.getTitle());
        return data;
    }

    private String dataJson(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data == null ? Map.of() : data);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Notification payload cannot be serialized", exception);
        }
    }
}
