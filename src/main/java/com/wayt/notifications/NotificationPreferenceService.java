package com.wayt.notifications;

import com.wayt.domain.NotificationPreference;
import com.wayt.domain.UserAccount;
import com.wayt.dto.MiscDtos;
import com.wayt.repository.NotificationPreferenceRepository;
import com.wayt.service.AuthService;
import com.wayt.support.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class NotificationPreferenceService {
    private final NotificationPreferenceRepository preferenceRepository;
    private final AuthService authService;

    public NotificationPreferenceService(NotificationPreferenceRepository preferenceRepository, AuthService authService) {
        this.preferenceRepository = preferenceRepository;
        this.authService = authService;
    }

    @Transactional(readOnly = true)
    public MiscDtos.NotificationPreferencesResponse preferences(String authorization) {
        UserAccount user = authService.authenticatedUser(authorization);
        return responseFor(user);
    }

    @Transactional
    public MiscDtos.NotificationPreferencesResponse updatePreferences(
            String authorization,
            MiscDtos.NotificationPreferencesPatchRequest request
    ) {
        UserAccount user = authService.authenticatedUser(authorization);
        if (request.items() == null) {
            throw ApiException.badRequest("Notification preferences are required");
        }

        for (MiscDtos.NotificationPreferencePatchItem item : request.items()) {
            if (!NotificationType.isKnownApiId(item.type())) {
                throw ApiException.badRequest("Unknown notification type: " + item.type());
            }
            NotificationPreference preference = preferenceRepository
                    .findByUserAccountAndNotificationType(user, item.type())
                    .orElseGet(() -> preferenceRepository.save(new NotificationPreference(user, item.type(), item.enabled())));
            preference.changeEnabled(item.enabled());
        }
        return responseFor(user);
    }

    @Transactional(readOnly = true)
    public boolean enabled(UserAccount user, NotificationType type) {
        return preferenceRepository.findByUserAccountAndNotificationType(user, type.apiId())
                .map(NotificationPreference::isEnabled)
                .orElse(type.defaultEnabled());
    }

    private MiscDtos.NotificationPreferencesResponse responseFor(UserAccount user) {
        Map<String, Boolean> saved = preferenceRepository.findByUserAccount(user).stream()
                .collect(Collectors.toMap(NotificationPreference::getNotificationType, NotificationPreference::isEnabled));
        Map<String, Boolean> merged = new LinkedHashMap<>();
        for (NotificationType type : NotificationType.values()) {
            merged.put(type.apiId(), saved.getOrDefault(type.apiId(), type.defaultEnabled()));
        }

        return new MiscDtos.NotificationPreferencesResponse(
                merged.entrySet().stream()
                        .map(entry -> new MiscDtos.NotificationPreferenceItem(entry.getKey(), entry.getValue()))
                        .toList()
        );
    }
}
