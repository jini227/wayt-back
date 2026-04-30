package com.wayt.service;

import com.wayt.domain.AuthProvider;
import com.wayt.domain.UserAccount;
import com.wayt.dto.MiscDtos;
import com.wayt.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WaytIdSuggestionServiceTests {
    @Test
    void concurrentSamePrefixRequestsShareOneRepositoryLookup() throws Exception {
        UserAccountRepository repository = mock(UserAccountRepository.class);
        WaytIdSuggestionService service = new WaytIdSuggestionService(
                repository,
                Clock.fixed(Instant.parse("2026-04-30T00:00:00Z"), ZoneOffset.UTC)
        );
        AtomicInteger lookups = new AtomicInteger();
        CountDownLatch lookupStarted = new CountDownLatch(1);
        CountDownLatch releaseLookup = new CountDownLatch(1);
        UserAccount viewer = user("@viewer", "Viewer");

        when(repository.findWaytIdSuggestions(eq("@hy"), eq("@hy" + Character.MAX_VALUE), any(Pageable.class)))
                .thenAnswer(invocation -> {
                    lookups.incrementAndGet();
                    lookupStarted.countDown();
                    assertThat(releaseLookup.await(2, TimeUnit.SECONDS)).isTrue();
                    return List.of(user("@hy_01", "Hy One"), user("@hy_02", "Hy Two"));
                });

        var executor = Executors.newFixedThreadPool(8);
        List<java.util.concurrent.Future<List<MiscDtos.WaytIdSuggestionResponse>>> futures = new ArrayList<>();
        for (int index = 0; index < 8; index++) {
            futures.add(executor.submit(() -> service.suggestions(viewer, "hy", 4)));
        }

        assertThat(lookupStarted.await(2, TimeUnit.SECONDS)).isTrue();
        releaseLookup.countDown();
        for (var future : futures) {
            assertThat(future.get(2, TimeUnit.SECONDS))
                    .extracting(MiscDtos.WaytIdSuggestionResponse::waytId)
                    .containsExactly("@hy_01", "@hy_02");
        }
        executor.shutdownNow();

        assertThat(lookups).hasValue(1);
    }

    @Test
    void repeatedRequestsPastThePerUserWindowReturnEmptyWithoutRepositoryLookup() throws Exception {
        UserAccountRepository repository = mock(UserAccountRepository.class);
        WaytIdSuggestionService service = new WaytIdSuggestionService(
                repository,
                Clock.fixed(Instant.parse("2026-04-30T00:00:00Z"), ZoneOffset.UTC)
        );
        UserAccount viewer = user("@viewer", "Viewer");
        when(repository.findWaytIdSuggestions(anyString(), anyString(), any(Pageable.class))).thenReturn(List.of());

        for (int index = 0; index < WaytIdSuggestionService.MAX_REQUESTS_PER_WINDOW; index++) {
            service.suggestions(viewer, "rate_" + index, 4);
        }

        assertThat(service.suggestions(viewer, "rate_over_limit", 4)).isEmpty();
        verify(repository, times(WaytIdSuggestionService.MAX_REQUESTS_PER_WINDOW))
                .findWaytIdSuggestions(anyString(), anyString(), any(Pageable.class));
    }

    private UserAccount user(String waytId, String nickname) throws Exception {
        UserAccount user = new UserAccount(AuthProvider.KAKAO, "provider-" + waytId, waytId, nickname, null);
        Field id = UserAccount.class.getDeclaredField("id");
        id.setAccessible(true);
        id.set(user, UUID.nameUUIDFromBytes(waytId.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        return user;
    }
}
