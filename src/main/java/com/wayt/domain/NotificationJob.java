package com.wayt.domain;

import com.wayt.notifications.NotificationJobStatus;
import com.wayt.notifications.PushTicket;
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

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "notification_jobs")
public class NotificationJob {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private UserAccount recipient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private PushToken pushToken;

    @ManyToOne(fetch = FetchType.LAZY)
    private Appointment appointment;

    @Column(nullable = false, length = 80)
    private String notificationType;

    @Column(nullable = false, unique = true, length = 240)
    private String eventKey;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 500)
    private String body;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String dataJson;

    @Column(nullable = false)
    private OffsetDateTime scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationJobStatus status = NotificationJobStatus.PENDING;

    @Column(nullable = false)
    private int retryCount;

    private OffsetDateTime nextAttemptAt;

    @Column(length = 120)
    private String ticketId;

    @Column(length = 30)
    private String receiptStatus;

    @Column(length = 120)
    private String errorCode;

    @Column(length = 500)
    private String errorMessage;

    @Column(nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    private OffsetDateTime sentAt;

    protected NotificationJob() {
    }

    public static NotificationJob pending(
            UserAccount recipient,
            PushToken pushToken,
            String notificationType,
            String eventKey,
            String title,
            String body,
            String dataJson,
            OffsetDateTime scheduledAt
    ) {
        NotificationJob job = new NotificationJob();
        job.recipient = recipient;
        job.pushToken = pushToken;
        job.appointment = null;
        job.notificationType = notificationType;
        job.eventKey = eventKey;
        job.title = title;
        job.body = body;
        job.dataJson = dataJson;
        job.scheduledAt = scheduledAt == null ? OffsetDateTime.now() : scheduledAt;
        return job;
    }

    public static NotificationJob pendingForAppointment(
            UserAccount recipient,
            PushToken pushToken,
            Appointment appointment,
            String notificationType,
            String eventKey,
            String title,
            String body,
            String dataJson,
            OffsetDateTime scheduledAt
    ) {
        NotificationJob job = pending(recipient, pushToken, notificationType, eventKey, title, body, dataJson, scheduledAt);
        job.appointment = appointment;
        return job;
    }

    public UUID getId() {
        return id;
    }

    public UserAccount getRecipient() {
        return recipient;
    }

    public PushToken getPushToken() {
        return pushToken;
    }

    public Appointment getAppointment() {
        return appointment;
    }

    public String getNotificationType() {
        return notificationType;
    }

    public String getEventKey() {
        return eventKey;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public String getDataJson() {
        return dataJson;
    }

    public OffsetDateTime getScheduledAt() {
        return scheduledAt;
    }

    public NotificationJobStatus getStatus() {
        return status;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public OffsetDateTime getNextAttemptAt() {
        return nextAttemptAt;
    }

    public String getTicketId() {
        return ticketId;
    }

    public String getReceiptStatus() {
        return receiptStatus;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public OffsetDateTime getSentAt() {
        return sentAt;
    }

    public void markSending() {
        status = NotificationJobStatus.SENDING;
        errorCode = null;
        errorMessage = null;
        updatedAt = OffsetDateTime.now();
    }

    public void markTicket(PushTicket ticket) {
        if (ticket.ok()) {
            ticketId = ticket.id();
            status = NotificationJobStatus.SENT;
            sentAt = OffsetDateTime.now();
            updatedAt = sentAt;
            return;
        }
        markFailed(ticket.errorCode(), ticket.message());
    }

    public void markRetry(Duration delay, String errorCode, String errorMessage) {
        retryCount += 1;
        status = NotificationJobStatus.RETRYING;
        nextAttemptAt = OffsetDateTime.now().plus(delay);
        this.errorCode = errorCode;
        this.errorMessage = truncate(errorMessage, 500);
        updatedAt = OffsetDateTime.now();
    }

    public void markFailed(String errorCode, String errorMessage) {
        status = NotificationJobStatus.FAILED;
        this.errorCode = errorCode;
        this.errorMessage = truncate(errorMessage, 500);
        updatedAt = OffsetDateTime.now();
    }

    public void markReceiptOk() {
        status = NotificationJobStatus.RECEIPT_OK;
        receiptStatus = "ok";
        errorCode = null;
        errorMessage = null;
        updatedAt = OffsetDateTime.now();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
