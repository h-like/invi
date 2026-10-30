package com.invi.api.invitation;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvitationRepository extends JpaRepository<Invitation, UUID> {

    Optional<Invitation> findBySlug(String slug);

    boolean existsBySlug(String slug);

    List<Invitation> findAllByMemberIdOrderByCreatedAtDesc(UUID memberId);

    List<Invitation> findByPlanAndWeddingDateBeforeOrderByWeddingDateAsc(Plan plan, LocalDate date, Limit limit);
}
