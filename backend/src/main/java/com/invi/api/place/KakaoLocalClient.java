package com.invi.api.place;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Kakao Local keyword search (https://developers.kakao.com/docs/latest/ko/local/dev-guide).
 * Called from the server only, so the REST API key never reaches a browser.
 */
@Component
class KakaoLocalClient {

    static final int PAGE_SIZE = 15;
    static final int MAX_PAGE = 3;

    private final RestClient restClient;
    private final String restApiKey;

    KakaoLocalClient(
            @Value("${invi.kakao.local.base-url:https://dapi.kakao.com}") String baseUrl,
            @Value("${invi.kakao.local.rest-api-key:}") String restApiKey) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.restApiKey = restApiKey == null ? "" : restApiKey.trim();
    }

    KeywordPage search(String query, int page) {
        if (restApiKey.isEmpty()) throw new PlaceSearchUnavailableException("지도 검색이 아직 설정되지 않았습니다.");
        try {
            KeywordPage result = restClient.get()
                    .uri(u -> u.path("/v2/local/search/keyword.json")
                            .queryParam("query", query)
                            .queryParam("page", page)
                            .queryParam("size", PAGE_SIZE)
                            .build())
                    .header("Authorization", "KakaoAK " + restApiKey)
                    .retrieve()
                    .body(KeywordPage.class);
            return result == null ? new KeywordPage(List.of(), new Meta(true)) : result;
        } catch (RestClientResponseException e) {
            throw new PlaceSearchUnavailableException(
                    "지도 검색에 실패했습니다 (" + e.getStatusCode().value() + ").", e);
        } catch (RestClientException e) {
            throw new PlaceSearchUnavailableException("지도 검색 서버에 연결하지 못했습니다.", e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record KeywordPage(List<Document> documents, Meta meta) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Meta(@JsonProperty("is_end") boolean isEnd) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Document(
            String id,
            @JsonProperty("place_name") String placeName,
            @JsonProperty("category_name") String categoryName,
            String phone,
            @JsonProperty("address_name") String addressName,
            @JsonProperty("road_address_name") String roadAddressName,
            String x,
            String y,
            @JsonProperty("place_url") String placeUrl) {}
}
