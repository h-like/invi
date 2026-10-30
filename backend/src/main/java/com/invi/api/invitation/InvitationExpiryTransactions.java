package com.invi.api.invitation;

import com.invi.api.guestbook.GuestbookEntryRepository;
import com.invi.api.media.MediaAssetRepository;
import com.invi.api.media.MediaLifecycle;
import com.invi.api.media.MediaReferences;
import com.invi.api.rsvp.RsvpRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Separate bean so each invitation is deleted in its own short transaction: one failure cannot
 * roll back or block the others, and the shared media lock is held only per invitation.
 */
@Service
public class InvitationExpiryTransactions {

    private final InvitationRepository invitations;
    private final RsvpRepository rsvps;
    private final GuestbookEntryRepository guestbook;
    private final MediaAssetRepository mediaAssets;
    private final MediaLifecycle lifecycle;

    public InvitationExpiryTransactions(InvitationRepository invitations, RsvpRepository rsvps,
            GuestbookEntryRepository guestbook, MediaAssetRepository mediaAssets, MediaLifecycle lifecycle) {
        this.invitations = invitations; this.rsvps = rsvps; this.guestbook = guestbook;
        this.mediaAssets = mediaAssets; this.lifecycle = lifecycle;
    }

    @Transactional(readOnly = true)
    public List<UUID> findExpired(LocalDate weddingDateBefore, int limit) {
        return invitations.findByPlanAndWeddingDateBeforeOrderByWeddingDateAscIdAsc(
                Plan.FREE, weddingDateBefore, Limit.of(limit)).stream().map(Invitation::getId).toList();
    }

    /** Re-checks under the lock: the date may have been postponed or the plan changed since listing. */
    @Transactional
    public boolean deleteIfExpired(UUID id, LocalDate weddingDateBefore) {
        lifecycle.lock();
        Invitation invitation = invitations.findById(id).orElse(null);
        if (invitation == null || invitation.getPlan() != Plan.FREE
                || !invitation.getWeddingDate().isBefore(weddingDateBefore)) {
            return false;
        }
        Set<String> released = MediaReferences.ofInvitation(invitation);
        released.addAll(mediaAssets.findR2KeysByInvitationId(id));
        lifecycle.referencesChanged(released, Set.of());
        rsvps.deleteAllByInvitationId(id);
        guestbook.deleteAllByInvitationId(id);
        mediaAssets.deleteAllByInvitationId(id);
        invitations.delete(invitation);
        return true;
    }
}
