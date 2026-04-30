package com.wayt.service;

import com.wayt.domain.SavedPlace;
import com.wayt.domain.UserAccount;
import com.wayt.dto.MiscDtos;
import com.wayt.repository.SavedPlaceRepository;
import com.wayt.support.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

@Service
public class SavedPlaceService {
    private static final int MAX_FAVORITE_PLACES = 15;
    private static final int MAX_RECENT_PLACES = 15;

    private final SavedPlaceRepository savedPlaceRepository;
    private final AuthService authService;

    public SavedPlaceService(SavedPlaceRepository savedPlaceRepository, AuthService authService) {
        this.savedPlaceRepository = savedPlaceRepository;
        this.authService = authService;
    }

    @Transactional
    public MiscDtos.SavedPlaceResponse saveFavorite(String authorization, MiscDtos.SavedPlaceCreateRequest request) {
        UserAccount owner = authService.authenticatedUser(authorization);
        String placeName = cleanPlaceName(request.placeName());
        String label = cleanLabel(request.label(), placeName);
        SavedPlace place = savedPlaceRepository
                .findByOwnerAndPlaceNameAndLatitudeAndLongitude(owner, placeName, request.latitude(), request.longitude())
                .orElseGet(() -> savedPlaceRepository.save(new SavedPlace(
                        owner,
                        label,
                        placeName,
                        request.latitude(),
                        request.longitude(),
                        Boolean.TRUE.equals(request.favorite())
        )));

        if (Boolean.TRUE.equals(request.favorite())) {
            OffsetDateTime now = OffsetDateTime.now();
            place.saveAsFavorite(label, now);
            pruneOldFavorites(owner, place, now);
        }

        return response(place);
    }

    @Transactional(readOnly = true)
    public List<MiscDtos.SavedPlaceResponse> savedPlaces(String authorization) {
        UserAccount owner = authService.authenticatedUser(authorization);
        return savedPlaceRepository.findByOwner(owner)
                .stream()
                .sorted(savedPlaceOrder())
                .map(this::response)
                .toList();
    }

    @Transactional
    public MiscDtos.SavedPlaceResponse updateSavedPlace(
            String authorization,
            UUID placeId,
            MiscDtos.SavedPlaceUpdateRequest request
    ) {
        UserAccount owner = authService.authenticatedUser(authorization);
        SavedPlace place = savedPlaceRepository.findByIdAndOwner(placeId, owner)
                .orElseThrow(() -> ApiException.notFound("Saved place not found"));
        String label = cleanLabel(request.label(), place.getPlaceName());
        boolean favorite = request.favorite() == null ? place.isFavorite() : request.favorite();
        OffsetDateTime now = OffsetDateTime.now();
        place.updateDetails(label, favorite, now);
        if (favorite) {
            pruneOldFavorites(owner, place, now);
        }
        return response(place);
    }

    @Transactional
    public void deleteSavedPlace(String authorization, UUID placeId) {
        UserAccount owner = authService.authenticatedUser(authorization);
        SavedPlace place = savedPlaceRepository.findByIdAndOwner(placeId, owner)
                .orElseThrow(() -> ApiException.notFound("Saved place not found"));
        savedPlaceRepository.delete(place);
    }

    @Transactional
    public void recordRecent(UserAccount owner, String rawPlaceName, double latitude, double longitude) {
        String placeName = cleanPlaceName(rawPlaceName);
        OffsetDateTime now = OffsetDateTime.now();
        SavedPlace place = savedPlaceRepository
                .findByOwnerAndPlaceNameAndLatitudeAndLongitude(owner, placeName, latitude, longitude)
                .orElseGet(() -> savedPlaceRepository.save(new SavedPlace(
                        owner,
                        placeName,
                        placeName,
                        latitude,
                        longitude,
                        false
        )));
        place.markUsed(now);
        pruneOldRecents(owner, place, now);
    }

    private void pruneOldFavorites(UserAccount owner, SavedPlace protectedPlace, OffsetDateTime now) {
        prunePlaces(
                savedPlaceRepository.findByOwner(owner).stream()
                        .filter(SavedPlace::isFavorite)
                        .toList(),
                MAX_FAVORITE_PLACES,
                protectedPlace,
                Comparator
                        .comparing(SavedPlace::getUpdatedAt)
                        .thenComparing(SavedPlace::getCreatedAt),
                place -> removeFavoriteOnly(place, now)
        );
    }

    private void pruneOldRecents(UserAccount owner, SavedPlace protectedPlace, OffsetDateTime now) {
        prunePlaces(
                savedPlaceRepository.findByOwner(owner).stream()
                        .filter(place -> place.getUseCount() > 0)
                        .toList(),
                MAX_RECENT_PLACES,
                protectedPlace,
                Comparator
                        .comparing((SavedPlace place) -> place.getLastUsedAt() == null ? OffsetDateTime.MIN : place.getLastUsedAt())
                        .thenComparing(SavedPlace::getCreatedAt),
                place -> removeRecentOnly(place, now)
        );
    }

    private void prunePlaces(
            List<SavedPlace> places,
            int maxPlaces,
            SavedPlace protectedPlace,
            Comparator<SavedPlace> oldestFirst,
            Consumer<SavedPlace> pruneAction
    ) {
        int extraCount = places.size() - maxPlaces;
        if (extraCount <= 0) {
            return;
        }

        places.stream()
                .filter(place -> !place.getId().equals(protectedPlace.getId()))
                .sorted(oldestFirst)
                .limit(extraCount)
                .forEach(pruneAction);
    }

    private void removeFavoriteOnly(SavedPlace place, OffsetDateTime now) {
        if (place.getUseCount() > 0) {
            place.removeFavorite(now);
            return;
        }

        savedPlaceRepository.delete(place);
    }

    private void removeRecentOnly(SavedPlace place, OffsetDateTime now) {
        if (place.isFavorite()) {
            place.clearRecentUse(now);
            return;
        }

        savedPlaceRepository.delete(place);
    }

    private Comparator<SavedPlace> savedPlaceOrder() {
        return Comparator
                .comparing(SavedPlace::isFavorite).reversed()
                .thenComparing(
                        (SavedPlace place) -> place.getLastUsedAt() == null ? OffsetDateTime.MIN : place.getLastUsedAt(),
                        Comparator.reverseOrder()
                )
                .thenComparing(SavedPlace::getUpdatedAt, Comparator.reverseOrder())
                .thenComparing(SavedPlace::getLabel);
    }

    private MiscDtos.SavedPlaceResponse response(SavedPlace place) {
        return new MiscDtos.SavedPlaceResponse(
                place.getId(),
                place.getLabel(),
                place.getPlaceName(),
                place.getLatitude(),
                place.getLongitude(),
                place.isFavorite(),
                place.getLastUsedAt(),
                place.getUseCount()
        );
    }

    private String cleanPlaceName(String placeName) {
        if (placeName == null || placeName.isBlank()) {
            throw ApiException.badRequest("placeName is required");
        }
        return placeName.trim();
    }

    private String cleanLabel(String label, String fallback) {
        if (label == null || label.isBlank()) {
            return fallback;
        }
        return label.trim();
    }
}
