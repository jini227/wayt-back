package com.wayt.repository;

import com.wayt.domain.Appointment;
import com.wayt.domain.Participant;
import com.wayt.domain.StatusLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StatusLogRepository extends JpaRepository<StatusLog, UUID> {
    List<StatusLog> findTop20ByAppointmentAndMessageInOrderByCreatedAtDesc(Appointment appointment, Collection<String> messages);

    List<StatusLog> findByAppointmentOrderByCreatedAtAsc(Appointment appointment);

    boolean existsByParticipantAndMessage(Participant participant, String message);

    Optional<StatusLog> findFirstByParticipantAndMessageOrderByCreatedAtAsc(Participant participant, String message);
}
