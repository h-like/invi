package com.invi.api.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;

@ExtendWith(MockitoExtension.class)
class OAuth2LoginSuccessHandlerTest {

    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private Authentication authentication;
    @Mock private OAuth2User oAuth2User;

    // JwtService has no external dependencies, so a real instance is simpler than mocking it.
    private final JwtService jwtService = new JwtService("test-secret-key-at-least-32-bytes-long-0123456789", 60);
    private final OAuth2LoginSuccessHandler handler =
            new OAuth2LoginSuccessHandler(jwtService, "http://localhost:5173");

    @Test
    void redirectsToFrontendCallback_withAJwtCarryingTheMemberId() throws Exception {
        UUID memberId = UUID.randomUUID();
        when(authentication.getPrincipal()).thenReturn(oAuth2User);
        when(oAuth2User.getName()).thenReturn(memberId.toString());

        handler.onAuthenticationSuccess(request, response, authentication);

        verify(response).sendRedirect(startsWith("http://localhost:5173/oauth/callback?token="));
    }

    @Test
    void theIssuedTokenActuallyParsesBackToTheSameMemberId() throws Exception {
        UUID memberId = UUID.randomUUID();
        when(authentication.getPrincipal()).thenReturn(oAuth2User);
        when(oAuth2User.getName()).thenReturn(memberId.toString());

        handler.onAuthenticationSuccess(request, response, authentication);

        ArgumentCaptor<String> redirectUrl = ArgumentCaptor.forClass(String.class);
        verify(response).sendRedirect(redirectUrl.capture());
        String token = redirectUrl.getValue().substring(redirectUrl.getValue().indexOf("token=") + "token=".length());

        assertThat(jwtService.parseMemberId(token)).contains(memberId);
    }
}
