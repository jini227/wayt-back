package com.wayt.repository;

import com.wayt.domain.Appointment;
import com.wayt.domain.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {
    List<Appointment> findByScheduledAtAfterOrderByScheduledAtAsc(OffsetDateTime now);

    @Query("""
            select p.appointment
            from Participant p
            where p.userAccount = :user
              and p.membershipStatus = com.wayt.domain.ParticipantMembershipStatus.ACTIVE
              and (:from is null or p.appointment.scheduledAt >= :from)
              and (:to is null or p.appointment.scheduledAt < :to)
            order by p.appointment.scheduledAt asc
            """)
    List<Appointment> findForUser(
            @Param("user") UserAccount user,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to
    );

    @Query("""
            select p.appointment
            from Participant p
            where p.userAccount = :user
              and p.membershipStatus = com.wayt.domain.ParticipantMembershipStatus.ACTIVE
              and p.appointment.completedAt is null
              and p.appointment.scheduledAt > :now
            order by p.appointment.scheduledAt asc
            """)
    List<Appointment> findUpcomingForUser(@Param("user") UserAccount user, @Param("now") OffsetDateTime now);

    @Query("""
            select p.appointment
            from Participant p
            where p.userAccount = :user
              and p.membershipStatus = com.wayt.domain.ParticipantMembershipStatus.ACTIVE
              and (p.appointment.completedAt is not null or p.appointment.scheduledAt <= :now)
            order by p.appointment.completedAt desc nulls last, p.appointment.scheduledAt desc
            """)
    List<Appointment> findHistoryForUser(@Param("user") UserAccount user, @Param("now") OffsetDateTime now);
}
