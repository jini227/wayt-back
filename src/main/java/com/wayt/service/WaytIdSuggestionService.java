package com.wayt.service;

import com.wayt.domain.UserAccount;
import com.wayt.dto.MiscDtos;
import com.wayt.repository.UserAccountRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class WaytIdSuggestionService {
    static final int MAX_REQUESTS_PER_WINDOW = 20;

    private static final int MAX_LIMIT = 4;
    private static final int CACHE_FETCH_LIMIT = MAX_LIMIT + 1;
    private static final int MAX_CACHE_ENTRIES = 512;
    private static final Duration CACHE_TTL = Duration.ofSeconds(10);
    private static final Duration RATE_WINDOW = Duration.ofSeconds(10);
    private static final UUID ANONYMOUS_RATE_LIMIT_KEY = new UUID(0L, 0L);

    private final UserAccountRepository userAccountRepository;
    private final Clock clock;
    private final ConcurrentHashMap<String, CachedUsers> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CompletableFuture<List<SuggestionCandidate>>> inFlight = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, RateWindow> rateWindows = new ConcurrentHashMap<>();

    @Autowired
    public WaytIdSuggestionService(UserAccountRepository userAccountRepository) {
        this(userAccountRepository, Clock.systemUTC());
    }

    WaytIdSuggestionService(UserAccountRepository userAccountRepository, Clock clock) {
        this.userAccountRepository = userAccountRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<MiscDtos.WaytIdSuggestionResponse> suggestions(UserAccount viewer, String query, Integer limit) {
        Instant now = clock.instant();
        String prefix = normalizePrefix(query);
        if (prefix.isBlank() || !allowRequest(viewer, now)) {
            return List.of();
        }

        int size = Math.max(1, Math.min(limit == null ? MAX_LIMIT : limit, MAX_LIMIT));
        return cachedUsers(prefix, now).stream()
                .filter(user -> viewer.getId() == null || !viewer.getId().equals(user.id()))
                .limit(size)
                .map(user -> new MiscDtos.WaytIdSuggestionResponse(
                        user.id(),
                        user.waytId(),
                        user.nickname(),
                        user.avatarUrl()
                ))
                .toList();
    }

    private List<SuggestionCandidate> cachedUsers(String prefix, Instant now) {
        String cacheKey = prefix + ":" + CACHE_FETCH_LIMIT;
        CachedUsers cached = cache.get(cacheKey);
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return cached.users();
        }

        CompletableFuture<List<SuggestionCandidate>> loading = new CompletableFuture<>();
        CompletableFuture<List<SuggestionCandidate>> existing = inFlight.putIfAbsent(cacheKey, loading);
        if (existing != null) {
            return existing.join();
        }

        try {
            List<SuggestionCandidate> users = userAccountRepository.findWaytIdSuggestions(
                            prefix,
                            prefix + Character.MAX_VALUE,
                            PageRequest.of(0, CACHE_FETCH_LIMIT)
                    ).stream()
                    .map(user -> new SuggestionCandidate(
                            user.getId(),
                            user.getWaytId(),
                            user.getNickname(),
                            user.getAvatarUrl()
                    ))
                    .toList();
            cache.put(cacheKey, new CachedUsers(users, now.plus(CACHE_TTL)));
            trimCache(now);
            loading.complete(users);
            return users;
        } catch (RuntimeException exception) {
            loading.completeExceptionally(exception);
            throw exception;
        } finally {
            inFlight.remove(cacheKey, loading);
        }
    }

    private boolean allowRequest(UserAccount viewer, Instant now) {
        UUID key = viewer.getId() == null ? ANONYMOUS_RATE_LIMIT_KEY : viewer.getId();
        RateDecision decision = new RateDecision();
        rateWindows.compute(key, (ignored, window) -> {
            if (window == null || !window.startedAt().plus(RATE_WINDOW).isAfter(now)) {
                decision.allowed = true;
                return new RateWindow(now, 1);
            }
            if (window.count() >= MAX_REQUESTS_PER_WINDOW) {
                decision.allowed = false;
                return window;
            }
            decision.allowed = true;
            return new RateWindow(window.startedAt(), window.count() + 1);
        });
        return decision.allowed;
    }

    private void trimCache(Instant now) {
        if (cache.size() <= MAX_CACHE_ENTRIES) {
            return;
        }
        for (Map.Entry<String, CachedUsers> entry : cache.entrySet()) {
            if (!entry.getValue().expiresAt().isAfter(now)) {
                cache.remove(entry.getKey(), entry.getValue());
            }
        }
        if (cache.size() <= MAX_CACHE_ENTRIES) {
            return;
        }

        Map<String, CachedUsers> snapshot = new LinkedHashMap<>(cache);
        int removeCount = cache.size() - MAX_CACHE_ENTRIES;
        for (String key : snapshot.keySet()) {
            cache.remove(key);
            removeCount--;
            if (removeCount <= 0) {
                return;
            }
        }
    }

    private String normalizePrefix(String query) {
        if (query == null) {
            return "";
        }
        String value = query.trim().toLowerCase();
        if (value.isBlank()) {
            return "";
        }
        return value.startsWith("@") ? value : "@" + value;
    }

    private record CachedUsers(List<SuggestionCandidate> users, Instant expiresAt) {
    }

    private record SuggestionCandidate(UUID id, String waytId, String nickname, String avatarUrl) {
    }

    private record RateWindow(Instant startedAt, int count) {
    }

    private static final class RateDecision {
        private boolean allowed;
    }
}
