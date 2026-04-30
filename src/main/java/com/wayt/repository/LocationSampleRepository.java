package com.wayt.repository;

import com.wayt.domain.Appointment;
import com.wayt.domain.LocationSample;
import com.wayt.domain.Participant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LocationSampleRepository extends JpaRepository<LocationSample, UUID> {
    List<LocationSample> findTop20ByAppointmentOrderByCapturedAtDesc(Appointment appointment);

    List<LocationSample> findTop5ByParticipantOrderByCapturedAtDesc(Participant participant);

    Optional<LocationSample> findFirstByParticipantOrderByCapturedAtDesc(Participant participant);
}
