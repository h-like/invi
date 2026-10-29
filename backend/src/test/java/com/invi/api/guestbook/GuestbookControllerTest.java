package com.invi.api.guestbook;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invi.api.account.CurrentMember;
import com.invi.api.account.CustomOAuth2UserService;
import com.invi.api.account.JwtAuthenticationFilter;
import com.invi.api.account.JwtService;
import com.invi.api.account.OAuth2LoginSuccessHandler;
import com.invi.api.common.SecurityConfig;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(GuestbookController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class GuestbookControllerTest {

    @Autowired private MockMvc mvc;

    @MockitoBean private GuestbookService guestbookService;
    @MockitoBean private CurrentMember currentMember;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private CustomOAuth2UserService customOAuth2UserService;
    @MockitoBean private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;

    private final UUID invitationId = UUID.randomUUID();

    @Test
    void submit_acceptsMessageAtTheLimit_withoutAuthentication() throws Exception {
        when(guestbookService.submit(eq(invitationId), any()))
                .thenReturn(new GuestbookEntryDto(UUID.randomUUID(), "하객", "축"));

        mvc.perform(post("/api/invitations/{id}/guestbook", invitationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("가".repeat(30), "축".repeat(500))))
                .andExpect(status().isCreated());
    }

    @Test
    void submit_rejectsOverlongMessageWith400_beforeReachingTheDatabase() throws Exception {
        mvc.perform(post("/api/invitations/{id}/guestbook", invitationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("하객", "축".repeat(501))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("message: 메시지는 500자 이하로 입력해 주세요."));
        verify(guestbookService, never()).submit(any(), any());
    }

    @Test
    void submit_rejectsOverlongAuthorWith400() throws Exception {
        mvc.perform(post("/api/invitations/{id}/guestbook", invitationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("가".repeat(31), "축하해요")))
                .andExpect(status().isBadRequest());
        verify(guestbookService, never()).submit(any(), any());
    }

    private static String body(String author, String message) {
        return "{\"author\":\"" + author + "\",\"message\":\"" + message + "\"}";
    }
}
