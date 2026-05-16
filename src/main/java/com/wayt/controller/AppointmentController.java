package com.wayt.controller;

import com.wayt.dto.AppointmentDtos;
import com.wayt.dto.InviteDtos;
import com.wayt.service.AppointmentService;
import com.wayt.service.InviteService;
import org.springframework.http.HttpHeaders;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {
    private final AppointmentService appointmentService;
    private final InviteService inviteService;

    public AppointmentController(AppointmentService appointmentService, InviteService inviteService) {
        this.appointmentService = appointmentService;
        this.inviteService = inviteService;
    }

    @PostMapping
    AppointmentDtos.AppointmentResponse create(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody AppointmentDtos.AppointmentCreateRequest request
    ) {
        return appointmentService.create(authorization, request);
    }

    @GetMapping
    List<AppointmentDtos.AppointmentResponse> list(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam(value = "scope", required = false) String scope,
            @RequestParam(value = "from", required = false) OffsetDateTime from,
            @RequestParam(value = "to", required = false) OffsetDateTime to,
            @RequestParam(value = "limit", required = false) Integer limit
    ) {
        return appointmentService.list(
                authorization,
                new AppointmentDtos.AppointmentListRequest(scope, from, to, limit)
        );
    }

    @GetMapping("/upcoming")
    List<AppointmentDtos.AppointmentResponse> upcoming(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        return appointmentService.upcoming(authorization);
    }

    @GetMapping("/history")
    List<AppointmentDtos.AppointmentResponse> history(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        return appointmentService.history(authorization);
    }

    @GetMapping("/{appointmentId}")
    AppointmentDtos.AppointmentResponse get(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID appointmentId
    ) {
        return appointmentService.get(authorization, appointmentId);
    }

    @GetMapping("/{appointmentId}/public")
    AppointmentDtos.AppointmentResponse publicGet(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID appointmentId
    ) {
        return appointmentService.getPublic(authorization, appointmentId);
    }

    @PostMapping("/{appointmentId}/leave")
    AppointmentDtos.AppointmentResponse leave(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID appointmentId
    ) {
        return appointmentService.leave(authorization, appointmentId);
    }

    @PostMapping("/{appointmentId}/participants/{participantId}/remove")
    AppointmentDtos.AppointmentResponse removeParticipant(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID appointmentId,
            @PathVariable UUID participantId
    ) {
        return appointmentService.removeParticipant(authorization, appointmentId, participantId);
    }

    @PostMapping("/{appointmentId}/invite-link")
    InviteDtos.InviteResponse createLinkInvite(@PathVariable UUID appointmentId) {
        return inviteService.createLinkInvite(appointmentId);
    }

    @GetMapping("/{appointmentId}/invites")
    List<InviteDtos.InviteResponse> invites(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID appointmentId
    ) {
        return inviteService.appointmentInvites(authorization, appointmentId);
    }

    @PostMapping("/{appointmentId}/invites/by-wayt-id")
    InviteDtos.InviteResponse inviteByWaytId(
            @PathVariable UUID appointmentId,
            @Valid @RequestBody InviteDtos.InviteByWaytIdRequest request
    ) {
        return inviteService.createWaytIdInvite(appointmentId, request);
    }

    @PostMapping("/{appointmentId}/invites/{inviteId}/cancel")
    InviteDtos.InviteResponse cancelInvite(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID appointmentId,
            @PathVariable UUID inviteId
    ) {
        return inviteService.cancel(authorization, appointmentId, inviteId);
    }

    @PostMapping("/{appointmentId}/locations")
    AppointmentDtos.AppointmentResponse updateLocation(
            @PathVariable UUID appointmentId,
            @Valid @RequestBody AppointmentDtos.LocationUpdateRequest request
    ) {
        return appointmentService.updateLocation(appointmentId, request);
    }

    @PostMapping("/{appointmentId}/locations/unavailable")
    AppointmentDtos.AppointmentResponse markLocationUnavailable(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID appointmentId,
            @Valid @RequestBody AppointmentDtos.LocationUnavailableRequest request
    ) {
        return appointmentService.markLocationUnavailable(authorization, appointmentId, request);
    }

    @PostMapping("/{appointmentId}/manual-arrival")
    AppointmentDtos.AppointmentResponse manualArrival(
            @PathVariable UUID appointmentId,
            @Valid @RequestBody AppointmentDtos.ManualArrivalRequest request
    ) {
        return appointmentService.manualArrival(appointmentId, request);
    }

    @PostMapping("/{appointmentId}/status-logs")
    AppointmentDtos.StatusLogResponse statusLog(
            @PathVariable UUID appointmentId,
            @Valid @RequestBody AppointmentDtos.StatusLogCreateRequest request
    ) {
        return appointmentService.addStatusLog(appointmentId, request);
    }

    @PatchMapping("/{appointmentId}/travel-mode")
    AppointmentDtos.AppointmentResponse updateTravelMode(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID appointmentId,
            @Valid @RequestBody AppointmentDtos.ParticipantTravelModeUpdateRequest request
    ) {
        return appointmentService.updateTravelMode(authorization, appointmentId, request);
    }

    @PatchMapping("/{appointmentId}/manual-eta")
    AppointmentDtos.AppointmentResponse updateManualEta(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID appointmentId,
            @Valid @RequestBody AppointmentDtos.ParticipantManualEtaUpdateRequest request
    ) {
        return appointmentService.updateManualEta(authorization, appointmentId, request);
    }
}
