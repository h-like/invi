package com.invi.api.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

class CorsConfigurationTest {

    private static CorsConfiguration configuration(String origin) {
        return SecurityConfig.corsConfigurationSource(origin)
                .getCorsConfiguration(new MockHttpServletRequest("GET", "/api/invitations"));
    }

    @Test
    void defaultFrontendAllowsOnlyLocalhost() {
        CorsConfiguration config = configuration("http://localhost:5173");
        assertThat(config.getAllowedOrigins()).containsExactly("http://localhost:5173");
        assertThat(config.getAllowCredentials()).isTrue();
        assertThat(config.getAllowedMethods()).containsExactly("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
        assertThat(config.checkOrigin("https://other.example.com")).isNull();
    }

    @Test
    void explicitLanFrontendDoesNotOpenOtherHostsOrPorts() {
        CorsConfiguration config = configuration("http://192.168.0.10:5173");
        assertThat(config.getAllowedOrigins()).containsExactly("http://192.168.0.10:5173");
        assertThat(config.checkOrigin("http://192.168.0.10:5173")).isEqualTo("http://192.168.0.10:5173");
        assertThat(config.checkOrigin("http://192.168.0.11:5173")).isNull();
        assertThat(config.checkOrigin("http://192.168.0.10:5174")).isNull();
        assertThat(config.checkOrigin("http://localhost:5173")).isNull();
    }

    @Test
    void normalizesHostnameRootSlashAndDefaultPort() {
        assertThat(configuration(" HTTP://LOCALHOST:5173/ ").getAllowedOrigins())
                .containsExactly("http://localhost:5173");
        assertThat(configuration("https://app.example.com:443/").getAllowedOrigins())
                .containsExactly("https://app.example.com");
    }

    @Test
    void explicitIpv6OriginKeepsItsBrackets() {
        assertThat(configuration("http://[::1]:5173/").getAllowedOrigins())
                .containsExactly("http://[::1]:5173");
    }

    @Test
    void rejectsWildcardCredentialsPathsAndMalformedOriginsWithoutEchoingInput() {
        for (String origin : List.of("*", "http://*.example.com", "/", "not a url", "file:///tmp",
                "http://user:example-secret@localhost:5173", "http://localhost:5173/path",
                "http://localhost:5173?query=1", "http://localhost:5173#fragment", "http://localhost:99999")) {
            assertThatThrownBy(() -> configuration(origin))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("invi.frontend.base-url")
                    .hasMessageNotContaining("example-secret");
        }
    }

    @Test
    void matchingPreflightAllowsAuthorizationAndContentType() throws Exception {
        CorsConfigurationSource source = SecurityConfig.corsConfigurationSource("http://192.168.0.10:5173");
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/invitations");
        request.addHeader("Origin", "http://192.168.0.10:5173");
        request.addHeader("Access-Control-Request-Method", "POST");
        request.addHeader("Access-Control-Request-Headers", "Authorization, Content-Type");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new CorsFilter(source).doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader("Access-Control-Allow-Origin")).isEqualTo("http://192.168.0.10:5173");
        assertThat(response.getHeader("Access-Control-Allow-Credentials")).isEqualTo("true");
        assertThat(response.getHeader("Access-Control-Allow-Headers")).containsIgnoringCase("authorization");
        assertThat(response.getHeader("Access-Control-Allow-Headers")).containsIgnoringCase("content-type");
    }

    @Test
    void otherOriginPreflightIsRejectedWithoutAllowOriginHeader() throws Exception {
        CorsConfigurationSource source = SecurityConfig.corsConfigurationSource("http://192.168.0.10:5173");
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/invitations");
        request.addHeader("Origin", "http://192.168.0.11:5173");
        request.addHeader("Access-Control-Request-Method", "POST");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new CorsFilter(source).doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getHeader("Access-Control-Allow-Origin")).isNull();
    }
}
