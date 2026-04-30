package com.wayt.domain;

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
@Table(name = "location_samples")
public class LocationSample {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Appointment appointment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Participant participant;

    private double latitude;
    private double longitude;
    private double accuracyMeters;
    private OffsetDateTime capturedAt;

    protected LocationSample() {
    }

    public LocationSample(Appointment appointment, Participant participant, double latitude, double longitude, double accuracyMeters, OffsetDateTime capturedAt) {
        this.appointment = appointment;
        this.participant = participant;
        this.latitude = latitude;
        this.longitude = longitude;
        this.accuracyMeters = accuracyMeters;
        this.capturedAt = capturedAt;
    }

    public UUID getId() {
        return id;
    }

    public Appointment getAppointment() {
        return appointment;
    }

    public Participant getParticipant() {
        return participant;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public double getAccuracyMeters() {
        return accuracyMeters;
    }

    public OffsetDateTime getCapturedAt() {
        return capturedAt;
    }
}
