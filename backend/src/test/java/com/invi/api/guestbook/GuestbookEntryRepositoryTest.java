package com.invi.api.guestbook;

import static org.assertj.core.api.Assertions.assertThat;

import com.invi.api.account.AuthProvider;
import com.invi.api.account.Member;
import com.invi.api.common.JpaAuditingConfig;
import com.invi.api.invitation.Invitation;
import com.invi.api.invitation.Plan;
import com.invi.api.template.Template;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

/**
 * Real PostgreSQL with the Flyway schema: without an ORDER BY the database may return rows in
 * any order, so the guest page and the couple's list could reshuffle between reads.
 * Each test rolls back.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
class GuestbookEntryRepositoryTest {

    @Autowired private GuestbookEntryRepository entries;
    @Autowired private EntityManager entityManager;

    private Invitation invitation;

    @BeforeEach
    void setUp() {
        Member member = Member.builder().provider(AuthProvider.KAKAO).providerId("guestbook-" + UUID.randomUUID())
                .email("guestbook@example.invalid").name("guestbook").build();
        Template template = Template.builder().code("guestbook-" + UUID.randomUUID()).name("t").category("t").build();
        entityManager.persist(member);
        entityManager.persist(template);
        invitation = Invitation.builder().member(member).template(template).slug("guestbook-" + UUID.randomUUID())
                .plan(Plan.FREE).weddingDate(LocalDate.of(2999, 1, 1)).build();
        entityManager.persist(invitation);
    }

    @Test
    void listsLiveEntriesNewestFirst_withTheIdBreakingTies() {
        GuestbookEntry oldest = save("oldest", "2026-01-01T00:00:00Z");
        GuestbookEntry newest = save("newest", "2026-03-01T00:00:00Z");
        GuestbookEntry tieA = save("tie-a", "2026-02-01T00:00:00Z");
        GuestbookEntry tieB = save("tie-b", "2026-02-01T00:00:00Z");
        GuestbookEntry deleted = save("deleted", "2026-04-01T00:00:00Z");
        entityManager.createNativeQuery("update guestbook_entry set deleted_at = now() where id = :id")
                .setParameter("id", deleted.getId()).executeUpdate();
        entityManager.clear();
        // PostgreSQL orders uuids by their bytes, i.e. by hex text (Java's compareTo is signed).
        List<UUID> ties = Stream.of(tieA.getId(), tieB.getId()).sorted(Comparator.comparing(UUID::toString).reversed()).toList();

        assertThat(entries.findByInvitationIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(invitation.getId()))
                .extracting(GuestbookEntry::getId)
                .containsExactly(newest.getId(), ties.get(0), ties.get(1), oldest.getId());
    }

    /** createdAt comes from auditing, so the test pins it afterwards to control the order. */
    private GuestbookEntry save(String author, String createdAt) {
        GuestbookEntry entry = entries.saveAndFlush(
                GuestbookEntry.builder().invitation(invitation).author(author).message("축하해요").build());
        entityManager.createNativeQuery("update guestbook_entry set created_at = cast(:at as timestamptz) where id = :id")
                .setParameter("at", createdAt).setParameter("id", entry.getId()).executeUpdate();
        return entry;
    }
}
