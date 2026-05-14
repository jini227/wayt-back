package com.wayt.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wayt.domain.AuthProvider;
import com.wayt.domain.UserAccount;
import com.wayt.dto.UserResponse;
import com.wayt.repository.UserAccountRepository;
import com.wayt.support.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthServiceReturnUriTests {
    @Test
    void kakaoAuthorizeUriAllowsProductionWebReturnUri() {
        AuthService service = authService();

        var uri = service.kakaoAuthorizeUri("http://52.79.233.46/wayt/auth/kakao");

        assertThat(uri.getScheme()).isEqualTo("https");
        assertThat(uri.getHost()).isEqualTo("kauth.kakao.com");
        assertThat(uri.getQuery()).contains("client_id=test-rest-api-key");
        assertThat(decodedState(uri.getQuery())).isEqualTo("http://52.79.233.46/wayt/auth/kakao");
    }

    @Test
    void kakaoAuthorizeUriAllowsLocalWebReturnUri() {
        AuthService service = authService();

        var uri = service.kakaoAuthorizeUri("http://localhost:5173/auth/kakao");

        assertThat(uri.getHost()).isEqualTo("kauth.kakao.com");
        assertThat(decodedState(uri.getQuery())).isEqualTo("http://localhost:5173/auth/kakao");
    }

    @Test
    void kakaoAuthorizeUriRequestsKoreanConsentScreen() {
        AuthService service = authService();

        var uri = service.kakaoAuthorizeUri("http://52.79.233.46/wayt/auth/kakao");

        assertThat(uri.getQuery()).contains("lang=ko");
    }

    @Test
    void kakaoAuthorizeUriRejectsUnexpectedWebReturnUri() {
        AuthService service = authService();

        assertThatThrownBy(() -> service.kakaoAuthorizeUri("http://52.79.233.46/dotori/auth/kakao"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Unsupported returnUri scheme");
    }

    @Test
    void uniqueWaytIdUsesRandomOnlyFallbackForKoreanNickname() {
        AuthService service = authService();

        String waytId = ReflectionTestUtils.invokeMethod(service, "uniqueWaytId", "민수");

        assertThat(waytId).doesNotStartWith("@wayt");
        assertThat(waytId).doesNotStartWith("@user");
        assertThat(waytId).matches("@[0-9a-f]{6}");
    }

    @Test
    void kakaoLoginRegeneratesExistingNonAsciiWaytId() throws Exception {
        UserAccountRepository repository = mock(UserAccountRepository.class);
        ResponseMapper mapper = mock(ResponseMapper.class);
        AuthService service = new AuthService(repository, mapper);
        UserAccount existing = new UserAccount(AuthProvider.KAKAO, "12345", "@김효진3064dd", "김효진", null);
        when(repository.findByProviderAndProviderUserId(AuthProvider.KAKAO, "12345")).thenReturn(Optional.of(existing));
        when(repository.existsByWaytId(anyString())).thenReturn(false);
        when(mapper.user(existing)).thenAnswer(invocation -> new UserResponse(
                existing.getId(),
                existing.getWaytId(),
                existing.getNickname(),
                existing.getAvatarUrl(),
                existing.getSubscriptionTier(),
                existing.getDefaultTravelMode(),
                existing.isTravelModeOnboardingCompleted()
        ));
        var kakaoUser = new ObjectMapper().readTree("""
                {
                  "id": 12345,
                  "kakao_account": {
                    "profile": {
                      "nickname": "김효진"
                    }
                  }
                }
                """);

        ReflectionTestUtils.invokeMethod(service, "loginWithKakaoUser", kakaoUser, null, null);

        assertThat(existing.getWaytId()).doesNotStartWith("@wayt");
        assertThat(existing.getWaytId()).doesNotStartWith("@user");
        assertThat(existing.getWaytId()).matches("@[0-9a-f]{6}");
    }

    @Test
    void sessionRegeneratesExistingNonAsciiWaytId() {
        UserAccountRepository repository = mock(UserAccountRepository.class);
        ResponseMapper mapper = mock(ResponseMapper.class);
        AuthService service = new AuthService(repository, mapper);
        UUID userId = UUID.randomUUID();
        UserAccount existing = new UserAccount(AuthProvider.KAKAO, "12345", "@김효진3064dd", "김효진", null);
        ReflectionTestUtils.setField(existing, "id", userId);
        when(repository.findById(userId)).thenReturn(Optional.of(existing));
        when(repository.existsByWaytId(anyString())).thenReturn(false);
        when(mapper.user(existing)).thenAnswer(invocation -> new UserResponse(
                existing.getId(),
                existing.getWaytId(),
                existing.getNickname(),
                existing.getAvatarUrl(),
                existing.getSubscriptionTier(),
                existing.getDefaultTravelMode(),
                existing.isTravelModeOnboardingCompleted()
        ));

        service.session("Bearer " + accessToken(userId));

        assertThat(existing.getWaytId()).doesNotStartWith("@wayt");
        assertThat(existing.getWaytId()).doesNotStartWith("@user");
        assertThat(existing.getWaytId()).matches("@[0-9a-f]{6}");
    }

    @Test
    void sessionRegeneratesExistingFixedFallbackWaytId() {
        UserAccountRepository repository = mock(UserAccountRepository.class);
        ResponseMapper mapper = mock(ResponseMapper.class);
        AuthService service = new AuthService(repository, mapper);
        UUID userId = UUID.randomUUID();
        UserAccount existing = new UserAccount(AuthProvider.KAKAO, "12345", "@wayt3064dd", "김효진", null);
        ReflectionTestUtils.setField(existing, "id", userId);
        when(repository.findById(userId)).thenReturn(Optional.of(existing));
        when(repository.existsByWaytId(anyString())).thenReturn(false);
        when(mapper.user(existing)).thenAnswer(invocation -> new UserResponse(
                existing.getId(),
                existing.getWaytId(),
                existing.getNickname(),
                existing.getAvatarUrl(),
                existing.getSubscriptionTier(),
                existing.getDefaultTravelMode(),
                existing.isTravelModeOnboardingCompleted()
        ));

        service.session("Bearer " + accessToken(userId));

        assertThat(existing.getWaytId()).doesNotStartWith("@wayt");
        assertThat(existing.getWaytId()).doesNotStartWith("@user");
        assertThat(existing.getWaytId()).matches("@[0-9a-f]{6}");
    }

    private AuthService authService() {
        AuthService service = new AuthService(mock(UserAccountRepository.class), mock(ResponseMapper.class));
        ReflectionTestUtils.setField(service, "kakaoRestApiKey", "test-rest-api-key");
        ReflectionTestUtils.setField(service, "kakaoRedirectUri", "http://52.79.233.46/wayt-api/api/auth/kakao/callback");
        ReflectionTestUtils.setField(service, "kakaoScopes", "profile_nickname,profile_image");
        return service;
    }

    private String decodedState(String query) {
        String state = java.util.Arrays.stream(query.split("&"))
                .filter(part -> part.startsWith("state="))
                .map(part -> part.substring("state=".length()))
                .findFirst()
                .orElseThrow();
        return new String(Base64.getUrlDecoder().decode(state), java.nio.charset.StandardCharsets.UTF_8);
    }

    private String accessToken(UUID userId) {
        String raw = "access:" + userId + ":0:test";
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
