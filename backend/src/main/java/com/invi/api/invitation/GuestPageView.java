package com.invi.api.invitation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.HashSet;
import java.util.Set;

/**
 * What guests may see of a saved page. A block the couple hid ({@code visible} is anything but
 * {@code true}) is left out of every public response, not just skipped by the renderer, so hidden
 * account numbers or parents' names never reach a guest's browser. The stored page is untouched:
 * the editor still gets everything, and only the guest copy is filtered.
 */
public final class GuestPageView {

    private GuestPageView() {}

    /** A copy of the page without hidden blocks; every other top-level field is kept as is. */
    public static JsonNode visibleOnly(JsonNode pageData) {
        if (pageData == null || !pageData.isObject() || !pageData.path("blocks").isArray()) {
            return pageData;
        }
        ObjectNode copy = ((ObjectNode) pageData).deepCopy();
        ArrayNode visible = copy.arrayNode();
        for (JsonNode block : pageData.path("blocks")) {
            if (isVisible(block)) visible.add(block.deepCopy());
        }
        copy.set("blocks", visible);
        return copy;
    }

    /** Whether guests currently see a block of this type, i.e. whether its form may accept writes. */
    public static boolean hasVisibleBlock(JsonNode pageData, String type) {
        if (pageData == null) return false;
        for (JsonNode block : pageData.path("blocks")) {
            if (isVisible(block) && type.equals(block.path("type").asText())) return true;
        }
        return false;
    }

    /**
     * Language overrides without the hidden blocks' text. Overrides are keyed by block id, either
     * directly or under a language code, so both levels are checked. Ids of blocks that are still
     * visible, and every other key, are kept.
     */
    public static JsonNode langVariantsFor(JsonNode langVariants, JsonNode pageData) {
        if (langVariants == null || !langVariants.isObject()) return langVariants;
        Set<String> hidden = hiddenBlockIds(pageData);
        if (hidden.isEmpty()) return langVariants;
        ObjectNode copy = ((ObjectNode) langVariants).deepCopy();
        copy.remove(hidden);
        for (JsonNode value : copy) {
            if (value instanceof ObjectNode nested) nested.remove(hidden);
        }
        return copy;
    }

    /** Ids only hidden blocks use; an id a visible block shares keeps its overrides. */
    private static Set<String> hiddenBlockIds(JsonNode pageData) {
        Set<String> hidden = new HashSet<>();
        Set<String> shown = new HashSet<>();
        if (pageData == null) return hidden;
        for (JsonNode block : pageData.path("blocks")) {
            JsonNode id = block.path("id");
            if (id.isTextual()) (isVisible(block) ? shown : hidden).add(id.textValue());
        }
        hidden.removeAll(shown);
        return hidden;
    }

    /** Strict on purpose: malformed visibility hides a block rather than exposing it. */
    private static boolean isVisible(JsonNode block) {
        JsonNode visible = block.path("visible");
        return block.isObject() && visible.isBoolean() && visible.booleanValue();
    }
}
