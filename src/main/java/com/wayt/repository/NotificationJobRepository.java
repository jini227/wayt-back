package com.wayt.repository;

import com.wayt.domain.NotificationJob;
import com.wayt.notifications.NotificationJobStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationJobRepository extends JpaRepository<NotificationJob, UUID> {
    boolean existsByEventKey(String eventKey);

    Optional<NotificationJob> findByTicketId(String ticketId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select job
            from NotificationJob job
            where job.status in :statuses
              and job.scheduledAt <= :now
              and (job.nextAttemptAt is null or job.nextAttemptAt <= :now)
              and job.pushToken.invalidatedAt is null
            order by job.createdAt asc
            """)
    List<NotificationJob> lockDueJobs(Collection<NotificationJobStatus> statuses, OffsetDateTime now, Pageable pageable);

    @Query("""
            select job
            from NotificationJob job
            where job.status = com.wayt.notifications.NotificationJobStatus.SENT
              and job.ticketId is not null
            order by job.sentAt asc
            """)
    List<NotificationJob> findSentJobsWaitingForReceipt(Pageable pageable);
}
