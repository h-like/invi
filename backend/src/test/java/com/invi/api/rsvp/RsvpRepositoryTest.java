package com.invi.api.rsvp;

import static org.assertj.core.api.Assertions.assertThat;

import com.invi.api.account.AuthProvider;
import com.invi.api.account.Member;
import com.invi.api.common.JpaAuditingConfig;
import com.invi.api.invitation.Invitation;
import com.invi.api.invitation.Plan;
import com.invi.api.template.Template;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

/**
 * Runs against the real PostgreSQL (like the context test; CI provides it) with the schema
 * the Flyway migrations build. It checks that side/meal are stored as enum names and that
 * rows without them load as null. Each test rolls back.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
class RsvpRepositoryTest {

    @Autowired private RsvpRepository rsvps;
    @Autowired private EntityManager entityManager;

    private Invitation invitation;

    @BeforeEach
    void setUp() {
        Member member = Member.builder().provider(AuthProvider.KAKAO).providerId("rsvp-" + UUID.randomUUID())
                .email("rsvp@example.invalid").name("rsvp").build();
        Template template = Template.builder().code("rsvp-" + UUID.randomUUID()).name("t").category("t").build();
        entityManager.persist(member);
        entityManager.persist(template);
        invitation = Invitation.builder().member(member).template(template).slug("rsvp-" + UUID.randomUUID())
                .plan(Plan.FREE).weddingDate(LocalDate.of(2999, 1, 1)).build();
        entityManager.persist(invitation);
    }

    @Test
    void storesSideAndMealAsNames() {
        Rsvp saved = rsvps.save(Rsvp.builder().invitation(invitation).guestName("g").attending(true).guestCount(2)
                .side(RsvpSide.BRIDE).meal(RsvpMeal.UNDECIDED).build());
        entityManager.flush();
        entityManager.clear();

        Object[] row = (Object[]) entityManager
                .createNativeQuery("select side, meal from rsvp_response where id = :id")
                .setParameter("id", saved.getId())
                .getSingleResult();
        assertThat(row).containsExactly("BRIDE", "UNDECIDED");

        Rsvp loaded = rsvps.findByInvitationIdOrderByCreatedAtDescIdDesc(invitation.getId()).get(0);
        assertThat(loaded.getSide()).isEqualTo(RsvpSide.BRIDE);
        assertThat(loaded.getMeal()).isEqualTo(RsvpMeal.UNDECIDED);
    }

    @Test
    void loadsRowsWithoutSideOrMeal() {
        rsvps.save(Rsvp.builder().invitation(invitation).guestName("old").attending(true).guestCount(1).build());
        entityManager.flush();
        entityManager.clear();

        List<Rsvp> loaded = rsvps.findByInvitationIdOrderByCreatedAtDescIdDesc(invitation.getId());
        assertThat(loaded).singleElement().satisfies(r -> {
            assertThat(r.getSide()).isNull();
            assertThat(r.getMeal()).isNull();
        });
    }

    @Test
    void listsNewestFirst_withTheIdBreakingTies() {
        Rsvp oldest = save("oldest", "2026-01-01T00:00:00Z");
        Rsvp newest = save("newest", "2026-03-01T00:00:00Z");
        Rsvp tieA = save("tie-a", "2026-02-01T00:00:00Z");
        Rsvp tieB = save("tie-b", "2026-02-01T00:00:00Z");
        entityManager.clear();
        List<UUID> ties = java.util.stream.Stream.of(tieA.getId(), tieB.getId()).sorted(java.util.Comparator.comparing(UUID::toString).reversed()).toList();

        assertThat(rsvps.findByInvitationIdOrderByCreatedAtDescIdDesc(invitation.getId())).extracting(Rsvp::getId)
                .containsExactly(newest.getId(), ties.get(0), ties.get(1), oldest.getId());
    }

    /** createdAt comes from auditing, so the test pins it afterwards to control the order. */
    private Rsvp save(String name, String createdAt) {
        Rsvp rsvp = rsvps.saveAndFlush(Rsvp.builder().invitation(invitation).guestName(name).attending(true).guestCount(1).build());
        entityManager.createNativeQuery("update rsvp_response set created_at = cast(:at as timestamptz) where id = :id")
                .setParameter("at", createdAt).setParameter("id", rsvp.getId()).executeUpdate();
        return rsvp;
    }
}
