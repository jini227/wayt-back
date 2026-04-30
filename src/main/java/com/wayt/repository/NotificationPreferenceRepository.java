package com.wayt.repository;

import com.wayt.domain.NotificationPreference;
import com.wayt.domain.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, UUID> {
    List<NotificationPreference> findByUserAccount(UserAccount userAccount);

    Optional<NotificationPreference> findByUserAccountAndNotificationType(UserAccount userAccount, String notificationType);
}
