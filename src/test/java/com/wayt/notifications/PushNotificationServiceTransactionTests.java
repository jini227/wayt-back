package com.wayt.notifications;

import com.wayt.repository.NotificationJobRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;

@SpringBootTest(properties = {
        "spring.task.scheduling.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:push-notification-service-tests;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
class PushNotificationServiceTransactionTests {
    @Autowired
    private PushNotificationService pushNotificationService;

    @MockBean
    private NotificationJobRepository jobRepository;

    @MockBean
    private PushGateway pushGateway;

    @Test
    void scheduledSendDueJobsRunsRepositoryQueryWithinTransaction() {
        AtomicBoolean transactionActive = new AtomicBoolean(false);
        Thread testThread = Thread.currentThread();
        doAnswer(invocation -> {
            if (Thread.currentThread() == testThread) {
                transactionActive.set(TransactionSynchronizationManager.isActualTransactionActive());
            }
            return List.of();
        }).when(jobRepository).lockDueJobs(anyCollection(), any(), any());

        pushNotificationService.scheduledSendDueJobs();

        assertThat(transactionActive.get()).isTrue();
    }

    @Test
    void scheduledReceiptCheckRunsRepositoryQueryWithinTransaction() {
        AtomicBoolean transactionActive = new AtomicBoolean(false);
        Thread testThread = Thread.currentThread();
        doAnswer(invocation -> {
            if (Thread.currentThread() == testThread) {
                transactionActive.set(TransactionSynchronizationManager.isActualTransactionActive());
            }
            return List.of();
        }).when(jobRepository).findSentJobsWaitingForReceipt(any());
        doReturn(Map.of()).when(pushGateway).receipts(anyList());

        pushNotificationService.scheduledReceiptCheck();

        assertThat(transactionActive.get()).isTrue();
    }
}
