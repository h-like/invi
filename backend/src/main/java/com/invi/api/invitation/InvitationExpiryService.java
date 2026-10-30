package com.invi.api.invitation;

import com.invi.api.guestbook.GuestbookEntryRepository;
import com.invi.api.media.MediaAssetRepository;
import com.invi.api.media.MediaLifecycle;
import com.invi.api.media.MediaReferences;
import com.invi.api.rsvp.RsvpRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Physically deletes expired free invitations with their RSVPs, guestbook (including soft-deleted
 * entries) and media rows. R2 files are not deleted here: their keys are released to the media
 * lifecycle, and the R2 cleanup job removes them after its own grace period.
 */
@Service
public class InvitationExpiryService {

    /** Wedding dates are Korean calendar dates. */
    static final ZoneId WEDDING_ZONE = ZoneId.of("Asia/Seoul");

    private final InvitationRepository invitations;
    private final RsvpRepository rsvps;
    private final GuestbookEntryRepository guestbook;
    private final MediaAssetRepository mediaAssets;
    private final MediaLifecycle lifecycle;
    private final InvitationExpiryProperties properties;
    private final Clock clock;

    public InvitationExpiryService(InvitationRepository invitations, RsvpRepository rsvps,
            GuestbookEntryRepository guestbook, MediaAssetRepository mediaAssets, MediaLifecycle lifecycle,
            InvitationExpiryProperties properties, Clock clock) {
        this.invitations = invitations; this.rsvps = rsvps; this.guestbook = guestbook;
        this.mediaAssets = mediaAssets; this.lifecycle = lifecycle; this.properties = properties; this.clock = clock;
    }

    @Transactional
    public InvitationExpiryReport run() {
        // Same lock as every media-reference mutation, so an editor save cannot race the deletion.
        lifecycle.lock();
        // A wedding on 6/12 with 10 retention days is kept through 6/22 and deleted from 6/23.
        LocalDate today = LocalDate.now(clock.withZone(WEDDING_ZONE));
        LocalDate weddingDateBefore = today.minusDays(properties.retentionDays());
        List<Invitation> expired = invitations.findByPlanAndWeddingDateBeforeOrderByWeddingDateAsc(
                Plan.FREE, weddingDateBefore, Limit.of(properties.batchSize()));
        List<UUID> ids = expired.stream().map(Invitation::getId).toList();
        if (properties.dryRun()) {
            return new InvitationExpiryReport(true, weddingDateBefore, ids, 0);
        }
        for (Invitation invitation : expired) {
            delete(invitation);
        }
        return new InvitationExpiryReport(false, weddingDateBefore, ids, expired.size());
    }

    private void delete(Invitation invitation) {
        Set<String> released = new HashSet<>(MediaReferences.extract(invitation.getPageData()));
        released.addAll(MediaReferences.extract(invitation.getLangVariants()));
        released.addAll(mediaAssets.findR2KeysByInvitationId(invitation.getId()));
        lifecycle.referencesChanged(released, Set.of());
        rsvps.deleteAllByInvitationId(invitation.getId());
        guestbook.deleteAllByInvitationId(invitation.getId());
        mediaAssets.deleteAllByInvitationId(invitation.getId());
        invitations.delete(invitation);
    }
}
