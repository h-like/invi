package com.invi.api.invitation;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * What a messenger (KakaoTalk, iMessage...) shows for a shared guest link. Messengers read
 * Open Graph tags from the raw HTML and never run the SPA, so the server fills these in.
 * Only fields already visible on the published page are used.
 */
public record LinkPreview(String title, String description, String imageUrl) {

    static final String DEFAULT_TITLE = "invi | 마음을 담은 초대";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy년 M월 d일 EEEE", Locale.KOREAN);

    /**
     * @param imageBaseUrl the R2 public base URL; the cover is only offered when it is one of
     *     our own uploads, so a page can't make the preview point at an arbitrary host.
     */
    static LinkPreview of(Invitation invitation, String imageBaseUrl) {
        JsonNode hero = block(invitation.getPageData(), "hero");
        JsonNode map = block(invitation.getPageData(), "map");
        String groom = text(hero, "groomName");
        String bride = text(hero, "brideName");
        String title = groom.isEmpty() || bride.isEmpty() ? DEFAULT_TITLE : groom + " ♥ " + bride + " 결혼합니다";

        StringBuilder description = new StringBuilder();
        LocalDate date = invitation.getWeddingDate();
        if (date != null) description.append(DATE.format(date));
        String time = text(hero, "weddingTime");
        if (time.matches("([01]\\d|2[0-3]):[0-5]\\d")) description.append(description.isEmpty() ? "" : " ").append(time);
        String venue = text(map, "venueName");
        if (!venue.isEmpty()) description.append(description.isEmpty() ? "" : " · ").append(venue);
        if (description.isEmpty()) description.append("소중한 분들을 저희의 결혼식에 초대합니다.");

        String cover = text(hero, "coverImage");
        boolean ownImage = imageBaseUrl != null && !imageBaseUrl.isBlank() && cover.startsWith("https://")
                && cover.startsWith(imageBaseUrl.endsWith("/") ? imageBaseUrl : imageBaseUrl + "/");
        return new LinkPreview(title, description.toString(), ownImage ? cover : null);
    }

    private static JsonNode block(JsonNode pageData, String type) {
        if (pageData == null) return null;
        for (JsonNode block : pageData.path("blocks")) {
            if (type.equals(block.path("type").asText())) return block.path("content");
        }
        return null;
    }

    private static String text(JsonNode content, String key) {
        if (content == null) return "";
        JsonNode value = content.get(key);
        return value != null && value.isTextual() ? value.asText().strip() : "";
    }
}
