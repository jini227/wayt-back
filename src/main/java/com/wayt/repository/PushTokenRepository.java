package com.wayt.repository;

import com.wayt.domain.PushToken;
import com.wayt.domain.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PushTokenRepository extends JpaRepository<PushToken, UUID> {
    Optional<PushToken> findByToken(String token);

    List<PushToken> findByUserAccountAndInvalidatedAtIsNull(UserAccount userAccount);
}
