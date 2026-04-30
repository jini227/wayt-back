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
@Table(name = "invites")
public class Invite {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Appointment appointment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private UserAccount inviter;

    @ManyToOne(fetch = FetchType.LAZY)
    private UserAccount invitee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InviteType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InviteStatus status = InviteStatus.PENDING;

    @Column(nullable = false, unique = true, length = 80)
    private String token;

    @Column(length = 40)
    private String targetWaytId;

    @Column(nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    protected Invite() {
    }

    public Invite(Appointment appointment, UserAccount inviter, InviteType type, String token, UserAccount invitee, String targetWaytId) {
        this.appointment = appointment;
        this.inviter = inviter;
        this.type = type;
        this.token = token;
        this.invitee = invitee;
        this.targetWaytId = targetWaytId;
    }

    public UUID getId() {
        return id;
    }

    public Appointment getAppointment() {
        return appointment;
    }

    public UserAccount getInviter() {
        return inviter;
    }

    public UserAccount getInvitee() {
        return invitee;
    }

    public InviteType getType() {
        return type;
    }

    public InviteStatus getStatus() {
        return status;
    }

    public String getToken() {
        return token;
    }

    public String getTargetWaytId() {
        return targetWaytId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void accept(UserAccount userAccount) {
        this.invitee = userAccount;
        this.status = InviteStatus.ACCEPTED;
    }

    public void decline() {
        this.status = InviteStatus.DECLINED;
    }

    public void cancel() {
        this.status = InviteStatus.CANCELLED;
    }
}
