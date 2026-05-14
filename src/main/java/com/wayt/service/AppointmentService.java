package com.wayt.service;

import com.wayt.domain.Appointment;
import com.wayt.domain.AppointmentCompletionReason;
import com.wayt.domain.ArrivalRecord;
import com.wayt.domain.ArrivalSource;
import com.wayt.domain.LocationSample;
import com.wayt.domain.Participant;
import com.wayt.domain.ParticipantMembershipStatus;
import com.wayt.domain.ParticipantRole;
import com.wayt.domain.ParticipantStatus;
import com.wayt.domain.Punctuality;
import com.wayt.domain.StatusLog;
import com.wayt.domain.TravelMode;
import com.wayt.domain.UserAccount;
import com.wayt.dto.AppointmentDtos;
import com.wayt.notifications.NotificationJobService;
import com.wayt.notifications.NotificationType;
import com.wayt.repository.AppointmentRepository;
import com.wayt.repository.ArrivalRecordRepository;
import com.wayt.repository.LocationSampleRepository;
import com.wayt.repository.ParticipantRepository;
import com.wayt.repository.StatusLogRepository;
import com.wayt.repository.UserAccountRepository;
import com.wayt.support.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class AppointmentService {
    private enum AppointmentListScope {
        UPCOMING,
        HISTORY,
        ALL
    }

    private static final int DEFAULT_SHARE_OFFSET_MINUTES = 60;
    private static final int DEFAULT_RADIUS_METERS = 20;
    private static final int DEFAULT_GRACE_MINUTES = 0;
    private static final double MAX_GPS_ACCURACY_FOR_ARRIVAL = 80.0;
    private static final Duration STARTED_ETA_REFRESH_COOLDOWN = Duration.ofMinutes(10);
    private static final String STARTED_MESSAGE = "출발했어요";
    private static final String NEAR_MESSAGE = "거의 다 왔어요";
    private static final String LATE_MESSAGE = "조금 늦어요";

    private final AppointmentRepository appointmentRepository;
    private final UserAccountRepository userAccountRepository;
    private final ParticipantRepository participantRepository;
    private final LocationSampleRepository locationSampleRepository;
    private final ArrivalRecordRepository arrivalRecordRepository;
    private final StatusLogRepository statusLogRepository;
    private final ResponseMapper mapper;
    private final EtaPolicyService etaPolicyService;
    private final AuthService authService;
    private final SavedPlaceService savedPlaceService;
    private final NotificationJobService notificationJobService;

    public AppointmentService(
            AppointmentRepository appointmentRepository,
            UserAccountRepository userAccountRepository,
            ParticipantRepository participantRepository,
            LocationSampleRepository locationSampleRepository,
            ArrivalRecordRepository arrivalRecordRepository,
            StatusLogRepository statusLogRepository,
            ResponseMapper mapper,
            EtaPolicyService etaPolicyService,
            AuthService authService,
            SavedPlaceService savedPlaceService,
            NotificationJobService notificationJobService
    ) {
        this.appointmentRepository = appointmentRepository;
        this.userAccountRepository = userAccountRepository;
        this.participantRepository = participantRepository;
        this.locationSampleRepository = locationSampleRepository;
        this.arrivalRecordRepository = arrivalRecordRepository;
        this.statusLogRepository = statusLogRepository;
        this.mapper = mapper;
        this.etaPolicyService = etaPolicyService;
        this.authService = authService;
        this.savedPlaceService = savedPlaceService;
        this.notificationJobService = notificationJobService;
    }

    @Transactional
    public AppointmentDtos.AppointmentResponse create(String authorization, AppointmentDtos.AppointmentCreateRequest request) {
        UserAccount host = authService.authenticatedUser(authorization);
        if (participantRepository.existsActiveUpcomingAppointmentAt(
                host,
                request.scheduledAt(),
                OffsetDateTime.now()
        )) {
            throw ApiException.conflict("이미 같은 시간에 예정된 약속이 있어요.");
        }

        Appointment appointment = appointmentRepository.save(new Appointment(
                host,
                request.title(),
                request.placeName(),
                request.latitude(),
                request.longitude(),
                request.scheduledAt(),
                valueOrDefault(request.shareStartOffsetMinutes(), DEFAULT_SHARE_OFFSET_MINUTES),
                request.penalty() == null || request.penalty().isBlank() ? "No penalty" : request.penalty(),
                valueOrDefault(request.arrivalRadiusMeters(), DEFAULT_RADIUS_METERS),
                valueOrDefault(request.graceMinutes(), DEFAULT_GRACE_MINUTES),
                request.memo()
        ));
        Participant hostParticipant = participantRepository.save(new Participant(appointment, host, ParticipantRole.HOST, true));
        hostParticipant.changeTravelMode(resolveTravelMode(request.hostTravelMode()));
        savedPlaceService.recordRecent(host, request.placeName(), request.latitude(), request.longitude());
        enqueueScheduledNotifications(appointment, host);
        return mapper.appointment(appointment, host);
    }

    @Transactional(readOnly = true)
    public List<AppointmentDtos.AppointmentResponse> upcoming(String authorization) {
        UserAccount viewer = authService.authenticatedUser(authorization);
        return appointmentRepository.findUpcomingForUser(viewer, OffsetDateTime.now())
                .stream()
                .map(appointment -> mapper.appointment(appointment, viewer))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AppointmentDtos.AppointmentResponse> list(
            String authorization,
            AppointmentDtos.AppointmentListRequest request
    ) {
        UserAccount viewer = authService.authenticatedUser(authorization);
        AppointmentListScope scope = resolveListScope(request == null ? null : request.scope());
        OffsetDateTime from = request == null ? null : request.from();
        OffsetDateTime to = request == null ? null : request.to();
        Integer limit = request == null ? null : request.limit();
        validateListRequest(from, to, limit);

        return appointmentsForScope(viewer, scope, from, to, OffsetDateTime.now())
                .stream()
                .limit(limit == null ? Long.MAX_VALUE : limit)
                .map(appointment -> mapper.appointment(appointment, viewer))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AppointmentDtos.AppointmentResponse> history(String authorization) {
        UserAccount viewer = authService.authenticatedUser(authorization);
        return appointmentRepository.findHistoryForUser(viewer, OffsetDateTime.now())
                .stream()
                .map(appointment -> mapper.appointment(appointment, viewer))
                .toList();
    }

    @Transactional(readOnly = true)
    public AppointmentDtos.AppointmentResponse get(String authorization, UUID appointmentId) {
        UserAccount viewer = authService.authenticatedUser(authorization);
        Appointment appointment = findAppointment(appointmentId);
        participantRepository.findByAppointmentAndUserAccountAndMembershipStatus(
                        appointment,
                        viewer,
                        ParticipantMembershipStatus.ACTIVE
                )
                .orElseThrow(() -> ApiException.notFound("Appointment not found"));
        return mapper.appointment(appointment, viewer);
    }

    @Transactional(readOnly = true)
    public AppointmentDtos.AppointmentResponse get(UUID appointmentId) {
        Appointment appointment = findAppointment(appointmentId);
        return mapper.appointment(appointment, defaultUser());
    }

    @Transactional
    public AppointmentDtos.AppointmentResponse updateLocation(UUID appointmentId, AppointmentDtos.LocationUpdateRequest request) {
        Appointment appointment = findAppointment(appointmentId);
        Participant participant = findParticipant(request.participantId());
        ensureActiveParticipant(appointment, participant);

        OffsetDateTime capturedAt = request.capturedAt() == null ? OffsetDateTime.now() : request.capturedAt();
        if (capturedAt.isBefore(appointment.locationShareStartsAt())) {
            participant.updateStatus(ParticipantStatus.BEFORE_LOCATION_SHARE);
            return mapper.appointment(appointment, participant.getUserAccount());
        }

        participant.consentLocation();
        participant.updateStatus(ParticipantStatus.MOVING);
        locationSampleRepository.save(new LocationSample(
                appointment,
                participant,
                request.latitude(),
                request.longitude(),
                request.accuracyMeters(),
                capturedAt
        ));

        double distance = distanceMeters(request.latitude(), request.longitude(), appointment.getLatitude(), appointment.getLongitude());
        if (request.accuracyMeters() <= MAX_GPS_ACCURACY_FOR_ARRIVAL
                && distance <= appointment.getArrivalRadiusMeters()
                && !arrivalRecordRepository.existsByParticipant(participant)) {
            boolean recordedArrival = recordArrival(appointment, participant, ArrivalSource.AUTO, capturedAt);
            if (recordedArrival) {
                enqueueArrivalStatus(appointment, participant, participant.getUserAccount().getNickname() + "님이 도착했어요");
            }
            if (completeIfAllParticipantsArrived(appointment, capturedAt)) {
                enqueueAllArrived(appointment);
            }
        } else {
            recordEtaPolicyIfEligible(appointment, participant, capturedAt);
            if (looksLate(appointment, capturedAt)) {
                if (participant.getStatus() != ParticipantStatus.LIKELY_LATE) {
                    participant.updateStatus(ParticipantStatus.LIKELY_LATE);
                    enqueueArrivalStatus(appointment, participant, participant.getUserAccount().getNickname() + "님이 늦을 것 같아요");
                }
            }
        }

        return mapper.appointment(appointment, participant.getUserAccount());
    }

    @Transactional
    public AppointmentDtos.AppointmentResponse markLocationUnavailable(
            String authorization,
            UUID appointmentId,
            AppointmentDtos.LocationUnavailableRequest request
    ) {
        UserAccount actor = authService.authenticatedUser(authorization);
        Appointment appointment = findAppointment(appointmentId);
        ensureAppointmentOpen(appointment);
        Participant participant = findParticipant(request.participantId());
        ensureActiveParticipant(appointment, participant);
        if (!participant.getUserAccount().getId().equals(actor.getId())) {
            throw ApiException.notFound("Participant not found");
        }

        participant.revokeLocationConsent();
        if (OffsetDateTime.now().isBefore(appointment.locationShareStartsAt())) {
            participant.updateStatus(ParticipantStatus.BEFORE_LOCATION_SHARE);
        } else if (participant.getStatus() != ParticipantStatus.ARRIVED
                && participant.getStatus() != ParticipantStatus.LATE_CONFIRMED) {
            participant.updateStatus(ParticipantStatus.LOCATION_OFF);
        }

        return mapper.appointment(appointment, actor);
    }

    @Transactional
    public AppointmentDtos.AppointmentResponse manualArrival(UUID appointmentId, AppointmentDtos.ManualArrivalRequest request) {
        Appointment appointment = findAppointment(appointmentId);
        Participant participant = findParticipant(request.participantId());
        ensureActiveParticipant(appointment, participant);
        OffsetDateTime arrivedAt = request.arrivedAt() == null ? OffsetDateTime.now() : request.arrivedAt();
        boolean recordedArrival = recordArrival(appointment, participant, ArrivalSource.MANUAL, arrivedAt);
        if (recordedArrival) {
            enqueueArrivalStatus(appointment, participant, participant.getUserAccount().getNickname() + "님이 직접 도착 처리했어요");
        }
        if (completeIfAllParticipantsArrived(appointment, arrivedAt)) {
            enqueueAllArrived(appointment);
        }
        statusLogRepository.save(new StatusLog(appointment, participant, "도착했어요"));
        return mapper.appointment(appointment, participant.getUserAccount());
    }

    @Transactional
    public AppointmentDtos.StatusLogResponse addStatusLog(UUID appointmentId, AppointmentDtos.StatusLogCreateRequest request) {
        Appointment appointment = findAppointment(appointmentId);
        Participant participant = findParticipant(request.participantId());
        ensureActiveParticipant(appointment, participant);
        AppointmentDtos.StatusLogAction action = resolveStatusLogAction(request);
        String message = messageFor(action);

        if (action == AppointmentDtos.StatusLogAction.STARTED) {
            if (statusLogRepository.existsByParticipantAndMessage(participant, STARTED_MESSAGE)) {
                throw ApiException.badRequest("이미 출발 상태를 공유했어요.");
            }
            participant.updateStatus(ParticipantStatus.MOVING);
            refreshEtaForStartedIfEligible(participant, OffsetDateTime.now());
        }

        StatusLog log = statusLogRepository.save(new StatusLog(appointment, participant, message));
        enqueueStatusLogNotification(appointment, participant, action);
        return mapper.statusLog(log);
    }

    @Transactional
    public AppointmentDtos.AppointmentResponse updateTravelMode(
            String authorization,
            UUID appointmentId,
            AppointmentDtos.ParticipantTravelModeUpdateRequest request
    ) {
        UserAccount actor = authService.authenticatedUser(authorization);
        Appointment appointment = findAppointment(appointmentId);
        Participant participant = findParticipant(request.participantId());
        ensureActiveParticipant(appointment, participant);
        if (!participant.getUserAccount().getId().equals(actor.getId())) {
            throw ApiException.notFound("Participant not found");
        }
        TravelMode nextMode = resolveTravelMode(request.travelMode());
        boolean changed = participant.getTravelMode() != nextMode;
        participant.changeTravelMode(nextMode);
        if (changed && !OffsetDateTime.now().isBefore(appointment.locationShareStartsAt())) {
            recordEtaPolicyIfEligible(appointment, participant, OffsetDateTime.now(), true);
        }
        return mapper.appointment(appointment, actor);
    }

    @Transactional
    public AppointmentDtos.AppointmentResponse updateManualEta(
            String authorization,
            UUID appointmentId,
            AppointmentDtos.ParticipantManualEtaUpdateRequest request
    ) {
        UserAccount actor = authService.authenticatedUser(authorization);
        Appointment appointment = findAppointment(appointmentId);
        ensureAppointmentOpen(appointment);
        Participant participant = findParticipant(request.participantId());
        ensureActiveParticipant(appointment, participant);
        if (!participant.getUserAccount().getId().equals(actor.getId())) {
            throw ApiException.notFound("Participant not found");
        }

        participant.updateManualEta(request.estimatedArrivalAt(), OffsetDateTime.now());
        updateStatusFromManualEta(appointment, participant, request.estimatedArrivalAt());
        return mapper.appointment(appointment, actor);
    }

    @Transactional
    public AppointmentDtos.AppointmentResponse leave(String authorization, UUID appointmentId) {
        UserAccount user = authService.authenticatedUser(authorization);
        Appointment appointment = findAppointment(appointmentId);
        ensureAppointmentOpen(appointment);
        Participant participant = participantRepository.findByAppointmentAndUserAccountAndMembershipStatus(
                        appointment,
                        user,
                        ParticipantMembershipStatus.ACTIVE
                )
                .orElseThrow(() -> ApiException.notFound("Appointment not found"));

        participant.leave(OffsetDateTime.now());
        transferHostIfNeeded(appointment, participant);

        return mapper.appointment(appointment, user);
    }

    @Transactional
    public AppointmentDtos.AppointmentResponse removeParticipant(String authorization, UUID appointmentId, UUID participantId) {
        UserAccount actor = authService.authenticatedUser(authorization);
        Appointment appointment = findAppointment(appointmentId);
        ensureAppointmentOpen(appointment);
        Participant actorParticipant = participantRepository.findByAppointmentAndUserAccountAndMembershipStatus(
                        appointment,
                        actor,
                        ParticipantMembershipStatus.ACTIVE
                )
                .orElseThrow(() -> ApiException.notFound("Appointment not found"));
        if (actorParticipant.getRole() != ParticipantRole.HOST) {
            throw ApiException.badRequest("방장만 참가자를 삭제할 수 있어요.");
        }

        Participant target = findParticipant(participantId);
        ensureActiveParticipant(appointment, target);
        if (target.getId().equals(actorParticipant.getId())) {
            throw ApiException.badRequest("내가 나가려면 나가기를 사용해 주세요.");
        }

        target.remove(actor, OffsetDateTime.now());
        transferHostIfNeeded(appointment, target);

        return mapper.appointment(appointment, actor);
    }

    public Appointment findAppointment(UUID appointmentId) {
        return appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> ApiException.notFound("Appointment not found"));
    }

    public Participant findParticipant(UUID participantId) {
        return participantRepository.findById(participantId)
                .orElseThrow(() -> ApiException.notFound("Participant not found"));
    }

    public UserAccount defaultUser() {
        return userAccountRepository.findByWaytId("@wayt_me")
                .orElseGet(() -> userAccountRepository.findAll().stream()
                        .findFirst()
                        .orElseThrow(() -> ApiException.notFound("No user exists yet")));
    }

    private UserAccount findUserByWaytId(String waytId) {
        return userAccountRepository.findByWaytId(waytId)
                .orElseThrow(() -> ApiException.notFound("User not found: " + waytId));
    }

    private void recordEtaPolicyIfEligible(Appointment appointment, Participant participant, OffsetDateTime now) {
        recordEtaPolicyIfEligible(appointment, participant, now, false);
    }

    private void recordEtaPolicyIfEligible(Appointment appointment, Participant participant, OffsetDateTime now, boolean travelModeChanged) {
        boolean nearLateBoundary = Math.abs(Duration.between(now, appointment.getScheduledAt().plusMinutes(appointment.getGraceMinutes())).toMinutes()) <= 10;
        if (!etaPolicyService.shouldCallEtaApi(participant, travelModeChanged, nearLateBoundary, now)) {
            return;
        }

        var policy = etaPolicyService.policyFor(participant);
        participant.recordEtaCalculation(policy, now, etaPolicyService.nextEligibleAt(participant, now));
    }

    private TravelMode resolveTravelMode(TravelMode travelMode) {
        return travelMode == null ? TravelMode.UNKNOWN : travelMode;
    }

    private void updateStatusFromManualEta(
            Appointment appointment,
            Participant participant,
            OffsetDateTime estimatedArrivalAt
    ) {
        if (estimatedArrivalAt == null
                || participant.getStatus() == ParticipantStatus.ARRIVED
                || participant.getStatus() == ParticipantStatus.LATE_CONFIRMED) {
            return;
        }

        OffsetDateTime arrivalDeadline = appointment.getScheduledAt().plusMinutes(appointment.getGraceMinutes());
        if (estimatedArrivalAt.isAfter(arrivalDeadline)) {
            participant.updateStatus(ParticipantStatus.LIKELY_LATE);
        } else if (participant.getStatus() == ParticipantStatus.LIKELY_LATE) {
            participant.updateStatus(ParticipantStatus.MOVING);
        }
    }

    private AppointmentListScope resolveListScope(String scope) {
        if (scope == null || scope.isBlank()) {
            return AppointmentListScope.UPCOMING;
        }
        try {
            return AppointmentListScope.valueOf(scope.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw ApiException.badRequest("지원하지 않는 약속 조회 범위예요.");
        }
    }

    private void validateListRequest(OffsetDateTime from, OffsetDateTime to, Integer limit) {
        if (from != null && to != null && !from.isBefore(to)) {
            throw ApiException.badRequest("조회 시작 시각은 종료 시각보다 이전이어야 해요.");
        }
        if (limit != null && limit < 1) {
            throw ApiException.badRequest("조회 개수는 1 이상이어야 해요.");
        }
    }

    private List<Appointment> appointmentsForScope(
            UserAccount viewer,
            AppointmentListScope scope,
            OffsetDateTime from,
            OffsetDateTime to,
            OffsetDateTime now
    ) {
        return switch (scope) {
            case ALL -> filterByScheduledRange(appointmentRepository.findForUser(viewer), from, to);
            case UPCOMING -> filterByScheduledRange(appointmentRepository.findUpcomingForUser(viewer, now), from, to);
            case HISTORY -> filterByScheduledRange(appointmentRepository.findHistoryForUser(viewer, now), from, to);
        };
    }

    private List<Appointment> filterByScheduledRange(List<Appointment> appointments, OffsetDateTime from, OffsetDateTime to) {
        if (from == null && to == null) {
            return appointments;
        }
        return appointments.stream()
                .filter(appointment -> from == null || !appointment.getScheduledAt().isBefore(from))
                .filter(appointment -> to == null || appointment.getScheduledAt().isBefore(to))
                .toList();
    }

    private boolean recordArrival(Appointment appointment, Participant participant, ArrivalSource source, OffsetDateTime arrivedAt) {
        if (arrivalRecordRepository.findByParticipant(participant).isPresent()) {
            return false;
        }
        long lateMinutes = Math.max(0, Duration.between(appointment.getScheduledAt().plusMinutes(appointment.getGraceMinutes()), arrivedAt).toMinutes());
        Punctuality punctuality = lateMinutes > 0 ? Punctuality.LATE : Punctuality.ON_TIME;
        participant.updateStatus(punctuality == Punctuality.LATE ? ParticipantStatus.LATE_CONFIRMED : ParticipantStatus.ARRIVED);
        arrivalRecordRepository.save(new ArrivalRecord(appointment, participant, source, arrivedAt, punctuality, lateMinutes));
        return true;
    }

    private boolean completeIfAllParticipantsArrived(Appointment appointment, OffsetDateTime completedAt) {
        if (appointment.isCompleted()) {
            return false;
        }

        List<Participant> participants = participantRepository.findByAppointmentAndMembershipStatusOrderByJoinedAtAsc(
                appointment,
                ParticipantMembershipStatus.ACTIVE
        );
        if (participants.isEmpty()) {
            return false;
        }

        boolean allArrived = participants.stream().allMatch(arrivalRecordRepository::existsByParticipant);
        if (allArrived) {
            appointment.complete(AppointmentCompletionReason.ALL_ARRIVED, completedAt);
            return true;
        }
        return false;
    }

    private void enqueueScheduledNotifications(Appointment appointment, UserAccount user) {
        notificationJobService.enqueueForUser(
                user,
                appointment,
                NotificationType.LOCATION_SHARE_START,
                "location-share-start:" + appointment.getId() + ":" + user.getId(),
                "위치 공유가 시작돼요",
                appointment.getTitle(),
                notificationJobService.appointmentData(appointment, "/appointments/" + appointment.getId()),
                appointment.locationShareStartsAt()
        );
        notificationJobService.enqueueForUser(
                user,
                appointment,
                NotificationType.APPOINTMENT_REMINDER,
                "appointment-reminder-1h:" + appointment.getId() + ":" + user.getId(),
                "약속 1시간 전이에요",
                appointment.getTitle(),
                notificationJobService.appointmentData(appointment, "/appointments/" + appointment.getId()),
                appointment.getScheduledAt().minusHours(1)
        );
    }

    private void enqueueArrivalStatus(Appointment appointment, Participant actor, String body) {
        notificationJobService.enqueueForParticipantsExcept(
                appointment,
                actor.getUserAccount(),
                NotificationType.ARRIVAL_STATUS,
                "arrival-status:" + appointment.getId() + ":" + actor.getId() + ":" + actor.getStatus(),
                "도착 상태가 바뀌었어요",
                body,
                notificationJobService.appointmentData(appointment, "/appointments/" + appointment.getId())
        );
    }

    private void enqueueAllArrived(Appointment appointment) {
        notificationJobService.enqueueForParticipantsExcept(
                appointment,
                null,
                NotificationType.ALL_ARRIVED,
                "all-arrived:" + appointment.getId(),
                "모두 도착했어요",
                appointment.getTitle(),
                notificationJobService.appointmentData(appointment, "/appointments/" + appointment.getId())
        );
    }

    private void enqueueStatusLogNotification(Appointment appointment, Participant actor, AppointmentDtos.StatusLogAction action) {
        NotificationType type = action == AppointmentDtos.StatusLogAction.STARTED
                ? NotificationType.DEPARTURE_STARTED
                : NotificationType.ARRIVAL_STATUS;
        notificationJobService.enqueueForParticipantsExcept(
                appointment,
                actor.getUserAccount(),
                type,
                type.apiId() + ":" + appointment.getId() + ":" + actor.getId() + ":" + action,
                "상태가 공유됐어요",
                actor.getUserAccount().getNickname() + "님 상태가 바뀌었어요",
                notificationJobService.appointmentData(appointment, "/appointments/" + appointment.getId())
        );
    }

    private AppointmentDtos.StatusLogAction resolveStatusLogAction(AppointmentDtos.StatusLogCreateRequest request) {
        if (request.action() != null) {
            return request.action();
        }
        if (request.message() == null || request.message().isBlank()) {
            throw ApiException.badRequest("상태 액션을 선택해 주세요.");
        }
        String normalized = request.message().toLowerCase();
        if (normalized.contains("늦") || normalized.contains("late")) {
            return AppointmentDtos.StatusLogAction.LATE;
        }
        if (normalized.contains("거의") || normalized.contains("near")) {
            return AppointmentDtos.StatusLogAction.NEAR;
        }
        if (normalized.contains("출발") || normalized.contains("start") || normalized.contains("depart")) {
            return AppointmentDtos.StatusLogAction.STARTED;
        }
        throw ApiException.badRequest("지원하지 않는 상태 메시지입니다.");
    }

    private String messageFor(AppointmentDtos.StatusLogAction action) {
        return switch (action) {
            case STARTED -> STARTED_MESSAGE;
            case NEAR -> NEAR_MESSAGE;
            case LATE -> LATE_MESSAGE;
        };
    }

    private void refreshEtaForStartedIfEligible(Participant participant, OffsetDateTime now) {
        OffsetDateTime etaCalculatedAt = participant.getEtaCalculatedAt();
        if (etaCalculatedAt == null || now.isBefore(etaCalculatedAt.plus(STARTED_ETA_REFRESH_COOLDOWN))) {
            return;
        }

        var policy = etaPolicyService.policyFor(participant);
        participant.recordEtaCalculation(policy, now, etaPolicyService.nextEligibleAt(participant, now));
    }

    private boolean looksLate(Appointment appointment, OffsetDateTime now) {
        return now.isAfter(appointment.getScheduledAt().plusMinutes(appointment.getGraceMinutes()));
    }

    private void ensureAppointmentOpen(Appointment appointment) {
        if (appointment.isCompleted()) {
            throw ApiException.badRequest("완료된 약속에서는 변경할 수 없어요.");
        }
    }

    private void ensureActiveParticipant(Appointment appointment, Participant participant) {
        if (!participant.getAppointment().getId().equals(appointment.getId()) || !participant.isActiveMembership()) {
            throw ApiException.notFound("Participant not found");
        }
    }

    private void transferHostIfNeeded(Appointment appointment, Participant departingParticipant) {
        if (departingParticipant.getRole() != ParticipantRole.HOST) {
            return;
        }

        participantRepository.findByAppointmentAndMembershipStatusOrderByJoinedAtAsc(
                        appointment,
                        ParticipantMembershipStatus.ACTIVE
                )
                .stream()
                .filter(participant -> !participant.getId().equals(departingParticipant.getId()))
                .findFirst()
                .ifPresentOrElse(nextHost -> {
                    departingParticipant.changeRole(ParticipantRole.PARTICIPANT);
                    nextHost.changeRole(ParticipantRole.HOST);
                    appointment.transferHost(nextHost.getUserAccount());
                }, () -> departingParticipant.changeRole(ParticipantRole.PARTICIPANT));
    }

    private int valueOrDefault(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double earthRadius = 6_371_000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadius * c;
    }
}
