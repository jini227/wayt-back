package com.wayt.service;

import com.wayt.domain.Appointment;
import com.wayt.domain.ParticipantRole;
import com.wayt.domain.Punctuality;
import com.wayt.dto.HistoryDtos;
import com.wayt.repository.AppointmentRepository;
import com.wayt.repository.ArrivalRecordRepository;
import com.wayt.repository.ParticipantRepository;
import com.wayt.support.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class HistoryService {
    private final AppointmentRepository appointmentRepository;
    private final ParticipantRepository participantRepository;
    private final ArrivalRecordRepository arrivalRecordRepository;
    private final ResponseMapper mapper;
    private final AppointmentService appointmentService;

    public HistoryService(
            AppointmentRepository appointmentRepository,
            ParticipantRepository participantRepository,
            ArrivalRecordRepository arrivalRecordRepository,
            ResponseMapper mapper,
            AppointmentService appointmentService
    ) {
        this.appointmentRepository = appointmentRepository;
        this.participantRepository = participantRepository;
        this.arrivalRecordRepository = arrivalRecordRepository;
        this.mapper = mapper;
        this.appointmentService = appointmentService;
    }

    @Transactional(readOnly = true)
    public List<HistoryDtos.HistoryItemResponse> list() {
        return appointmentRepository.findAll().stream()
                .map(this::history)
                .toList();
    }

    @Transactional(readOnly = true)
    public HistoryDtos.HistoryItemResponse get(UUID appointmentId) {
        return appointmentRepository.findById(appointmentId)
                .map(this::history)
                .orElseThrow(() -> ApiException.notFound("History item not found"));
    }

    private HistoryDtos.HistoryItemResponse history(Appointment appointment) {
        var participants = participantRepository.findByAppointmentOrderByJoinedAtAsc(appointment);
        long lateCount = arrivalRecordRepository.findByAppointment(appointment)
                .stream()
                .filter(record -> record.getPunctuality() == Punctuality.LATE)
                .count();
        String summary = lateCount == 0 ? "전원 정시" : "지각자 " + lateCount + "명";
        String role = participants.stream()
                .filter(participant -> participant.getUserAccount().getId().equals(appointmentService.defaultUser().getId()))
                .map(participant -> participant.getRole() == ParticipantRole.HOST ? "방장" : "참가자")
                .findFirst()
                .orElse("참가자");

        return new HistoryDtos.HistoryItemResponse(
                appointment.getId(),
                appointment.getTitle(),
                appointment.getScheduledAt(),
                appointment.getPlaceName(),
                role,
                summary,
                appointment.getPenalty(),
                participants.stream().map(participant -> mapper.participant(appointment, participant)).toList()
        );
    }
}
