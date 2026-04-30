package com.wayt.config;

import com.wayt.domain.Appointment;
import com.wayt.domain.ArrivalRecord;
import com.wayt.domain.ArrivalSource;
import com.wayt.domain.AuthProvider;
import com.wayt.domain.Participant;
import com.wayt.domain.ParticipantRole;
import com.wayt.domain.ParticipantStatus;
import com.wayt.domain.Punctuality;
import com.wayt.domain.StatusLog;
import com.wayt.domain.TravelMode;
import com.wayt.domain.UserAccount;
import com.wayt.repository.AppointmentRepository;
import com.wayt.repository.ArrivalRecordRepository;
import com.wayt.repository.ParticipantRepository;
import com.wayt.repository.StatusLogRepository;
import com.wayt.repository.UserAccountRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Component
public class SeedData {
    private final UserAccountRepository userAccountRepository;
    private final AppointmentRepository appointmentRepository;
    private final ParticipantRepository participantRepository;
    private final ArrivalRecordRepository arrivalRecordRepository;
    private final StatusLogRepository statusLogRepository;

    public SeedData(
            UserAccountRepository userAccountRepository,
            AppointmentRepository appointmentRepository,
            ParticipantRepository participantRepository,
            ArrivalRecordRepository arrivalRecordRepository,
            StatusLogRepository statusLogRepository
    ) {
        this.userAccountRepository = userAccountRepository;
        this.appointmentRepository = appointmentRepository;
        this.participantRepository = participantRepository;
        this.arrivalRecordRepository = arrivalRecordRepository;
        this.statusLogRepository = statusLogRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seed() {
        if (userAccountRepository.existsByWaytId("@wayt_me")) {
            return;
        }

        UserAccount me = userAccountRepository.save(new UserAccount(AuthProvider.KAKAO, "seed-me", "@wayt_me", "나", "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?auto=format&fit=crop&w=160&q=80"));
        UserAccount minsu = userAccountRepository.save(new UserAccount(AuthProvider.KAKAO, "seed-minsu", "@minsu", "민수", "https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?auto=format&fit=crop&w=160&q=80"));
        UserAccount jiyoon = userAccountRepository.save(new UserAccount(AuthProvider.KAKAO, "seed-jiyoon", "@jiyoon23", "지윤", "https://images.unsplash.com/photo-1494790108377-be9c29b29330?auto=format&fit=crop&w=160&q=80"));
        UserAccount hyunwoo = userAccountRepository.save(new UserAccount(AuthProvider.KAKAO, "seed-hyunwoo", "@hyunwoo", "현우", "https://api.dicebear.com/9.x/personas/png?seed=hyunwoo&backgroundColor=b6e3f4"));

        OffsetDateTime scheduledAt = OffsetDateTime.now().plusMinutes(32);
        Appointment hongdae = appointmentRepository.save(new Appointment(
                me,
                "홍대 저녁 약속",
                "홍대입구역 9번 출구",
                37.557192,
                126.924634,
                scheduledAt,
                120,
                "지각자 커피 사기",
                100,
                5,
                "예약자명: 김민수\n늦으면 커피 사기\n2차는 현장에서 정하기"
        ));

        Participant pMe = participantRepository.save(new Participant(hongdae, me, ParticipantRole.HOST, true));
        Participant pMinsu = participantRepository.save(new Participant(hongdae, minsu, ParticipantRole.PARTICIPANT, true));
        Participant pJiyoon = participantRepository.save(new Participant(hongdae, jiyoon, ParticipantRole.PARTICIPANT, true));
        Participant pHyunwoo = participantRepository.save(new Participant(hongdae, hyunwoo, ParticipantRole.PARTICIPANT, false));

        pMe.changeTravelMode(TravelMode.TRANSIT);
        pMinsu.changeTravelMode(TravelMode.TRANSIT);
        pJiyoon.changeTravelMode(TravelMode.WALK);
        pHyunwoo.changeTravelMode(TravelMode.UNKNOWN);
        pMe.updateStatus(ParticipantStatus.ARRIVED);
        pMinsu.updateStatus(ParticipantStatus.MOVING);
        pJiyoon.updateStatus(ParticipantStatus.NEAR_ARRIVAL);
        pHyunwoo.updateStatus(ParticipantStatus.LIKELY_LATE);

        arrivalRecordRepository.save(new ArrivalRecord(hongdae, pMe, ArrivalSource.AUTO, scheduledAt.minusMinutes(4), Punctuality.ON_TIME, 0));
        statusLogRepository.save(new StatusLog(hongdae, pMinsu, "출발했어요"));
        statusLogRepository.save(new StatusLog(hongdae, pJiyoon, "거의 다 왔어요"));
        statusLogRepository.save(new StatusLog(hongdae, pHyunwoo, "조금 늦어요"));
        statusLogRepository.save(new StatusLog(hongdae, pMe, "도착했어요"));

        appointmentRepository.save(new Appointment(
                me,
                "팀플 회의",
                "중앙도서관 1층",
                37.5800,
                126.9830,
                OffsetDateTime.now().plusDays(1).withHour(14).withMinute(0),
                30,
                "벌칙 없음",
                100,
                5,
                ""
        ));

        appointmentRepository.save(new Appointment(
                me,
                "서울역 여행 집합",
                "서울역 3번 출구",
                37.5547,
                126.9706,
                OffsetDateTime.now().plusDays(5).withHour(8).withMinute(0),
                180,
                "지각자 아메리카노",
                100,
                5,
                ""
        ));
    }
}
