package com.invi.api.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TrackedMediaObjectTest {
    private static final Instant NOW = Instant.parse("2026-10-28T00:00:00Z");
    private static final Duration GRACE = Duration.ofDays(7);
    private static final String KEY = "invitations/12345678-1234-1234-1234-123456789abc/12345678-1234-4234-8234-123456789abc.jpg";

    private TrackedMediaObject discoveredAt(Instant firstSeen) {
        return TrackedMediaObject.discovered("invi-media", KEY, firstSeen, firstSeen);
    }

    @Test
    void eligibleAtExactlySevenDaysButNotOneNanosecondBefore() {
        TrackedMediaObject object = discoveredAt(NOW);
        assertThat(object.eligible(NOW.plus(GRACE).minusNanos(1), GRACE)).isFalse();
        assertThat(object.eligible(NOW.plus(GRACE), GRACE)).isTrue();
    }

    @Test
    void safeAfterIsAnIndependentMinimumEvenAfterFullGrace() {
        Instant safeAfter = NOW.plus(GRACE).plus(Duration.ofHours(1));
        TrackedMediaObject object = TrackedMediaObject.discovered("invi-media", KEY, NOW, safeAfter);
        assertThat(object.eligible(safeAfter.minusNanos(1), GRACE)).isFalse();
        assertThat(object.eligible(safeAfter, GRACE)).isTrue();
    }

    @Test
    void objectModificationTimeRequiresItsOwnFullGrace() {
        TrackedMediaObject object = discoveredAt(NOW.minus(GRACE));
        Instant modified = NOW.plus(Duration.ofHours(2));
        object.observeObject(modified, "etag", NOW);
        assertThat(object.eligible(NOW.plus(GRACE), GRACE)).isFalse();
        assertThat(object.eligible(modified.plus(GRACE).minusNanos(1), GRACE)).isFalse();
        assertThat(object.eligible(modified.plus(GRACE), GRACE)).isTrue();
    }

    @Test
    void unknownOldObjectGetsFreshGraceFromFirstDiscovery() {
        TrackedMediaObject object = discoveredAt(NOW);
        object.observeObject(NOW.minus(Duration.ofDays(90)), "old-etag", NOW);
        assertThat(object.getUnreferencedSince()).isEqualTo(NOW);
        assertThat(object.eligible(NOW, GRACE)).isFalse();
        assertThat(object.eligible(NOW.plus(GRACE).minusNanos(1), GRACE)).isFalse();
        assertThat(object.eligible(NOW.plus(GRACE), GRACE)).isTrue();
    }

    @Test
    void firstObjectObservationAfterPresignRestartsGrace() {
        TrackedMediaObject object = discoveredAt(NOW);
        Instant uploadedAt = NOW.plus(Duration.ofHours(3));
        object.observeObject(uploadedAt, "first-put", uploadedAt);
        assertThat(object.getUnreferencedSince()).isEqualTo(uploadedAt);
        assertThat(object.eligible(NOW.plus(GRACE), GRACE)).isFalse();
        assertThat(object.eligible(uploadedAt.plus(GRACE), GRACE)).isTrue();
    }

    @Test
    void unchangedObservationsAndUnusedCallsDoNotRestartGrace() {
        TrackedMediaObject object = discoveredAt(NOW);
        object.observeObject(NOW.minusSeconds(10), "etag", NOW);
        object.observeObject(NOW.minusSeconds(10), "etag", NOW.plus(Duration.ofDays(6)));
        object.unused(NOW.plus(Duration.ofDays(6)));
        assertThat(object.getUnreferencedSince()).isEqualTo(NOW);
        assertThat(object.eligible(NOW.plus(GRACE), GRACE)).isTrue();
    }

    @Test
    void changedEtagWithSameModificationTimeRestartsGrace() {
        TrackedMediaObject object = discoveredAt(NOW);
        object.observeObject(NOW, "old", NOW);
        Instant changedAt = NOW.plus(Duration.ofDays(6));
        object.observeObject(NOW, "new", changedAt);
        assertThat(object.getUnreferencedSince()).isEqualTo(changedAt);
        assertThat(object.getObjectEtag()).isEqualTo("new");
        assertThat(object.eligible(NOW.plus(GRACE), GRACE)).isFalse();
        assertThat(object.eligible(changedAt.plus(GRACE), GRACE)).isTrue();
    }

    @Test
    void changedModificationTimeWithSameEtagRestartsGrace() {
        TrackedMediaObject object = discoveredAt(NOW);
        object.observeObject(NOW, "same", NOW);
        Instant changedAt = NOW.plus(Duration.ofDays(6));
        object.observeObject(changedAt, "same", changedAt);
        assertThat(object.getUnreferencedSince()).isEqualTo(changedAt);
        assertThat(object.getObjectModifiedAt()).isEqualTo(changedAt);
        assertThat(object.eligible(NOW.plus(GRACE), GRACE)).isFalse();
        assertThat(object.eligible(changedAt.plus(GRACE), GRACE)).isTrue();
    }

    @Test
    void referencedProtectionClearsAgeAndAnyDeletionClaim() {
        TrackedMediaObject object = discoveredAt(NOW);
        object.claim(UUID.randomUUID(), NOW);
        object.protect();
        assertThat(object.getState()).isEqualTo(MediaObjectState.ACTIVE);
        assertThat(object.getUnreferencedSince()).isNull();
        assertThat(object.getDeletionClaimId()).isNull();
        assertThat(object.getClaimExpiresAt()).isNull();
        assertThat(object.eligible(NOW.plus(Duration.ofDays(100)), GRACE)).isFalse();
    }

    @Test
    void removingReferenceStartsNewGraceInsteadOfKeepingOriginalAge() {
        TrackedMediaObject object = discoveredAt(NOW.minus(Duration.ofDays(30)));
        object.protect();
        object.dropped(NOW);
        object.unused(NOW.plus(Duration.ofDays(1)));
        assertThat(object.getUnreferencedSince()).isEqualTo(NOW);
        assertThat(object.eligible(NOW.plus(GRACE).minusNanos(1), GRACE)).isFalse();
        assertThat(object.eligible(NOW.plus(GRACE), GRACE)).isTrue();
    }

    @Test
    void lateAttachmentRestartsGraceForActiveObject() {
        TrackedMediaObject object = discoveredAt(NOW.minus(Duration.ofDays(30)));
        object.attached(NOW);
        assertThat(object.getUnreferencedSince()).isEqualTo(NOW);
        assertThat(object.eligible(NOW.plus(GRACE).minusNanos(1), GRACE)).isFalse();
        assertThat(object.eligible(NOW.plus(GRACE), GRACE)).isTrue();
    }

    @Test
    void retiredObjectsCannotBeAttached() {
        TrackedMediaObject deleting = discoveredAt(NOW);
        deleting.claim(UUID.randomUUID(), NOW);
        TrackedMediaObject deleted = discoveredAt(NOW);
        deleted.deleted(NOW);
        assertThatThrownBy(() -> deleting.attached(NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> deleted.attached(NOW)).isInstanceOf(IllegalStateException.class);
        assertThat(deleting.getState()).isEqualTo(MediaObjectState.DELETING);
        assertThat(deleted.getState()).isEqualTo(MediaObjectState.DELETED);
    }

    @Test
    void deletedObjectRemainsAnIneligibleTombstone() {
        TrackedMediaObject object = discoveredAt(NOW.minus(Duration.ofDays(30)));
        object.claim(UUID.randomUUID(), NOW);
        object.deleted(NOW);
        assertThat(object.getState()).isEqualTo(MediaObjectState.DELETED);
        assertThat(object.getDeletedAt()).isEqualTo(NOW);
        assertThat(object.getClaimExpiresAt()).isNull();
        assertThat(object.eligible(NOW.plus(Duration.ofDays(100)), GRACE)).isFalse();
    }

    @Test
    void reappearingKeyReturnsToDeletingWithFreshGrace() {
        TrackedMediaObject object = discoveredAt(NOW.minus(Duration.ofDays(30)));
        object.observeObject(NOW.minus(Duration.ofDays(30)), "old", NOW.minus(Duration.ofDays(30)));
        object.deleted(NOW.minus(Duration.ofDays(1)));
        object.observeObject(NOW, "replacement", NOW);
        assertThat(object.getState()).isEqualTo(MediaObjectState.DELETING);
        assertThat(object.getDeletedAt()).isNull();
        assertThat(object.getUnreferencedSince()).isEqualTo(NOW);
        assertThat(object.eligible(NOW.plus(GRACE).minusNanos(1), GRACE)).isFalse();
        assertThat(object.eligible(NOW.plus(GRACE), GRACE)).isTrue();
    }

    @Test
    void protectionCanRestoreReferencedTombstoneWithoutKeepingDeletedAge() {
        TrackedMediaObject object = discoveredAt(NOW.minus(GRACE));
        object.deleted(NOW);
        object.protect();
        assertThat(object.getState()).isEqualTo(MediaObjectState.ACTIVE);
        assertThat(object.getDeletedAt()).isNull();
        assertThat(object.getUnreferencedSince()).isNull();
        assertThat(object.eligible(NOW.plus(GRACE), GRACE)).isFalse();
    }

    @Test
    void deletionLeaseExpiresAtExactlyThirtyMinutes() {
        TrackedMediaObject object = discoveredAt(NOW.minus(GRACE));
        UUID claim = UUID.randomUUID();
        assertThat(object.claimAvailable(NOW)).isTrue();
        object.claim(claim, NOW);
        assertThat(object.getState()).isEqualTo(MediaObjectState.DELETING);
        assertThat(object.getClaimExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(30)));
        assertThat(object.ownsClaim(claim)).isTrue();
        assertThat(object.ownsClaim(UUID.randomUUID())).isFalse();
        assertThat(object.claimAvailable(NOW.plus(Duration.ofMinutes(30)).minusNanos(1))).isFalse();
        assertThat(object.claimAvailable(NOW.plus(Duration.ofMinutes(30)))).isTrue();
    }

    @Test
    void replacementLeaseTransfersOwnershipAndRestartsLeaseExpiry() {
        TrackedMediaObject object = discoveredAt(NOW.minus(GRACE));
        UUID oldClaim = UUID.randomUUID();
        UUID newClaim = UUID.randomUUID();
        object.claim(oldClaim, NOW);
        Instant reclaimedAt = NOW.plus(Duration.ofMinutes(30));
        object.claim(newClaim, reclaimedAt);
        assertThat(object.ownsClaim(oldClaim)).isFalse();
        assertThat(object.ownsClaim(newClaim)).isTrue();
        assertThat(object.getClaimExpiresAt()).isEqualTo(reclaimedAt.plus(Duration.ofMinutes(30)));
        assertThat(object.claimAvailable(reclaimedAt)).isFalse();
    }

    @Test
    void failedDeletionCanReleaseLeaseForRetryWithoutResettingGrace() {
        Instant firstSeen = NOW.minus(GRACE);
        TrackedMediaObject object = discoveredAt(firstSeen);
        object.claim(UUID.randomUUID(), NOW);
        Instant retryAt = NOW.plusSeconds(1);
        object.retryClaim(retryAt);
        assertThat(object.claimAvailable(retryAt)).isTrue();
        assertThat(object.getState()).isEqualTo(MediaObjectState.DELETING);
        assertThat(object.getUnreferencedSince()).isEqualTo(firstSeen);
        assertThat(object.eligible(retryAt, GRACE)).isTrue();
    }
}
