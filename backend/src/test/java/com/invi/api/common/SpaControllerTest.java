package com.invi.api.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invi.api.account.CustomOAuth2UserService;
import com.invi.api.account.JwtAuthenticationFilter;
import com.invi.api.account.JwtService;
import com.invi.api.account.OAuth2LoginSuccessHandler;
import com.invi.api.invitation.GuestLinkPreviewController;
import com.invi.api.invitation.InvitationService;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@WebMvcTest(controllers = {SpaController.class, GuestLinkPreviewController.class},
        properties = "invi.frontend.base-url=https://invi-test.example.com")
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, HttpsTestHeadersFilter.class})
@ActiveProfiles("https-test")
class SpaControllerTest {

    @Autowired private MockMvc mvc;
    @Autowired private ApplicationContext context;

    @MockitoBean private JwtService jwtService;
    @MockitoBean private CustomOAuth2UserService customOAuth2UserService;
    @MockitoBean private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    @MockitoBean private InvitationService invitationService;

    // MockMvc records the forward only: the frontend's compiled index.html is not in this checkout,
    // so GuestLinkPreviewController falls back to the same forward for /i/{slug}.
    @ParameterizedTest
    @ValueSource(strings = {"/", "/templates", "/templates/classic", "/login", "/oauth/callback",
            "/create", "/invitations", "/e/123", "/e/123/guests", "/i/our-wedding"})
    void get_explicitShellRoutes_arePublicAndForwardToIndex(String path) throws Exception {
        mvc.perform(get(path).accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"))
                .andExpect(header().string("X-Robots-Tag", "noindex, nofollow, noarchive"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/templates", "/templates/classic", "/login", "/oauth/callback",
            "/create", "/invitations", "/e/123", "/e/123/guests", "/i/our-wedding"})
    void head_explicitShellRoutes_arePublicAndForwardToIndex(String path) throws Exception {
        mvc.perform(head(path).accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"))
                .andExpect(header().string("X-Robots-Tag", "noindex, nofollow, noarchive"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/private", "/api/invitations/123"})
    void get_privateApi_remainsUnauthorizedEvenWhenHtmlIsAccepted(String path) throws Exception {
        MvcResult result = mvc.perform(get(path).accept(MediaType.TEXT_HTML))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Robots-Tag", "noindex, nofollow, noarchive"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().doesNotExist("Location"))
                .andReturn();
        assertNotShell(result);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/private", "/api/invitations/123"})
    void head_privateApi_remainsUnauthorized(String path) throws Exception {
        MvcResult result = mvc.perform(head(path).accept(MediaType.TEXT_HTML))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Robots-Tag", "noindex, nofollow, noarchive"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().doesNotExist("Location"))
                .andReturn();
        assertNotShell(result);
    }

    @Test
    void rejectedCorsPreflight_retainsPrivacyHeaders() throws Exception {
        MvcResult result = mvc.perform(options("/api/private")
                        .header("Origin", "https://wrong-origin.example.com")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"))
                .andExpect(header().string("X-Robots-Tag", "noindex, nofollow, noarchive"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andReturn();
        assertNotShell(result);
    }

    @Test
    void post_invitationShell_isNotPublic() throws Exception {
        MvcResult result = mvc.perform(post("/i/our-wedding").accept(MediaType.TEXT_HTML))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Location"))
                .andReturn();
        assertNotShell(result);
    }

    @Test
    void post_invitationShell_hasNoControllerPostMappingEvenWithoutSecurity() throws Exception {
        MockMvc controllerOnly = MockMvcBuilders.standaloneSetup(new GuestLinkPreviewController(
                invitationService, new DefaultResourceLoader(), "https://invi-test.example.com", "")).build();
        MvcResult result = controllerOnly.perform(post("/i/our-wedding").accept(MediaType.TEXT_HTML))
                .andExpect(status().isMethodNotAllowed())
                .andReturn();
        assertNotShell(result);
    }

    @Test
    void missingJavaScript_isNotFoundRatherThanIndex() throws Exception {
        MvcResult result = mvc.perform(get("/assets/missing.js").accept(MediaType.ALL))
                .andExpect(status().isNotFound())
                .andReturn();
        assertNotShell(result);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/unknown-spa-route", "/i/our-wedding/extra", "/e/123/unknown"})
    void unknownRoutes_areNotPublicShellRoutes(String path) throws Exception {
        MvcResult result = mvc.perform(get(path).accept(MediaType.TEXT_HTML))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Location"))
                .andReturn();
        assertNotShell(result);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/unknown-spa-route", "/i/our-wedding/extra", "/e/123/unknown"})
    void unknownRoutes_haveNoControllerFallbackEvenWithoutSecurity(String path) throws Exception {
        // Security rejects these before MVC. Test mapping separately so a future catch-all cannot hide behind 401.
        MockMvc controllerOnly = MockMvcBuilders.standaloneSetup(new SpaController()).build();
        MvcResult result = controllerOnly.perform(get(path).accept(MediaType.TEXT_HTML))
                .andExpect(status().isNotFound())
                .andReturn();
        assertNotShell(result);
    }

    @Test
    void robots_disallowsEveryCrawlerAndPath() throws Exception {
        mvc.perform(get("/robots.txt"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string("User-agent: *\nDisallow: /\n"))
                .andExpect(header().string("X-Robots-Tag", "noindex, nofollow, noarchive"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(result -> assertThat(result.getResponse().getForwardedUrl()).isNull());
    }

    @Test
    void mvcSlice_doesNotLoadDatabaseR2OrDeploymentGuard() {
        assertThat(context.getBeanNamesForType(DataSource.class)).isEmpty();
        assertThat(context.getBeanNamesForType(S3Client.class)).isEmpty();
        assertThat(context.getBeanNamesForType(S3Presigner.class)).isEmpty();
        assertThat(context.getBeanNamesForType(HttpsTestDeploymentGuard.class)).isEmpty();
        assertThat(context.getEnvironment().getProperty("invi.frontend.base-url"))
                .isEqualTo("https://invi-test.example.com");
    }

    private static void assertNotShell(MvcResult result) throws Exception {
        assertThat(result.getResponse().getForwardedUrl()).isNull();
        assertThat(result.getResponse().getContentAsString())
                .doesNotContainIgnoringCase("<html", "<!doctype html");
        String contentType = result.getResponse().getContentType();
        if (contentType != null) {
            assertThat(MediaType.parseMediaType(contentType).isCompatibleWith(MediaType.TEXT_HTML)).isFalse();
        }
    }
}
