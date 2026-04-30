package com.wayt.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "appointments")
public class Appointment {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private UserAccount host;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 200)
    private String placeName;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(nullable = false)
    private OffsetDateTime scheduledAt;

    @Column(nullable = false)
    private int shareStartOffsetMinutes;

    @Column(nullable = false, length = 200)
    private String penalty;

    @Column(nullable = false)
    private int arrivalRadiusMeters;

    @Column(nullable = false)
    private int graceMinutes;

    @Column(columnDefinition = "TEXT")
    private String memo;

    private OffsetDateTime completedAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private AppointmentCompletionReason completionReason;

    @Column(nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    protected Appointment() {
    }

    public Appointment(
            UserAccount host,
            String title,
            String placeName,
            double latitude,
            double longitude,
            OffsetDateTime scheduledAt,
            int shareStartOffsetMinutes,
            String penalty,
            int arrivalRadiusMeters,
            int graceMinutes,
            String memo
    ) {
        this.host = host;
        this.title = title;
        this.placeName = placeName;
        this.latitude = latitude;
        this.longitude = longitude;
        this.scheduledAt = scheduledAt;
        this.shareStartOffsetMinutes = shareStartOffsetMinutes;
        this.penalty = penalty;
        this.arrivalRadiusMeters = arrivalRadiusMeters;
        this.graceMinutes = graceMinutes;
        this.memo = memo;
    }

    public UUID getId() {
        return id;
    }

    public UserAccount getHost() {
        return host;
    }

    public void transferHost(UserAccount host) {
        this.host = host;
    }

    public String getTitle() {
        return title;
    }

    public String getPlaceName() {
        return placeName;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public OffsetDateTime getScheduledAt() {
        return scheduledAt;
    }

    public int getShareStartOffsetMinutes() {
        return shareStartOffsetMinutes;
    }

    public String getPenalty() {
        return penalty;
    }

    public int getArrivalRadiusMeters() {
        return arrivalRadiusMeters;
    }

    public int getGraceMinutes() {
        return graceMinutes;
    }

    public String getMemo() {
        return memo;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }

    public AppointmentCompletionReason getCompletionReason() {
        return completionReason;
    }

    public boolean isCompleted() {
        return completedAt != null;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime locationShareStartsAt() {
        return scheduledAt.minusMinutes(shareStartOffsetMinutes);
    }

    public void complete(AppointmentCompletionReason reason, OffsetDateTime completedAt) {
        if (isCompleted()) {
            return;
        }
        this.completedAt = completedAt == null ? OffsetDateTime.now() : completedAt;
        this.completionReason = reason;
    }
}
