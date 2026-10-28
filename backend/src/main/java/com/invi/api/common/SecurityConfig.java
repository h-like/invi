package com.invi.api.common;

import com.invi.api.account.CustomOAuth2UserService;
import com.invi.api.account.JwtAuthenticationFilter;
import com.invi.api.account.OAuth2LoginSuccessHandler;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Kakao/Google login via oauth2Login() issues our own JWT on success
 * (OAuth2LoginSuccessHandler) instead of relying on the session — every
 * subsequent API call is authenticated by JwtAuthenticationFilter reading
 * "Authorization: Bearer <token>". Guest-facing endpoints (templates, a
 * published invitation by slug, RSVP submission, guestbook read/submit) stay
 * public since hakgek never log in. Everything else — including reading RSVP
 * responses, which is an owner-only action per the 기획서 — requires a token,
 * and the service layer additionally checks that the token's member actually
 * owns the invitation (InvitationService/RsvpService/GuestbookService's
 * getOwnedOrThrow-style checks).
 *
 * oauth2Login() is wired only when a ClientRegistrationRepository bean exists —
 * i.e. when application-local.yml (gitignored, real Kakao/Google secrets) is on
 * the active profile. Without it (CI, contextLoads(), a fresh clone before
 * secrets are set up), the app still boots — just without login capability —
 * instead of failing on a missing bean.
 */
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    @Bean
    public org.springframework.security.crypto.password.PasswordEncoder passwordEncoder() {
        return new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
    }

    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectProvider<ClientRegistrationRepository> clientRegistrations;

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            @Value("${invi.frontend.base-url:http://localhost:5173}") String frontendBaseUrl) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource(frontendBaseUrl)))
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.GET, "/api/hello", "/actuator/**", "/api/templates/**")
                                        .permitAll()
                                        .requestMatchers(
                                                HttpMethod.GET,
                                                "/api/invitations/slug/**",
                                                "/api/invitations/slug-available")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.GET, "/api/invitations/*/guestbook")
                                        .permitAll()
                                        .requestMatchers(
                                                HttpMethod.POST, "/api/invitations/*/rsvps", "/api/invitations/*/guestbook")
                                        .permitAll()
                                        .requestMatchers("/oauth2/**", "/login/**")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .exceptionHandling(
                        exceptions ->
                                exceptions.defaultAuthenticationEntryPointFor(
                                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                        request -> request.getRequestURI().startsWith("/api/")));

        if (clientRegistrations.getIfAvailable() != null) {
            http.oauth2Login(
                    oauth2 ->
                            oauth2.userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
                                    .successHandler(oAuth2LoginSuccessHandler));
        }

        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    static CorsConfigurationSource corsConfigurationSource(String frontendBaseUrl) {
        CorsConfiguration config = new CorsConfiguration();
        // One explicit frontend origin. Never allow every site or an entire LAN subnet.
        config.setAllowedOrigins(List.of(frontendOrigin(frontendBaseUrl)));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private static String frontendOrigin(String value) {
        try {
            URI uri = URI.create(value.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            String path = uri.getRawPath();
            if (!("http".equals(scheme) || "https".equals(scheme)) || uri.getHost() == null
                    || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || (path != null && !path.isEmpty() && !"/".equals(path)) || uri.getPort() > 65535) {
                throw new IllegalArgumentException();
            }
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            if (host.contains(":") && !host.startsWith("[")) host = "[" + host + "]";
            int port = uri.getPort();
            boolean defaultPort = port == -1 || ("http".equals(scheme) && port == 80)
                    || ("https".equals(scheme) && port == 443);
            return scheme + "://" + host + (defaultPort ? "" : ":" + port);
        } catch (IllegalArgumentException | NullPointerException ex) {
            // Do not echo a misconfigured value that might accidentally contain credentials.
            throw new IllegalArgumentException("invi.frontend.base-url must be an absolute HTTP(S) origin without credentials, path, query or fragment.");
        }
    }
}
