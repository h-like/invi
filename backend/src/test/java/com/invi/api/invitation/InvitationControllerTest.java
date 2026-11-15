package com.invi.api.invitation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invi.api.account.CurrentMember;
import com.invi.api.account.CustomOAuth2UserService;
import com.invi.api.account.JwtAuthenticationFilter;
import com.invi.api.account.JwtService;
import com.invi.api.account.OAuth2LoginSuccessHandler;
import com.invi.api.common.SecurityConfig;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Access rules for the review-link and unpublish endpoints: the review page is public (the token
 * is the permission), while making, revoking or unpublishing needs a logged-in caller. Ownership
 * itself is checked in InvitationService (see InvitationServiceTest).
 */
@WebMvcTest(InvitationController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class InvitationControllerTest {

    @Autowired private MockMvc mvc;

    @MockitoBean private InvitationService invitationService;
    @MockitoBean private CurrentMember currentMember;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private CustomOAuth2UserService customOAuth2UserService;
    @MockitoBean private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;

    private final UUID id = UUID.randomUUID();
    private final UUID memberId = UUID.randomUUID();

    @Test
    void reviewPage_isPublic() throws Exception {
        when(invitationService.findByReviewToken("token"))
                .thenReturn(new GuestInvitationDto(id, "our-wedding", LocalDate.of(2027, 5, 22), "{\"blocks\":[]}", null));

        mvc.perform(get("/api/invitations/review/token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageData.blocks").isArray());
    }

    @Test
    void ownerActions_requireLogin() throws Exception {
        mvc.perform(post("/api/invitations/{id}/review-link", id)).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/invitations/{id}/review-link", id)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/invitations/{id}/unpublish", id)).andExpect(status().isUnauthorized());

        verify(invitationService, never()).issueReviewLink(any(), any());
        verify(invitationService, never()).revokeReviewLink(any(), any());
        verify(invitationService, never()).unpublish(any(), any());
    }

    @Test
    void ownerActions_passTheCallerToTheService() throws Exception {
        when(currentMember.requireId(any())).thenReturn(memberId);
        when(invitationService.issueReviewLink(memberId, id)).thenReturn(new ReviewLinkDto("token"));

        mvc.perform(post("/api/invitations/{id}/review-link", id).with(user(memberId.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("token"));
        mvc.perform(delete("/api/invitations/{id}/review-link", id).with(user(memberId.toString())))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/invitations/{id}/unpublish", id).with(user(memberId.toString())))
                .andExpect(status().isOk());

        verify(invitationService).revokeReviewLink(memberId, id);
        verify(invitationService).unpublish(memberId, id);
    }
}
