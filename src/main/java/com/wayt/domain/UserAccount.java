package com.wayt.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_accounts")
public class UserAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuthProvider provider;

    @Column(nullable = false, length = 120)
    private String providerUserId;

    @Column(nullable = false, unique = true, length = 40)
    private String waytId;

    @Column(nullable = false, length = 80)
    private String nickname;

    @Column(length = 500)
    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubscriptionTier subscriptionTier = SubscriptionTier.FREE;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, columnDefinition = "varchar(20)")
    private TravelMode defaultTravelMode;

    @Column(nullable = false)
    private boolean travelModeOnboardingCompleted;

    @Column(nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    protected UserAccount() {
    }

    public UserAccount(AuthProvider provider, String providerUserId, String waytId, String nickname, String avatarUrl) {
        this.provider = provider;
        this.providerUserId = providerUserId;
        this.waytId = waytId;
        this.nickname = nickname;
        this.avatarUrl = avatarUrl;
    }

    public UUID getId() {
        return id;
    }

    public AuthProvider getProvider() {
        return provider;
    }

    public String getProviderUserId() {
        return providerUserId;
    }

    public String getWaytId() {
        return waytId;
    }

    public String getNickname() {
        return nickname;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public SubscriptionTier getSubscriptionTier() {
        return subscriptionTier;
    }

    public TravelMode getDefaultTravelMode() {
        return defaultTravelMode;
    }

    public boolean isTravelModeOnboardingCompleted() {
        return travelModeOnboardingCompleted;
    }

    public void updateProfile(String nickname, String avatarUrl) {
        this.nickname = nickname;
        this.avatarUrl = avatarUrl;
    }

    public void updateTravelModePreference(TravelMode defaultTravelMode, boolean onboardingCompleted) {
        this.defaultTravelMode = defaultTravelMode == TravelMode.UNKNOWN ? null : defaultTravelMode;
        this.travelModeOnboardingCompleted = onboardingCompleted;
    }

    public void changeWaytId(String waytId) {
        this.waytId = waytId;
    }

    public void changeSubscriptionTier(SubscriptionTier subscriptionTier) {
        this.subscriptionTier = subscriptionTier == null ? SubscriptionTier.FREE : subscriptionTier;
    }
}
