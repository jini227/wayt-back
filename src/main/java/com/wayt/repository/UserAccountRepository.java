package com.wayt.repository;

import com.wayt.domain.AuthProvider;
import com.wayt.domain.UserAccount;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {
    Optional<UserAccount> findByProviderAndProviderUserId(AuthProvider provider, String providerUserId);

    Optional<UserAccount> findByWaytId(String waytId);

    boolean existsByWaytId(String waytId);

    @Query("""
            select account from UserAccount account
            where account.waytId >= :prefix
              and account.waytId < :upperBound
            order by account.waytId asc
            """)
    List<UserAccount> findWaytIdSuggestions(
            @Param("prefix") String prefix,
            @Param("upperBound") String upperBound,
            Pageable pageable
    );
}
