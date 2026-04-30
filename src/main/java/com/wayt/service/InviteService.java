package com.wayt.service;

import com.wayt.domain.Appointment;
import com.wayt.domain.Invite;
import com.wayt.domain.InviteStatus;
import com.wayt.domain.InviteType;
import com.wayt.domain.Participant;
import com.wayt.domain.ParticipantMembershipStatus;
import com.wayt.domain.ParticipantRole;
import com.wayt.domain.TravelMode;
import com.wayt.domain.UserAccount;
import com.wayt.dto.InviteDtos;
import com.wayt.notifications.NotificationJobService;
import com.wayt.notifications.NotificationType;
import com.wayt.repository.AppointmentRepository;
import com.wayt.repository.InviteRepository;
import com.wayt.repository.ParticipantRepository;
import com.wayt.repository.UserAccountRepository;
import com.wayt.support.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class InviteService {
    private final AppointmentRepository appointmentRepository;
    private final UserAccountRepository userAccountRepository;
    private final InviteRepository inviteRepository;
    private final ParticipantRepository participantRepository;
    private final AuthService authService;
    private final NotificationJobService notificationJobService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${wayt.app.public-base-url}")
    private String publicBaseUrl;

    public InviteService(
            AppointmentRepository appointmentRepository,
            UserAccountRepository userAccountRepository,
            InviteRepository inviteRepository,
            ParticipantRepository participantRepository,
            AuthService authService,
            NotificationJobService notificationJobService
    ) {
        this.appointmentRepository = appointmentRepository;
        this.userAccountRepository = userAccountRepository;
        this.inviteRepository = inviteRepository;
        this.participantRepository = participantRepository;
        this.authService = authService;
        this.notificationJobService = notificationJobService;
    }

    @Transactional
    public InviteDtos.InviteResponse createLinkInvite(UUID appointmentId) {
        Appointment appointment = findAppointment(appointmentId);
        Invite invite = inviteRepository.save(new Invite(
                appointment,
                appointment.getHost(),
                InviteType.LINK,
                randomToken(),
                null,
                null
        ));
        return response(invite);
    }

    @Transactional
    public InviteDtos.InviteResponse createWaytIdInvite(UUID appointmentId, InviteDtos.InviteByWaytIdRequest request) {
        Appointment appointment = findAppointment(appointmentId);
        UserAccount inviter = userAccountRepository.findByWaytId(request.inviterWaytId())
                .orElseThrow(() -> ApiException.notFound("Inviter not found"));
        UserAccount invitee = userAccountRepository.findByWaytId(request.targetWaytId())
                .orElseThrow(() -> ApiException.notFound("Invitee not found"));

        Invite invite = inviteRepository.save(new Invite(
                appointment,
                inviter,
                InviteType.WAYT_ID,
                randomToken(),
                invitee,
                invitee.getWaytId()
        ));
        notificationJobService.enqueueForUser(
                invitee,
                appointment,
                NotificationType.APPOINTMENT_INVITE,
                "appointment-invite:" + appointment.getId() + ":" + invitee.getId() + ":" + invite.getId(),
                "약속 초대가 도착했어요",
                appointment.getTitle(),
                inviteAcceptanceData(appointment, invite),
                null
        );
        return response(invite);
    }

    @Transactional(readOnly = true)
    public List<InviteDtos.InviteResponse> appointmentInvites(String authorization, UUID appointmentId) {
        Appointment appointment = findAppointment(appointmentId);
        UserAccount viewer = authService.authenticatedUser(authorization);
        if (!participantRepository.existsByAppointmentAndUserAccountAndMembershipStatus(
                appointment,
                viewer,
                ParticipantMembershipStatus.ACTIVE
        )) {
            throw ApiException.notFound("Appointment not found");
        }

        return inviteRepository.findByAppointmentOrderByCreatedAtDesc(appointment)
                .stream()
                .map(this::response)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<InviteDtos.ReceivedInviteResponse> receivedInvites(String authorization) {
        UserAccount viewer = authService.authenticatedUser(authorization);
        return inviteRepository.findByInviteeAndStatusAndAppointmentScheduledAtAfterOrderByCreatedAtDesc(
                        viewer,
                        InviteStatus.PENDING,
                        OffsetDateTime.now()
                )
                .stream()
                .map(this::receivedResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public InviteDtos.InviteResponse getByToken(String token) {
        return inviteRepository.findByToken(token)
                .map(this::response)
                .orElseThrow(() -> ApiException.notFound("Invite not found"));
    }

    @Transactional
    public InviteDtos.InviteResponse accept(UUID inviteId, InviteDtos.AcceptInviteRequest request) {
        Invite invite = inviteRepository.findById(inviteId)
                .orElseThrow(() -> ApiException.notFound("Invite not found"));
        ensurePending(invite);
        Appointment appointment = invite.getAppointment();
        if (!appointment.getScheduledAt().isAfter(OffsetDateTime.now())) {
            throw ApiException.badRequest("이미 지난 약속 초대예요.");
        }

        UserAccount user = userAccountRepository.findById(request.userId())
                .orElseThrow(() -> ApiException.notFound("User not found"));
        invite.accept(user);

        Participant participant = participantRepository.findByAppointmentAndUserAccount(appointment, user)
                .orElseGet(() -> participantRepository.save(new Participant(
                        appointment,
                        user,
                        roleForJoiningParticipant(appointment),
                        request.locationConsent()
                )));

        if (!participant.isActiveMembership()) {
            ParticipantRole role = roleForJoiningParticipant(appointment);
            participant.reactivate(role, OffsetDateTime.now());
            if (role == ParticipantRole.HOST) {
                appointment.transferHost(user);
            }
        }
        if (participant.getRole() == ParticipantRole.HOST && !appointment.getHost().getId().equals(user.getId())) {
            appointment.transferHost(user);
        }

        participant.changeTravelMode(request.travelMode() == null ? TravelMode.UNKNOWN : request.travelMode());
        if (request.locationConsent()) {
            participant.consentLocation();
        }

        enqueueParticipantScheduledNotifications(appointment, user);
        enqueueInviteResponse(invite, user.getNickname() + "님이 초대를 수락했어요");

        return response(invite);
    }

    @Transactional
    public InviteDtos.InviteResponse decline(UUID inviteId) {
        Invite invite = inviteRepository.findById(inviteId)
                .orElseThrow(() -> ApiException.notFound("Invite not found"));
        ensurePending(invite);
        invite.decline();
        String name = invite.getInvitee() == null ? "초대받은 사람" : invite.getInvitee().getNickname() + "님";
        enqueueInviteResponse(invite, name + "이 초대를 거절했어요");
        return response(invite);
    }

    @Transactional
    public InviteDtos.InviteResponse cancel(String authorization, UUID appointmentId, UUID inviteId) {
        UserAccount actor = authService.authenticatedUser(authorization);
        Appointment appointment = findAppointment(appointmentId);
        Participant actorParticipant = participantRepository.findByAppointmentAndUserAccountAndMembershipStatus(
                        appointment,
                        actor,
                        ParticipantMembershipStatus.ACTIVE
                )
                .orElseThrow(() -> ApiException.notFound("Appointment not found"));
        Invite invite = inviteRepository.findById(inviteId)
                .orElseThrow(() -> ApiException.notFound("Invite not found"));
        if (!invite.getAppointment().getId().equals(appointment.getId())) {
            throw ApiException.notFound("Invite not found");
        }
        if (invite.getType() != InviteType.WAYT_ID) {
            throw ApiException.badRequest("아이디 초대만 취소할 수 있어요.");
        }
        if (invite.getStatus() != InviteStatus.PENDING) {
            throw ApiException.badRequest("수락 대기 중인 초대만 취소할 수 있어요.");
        }
        boolean canCancel = actorParticipant.getRole() == ParticipantRole.HOST
                || invite.getInviter().getId().equals(actor.getId());
        if (!canCancel) {
            throw ApiException.badRequest("방장 또는 초대한 사람만 취소할 수 있어요.");
        }

        invite.cancel();
        enqueueInviteCancellation(invite);
        return response(invite);
    }

    private Appointment findAppointment(UUID appointmentId) {
        return appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> ApiException.notFound("Appointment not found"));
    }

    private void ensurePending(Invite invite) {
        if (invite.getStatus() == InviteStatus.PENDING) {
            return;
        }
        throw ApiException.badRequest(unavailableInviteMessage(invite.getStatus()));
    }

    private String unavailableInviteMessage(InviteStatus status) {
        return switch (status) {
            case CANCELLED -> "취소된 초대예요.";
            case ACCEPTED -> "이미 수락한 초대예요.";
            case DECLINED -> "이미 거절한 초대예요.";
            case EXPIRED -> "만료된 초대예요.";
            case PENDING -> "초대를 처리할 수 있어요.";
        };
    }

    private ParticipantRole roleForJoiningParticipant(Appointment appointment) {
        return participantRepository.findByAppointmentAndMembershipStatusOrderByJoinedAtAsc(
                appointment,
                ParticipantMembershipStatus.ACTIVE
        ).isEmpty() ? ParticipantRole.HOST : ParticipantRole.PARTICIPANT;
    }

    private InviteDtos.InviteResponse response(Invite invite) {
        Appointment appointment = invite.getAppointment();
        UserAccount inviter = invite.getInviter();
        String url = publicBaseUrl + "/invite/" + invite.getToken();
        UserAccount invitee = invite.getInvitee();
        return new InviteDtos.InviteResponse(
                invite.getId(),
                appointment.getId(),
                appointment.getTitle(),
                appointment.getPlaceName(),
                appointment.getScheduledAt(),
                appointment.getMemo(),
                inviter.getNickname(),
                inviter.getWaytId(),
                inviter.getAvatarUrl(),
                invite.getType(),
                invite.getStatus(),
                invite.getToken(),
                url,
                invite.getTargetWaytId(),
                invitee == null ? null : invitee.getNickname(),
                invitee == null ? null : invitee.getAvatarUrl(),
                invite.getCreatedAt()
        );
    }

    private InviteDtos.ReceivedInviteResponse receivedResponse(Invite invite) {
        Appointment appointment = invite.getAppointment();
        UserAccount inviter = invite.getInviter();
        return new InviteDtos.ReceivedInviteResponse(
                invite.getId(),
                appointment.getId(),
                appointment.getTitle(),
                appointment.getPlaceName(),
                appointment.getScheduledAt(),
                invite.getStatus(),
                invite.getToken(),
                inviter.getNickname(),
                inviter.getWaytId(),
                inviter.getAvatarUrl(),
                invite.getCreatedAt()
        );
    }

    private void enqueueParticipantScheduledNotifications(Appointment appointment, UserAccount user) {
        Map<String, Object> data = notificationJobService.appointmentData(appointment, "/appointments/" + appointment.getId());
        notificationJobService.enqueueForUser(
                user,
                appointment,
                NotificationType.LOCATION_SHARE_START,
                "location-share-start:" + appointment.getId() + ":" + user.getId(),
                "위치 공유가 시작돼요",
                appointment.getTitle(),
                data,
                appointment.locationShareStartsAt()
        );
        notificationJobService.enqueueForUser(
                user,
                appointment,
                NotificationType.APPOINTMENT_REMINDER,
                "appointment-reminder-1h:" + appointment.getId() + ":" + user.getId(),
                "약속 1시간 전이에요",
                appointment.getTitle(),
                data,
                appointment.getScheduledAt().minusHours(1)
        );
    }

    private void enqueueInviteResponse(Invite invite, String body) {
        Appointment appointment = invite.getAppointment();
        notificationJobService.enqueueForUser(
                appointment.getHost(),
                appointment,
                NotificationType.INVITE_RESPONSE,
                "invite-response:" + appointment.getId() + ":" + appointment.getHost().getId() + ":" + invite.getId() + ":" + invite.getStatus(),
                "초대 응답이 도착했어요",
                body,
                inviteAppointmentData(appointment, invite),
                null
        );
    }

    private void enqueueInviteCancellation(Invite invite) {
        UserAccount invitee = invite.getInvitee();
        if (invitee == null) {
            return;
        }
        Appointment appointment = invite.getAppointment();
        notificationJobService.enqueueForUser(
                invitee,
                appointment,
                NotificationType.APPOINTMENT_INVITE_CANCELLED,
                "appointment-invite-cancelled:" + appointment.getId() + ":" + invitee.getId() + ":" + invite.getId(),
                "초대가 취소됐어요",
                appointment.getTitle(),
                inviteAcceptanceData(appointment, invite),
                null
        );
    }

    private Map<String, Object> inviteAcceptanceData(Appointment appointment, Invite invite) {
        return inviteData(appointment, invite, "/invites/" + invite.getToken());
    }

    private Map<String, Object> inviteAppointmentData(Appointment appointment, Invite invite) {
        return inviteData(appointment, invite, "/appointments/" + appointment.getId());
    }

    private Map<String, Object> inviteData(Appointment appointment, Invite invite, String route) {
        Map<String, Object> data = new LinkedHashMap<>(notificationJobService.appointmentData(appointment, route));
        data.put("inviteId", invite.getId().toString());
        return data;
    }

    private String randomToken() {
        byte[] bytes = new byte[6];
        secureRandom.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes).toUpperCase();
    }
}
