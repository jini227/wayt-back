package com.wayt.controller;

import com.wayt.dto.AppointmentDtos;
import com.wayt.service.AppointmentService;
import jakarta.validation.Valid;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.UUID;

@Controller
public class AppointmentSocketController {
    private final AppointmentService appointmentService;
    private final SimpMessagingTemplate messagingTemplate;

    public AppointmentSocketController(AppointmentService appointmentService, SimpMessagingTemplate messagingTemplate) {
        this.appointmentService = appointmentService;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/appointments/{appointmentId}/location")
    public void location(
            @DestinationVariable UUID appointmentId,
            @Valid AppointmentDtos.LocationUpdateRequest request
    ) {
        var response = appointmentService.updateLocation(appointmentId, request);
        messagingTemplate.convertAndSend("/topic/appointments/" + appointmentId + "/presence", response);
    }

    @MessageMapping("/appointments/{appointmentId}/status")
    public void status(
            @DestinationVariable UUID appointmentId,
            @Valid AppointmentDtos.StatusLogCreateRequest request
    ) {
        appointmentService.addStatusLog(appointmentId, request);
        var response = appointmentService.get(appointmentId);
        messagingTemplate.convertAndSend("/topic/appointments/" + appointmentId + "/presence", response);
    }
}
