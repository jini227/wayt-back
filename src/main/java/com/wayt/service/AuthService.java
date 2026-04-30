package com.wayt.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.wayt.domain.AuthProvider;
import com.wayt.domain.UserAccount;
import com.wayt.dto.AuthDtos;
import com.wayt.dto.UserResponse;
import com.wayt.repository.UserAccountRepository;
import com.wayt.support.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserAccountRepository userAccountRepository;
    private final ResponseMapper mapper;
    private final SecureRandom secureRandom = new SecureRandom();
    private final RestClient kakaoClient = RestClient.builder()
            .baseUrl("https://kapi.kakao.com")
            .build();
    private final RestClient kakaoAuthClient = RestClient.builder()
            .baseUrl("https://kauth.kakao.com")
            .build();

    @Value("${wayt.kakao.rest-api-key}")
    private String kakaoRestApiKey;

    @Value("${wayt.kakao.client-secret}")
    private String kakaoClientSecret;

    @Value("${wayt.kakao.redirect-uri}")
    private String kakaoRedirectUri;

    @Value("${wayt.kakao.scopes}")
    private String kakaoScopes;

    @Value("${wayt.app.public-base-url}")
    private String publicBaseUrl;

    @Value("${wayt.upload.avatar-dir:data/uploads/avatars}")
    private String avatarUploadDir;

    public AuthService(UserAccountRepository userAccountRepository, ResponseMapper mapper) {
        this.userAccountRepository = userAccountRepository;
        this.mapper = mapper;
    }

    @Transactional
    public AuthDtos.AuthResponse loginWithKakao(AuthDtos.KakaoLoginRequest request) {
        JsonNode kakaoUser = fetchKakaoUser(request.kakaoAccessToken());
        return loginWithKakaoUser(kakaoUser, request.nickname(), request.avatarUrl());
    }

    @Transactional
    public AuthDtos.AuthResponse loginWithKakaoCode(AuthDtos.KakaoCallbackRequest request) {
        JsonNode token = exchangeKakaoCode(request.code(), firstNonBlank(request.redirectUri(), kakaoRedirectUri));
        JsonNode kakaoUser = fetchKakaoUser(token.path("access_token").asText());
        return loginWithKakaoUser(kakaoUser, null, null);
    }

    public URI kakaoAuthorizeUri(String returnUri) {
        validateReturnUri(returnUri);
        if (kakaoRestApiKey == null || kakaoRestApiKey.isBlank()) {
            throw ApiException.badRequest("KAKAO_REST_API_KEY is required for Kakao REST OAuth");
        }

        String state = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(returnUri.getBytes(StandardCharsets.UTF_8));

        String uri = "https://kauth.kakao.com/oauth/authorize"
                + "?response_type=code"
                + "&client_id=" + encode(kakaoRestApiKey)
                + "&redirect_uri=" + encode(kakaoRedirectUri)
                + "&state=" + encode(state)
                + scopeParam();
        return URI.create(uri);
    }

    public URI appCallbackUri(AuthDtos.AuthResponse auth, String state) {
        String returnUri = decodeState(state);
        validateReturnUri(returnUri);
        String separator = returnUri.contains("?") ? "&" : "?";
        String uri = returnUri + separator
                + "accessToken=" + encode(auth.accessToken())
                + "&refreshToken=" + encode(auth.refreshToken())
                + "&userId=" + encode(auth.user().id().toString())
                + "&waytId=" + encode(auth.user().waytId())
                + "&nickname=" + encode(auth.user().nickname())
                + "&avatarUrl=" + encode(blankIfNull(auth.user().avatarUrl()))
                + "&defaultTravelMode=" + encode(auth.user().defaultTravelMode() == null ? "" : auth.user().defaultTravelMode().name())
                + "&travelModeOnboardingCompleted=" + auth.user().travelModeOnboardingCompleted();
        return URI.create(uri);
    }

    public URI appErrorCallbackUri(String state, String error, String errorDescription) {
        String returnUri = decodeState(state);
        validateReturnUri(returnUri);
        String separator = returnUri.contains("?") ? "&" : "?";
        String uri = returnUri + separator
                + "error=" + encode(firstNonBlank(error, "kakao_login_failed"))
                + "&errorDescription=" + encode(firstNonBlank(errorDescription, "Kakao login failed"));
        return URI.create(uri);
    }

    private AuthDtos.AuthResponse loginWithKakaoUser(JsonNode kakaoUser, String fallbackNickname, String fallbackAvatarUrl) {
        String providerUserId = kakaoUser.path("id").asText();
        JsonNode profile = kakaoUser.path("kakao_account").path("profile");
        JsonNode properties = kakaoUser.path("properties");
        String nickname = firstNonBlank(
                profile.path("nickname").asText(null),
                properties.path("nickname").asText(null),
                fallbackNickname,
                "Wayt user"
        );
        String avatarUrl = firstNonBlank(
                profile.path("profile_image_url").asText(null),
                profile.path("thumbnail_image_url").asText(null),
                properties.path("profile_image").asText(null),
                properties.path("thumbnail_image").asText(null),
                fallbackAvatarUrl,
                null
        );
        UserAccount user = userAccountRepository
                .findByProviderAndProviderUserId(AuthProvider.KAKAO, providerUserId)
                .map(existing -> {
                    log.info("Kakao user already exists: providerUserId={}, userId={}", providerUserId, existing.getId());
                    return existing;
                })
                .orElseGet(() -> userAccountRepository.save(new UserAccount(
                        AuthProvider.KAKAO,
                        providerUserId,
                        uniqueWaytId(nickname),
                        nickname,
                        avatarUrl
                )));

        log.info("Kakao login completed: providerUserId={}, userId={}, waytId={}", providerUserId, user.getId(), user.getWaytId());

        return new AuthDtos.AuthResponse(mapper.user(user), issueToken(user, "access"), issueToken(user, "refresh"));
    }

    @Transactional
    public UserResponse updateProfile(String authorization, AuthDtos.ProfileUpdateRequest request) {
        UUID userId = userIdFromBearerToken(authorization, "access");
        UserAccount user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Session user no longer exists"));

        String nickname = trimToNull(request.nickname());
        if (request.nickname() != null && nickname == null) {
            throw ApiException.badRequest("nickname is required");
        }

        user.updateProfile(nickname == null ? user.getNickname() : nickname, user.getAvatarUrl());
        if (request.defaultTravelMode() != null || request.travelModeOnboardingCompleted() != null) {
            user.updateTravelModePreference(
                    request.defaultTravelMode(),
                    Boolean.TRUE.equals(request.travelModeOnboardingCompleted())
            );
        }
        return mapper.user(user);
    }

    @Transactional
    public UserResponse uploadAvatar(String authorization, MultipartFile file) {
        UserAccount user = authenticatedUser(authorization);
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("profile image is required");
        }
        if (file.getSize() > 5 * 1024 * 1024) {
            throw ApiException.badRequest("profile image must be 5MB or smaller");
        }

        String contentType = firstNonBlank(file.getContentType(), "application/octet-stream");
        if (!contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw ApiException.badRequest("profile image must be an image file");
        }

        String filename = user.getId() + "-" + randomSuffix(6) + extensionFor(contentType, file.getOriginalFilename());
        Path root = avatarRoot();
        Path target = root.resolve(filename).normalize();
        if (!target.startsWith(root)) {
            throw ApiException.badRequest("invalid profile image name");
        }

        try {
            Files.createDirectories(root);
            file.transferTo(target);
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Profile image upload failed");
        }

        deleteUploadedAvatar(user.getAvatarUrl());
        user.updateProfile(user.getNickname(), publicBaseUrl + "/api/uploads/avatars/" + filename);
        return mapper.user(user);
    }

    @Transactional
    public UserResponse deleteAvatar(String authorization) {
        UserAccount user = authenticatedUser(authorization);
        deleteUploadedAvatar(user.getAvatarUrl());
        user.updateProfile(user.getNickname(), null);
        return mapper.user(user);
    }

    public StoredAvatar avatar(String filename) {
        Path root = avatarRoot();
        Path target = root.resolve(filename).normalize();
        if (!target.startsWith(root) || !Files.exists(target)) {
            throw ApiException.notFound("Profile image not found");
        }

        try {
            Resource resource = new UrlResource(target.toUri());
            String contentType = firstNonBlank(Files.probeContentType(target), "application/octet-stream");
            return new StoredAvatar(resource, contentType);
        } catch (MalformedURLException exception) {
            throw ApiException.notFound("Profile image not found");
        } catch (IOException exception) {
            return new StoredAvatar(newResource(target), "application/octet-stream");
        }
    }

    public AuthDtos.AuthResponse refresh(AuthDtos.RefreshRequest request) {
        if (request.refreshToken().isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "refreshToken is required");
        }
        UserAccount user = userAccountRepository.findAll().stream()
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("No user exists yet"));
        return new AuthDtos.AuthResponse(mapper.user(user), issueToken(user, "access"), issueToken(user, "refresh"));
    }

    public UserResponse session(String authorization) {
        return mapper.user(authenticatedUser(authorization));
    }

    private String uniqueWaytId(String nickname) {
        String base = nickname.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "");
        if (base.isBlank()) {
            base = "user";
        }

        for (int i = 0; i < 20; i++) {
            String candidate = "@" + base + randomSuffix(3);
            if (!userAccountRepository.existsByWaytId(candidate)) {
                return candidate;
            }
        }
        return "@user_" + randomSuffix(8);
    }

    private String randomSuffix(int bytes) {
        byte[] buffer = new byte[bytes];
        secureRandom.nextBytes(buffer);
        return HexFormat.of().formatHex(buffer).substring(0, bytes * 2);
    }

    private String issueToken(UserAccount user, String type) {
        String raw = type + ":" + user.getId() + ":" + System.currentTimeMillis() + ":" + randomSuffix(8);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private UUID userIdFromBearerToken(String authorization, String expectedType) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Bearer token is required");
        }

        try {
            String token = authorization.substring("Bearer ".length()).trim();
            String decoded = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            String[] parts = decoded.split(":");
            if (parts.length < 4 || !expectedType.equals(parts[0])) {
                throw new IllegalArgumentException("Unexpected token format");
            }
            return UUID.fromString(parts[1]);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid session token");
        }
    }

    public UserAccount authenticatedUser(String authorization) {
        UUID userId = userIdFromBearerToken(authorization, "access");
        return userAccountRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Session user no longer exists"));
    }

    private JsonNode fetchKakaoUser(String accessToken) {
        try {
            return kakaoClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v2/user/me")
                            .queryParam("property_keys", "[\"kakao_account.profile\",\"properties.nickname\",\"properties.profile_image\",\"properties.thumbnail_image\"]")
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException exception) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid Kakao access token");
        }
    }

    private JsonNode exchangeKakaoCode(String code, String redirectUri) {
        if (kakaoRestApiKey == null || kakaoRestApiKey.isBlank()) {
            throw ApiException.badRequest("KAKAO_REST_API_KEY is required for REST OAuth callback");
        }
        if (kakaoClientSecret == null || kakaoClientSecret.isBlank()) {
            throw ApiException.badRequest("KAKAO_CLIENT_SECRET is required because Kakao client secret is enabled");
        }

        try {
            return kakaoAuthClient.post()
                    .uri("/oauth/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body("grant_type=authorization_code"
                            + "&client_id=" + encode(kakaoRestApiKey)
                            + "&client_secret=" + encode(kakaoClientSecret)
                            + "&redirect_uri=" + encode(redirectUri)
                            + "&code=" + encode(code))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException exception) {
            log.warn(
                    "Kakao authorization code exchange failed: status={}, body={}",
                    exception.getStatusCode(),
                    exception.getResponseBodyAsString()
            );
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Kakao authorization code exchange failed: " + exception.getResponseBodyAsString());
        }
    }

    private String decodeState(String state) {
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(state);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw ApiException.badRequest("Invalid OAuth state");
        }
    }

    private void validateReturnUri(String returnUri) {
        if (returnUri == null || returnUri.isBlank()) {
            throw ApiException.badRequest("returnUri is required");
        }

        URI uri = URI.create(returnUri);
        String scheme = uri.getScheme();
        if (!"exp".equals(scheme) && !"wayt".equals(scheme)) {
            throw ApiException.badRequest("Unsupported returnUri scheme");
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(blankIfNull(value), StandardCharsets.UTF_8);
    }

    private String scopeParam() {
        if (kakaoScopes == null || kakaoScopes.isBlank()) {
            return "";
        }
        return "&scope=" + encode(kakaoScopes);
    }

    private String blankIfNull(String value) {
        return value == null ? "" : value;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private Path avatarRoot() {
        return Paths.get(avatarUploadDir).toAbsolutePath().normalize();
    }

    private Resource newResource(Path target) {
        try {
            return new UrlResource(target.toUri());
        } catch (MalformedURLException exception) {
            throw ApiException.notFound("Profile image not found");
        }
    }

    private void deleteUploadedAvatar(String avatarUrl) {
        String prefix = publicBaseUrl + "/api/uploads/avatars/";
        if (avatarUrl == null || !avatarUrl.startsWith(prefix)) {
            return;
        }

        String filename = avatarUrl.substring(prefix.length());
        Path target = avatarRoot().resolve(filename).normalize();
        if (!target.startsWith(avatarRoot())) {
            return;
        }

        try {
            Files.deleteIfExists(target);
        } catch (IOException ignored) {
            log.warn("Failed to delete old profile image: {}", target);
        }
    }

    private String extensionFor(String contentType, String originalFilename) {
        String normalized = contentType.toLowerCase(Locale.ROOT);
        if (normalized.contains("png")) {
            return ".png";
        }
        if (normalized.contains("webp")) {
            return ".webp";
        }
        if (normalized.contains("heic")) {
            return ".heic";
        }
        if (normalized.contains("heif")) {
            return ".heif";
        }
        if (normalized.contains("gif")) {
            return ".gif";
        }

        String fallback = originalExtension(originalFilename);
        return fallback == null ? ".jpg" : fallback;
    }

    private String originalExtension(String originalFilename) {
        if (originalFilename == null) {
            return null;
        }
        String filename = Paths.get(originalFilename).getFileName().toString();
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            return null;
        }
        String extension = filename.substring(dotIndex).toLowerCase(Locale.ROOT);
        return extension.matches("\\.(jpg|jpeg|png|webp|heic|heif|gif)") ? extension : null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    public record StoredAvatar(Resource resource, String contentType) {
    }
}
