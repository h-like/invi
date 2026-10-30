package com.invi.api.invitation;

import static org.assertj.core.api.Assertions.assertThat;

import com.invi.api.account.AuthProvider;
import com.invi.api.account.Member;
import com.invi.api.account.MemberRepository;
import com.invi.api.guestbook.GuestbookEntry;
import com.invi.api.guestbook.GuestbookEntryRepository;
import com.invi.api.media.MediaAsset;
import com.invi.api.media.MediaAssetRepository;
import com.invi.api.media.MediaType;
import com.invi.api.rsvp.Rsvp;
import com.invi.api.rsvp.RsvpRepository;
import com.invi.api.template.Template;
import com.invi.api.template.TemplateRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs the bulk JPQL deletes and the PostgreSQL advisory lock against the real database
 * (same requirement as the context test; CI provides PostgreSQL). Each test rolls back.
 * A full context instead of @DataJpaTest because the lock and media lifecycle need it.
 */
@SpringBootTest(properties = "invi.invitation.expiry.enabled=false")
@Transactional
class InvitationExpiryTransactionsIntegrationTest {

    private static final LocalDate LONG_AGO = LocalDate.of(2000, 1, 1);
    private static final LocalDate CUTOFF = LocalDate.of(2000, 1, 2);

    @Autowired private InvitationExpiryTransactions transactions;
    @Autowired private InvitationRepository invitations;
    @Autowired private RsvpRepository rsvps;
    @Autowired private GuestbookEntryRepository guestbook;
    @Autowired private MediaAssetRepository mediaAssets;
    @Autowired private MemberRepository members;
    @Autowired private TemplateRepository templates;
    @Autowired private EntityManager entityManager;

    private Member member;
    private Template template;

    @BeforeEach
    void setUp() {
        member = members.save(Member.builder().provider(AuthProvider.KAKAO).providerId("expiry-" + UUID.randomUUID())
                .email("expiry@example.invalid").name("expiry").build());
        template = templates.save(Template.builder().code("expiry-" + UUID.randomUUID()).name("t").category("t").build());
    }

    private Invitation invitation(Plan plan, LocalDate weddingDate) {
        return invitations.save(Invitation.builder().member(member).template(template)
                .slug("expiry-" + UUID.randomUUID()).plan(plan).weddingDate(weddingDate).build());
    }

    @Test
    void deletesExpiredFreeInvitationWithAllChildren() {
        Invitation expired = invitation(Plan.FREE, LONG_AGO);
        Invitation other = invitation(Plan.FREE, LocalDate.of(2999, 1, 1));
        rsvps.save(Rsvp.builder().invitation(expired).guestName("g").attending(true).guestCount(1).build());
        rsvps.save(Rsvp.builder().invitation(other).guestName("g").attending(true).guestCount(1).build());
        guestbook.save(GuestbookEntry.builder().invitation(expired).author("a").message("m").build()).softDelete();
        mediaAssets.save(MediaAsset.builder().invitation(expired).type(MediaType.GALLERY)
                .r2Key("invitations/" + expired.getId() + "/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa.jpg").build());
        entityManager.flush();
        entityManager.clear();

        assertThat(transactions.deleteIfExpired(expired.getId(), CUTOFF)).isTrue();
        entityManager.flush();
        entityManager.clear();

        assertThat(invitations.findById(expired.getId())).isEmpty();
        assertThat(rsvps.findByInvitationId(expired.getId())).isEmpty();
        assertThat(mediaAssets.findR2KeysByInvitationId(expired.getId())).isEmpty();
        assertThat(guestbook.findAll()).noneMatch(entry -> entry.getInvitation().getId().equals(expired.getId()));
        assertThat(invitations.findById(other.getId())).isPresent();
        assertThat(rsvps.findByInvitationId(other.getId())).hasSize(1);
    }

    @Test
    void listsOnlyExpiredFreeInvitations() {
        Invitation expired = invitation(Plan.FREE, LONG_AGO);
        Invitation paid = invitation(Plan.PAID, LONG_AGO);
        Invitation onCutoff = invitation(Plan.FREE, CUTOFF);

        assertThat(transactions.findExpired(CUTOFF, 1000))
                .contains(expired.getId()).doesNotContain(paid.getId(), onCutoff.getId());
    }

    @Test
    void postponedOrUpgradedInvitationIsRecheckedAndKept() {
        Invitation postponed = invitation(Plan.FREE, LONG_AGO);
        Invitation paid = invitation(Plan.PAID, LONG_AGO);
        postponed.updateWeddingDate(LocalDate.of(2999, 1, 1));
        entityManager.flush();
        entityManager.clear();

        assertThat(transactions.deleteIfExpired(postponed.getId(), CUTOFF)).isFalse();
        assertThat(transactions.deleteIfExpired(paid.getId(), CUTOFF)).isFalse();
        assertThat(invitations.findById(postponed.getId())).isPresent();
        assertThat(invitations.findById(paid.getId())).isPresent();
    }
}
