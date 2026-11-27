package com.invi.api.invitation;

import static org.assertj.core.api.Assertions.assertThat;

import com.invi.api.account.AuthProvider;
import com.invi.api.account.Member;
import com.invi.api.common.JpaAuditingConfig;
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

/** Runs against the real PostgreSQL with the Flyway schema. Each test rolls back. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
class InvitationRepositoryTest {

    @Autowired private InvitationRepository invitations;
    @Autowired private EntityManager entityManager;

    private Member member;
    private Template template;

    @BeforeEach
    void setUp() {
        member = Member.builder().provider(AuthProvider.KAKAO).providerId("inv-" + UUID.randomUUID())
                .email("inv@example.invalid").name("inv").build();
        template = Template.builder().code("inv-" + UUID.randomUUID()).name("t").category("t").build();
        entityManager.persist(member);
        entityManager.persist(template);
    }

    @Test
    void listsAMembersInvitationsNewestFirst_withTheIdBreakingTies() {
        Invitation oldest = save("2026-01-01T00:00:00Z");
        Invitation newest = save("2026-03-01T00:00:00Z");
        // Five ties: without the id tie-break an arbitrary order would match only 1 time in 120.
        List<UUID> ties = Stream.generate(() -> save("2026-02-01T00:00:00Z").getId()).limit(5)
                .sorted(Comparator.comparing(UUID::toString).reversed()).toList();
        entityManager.clear();
        List<UUID> expected = Stream.of(Stream.of(newest.getId()), ties.stream(), Stream.of(oldest.getId()))
                .flatMap(s -> s).toList();

        assertThat(invitations.findAllByMemberIdOrderByCreatedAtDescIdDesc(member.getId()))
                .extracting(Invitation::getId)
                .containsExactlyElementsOf(expected);
    }

    /** createdAt comes from auditing, so the test pins it afterwards to control the order. */
    private Invitation save(String createdAt) {
        Invitation invitation = invitations.saveAndFlush(Invitation.builder().member(member).template(template)
                .slug("inv-" + UUID.randomUUID()).plan(Plan.FREE).weddingDate(LocalDate.of(2999, 1, 1)).build());
        entityManager.createNativeQuery("update invitation set created_at = cast(:at as timestamptz) where id = :id")
                .setParameter("at", createdAt).setParameter("id", invitation.getId()).executeUpdate();
        return invitation;
    }
}
