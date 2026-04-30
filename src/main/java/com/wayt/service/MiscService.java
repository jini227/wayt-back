package com.wayt.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.wayt.domain.AddressBookEntry;
import com.wayt.domain.PushToken;
import com.wayt.domain.UserAccount;
import com.wayt.dto.MiscDtos;
import com.wayt.repository.AddressBookEntryRepository;
import com.wayt.repository.PushTokenRepository;
import com.wayt.repository.UserAccountRepository;
import com.wayt.support.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.HtmlUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MiscService {
    private final UserAccountRepository userAccountRepository;
    private final AddressBookEntryRepository addressBookEntryRepository;
    private final PushTokenRepository pushTokenRepository;
    private final ResponseMapper mapper;
    private final AuthService authService;
    private final SavedPlaceService savedPlaceService;
    private final WaytIdSuggestionService waytIdSuggestionService;
    private final RestClient naverMapsClient = RestClient.builder()
            .baseUrl("https://maps.apigw.ntruss.com")
            .build();
    private final RestClient naverSearchClient = RestClient.builder()
            .baseUrl("https://openapi.naver.com")
            .build();
    private final RestClient kakaoLocalClient = RestClient.builder()
            .baseUrl("https://dapi.kakao.com")
            .build();
    private final RestClient osmClient = RestClient.builder()
            .baseUrl("https://nominatim.openstreetmap.org")
            .build();

    @Value("${wayt.naver.maps.ncp-key-id}")
    private String naverMapsKeyId;

    @Value("${wayt.naver.maps.ncp-key}")
    private String naverMapsKey;

    @Value("${wayt.naver.search.client-id:}")
    private String naverSearchClientId;

    @Value("${wayt.naver.search.client-secret:}")
    private String naverSearchClientSecret;

    @Value("${wayt.kakao.rest-api-key}")
    private String kakaoRestApiKey;

    public MiscService(
            UserAccountRepository userAccountRepository,
            AddressBookEntryRepository addressBookEntryRepository,
            PushTokenRepository pushTokenRepository,
            ResponseMapper mapper,
            AuthService authService,
            SavedPlaceService savedPlaceService,
            WaytIdSuggestionService waytIdSuggestionService
    ) {
        this.userAccountRepository = userAccountRepository;
        this.addressBookEntryRepository = addressBookEntryRepository;
        this.pushTokenRepository = pushTokenRepository;
        this.mapper = mapper;
        this.authService = authService;
        this.savedPlaceService = savedPlaceService;
        this.waytIdSuggestionService = waytIdSuggestionService;
    }

    @Transactional
    public MiscDtos.SavedPlaceResponse savePlace(String authorization, MiscDtos.SavedPlaceCreateRequest request) {
        return savedPlaceService.saveFavorite(authorization, request);
    }

    @Transactional(readOnly = true)
    public List<MiscDtos.SavedPlaceResponse> savedPlaces(String authorization) {
        return savedPlaceService.savedPlaces(authorization);
    }

    @Transactional
    public MiscDtos.SavedPlaceResponse updateSavedPlace(
            String authorization,
            java.util.UUID placeId,
            MiscDtos.SavedPlaceUpdateRequest request
    ) {
        return savedPlaceService.updateSavedPlace(authorization, placeId, request);
    }

    @Transactional
    public void deleteSavedPlace(String authorization, java.util.UUID placeId) {
        savedPlaceService.deleteSavedPlace(authorization, placeId);
    }

    @Transactional
    public MiscDtos.AddressBookResponse addAddressBook(String authorization, MiscDtos.AddressBookCreateRequest request) {
        UserAccount owner = authService.authenticatedUser(authorization);
        UserAccount target = userAccountRepository.findByWaytId(request.targetWaytId())
                .orElseThrow(() -> ApiException.notFound("Target user not found"));
        if (owner.getId().equals(target.getId())) {
            throw ApiException.badRequest("내 아이디는 주소록에 추가할 수 없어요.");
        }
        addressBookEntryRepository.findByOwnerAndSavedUser(owner, target)
                .ifPresent(entry -> {
                    throw ApiException.badRequest("이미 주소록에 있는 사용자예요.");
                });
        String displayName = request.displayName() == null || request.displayName().isBlank()
                ? target.getNickname()
                : request.displayName().trim();
        AddressBookEntry entry = addressBookEntryRepository.save(new AddressBookEntry(owner, target, displayName));
        return new MiscDtos.AddressBookResponse(entry.getId(), mapper.user(target), entry.getDisplayName());
    }

    @Transactional(readOnly = true)
    public List<MiscDtos.AddressBookResponse> addressBook(String authorization) {
        UserAccount owner = authService.authenticatedUser(authorization);
        return addressBookEntryRepository.findByOwnerOrderByDisplayNameAsc(owner)
                .stream()
                .map(entry -> new MiscDtos.AddressBookResponse(entry.getId(), mapper.user(entry.getSavedUser()), entry.getDisplayName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MiscDtos.WaytIdSuggestionResponse> waytIdSuggestions(String authorization, String query, Integer limit) {
        UserAccount viewer = authService.authenticatedUser(authorization);
        return waytIdSuggestionService.suggestions(viewer, query, limit);
    }

    @Transactional
    public void deleteAddressBook(String authorization, java.util.UUID entryId) {
        UserAccount owner = authService.authenticatedUser(authorization);
        AddressBookEntry entry = addressBookEntryRepository.findByIdAndOwner(entryId, owner)
                .orElseThrow(() -> ApiException.notFound("Address book entry not found"));
        addressBookEntryRepository.delete(entry);
    }

    @Transactional(readOnly = true)
    public MiscDtos.ReverseGeocodeResponse reverseGeocode(double lat, double lng) {
        if (naverMapsKeyId == null || naverMapsKeyId.isBlank() || naverMapsKey == null || naverMapsKey.isBlank()) {
            throw ApiException.badRequest("Naver Maps API keys are required");
        }

        JsonNode body;
        try {
            body = naverMapsClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/map-reversegeocode/v2/gc")
                            .queryParam("coords", lng + "," + lat)
                            .queryParam("orders", "roadaddr,addr")
                            .queryParam("output", "json")
                            .build())
                    .header("x-ncp-apigw-api-key-id", naverMapsKeyId)
                    .header("x-ncp-apigw-api-key", naverMapsKey)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException exception) {
            throw ApiException.badRequest("Address lookup failed");
        }
        if (body == null) {
            throw ApiException.notFound("Address not found");
        }

        String roadAddress = null;
        String jibunAddress = null;
        for (JsonNode result : body.path("results")) {
            String name = text(result.path("name"));
            if ("roadaddr".equals(name) && roadAddress == null) {
                roadAddress = formatAddress(result);
            }
            if ("addr".equals(name) && jibunAddress == null) {
                jibunAddress = formatAddress(result);
            }
        }

        String displayName = firstNonBlank(roadAddress, jibunAddress);
        if (displayName == null) {
            throw ApiException.notFound("Address not found");
        }

        return new MiscDtos.ReverseGeocodeResponse(displayName, roadAddress, jibunAddress, lat, lng);
    }

    @Transactional(readOnly = true)
    public MiscDtos.MapPlaceSearchResponse searchPlaces(String query, Double lat, Double lng) {
        if (query == null || query.isBlank()) {
            throw ApiException.badRequest("Search query is required");
        }
        if (naverMapsKeyId == null || naverMapsKeyId.isBlank() || naverMapsKey == null || naverMapsKey.isBlank()) {
            throw ApiException.badRequest("Naver Maps API keys are required");
        }

        Map<String, MiscDtos.MapPlaceResponse> places = new LinkedHashMap<>();
        for (MiscDtos.MapPlaceResponse place : searchKakaoKeywordPlaces(query.trim())) {
            places.putIfAbsent(placeKey(place), place);
        }
        for (MiscDtos.MapPlaceResponse place : searchKakaoAddressPlaces(query.trim())) {
            places.putIfAbsent(placeKey(place), place);
        }
        for (MiscDtos.MapPlaceResponse place : searchNaverLocalPlaces(query.trim())) {
            places.putIfAbsent(placeKey(place), place);
        }
        for (MiscDtos.MapPlaceResponse place : searchOsmPlaces(query.trim())) {
            places.putIfAbsent(placeKey(place), place);
        }
        for (MiscDtos.MapPlaceResponse place : geocodePlaces(query.trim(), lat, lng)) {
            places.putIfAbsent(placeKey(place), place);
        }

        return new MiscDtos.MapPlaceSearchResponse(places.values().stream().limit(8).toList());
    }

    @Transactional
    public MiscDtos.PushTokenResponse savePushToken(String authorization, MiscDtos.PushTokenRequest request) {
        UserAccount user = authService.authenticatedUser(authorization);
        PushToken token = pushTokenRepository.findByToken(request.token())
                .map(existing -> {
                    existing.updateRegistration(
                            user,
                            request.platform(),
                            request.environment(),
                            request.deviceId(),
                            request.appVersion()
                    );
                    return existing;
                })
                .orElseGet(() -> {
                    PushToken created = new PushToken(user, request.token(), request.platform());
                    created.updateRegistration(
                            user,
                            request.platform(),
                            request.environment(),
                            request.deviceId(),
                            request.appVersion()
                    );
                    return pushTokenRepository.save(created);
                });
        return new MiscDtos.PushTokenResponse(token.getId(), token.getToken(), token.getPlatform(), token.getEnvironment());
    }

    private String formatAddress(JsonNode result) {
        List<String> parts = new ArrayList<>();
        JsonNode region = result.path("region");
        addIfPresent(parts, region.path("area1").path("name"));
        addIfPresent(parts, region.path("area2").path("name"));
        addIfPresent(parts, region.path("area3").path("name"));
        addIfPresent(parts, region.path("area4").path("name"));

        JsonNode land = result.path("land");
        addIfPresent(parts, land.path("name"));
        String number = landNumber(land);
        if (number != null) {
            parts.add(number);
        }
        String building = firstNonBlank(text(land.path("addition0").path("value")), text(land.path("addition1").path("value")));
        if (building != null) {
            parts.add(building);
        }

        return parts.isEmpty() ? null : String.join(" ", parts);
    }

    private List<MiscDtos.MapPlaceResponse> searchKakaoKeywordPlaces(String query) {
        if (kakaoRestApiKey == null || kakaoRestApiKey.isBlank()) {
            return List.of();
        }

        JsonNode body;
        try {
            body = kakaoLocalClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v2/local/search/keyword.json")
                            .queryParam("query", query)
                            .queryParam("size", 10)
                            .queryParam("sort", "accuracy")
                            .build())
                    .header("Authorization", "KakaoAK " + kakaoRestApiKey)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException exception) {
            return List.of();
        }
        if (body == null) {
            return List.of();
        }

        List<MiscDtos.MapPlaceResponse> places = new ArrayList<>();
        for (JsonNode document : body.path("documents")) {
            MiscDtos.MapPlaceResponse place = kakaoPlace(document, "kakao-keyword");
            if (place != null) {
                places.add(place);
            }
        }
        return places;
    }

    private List<MiscDtos.MapPlaceResponse> searchKakaoAddressPlaces(String query) {
        if (kakaoRestApiKey == null || kakaoRestApiKey.isBlank()) {
            return List.of();
        }

        JsonNode body;
        try {
            body = kakaoLocalClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v2/local/search/address.json")
                            .queryParam("query", query)
                            .queryParam("size", 8)
                            .build())
                    .header("Authorization", "KakaoAK " + kakaoRestApiKey)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException exception) {
            return List.of();
        }
        if (body == null) {
            return List.of();
        }

        List<MiscDtos.MapPlaceResponse> places = new ArrayList<>();
        for (JsonNode document : body.path("documents")) {
            MiscDtos.MapPlaceResponse place = kakaoPlace(document, "kakao-address");
            if (place != null) {
                places.add(place);
            }
        }
        return places;
    }

    private MiscDtos.MapPlaceResponse kakaoPlace(JsonNode document, String source) {
        Double latitude = parseCoordinate(text(document.path("y")), 90);
        Double longitude = parseCoordinate(text(document.path("x")), 180);
        if (latitude == null || longitude == null) {
            return null;
        }
        String title = text(document.path("place_name"));
        String roadAddress = text(document.path("road_address_name"));
        String address = firstNonBlank(roadAddress, text(document.path("address_name")), text(document.path("address").path("address_name")));
        return new MiscDtos.MapPlaceResponse(
                firstNonBlank(title, address, "검색 결과"),
                address,
                roadAddress,
                latitude,
                longitude,
                source
        );
    }

    private List<MiscDtos.MapPlaceResponse> searchNaverLocalPlaces(String query) {
        if (naverSearchClientId == null || naverSearchClientId.isBlank()
                || naverSearchClientSecret == null || naverSearchClientSecret.isBlank()) {
            return List.of();
        }

        JsonNode body;
        try {
            body = naverSearchClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1/search/local.json")
                            .queryParam("query", query)
                            .queryParam("display", 8)
                            .build())
                    .header("X-Naver-Client-Id", naverSearchClientId)
                    .header("X-Naver-Client-Secret", naverSearchClientSecret)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException exception) {
            return List.of();
        }
        if (body == null) {
            return List.of();
        }

        List<MiscDtos.MapPlaceResponse> places = new ArrayList<>();
        for (JsonNode item : body.path("items")) {
            Double longitude = parseCoordinate(text(item.path("mapx")), 180);
            Double latitude = parseCoordinate(text(item.path("mapy")), 90);
            if (latitude == null || longitude == null) {
                continue;
            }
            String title = cleanHtml(text(item.path("title")));
            String address = cleanHtml(firstNonBlank(text(item.path("roadAddress")), text(item.path("address"))));
            String roadAddress = cleanHtml(text(item.path("roadAddress")));
            places.add(new MiscDtos.MapPlaceResponse(
                    firstNonBlank(title, address, "검색 결과"),
                    address,
                    roadAddress,
                    latitude,
                    longitude,
                    "local"
            ));
        }
        return places;
    }

    private List<MiscDtos.MapPlaceResponse> geocodePlaces(String query, Double lat, Double lng) {
        JsonNode body;
        try {
            body = naverMapsClient.get()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder
                                .path("/map-geocode/v2/geocode")
                                .queryParam("query", query);
                        if (lat != null && lng != null) {
                            builder.queryParam("coordinate", lng + "," + lat);
                        }
                        return builder.build();
                    })
                    .header("x-ncp-apigw-api-key-id", naverMapsKeyId)
                    .header("x-ncp-apigw-api-key", naverMapsKey)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException exception) {
            return List.of();
        }
        if (body == null) {
            return List.of();
        }

        List<MiscDtos.MapPlaceResponse> places = new ArrayList<>();
        for (JsonNode addressNode : body.path("addresses")) {
            Double latitude = parseCoordinate(text(addressNode.path("y")), 90);
            Double longitude = parseCoordinate(text(addressNode.path("x")), 180);
            if (latitude == null || longitude == null) {
                continue;
            }
            String roadAddress = text(addressNode.path("roadAddress"));
            String jibunAddress = text(addressNode.path("jibunAddress"));
            String address = firstNonBlank(roadAddress, jibunAddress);
            places.add(new MiscDtos.MapPlaceResponse(
                    firstNonBlank(address, query),
                    address,
                    roadAddress,
                    latitude,
                    longitude,
                    "geocode"
            ));
        }
        return places;
    }

    private List<MiscDtos.MapPlaceResponse> searchOsmPlaces(String query) {
        Map<String, MiscDtos.MapPlaceResponse> places = new LinkedHashMap<>();
        for (String searchQuery : osmSearchQueries(query)) {
            JsonNode body;
            try {
                body = osmClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/search")
                                .queryParam("format", "jsonv2")
                                .queryParam("countrycodes", "kr")
                                .queryParam("limit", 8)
                                .queryParam("q", searchQuery)
                                .build())
                        .header("User-Agent", "WaytLocalDev/1.0")
                        .retrieve()
                        .body(JsonNode.class);
            } catch (RestClientException exception) {
                continue;
            }
            if (body == null || !body.isArray()) {
                continue;
            }

            for (JsonNode item : body) {
                Double latitude = parseCoordinate(text(item.path("lat")), 90);
                Double longitude = parseCoordinate(text(item.path("lon")), 180);
                String displayName = text(item.path("display_name"));
                if (latitude == null || longitude == null || displayName == null) {
                    continue;
                }
                String title = osmTitle(query, item, displayName);
                MiscDtos.MapPlaceResponse place = new MiscDtos.MapPlaceResponse(
                        title,
                        displayName,
                        null,
                        latitude,
                        longitude,
                        "osm"
                );
                places.putIfAbsent(placeKey(place), place);
            }
        }
        return places.values().stream().limit(8).toList();
    }

    private String placeKey(MiscDtos.MapPlaceResponse place) {
        return Math.round(place.latitude() * 100000) + ":" + Math.round(place.longitude() * 100000);
    }

    private Double parseCoordinate(String raw, int maxAbsValue) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            double value = Double.parseDouble(raw);
            if (Math.abs(value) > maxAbsValue) {
                value = value / 10000000.0;
            }
            return value;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String cleanHtml(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.replaceAll("<[^>]*>", "");
        value = HtmlUtils.htmlUnescape(value).trim();
        return value.isBlank() ? null : value;
    }

    private List<String> osmSearchQueries(String query) {
        if (query.endsWith("역") && query.length() > 1) {
            return List.of(query.substring(0, query.length() - 1), query);
        }
        return List.of(query);
    }

    private String osmTitle(String query, JsonNode item, String displayName) {
        String category = text(item.path("category"));
        String type = text(item.path("type"));
        if (query.endsWith("역") && ("railway".equals(category) || "station".equals(type) || "stop".equals(type))) {
            return query;
        }
        return firstNonBlank(text(item.path("name")), firstAddressPart(displayName), query);
    }

    private String firstAddressPart(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return null;
        }
        String[] parts = displayName.split(",");
        return parts.length == 0 ? displayName : parts[0].trim();
    }

    private String landNumber(JsonNode land) {
        String number1 = text(land.path("number1"));
        String number2 = text(land.path("number2"));
        if (number1 == null) {
            return null;
        }
        return number2 == null ? number1 : number1 + "-" + number2;
    }

    private void addIfPresent(List<String> parts, JsonNode node) {
        String value = text(node);
        if (value != null) {
            parts.add(value);
        }
    }

    private String text(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText();
        return value == null || value.isBlank() ? null : value;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

}
