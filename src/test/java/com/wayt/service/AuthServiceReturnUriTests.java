package com.wayt.service;

import com.wayt.repository.UserAccountRepository;
import com.wayt.support.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

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
    void kakaoAuthorizeUriRejectsUnexpectedWebReturnUri() {
        AuthService service = authService();

        assertThatThrownBy(() -> service.kakaoAuthorizeUri("http://52.79.233.46/dotori/auth/kakao"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Unsupported returnUri scheme");
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
}
