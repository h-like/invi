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

    /**
     * Searches venues only. Kakao returns at most 45 places over three pages and a generic word
     * matches many non-venues, so further pages are read until enough venues are found.
     */
    public List<PlaceDto> searchVenues(String rawQuery) {
        String query = rawQuery == null ? "" : rawQuery.strip();
        if (query.length() < 2) throw new IllegalArgumentException("검색어를 두 글자 이상 입력해 주세요.");
        if (query.length() > MAX_QUERY_LENGTH)
            throw new IllegalArgumentException("검색어는 " + MAX_QUERY_LENGTH + "자 이하로 입력해 주세요.");
        Map<String, PlaceDto> venues = new LinkedHashMap<>();
        for (int page = 1; page <= KakaoLocalClient.MAX_PAGE && venues.size() < ENOUGH_RESULTS; page++) {
            KakaoLocalClient.KeywordPage result = kakao.search(query, page);
            List<KakaoLocalClient.Document> docs = result.documents() == null ? List.of() : result.documents();
            for (KakaoLocalClient.Document doc : docs) {
                if (!VenueFilter.isVenue(doc.placeName(), doc.categoryName())) continue;
                PlaceDto place = toDto(doc);
                if (place != null) venues.putIfAbsent(place.id(), place);
            }
            if (result.meta() == null || result.meta().isEnd()) break;
        }
        return new ArrayList<>(venues.values());
    }

    private static PlaceDto toDto(KakaoLocalClient.Document doc) {
        if (blankToNull(doc.id()) == null || blankToNull(doc.placeName()) == null) return null;
        String road = blankToNull(doc.roadAddressName());
        String jibun = blankToNull(doc.addressName());
        if (road == null && jibun == null) return null;
        try {
            double lng = Double.parseDouble(doc.x());
            double lat = Double.parseDouble(doc.y());
            return new PlaceDto(doc.id(), doc.placeName().trim(), lastSegment(doc.categoryName()), road, jibun,
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
