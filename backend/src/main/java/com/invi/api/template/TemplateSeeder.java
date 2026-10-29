package com.invi.api.template;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.invi.api.media.MediaLifecycle;
import com.invi.api.media.MediaReferences;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps the presets (기획서 3번: 초기 4종 이후 확장) present in every environment's DB
 * on each boot, keyed by {@link Template#getCode()} rather than per-DB UUIDs. Missing
 * presets are created, drifted ones are realigned, and rows no longer listed are left
 * alone because invitations reference them. Invitations copy {@code defaultPageData}
 * at creation, so realigning a preset never changes an existing invitation.
 *
 * <p>Presets come from the frontend theme registry ({@code frontend/src/editor/themes.json},
 * copied to {@value #PRESETS_RESOURCE} by the build), so a template's code and default
 * {@code design} always match a theme the editor and renderer know.
 */
@Component
@RequiredArgsConstructor
public class TemplateSeeder implements CommandLineRunner {

    static final String PRESETS_RESOURCE = "presets/themes.json";

    private final TemplateRepository templateRepository;
    private final ObjectMapper objectMapper;
    private final MediaLifecycle lifecycle;

    private record Preset(String code, String name, JsonNode design) {}

    @Override
    @Transactional
    public void run(String... args) {
        List<Preset> presets = loadPresets();
        lifecycle.lock();
        for (Preset preset : presets) {
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
                            .category(preset.code())
                            .defaultPageData(pageData)
                            .build();
            lifecycle.referencesChanged(Set.of(), referencedKeys(created));
            templateRepository.save(created);
            return;
        }
        Set<String> before = referencedKeys(existing);
        if (existing.syncPreset(preset.code(), preset.name(), preset.code(), pageData, null)) {
            lifecycle.referencesChanged(before, referencedKeys(existing));
        }
    }

    private static Set<String> referencedKeys(Template template) {
        Set<String> keys = new HashSet<>(MediaReferences.extract(template.getDefaultPageData()));
        keys.addAll(MediaReferences.extractText(template.getThumbnailUrl()));
        return keys;
    }

    /** Fails the boot on a malformed registry instead of seeding templates the UI can't render. */
    private List<Preset> loadPresets() {
        JsonNode root;
        try (InputStream in = new ClassPathResource(PRESETS_RESOURCE).getInputStream()) {
            root = objectMapper.readTree(in);
        } catch (IOException e) {
            throw new IllegalStateException("Template presets missing: " + PRESETS_RESOURCE, e);
        }
        if (!root.isArray() || root.isEmpty()) {
            throw new IllegalStateException("Template presets must be a non-empty array");
        }
        List<Preset> presets = new ArrayList<>();
        Set<String> codes = new HashSet<>();
        for (JsonNode node : root) {
            String code = node.path("code").asText("");
            String name = node.path("name").asText("");
            JsonNode design = node.path("design");
            if (code.isBlank() || name.isBlank() || !design.isObject()
                    || !code.equals(design.path("theme").asText()) || !codes.add(code)) {
                throw new IllegalStateException("Invalid template preset: " + node);
            }
            presets.add(new Preset(code, name, design));
        }
        return presets;
    }

    @SneakyThrows
    private JsonNode buildPageData(Preset preset) {
        ObjectNode pageData =
                (ObjectNode)
                        objectMapper.readTree(
                                """
                                {
                                  "blocks": [
                                    { "id": "hero-1", "type": "hero", "order": 0, "visible": true,
                                      "content": { "groomName": "", "brideName": "", "weddingDate": "" }, "style": {} },
                                    { "id": "countdown-1", "type": "countdown", "order": 1, "visible": true,
                                      "content": {}, "style": {} },
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
                                      "content": {}, "style": {} },
                                    { "id": "guestbook-1", "type": "guestbook", "order": 6, "visible": true,
                                      "content": {}, "style": {} }
                                  ]
                                }
                                """);
        // The renderer reads `design` first; block style tokens only matter for legacy pages.
        pageData.set("design", preset.design().deepCopy());
        return pageData;
    }
}
