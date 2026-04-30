package com.wayt.dto;

import com.wayt.domain.SubscriptionTier;
import com.wayt.domain.TravelMode;

import java.util.UUID;

public record UserResponse(
        UUID id,
        String waytId,
        String nickname,
        String avatarUrl,
        SubscriptionTier subscriptionTier,
        TravelMode defaultTravelMode,
        boolean travelModeOnboardingCompleted
) {
}
