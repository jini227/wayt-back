package com.wayt;

import com.wayt.domain.Appointment;
import com.wayt.domain.AuthProvider;
import com.wayt.domain.Participant;
import com.wayt.domain.ParticipantRole;
import com.wayt.domain.UserAccount;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wayt.repository.AppointmentRepository;
import com.wayt.repository.ParticipantRepository;
import com.wayt.repository.UserAccountRepository;
import com.wayt.service.AppointmentService;
import com.wayt.support.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.task.scheduling.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:appointment-public-detail-tests;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
class AppointmentPublicDetailTests {
    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private ParticipantRepository participantRepository;

    @Autowired
    private AppointmentService appointmentService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @Transactional
    void publicDetailAllowsAnonymousViewersWithoutParticipantActions() throws Exception {
        UserAccount host = saveUser("anonymous-host", "@anonymous_host", "anonymous-host");
        Appointment appointment = saveAppointment(host);

        var detail = appointmentService.getPublic(null, appointment.getId());

        assertThat(detail.id()).isEqualTo(appointment.getId());
        assertThat(detail.title()).isEqualTo("Public detail");
        assertThat(detail.isParticipant()).isFalse();
        assertThat(detail.myRole()).isNull();
        assertThat(detail.participants()).hasSize(1);
        assertThat(detail.participants().getFirst().name()).isEqualTo("anonymous-host");
        assertThat(objectMapper.writeValueAsString(detail)).contains("\"isParticipant\":false");
    }

    @Test
    @Transactional
    void publicDetailMarksAuthenticatedParticipants() {
        UserAccount host = saveUser("participant-host", "@participant_host", "participant-host");
        Appointment appointment = saveAppointment(host);

        var detail = appointmentService.getPublic(bearer(host), appointment.getId());

        assertThat(detail.isParticipant()).isTrue();
        assertThat(detail.myRole()).isEqualTo(ParticipantRole.HOST);
    }

    @Test
    @Transactional
    void existingPrivateDetailStillRejectsNonParticipants() {
        UserAccount host = saveUser("private-host", "@private_host", "private-host");
        UserAccount outsider = saveUser("private-outsider", "@private_outsider", "private-outsider");
        Appointment appointment = saveAppointment(host);

        assertThatThrownBy(() -> appointmentService.get(bearer(outsider), appointment.getId()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Appointment not found");
    }

    private UserAccount saveUser(String providerId, String waytId, String nickname) {
        return userAccountRepository.save(new UserAccount(
                AuthProvider.KAKAO,
                providerId + "-" + UUID.randomUUID(),
                waytId + "_" + UUID.randomUUID().toString().substring(0, 8),
                nickname,
                null
        ));
    }

    private Appointment saveAppointment(UserAccount host) {
        Appointment appointment = appointmentRepository.save(new Appointment(
                host,
                "Public detail",
                "Incheon IT Tower",
                37.2656,
                127.0001,
                OffsetDateTime.now().plusHours(2),
                60,
                "No penalty",
                20,
                0,
                "memo"
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
