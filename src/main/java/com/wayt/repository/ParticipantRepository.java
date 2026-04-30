package com.wayt.repository;

import com.wayt.domain.Appointment;
import com.wayt.domain.Participant;
import com.wayt.domain.ParticipantMembershipStatus;
import com.wayt.domain.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ParticipantRepository extends JpaRepository<Participant, UUID> {
    List<Participant> findByAppointmentOrderByJoinedAtAsc(Appointment appointment);

    List<Participant> findByAppointmentAndMembershipStatusOrderByJoinedAtAsc(
            Appointment appointment,
            ParticipantMembershipStatus membershipStatus
    );

    Optional<Participant> findByAppointmentAndUserAccount(Appointment appointment, UserAccount userAccount);

    Optional<Participant> findByAppointmentAndUserAccountAndMembershipStatus(
            Appointment appointment,
            UserAccount userAccount,
            ParticipantMembershipStatus membershipStatus
    );

    boolean existsByAppointmentAndUserAccount(Appointment appointment, UserAccount userAccount);

    boolean existsByAppointmentAndUserAccountAndMembershipStatus(
            Appointment appointment,
            UserAccount userAccount,
            ParticipantMembershipStatus membershipStatus
    );

    @Query("""
            select case when count(participant) > 0 then true else false end
            from Participant participant
            where participant.userAccount = :userAccount
              and participant.membershipStatus = com.wayt.domain.ParticipantMembershipStatus.ACTIVE
              and participant.appointment.completedAt is null
              and participant.appointment.scheduledAt > :now
              and participant.appointment.scheduledAt = :scheduledAt
            """)
    boolean existsActiveUpcomingAppointmentAt(
            @Param("userAccount") UserAccount userAccount,
            @Param("scheduledAt") OffsetDateTime scheduledAt,
            @Param("now") OffsetDateTime now
    );
}
