package com.invi.api.place;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PlaceSearchService {

    static final int MAX_QUERY_LENGTH = 50;
    static final int ENOUGH_RESULTS = 10;

    private final KakaoLocalClient kakao;
    private final PlaceSearchGuard guard;

    /**
     * Any place Kakao knows, for ceremonies held where the venue filter does not reach
     * (churches, restaurants, rented spaces). Still a real place with an id, so links stay exact.
     */
    public List<PlaceDto> searchAll(String memberKey, String rawQuery) {
        String query = validQuery(rawQuery);
        guard.acquireSearch(memberKey);
        return guard.cached("all|" + query, () -> {
            KakaoLocalClient.KeywordPage result = page(query, 1);
            Map<String, PlaceDto> places = new LinkedHashMap<>();
            for (KakaoLocalClient.Document doc : documents(result)) {
                PlaceDto place = toDto(doc);
                if (place != null) places.putIfAbsent(place.id(), place);
            }
            return new ArrayList<>(places.values());
        });
    }

    /**
     * Searches venues only. Kakao returns at most 45 places over three pages and a generic word
     * matches many non-venues, so further pages are read until enough venues are found.
     */
    public List<PlaceDto> searchVenues(String memberKey, String rawQuery) {
        String query = validQuery(rawQuery);
        guard.acquireSearch(memberKey);
        return guard.cached("venue|" + query, () -> {
            Map<String, PlaceDto> venues = new LinkedHashMap<>();
            for (int p = 1; p <= KakaoLocalClient.MAX_PAGE && venues.size() < ENOUGH_RESULTS; p++) {
                KakaoLocalClient.KeywordPage result = page(query, p);
                for (KakaoLocalClient.Document doc : documents(result)) {
                    if (!VenueFilter.isVenue(doc.placeName(), doc.categoryName())) continue;
                    PlaceDto place = toDto(doc);
                    if (place != null) venues.putIfAbsent(place.id(), place);
                }
                if (result.meta() == null || result.meta().isEnd()) break;
            }
            return new ArrayList<>(venues.values());
        });
    }

    private KakaoLocalClient.KeywordPage page(String query, int page) {
        guard.acquireKakaoCall();
        return kakao.search(query, page);
    }

    private static List<KakaoLocalClient.Document> documents(KakaoLocalClient.KeywordPage result) {
        return result.documents() == null ? List.of() : result.documents();
    }

    /** Trimmed with inner runs of spaces collapsed, so "롯데호텔  서울" shares the cache with "롯데호텔 서울". */
    private static String validQuery(String rawQuery) {
        String query = rawQuery == null ? "" : rawQuery.strip().replaceAll("\\s+", " ");
        if (query.length() < 2) throw new IllegalArgumentException("검색어를 두 글자 이상 입력해 주세요.");
        if (query.length() > MAX_QUERY_LENGTH)
            throw new IllegalArgumentException("검색어는 " + MAX_QUERY_LENGTH + "자 이하로 입력해 주세요.");
        return query;
    }

    private static PlaceDto toDto(KakaoLocalClient.Document doc) {
        if (blankToNull(doc.id()) == null || blankToNull(doc.placeName()) == null) return null;
        String road = blankToNull(doc.roadAddressName());
        String jibun = blankToNull(doc.addressName());
        if (road == null && jibun == null) return null;
        try {
            double lng = Double.parseDouble(doc.x());
            double lat = Double.parseDouble(doc.y());
            String category = doc.categoryName() == null ? "" : lastSegment(doc.categoryName());
            return new PlaceDto(doc.id(), doc.placeName().trim(), category, road, jibun,
                    blankToNull(doc.phone()), lat, lng, blankToNull(doc.placeUrl()));
        } catch (NumberFormatException | NullPointerException e) {
            return null;
        }
    }

    private static String lastSegment(String category) {
        String[] parts = category.split(">");
        return parts[parts.length - 1].trim();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
