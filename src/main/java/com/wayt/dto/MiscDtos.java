package com.wayt.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class MiscDtos {
    private MiscDtos() {
    }

    public record AddressBookCreateRequest(
            @NotBlank String targetWaytId,
            String displayName
    ) {
    }

    public record AddressBookResponse(
            UUID id,
            UserResponse user,
            String displayName
    ) {
    }

    public record WaytIdSuggestionResponse(
            UUID id,
            String waytId,
            String nickname,
            String avatarUrl
    ) {
    }

    public record SavedPlaceCreateRequest(
            String label,
            @NotBlank String placeName,
            double latitude,
            double longitude,
            Boolean favorite
    ) {
    }

    public record SavedPlaceUpdateRequest(
            String label,
            Boolean favorite
    ) {
    }

    public record SavedPlaceResponse(
            UUID id,
            String label,
            String placeName,
            double latitude,
            double longitude,
            boolean favorite,
            OffsetDateTime lastUsedAt,
            int useCount
    ) {
    }

    public record PushTokenRequest(
            @NotBlank String token,
            @NotBlank String platform,
            String environment,
            String deviceId,
            String appVersion
    ) {
    }

    public record PushTokenResponse(
            UUID id,
            String token,
            String platform,
            String environment
    ) {
    }

    public record NotificationPreferenceItem(
            String type,
            boolean enabled
    ) {
    }

    public record NotificationPreferencesResponse(
            List<NotificationPreferenceItem> items
    ) {
    }

    public record NotificationPreferencePatchItem(
            @NotBlank String type,
            boolean enabled
    ) {
    }

    public record NotificationPreferencesPatchRequest(
            @NotNull List<@NotNull NotificationPreferencePatchItem> items
    ) {
    }

    public record ReverseGeocodeResponse(
            String displayName,
            String roadAddress,
            String jibunAddress,
            double latitude,
            double longitude
    ) {
    }

    public record MapPlaceSearchResponse(
            java.util.List<MapPlaceResponse> items
    ) {
    }

    public record MapPlaceResponse(
            String title,
            String address,
            String roadAddress,
            double latitude,
            double longitude,
            String source
    ) {
    }
}
