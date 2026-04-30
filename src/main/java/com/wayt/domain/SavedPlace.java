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
        name = "saved_places",
        uniqueConstraints = @UniqueConstraint(columnNames = {"owner_id", "place_name", "latitude", "longitude"})
)
public class SavedPlace {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private UserAccount owner;

    @Column(nullable = false, length = 80)
    private String label;

    @Column(nullable = false, length = 200)
    private String placeName;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(nullable = false)
    private boolean favorite;

    @Column(nullable = false)
    private int useCount;

    private OffsetDateTime lastUsedAt;

    @Column(nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(nullable = false)
    private OffsetDateTime updatedAt = createdAt;

    protected SavedPlace() {
    }

    public SavedPlace(
            UserAccount owner,
            String label,
            String placeName,
            double latitude,
            double longitude,
            boolean favorite
    ) {
        this.owner = owner;
        this.label = label;
        this.placeName = placeName;
        this.latitude = latitude;
        this.longitude = longitude;
        this.favorite = favorite;
    }

    public UUID getId() {
        return id;
    }

    public UserAccount getOwner() {
        return owner;
    }

    public String getLabel() {
        return label;
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

    public boolean isFavorite() {
        return favorite;
    }

    public int getUseCount() {
        return useCount;
    }

    public OffsetDateTime getLastUsedAt() {
        return lastUsedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void saveAsFavorite(String label, OffsetDateTime now) {
        this.label = label;
        this.favorite = true;
        this.updatedAt = now;
    }

    public void updateDetails(String label, boolean favorite, OffsetDateTime now) {
        this.label = label;
        this.favorite = favorite;
        this.updatedAt = now;
    }

    public void removeFavorite(OffsetDateTime now) {
        this.favorite = false;
        this.updatedAt = now;
    }

    public void markUsed(OffsetDateTime now) {
        this.useCount += 1;
        this.lastUsedAt = now;
        this.updatedAt = now;
    }

    public void clearRecentUse(OffsetDateTime now) {
        this.useCount = 0;
        this.lastUsedAt = null;
        this.updatedAt = now;
    }
}
