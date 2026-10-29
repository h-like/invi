package com.invi.api.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class HttpsTestHeadersFilterTest {

    private final HttpsTestHeadersFilter filter = new HttpsTestHeadersFilter();

    @Test
    void explicitOrder_runsAfterForwardedHeadersAndBeforeSecurity() {
        Order order = HttpsTestHeadersFilter.class.getAnnotation(Order.class);

        assertThat(order).isNotNull();
        assertThat(order.value()).isEqualTo(Ordered.HIGHEST_PRECEDENCE + 10);
        assertThat(order.value()).isGreaterThan(Ordered.HIGHEST_PRECEDENCE);
        // Spring Security's servlet filter order is -100; avoid coupling to Boot's package layout.
        assertThat(order.value()).isLessThan(-100);
    }

    @Test
    void addsNoIndexAndNoReferrerHeaders_beforeCallingChainExactlyOnce() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/i/our-wedding");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicInteger calls = new AtomicInteger();
        FilterChain chain = (actualRequest, actualResponse) -> {
            calls.incrementAndGet();
            assertThat(actualRequest).isSameAs(request);
            assertThat(actualResponse).isSameAs(response);
            assertPrivacyHeaders(response);
            actualResponse.setContentType(MediaType.TEXT_HTML_VALUE);
            actualResponse.getWriter().write("<!doctype html><html>shell</html>");
        };

        filter.doFilter(request, response, chain);

        assertThat(calls.get()).isEqualTo(1);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).startsWith(MediaType.TEXT_HTML_VALUE);
        assertThat(response.getContentAsString()).isEqualTo("<!doctype html><html>shell</html>");
        assertPrivacyHeaders(response);
    }

    @Test
    void successfulJson_keepsStatusContentTypeAndBody() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/invitations");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicInteger calls = new AtomicInteger();
        String body = "{\"id\":\"123\",\"state\":\"draft\"}";

        filter.doFilter(request, response, (actualRequest, actualResponse) -> {
            calls.incrementAndGet();
            ((HttpServletResponse) actualResponse).setStatus(201);
            actualResponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
            actualResponse.getWriter().write(body);
        });

        assertThat(calls.get()).isEqualTo(1);
        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_JSON_VALUE);
        assertThat(response.getContentAsString()).isEqualTo(body);
        assertThat(response.getForwardedUrl()).isNull();
        assertPrivacyHeaders(response);
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404, 500, 503})
    void jsonErrors_keepOriginalStatusAndBody_andAreNeverReplacedWithHtml(int status) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/private");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicInteger calls = new AtomicInteger();
        String body = "{\"message\":\"original API error\",\"status\":" + status + "}";

        filter.doFilter(request, response, (actualRequest, actualResponse) -> {
            calls.incrementAndGet();
            ((HttpServletResponse) actualResponse).setStatus(status);
            actualResponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
            actualResponse.getWriter().write(body);
        });

        assertThat(calls.get()).isEqualTo(1);
        assertThat(response.getStatus()).isEqualTo(status);
        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_JSON_VALUE);
        assertThat(response.getContentAsString()).isEqualTo(body);
        assertThat(response.getForwardedUrl()).isNull();
        assertThat(response.getRedirectedUrl()).isNull();
        assertPrivacyHeaders(response);
    }

    @Test
    void missingAsset_keepsNotFoundWithoutForwardingToIndex() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/assets/missing.js");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicInteger calls = new AtomicInteger();

        filter.doFilter(request, response, (actualRequest, actualResponse) -> {
            calls.incrementAndGet();
            ((HttpServletResponse) actualResponse).sendError(404, "Static resource not found");
        });

        assertThat(calls.get()).isEqualTo(1);
        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.getErrorMessage()).isEqualTo("Static resource not found");
        assertThat(response.getContentAsString()).isEmpty();
        assertThat(response.getForwardedUrl()).isNull();
        assertPrivacyHeaders(response);
    }

    @Test
    void redirect_keepsLocationAndStatus() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth/callback");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicInteger calls = new AtomicInteger();

        filter.doFilter(request, response, (actualRequest, actualResponse) -> {
            calls.incrementAndGet();
            ((HttpServletResponse) actualResponse).sendRedirect("/login");
        });

        assertThat(calls.get()).isEqualTo(1);
        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getRedirectedUrl()).isEqualTo("/login");
        assertThat(response.getForwardedUrl()).isNull();
        assertPrivacyHeaders(response);
    }

    @Test
    void replacesLessRestrictiveHeaders_withoutDuplicatingValues() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/robots.txt");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setHeader("X-Robots-Tag", "index, follow");
        response.setHeader("Referrer-Policy", "unsafe-url");
        AtomicInteger calls = new AtomicInteger();

        filter.doFilter(request, response, (actualRequest, actualResponse) -> calls.incrementAndGet());

        assertThat(calls.get()).isEqualTo(1);
        assertThat(response.getHeaders("X-Robots-Tag")).containsExactly("noindex, nofollow, noarchive");
        assertThat(response.getHeaders("Referrer-Policy")).containsExactly("no-referrer");
    }

    @Test
    void chainIOException_isPropagatedWithoutRenderingHtml() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/private");
        MockHttpServletResponse response = new MockHttpServletResponse();
        IOException failure = new IOException("downstream failed");
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> filter.doFilter(request, response, (actualRequest, actualResponse) -> {
            calls.incrementAndGet();
            throw failure;
        })).isSameAs(failure);

        assertThat(calls.get()).isEqualTo(1);
        assertThat(response.getContentAsByteArray()).isEmpty();
        assertThat(response.getForwardedUrl()).isNull();
        assertPrivacyHeaders(response);
    }

    @Test
    void chainServletException_isPropagatedWithoutRenderingHtml() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/i/our-wedding");
        MockHttpServletResponse response = new MockHttpServletResponse();
        ServletException failure = new ServletException("downstream failed");
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> filter.doFilter(request, response, (actualRequest, actualResponse) -> {
            calls.incrementAndGet();
            throw failure;
        })).isSameAs(failure);

        assertThat(calls.get()).isEqualTo(1);
        assertThat(response.getContentAsByteArray()).isEmpty();
        assertThat(response.getForwardedUrl()).isNull();
        assertPrivacyHeaders(response);
    }

    private static void assertPrivacyHeaders(MockHttpServletResponse response) {
        assertThat(response.getHeader("X-Robots-Tag")).isEqualTo("noindex, nofollow, noarchive");
        assertThat(response.getHeader("Referrer-Policy")).isEqualTo("no-referrer");
    }
}
