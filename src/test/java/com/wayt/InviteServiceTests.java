package com.wayt;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wayt.domain.Appointment;
import com.wayt.domain.AuthProvider;
import com.wayt.domain.Participant;
import com.wayt.domain.ParticipantRole;
import com.wayt.domain.PushToken;
import com.wayt.domain.UserAccount;
import com.wayt.dto.InviteDtos;
import com.wayt.notifications.NotificationType;
import com.wayt.repository.AppointmentRepository;
import com.wayt.repository.NotificationJobRepository;
import com.wayt.repository.ParticipantRepository;
import com.wayt.repository.PushTokenRepository;
import com.wayt.repository.UserAccountRepository;
import com.wayt.service.InviteService;
import com.wayt.support.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.task.scheduling.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:invite-service-tests;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
class InviteServiceTests {
    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private ParticipantRepository participantRepository;

    @Autowired
    private PushTokenRepository pushTokenRepository;

    @Autowired
    private NotificationJobRepository notificationJobRepository;

    @Autowired
    private InviteService inviteService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @Transactional
    void receivedInvitesListsOnlyPendingWaytIdInvitesForAuthenticatedUser() {
        String suffix = UUID.randomUUID().toString();
        UserAccount host = saveUser("received-host-" + suffix, "@received_host_" + suffix.substring(0, 8), "received-host", null);
        UserAccount invitee = saveUser("received-invitee-" + suffix, "@received_invitee_" + suffix.substring(0, 8), "received-invitee", "https://example.com/invitee.png");
        UserAccount otherInvitee = saveUser("received-other-" + suffix, "@received_other_" + suffix.substring(0, 8), "received-other", null);
        Appointment pendingAppointment = saveAppointment(host, "Pending invite", "Suwon Station", OffsetDateTime.now().plusHours(3));
        Appointment declinedAppointment = saveAppointment(host, "Declined invite", "Gangnam Station", OffsetDateTime.now().plusHours(4));
        Appointment otherAppointment = saveAppointment(host, "Other invitee invite", "Hongdae", OffsetDateTime.now().plusHours(5));

        InviteDtos.InviteResponse pending = inviteService.createWaytIdInvite(
                pendingAppointment.getId(),
                new InviteDtos.InviteByWaytIdRequest(host.getWaytId(), invitee.getWaytId())
        );
        InviteDtos.InviteResponse declined = inviteService.createWaytIdInvite(
                declinedAppointment.getId(),
                new InviteDtos.InviteByWaytIdRequest(host.getWaytId(), invitee.getWaytId())
        );
        inviteService.decline(declined.id());
        inviteService.createWaytIdInvite(
                otherAppointment.getId(),
                new InviteDtos.InviteByWaytIdRequest(host.getWaytId(), otherInvitee.getWaytId())
        );
        inviteService.createLinkInvite(pendingAppointment.getId());

        var received = inviteService.receivedInvites(bearer(invitee));

        assertThat(received).hasSize(1);
        assertThat(received.getFirst().id()).isEqualTo(pending.id());
        assertThat(received.getFirst().token()).isEqualTo(pending.token());
        assertThat(received.getFirst().appointmentId()).isEqualTo(pendingAppointment.getId());
        assertThat(received.getFirst().appointmentTitle()).isEqualTo("Pending invite");
        assertThat(received.getFirst().placeName()).isEqualTo("Suwon Station");
        assertThat(received.getFirst().scheduledAt()).isEqualTo(pendingAppointment.getScheduledAt());
        assertThat(received.getFirst().inviterNickname()).isEqualTo("received-host");
        assertThat(received.getFirst().inviterWaytId()).isEqualTo(host.getWaytId());
        assertThat(received.getFirst().inviterAvatarUrl()).isNull();
    }

    @Test
    @Transactional
    void receivedInvitesExcludesPendingInvitesForPastAppointments() {
        String suffix = UUID.randomUUID().toString();
        UserAccount host = saveUser("past-host-" + suffix, "@past_host_" + suffix.substring(0, 8), "past-host", null);
        UserAccount invitee = saveUser("past-invitee-" + suffix, "@past_invitee_" + suffix.substring(0, 8), "past-invitee", null);
        Appointment pastAppointment = saveAppointment(host, "Past pending invite", "Suwon Station", OffsetDateTime.now().minusHours(2));
        Appointment futureAppointment = saveAppointment(host, "Future pending invite", "Gangnam Station", OffsetDateTime.now().plusHours(2));

        inviteService.createWaytIdInvite(
                pastAppointment.getId(),
                new InviteDtos.InviteByWaytIdRequest(host.getWaytId(), invitee.getWaytId())
        );
        InviteDtos.InviteResponse future = inviteService.createWaytIdInvite(
                futureAppointment.getId(),
                new InviteDtos.InviteByWaytIdRequest(host.getWaytId(), invitee.getWaytId())
        );

        var received = inviteService.receivedInvites(bearer(invitee));

        assertThat(received).hasSize(1);
        assertThat(received.getFirst().id()).isEqualTo(future.id());
        assertThat(received.getFirst().appointmentTitle()).isEqualTo("Future pending invite");
    }

    @Test
    @Transactional
    void inviteTokenResponseIncludesDetailsNeededForAcceptance() {
        String suffix = UUID.randomUUID().toString();
        UserAccount host = saveUser("detail-host-" + suffix, "@detail_host_" + suffix.substring(0, 8), "detail-host", "https://example.com/host.png");
        Appointment appointment = appointmentRepository.save(new Appointment(
                host,
                "Birthday dinner",
                "Hongdae Station Exit 9",
                37.5572,
                126.9254,
                OffsetDateTime.now().plusHours(3),
                60,
                "No penalty",
                20,
                0,
                "Bring a small gift"
        ));
        participantRepository.save(new Participant(appointment, host, ParticipantRole.HOST, true));
        InviteDtos.InviteResponse invite = inviteService.createLinkInvite(appointment.getId());

        InviteDtos.InviteResponse response = inviteService.getByToken(invite.token());

        assertThat(response.appointmentTitle()).isEqualTo("Birthday dinner");
        assertThat(response.placeName()).isEqualTo("Hongdae Station Exit 9");
        assertThat(response.scheduledAt()).isEqualTo(appointment.getScheduledAt());
        assertThat(response.memo()).isEqualTo("Bring a small gift");
        assertThat(response.inviterNickname()).isEqualTo("detail-host");
        assertThat(response.inviterWaytId()).isEqualTo(host.getWaytId());
        assertThat(response.inviterAvatarUrl()).isEqualTo("https://example.com/host.png");
    }

    @Test
    @Transactional
    void acceptingPastInviteFailsWithClearExpiredInviteMessage() {
        String suffix = UUID.randomUUID().toString();
        UserAccount host = saveUser("accept-past-host-" + suffix, "@accept_past_host_" + suffix.substring(0, 8), "accept-past-host", null);
        UserAccount invitee = saveUser("accept-past-invitee-" + suffix, "@accept_past_invitee_" + suffix.substring(0, 8), "accept-past-invitee", null);
        Appointment pastAppointment = saveAppointment(host, "Past accept invite", "Suwon Station", OffsetDateTime.now().minusMinutes(1));
        InviteDtos.InviteResponse invite = inviteService.createWaytIdInvite(
                pastAppointment.getId(),
                new InviteDtos.InviteByWaytIdRequest(host.getWaytId(), invitee.getWaytId())
        );

        assertThatThrownBy(() -> inviteService.accept(
                invite.id(),
                new InviteDtos.AcceptInviteRequest(invitee.getId(), null, true)
        )).isInstanceOf(ApiException.class)
                .hasMessage("이미 지난 약속 초대예요.")
                .satisfies(exception -> assertThat(((ApiException) exception).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @Transactional
    void cancelledInviteCannotBeAcceptedOrDeclined() {
        String suffix = UUID.randomUUID().toString();
        UserAccount host = saveUser("cancel-action-host-" + suffix, "@cancel_action_host_" + suffix.substring(0, 8), "cancel-action-host", null);
        UserAccount invitee = saveUser("cancel-action-invitee-" + suffix, "@cancel_action_invitee_" + suffix.substring(0, 8), "cancel-action-invitee", null);
        Appointment appointment = saveAppointment(host, "Cancelled action invite", "Suwon Station", OffsetDateTime.now().plusHours(2));
        InviteDtos.InviteResponse invite = inviteService.createWaytIdInvite(
                appointment.getId(),
                new InviteDtos.InviteByWaytIdRequest(host.getWaytId(), invitee.getWaytId())
        );

        inviteService.cancel(bearer(host), appointment.getId(), invite.id());

        assertThatThrownBy(() -> inviteService.accept(
                invite.id(),
                new InviteDtos.AcceptInviteRequest(invitee.getId(), null, true)
        )).isInstanceOf(ApiException.class)
                .hasMessage("취소된 초대예요.")
                .satisfies(exception -> assertThat(((ApiException) exception).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> inviteService.decline(invite.id()))
                .isInstanceOf(ApiException.class)
                .hasMessage("취소된 초대예요.")
                .satisfies(exception -> assertThat(((ApiException) exception).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));

        assertThat(inviteService.getByToken(invite.token()).status().name()).isEqualTo("CANCELLED");
        assertThat(participantRepository.findByAppointmentAndUserAccount(appointment, invitee)).isEmpty();
    }

    @Test
    @Transactional
    void waytIdInvitePushPayloadRoutesToInviteAcceptanceScreen() throws Exception {
        String suffix = UUID.randomUUID().toString();
        UserAccount host = saveUser("push-host-" + suffix, "@push_host_" + suffix.substring(0, 8), "push-host", null);
        UserAccount invitee = saveUser("push-invitee-" + suffix, "@push_invitee_" + suffix.substring(0, 8), "push-invitee", null);
        Appointment appointment = saveAppointment(host, "Push route invite", "Suwon Station", OffsetDateTime.now().plusHours(3));
        pushTokenRepository.save(new PushToken(invitee, "ExpoPushToken[" + suffix.substring(0, 8) + "]", "ios"));

        InviteDtos.InviteResponse invite = inviteService.createWaytIdInvite(
                appointment.getId(),
                new InviteDtos.InviteByWaytIdRequest(host.getWaytId(), invitee.getWaytId())
        );

        var job = notificationJobRepository.findAll()
                .stream()
                .filter(item -> item.getNotificationType().equals(NotificationType.APPOINTMENT_INVITE.apiId()))
                .filter(item -> item.getRecipient().getId().equals(invitee.getId()))
                .findFirst()
                .orElseThrow();
        Map<String, Object> data = objectMapper.readValue(job.getDataJson(), new TypeReference<>() {
        });

        assertThat(data.get("route")).isEqualTo("/invites/" + invite.token());
        assertThat(data.get("appointmentId")).isEqualTo(appointment.getId().toString());
        assertThat(data.get("inviteId")).isEqualTo(invite.id().toString());
    }

    @Test
    @Transactional
    void inviteResponsePushPayloadRoutesHostBackToAppointment() throws Exception {
        String suffix = UUID.randomUUID().toString();
        UserAccount host = saveUser("response-host-" + suffix, "@response_host_" + suffix.substring(0, 8), "response-host", null);
        UserAccount invitee = saveUser("response-invitee-" + suffix, "@response_invitee_" + suffix.substring(0, 8), "response-invitee", null);
        Appointment appointment = saveAppointment(host, "Response route invite", "Suwon Station", OffsetDateTime.now().plusHours(3));
        pushTokenRepository.save(new PushToken(host, "ExpoPushToken[host-" + suffix.substring(0, 8) + "]", "ios"));
        InviteDtos.InviteResponse invite = inviteService.createWaytIdInvite(
                appointment.getId(),
                new InviteDtos.InviteByWaytIdRequest(host.getWaytId(), invitee.getWaytId())
        );

        inviteService.accept(invite.id(), new InviteDtos.AcceptInviteRequest(invitee.getId(), null, true));

        var job = notificationJobRepository.findAll()
                .stream()
                .filter(item -> item.getNotificationType().equals(NotificationType.INVITE_RESPONSE.apiId()))
                .filter(item -> item.getRecipient().getId().equals(host.getId()))
                .findFirst()
                .orElseThrow();
        Map<String, Object> data = objectMapper.readValue(job.getDataJson(), new TypeReference<>() {
        });

        assertThat(data.get("route")).isEqualTo("/appointments/" + appointment.getId());
        assertThat(data.get("appointmentId")).isEqualTo(appointment.getId().toString());
        assertThat(data.get("inviteId")).isEqualTo(invite.id().toString());
    }

    private UserAccount saveUser(String providerId, String waytId, String nickname, String avatarUrl) {
        return userAccountRepository.save(new UserAccount(
                AuthProvider.KAKAO,
                providerId,
                waytId,
                nickname,
                avatarUrl
        ));
    }

    private Appointment saveAppointment(UserAccount host, String title, String placeName, OffsetDateTime scheduledAt) {
        Appointment appointment = appointmentRepository.save(new Appointment(
                host,
                title,
                placeName,
                37.2656,
                127.0001,
                scheduledAt,
                60,
                "No penalty",
                20,
                0,
                null
        ));
        participantRepository.save(new Participant(appointment, host, ParticipantRole.HOST, true));
        return appointment;
    }

    private String bearer(UserAccount user) {
        String token = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(("access:" + user.getId() + ":0:test").getBytes(StandardCharsets.UTF_8));
        return "Bearer " + token;
    }
}
