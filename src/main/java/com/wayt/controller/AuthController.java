package com.wayt.controller;

import com.wayt.dto.AuthDtos;
import com.wayt.dto.UserResponse;
import com.wayt.service.AppointmentService;
import com.wayt.service.AuthService;
import com.wayt.service.ResponseMapper;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;

@RestController
@RequestMapping("/api")
public class AuthController {
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;
    private final AppointmentService appointmentService;
    private final ResponseMapper mapper;

    public AuthController(AuthService authService, AppointmentService appointmentService, ResponseMapper mapper) {
        this.authService = authService;
        this.appointmentService = appointmentService;
        this.mapper = mapper;
    }

    @PostMapping("/auth/kakao")
    AuthDtos.AuthResponse kakao(@Valid @RequestBody AuthDtos.KakaoLoginRequest request) {
        return authService.loginWithKakao(request);
    }

    @GetMapping("/auth/kakao/authorize")
    ResponseEntity<Void> kakaoAuthorize(@RequestParam String returnUri) {
        return ResponseEntity
                .status(HttpStatus.FOUND)
                .location(authService.kakaoAuthorizeUri(returnUri))
                .build();
    }

    @GetMapping("/auth/kakao/callback")
    ResponseEntity<?> kakaoCallback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String redirectUri,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            @RequestParam(name = "error_description", required = false) String errorDescription
    ) {
        log.info(
                "Kakao callback received: hasCode={}, hasState={}, error={}, errorDescription={}",
                code != null && !code.isBlank(),
                state != null && !state.isBlank(),
                error,
                errorDescription
        );

        if (code == null || code.isBlank()) {
            if (state == null || state.isBlank()) {
                String message = errorDescription == null || errorDescription.isBlank()
                        ? "Kakao authorization code is missing"
                        : errorDescription;
                return ResponseEntity.badRequest().body(message);
            }

            URI appRedirect = authService.appErrorCallbackUri(state, error, errorDescription);
            return ResponseEntity.status(HttpStatus.FOUND).location(appRedirect).build();
        }

        AuthDtos.AuthResponse auth = authService.loginWithKakaoCode(new AuthDtos.KakaoCallbackRequest(code, redirectUri));
        if (state == null || state.isBlank()) {
            return ResponseEntity.ok(auth);
        }

        URI appRedirect = authService.appCallbackUri(auth, state);
        return ResponseEntity.status(HttpStatus.FOUND).location(appRedirect).build();
    }

    @PostMapping("/auth/refresh")
    AuthDtos.AuthResponse refresh(@Valid @RequestBody AuthDtos.RefreshRequest request) {
        return authService.refresh(request);
    }

    @GetMapping("/auth/session")
    UserResponse session(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return authService.session(authorization);
    }

    @PatchMapping("/me/profile")
    UserResponse updateProfile(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody AuthDtos.ProfileUpdateRequest request
    ) {
        return authService.updateProfile(authorization, request);
    }

    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    UserResponse uploadAvatar(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestPart("file") MultipartFile file
    ) {
        return authService.uploadAvatar(authorization, file);
    }

    @DeleteMapping("/me/avatar")
    UserResponse deleteAvatar(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return authService.deleteAvatar(authorization);
    }

    @GetMapping("/uploads/avatars/{filename:.+}")
    ResponseEntity<Resource> avatar(@PathVariable String filename) {
        AuthService.StoredAvatar avatar = authService.avatar(filename);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(avatar.contentType()))
                .body(avatar.resource());
    }

    @GetMapping("/me")
    UserResponse me() {
        return mapper.user(appointmentService.defaultUser());
    }
}
