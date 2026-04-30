package com.wayt.controller;

import com.wayt.dto.InviteDtos;
import com.wayt.service.InviteService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/invites")
public class InviteController {
    private final InviteService inviteService;

    public InviteController(InviteService inviteService) {
        this.inviteService = inviteService;
    }

    @GetMapping("/{token}")
    InviteDtos.InviteResponse byToken(@PathVariable String token) {
        return inviteService.getByToken(token);
    }

    @PostMapping("/{inviteId}/accept")
    InviteDtos.InviteResponse accept(@PathVariable UUID inviteId, @Valid @RequestBody InviteDtos.AcceptInviteRequest request) {
        return inviteService.accept(inviteId, request);
    }

    @PostMapping("/{inviteId}/decline")
    InviteDtos.InviteResponse decline(@PathVariable UUID inviteId) {
        return inviteService.decline(inviteId);
    }
}
