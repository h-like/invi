package com.invi.api.media;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, UUID> {

    List<MediaAsset> findByInvitationIdOrderBySortOrderAsc(UUID invitationId);

    List<MediaAsset> findByR2Key(String r2Key);

    /** Keys only: loading MediaAsset entities before a bulk delete would leave stale managed rows. */
    @Query("select m.r2Key from MediaAsset m where m.invitation.id = :invitationId")
    List<String> findR2KeysByInvitationId(@Param("invitationId") UUID invitationId);

    /** Bulk delete for the free-plan expiry batch; does not load entities. */
    @Modifying(flushAutomatically = true)
    @Query("delete from MediaAsset m where m.invitation.id = :invitationId")
    int deleteAllByInvitationId(@Param("invitationId") UUID invitationId);
}
