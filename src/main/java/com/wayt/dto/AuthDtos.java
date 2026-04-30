package com.wayt.dto;

import com.wayt.domain.TravelMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {
    }

    public record KakaoLoginRequest(
            @NotBlank String kakaoAccessToken,
            String providerUserId,
            String nickname,
            String avatarUrl
    ) {
    }

    public record AuthResponse(
            UserResponse user,
            String accessToken,
            String refreshToken
    ) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record KakaoCallbackRequest(
            @NotBlank String code,
            String redirectUri
    ) {
    }

    public record ProfileUpdateRequest(
            @Size(max = 6) String nickname,
            @Size(max = 500) String avatarUrl,
            TravelMode defaultTravelMode,
            Boolean travelModeOnboardingCompleted
    ) {
    }
}
