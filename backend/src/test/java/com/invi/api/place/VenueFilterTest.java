package com.invi.api.place;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VenueFilterTest {

    @Test
    void acceptsWeddingHallsHotelsAndConventionHalls() {
        assertThat(VenueFilter.isVenue("더채플앳청담", "가정,생활 > 결혼 > 웨딩홀")).isTrue();
        assertThat(VenueFilter.isVenue("더채플앳 청담", "가정,생활 > 결혼 > 예식장")).isTrue();
        assertThat(VenueFilter.isVenue("롯데호텔 서울", "여행 > 숙박 > 호텔 > 특급호텔")).isTrue();
        assertThat(VenueFilter.isVenue("코엑스", "사회,공공기관 > 행사장 > 컨벤션센터")).isTrue();
    }

    @Test
    void acceptsHallsFiledAsRestaurantsOnlyWhenTheNameSaysSo() {
        assertThat(VenueFilter.isVenue("라비두스 웨딩홀", "음식점 > 뷔페")).isTrue();
        assertThat(VenueFilter.isVenue("동네 뷔페", "음식점 > 뷔페")).isFalse();
        assertThat(VenueFilter.isVenue("더그랜드롯데 서울 크리스탈볼룸", "문화,예술 > 문화시설")).isTrue();
        assertThat(VenueFilter.isVenue("시립 미술관", "문화,예술 > 문화시설")).isFalse();
    }

    @Test
    void rejectsWeddingServicesAndPlacesNearAVenue() {
        assertThat(VenueFilter.isVenue("청담 웨딩드레스", "가정,생활 > 결혼 > 웨딩드레스")).isFalse();
        assertThat(VenueFilter.isVenue("웨딩 스튜디오", "가정,생활 > 결혼 > 웨딩스튜디오")).isFalse();
        assertThat(VenueFilter.isVenue("롯데호텔 주차장", "교통,수송 > 교통시설 > 주차장")).isFalse();
        assertThat(VenueFilter.isVenue("호텔 라운지 카페", "음식점 > 카페")).isFalse();
        assertThat(VenueFilter.isVenue("OO모텔", "여행 > 숙박 > 모텔")).isFalse();
        assertThat(VenueFilter.isVenue("신라호텔", "여행 > 숙박 > 여관,모텔")).isFalse();
        assertThat(VenueFilter.isVenue("웨디플", "가정,생활 > 결혼 > 웨딩컨설팅")).isFalse();
        assertThat(VenueFilter.isVenue("웨딩홀", null)).isFalse();
    }
}
