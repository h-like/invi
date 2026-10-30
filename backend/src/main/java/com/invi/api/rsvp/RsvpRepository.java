package com.invi.api.rsvp;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RsvpRepository extends JpaRepository<Rsvp, UUID> {

    List<Rsvp> findByInvitationId(UUID invitationId);

    /** Bulk delete for the free-plan expiry batch; does not load entities. */
    @Modifying(flushAutomatically = true)
    @Query("delete from Rsvp r where r.invitation.id = :invitationId")
    int deleteAllByInvitationId(@Param("invitationId") UUID invitationId);
}
