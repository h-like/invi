package com.invi.api.template;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.invi.api.media.MediaLifecycle;
import com.invi.api.media.MediaReferences;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps the code-defined presets (기획서 3번: 초기 4종 이후 확장) present in every
 * environment's DB on each boot, keyed by {@link Template#getCode()} rather than
 * per-DB UUIDs. Missing presets are created, drifted ones are realigned, and rows
 * the code no longer lists are left alone because invitations reference them.
 * Invitations copy {@code defaultPageData} at creation, so realigning a preset
 * never changes an existing invitation.
 */
@Component
@RequiredArgsConstructor
public class TemplateSeeder implements CommandLineRunner {

    private final TemplateRepository templateRepository;
    private final ObjectMapper objectMapper;
    private final MediaLifecycle lifecycle;

    private record Preset(String code, String name, String category, String fontFamily, String bgColor, String accentColor) {}

    private static final List<Preset> PRESETS =
            List.of(
                    new Preset("classic", "클래식", "classic", "noto-serif-kr", "#faf7f2", "#b08d57"),
                    new Preset("minimal", "미니멀", "minimal", "pretendard", "#ffffff", "#1a1a1a"),
                    new Preset("romantic", "로맨틱", "romantic", "noto-serif-kr", "#fdf1f4", "#c9647a"),
                    new Preset("modern", "모던", "modern", "pretendard", "#141414", "#4fd1c5"));

    @Override
    @Transactional
    public void run(String... args) {
        lifecycle.lock();
        for (Preset preset : PRESETS) {
            sync(preset);
        }
    }

    private void sync(Preset preset) {
        JsonNode pageData = buildPageData(preset);
        // Rows seeded before `code` existed used the code as their category.
        Template existing =
                templateRepository
                        .findByCode(preset.code())
                        .or(() -> templateRepository.findFirstByCodeIsNullAndCategoryOrderByCreatedAtAsc(preset.code()))
                        .orElse(null);
        if (existing == null) {
            Template created =
                    Template.builder()
                            .code(preset.code())
                            .name(preset.name())
                            .category(preset.category())
                            .defaultPageData(pageData)
                            .build();
            lifecycle.referencesChanged(Set.of(), referencedKeys(created));
            templateRepository.save(created);
            return;
        }
        Set<String> before = referencedKeys(existing);
        if (existing.syncPreset(preset.code(), preset.name(), preset.category(), pageData, null)) {
            lifecycle.referencesChanged(before, referencedKeys(existing));
        }
    }

    private static Set<String> referencedKeys(Template template) {
        Set<String> keys = new HashSet<>(MediaReferences.extract(template.getDefaultPageData()));
        keys.addAll(MediaReferences.extractText(template.getThumbnailUrl()));
        return keys;
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
                      "content": {
                        "groom": { "holder": "", "bank": "", "accountNumber": "", "message": "" },
                        "groomFather": { "holder": "", "bank": "", "accountNumber": "", "message": "" },
                        "groomMother": { "holder": "", "bank": "", "accountNumber": "", "message": "" },
                        "bride": { "holder": "", "bank": "", "accountNumber": "", "message": "" },
                        "brideFather": { "holder": "", "bank": "", "accountNumber": "", "message": "" },
                        "brideMother": { "holder": "", "bank": "", "accountNumber": "", "message": "" }
                      }, "style": {} },
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
