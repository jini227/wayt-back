package com.wayt.repository;

import com.wayt.domain.Invite;
import com.wayt.domain.Appointment;
import com.wayt.domain.InviteStatus;
import com.wayt.domain.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InviteRepository extends JpaRepository<Invite, UUID> {
    Optional<Invite> findByToken(String token);

    List<Invite> findByAppointmentOrderByCreatedAtDesc(Appointment appointment);

    List<Invite> findByInviteeAndStatusAndAppointmentScheduledAtAfterOrderByCreatedAtDesc(
            UserAccount invitee,
            InviteStatus status,
            OffsetDateTime scheduledAt
    );
}
