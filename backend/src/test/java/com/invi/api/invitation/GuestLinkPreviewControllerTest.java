package com.invi.api.invitation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class GuestLinkPreviewControllerTest {

    private static final String SHELL = """
            <!doctype html>
            <html lang="ko">
              <head>
                <meta name="description" content="generic" />
                <title>invi | 마음을 담은 초대</title>
              </head>
              <body><div id="root"></div><script type="module" src="/assets/index.js"></script></body>
            </html>
            """;

    private final InvitationService service = mock(InvitationService.class);

    @Test
    void publishedInvitation_getsEscapedOpenGraphTagsAndNoCache() throws Exception {
        when(service.findPublishedPreview("our-day", "https://media.example.com")).thenReturn(Optional.of(
                new LinkPreview("하늘 ♥ \"><script>x</script> 결혼합니다", "2026년 11월 1일 · 정원",
                        "https://media.example.com/invitations/1/a.webp")));

        String body = mvc(SHELL).perform(get("/i/our-day"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-cache"))
                .andExpect(header().string("Content-Type", "text/html;charset=UTF-8"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(body)
                .contains("<meta property=\"og:url\" content=\"https://invi.example.com/i/our-day\" />")
                .contains("<meta property=\"og:image\" content=\"https://media.example.com/invitations/1/a.webp\" />")
                .contains("<meta name=\"twitter:card\" content=\"summary_large_image\" />")
                .contains("<meta property=\"og:description\" content=\"2026년 11월 1일 · 정원\" />")
                .contains("<title>하늘 ♥ &quot;&gt;&lt;script&gt;x&lt;/script&gt; 결혼합니다</title>")
                .doesNotContain("<script>x</script>")
                .doesNotContain("content=\"generic\"")
                .contains("<script type=\"module\" src=\"/assets/index.js\"></script>");
        assertThat(body.indexOf("og:title")).isLessThan(body.indexOf("</head>"));
        assertThat(body).containsOnlyOnce("<title>");
    }

    @Test
    void draftOrUnknownSlug_getsThePlainShellUnchanged() throws Exception {
        when(service.findPublishedPreview(anyString(), anyString())).thenReturn(Optional.empty());

        String body = mvc(SHELL).perform(get("/i/secret-draft"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(body).isEqualTo(SHELL);
    }

    @Test
    void previewWithoutCover_usesSmallCardAndNoImageTag() throws Exception {
        when(service.findPublishedPreview(anyString(), anyString()))
                .thenReturn(Optional.of(new LinkPreview("A ♥ B 결혼합니다", "날짜", null)));

        mvc(SHELL).perform(head("/i/our-day")).andExpect(status().isOk());
        String got = mvc(SHELL).perform(get("/i/our-day"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(got).doesNotContain("og:image").contains("content=\"summary\"");
    }

    @Test
    void missingCompiledShell_fallsBackToTheIndexForward() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new GuestLinkPreviewController(
                service, new DefaultResourceLoader(), "https://invi.example.com", "")).build();
        mvc.perform(get("/i/our-day")).andExpect(forwardedUrl("/index.html"));
    }

    private MockMvc mvc(String shell) {
        DefaultResourceLoader loader = new DefaultResourceLoader() {
            @Override
            public Resource getResource(String location) {
                return new ByteArrayResource(shell.getBytes(StandardCharsets.UTF_8));
            }
        };
        return MockMvcBuilders.standaloneSetup(new GuestLinkPreviewController(
                service, loader, "https://invi.example.com/", "https://media.example.com")).build();
    }
}
