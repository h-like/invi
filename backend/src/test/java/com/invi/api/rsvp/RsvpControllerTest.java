package com.invi.api.rsvp;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(RsvpController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class RsvpControllerTest {

    @Autowired private MockMvc mvc;

    @MockitoBean private RsvpService rsvpService;
    @MockitoBean private CurrentMember currentMember;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private CustomOAuth2UserService customOAuth2UserService;
    @MockitoBean private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;

    private final UUID invitationId = UUID.randomUUID();

    @Test
    void submit_acceptsValuesAtTheLimits() throws Exception {
        submit("가".repeat(30), 20, "말".repeat(200)).andExpect(status().isCreated());
    }

    @Test
    void submit_rejectsGuestCountAboveTwenty() throws Exception {
        submit("하객", 21, null).andExpect(status().isBadRequest());
        verify(rsvpService, never()).submit(any(), any(), any());
    }

    @Test
    void submit_rejectsOverlongNameOrMessage() throws Exception {
        submit("가".repeat(31), 1, null).andExpect(status().isBadRequest());
        submit("하객", 1, "말".repeat(201)).andExpect(status().isBadRequest());
        verify(rsvpService, never()).submit(any(), any(), any());
    }

    private ResultActions submit(String name, int count, String message)
            throws Exception {
        String messageJson = message == null ? "null" : "\"" + message + "\"";
        return mvc.perform(post("/api/invitations/{id}/rsvps", invitationId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"guestName\":\"" + name + "\",\"attending\":true,\"guestCount\":" + count
                        + ",\"message\":" + messageJson + "}"));
    }
}
