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
@Table(name = "arrival_records")
public class ArrivalRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Appointment appointment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Participant participant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ArrivalSource source;

    @Column(nullable = false)
    private OffsetDateTime arrivedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Punctuality punctuality;

    @Column(nullable = false)
    private long lateMinutes;

    protected ArrivalRecord() {
    }

    public ArrivalRecord(Appointment appointment, Participant participant, ArrivalSource source, OffsetDateTime arrivedAt, Punctuality punctuality, long lateMinutes) {
        this.appointment = appointment;
        this.participant = participant;
        this.source = source;
        this.arrivedAt = arrivedAt;
        this.punctuality = punctuality;
        this.lateMinutes = lateMinutes;
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

    public ArrivalSource getSource() {
        return source;
    }

    public OffsetDateTime getArrivedAt() {
        return arrivedAt;
    }

    public Punctuality getPunctuality() {
        return punctuality;
    }

    public long getLateMinutes() {
        return lateMinutes;
    }
}
