package com.wayt.notifications;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wayt.domain.NotificationJob;
import com.wayt.repository.NotificationJobRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

@Service
public class PushNotificationService {
    private static final int SEND_BATCH_SIZE = 50;
    private static final int RECEIPT_BATCH_SIZE = 100;

    private final NotificationJobRepository jobRepository;
    private final PushGateway pushGateway;
    private final PushRetryPolicy retryPolicy;
    private final NotificationReceiptHandler receiptHandler;
    private final ObjectMapper objectMapper;

    public PushNotificationService(NotificationJobRepository jobRepository, PushGateway pushGateway, ObjectMapper objectMapper) {
        this.jobRepository = jobRepository;
        this.pushGateway = pushGateway;
        this.objectMapper = objectMapper;
        this.retryPolicy = new PushRetryPolicy();
        this.receiptHandler = new NotificationReceiptHandler();
    }

    @Scheduled(fixedDelayString = "${wayt.notifications.send-delay-ms:30000}")
    @Transactional
    public void scheduledSendDueJobs() {
        sendDueJobs(SEND_BATCH_SIZE);
    }

    @Scheduled(fixedDelayString = "${wayt.notifications.receipt-delay-ms:60000}")
    @Transactional
    public void scheduledReceiptCheck() {
        checkReceipts(RECEIPT_BATCH_SIZE);
    }

    @Transactional
    public int sendDueJobs(int batchSize) {
        List<NotificationJob> jobs = jobRepository.lockDueJobs(
                EnumSet.of(NotificationJobStatus.PENDING, NotificationJobStatus.RETRYING),
                OffsetDateTime.now(),
                PageRequest.of(0, batchSize)
        );
        int processed = 0;
        for (NotificationJob job : jobs) {
            job.markSending();
            sendJob(job);
            processed += 1;
        }
        return processed;
    }

    @Transactional
    public int checkReceipts(int batchSize) {
        List<NotificationJob> jobs = jobRepository.findSentJobsWaitingForReceipt(PageRequest.of(0, batchSize));
        List<String> ticketIds = jobs.stream().map(NotificationJob::getTicketId).toList();
        Map<String, PushReceipt> receipts = pushGateway.receipts(ticketIds);
        int processed = 0;
        for (NotificationJob job : jobs) {
            PushReceipt receipt = receipts.get(job.getTicketId());
            if (receipt == null) {
                continue;
            }
            receiptHandler.applyReceipt(job, receipt);
            processed += 1;
        }
        return processed;
    }

    private void sendJob(NotificationJob job) {
        try {
            PushTicket ticket = pushGateway.send(new PushMessage(
                    job.getPushToken().getToken(),
                    job.getTitle(),
                    job.getBody(),
                    dataMap(job)
            ));
            job.markTicket(ticket);
        } catch (PushGatewayException exception) {
            if (retryPolicy.shouldRetry(exception.failure())) {
                job.markRetry(retryPolicy.backoffDelay(job.getRetryCount()), "PushGatewayTransient", exception.getMessage());
            } else {
                job.markFailed("PushGatewayPermanent", exception.getMessage());
            }
        } catch (RuntimeException exception) {
            job.markRetry(retryPolicy.backoffDelay(job.getRetryCount()), "PushGatewayNetwork", exception.getMessage());
        }
    }

    private Map<String, Object> dataMap(NotificationJob job) {
        try {
            return objectMapper.readValue(job.getDataJson(), new TypeReference<>() {
            });
        } catch (Exception exception) {
            return Map.of();
        }
    }
}
