package com.wayt.controller;

import com.wayt.dto.MiscDtos;
import com.wayt.notifications.NotificationPreferenceService;
import com.wayt.service.MiscService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class MiscController {
    private final MiscService miscService;
    private final NotificationPreferenceService notificationPreferenceService;

    public MiscController(MiscService miscService, NotificationPreferenceService notificationPreferenceService) {
        this.miscService = miscService;
        this.notificationPreferenceService = notificationPreferenceService;
    }

    @PostMapping("/places")
    MiscDtos.SavedPlaceResponse savePlace(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody MiscDtos.SavedPlaceCreateRequest request
    ) {
        return miscService.savePlace(authorization, request);
    }

    @GetMapping("/places")
    List<MiscDtos.SavedPlaceResponse> savedPlaces(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        return miscService.savedPlaces(authorization);
    }

    @PatchMapping("/places/{placeId}")
    MiscDtos.SavedPlaceResponse updateSavedPlace(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID placeId,
            @Valid @RequestBody MiscDtos.SavedPlaceUpdateRequest request
    ) {
        return miscService.updateSavedPlace(authorization, placeId, request);
    }

    @DeleteMapping("/places/{placeId}")
    void deleteSavedPlace(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID placeId
    ) {
        miscService.deleteSavedPlace(authorization, placeId);
    }

    @PostMapping("/address-book")
    MiscDtos.AddressBookResponse addAddressBook(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody MiscDtos.AddressBookCreateRequest request
    ) {
        return miscService.addAddressBook(authorization, request);
    }

    @GetMapping("/address-book")
    List<MiscDtos.AddressBookResponse> addressBook(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        return miscService.addressBook(authorization);
    }

    @GetMapping("/users/wayt-id-suggestions")
    List<MiscDtos.WaytIdSuggestionResponse> waytIdSuggestions(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam String query,
            @RequestParam(required = false) Integer limit
    ) {
        return miscService.waytIdSuggestions(authorization, query, limit);
    }

    @DeleteMapping("/address-book/{entryId}")
    void deleteAddressBook(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID entryId
    ) {
        miscService.deleteAddressBook(authorization, entryId);
    }

    @GetMapping("/maps/reverse-geocode")
    MiscDtos.ReverseGeocodeResponse reverseGeocode(@RequestParam double lat, @RequestParam double lng) {
        return miscService.reverseGeocode(lat, lng);
    }

    @GetMapping("/maps/search")
    MiscDtos.MapPlaceSearchResponse searchPlaces(
            @RequestParam String query,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng
    ) {
        return miscService.searchPlaces(query, lat, lng);
    }

    @PostMapping("/push-tokens")
    MiscDtos.PushTokenResponse savePushToken(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody MiscDtos.PushTokenRequest request
    ) {
        return miscService.savePushToken(authorization, request);
    }

    @GetMapping("/me/notification-preferences")
    MiscDtos.NotificationPreferencesResponse notificationPreferences(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        return notificationPreferenceService.preferences(authorization);
    }

    @PatchMapping("/me/notification-preferences")
    MiscDtos.NotificationPreferencesResponse updateNotificationPreferences(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody MiscDtos.NotificationPreferencesPatchRequest request
    ) {
        return notificationPreferenceService.updatePreferences(authorization, request);
    }
}
