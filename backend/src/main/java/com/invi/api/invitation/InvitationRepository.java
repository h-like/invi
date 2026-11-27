package com.invi.api.invitation;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvitationRepository extends JpaRepository<Invitation, UUID> {

    Optional<Invitation> findBySlug(String slug);

    boolean existsBySlug(String slug);

    long countByMemberId(UUID memberId);

    Optional<Invitation> findByReviewToken(String reviewToken);

    boolean existsByReviewToken(String reviewToken);

    /**
     * A bulk update on purpose: unlike a change to the managed entity, it never bumps
     * {@code @Version}, so a review link made in one tab cannot make another tab's save stale.
     * Clears the persistence context so later reads in the transaction see the new token.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Invitation i set i.reviewToken = :token where i.id = :id")
    int updateReviewToken(@Param("id") UUID id, @Param("token") String token);

    List<Invitation> findAllByMemberIdOrderByCreatedAtDescIdDesc(UUID memberId);

    List<Invitation> findByPlanAndWeddingDateBeforeOrderByWeddingDateAscIdAsc(Plan plan, LocalDate date, Limit limit);
}
