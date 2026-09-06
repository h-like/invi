package com.invi.api.common;

import com.invi.api.account.CustomOAuth2UserService;
import com.invi.api.account.JwtAuthenticationFilter;
import com.invi.api.account.OAuth2LoginSuccessHandler;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Kakao/Google login via oauth2Login() issues our own JWT on success
 * (OAuth2LoginSuccessHandler) instead of relying on the session — every
 * subsequent API call is authenticated by JwtAuthenticationFilter reading
 * "Authorization: Bearer <token>". Guest-facing endpoints (templates, a
 * published invitation by slug, RSVP/guestbook submission) stay public since
 * hakgek never log in.
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

    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectProvider<ClientRegistrationRepository> clientRegistrations;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(HttpMethod.GET, "/api/hello", "/actuator/**", "/api/templates/**")
                                        .permitAll()
                                        .requestMatchers(
                                                HttpMethod.GET,
                                                "/api/invitations/slug/**",
                                                "/api/invitations/slug-available")
                                        .permitAll()
                                        .requestMatchers(
                                                HttpMethod.GET, "/api/invitations/*/rsvps", "/api/invitations/*/guestbook")
                                        .permitAll()
                                        .requestMatchers(
                                                HttpMethod.POST, "/api/invitations/*/rsvps", "/api/invitations/*/guestbook")
                                        .permitAll()
                                        .requestMatchers("/oauth2/**", "/login/**")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated());

        if (clientRegistrations.getIfAvailable() != null) {
            http.oauth2Login(
                    oauth2 ->
                            oauth2.userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
                                    .successHandler(oAuth2LoginSuccessHandler));
        }

        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:5173"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
