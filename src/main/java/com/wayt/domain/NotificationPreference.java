package com.wayt.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "notification_preferences",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_account_id", "notification_type"})
)
public class NotificationPreference {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private UserAccount userAccount;

    @Column(name = "notification_type", nullable = false, length = 80)
    private String notificationType;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    protected NotificationPreference() {
    }

    public NotificationPreference(UserAccount userAccount, String notificationType, boolean enabled) {
        this.userAccount = userAccount;
        this.notificationType = notificationType;
        this.enabled = enabled;
    }

    public UUID getId() {
        return id;
    }

    public UserAccount getUserAccount() {
        return userAccount;
    }

    public String getNotificationType() {
        return notificationType;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void changeEnabled(boolean enabled) {
        this.enabled = enabled;
        this.updatedAt = OffsetDateTime.now();
    }
}
