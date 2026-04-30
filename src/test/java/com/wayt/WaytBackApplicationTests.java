package com.wayt;

import com.wayt.domain.Appointment;
import com.wayt.domain.AppointmentCompletionReason;
import com.wayt.domain.AuthProvider;
import com.wayt.domain.LocationSample;
import com.wayt.domain.Participant;
import com.wayt.domain.ParticipantMembershipStatus;
import com.wayt.domain.ParticipantRole;
import com.wayt.domain.ParticipantStatus;
import com.wayt.domain.PushToken;
import com.wayt.domain.SavedPlace;
import com.wayt.domain.StatusLog;
import com.wayt.domain.UserAccount;
import com.wayt.domain.TravelMode;
import com.wayt.dto.AppointmentDtos;
import com.wayt.dto.AuthDtos;
import com.wayt.dto.InviteDtos;
import com.wayt.dto.MiscDtos;
import com.wayt.repository.AddressBookEntryRepository;
import com.wayt.repository.AppointmentRepository;
import com.wayt.repository.LocationSampleRepository;
import com.wayt.repository.NotificationJobRepository;
import com.wayt.repository.ParticipantRepository;
import com.wayt.repository.PushTokenRepository;
import com.wayt.repository.SavedPlaceRepository;
import com.wayt.repository.StatusLogRepository;
import com.wayt.repository.UserAccountRepository;
import com.wayt.service.AppointmentService;
import com.wayt.service.AuthService;
import com.wayt.service.InviteService;
import com.wayt.service.MiscService;
import com.wayt.service.ResponseMapper;
import com.wayt.service.SavedPlaceService;
import com.wayt.support.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class WaytBackApplicationTests {
    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private ParticipantRepository participantRepository;

    @Autowired
    private LocationSampleRepository locationSampleRepository;

    @Autowired
    private StatusLogRepository statusLogRepository;

    @Autowired
    private AddressBookEntryRepository addressBookEntryRepository;

    @Autowired
    private SavedPlaceRepository savedPlaceRepository;

    @Autowired
    private PushTokenRepository pushTokenRepository;

    @Autowired
    private NotificationJobRepository notificationJobRepository;

    @Autowired
    private ResponseMapper responseMapper;

    @Autowired
    private AppointmentService appointmentService;

    @Autowired
    private AuthService authService;

    @Autowired
    private MiscService miscService;

    @Autowired
    private SavedPlaceService savedPlaceService;

    @Autowired
    private InviteService inviteService;

    @Test
    void contextLoads() {
    }

    @Test
    @Transactional
    void appointmentResponseIncludesLatestParticipantLocation() {
        UserAccount user = userAccountRepository.save(new UserAccount(
                AuthProvider.KAKAO,
                "test-latest-location",
                "@latest_location",
                "위치유저",
                "https://example.com/avatar.jpg"
        ));
        Appointment appointment = appointmentRepository.save(new Appointment(
                user,
                "위치 테스트",
                "수원역",
                37.2656,
                127.0001,
                OffsetDateTime.now().plusHours(1),
                120,
                "테스트",
                100,
                5,
                null
        ));
        Participant participant = participantRepository.save(new Participant(appointment, user, ParticipantRole.HOST, true));
        locationSampleRepository.save(new LocationSample(appointment, participant, 37.1, 127.1, 30, OffsetDateTime.now().minusMinutes(5)));
        locationSampleRepository.save(new LocationSample(appointment, participant, 37.265, 127.002, 20, OffsetDateTime.now()));

        var response = responseMapper.appointment(appointment, user);

        assertThat(response.participants()).hasSize(1);
        var participantResponse = response.participants().getFirst();
        assertThat(participantResponse.latestLatitude()).isEqualTo(37.265);
        assertThat(participantResponse.latestLongitude()).isEqualTo(127.002);
        assertThat(participantResponse.latestAccuracyMeters()).isEqualTo(20);
        assertThat(participantResponse.latestLocationCapturedAt()).isNotNull();
    }

    @Test
    @Transactional
    void markLocationUnavailableSetsParticipantLocationOffAfterShareStarts() {
        String suffix = UUID.randomUUID().toString();
        OffsetDateTime now = OffsetDateTime.now();
        UserAccount user = userAccountRepository.save(new UserAccount(
                AuthProvider.KAKAO,
                "location-off-" + suffix,
                "@location_off_" + suffix.substring(0, 8),
                "location-off-user",
                null
        ));
        Appointment appointment = appointmentRepository.save(new Appointment(
                user,
                "Location off test",
                "Suwon Station",
                37.2656,
                127.0001,
                now.plusMinutes(30),
                60,
                "No penalty",
                20,
                0,
                null
        ));
        Participant participant = participantRepository.save(new Participant(appointment, user, ParticipantRole.HOST, true));
        locationSampleRepository.save(new LocationSample(appointment, participant, 37.265, 127.002, 20, now.minusMinutes(1)));

        var response = appointmentService.markLocationUnavailable(
                bearer(user),
                appointment.getId(),
                new AppointmentDtos.LocationUnavailableRequest(participant.getId())
        );

        assertThat(participant.getStatus()).isEqualTo(ParticipantStatus.LOCATION_OFF);
        assertThat(participant.isLocationConsent()).isFalse();
        assertThat(response.participants()).hasSize(1);
        assertThat(response.participants().getFirst().status()).isEqualTo(ParticipantStatus.LOCATION_OFF);
    }

    @Test
    @Transactional
    void manualArrivalCompletesSingleParticipantFutureAppointmentAndMovesItToHistory() {
        String suffix = UUID.randomUUID().toString();
        OffsetDateTime now = OffsetDateTime.now();
        UserAccount user = userAccountRepository.save(new UserAccount(
                AuthProvider.KAKAO,
                "completion-" + suffix,
                "@completion_" + suffix.substring(0, 8),
                "completion-user",
                "https://example.com/avatar.jpg"
        ));
        Appointment appointment = appointmentRepository.save(new Appointment(
                user,
                "Completion test",
                "Suwon Station",
                37.2656,
                127.0001,
                now.plusHours(2),
                120,
                "No penalty",
                100,
                5,
                null
        ));
        Participant participant = participantRepository.save(new Participant(appointment, user, ParticipantRole.HOST, true));

        var response = appointmentService.manualArrival(
                appointment.getId(),
                new AppointmentDtos.ManualArrivalRequest(participant.getId(), now)
        );

        assertThat(response.completedAt()).isNotNull();
        assertThat(response.completionReason()).isEqualTo(AppointmentCompletionReason.ALL_ARRIVED);
        assertThat(appointmentRepository.findUpcomingForUser(user, now)).isEmpty();
        assertThat(appointmentRepository.findHistoryForUser(user, now)).containsExactly(appointment);
    }

    @Test
    @Transactional
    void appointmentListAllReturnsActiveAppointmentsInRequestedRangeIncludingPastAndCompleted() {
        String suffix = UUID.randomUUID().toString();
        OffsetDateTime now = OffsetDateTime.now().withNano(0);
        UserAccount user = userAccountRepository.save(new UserAccount(
                AuthProvider.KAKAO,
                "calendar-list-" + suffix,
                "@calendar_list_" + suffix.substring(0, 8),
                "calendar-list-user",
                null
        ));
        UserAccount otherUser = userAccountRepository.save(new UserAccount(
                AuthProvider.KAKAO,
                "calendar-other-" + suffix,
                "@calendar_other_" + suffix.substring(0, 8),
                "calendar-other-user",
                null
        ));
        Appointment past = saveAppointmentFor(user, "Past appointment", now.minusDays(2));
        Appointment completed = saveAppointmentFor(user, "Completed appointment", now.minusDays(1));
        completed.complete(AppointmentCompletionReason.ALL_ARRIVED, now.minusHours(12));
        Appointment future = saveAppointmentFor(user, "Future appointment", now.plusDays(1));
        saveAppointmentFor(user, "Outside appointment", now.plusDays(12));
        saveAppointmentFor(otherUser, "Other user appointment", now.minusDays(1));

        var responses = appointmentService.list(
                bearer(user),
                new AppointmentDtos.AppointmentListRequest("all", now.minusDays(3), now.plusDays(3), null)
        );

        assertThat(responses)
                .extracting(AppointmentDtos.AppointmentResponse::title)
                .containsExactly("Past appointment", "Completed appointment", "Future appointment");
        assertThat(responses)
                .filteredOn(response -> response.id().equals(completed.getId()))
                .singleElement()
                .satisfies(response -> assertThat(response.completedAt()).isNotNull());
        assertThat(appointmentService.upcoming(bearer(user)))
                .extracting(AppointmentDtos.AppointmentResponse::title)
                .containsExactly("Future appointment", "Outside appointment");
        assertThat(appointmentService.history(bearer(user)))
                .extracting(AppointmentDtos.AppointmentResponse::title)
                .containsExactlyInAnyOrder("Completed appointment", "Past appointment");
        assertThat(past.getId()).isNotNull();
        assertThat(future.getId()).isNotNull();
    }

    @Test
    @Transactional
    void createUsesTighterDefaultArrivalRadiusAndNoGraceWhenNotProvided() {
        String suffix = UUID.randomUUID().toString();
        userAccountRepository.save(new UserAccount(
                AuthProvider.KAKAO,
                "defaults-" + suffix,
                "@defaults_" + suffix.substring(0, 8),
                "defaults-user",
                "https://example.com/avatar.jpg"
        ));
        var auth = authService.refresh(new AuthDtos.RefreshRequest("refresh-token"));

        var response = appointmentService.create(
                "Bearer " + auth.accessToken(),
                new AppointmentDtos.AppointmentCreateRequest(
                        null,
                        "Default option test",
                        "Suwon Station",
                        37.2656,
                        127.0001,
                        OffsetDateTime.now().plusHours(2),
                        null,
                        "No penalty",
                        null,
                        null,
                        null
                )
        );

        assertThat(response.arrivalRadiusMeters()).isEqualTo(20);
        assertThat(response.graceMinutes()).isZero();
        assertThat(response.shareStartOffsetMinutes()).isEqualTo(60);
    }

    @Test
    @Transactional
    void profileUpdateStoresDefaultTravelModeAndOnboardingCompletion() {
        String suffix = UUID.randomUUID().toString();
        userAccountRepository.save(new UserAccount(
                AuthProvider.KAKAO,
                "travel-profile-" + suffix,
                "@travel_profile_" + suffix.substring(0, 8),
                "travel-profile",
                null
        ));
        var auth = authService.refresh(new AuthDtos.RefreshRequest("refresh-token"));

        var response = authService.updateProfile(
                "Bearer " + auth.accessToken(),
                new AuthDtos.ProfileUpdateRequest("travel", null, TravelMode.BICYCLE, true)
        );

        assertThat(response.defaultTravelMode()).isEqualTo(TravelMode.BICYCLE);
        assertThat(response.travelModeOnboardingCompleted()).isTrue();
    }

    @Test
    @Transactional
    void createStoresHostTravelModeOnParticipant() {
        String suffix = UUID.randomUUID().toString();
        userAccountRepository.save(new UserAccount(
                AuthProvider.KAKAO,
                "host-travel-" + suffix,
                "@host_travel_" + suffix.substring(0, 8),
                "host-travel",
                null
        ));
        var auth = authService.refresh(new AuthDtos.RefreshRequest("refresh-token"));

        var response = appointmentService.create(
                "Bearer " + auth.accessToken(),
                new AppointmentDtos.AppointmentCreateRequest(
                        null,
                        "Host travel mode test",
                        "Suwon Station",
                        37.2656,
                        127.0001,
                        OffsetDateTime.now().plusHours(2),
                        null,
                        "No penalty",
                        null,
                        null,
                        null,
                        TravelMode.CAR
                )
        );

        assertThat(response.participants()).hasSize(1);
        assertThat(response.participants().getFirst().travelMode()).isEqualTo(TravelMode.CAR);
    }

    @Test
    @Transactional
    void inviteAcceptStoresBicycleTravelModeOnJoiningParticipant() {
        String suffix = UUID.randomUUID().toString();
        UserAccount host = userAccountRepository.save(new UserAccount(
                AuthProvider.KAKAO,
                "bicycle-host-" + suffix,
                "@bicycle_host_" + suffix.substring(0, 8),
                "bicycle-host",
                null
        ));
        UserAccount invitee = userAccountRepository.save(new UserAccount(
                AuthProvider.KAKAO,
                "bicycle-invitee-" + suffix,
                "@bicycle_invitee_" + suffix.substring(0, 8),
                "bicycle-invitee",
                null
        ));
        Appointment appointment = appointmentRepository.save(new Appointment(
                host,
                "Bicycle invite test",
                "Suwon Station",
                37.2656,
                127.0001,
                OffsetDateTime.now().plusHours(2),
                60,
                "No penalty",
                20,
                0,
                null
        ));
        participantRepository.save(new Participant(appointment, host, ParticipantRole.HOST, true));
        var invite = inviteService.createLinkInvite(appointment.getId());

        inviteService.accept(
                invite.id(),
                new InviteDtos.AcceptInviteRequest(invitee.getId(), TravelMode.BICYCLE, true)
        );

        var participant = participantRepository.findByAppointmentAndUserAccount(appointment, invitee).orElseThrow();
        assertThat(participant.getTravelMode()).isEqualTo(TravelMode.BICYCLE);
    }

    @Test
    @Transactional
    void travelModeUpdateOnlyChangesAuthenticatedParticipant() {
        String suffix = UUID.randomUUID().toString();
        UserAccount host = userAccountRepository.save(new UserAccount(
                AuthProvider.KAKAO,
                "travel-owner-host-" + suffix,
                "@travel_owner_host_" + suffix.substring(0, 8),
                "travel-owner-host",
                null
        ));
        UserAccount guest = userAccountRepository.save(new UserAccount(
                AuthProvider.KAKAO,
                "travel-owner-guest-" + suffix,
                "@travel_owner_guest_" + suffix.substring(0, 8),
                "travel-owner-guest",
                null
        ));
        Appointment appointment = appointmentRepository.save(new Appointment(
                host,
                "Travel owner test",
                "Suwon Station",
                37.2656,
                127.0001,
                OffsetDateTime.now().plusHours(2),
                60,
                "No penalty",
                20,
                0,
                null
        ));
        participantRepository.save(new Participant(appointment, host, ParticipantRole.HOST, true));
        Participant guestParticipant = participantRepository.save(new Participant(appointment, guest, ParticipantRole.PARTICIPANT, true));

        var response = appointmentService.updateTravelMode(
                bearer(guest),
                appointment.getId(),
                new AppointmentDtos.ParticipantTravelModeUpdateRequest(guestParticipant.getId(), TravelMode.WALK)
        );

        assertThat(response.participants())
                .filteredOn(participant -> participant.id().equals(guestParticipant.getId()))
                .singleElement()
                .satisfies(participant -> assertThat(participant.travelMode()).isEqualTo(TravelMode.WALK));
        assertThatThrownBy(() -> appointmentService.updateTravelMode(
                bearer(host),
                appointment.getId(),
                new AppointmentDtos.ParticipantTravelModeUpdateRequest(guestParticipant.getId(), TravelMode.CAR)
        )).isInstanceOf(ApiException.class)
                .hasMessage("Participant not found")
                .satisfies(exception -> assertThat(((ApiException) exception).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThat(guestParticipant.getTravelMode()).isEqualTo(TravelMode.WALK);
    }

    @Test
    @Transactional
    void manualEtaUpdateStoresOnlyAuthenticatedParticipantEstimateAndCanClearIt() {
        String suffix = UUID.randomUUID().toString();
        UserAccount host = userAccountRepository.save(new UserAccount(
                AuthProvider.KAKAO,
                "manual-eta-host-" + suffix,
                "@manual_eta_host_" + suffix.substring(0, 8),
                "manual-eta-host",
                null
        ));
        UserAccount guest = userAccountRepository.save(new UserAccount(
                AuthProvider.KAKAO,
                "manual-eta-guest-" + suffix,
                "@manual_eta_guest_" + suffix.substring(0, 8),
                "manual-eta-guest",
                null
        ));
        Appointment appointment = appointmentRepository.save(new Appointment(
                host,
                "Manual ETA test",
                "Suwon Station",
                37.2656,
                127.0001,
                OffsetDateTime.now().plusHours(2),
                60,
                "No penalty",
                20,
                0,
                null
        ));
        participantRepository.save(new Participant(appointment, host, ParticipantRole.HOST, true));
        Participant guestParticipant = participantRepository.save(new Participant(appointment, guest, ParticipantRole.PARTICIPANT, true));
        OffsetDateTime estimatedArrivalAt = OffsetDateTime.now().plusMinutes(24).withNano(0);

        var response = appointmentService.updateManualEta(
                bearer(guest),
                appointment.getId(),
                new AppointmentDtos.ParticipantManualEtaUpdateRequest(guestParticipant.getId(), estimatedArrivalAt)
        );

        assertThat(response.participants())
                .filteredOn(participant -> participant.id().equals(guestParticipant.getId()))
                .singleElement()
                .satisfies(participant -> {
                    assertThat(participant.manualEstimatedArrivalAt()).isEqualTo(estimatedArrivalAt);
                    assertThat(participant.manualEtaUpdatedAt()).isNotNull();
                });
        assertThatThrownBy(() -> appointmentService.updateManualEta(
                bearer(host),
                appointment.getId(),
                new AppointmentDtos.ParticipantManualEtaUpdateRequest(guestParticipant.getId(), estimatedArrivalAt.plusMinutes(5))
        )).isInstanceOf(ApiException.class)
                .hasMessage("Participant not found")
                .satisfies(exception -> assertThat(((ApiException) exception).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));

        var cleared = appointmentService.updateManualEta(
                bearer(guest),
                appointment.getId(),
                new AppointmentDtos.ParticipantManualEtaUpdateRequest(guestParticipant.getId(), null)
        );

        assertThat(cleared.participants())
                .filteredOn(participant -> participant.id().equals(guestParticipant.getId()))
                .singleElement()
                .satisfies(participant -> {
                    assertThat(participant.manualEstimatedArrivalAt()).isNull();
                    assertThat(participant.manualEtaUpdatedAt()).isNull();
                });
    }

    @Test
    @Transactional
    void manualEtaAfterAppointmentDeadlineMarksParticipantLikelyLate() {
        String suffix = UUID.randomUUID().toString();
        OffsetDateTime scheduledAt = OffsetDateTime.now().plusMinutes(4).withSecond(0).withNano(0);
        UserAccount user = userAccountRepository.save(new UserAccount(
                AuthProvider.KAKAO,
                "manual-eta-late-" + suffix,
                "@manual_late_" + suffix.substring(0, 8),
                "manual-eta-late-user",
                null
        ));
        Appointment appointment = appointmentRepository.save(new Appointment(
                user,
                "Manual ETA late test",
                "Suwon Station",
                37.2656,
                127.0001,
                scheduledAt,
                60,
                "No penalty",
                20,
                0,
                null
        ));
        Participant participant = participantRepository.save(new Participant(appointment, user, ParticipantRole.HOST, true));
        participant.updateStatus(ParticipantStatus.MOVING);
        OffsetDateTime lateEstimatedArrivalAt = scheduledAt.plusMinutes(59);

        var response = appointmentService.updateManualEta(
                bearer(user),
                appointment.getId(),
                new AppointmentDtos.ParticipantManualEtaUpdateRequest(participant.getId(), lateEstimatedArrivalAt)
        );

        assertThat(participant.getStatus()).isEqualTo(ParticipantStatus.LIKELY_LATE);
        assertThat(response.participants())
                .filteredOn(item -> item.id().equals(participant.getId()))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.manualEstimatedArrivalAt()).isEqualTo(lateEstimatedArrivalAt);
                    assertThat(item.status()).isEqualTo(ParticipantStatus.LIKELY_LATE);
                });
    }

    @Test
    @Transactional
    void manualEtaBeforeAppointmentDeadlineClearsPreviousLikelyLateStatus() {
        String suffix = UUID.randomUUID().toString();
        OffsetDateTime scheduledAt = OffsetDateTime.now().plusMinutes(31).withSecond(0).withNano(0);
        UserAccount user = userAccountRepository.save(new UserAccount(
                AuthProvider.KAKAO,
                "manual-eta-clear-late-" + suffix,
                "@manual_clear_" + suffix.substring(0, 8),
                "manual-eta-clear-user",
                null
        ));
        Appointment appointment = appointmentRepository.save(new Appointment(
                user,
                "Manual ETA clear late test",
                "Suwon Station",
                37.2656,
                127.0001,
                scheduledAt,
                60,
                "No penalty",
                20,
                0,
                null
        ));
        Participant participant = participantRepository.save(new Participant(appointment, user, ParticipantRole.HOST, true));
        participant.updateStatus(ParticipantStatus.MOVING);

        appointmentService.updateManualEta(
                bearer(user),
                appointment.getId(),
                new AppointmentDtos.ParticipantManualEtaUpdateRequest(participant.getId(), scheduledAt.plusMinutes(3))
        );
        assertThat(participant.getStatus()).isEqualTo(ParticipantStatus.LIKELY_LATE);

        var response = appointmentService.updateManualEta(
                bearer(user),
                appointment.getId(),
                new AppointmentDtos.ParticipantManualEtaUpdateRequest(participant.getId(), scheduledAt.minusMinutes(2))
        );

        assertThat(participant.getStatus()).isEqualTo(ParticipantStatus.MOVING);
        assertThat(response.participants())
                .filteredOn(item -> item.id().equals(participant.getId()))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.manualEstimatedArrivalAt()).isEqualTo(scheduledAt.minusMinutes(2));
                    assertThat(item.status()).isEqualTo(ParticipantStatus.MOVING);
                });
    }

    @Test
    @Transactional
    void createRejectsExactSameTimeWhenUserAlreadyHasActiveUpcomingAppointmentAsParticipant() {
        String suffix = UUID.randomUUID().toString();
        OffsetDateTime scheduledAt = OffsetDateTime.now().plusDays(1).withNano(0);
        UserAccount existingHost = userAccountRepository.save(new UserAccount(
                AuthProvider.KAKAO,
                "duplicate-time-host-" + suffix,
                "@dup_time_host_" + suffix.substring(0, 8),
                "duplicate-time-host",
                null
        ));
        UserAccount participant = userAccountRepository.save(new UserAccount(
                AuthProvider.KAKAO,
                "duplicate-time-participant-" + suffix,
                "@dup_time_participant_" + suffix.substring(0, 8),
                "duplicate-time-participant",
                null
        ));
        Appointment existing = appointmentRepository.save(new Appointment(
                existingHost,
                "Existing appointment",
                "Suwon Station",
                37.2656,
                127.0001,
                scheduledAt,
                60,
                "No penalty",
                20,
                0,
                null
        ));
        participantRepository.save(new Participant(existing, existingHost, ParticipantRole.HOST, true));
        participantRepository.save(new Participant(existing, participant, ParticipantRole.PARTICIPANT, true));

        assertThatThrownBy(() -> appointmentService.create(
                bearer(participant),
                new AppointmentDtos.AppointmentCreateRequest(
                        null,
                        "Duplicate time",
                        "Anyang Station",
                        37.4019,
                        126.9226,
                        scheduledAt,
                        60,
                        "",
                        20,
                        0,
                        null
                )
        )).isInstanceOf(ApiException.class)
                .hasMessage("이미 같은 시간에 예정된 약속이 있어요.")
                .satisfies(exception -> assertThat(((ApiException) exception).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    @Transactional
    void createAllowsExactSameTimeAfterUserLeftExistingAppointment() {
        String suffix = UUID.randomUUID().toString();
        OffsetDateTime scheduledAt = OffsetDateTime.now().plusDays(1).withNano(0);
        UserAccount existingHost = userAccountRepository.save(new UserAccount(
                AuthProvider.KAKAO,
                "left-time-host-" + suffix,
                "@left_time_host_" + suffix.substring(0, 8),
                "left-time-host",
                null
        ));
        UserAccount leaver = userAccountRepository.save(new UserAccount(
                AuthProvider.KAKAO,
                "left-time-leaver-" + suffix,
                "@left_time_leaver_" + suffix.substring(0, 8),
                "left-time-leaver",
                null
        ));
        Appointment existing = appointmentRepository.save(new Appointment(
                existingHost,
                "Existing appointment",
                "Suwon Station",
                37.2656,
                127.0001,
                scheduledAt,
                60,
                "No penalty",
                20,
                0,
                null
        ));
        participantRepository.save(new Participant(existing, existingHost, ParticipantRole.HOST, true));
        participantRepository.save(new Participant(existing, leaver, ParticipantRole.PARTICIPANT, true));
        appointmentService.leave(bearer(leaver), existing.getId());

        var response = appointmentService.create(
                bearer(leaver),
                new AppointmentDtos.AppointmentCreateRequest(
                        null,
                        "Same time after leave",
                        "Anyang Station",
                        37.4019,
                        126.9226,
                        scheduledAt,
                        60,
                        "",
                        20,
                        0,
                        null
                )
        );

        assertThat(response.scheduledAt()).isEqualTo(scheduledAt);
    }

    @Test
    @Transactional
    void createAllowsExactSameTimeWhenExistingAppointmentIsCompleted() {
        String suffix = UUID.randomUUID().toString();
        OffsetDateTime scheduledAt = OffsetDateTime.now().plusDays(1).withNano(0);
        UserAccount host = userAccountRepository.save(new UserAccount(
                AuthProvider.KAKAO,
                "completed-time-host-" + suffix,
                "@completed_time_" + suffix.substring(0, 8),
                "completed-time-host",
                null
        ));
        Appointment existing = appointmentRepository.save(new Appointment(
                host,
                "Completed appointment",
                "Suwon Station",
                37.2656,
                127.0001,
                scheduledAt,
                60,
                "No penalty",
                20,
                0,
                null
        ));
        participantRepository.save(new Participant(existing, host, ParticipantRole.HOST, true));
        existing.complete(AppointmentCompletionReason.ALL_ARRIVED, OffsetDateTime.now());

        var response = appointmentService.create(
                bearer(host),
                new AppointmentDtos.AppointmentCreateRequest(
                        null,
                        "Same time after completion",
                        "Anyang Station",
                        37.4019,
                        126.9226,
                        scheduledAt,
                        60,
                        "",
                        20,
                        0,
                        null
                )
        );

        assertThat(response.scheduledAt()).isEqualTo(scheduledAt);
    }

    @Test
    @Transactional
    void createAllowsExactSameTimeWhenExistingAppointmentIsPast() {
        String suffix = UUID.randomUUID().toString();
        OffsetDateTime scheduledAt = OffsetDateTime.now().minusDays(1).withNano(0);
        UserAccount host = userAccountRepository.save(new UserAccount(
                AuthProvider.KAKAO,
                "past-time-host-" + suffix,
                "@past_time_" + suffix.substring(0, 8),
                "past-time-host",
                null
        ));
        Appointment existing = appointmentRepository.save(new Appointment(
                host,
                "Past appointment",
                "Suwon Station",
                37.2656,
                127.0001,
                scheduledAt,
                60,
                "No penalty",
                20,
                0,
                null
        ));
        participantRepository.save(new Participant(existing, host, ParticipantRole.HOST, true));

        var response = appointmentService.create(
                bearer(host),
                new AppointmentDtos.AppointmentCreateRequest(
                        null,
                        "Same time after past appointment",
                        "Anyang Station",
                        37.4019,
                        126.9226,
                        scheduledAt,
                        60,
                        "",
                        20,
                        0,
                        null
                )
        );

        assertThat(response.scheduledAt()).isEqualTo(scheduledAt);
    }

    @Test
    @Transactional
    void creatingAppointmentRecordsRecentPlaceForHost() {
        String suffix = UUID.randomUUID().toString();
        UserAccount host = userAccountRepository.save(new UserAccount(
                AuthProvider.KAKAO,
                "place-host-" + suffix,
                "@place_host_" + suffix.substring(0, 8),
                "place-host",
                null
        ));

        var response = appointmentService.create(
                bearer(host),
                new AppointmentDtos.AppointmentCreateRequest(
                        null,
                        "Recent place test",
                        "홍대입구역 9번 출구",
                        37.557192,
                        126.924634,
                        OffsetDateTime.now().plusHours(2),
                        30,
                        "",
                        20,
                        0,
                        null
                )
        );

        assertThat(response.placeName()).isEqualTo("홍대입구역 9번 출구");
        assertThat(savedPlaceRepository.findByOwner(host))
                .singleElement()
                .satisfies(place -> {
                    assertThat(place.getLabel()).isEqualTo("홍대입구역 9번 출구");
                    assertThat(place.getPlaceName()).isEqualTo("홍대입구역 9번 출구");
                    assertThat(place.getLatitude()).isEqualTo(37.557192);
                    assertThat(place.getLongitude()).isEqualTo(126.924634);
                    assertThat(place.isFavorite()).isFalse();
                    assertThat(place.getUseCount()).isEqualTo(1);
                    assertThat(place.getLastUsedAt()).isNotNull();
                });
    }

    @Test
    @Transactional
    void favoriteSavedPlaceSurvivesLaterAppointmentUseAndListsBeforeRecents() {
        String suffix = UUID.randomUUID().toString();
        UserAccount host = userAccountRepository.save(new UserAccount(
                AuthProvider.KAKAO,
                "favorite-place-host-" + suffix,
                "@fav_place_" + suffix.substring(0, 8),
                "favorite-place-host",
                null
        ));
        var favorite = savedPlaceService.saveFavorite(
                bearer(host),
                new MiscDtos.SavedPlaceCreateRequest(
                        "회사",
                        "강남역 10번 출구",
                        37.497952,
                        127.027619,
                        true
                )
        );

        appointmentService.create(
                bearer(host),
                new AppointmentDtos.AppointmentCreateRequest(
                        null,
                        "Favorite reuse test",
                        "강남역 10번 출구",
                        37.497952,
                        127.027619,
                        OffsetDateTime.now().plusHours(2),
                        30,
                        "",
                        20,
                        0,
                        null
                )
        );
        appointmentService.create(
                bearer(host),
                new AppointmentDtos.AppointmentCreateRequest(
                        null,
                        "Other recent place test",
                        "서울역 3번 출구",
                        37.5547,
                        126.9706,
                        OffsetDateTime.now().plusHours(3),
                        30,
                        "",
                        20,
                        0,
                        null
                )
        );

        assertThat(savedPlaceService.savedPlaces(bearer(host)))
                .extracting(MiscDtos.SavedPlaceResponse::placeName)
                .containsExactly("강남역 10번 출구", "서울역 3번 출구");
        assertThat(savedPlaceService.savedPlaces(bearer(host)).getFirst())
                .satisfies(place -> {
                    assertThat(place.id()).isEqualTo(favorite.id());
                    assertThat(place.label()).isEqualTo("회사");
                    assertThat(place.favorite()).isTrue();
                    assertThat(place.useCount()).isEqualTo(1);
                    assertThat(place.lastUsedAt()).isNotNull();
                });
    }

    @Test
    @Transactional
    void savedPlaceLabelAndFavoriteCanBeUpdatedByOwner() {
        String suffix = UUID.randomUUID().toString();
        UserAccount host = userAccountRepository.save(new UserAccount(
                AuthProvider.KAKAO,
                "editable-place-host-" + suffix,
                "@edit_place_" + suffix.substring(0, 8),
                "editable-place-host",
                null
        ));
        var created = savedPlaceService.saveFavorite(
                bearer(host),
                new MiscDtos.SavedPlaceCreateRequest(
                        "회사",
                        "강남역 10번 출구",
                        37.497952,
                        127.027619,
                        true
                )
        );

        var updated = savedPlaceService.updateSavedPlace(
                bearer(host),
                created.id(),
                new MiscDtos.SavedPlaceUpdateRequest("스터디 장소", false)
        );

        assertThat(updated.id()).isEqualTo(created.id());
        assertThat(updated.label()).isEqualTo("스터디 장소");
        assertThat(updated.favorite()).isFalse();
        assertThat(savedPlaceRepository.findById(created.id()))
                .hasValueSatisfying(place -> {
                    assertThat(place.getLabel()).isEqualTo("스터디 장소");
                    assertThat(place.isFavorite()).isFalse();
                });
    }

    @Test
    @Transactional
    void favoriteSavedPlacesPruneOldestFavoriteWhenLimitIsExceeded() {
        String suffix = UUID.randomUUID().toString();
        UserAccount host = userAccountRepository.save(new UserAccount(
                AuthProvider.KAKAO,
                "favorite-limit-host-" + suffix,
                "@fav_limit_" + suffix.substring(0, 8),
                "favorite-limit-host",
                null
        ));

        for (int index = 0; index < 15; index++) {
            savedPlaceService.saveFavorite(
                    bearer(host),
                    new MiscDtos.SavedPlaceCreateRequest(
                            "즐겨찾기 " + index,
                            "즐겨찾기 장소 " + index,
                            37.0 + index,
                            127.0 + index,
                            true
                    )
            );
        }

        savedPlaceService.saveFavorite(
                bearer(host),
                new MiscDtos.SavedPlaceCreateRequest(
                        "새 즐겨찾기",
                        "새 장소",
                        38.0,
                        128.0,
                        true
                )
        );

        assertThat(savedPlaceRepository.findByOwner(host))
                .filteredOn(SavedPlace::isFavorite)
                .hasSize(15)
                .extracting(SavedPlace::getPlaceName)
                .doesNotContain("즐겨찾기 장소 0")
                .contains("새 장소");
    }

    @Test
    @Transactional
    void favoritePruningKeepsOldRecentPlaceAsRecentOnly() {
        String suffix = UUID.randomUUID().toString();
        UserAccount host = userAccountRepository.save(new UserAccount(
                AuthProvider.KAKAO,
                "favorite-prune-recent-host-" + suffix,
                "@fav_prune_recent_" + suffix.substring(0, 8),
                "favorite-prune-recent-host",
                null
        ));
        savedPlaceService.saveFavorite(
                bearer(host),
                new MiscDtos.SavedPlaceCreateRequest(
                        "오래된 단골",
                        "오래된 단골 장소",
                        37.1,
                        127.1,
                        true
                )
        );
        savedPlaceService.recordRecent(host, "오래된 단골 장소", 37.1, 127.1);

        for (int index = 0; index < 14; index++) {
            savedPlaceService.saveFavorite(
                    bearer(host),
                    new MiscDtos.SavedPlaceCreateRequest(
                            "즐겨찾기 " + index,
                            "즐겨찾기 장소 " + index,
                            37.2 + index,
                            127.2 + index,
                            true
                    )
            );
        }

        savedPlaceService.saveFavorite(
                bearer(host),
                new MiscDtos.SavedPlaceCreateRequest(
                        "새 즐겨찾기",
                        "새 즐겨찾기 장소",
                        38.9,
                        128.9,
                        true
                )
        );

        assertThat(savedPlaceRepository.findByOwner(host))
                .filteredOn(SavedPlace::isFavorite)
                .hasSize(15)
                .extracting(SavedPlace::getPlaceName)
                .doesNotContain("오래된 단골 장소")
                .contains("새 즐겨찾기 장소");
        assertThat(savedPlaceRepository.findByOwner(host))
                .filteredOn(place -> place.getPlaceName().equals("오래된 단골 장소"))
                .singleElement()
                .satisfies(place -> {
                    assertThat(place.isFavorite()).isFalse();
                    assertThat(place.getUseCount()).isEqualTo(1);
                    assertThat(place.getLastUsedAt()).isNotNull();
                });
    }

    @Test
    @Transactional
    void recentSavedPlacesAreTrimmedToFifteenPerOwner() {
        String suffix = UUID.randomUUID().toString();
        UserAccount host = userAccountRepository.save(new UserAccount(
                AuthProvider.KAKAO,
                "recent-limit-host-" + suffix,
                "@recent_limit_" + suffix.substring(0, 8),
                "recent-limit-host",
                null
        ));

        for (int index = 0; index < 20; index++) {
            savedPlaceService.recordRecent(
                    host,
                    "최근 장소 " + index,
                    37.0 + index,
                    127.0 + index
            );
        }

        assertThat(savedPlaceRepository.findByOwner(host))
                .filteredOn(place -> place.getUseCount() > 0)
                .hasSize(15)
                .extracting(SavedPlace::getPlaceName)
                .doesNotContain("최근 장소 0", "최근 장소 1", "최근 장소 2", "최근 장소 3", "최근 장소 4")
                .contains("최근 장소 19");
    }

    @Test
    @Transactional
    void recentPruningKeepsOldFavoritePlaceAsFavoriteOnly() {
        String suffix = UUID.randomUUID().toString();
        UserAccount host = userAccountRepository.save(new UserAccount(
                AuthProvider.KAKAO,
                "recent-prune-favorite-host-" + suffix,
                "@recent_prune_fav_" + suffix.substring(0, 8),
                "recent-prune-favorite-host",
                null
        ));
        savedPlaceService.saveFavorite(
                bearer(host),
                new MiscDtos.SavedPlaceCreateRequest(
                        "오래된 즐겨찾기",
                        "오래된 즐겨찾기 장소",
                        37.1,
                        127.1,
                        true
                )
        );
        savedPlaceService.recordRecent(host, "오래된 즐겨찾기 장소", 37.1, 127.1);

        for (int index = 0; index < 15; index++) {
            savedPlaceService.recordRecent(
                    host,
                    "최근 장소 " + index,
                    37.2 + index,
                    127.2 + index
            );
        }

        assertThat(savedPlaceRepository.findByOwner(host))
                .filteredOn(place -> place.getUseCount() > 0)
                .hasSize(15)
                .extracting(SavedPlace::getPlaceName)
                .doesNotContain("오래된 즐겨찾기 장소")
                .contains("최근 장소 14");
        assertThat(savedPlaceRepository.findByOwner(host))
                .filteredOn(place -> place.getPlaceName().equals("오래된 즐겨찾기 장소"))
                .singleElement()
                .satisfies(place -> {
                    assertThat(place.isFavorite()).isTrue();
                    assertThat(place.getUseCount()).isZero();
                    assertThat(place.getLastUsedAt()).isNull();
                });
    }

    @Test
    @Transactional
    void startedStatusLogCanOnlyBeRecordedOnce() {
        String suffix = UUID.randomUUID().toString();
        OffsetDateTime now = OffsetDateTime.now();
        UserAccount user = userAccountRepository.save(new UserAccount(
                AuthProvider.KAKAO,
                "started-once-" + suffix,
                "@started_" + suffix.substring(0, 8),
                "started-user",
                "https://example.com/avatar.jpg"
        ));
        Appointment appointment = appointmentRepository.save(new Appointment(
                user,
                "Started once test",
                "Suwon Station",
                37.2656,
                127.0001,
                now.plusHours(2),
                120,
                "No penalty",
                100,
                5,
                null
        ));
        Participant participant = participantRepository.save(new Participant(appointment, user, ParticipantRole.HOST, true));

        var response = appointmentService.addStatusLog(
                appointment.getId(),
                new AppointmentDtos.StatusLogCreateRequest(
                        participant.getId(),
                        AppointmentDtos.StatusLogAction.STARTED,
                        null
                )
        );

        assertThat(response.message()).isEqualTo("출발했어요");
        assertThat(statusLogRepository.findByAppointmentOrderByCreatedAtAsc(appointment)).hasSize(1);
        assertThat(responseMapper.appointment(appointment, user).participants().getFirst().startedAt()).isNotNull();
        assertThatThrownBy(() -> appointmentService.addStatusLog(
                appointment.getId(),
                new AppointmentDtos.StatusLogCreateRequest(
                        participant.getId(),
                        AppointmentDtos.StatusLogAction.STARTED,
                        null
                )
        )).isInstanceOf(ApiException.class)
                .hasMessage("이미 출발 상태를 공유했어요.");
        assertThat(statusLogRepository.findByAppointmentOrderByCreatedAtAsc(appointment)).hasSize(1);
    }

    @Test
    @Transactional
    void startedStatusLogRefreshesEtaOnlyWhenLastCalculationIsAtLeastTenMinutesOld() {
        OffsetDateTime now = OffsetDateTime.now();
        Participant recentParticipant = participantWithEtaCalculatedAt("recent-eta", now.minusMinutes(9), now);
        Participant staleParticipant = participantWithEtaCalculatedAt("stale-eta", now.minusMinutes(10), now);

        appointmentService.addStatusLog(
                recentParticipant.getAppointment().getId(),
                new AppointmentDtos.StatusLogCreateRequest(
                        recentParticipant.getId(),
                        AppointmentDtos.StatusLogAction.STARTED,
                        null
                )
        );
        appointmentService.addStatusLog(
                staleParticipant.getAppointment().getId(),
                new AppointmentDtos.StatusLogCreateRequest(
                        staleParticipant.getId(),
                        AppointmentDtos.StatusLogAction.STARTED,
                        null
                )
        );

        assertThat(recentParticipant.getEtaApiCallCount()).isEqualTo(1);
        assertThat(recentParticipant.getEtaCalculatedAt()).isEqualTo(now.minusMinutes(9));
        assertThat(staleParticipant.getEtaApiCallCount()).isEqualTo(2);
        assertThat(staleParticipant.getEtaCalculatedAt()).isAfterOrEqualTo(now);
    }

    @Test
    @Transactional
    void nearAndLateStatusLogsDoNotChangeParticipantStatus() {
        String suffix = UUID.randomUUID().toString();
        OffsetDateTime now = OffsetDateTime.now();
        UserAccount user = userAccountRepository.save(new UserAccount(
                AuthProvider.KAKAO,
                "status-log-only-" + suffix,
                "@log_only_" + suffix.substring(0, 8),
                "status-log-user",
                null
        ));
        Appointment appointment = appointmentRepository.save(new Appointment(
                user,
                "Status log only test",
                "Suwon Station",
                37.2656,
                127.0001,
                now.plusHours(2),
                120,
                "No penalty",
                100,
                5,
                null
        ));
        Participant lateParticipant = participantRepository.save(new Participant(appointment, user, ParticipantRole.HOST, true));
        lateParticipant.updateStatus(ParticipantStatus.MOVING);

        appointmentService.addStatusLog(
                appointment.getId(),
                new AppointmentDtos.StatusLogCreateRequest(
                        lateParticipant.getId(),
                        AppointmentDtos.StatusLogAction.LATE,
                        null
                )
        );

        assertThat(lateParticipant.getStatus()).isEqualTo(ParticipantStatus.MOVING);

        UserAccount nearUser = userAccountRepository.save(new UserAccount(
                AuthProvider.KAKAO,
                "status-log-near-" + suffix,
                "@log_near_" + suffix.substring(0, 8),
                "status-log-near-user",
                null
        ));
        Participant nearParticipant = participantRepository.save(new Participant(appointment, nearUser, ParticipantRole.PARTICIPANT, true));
        nearParticipant.updateStatus(ParticipantStatus.LIKELY_LATE);

        appointmentService.addStatusLog(
                appointment.getId(),
                new AppointmentDtos.StatusLogCreateRequest(
                        nearParticipant.getId(),
                        AppointmentDtos.StatusLogAction.NEAR,
                        null
                )
        );

        assertThat(nearParticipant.getStatus()).isEqualTo(ParticipantStatus.LIKELY_LATE);
        assertThat(statusLogRepository.findByAppointmentOrderByCreatedAtAsc(appointment)).hasSize(2);
    }

    @Test
    @Transactional
    void appointmentResponseStatusLogsOnlyIncludeStatusButtonMessages() {
        String suffix = UUID.randomUUID().toString();
        UserAccount host = userAccountRepository.save(new UserAccount(
                AuthProvider.KAKAO,
                "status-filter-host-" + suffix,
                "@status_filter_" + suffix.substring(0, 8),
                "status-filter-host",
                null
        ));
        UserAccount guest = userAccountRepository.save(new UserAccount(
                AuthProvider.KAKAO,
                "status-filter-guest-" + suffix,
                "@filter_guest_" + suffix.substring(0, 8),
                "status-filter-guest",
                null
        ));
        Appointment appointment = appointmentRepository.save(new Appointment(
                host,
                "Status filter test",
                "Suwon Station",
                37.2656,
                127.0001,
                OffsetDateTime.now().plusHours(2),
                120,
                "No penalty",
                100,
                5,
                null
        ));
        Participant hostParticipant = participantRepository.save(new Participant(appointment, host, ParticipantRole.HOST, true));
        Participant guestParticipant = participantRepository.save(new Participant(appointment, guest, ParticipantRole.PARTICIPANT, true));

        statusLogRepository.save(new StatusLog(appointment, hostParticipant, "출발했어요"));
        statusLogRepository.save(new StatusLog(appointment, guestParticipant, "삭제되었어요"));
        statusLogRepository.save(new StatusLog(appointment, guestParticipant, "거의 다 왔어요"));
        statusLogRepository.save(new StatusLog(appointment, guestParticipant, "나갔어요"));
        statusLogRepository.save(new StatusLog(appointment, hostParticipant, "도착했어요"));

        assertThat(responseMapper.appointment(appointment, host).statusLogs())
                .extracting(AppointmentDtos.StatusLogResponse::message)
                .containsExactlyInAnyOrder("출발했어요", "거의 다 왔어요", "도착했어요");
    }

    @Test
    @Transactional
    void addressBookUsesAuthenticatedOwnerForAddListAndDelete() {
        String suffix = UUID.randomUUID().toString();
        UserAccount owner = userAccountRepository.save(new UserAccount(
                AuthProvider.KAKAO,
                "address-owner-" + suffix,
                "@address_owner_" + suffix.substring(0, 8),
                "주소록주인",
                null
        ));
        UserAccount saved = userAccountRepository.save(new UserAccount(
                AuthProvider.KAKAO,
                "address-saved-" + suffix,
                "@address_saved_" + suffix.substring(0, 8),
                "초대친구",
                "https://example.com/saved.png"
        ));
        UserAccount stranger = userAccountRepository.save(new UserAccount(
                AuthProvider.KAKAO,
                "address-stranger-" + suffix,
                "@address_stranger_" + suffix.substring(0, 8),
                "남의목록",
                null
        ));

        var created = miscService.addAddressBook(
                bearer(owner),
                new MiscDtos.AddressBookCreateRequest(saved.getWaytId(), "")
        );

        assertThat(created.displayName()).isEqualTo(saved.getNickname());
        assertThat(miscService.addressBook(bearer(owner)))
                .extracting(item -> item.user().waytId())
                .containsExactly(saved.getWaytId());
        assertThat(miscService.addressBook(bearer(stranger))).isEmpty();

        miscService.deleteAddressBook(bearer(owner), created.id());

        assertThat(addressBookEntryRepository.findByOwnerOrderByDisplayNameAsc(owner)).isEmpty();
    }

    @Test
    @Transactional
    void addressBookRejectsSelfAndDuplicateEntries() {
        String suffix = UUID.randomUUID().toString();
        UserAccount owner = userAccountRepository.save(new UserAccount(
                AuthProvider.KAKAO,
                "address-duplicate-owner-" + suffix,
                "@address_dup_owner_" + suffix.substring(0, 8),
                "주소록중복",
                null
        ));
        UserAccount saved = userAccountRepository.save(new UserAccount(
                AuthProvider.KAKAO,
                "address-duplicate-saved-" + suffix,
                "@address_dup_saved_" + suffix.substring(0, 8),
                "중복친구",
                null
        ));

        miscService.addAddressBook(
                bearer(owner),
                new MiscDtos.AddressBookCreateRequest(saved.getWaytId(), "친구")
        );

        assertThatThrownBy(() -> miscService.addAddressBook(
                bearer(owner),
                new MiscDtos.AddressBookCreateRequest(saved.getWaytId(), "친구")
        )).isInstanceOf(ApiException.class)
                .hasMessage("이미 주소록에 있는 사용자예요.");
        assertThatThrownBy(() -> miscService.addAddressBook(
                bearer(owner),
                new MiscDtos.AddressBookCreateRequest(owner.getWaytId(), null)
        )).isInstanceOf(ApiException.class)
                .hasMessage("내 아이디는 주소록에 추가할 수 없어요.");
    }

    @Test
    @Transactional
    void waytIdSuggestionsUsePrefixLimitAndExcludeAuthenticatedUser() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        UserAccount viewer = userAccountRepository.save(new UserAccount(
                AuthProvider.KAKAO,
                "suggest-viewer-" + suffix,
                "@suggest_" + suffix + "_00",
                "viewer",
                null
        ));
        userAccountRepository.save(new UserAccount(AuthProvider.KAKAO, "suggest-1-" + suffix, "@suggest_" + suffix + "_01", "one", "https://example.com/1.png"));
        userAccountRepository.save(new UserAccount(AuthProvider.KAKAO, "suggest-2-" + suffix, "@suggest_" + suffix + "_02", "two", null));
        userAccountRepository.save(new UserAccount(AuthProvider.KAKAO, "suggest-3-" + suffix, "@suggest_" + suffix + "_03", "three", null));
        userAccountRepository.save(new UserAccount(AuthProvider.KAKAO, "suggest-4-" + suffix, "@suggest_" + suffix + "_04", "four", null));
        userAccountRepository.save(new UserAccount(AuthProvider.KAKAO, "suggest-5-" + suffix, "@suggest_" + suffix + "_05", "five", null));
        userAccountRepository.save(new UserAccount(AuthProvider.KAKAO, "other-suggest-" + suffix, "@other_" + suffix, "other", null));

        var suggestions = miscService.waytIdSuggestions(
                bearer(viewer),
                "suggest_" + suffix,
                4
        );

        assertThat(suggestions)
                .extracting(MiscDtos.WaytIdSuggestionResponse::waytId)
                .containsExactly(
                        "@suggest_" + suffix + "_01",
                        "@suggest_" + suffix + "_02",
                        "@suggest_" + suffix + "_03",
                        "@suggest_" + suffix + "_04"
                );
        assertThat(suggestions).hasSize(4);
    }

    @Test
    @Transactional
    void waytIdInvitesCanBeReloadedForAppointment() {
        String suffix = UUID.randomUUID().toString();
        UserAccount host = userAccountRepository.save(new UserAccount(
                AuthProvider.KAKAO,
                "invite-host-" + suffix,
                "@invite_host_" + suffix.substring(0, 8),
                "invite-host",
                null
        ));
        UserAccount invitee = userAccountRepository.save(new UserAccount(
                AuthProvider.KAKAO,
                "invitee-" + suffix,
                "@invitee_" + suffix.substring(0, 8),
                "invitee",
                "https://example.com/invitee.png"
        ));
        Appointment appointment = appointmentRepository.save(new Appointment(
                host,
                "Reload invite test",
                "Suwon Station",
                37.2656,
                127.0001,
                OffsetDateTime.now().plusHours(2),
                120,
                "No penalty",
                100,
                5,
                null
        ));
        participantRepository.save(new Participant(appointment, host, ParticipantRole.HOST, true));

        var created = inviteService.createWaytIdInvite(
                appointment.getId(),
                new InviteDtos.InviteByWaytIdRequest(host.getWaytId(), invitee.getWaytId())
        );

        var reloaded = inviteService.appointmentInvites(bearer(host), appointment.getId());

        assertThat(reloaded).hasSize(1);
        assertThat(reloaded.getFirst().id()).isEqualTo(created.id());
        assertThat(reloaded.getFirst().targetWaytId()).isEqualTo(invitee.getWaytId());
        assertThat(created.targetNickname()).isEqualTo("invitee");
        assertThat(created.targetAvatarUrl()).isEqualTo("https://example.com/invitee.png");
        assertThat(reloaded.getFirst().targetNickname()).isEqualTo("invitee");
        assertThat(reloaded.getFirst().targetAvatarUrl()).isEqualTo("https://example.com/invitee.png");
        assertThat(reloaded.getFirst().status()).isEqualTo(created.status());
    }

    @Test
    @Transactional
    void pendingWaytIdInviteCanBeCancelledAndNotifiesInvitee() {
        String suffix = UUID.randomUUID().toString();
        UserAccount host = userAccountRepository.save(new UserAccount(
                AuthProvider.KAKAO,
                "cancel-host-" + suffix,
                "@cancel_host_" + suffix.substring(0, 8),
                "cancel-host",
                null
        ));
        UserAccount invitee = userAccountRepository.save(new UserAccount(
                AuthProvider.KAKAO,
                "cancel-invitee-" + suffix,
                "@cancel_invitee_" + suffix.substring(0, 8),
                "cancel-invitee",
                null
        ));
        pushTokenRepository.save(new PushToken(invitee, "ExponentPushToken[cancel-" + suffix + "]", "ios"));
        Appointment appointment = appointmentRepository.save(new Appointment(
                host,
                "Cancel invite test",
                "Suwon Station",
                37.2656,
                127.0001,
                OffsetDateTime.now().plusHours(2),
                120,
                "No penalty",
                100,
                5,
                null
        ));
        participantRepository.save(new Participant(appointment, host, ParticipantRole.HOST, true));
        var created = inviteService.createWaytIdInvite(
                appointment.getId(),
                new InviteDtos.InviteByWaytIdRequest(host.getWaytId(), invitee.getWaytId())
        );
        notificationJobRepository.deleteAll();

        var cancelled = inviteService.cancel(bearer(host), appointment.getId(), created.id());

        assertThat(cancelled.status().name()).isEqualTo("CANCELLED");
        assertThat(inviteService.receivedInvites(bearer(invitee))).isEmpty();
        assertThat(notificationJobRepository.findAll())
                .singleElement()
                .satisfies(job -> {
                    assertThat(job.getRecipient().getId()).isEqualTo(invitee.getId());
                    assertThat(job.getNotificationType()).isEqualTo("appointment-invite-cancelled");
                    assertThat(job.getTitle()).isEqualTo("초대가 취소됐어요");
                    assertThat(job.getBody()).isEqualTo("Cancel invite test");
                });
    }

    @Test
    @Transactional
    void leavingAppointmentHidesItFromLeaverButKeepsRecordForRemainingParticipants() {
        String suffix = UUID.randomUUID().toString();
        UserAccount host = userAccountRepository.save(new UserAccount(
                AuthProvider.KAKAO,
                "leave-host-" + suffix,
                "@leave_host_" + suffix.substring(0, 8),
                "leave-host",
                null
        ));
        UserAccount guest = userAccountRepository.save(new UserAccount(
                AuthProvider.KAKAO,
                "leave-guest-" + suffix,
                "@leave_guest_" + suffix.substring(0, 8),
                "leave-guest",
                null
        ));
        Appointment appointment = appointmentRepository.save(new Appointment(
                host,
                "Leave test",
                "Suwon Station",
                37.2656,
                127.0001,
                OffsetDateTime.now().plusHours(2),
                120,
                "No penalty",
                100,
                5,
                null
        ));
        participantRepository.save(new Participant(appointment, host, ParticipantRole.HOST, true));
        Participant guestParticipant = participantRepository.save(new Participant(appointment, guest, ParticipantRole.PARTICIPANT, true));

        appointmentService.leave(bearer(guest), appointment.getId());

        assertThat(guestParticipant.getMembershipStatus()).isEqualTo(ParticipantMembershipStatus.LEFT);
        assertThat(guestParticipant.getLeftAt()).isNotNull();
        assertThat(appointmentRepository.findUpcomingForUser(guest, OffsetDateTime.now())).isEmpty();
        assertThat(appointmentRepository.findUpcomingForUser(host, OffsetDateTime.now())).containsExactly(appointment);
        assertThatThrownBy(() -> appointmentService.get(bearer(guest), appointment.getId()))
                .isInstanceOf(ApiException.class)
                .hasMessage("Appointment not found");
        assertThat(responseMapper.appointment(appointment, host).participants())
                .extracting(AppointmentDtos.ParticipantResponse::id)
                .doesNotContain(guestParticipant.getId());
    }

    @Test
    @Transactional
    void hostLeavingTransfersHostToEarliestRemainingParticipant() {
        String suffix = UUID.randomUUID().toString();
        UserAccount host = userAccountRepository.save(new UserAccount(
                AuthProvider.KAKAO,
                "host-leave-" + suffix,
                "@host_leave_" + suffix.substring(0, 8),
                "host-leave",
                null
        ));
        UserAccount guest = userAccountRepository.save(new UserAccount(
                AuthProvider.KAKAO,
                "new-host-" + suffix,
                "@new_host_" + suffix.substring(0, 8),
                "new-host",
                null
        ));
        Appointment appointment = appointmentRepository.save(new Appointment(
                host,
                "Host transfer test",
                "Suwon Station",
                37.2656,
                127.0001,
                OffsetDateTime.now().plusHours(2),
                120,
                "No penalty",
                100,
                5,
                null
        ));
        Participant hostParticipant = participantRepository.save(new Participant(appointment, host, ParticipantRole.HOST, true));
        Participant guestParticipant = participantRepository.save(new Participant(appointment, guest, ParticipantRole.PARTICIPANT, true));

        appointmentService.leave(bearer(host), appointment.getId());

        assertThat(hostParticipant.getMembershipStatus()).isEqualTo(ParticipantMembershipStatus.LEFT);
        assertThat(appointment.getHost()).isEqualTo(guest);
        assertThat(guestParticipant.getRole()).isEqualTo(ParticipantRole.HOST);
        assertThat(responseMapper.appointment(appointment, guest).myRole()).isEqualTo(ParticipantRole.HOST);
    }

    @Test
    @Transactional
    void hostCanRemoveParticipantAndNewInviteReactivatesSameParticipant() {
        String suffix = UUID.randomUUID().toString();
        UserAccount host = userAccountRepository.save(new UserAccount(
                AuthProvider.KAKAO,
                "remove-host-" + suffix,
                "@remove_host_" + suffix.substring(0, 8),
                "remove-host",
                null
        ));
        UserAccount guest = userAccountRepository.save(new UserAccount(
                AuthProvider.KAKAO,
                "remove-guest-" + suffix,
                "@remove_guest_" + suffix.substring(0, 8),
                "remove-guest",
                null
        ));
        Appointment appointment = appointmentRepository.save(new Appointment(
                host,
                "Remove test",
                "Suwon Station",
                37.2656,
                127.0001,
                OffsetDateTime.now().plusHours(2),
                120,
                "No penalty",
                100,
                5,
                null
        ));
        participantRepository.save(new Participant(appointment, host, ParticipantRole.HOST, true));
        Participant guestParticipant = participantRepository.save(new Participant(appointment, guest, ParticipantRole.PARTICIPANT, true));

        appointmentService.removeParticipant(bearer(host), appointment.getId(), guestParticipant.getId());

        assertThat(guestParticipant.getMembershipStatus()).isEqualTo(ParticipantMembershipStatus.REMOVED);
        assertThat(guestParticipant.getRemovedAt()).isNotNull();
        assertThat(guestParticipant.getRemovedBy()).isEqualTo(host);
        assertThat(appointmentRepository.findUpcomingForUser(guest, OffsetDateTime.now())).isEmpty();
        assertThat(responseMapper.appointment(appointment, host).participants())
                .extracting(AppointmentDtos.ParticipantResponse::id)
                .doesNotContain(guestParticipant.getId());

        var invite = inviteService.createWaytIdInvite(
                appointment.getId(),
                new InviteDtos.InviteByWaytIdRequest(host.getWaytId(), guest.getWaytId())
        );
        inviteService.accept(invite.id(), new InviteDtos.AcceptInviteRequest(guest.getId(), null, true));

        Participant reactivated = participantRepository.findByAppointmentAndUserAccount(appointment, guest).orElseThrow();
        assertThat(reactivated.getId()).isEqualTo(guestParticipant.getId());
        assertThat(reactivated.getMembershipStatus()).isEqualTo(ParticipantMembershipStatus.ACTIVE);
        assertThat(reactivated.getRole()).isEqualTo(ParticipantRole.PARTICIPANT);
        assertThat(reactivated.getRemovedAt()).isNull();
        assertThat(reactivated.getRemovedBy()).isNull();
        assertThat(appointmentRepository.findUpcomingForUser(guest, OffsetDateTime.now())).containsExactly(appointment);
    }

    @Test
    @Transactional
    void participantManagementActionsDoNotCreateStatusLogs() {
        String suffix = UUID.randomUUID().toString();
        UserAccount host = userAccountRepository.save(new UserAccount(
                AuthProvider.KAKAO,
                "management-log-host-" + suffix,
                "@mgmt_host_" + suffix.substring(0, 8),
                "management-log-host",
                null
        ));
        UserAccount leaver = userAccountRepository.save(new UserAccount(
                AuthProvider.KAKAO,
                "management-log-leaver-" + suffix,
                "@mgmt_leave_" + suffix.substring(0, 8),
                "management-log-leaver",
                null
        ));
        UserAccount removed = userAccountRepository.save(new UserAccount(
                AuthProvider.KAKAO,
                "management-log-removed-" + suffix,
                "@mgmt_remove_" + suffix.substring(0, 8),
                "management-log-removed",
                null
        ));
        Appointment appointment = appointmentRepository.save(new Appointment(
                host,
                "Management log test",
                "Suwon Station",
                37.2656,
                127.0001,
                OffsetDateTime.now().plusHours(2),
                120,
                "No penalty",
                100,
                5,
                null
        ));
        participantRepository.save(new Participant(appointment, host, ParticipantRole.HOST, true));
        participantRepository.save(new Participant(appointment, leaver, ParticipantRole.PARTICIPANT, true));
        Participant removedParticipant = participantRepository.save(new Participant(appointment, removed, ParticipantRole.PARTICIPANT, true));

        appointmentService.leave(bearer(leaver), appointment.getId());
        appointmentService.removeParticipant(bearer(host), appointment.getId(), removedParticipant.getId());

        assertThat(statusLogRepository.findByAppointmentOrderByCreatedAtAsc(appointment)).isEmpty();
    }

    private Participant participantWithEtaCalculatedAt(String prefix, OffsetDateTime etaCalculatedAt, OffsetDateTime now) {
        String suffix = UUID.randomUUID().toString();
        UserAccount user = userAccountRepository.save(new UserAccount(
                AuthProvider.KAKAO,
                prefix + "-" + suffix,
                "@" + prefix + "_" + suffix.substring(0, 8),
                prefix + "-user",
                "https://example.com/avatar.jpg"
        ));
        Appointment appointment = appointmentRepository.save(new Appointment(
                user,
                prefix + " test",
                "Suwon Station",
                37.2656,
                127.0001,
                now.plusHours(2),
                120,
                "No penalty",
                100,
                5,
                null
        ));
        Participant participant = participantRepository.save(new Participant(appointment, user, ParticipantRole.HOST, true));
        participant.recordEtaCalculation(participant.getEtaRefreshPolicy(), etaCalculatedAt, null);
        return participant;
    }

    private Appointment saveAppointmentFor(UserAccount user, String title, OffsetDateTime scheduledAt) {
        Appointment appointment = appointmentRepository.save(new Appointment(
                user,
                title,
                "Suwon Station",
                37.2656,
                127.0001,
                scheduledAt,
                60,
                "No penalty",
                20,
                0,
                null
        ));
        participantRepository.save(new Participant(appointment, user, ParticipantRole.HOST, true));
        return appointment;
    }

    private String bearer(UserAccount user) {
        String token = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(("access:" + user.getId() + ":0:test").getBytes(StandardCharsets.UTF_8));
        return "Bearer " + token;
    }
}
