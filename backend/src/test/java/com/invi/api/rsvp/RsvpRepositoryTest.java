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
 * Runs against the real PostgreSQL (like the context test; CI provides it) with the app's
 * own ddl-auto=update, so it also proves the nullable side/meal columns are added to an
 * existing table. Each test rolls back.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=update")
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

        Rsvp loaded = rsvps.findByInvitationId(invitation.getId()).get(0);
        assertThat(loaded.getSide()).isEqualTo(RsvpSide.BRIDE);
        assertThat(loaded.getMeal()).isEqualTo(RsvpMeal.UNDECIDED);
    }

    @Test
    void loadsRowsWithoutSideOrMeal() {
        rsvps.save(Rsvp.builder().invitation(invitation).guestName("old").attending(true).guestCount(1).build());
        entityManager.flush();
        entityManager.clear();

        List<Rsvp> loaded = rsvps.findByInvitationId(invitation.getId());
        assertThat(loaded).singleElement().satisfies(r -> {
            assertThat(r.getSide()).isNull();
            assertThat(r.getMeal()).isNull();
        });
    }
}
