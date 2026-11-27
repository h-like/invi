package com.invi.api.place;

import java.util.Arrays;
import java.util.List;

/**
 * Keeps only places a wedding can be held at (wedding halls, hotels, convention and banquet
 * halls), so a couple cannot pick a dress shop, a hotel's parking lot or a same-named cafe.
 * Kakao's category_name is a path such as "가정,생활 > 결혼 > 웨딩홀"; we match its segments.
 */
final class VenueFilter {

    /** Category segments that are a ceremony venue on their own. */
    private static final List<String> VENUE_CATEGORIES =
            List.of("웨딩홀", "예식장", "하우스웨딩", "스몰웨딩", "컨벤션", "연회장", "호텔");

    /**
     * Many halls are filed as a buffet, and hotel ballrooms as a cultural venue
     * ("문화,예술 > 문화시설"); their name still says what they are.
     */
    private static final List<String> VENUE_NAMES = List.of("웨딩", "예식", "컨벤션", "볼룸");

    private static final List<String> NAME_MATCHED_ROOTS = List.of("음식점", "문화,예술");

    /** Wedding services that share the 결혼 category but are not a place to hold one. */
    private static final List<String> NOT_VENUES = List.of(
            "드레스", "스튜디오", "메이크업", "한복", "결혼정보", "웨딩컨설팅", "폐백", "예물", "웨딩카", "청첩장",
            "사진", "모텔", "주차장");

    private VenueFilter() {}

    static boolean isVenue(String placeName, String categoryName) {
        if (categoryName == null || categoryName.isBlank()) return false;
        List<String> segments = Arrays.stream(categoryName.split(">")).map(String::trim).toList();
        if (segments.stream().anyMatch(s -> NOT_VENUES.stream().anyMatch(s::contains))) return false;
        if (segments.stream().anyMatch(s -> VENUE_CATEGORIES.stream().anyMatch(s::contains))) return true;
        String name = placeName == null ? "" : placeName;
        return NAME_MATCHED_ROOTS.contains(segments.get(0)) && VENUE_NAMES.stream().anyMatch(name::contains);
    }
}
