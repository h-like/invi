package com.invi.api.template;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Seeds the initial 4 templates (기획서 3번: 초기 4종) on first boot. Same block
 * layout across all four — only the style tokens differ — since Phase 1's job
 * is proving the page_data block schema out, not final template design.
 */
@Component
@RequiredArgsConstructor
public class TemplateSeeder implements CommandLineRunner {

    private final TemplateRepository templateRepository;
    private final ObjectMapper objectMapper;

    private record Preset(String name, String category, String fontFamily, String bgColor, String accentColor) {}

    private static final List<Preset> PRESETS =
            List.of(
                    new Preset("클래식", "classic", "noto-serif-kr", "#faf7f2", "#b08d57"),
                    new Preset("미니멀", "minimal", "pretendard", "#ffffff", "#1a1a1a"),
                    new Preset("로맨틱", "romantic", "noto-serif-kr", "#fdf1f4", "#c9647a"),
                    new Preset("모던", "modern", "pretendard", "#141414", "#4fd1c5"));

    @Override
    public void run(String... args) {
        if (templateRepository.count() > 0) {
            return;
        }
        for (Preset preset : PRESETS) {
            Template template =
                    Template.builder()
                            .name(preset.name())
                            .category(preset.category())
                            .defaultPageData(buildPageData(preset))
                            .build();
            templateRepository.save(template);
        }
    }

    @SneakyThrows
    private JsonNode buildPageData(Preset preset) {
        String json =
                """
                {
                  "blocks": [
                    { "id": "hero-1", "type": "hero", "order": 0, "visible": true,
                      "content": { "groomName": "", "brideName": "", "weddingDate": "" },
                      "style": { "bgColor": "%1$s", "fontFamily": "%2$s", "accentColor": "%3$s" } },
                    { "id": "countdown-1", "type": "countdown", "order": 1, "visible": true,
                      "content": {}, "style": { "accentColor": "%3$s" } },
                    { "id": "gallery-1", "type": "gallery", "order": 2, "visible": true,
                      "content": { "images": [] }, "style": {} },
                    { "id": "map-1", "type": "map", "order": 3, "visible": true,
                      "content": { "venueName": "", "address": "" }, "style": {} },
                    { "id": "account-1", "type": "account", "order": 4, "visible": true,
                      "content": { "groomAccounts": [], "brideAccounts": [] }, "style": {} },
                    { "id": "rsvp-1", "type": "rsvp", "order": 5, "visible": true,
                      "content": {}, "style": { "accentColor": "%3$s" } },
                    { "id": "guestbook-1", "type": "guestbook", "order": 6, "visible": true,
                      "content": {}, "style": {} }
                  ]
                }
                """
                        .formatted(preset.bgColor(), preset.fontFamily(), preset.accentColor());
        return objectMapper.readTree(json);
    }
}
