package com.invi.api.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class MediaCleanupTransactionsTest {
    private static final String BUCKET = "cleanup-test-bucket";
    private static final String PREFIX = "invitations/";
    private static final String INVITATION = "12345678-1234-1234-1234-123456789abc";
    private static final String KEY = PREFIX + INVITATION + "/12345678-1234-4234-8234-123456789abc.jpg";
    private static final String OTHER = KEY.replace(".jpg", ".png");
    private static final String THIRD = KEY.replace(".jpg", ".webp");
    private static final String FOREIGN = KEY.replace(INVITATION, "abcdefab-1234-1234-1234-123456789abc");
    private static final Instant NOW = Instant.parse("2026-10-28T00:00:00Z");
    private static final Instant OLD = NOW.minus(Duration.ofDays(30));
    private static final Duration GRACE = Duration.ofDays(7);
    private static final Duration LEASE = Duration.ofMinutes(30);

    @Mock private MediaMutationGuard guard;
    @Mock private TrackedMediaObjectRepository objects;
    @Mock private MediaAssetRepository assets;
    @Mock private MediaReferences references;
    @Captor private ArgumentCaptor<List<TrackedMediaObject>> discoveries;
    private AdjustableClock clock;
    private MediaCleanupProperties properties;
    private MediaCleanupTransactions transactions;
    private InOrder order;

    @BeforeEach
    void setUp() {
        clock = new AdjustableClock(NOW);
        properties = new MediaCleanupProperties(true, false, GRACE, 10000, 100);
        transactions = worker(properties);
        order = inOrder(guard, objects, assets, references);
    }

    @AfterEach
    void noUnexpectedCollaboratorWork() {
        verifyNoMoreInteractions(guard, objects, assets, references);
    }

    @Test
    void firstDiscoveryOfOldR2ObjectStartsFreshSevenDayGrace() {
        List<TrackedMediaObject> rows = new ArrayList<>();
        stubObservation(rows, List.of());
        when(references.allKeys()).thenReturn(Set.of());
        var inventory = List.of(head(KEY));
        assertThat(transactions.observe(inventory, PREFIX))
                .isEqualTo(new MediaCleanupTransactions.Plan(1, 0, 1, 0, 0, List.of()));
        var saved = verifyObservation(PREFIX);
        assertThat(saved).hasSize(1);
        var object = saved.get(0);
        assertThat(object.getBucket()).isEqualTo(BUCKET);
        assertThat(object.getR2Key()).isEqualTo(KEY);
        assertThat(object.getUnreferencedSince()).isEqualTo(NOW);
        assertThat(object.getSafeAfter()).isEqualTo(NOW.plus(Duration.ofHours(1)));
        assertThat(object.getObjectModifiedAt()).isEqualTo(OLD);
        rows.add(object);
        clock.at(NOW.plus(GRACE).minusNanos(1));
        assertThat(transactions.observe(inventory, PREFIX).candidates()).isEmpty();
        assertThat(verifyObservation(PREFIX)).isEmpty();
        clock.at(NOW.plus(GRACE));
        assertThat(transactions.observe(inventory, PREFIX).candidates()).containsExactly(KEY);
        assertThat(verifyObservation(PREFIX)).isEmpty();
        assertThat(object.getUnreferencedSince()).isEqualTo(NOW);
    }

    @Test
    void legacyAssetWithMissingObjectIsTrackedFreshAndDeduplicated() {
        List<TrackedMediaObject> rows = new ArrayList<>();
        stubObservation(rows, List.of(asset(KEY), asset(KEY)));
        when(references.allKeys()).thenReturn(Set.of());
        assertThat(transactions.observe(List.of(), PREFIX))
                .isEqualTo(new MediaCleanupTransactions.Plan(1, 0, 1, 0, 0, List.of()));
        var saved = verifyObservation(PREFIX);
        assertThat(saved).hasSize(1);
        var object = saved.get(0);
        assertThat(object.getR2Key()).isEqualTo(KEY);
        assertThat(object.getUnreferencedSince()).isEqualTo(NOW);
        assertThat(object.getSafeAfter()).isEqualTo(NOW.plus(Duration.ofHours(1)));
        assertThat(object.getObjectModifiedAt()).isNull();
        assertThat(object.getObjectEtag()).isNull();
        rows.add(object);
        clock.at(NOW.plus(GRACE));
        assertThat(transactions.observe(List.of(), PREFIX).candidates()).containsExactly(KEY);
        assertThat(verifyObservation(PREFIX)).isEmpty();
    }

    @Test
    void unuploadedReceiptAgesIntoEligibilityAndClaimsAbsentObject() {
        var object = receipt(KEY, NOW.minus(GRACE));
        stubObservation(List.of(object), List.of());
        when(objects.findByBucketAndR2Key(BUCKET, KEY)).thenReturn(Optional.of(object));
        when(references.allKeys()).thenReturn(Set.of());
        clock.at(NOW.minusNanos(1));
        assertThat(transactions.observe(List.of(), PREFIX).candidates()).isEmpty();
        verifyObservation(PREFIX);
        clock.at(NOW);
        assertThat(transactions.observe(List.of(), PREFIX).candidates()).containsExactly(KEY);
        verifyObservation(PREFIX);
        var claim = transactions.claim(KEY, null);
        verifyLookup(KEY, true);
        assertThat(claim).isNotNull();
        assertThat(claim.key()).isEqualTo(KEY);
        assertThat(claim.exists()).isFalse();
        assertThat(object.getState()).isEqualTo(MediaObjectState.DELETING);
        assertThat(object.getDeletionClaimId()).isEqualTo(claim.token());
        assertThat(object.getClaimExpiresAt()).isEqualTo(NOW.plus(LEASE));
    }

    @Test
    void safeAfterIndependentlyBlocksOtherwiseOldReceipt() {
        var object = TrackedMediaObject.discovered(BUCKET, KEY, OLD, NOW.plusSeconds(1));
        stubLookup(object);
        when(references.allKeys()).thenReturn(Set.of());
        assertThat(transactions.claim(KEY, null)).isNull();
        verifyLookup(KEY, true);
        clock.at(NOW.plusSeconds(1));
        assertThat(transactions.claim(KEY, null)).isNotNull();
        verifyLookup(KEY, true);
    }

    @Test
    void anyGlobalReferenceProtectsActiveAndDeletingKeysWithoutAssetRows() {
        var active = stable(KEY, OLD);
        var deleting = stable(OTHER, OLD);
        deleting.claim(UUID.randomUUID(), NOW.minusSeconds(1));
        stubObservation(List.of(active, deleting), List.of());
        when(references.allKeys()).thenReturn(Set.of(KEY, OTHER, FOREIGN));
        assertThat(transactions.observe(List.of(head(KEY), head(OTHER)), PREFIX))
                .isEqualTo(new MediaCleanupTransactions.Plan(2, 2, 0, 0, 0, List.of()));
        verifyObservation(PREFIX);
        for (var object : List.of(active, deleting)) {
            assertThat(object.getState()).isEqualTo(MediaObjectState.ACTIVE);
            assertThat(object.getUnreferencedSince()).isNull();
            assertThat(object.getDeletionClaimId()).isNull();
            assertThat(object.getClaimExpiresAt()).isNull();
        }
    }

    @Test
    void droppingReferenceRestartsGraceDespiteOldR2Modification() {
        var object = stable(KEY, OLD);
        object.protect();
        object.dropped(NOW);
        stubObservation(List.of(object), List.of());
        when(references.allKeys()).thenReturn(Set.of());
        clock.at(NOW.plus(GRACE).minusNanos(1));
        assertThat(transactions.observe(List.of(head(KEY)), PREFIX).candidates()).isEmpty();
        verifyObservation(PREFIX);
        clock.at(NOW.plus(GRACE));
        assertThat(transactions.observe(List.of(head(KEY)), PREFIX).candidates()).containsExactly(KEY);
        verifyObservation(PREFIX);
        assertThat(object.getUnreferencedSince()).isEqualTo(NOW);
    }

    @Test
    void rescanNoticingRemovedReferenceStartsANewFullGrace() {
        var object = stable(KEY, OLD);
        stubObservation(List.of(object), List.of());
        when(references.allKeys()).thenReturn(Set.of(KEY), Set.of());
        assertThat(transactions.observe(List.of(head(KEY)), PREFIX).referenced()).isEqualTo(1);
        verifyObservation(PREFIX);
        assertThat(object.getUnreferencedSince()).isNull();
        Instant removedAt = NOW.plus(Duration.ofDays(3));
        clock.at(removedAt);
        assertThat(transactions.observe(List.of(head(KEY)), PREFIX).candidates()).isEmpty();
        verifyObservation(PREFIX);
        assertThat(object.getUnreferencedSince()).isEqualTo(removedAt);
        clock.at(removedAt.plus(GRACE).minusNanos(1));
        assertThat(transactions.observe(List.of(head(KEY)), PREFIX).candidates()).isEmpty();
        verifyObservation(PREFIX);
        clock.at(removedAt.plus(GRACE));
        assertThat(transactions.observe(List.of(head(KEY)), PREFIX).candidates()).containsExactly(KEY);
        verifyObservation(PREFIX);
    }

    @Test
    void unmanagedAndForeignPrefixKeysAreNeverTrackedOrMutated() {
        String prefix = PREFIX + INVITATION + "/";
        String unmanaged = KEY.replace(".jpg", ".gif");
        var ignored = List.of(receipt(FOREIGN, OLD), receipt(unmanaged, OLD));
        when(objects.findByBucketAndR2KeyStartingWith(BUCKET, prefix)).thenReturn(ignored);
        when(assets.findAll()).thenReturn(List.of(asset(FOREIGN), asset(unmanaged), asset(null)));
        when(references.allKeys()).thenReturn(Set.of());
        var inventory = List.of(head(FOREIGN), head(unmanaged),
                new MediaCleanupTransactions.Observation(null, OLD, "etag"));
        assertThat(transactions.observe(inventory, prefix))
                .isEqualTo(new MediaCleanupTransactions.Plan(0, 0, 0, 0, 0, List.of()));
        assertThat(verifyObservation(prefix)).isEmpty();
        for (var object : ignored) {
            assertThat(object.getUnreferencedSince()).isEqualTo(OLD);
            assertThat(object.getState()).isEqualTo(MediaObjectState.ACTIVE);
            assertThat(object.getObjectModifiedAt()).isNull();
        }
    }

    @Test
    void stableOldObjectsUseAgeThenKeyOrderingAndConfiguredBatchLimit() {
        transactions = worker(new MediaCleanupProperties(true, false, GRACE, 10000, 2));
        var oldest = stable(FOREIGN, OLD.minusSeconds(1));
        var firstTie = stable(KEY, OLD);
        var secondTie = stable(OTHER, OLD);
        var thirdTie = stable(THIRD, OLD);
        var young = receipt(KEY.replace("8234", "9234"), NOW);
        var tombstone = receipt(KEY.replace("8234", "a234"), OLD);
        tombstone.deleted(NOW.minusSeconds(1));
        stubObservation(List.of(thirdTie, young, secondTie, tombstone, firstTie, oldest), List.of());
        when(references.allKeys()).thenReturn(Set.of());
        assertThat(transactions.observe(List.of(head(THIRD), head(OTHER), head(KEY), head(FOREIGN)), PREFIX))
                .isEqualTo(new MediaCleanupTransactions.Plan(6, 0, 1, 4, 0, List.of(FOREIGN, KEY)));
        assertThat(verifyObservation(PREFIX)).isEmpty();
        assertThat(firstTie.getUnreferencedSince()).isEqualTo(OLD);
        assertThat(oldest.getUnreferencedSince()).isEqualTo(OLD.minusSeconds(1));
        assertThat(tombstone.getState()).isEqualTo(MediaObjectState.DELETED);
    }

    @Test
    void lateSaveAfterPlanningIsRecheckedAndProtectedAtClaim() {
        var object = stable(KEY, OLD);
        stubObservation(List.of(object), List.of());
        stubLookup(object);
        when(references.allKeys()).thenReturn(Set.of(), Set.of(KEY));
        assertThat(transactions.observe(List.of(head(KEY)), PREFIX).candidates()).containsExactly(KEY);
        verifyObservation(PREFIX);
        assertThat(transactions.claim(KEY, head(KEY))).isNull();
        verifyLookup(KEY, true);
        assertThat(object.getState()).isEqualTo(MediaObjectState.ACTIVE);
        assertThat(object.getUnreferencedSince()).isNull();
        assertThat(object.getDeletionClaimId()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"etag", "modified", "both"})
    void changedHeadRestartsFullGraceAndSkipsClaim(String change) {
        var object = stable(KEY, OLD);
        stubLookup(object);
        when(references.allKeys()).thenReturn(Set.of());
        Instant modified = change.equals("etag") ? OLD : OLD.plusSeconds(1);
        String etag = change.equals("modified") ? "etag" : "replacement";
        var changed = new MediaCleanupTransactions.Observation(KEY, modified, etag);
        assertThat(transactions.claim(KEY, changed)).isNull();
        verifyLookup(KEY, true);
        assertThat(object.getUnreferencedSince()).isEqualTo(NOW);
        assertThat(object.getObjectModifiedAt()).isEqualTo(modified);
        assertThat(object.getObjectEtag()).isEqualTo(etag);
        assertThat(object.getState()).isEqualTo(MediaObjectState.ACTIVE);
        clock.at(NOW.plus(GRACE).minusNanos(1));
        assertThat(transactions.claim(KEY, changed)).isNull();
        verifyLookup(KEY, true);
        clock.at(NOW.plus(GRACE));
        var claim = transactions.claim(KEY, changed);
        verifyLookup(KEY, true);
        assertThat(claim).isNotNull();
        assertThat(claim.exists()).isTrue();
    }

    @Test
    void listAndHeadPrecisionDifferencesDoNotRestartGrace() {
        var object = receipt(KEY, OLD);
        var listed = new MediaCleanupTransactions.Observation(KEY, OLD.plusNanos(999999999), "etag");
        object.observeObject(listed.modifiedAt(), listed.etag(), OLD);
        stubLookup(object);
        when(references.allKeys()).thenReturn(Set.of());
        assertThat(transactions.claim(KEY, head(KEY))).isNotNull();
        verifyLookup(KEY, true);
        assertThat(object.getUnreferencedSince()).isEqualTo(OLD);
    }

    @Test
    void absentTrackedRowSkipsClaimWithoutReferenceScan() {
        when(objects.findByBucketAndR2Key(BUCKET, KEY)).thenReturn(Optional.empty());
        assertThat(transactions.claim(KEY, head(KEY))).isNull();
        verifyLookup(KEY, false);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"photo.jpg", "../photo.jpg", "invitations/not-a-uuid/photo.jpg", "https://example.com/photo.jpg"})
    void unmanagedClaimSkipsBeforeRepositoryRead(String key) {
        assertThat(transactions.claim(key, null)).isNull();
        order.verify(guard).lock();
    }

    @ParameterizedTest
    @MethodSource("unsafeHeads")
    void unsafeHeadSkipsWithoutMutatingObject(MediaCleanupTransactions.Observation unsafe) {
        var object = stable(KEY, OLD);
        stubLookup(object);
        when(references.allKeys()).thenReturn(Set.of());
        assertThat(transactions.claim(KEY, unsafe)).isNull();
        verifyLookup(KEY, true);
        assertThat(object.getUnreferencedSince()).isEqualTo(OLD);
        assertThat(object.getObjectModifiedAt()).isEqualTo(OLD);
        assertThat(object.getObjectEtag()).isEqualTo("etag");
        assertThat(object.getState()).isEqualTo(MediaObjectState.ACTIVE);
        assertThat(object.getDeletionClaimId()).isNull();
    }

    static Stream<MediaCleanupTransactions.Observation> unsafeHeads() {
        return Stream.of(new MediaCleanupTransactions.Observation(OTHER, OLD, "etag"),
                new MediaCleanupTransactions.Observation(null, OLD, "etag"),
                new MediaCleanupTransactions.Observation(KEY, null, "etag"),
                new MediaCleanupTransactions.Observation(KEY, OLD, null),
                new MediaCleanupTransactions.Observation(KEY, OLD, ""),
                new MediaCleanupTransactions.Observation(KEY, OLD, "  "));
    }

    @Test
    void deletingLeaseBlocksOtherWorkerUntilExactlyThirtyMinutes() {
        var object = stable(KEY, OLD);
        stubObservation(List.of(object), List.of());
        stubLookup(object);
        when(references.allKeys()).thenReturn(Set.of());
        var otherWorker = worker(properties);
        var first = transactions.claim(KEY, head(KEY));
        verifyLookup(KEY, true);
        assertThat(first).isNotNull();
        clock.at(NOW.plus(LEASE).minusNanos(1));
        assertThat(otherWorker.observe(List.of(head(KEY)), PREFIX))
                .isEqualTo(new MediaCleanupTransactions.Plan(1, 0, 0, 1, 1, List.of()));
        verifyObservation(PREFIX);
        assertThat(otherWorker.claim(KEY, head(KEY))).isNull();
        verifyLookup(KEY, true);
        assertThat(object.getDeletionClaimId()).isEqualTo(first.token());
        assertThat(object.getClaimExpiresAt()).isEqualTo(NOW.plus(LEASE));
        clock.at(NOW.plus(LEASE));
        assertThat(otherWorker.observe(List.of(head(KEY)), PREFIX).candidates()).containsExactly(KEY);
        verifyObservation(PREFIX);
        var replacement = otherWorker.claim(KEY, head(KEY));
        verifyLookup(KEY, true);
        assertThat(replacement).isNotNull();
        assertThat(replacement.token()).isNotEqualTo(first.token());
        assertThat(object.getDeletionClaimId()).isEqualTo(replacement.token());
        assertThat(object.getClaimExpiresAt()).isEqualTo(NOW.plus(LEASE).plus(LEASE));
    }

    @Test
    void finishIsPostStorageBoundaryDeletingOnlyMatchingAssetsThenMarkingTombstone() {
        var object = stable(KEY, OLD);
        stubLookup(object);
        when(references.allKeys()).thenReturn(Set.of());
        var claim = transactions.claim(KEY, head(KEY));
        verifyLookup(KEY, true);
        assertThat(claim).isNotNull();
        assertThat(claim.exists()).isTrue();
        assertThat(object.getState()).isEqualTo(MediaObjectState.DELETING);
        assertThat(object.getDeletedAt()).isNull();
        // Claim cannot delete DB assets. Real R2 I/O/order belongs to service-level tests.
        verifyNoMoreInteractions(assets);
        List<MediaAsset> matchingRows = List.of(asset(KEY), asset(KEY));
        when(assets.findByR2Key(KEY)).thenReturn(matchingRows);
        org.mockito.Mockito.doAnswer(invocation -> {
            assertThat(object.getState()).isEqualTo(MediaObjectState.DELETING);
            assertThat(object.getDeletedAt()).isNull();
            return null;
        }).when(assets).deleteAll(matchingRows);
        // Simulate caller reaching finish after the R2 stage succeeds.
        clock.at(NOW.plusSeconds(2));
        assertThat(transactions.finish(claim)).isTrue();
        verifyFinish(KEY, matchingRows);
        assertThat(object.getState()).isEqualTo(MediaObjectState.DELETED);
        assertThat(object.getDeletedAt()).isEqualTo(clock.instant());
        assertThat(object.getClaimExpiresAt()).isNull();
        assertThat(object.eligible(NOW.plus(Duration.ofDays(100)), GRACE)).isFalse();
    }

    @Test
    void finishForMissingRowIsNoOp() {
        when(objects.findByBucketAndR2Key(BUCKET, KEY)).thenReturn(Optional.empty());
        assertThat(transactions.finish(new MediaCleanupTransactions.Claim(KEY, UUID.randomUUID(), true))).isFalse();
        verifyLookup(KEY, false);
    }

    @ParameterizedTest
    @EnumSource(value = MediaObjectState.class, names = {"ACTIVE", "DELETED"})
    void finishRequiresDeletingState(MediaObjectState state) {
        var object = receipt(KEY, OLD);
        UUID token = UUID.randomUUID();
        if (state == MediaObjectState.DELETED) {
            object.claim(token, NOW.minusSeconds(1));
            object.deleted(NOW.minusSeconds(1));
        }
        stubLookup(object);
        assertThat(transactions.finish(new MediaCleanupTransactions.Claim(KEY, token, true))).isFalse();
        verifyLookup(KEY, false);
        assertThat(object.getState()).isEqualTo(state);
    }

    @Test
    void finishRejectsWrongOwnerBeforeReferenceOrAssetReads() {
        var object = stable(KEY, OLD);
        UUID owner = UUID.randomUUID();
        object.claim(owner, NOW);
        stubLookup(object);
        assertThat(transactions.finish(new MediaCleanupTransactions.Claim(KEY, UUID.randomUUID(), true))).isFalse();
        verifyLookup(KEY, false);
        assertThat(object.getDeletionClaimId()).isEqualTo(owner);
        assertThat(object.getState()).isEqualTo(MediaObjectState.DELETING);
        assertThat(object.getClaimExpiresAt()).isEqualTo(NOW.plus(LEASE));
    }

    @Test
    void repeatingFinishDoesNotDeleteAgainOrChangeTombstoneTime() {
        var object = stable(KEY, OLD);
        UUID owner = UUID.randomUUID();
        object.claim(owner, NOW);
        var claim = new MediaCleanupTransactions.Claim(KEY, owner, false);
        stubLookup(object);
        when(references.allKeys()).thenReturn(Set.of());
        when(assets.findByR2Key(KEY)).thenReturn(List.of());
        assertThat(transactions.finish(claim)).isTrue();
        verifyFinish(KEY, List.of());
        clock.at(NOW.plusSeconds(10));
        assertThat(transactions.finish(claim)).isFalse();
        verifyLookup(KEY, false);
        assertThat(object.getState()).isEqualTo(MediaObjectState.DELETED);
        assertThat(object.getDeletedAt()).isEqualTo(NOW);
    }

    @Test
    void unexpectedPersistedReferencePreventsDatabaseDeleteAndTombstone() {
        var object = stable(KEY, OLD);
        UUID owner = UUID.randomUUID();
        object.claim(owner, NOW);
        stubLookup(object);
        when(references.allKeys()).thenReturn(Set.of(KEY));
        assertThatThrownBy(() -> transactions.finish(new MediaCleanupTransactions.Claim(KEY, owner, true)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("A persisted media reference bypassed the mutation guard.");
        verifyLookup(KEY, true);
        assertThat(object.getState()).isEqualTo(MediaObjectState.DELETING);
        assertThat(object.getDeletionClaimId()).isEqualTo(owner);
        assertThat(object.getClaimExpiresAt()).isEqualTo(NOW.plus(LEASE));
        assertThat(object.getDeletedAt()).isNull();
    }

    @Test
    void failureKeepsDeletingFenceAndAgeButExpiresOwnLeaseForRetry() {
        var object = stable(KEY, OLD);
        stubLookup(object);
        when(references.allKeys()).thenReturn(Set.of());
        var claim = transactions.claim(KEY, head(KEY));
        verifyLookup(KEY, true);
        clock.at(NOW.plusSeconds(1));
        transactions.failed(claim);
        verifyLookup(KEY, false);
        assertThat(object.getState()).isEqualTo(MediaObjectState.DELETING);
        assertThat(object.getDeletionClaimId()).isEqualTo(claim.token());
        assertThat(object.getClaimExpiresAt()).isEqualTo(clock.instant());
        assertThat(object.getUnreferencedSince()).isEqualTo(OLD);
        assertThat(object.getDeletedAt()).isNull();
        var retry = worker(properties).claim(KEY, head(KEY));
        verifyLookup(KEY, true);
        assertThat(retry).isNotNull();
        assertThat(retry.token()).isNotEqualTo(claim.token());
        assertThat(object.getClaimExpiresAt()).isEqualTo(clock.instant().plus(LEASE));
    }

    @Test
    void foreignFailureCannotExpireCurrentLease() {
        var object = stable(KEY, OLD);
        UUID owner = UUID.randomUUID();
        object.claim(owner, NOW);
        stubLookup(object);
        clock.at(NOW.plusSeconds(1));
        transactions.failed(new MediaCleanupTransactions.Claim(KEY, UUID.randomUUID(), true));
        verifyLookup(KEY, false);
        assertThat(object.getState()).isEqualTo(MediaObjectState.DELETING);
        assertThat(object.getDeletionClaimId()).isEqualTo(owner);
        assertThat(object.getClaimExpiresAt()).isEqualTo(NOW.plus(LEASE));
    }

    @Test
    void failureForMissingRowIsNoOp() {
        when(objects.findByBucketAndR2Key(BUCKET, KEY)).thenReturn(Optional.empty());
        transactions.failed(new MediaCleanupTransactions.Claim(KEY, UUID.randomUUID(), true));
        verifyLookup(KEY, false);
    }

    @ParameterizedTest
    @EnumSource(value = MediaObjectState.class, names = {"ACTIVE", "DELETED"})
    void failureDoesNotChangeNonDeletingRows(MediaObjectState state) {
        var object = receipt(KEY, OLD);
        UUID token = UUID.randomUUID();
        if (state == MediaObjectState.DELETED) {
            object.claim(token, NOW.minusSeconds(1));
            object.deleted(NOW.minusSeconds(1));
        }
        Instant deletedAt = object.getDeletedAt();
        stubLookup(object);
        transactions.failed(new MediaCleanupTransactions.Claim(KEY, token, true));
        verifyLookup(KEY, false);
        assertThat(object.getState()).isEqualTo(state);
        assertThat(object.getDeletedAt()).isEqualTo(deletedAt);
        assertThat(object.getClaimExpiresAt()).isNull();
    }

    @Test
    void oldClaimCannotFinishOrExpireReplacementButNewClaimCanFinish() {
        var object = stable(KEY, OLD);
        stubLookup(object);
        when(references.allKeys()).thenReturn(Set.of());
        var oldClaim = transactions.claim(KEY, head(KEY));
        verifyLookup(KEY, true);
        clock.at(NOW.plus(LEASE));
        var otherWorker = worker(properties);
        var newClaim = otherWorker.claim(KEY, head(KEY));
        verifyLookup(KEY, true);
        assertThat(newClaim).isNotNull();
        assertThat(newClaim.token()).isNotEqualTo(oldClaim.token());
        assertThat(transactions.finish(oldClaim)).isFalse();
        verifyLookup(KEY, false);
        transactions.failed(oldClaim);
        verifyLookup(KEY, false);
        assertThat(object.getState()).isEqualTo(MediaObjectState.DELETING);
        assertThat(object.getDeletionClaimId()).isEqualTo(newClaim.token());
        assertThat(object.getClaimExpiresAt()).isEqualTo(NOW.plus(LEASE).plus(LEASE));
        when(assets.findByR2Key(KEY)).thenReturn(List.of());
        assertThat(otherWorker.finish(newClaim)).isTrue();
        verifyFinish(KEY, List.of());
        assertThat(object.getState()).isEqualTo(MediaObjectState.DELETED);
        assertThat(object.getDeletedAt()).isEqualTo(clock.instant());
    }

    @Test
    void observationScanFailureAbortsBeforeRepositoryAccess() {
        var failure = new IllegalStateException("scan failed");
        when(references.allKeys()).thenThrow(failure);
        assertThatThrownBy(() -> transactions.observe(List.of(head(KEY)), PREFIX)).isSameAs(failure);
        order.verify(guard).lock();
        order.verify(references).allKeys();
    }

    @Test
    void claimScanFailureDoesNotCreateDeletionFence() {
        var object = stable(KEY, OLD);
        stubLookup(object);
        var failure = new IllegalStateException("scan failed");
        when(references.allKeys()).thenThrow(failure);
        assertThatThrownBy(() -> transactions.claim(KEY, head(KEY))).isSameAs(failure);
        verifyLookup(KEY, true);
        assertThat(object.getState()).isEqualTo(MediaObjectState.ACTIVE);
        assertThat(object.getUnreferencedSince()).isEqualTo(OLD);
        assertThat(object.getDeletionClaimId()).isNull();
    }

    @Test
    void finishScanFailureDoesNotDeleteAssetsOrMarkTombstone() {
        var object = stable(KEY, OLD);
        UUID token = UUID.randomUUID();
        object.claim(token, NOW);
        stubLookup(object);
        var failure = new IllegalStateException("scan failed");
        when(references.allKeys()).thenThrow(failure);
        assertThatThrownBy(() -> transactions.finish(new MediaCleanupTransactions.Claim(KEY, token, true)))
                .isSameAs(failure);
        verifyLookup(KEY, true);
        assertThat(object.getState()).isEqualTo(MediaObjectState.DELETING);
        assertThat(object.getDeletedAt()).isNull();
        assertThat(object.getClaimExpiresAt()).isEqualTo(NOW.plus(LEASE));
    }

    private MediaCleanupTransactions worker(MediaCleanupProperties settings) {
        return new MediaCleanupTransactions(guard, objects, assets, references, clock, settings, BUCKET);
    }

    private void stubObservation(List<TrackedMediaObject> rows, List<MediaAsset> attachments) {
        when(objects.findByBucketAndR2KeyStartingWith(BUCKET, PREFIX)).thenReturn(rows);
        when(assets.findAll()).thenReturn(attachments);
    }

    private void stubLookup(TrackedMediaObject object) {
        when(objects.findByBucketAndR2Key(BUCKET, object.getR2Key())).thenReturn(Optional.of(object));
    }

    private List<TrackedMediaObject> verifyObservation(String prefix) {
        order.verify(guard).lock();
        order.verify(references).allKeys();
        order.verify(objects).findByBucketAndR2KeyStartingWith(BUCKET, prefix);
        order.verify(assets).findAll();
        order.verify(objects).saveAll(discoveries.capture());
        return discoveries.getValue();
    }

    private void verifyLookup(String key, boolean scansReferences) {
        order.verify(guard).lock();
        order.verify(objects).findByBucketAndR2Key(BUCKET, key);
        if (scansReferences) order.verify(references).allKeys();
    }

    private void verifyFinish(String key, List<MediaAsset> matchingRows) {
        verifyLookup(key, true);
        order.verify(assets).findByR2Key(key);
        order.verify(assets).deleteAll(matchingRows);
    }

    private static TrackedMediaObject receipt(String key, Instant firstSeen) {
        return TrackedMediaObject.discovered(BUCKET, key, firstSeen, firstSeen.plus(Duration.ofHours(1)));
    }

    private static TrackedMediaObject stable(String key, Instant firstSeen) {
        var object = receipt(key, firstSeen);
        object.observeObject(OLD, "etag", firstSeen);
        return object;
    }

    private static MediaCleanupTransactions.Observation head(String key) {
        return new MediaCleanupTransactions.Observation(key, OLD, "etag");
    }

    private static MediaAsset asset(String key) {
        return MediaAsset.builder().r2Key(key).type(MediaType.COVER).build();
    }

    private static final class AdjustableClock extends Clock {
        private Instant now;
        private AdjustableClock(Instant now) { this.now = now; }
        private void at(Instant instant) { now = instant; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
        @Override public Instant instant() { return now; }
    }
}
