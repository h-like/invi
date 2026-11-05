package com.invi.api.guestbook;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GuestbookEntryRepository extends JpaRepository<GuestbookEntry, UUID> {

    /** Newest first, like the guest page shows a new entry; id breaks ties so the order is stable. */
    List<GuestbookEntry> findByInvitationIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(UUID invitationId);

    /** Bulk delete for the free-plan expiry batch; does not load entities. */
    @Modifying(flushAutomatically = true)
    @Query("delete from GuestbookEntry g where g.invitation.id = :invitationId")
    int deleteAllByInvitationId(@Param("invitationId") UUID invitationId);
}
