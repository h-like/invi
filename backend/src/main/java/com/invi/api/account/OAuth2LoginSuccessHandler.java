package com.invi.api.account;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * After Kakao/Google login succeeds, issues our own JWT (the SPA calls the API with
 * it, not with the OAuth2 session) and redirects to the frontend's callback route.
 */
@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final JwtService jwtService;

    @Value("${invi.frontend.base-url}")
    private String frontendBaseUrl;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException, ServletException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        UUID memberId = UUID.fromString(oAuth2User.getName());
        String token = jwtService.issue(memberId);

        String redirectUrl =
                UriComponentsBuilder.fromUriString(frontendBaseUrl + "/oauth/callback")
                        .queryParam("token", token)
                        .build()
                        .toUriString();
        response.sendRedirect(redirectUrl);
    }
}
