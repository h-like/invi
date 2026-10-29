package com.invi.api.invitation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.util.HtmlUtils;

/**
 * Serves the SPA shell for guest links with Open Graph tags filled in, so a link shared in
 * KakaoTalk shows the couple's names, date, venue and cover. Messenger crawlers don't run
 * JavaScript, which is why this can't live in React. Drafts and unknown slugs get the plain
 * shell, exactly as before; the SPA then shows its own not-found state.
 */
@Controller
@Profile("https-test")
public class GuestLinkPreviewController {

    private static final String UTF_8 = StandardCharsets.UTF_8.name();
    private static final MediaType HTML_UTF8 = new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8);
    private static final Pattern TITLE = Pattern.compile("<title>.*?</title>", Pattern.DOTALL);
    private static final Pattern DESCRIPTION = Pattern.compile("<meta name=\"description\"[^>]*>");

    private final InvitationService invitationService;
    private final ResourceLoader resourceLoader;
    private final String publicOrigin;
    private final String imageBaseUrl;
    private volatile String shell;

    public GuestLinkPreviewController(InvitationService invitationService, ResourceLoader resourceLoader,
            @Value("${invi.frontend.base-url}") String publicOrigin,
            @Value("${invi.r2.public-base-url:}") String imageBaseUrl) {
        this.invitationService = invitationService;
        this.resourceLoader = resourceLoader;
        this.publicOrigin = publicOrigin.endsWith("/") ? publicOrigin.substring(0, publicOrigin.length() - 1) : publicOrigin;
        this.imageBaseUrl = imageBaseUrl;
    }

    @GetMapping(path = "/i/{slug}", produces = MediaType.TEXT_HTML_VALUE)
    public Object guestPage(@PathVariable String slug) {
        Optional<String> html = loadShell();
        if (html.isEmpty()) return new ModelAndView("forward:/index.html");
        String body = invitationService.findPublishedPreview(slug, imageBaseUrl)
                .map(preview -> withPreview(html.get(), preview, publicOrigin + "/i/" + slug))
                .orElse(html.get());
        // Owners edit and republish; a crawler or guest must not keep a stale preview.
        return ResponseEntity.ok().cacheControl(CacheControl.noCache()).contentType(HTML_UTF8).body(body);
    }

    static String withPreview(String shell, LinkPreview preview, String url) {
        String title = HtmlUtils.htmlEscape(preview.title(), UTF_8);
        String description = HtmlUtils.htmlEscape(preview.description(), UTF_8);
        StringBuilder tags = new StringBuilder()
                .append(meta("property", "og:type", "website"))
                .append(meta("property", "og:site_name", "invi"))
                .append(meta("property", "og:title", title))
                .append(meta("property", "og:description", description))
                .append(meta("property", "og:url", HtmlUtils.htmlEscape(url, UTF_8)));
        if (preview.imageUrl() != null) {
            tags.append(meta("property", "og:image", HtmlUtils.htmlEscape(preview.imageUrl(), UTF_8)))
                    .append(meta("property", "og:image:alt", "두 사람의 웨딩 사진"))
                    .append(meta("name", "twitter:card", "summary_large_image"));
        } else {
            tags.append(meta("name", "twitter:card", "summary"));
        }
        String html = TITLE.matcher(shell).replaceFirst(Matcher.quoteReplacement("<title>" + title + "</title>"));
        html = DESCRIPTION.matcher(html).replaceFirst(
                Matcher.quoteReplacement("<meta name=\"description\" content=\"" + description + "\" />"));
        return html.replaceFirst("</head>", Matcher.quoteReplacement(tags + "</head>"));
    }

    private static String meta(String attribute, String key, String escapedContent) {
        return "<meta " + attribute + "=\"" + key + "\" content=\"" + escapedContent + "\" />\n    ";
    }

    private Optional<String> loadShell() {
        String cached = shell;
        if (cached != null) return Optional.of(cached);
        Resource resource = resourceLoader.getResource("classpath:static/index.html");
        if (!resource.exists()) return Optional.empty();
        try (InputStream in = resource.getInputStream()) {
            shell = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return Optional.of(shell);
        } catch (IOException e) {
            return Optional.empty();
        }
    }
}
