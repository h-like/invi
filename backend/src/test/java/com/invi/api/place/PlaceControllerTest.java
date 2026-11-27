package com.invi.api.place;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invi.api.account.CustomOAuth2UserService;
import com.invi.api.account.JwtAuthenticationFilter;
import com.invi.api.account.JwtService;
import com.invi.api.account.OAuth2LoginSuccessHandler;
import com.invi.api.common.SecurityConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PlaceController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class PlaceControllerTest {

    @Autowired private MockMvc mvc;

    @MockitoBean private PlaceSearchService placeSearchService;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private CustomOAuth2UserService customOAuth2UserService;
    @MockitoBean private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;

    @Test
    void search_requiresLogin() throws Exception {
        mvc.perform(get("/api/places/search").param("query", "롯데호텔")).andExpect(status().isUnauthorized());
        verify(placeSearchService, never()).searchVenues(any());
    }

    @Test
    void search_returnsVenues() throws Exception {
        when(placeSearchService.searchVenues("롯데호텔")).thenReturn(List.of(new PlaceDto(
                "123", "롯데호텔 서울", "특급호텔", "서울 중구 을지로 30", "서울 중구 소공동 1", null, 37.56, 126.98, null)));

        mvc.perform(get("/api/places/search").param("query", "롯데호텔").with(user("member")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("123"))
                .andExpect(jsonPath("$[0].roadAddress").value("서울 중구 을지로 30"));
    }

    @Test
    void search_scopeAllUsesTheUnfilteredSearch_andUnknownScopeIs400() throws Exception {
        when(placeSearchService.searchAll("명동성당")).thenReturn(List.of(new PlaceDto(
                "10", "명동성당", "성당", "서울 중구 명동길 74", null, null, 37.56, 126.98, null)));

        mvc.perform(get("/api/places/search").param("query", "명동성당").param("scope", "all").with(user("member")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].category").value("성당"));
        mvc.perform(get("/api/places/search").param("query", "명동성당").param("scope", "x").with(user("member")))
                .andExpect(status().isBadRequest());
        verify(placeSearchService, never()).searchVenues(any());
    }

    @Test
    void search_answers400ForBadQuery_and503WhenKakaoIsUnavailable() throws Exception {
        when(placeSearchService.searchVenues("가")).thenThrow(new IllegalArgumentException("검색어를 두 글자 이상 입력해 주세요."));
        mvc.perform(get("/api/places/search").param("query", "가").with(user("member")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("검색어를 두 글자 이상 입력해 주세요."));

        when(placeSearchService.searchVenues("호텔")).thenThrow(new PlaceSearchUnavailableException("지도 검색이 아직 설정되지 않았습니다."));
        mvc.perform(get("/api/places/search").param("query", "호텔").with(user("member")))
                .andExpect(status().isServiceUnavailable());
    }
}
