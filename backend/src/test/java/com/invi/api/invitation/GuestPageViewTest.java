package com.invi.api.invitation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class GuestPageViewTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode json(String text) throws Exception {
        return mapper.readTree(text);
    }

    @Test
    void visibleOnly_dropsHiddenAndMalformedBlocks_keepsOtherTopLevelFields_andLeavesTheSourceUntouched()
            throws Exception {
        JsonNode page = json("""
                {"blocks":[
                  {"id":"hero-1","type":"hero","visible":true,"content":{"groomName":"하늘"}},
                  {"id":"account-1","type":"account","visible":false,"content":{"groom":{"accountNumber":"123"}}},
                  {"id":"map-1","type":"map","content":{"venueName":"정원"}},
                  {"id":"rsvp-1","type":"rsvp","visible":"true","content":{}},
                  "not-a-block"],
                 "design":{"theme":"classic"},"future":{"keep":true}}
                """);
        String before = page.toString();

        JsonNode guest = GuestPageView.visibleOnly(page);

        assertThat(guest.path("blocks")).hasSize(1);
        assertThat(guest.path("blocks").get(0).path("id").asText()).isEqualTo("hero-1");
        assertThat(guest.toString()).doesNotContain("123").doesNotContain("정원");
        assertThat(guest.path("design")).isEqualTo(page.path("design"));
        assertThat(guest.path("future")).isEqualTo(page.path("future"));
        assertThat(page.toString()).isEqualTo(before);
    }

    @Test
    void visibleOnly_passesThroughPagesWithoutABlockArray() throws Exception {
        assertThat(GuestPageView.visibleOnly(null)).isNull();
        JsonNode noBlocks = json("{\"design\":{}}");
        assertThat(GuestPageView.visibleOnly(noBlocks)).isEqualTo(noBlocks);
    }

    @Test
    void hasVisibleBlock_requiresAVisibleBlockOfThatType() throws Exception {
        JsonNode page = json("""
                {"blocks":[{"id":"rsvp-1","type":"rsvp","visible":false},
                           {"id":"guestbook-1","type":"guestbook","visible":true}]}
                """);

        assertThat(GuestPageView.hasVisibleBlock(page, "rsvp")).isFalse();
        assertThat(GuestPageView.hasVisibleBlock(page, "guestbook")).isTrue();
        assertThat(GuestPageView.hasVisibleBlock(page, "map")).isFalse();
        assertThat(GuestPageView.hasVisibleBlock(null, "rsvp")).isFalse();
    }

    @Test
    void langVariantsFor_removesHiddenBlockOverridesAtEitherLevel() throws Exception {
        JsonNode page = json("""
                {"blocks":[{"id":"hero-1","type":"hero","visible":true},
                           {"id":"account-1","type":"account","visible":false}]}
                """);
        JsonNode byBlock = json("{\"hero-1\":{\"en\":\"Hi\"},\"account-1\":{\"en\":\"secret\"}}");
        JsonNode byLanguage = json("{\"en\":{\"hero-1\":\"Hi\",\"account-1\":\"secret\"},\"meta\":1}");

        assertThat(GuestPageView.langVariantsFor(byBlock, page).toString())
                .isEqualTo("{\"hero-1\":{\"en\":\"Hi\"}}");
        assertThat(GuestPageView.langVariantsFor(byLanguage, page).toString())
                .isEqualTo("{\"en\":{\"hero-1\":\"Hi\"},\"meta\":1}");
        assertThat(byBlock.has("account-1")).isTrue();
        assertThat(GuestPageView.langVariantsFor(null, page)).isNull();
    }

    @Test
    void langVariantsFor_keepsAnIdThatAVisibleBlockAlsoUses() throws Exception {
        JsonNode page = json("""
                {"blocks":[{"id":"dup","type":"hero","visible":true},{"id":"dup","type":"map","visible":false}]}
                """);
        JsonNode variants = json("{\"dup\":{\"en\":\"Hi\"}}");

        assertThat(GuestPageView.langVariantsFor(variants, page)).isEqualTo(variants);
    }
}
