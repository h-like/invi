package com.invi.api.invitation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class LinkPreviewTest {

    private static final String R2 = "https://media.example.com";

    @Test
    void usesNamesDateTimeVenueAndOwnCover() throws Exception {
        LinkPreview preview = LinkPreview.of(invitation("""
                {"blocks":[
                  {"type":"hero","content":{"groomName":" 하늘 ","brideName":"다온","weddingTime":"13:30",
                    "coverImage":"https://media.example.com/invitations/1/a.webp"}},
                  {"type":"map","content":{"venueName":"초대의 정원"}}]}
                """), R2);

        assertThat(preview.title()).isEqualTo("하늘 ♥ 다온 결혼합니다");
        assertThat(preview.description()).isEqualTo("2026년 11월 1일 일요일 13:30 · 초대의 정원");
        assertThat(preview.imageUrl()).isEqualTo("https://media.example.com/invitations/1/a.webp");
    }

    @Test
    void ignoresCoversOutsideOurBucketAndLookalikeHosts() throws Exception {
        for (String cover : new String[] {"https://evil.example.com/a.webp",
                "https://media.example.com.evil.net/a.webp", "http://media.example.com/a.webp"}) {
            LinkPreview preview = LinkPreview.of(invitation(
                    "{\"blocks\":[{\"type\":\"hero\",\"content\":{\"coverImage\":\"" + cover + "\"}}]}"), R2);
            assertThat(preview.imageUrl()).as(cover).isNull();
        }
        assertThat(LinkPreview.of(invitation(
                "{\"blocks\":[{\"type\":\"hero\",\"content\":{\"coverImage\":\"https://media.example.com/a.webp\"}}]}"),
                "").imageUrl()).isNull();
    }

    @Test
    void fallsBackToGenericCopyWhenNamesOrDetailsAreMissing() throws Exception {
        LinkPreview preview = LinkPreview.of(Invitation.builder().slug("s").build(), R2);
        assertThat(preview.title()).isEqualTo(LinkPreview.DEFAULT_TITLE);
        assertThat(preview.description()).isEqualTo("소중한 분들을 저희의 결혼식에 초대합니다.");

        LinkPreview oneName = LinkPreview.of(invitation(
                "{\"blocks\":[{\"type\":\"hero\",\"content\":{\"groomName\":\"하늘\",\"weddingTime\":\"25:00\"}}]}"), R2);
        assertThat(oneName.title()).isEqualTo(LinkPreview.DEFAULT_TITLE);
        assertThat(oneName.description()).isEqualTo("2026년 11월 1일 일요일");
    }

    private static Invitation invitation(String pageData) throws Exception {
        return Invitation.builder()
                .slug("s")
                .weddingDate(LocalDate.of(2026, 11, 1))
                .pageData(new ObjectMapper().readTree(pageData))
                .build();
    }
}
