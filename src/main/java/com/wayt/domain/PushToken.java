package com.wayt.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "push_tokens")
public class PushToken {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private UserAccount userAccount;

    @Column(nullable = false, unique = true, length = 200)
    private String token;

    @Column(nullable = false, length = 20)
    private String platform;

    @Column(nullable = false, length = 20)
    private String environment = "development";

    @Column(length = 120)
    private String deviceId;

    @Column(length = 40)
    private String appVersion;

    private OffsetDateTime lastSeenAt = OffsetDateTime.now();

    private OffsetDateTime invalidatedAt;

    @Column(nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    protected PushToken() {
    }

    public PushToken(UserAccount userAccount, String token, String platform) {
        this.userAccount = userAccount;
        this.token = token;
        this.platform = platform;
        this.lastSeenAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public UserAccount getUserAccount() {
        return userAccount;
    }

    public String getToken() {
        return token;
    }

    public String getPlatform() {
        return platform;
    }

    public String getEnvironment() {
        return environment;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public String getAppVersion() {
        return appVersion;
    }

    public OffsetDateTime getLastSeenAt() {
        return lastSeenAt;
    }

    public OffsetDateTime getInvalidatedAt() {
        return invalidatedAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public boolean active() {
        return invalidatedAt == null;
    }

    public void updateRegistration(
            UserAccount userAccount,
            String platform,
            String environment,
            String deviceId,
            String appVersion
    ) {
        this.userAccount = userAccount;
        this.platform = platform;
        this.environment = blankToDefault(environment, "development");
        this.deviceId = blankToNull(deviceId);
        this.appVersion = blankToNull(appVersion);
        this.invalidatedAt = null;
        this.lastSeenAt = OffsetDateTime.now();
        this.updatedAt = this.lastSeenAt;
    }

    public void touch(String platform) {
        updateRegistration(userAccount, platform, environment, deviceId, appVersion);
    }

    public void invalidate() {
        if (invalidatedAt == null) {
            invalidatedAt = OffsetDateTime.now();
        }
        updatedAt = OffsetDateTime.now();
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
