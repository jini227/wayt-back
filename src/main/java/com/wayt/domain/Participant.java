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
@Table(name = "participants")
public class Participant {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Appointment appointment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private UserAccount userAccount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ParticipantRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20) default 'ACTIVE'")
    private ParticipantMembershipStatus membershipStatus = ParticipantMembershipStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ParticipantStatus status = ParticipantStatus.WAITING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20)")
    private TravelMode travelMode = TravelMode.UNKNOWN;

    @Column(nullable = false)
    private boolean locationConsent;

    @Column(nullable = false)
    private OffsetDateTime joinedAt = OffsetDateTime.now();

    private OffsetDateTime leftAt;

    private OffsetDateTime removedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    private UserAccount removedBy;

    private OffsetDateTime etaCalculatedAt;

    private OffsetDateTime etaNextEligibleAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private EtaRefreshPolicy etaRefreshPolicy = EtaRefreshPolicy.FREE_ONE_TIME_WITH_EDGE_CHECK;

    @Column(nullable = false)
    private int etaApiCallCount;

    private OffsetDateTime manualEstimatedArrivalAt;

    private OffsetDateTime manualEtaUpdatedAt;

    protected Participant() {
    }

    public Participant(Appointment appointment, UserAccount userAccount, ParticipantRole role, boolean locationConsent) {
        this.appointment = appointment;
        this.userAccount = userAccount;
        this.role = role;
        this.locationConsent = locationConsent;
    }

    public UUID getId() {
        return id;
    }

    public Appointment getAppointment() {
        return appointment;
    }

    public UserAccount getUserAccount() {
        return userAccount;
    }

    public ParticipantRole getRole() {
        return role;
    }

    public ParticipantMembershipStatus getMembershipStatus() {
        return membershipStatus;
    }

    public ParticipantStatus getStatus() {
        return status;
    }

    public TravelMode getTravelMode() {
        return travelMode;
    }

    public boolean isLocationConsent() {
        return locationConsent;
    }

    public OffsetDateTime getJoinedAt() {
        return joinedAt;
    }

    public OffsetDateTime getLeftAt() {
        return leftAt;
    }

    public OffsetDateTime getRemovedAt() {
        return removedAt;
    }

    public UserAccount getRemovedBy() {
        return removedBy;
    }

    public OffsetDateTime getEtaCalculatedAt() {
        return etaCalculatedAt;
    }

    public OffsetDateTime getEtaNextEligibleAt() {
        return etaNextEligibleAt;
    }

    public EtaRefreshPolicy getEtaRefreshPolicy() {
        return etaRefreshPolicy;
    }

    public int getEtaApiCallCount() {
        return etaApiCallCount;
    }

    public OffsetDateTime getManualEstimatedArrivalAt() {
        return manualEstimatedArrivalAt;
    }

    public OffsetDateTime getManualEtaUpdatedAt() {
        return manualEtaUpdatedAt;
    }

    public boolean isActiveMembership() {
        return membershipStatus == ParticipantMembershipStatus.ACTIVE;
    }

    public void changeRole(ParticipantRole role) {
        this.role = role == null ? ParticipantRole.PARTICIPANT : role;
    }

    public void changeTravelMode(TravelMode travelMode) {
        this.travelMode = travelMode == null ? TravelMode.UNKNOWN : travelMode;
    }

    public void updateStatus(ParticipantStatus status) {
        this.status = status;
    }

    public void consentLocation() {
        this.locationConsent = true;
    }

    public void revokeLocationConsent() {
        this.locationConsent = false;
    }

    public void leave(OffsetDateTime leftAt) {
        this.membershipStatus = ParticipantMembershipStatus.LEFT;
        this.leftAt = leftAt == null ? OffsetDateTime.now() : leftAt;
        this.removedAt = null;
        this.removedBy = null;
    }

    public void remove(UserAccount removedBy, OffsetDateTime removedAt) {
        this.membershipStatus = ParticipantMembershipStatus.REMOVED;
        this.removedAt = removedAt == null ? OffsetDateTime.now() : removedAt;
        this.removedBy = removedBy;
        this.leftAt = null;
    }

    public void reactivate(ParticipantRole role, OffsetDateTime joinedAt) {
        this.membershipStatus = ParticipantMembershipStatus.ACTIVE;
        changeRole(role);
        this.joinedAt = joinedAt == null ? OffsetDateTime.now() : joinedAt;
        this.leftAt = null;
        this.removedAt = null;
        this.removedBy = null;
    }

    public void recordEtaCalculation(EtaRefreshPolicy policy, OffsetDateTime calculatedAt, OffsetDateTime nextEligibleAt) {
        this.etaRefreshPolicy = policy;
        this.etaCalculatedAt = calculatedAt;
        this.etaNextEligibleAt = nextEligibleAt;
        this.etaApiCallCount += 1;
    }

    public void updateManualEta(OffsetDateTime estimatedArrivalAt, OffsetDateTime updatedAt) {
        if (estimatedArrivalAt == null) {
            this.manualEstimatedArrivalAt = null;
            this.manualEtaUpdatedAt = null;
            return;
        }
        this.manualEstimatedArrivalAt = estimatedArrivalAt;
        this.manualEtaUpdatedAt = updatedAt == null ? OffsetDateTime.now() : updatedAt;
    }
}
