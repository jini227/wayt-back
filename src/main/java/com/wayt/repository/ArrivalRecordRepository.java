package com.wayt.repository;

import com.wayt.domain.Appointment;
import com.wayt.domain.ArrivalRecord;
import com.wayt.domain.Participant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ArrivalRecordRepository extends JpaRepository<ArrivalRecord, UUID> {
    List<ArrivalRecord> findByAppointment(Appointment appointment);

    Optional<ArrivalRecord> findByParticipant(Participant participant);

    boolean existsByParticipant(Participant participant);
}
