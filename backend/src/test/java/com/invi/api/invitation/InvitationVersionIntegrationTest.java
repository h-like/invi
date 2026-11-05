package com.invi.api.invitation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.invi.api.account.AuthProvider;
import com.invi.api.account.Member;
import com.invi.api.account.MemberRepository;
import com.invi.api.common.StaleVersionException;
import com.invi.api.template.Template;
import com.invi.api.template.TemplateRepository;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The editor sends back the version from its last response, so every write must answer with
 * the version Hibernate actually stored (bumped at flush). Real PostgreSQL with the V3 column;
 * a full context because saves take the media advisory lock. Each test rolls back.
 */
@SpringBootTest(properties = "invi.invitation.expiry.enabled=false")
@Transactional
class InvitationVersionIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired private InvitationService service;
    @Autowired private InvitationRepository invitations;
    @Autowired private MemberRepository members;
    @Autowired private TemplateRepository templates;

    private UUID ownerId;
    private UUID id;

    @BeforeEach
    void setUp() {
        Member owner = members.save(Member.builder().provider(AuthProvider.KAKAO).providerId("version-" + UUID.randomUUID())
                .email("version@example.invalid").name("version").build());
        Template template = templates.save(Template.builder().code("version-" + UUID.randomUUID()).name("t").category("t").build());
        Invitation invitation = invitations.saveAndFlush(Invitation.builder().member(owner).template(template)
                .slug("version-" + UUID.randomUUID()).weddingDate(LocalDate.of(2999, 1, 1)).build());
        ownerId = owner.getId();
        id = invitation.getId();
    }

    private static JsonNode page(String heroName) {
        return JSON.readTree("{\"blocks\":[{\"id\":\"hero-1\",\"content\":{\"groomName\":\"" + heroName + "\"}}]}");
    }

    @Test
    void eachWriteReturnsTheStoredVersion_andAnOlderCopyIsRefused() {
        long loaded = service.findById(ownerId, id).version();
        assertThat(loaded).isZero();

        InvitationDto first = service.updatePageData(ownerId, id, page("A"), loaded);
        assertThat(first.version()).isEqualTo(loaded + 1);
        assertThat(invitations.findById(id).orElseThrow().getVersion()).isEqualTo(first.version());

        // A second tab still holding the loaded copy must not overwrite the first save.
        assertThatThrownBy(() -> service.updatePageData(ownerId, id, page("B"), loaded))
                .isInstanceOf(StaleVersionException.class);
        assertThat(service.findById(ownerId, id).pageData()).contains("\"A\"");

        // The same tab keeps saving with each response's version, through date and publish too.
        InvitationDto second = service.updatePageData(ownerId, id, page("C"), first.version());
        InvitationDto dated = service.updateWeddingDate(ownerId, id, LocalDate.of(2999, 2, 1));
        InvitationDto published = service.publish(ownerId, id);
        assertThat(second.version()).isEqualTo(first.version() + 1);
        assertThat(dated.version()).isEqualTo(second.version() + 1);
        assertThat(published.version()).isEqualTo(dated.version() + 1);
        assertThat(service.updatePageData(ownerId, id, page("D"), published.version()).version())
                .isEqualTo(published.version() + 1);
    }

    @Test
    void savingUnchangedContentKeepsTheVersion() {
        InvitationDto first = service.updatePageData(ownerId, id, page("A"), 0L);

        assertThat(service.updatePageData(ownerId, id, page("A"), first.version()).version()).isEqualTo(first.version());
    }
}
