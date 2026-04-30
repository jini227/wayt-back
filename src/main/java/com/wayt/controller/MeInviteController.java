package com.wayt.controller;

import com.wayt.dto.InviteDtos;
import com.wayt.service.InviteService;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/me/invites")
public class MeInviteController {
    private final InviteService inviteService;

    public MeInviteController(InviteService inviteService) {
        this.inviteService = inviteService;
    }

    @GetMapping
    List<InviteDtos.ReceivedInviteResponse> received(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        return inviteService.receivedInvites(authorization);
    }
}
