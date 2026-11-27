package com.invi.api.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.invi.api.place.KakaoLocalClient.Document;
import com.invi.api.place.KakaoLocalClient.KeywordPage;
import com.invi.api.place.KakaoLocalClient.Meta;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class PlaceSearchServiceTest {

    private final KakaoLocalClient kakao = mock(KakaoLocalClient.class);
    private final PlaceSearchService service = new PlaceSearchService(kakao);

    private static Document doc(String id, String name, String category) {
        return new Document(id, name, category, "02-000-0000", "서울 강남구 청담동 1", "서울 강남구 도산대로 1",
                "127.04", "37.52", "http://place.map.kakao.com/" + id);
    }

    @Test
    void returnsOnlyVenues_withExactAddressAndCoordinates() {
        when(kakao.search("청담 웨딩", 1)).thenReturn(new KeywordPage(List.of(
                doc("1", "청담 웨딩홀", "가정,생활 > 결혼 > 웨딩홀"),
                doc("2", "청담 웨딩드레스", "가정,생활 > 결혼 > 웨딩드레스")), new Meta(true)));

        List<PlaceDto> result = service.searchVenues("  청담 웨딩 ");

        assertThat(result).extracting(PlaceDto::id).containsExactly("1");
        PlaceDto place = result.get(0);
        assertThat(place.roadAddress()).isEqualTo("서울 강남구 도산대로 1");
        assertThat(place.category()).isEqualTo("웨딩홀");
        assertThat(place.lat()).isEqualTo(37.52);
        assertThat(place.lng()).isEqualTo(127.04);
    }

    @Test
    void readsFurtherPages_untilEnoughVenues() {
        when(kakao.search("호텔", 1)).thenReturn(new KeywordPage(
                List.of(doc("p", "호텔 주차장", "교통,수송 > 교통시설 > 주차장")), new Meta(false)));
        when(kakao.search("호텔", 2)).thenReturn(new KeywordPage(
                IntStream.range(0, 10).mapToObj(i -> doc("h" + i, "호텔" + i, "여행 > 숙박 > 호텔")).toList(),
                new Meta(false)));

        assertThat(service.searchVenues("호텔")).hasSize(10);
        verify(kakao, never()).search("호텔", 3);
    }

    @Test
    void skipsPlacesWithoutCoordinatesOrAddress() {
        Document noCoords = new Document("1", "웨딩홀", "가정,생활 > 결혼 > 웨딩홀", null, "주소", null, "", "", null);
        Document noAddress = new Document("2", "웨딩홀", "가정,생활 > 결혼 > 웨딩홀", null, "", " ", "127", "37", null);
        when(kakao.search("웨딩홀", 1)).thenReturn(new KeywordPage(List.of(noCoords, noAddress), new Meta(true)));

        assertThat(service.searchVenues("웨딩홀")).isEmpty();
    }

    @Test
    void rejectsTooShortOrTooLongQueries_withoutCallingKakao() {
        assertThatThrownBy(() -> service.searchVenues(" 가 ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.searchVenues("가".repeat(51))).isInstanceOf(IllegalArgumentException.class);
        verify(kakao, never()).search(anyString(), anyInt());
    }
}
