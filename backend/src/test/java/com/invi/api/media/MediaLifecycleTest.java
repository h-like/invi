package com.invi.api.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.invi.api.common.ConflictException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class MediaLifecycleTest {
    private static final Instant NOW = Instant.parse("2026-10-28T00:00:00Z");
    private static final String BUCKET = "test-media-bucket";
    private static final String KEY = "invitations/12345678-1234-1234-1234-123456789abc/12345678-1234-4234-8234-123456789abc.jpg";
    private static final String OTHER = KEY.replace(".jpg", ".png");
    private static final String REMOVED = KEY.replace(".jpg", ".webp");
    private MediaMutationGuard guard;
    private TrackedMediaObjectRepository objects;
    private MediaReferences references;
    private MediaLifecycle lifecycle;

    @BeforeEach
    void setUp() {
        guard = mock(MediaMutationGuard.class);
        objects = mock(TrackedMediaObjectRepository.class);
        references = mock(MediaReferences.class);
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenReturn(NOW);
        when(objects.save(any(TrackedMediaObject.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lifecycle = new MediaLifecycle(guard, objects, references, clock, BUCKET);
    }

    private TrackedMediaObject tracked(String key) {
        TrackedMediaObject object = TrackedMediaObject.discovered(BUCKET, key,
                NOW.minus(Duration.ofDays(30)), NOW.minus(Duration.ofDays(30)));
        when(objects.findByBucketAndR2Key(BUCKET, key)).thenReturn(Optional.of(object));
        return object;
    }

    private TrackedMediaObject retired(MediaObjectState state) {
        TrackedMediaObject object = tracked(KEY);
        object.claim(UUID.randomUUID(), NOW.minusSeconds(10));
        if (state == MediaObjectState.DELETED) object.deleted(NOW.minusSeconds(5));
        return object;
    }

    @Test
    void lockDelegatesToMutationGuard() {
        lifecycle.lock();
        verify(guard).lock();
    }

    @Test
    void issuedPresignCreatesReceiptInConfiguredBucketUntilExpiryPlusOneHour() {
        Instant expiresAt = NOW.plus(Duration.ofMinutes(15));
        lifecycle.issued(KEY, expiresAt);
        ArgumentCaptor<TrackedMediaObject> saved = ArgumentCaptor.forClass(TrackedMediaObject.class);
        verify(objects).save(saved.capture());
        assertThat(saved.getValue().getBucket()).isEqualTo(BUCKET);
        assertThat(saved.getValue().getR2Key()).isEqualTo(KEY);
        assertThat(saved.getValue().getState()).isEqualTo(MediaObjectState.ACTIVE);
        assertThat(saved.getValue().getUnreferencedSince()).isEqualTo(NOW);
        assertThat(saved.getValue().getSafeAfter()).isEqualTo(expiresAt.plus(Duration.ofHours(1)));
        verifyNoInteractions(references);
    }

    @Test
    void referencesChangedProtectsEveryAfterKeyAndResetsOnlyRemovedKeys() {
        TrackedMediaObject retained = tracked(KEY);
        TrackedMediaObject added = tracked(OTHER);
        TrackedMediaObject removed = tracked(REMOVED);
        removed.protect();
        lifecycle.referencesChanged(Set.of(KEY, REMOVED), Set.of(KEY, OTHER));
        assertThat(retained.getUnreferencedSince()).isNull();
        assertThat(added.getUnreferencedSince()).isNull();
        assertThat(removed.getUnreferencedSince()).isEqualTo(NOW);
        assertThat(retained.getState()).isEqualTo(MediaObjectState.ACTIVE);
        assertThat(added.getState()).isEqualTo(MediaObjectState.ACTIVE);
        verify(objects, never()).save(any(TrackedMediaObject.class));
    }

    @Test
    void unchangedSharedKeyRemainsProtectedRatherThanReceivingRemovalAge() {
        TrackedMediaObject object = tracked(KEY);
        lifecycle.referencesChanged(Set.of(KEY), Set.of(KEY));
        assertThat(object.getUnreferencedSince()).isNull();
        assertThat(object.eligible(NOW.plus(Duration.ofDays(100)), Duration.ofDays(7))).isFalse();
    }

    @Test
    void removingOneReferenceToSharedKeyMayConservativelyRestartGrace() {
        TrackedMediaObject object = tracked(KEY);
        object.protect();
        when(references.allKeys()).thenReturn(Set.of(KEY));
        lifecycle.referencesChanged(Set.of(KEY), Set.of());
        assertThat(object.getUnreferencedSince()).isEqualTo(NOW);
        // Global references are checked by cleanup, not used to shorten this local grace period.
        verifyNoInteractions(references);
    }

    @ParameterizedTest
    @EnumSource(value = MediaObjectState.class, names = {"DELETING", "DELETED"})
    void referenceSaveRejectsRetiredKeysBeforeChangingAnyObjects(MediaObjectState state) {
        TrackedMediaObject retired = retired(state);
        TrackedMediaObject active = tracked(OTHER);
        Instant originalAge = active.getUnreferencedSince();
        lifecycle.assertUsable(Set.of(OTHER));
        assertThatThrownBy(() -> lifecycle.referencesChanged(Set.of(OTHER), Set.of(KEY, OTHER)))
                .isInstanceOf(ConflictException.class);
        assertThat(active.getUnreferencedSince()).isEqualTo(originalAge);
        assertThat(retired.getState()).isEqualTo(state);
        verify(objects, never()).save(any(TrackedMediaObject.class));
    }

    @ParameterizedTest
    @EnumSource(value = MediaObjectState.class, names = {"DELETING", "DELETED"})
    void attachmentRejectsRetiredKeysWithoutClearingStateOrLease(MediaObjectState state) {
        TrackedMediaObject object = retired(state);
        UUID claim = object.getDeletionClaimId();
        Instant age = object.getUnreferencedSince();
        assertThatThrownBy(() -> lifecycle.attached(KEY)).isInstanceOf(ConflictException.class);
        assertThat(object.getState()).isEqualTo(state);
        assertThat(object.getDeletionClaimId()).isEqualTo(claim);
        assertThat(object.getUnreferencedSince()).isEqualTo(age);
        verify(objects, never()).save(any(TrackedMediaObject.class));
    }

    @Test
    void activeAttachmentRestartsGraceWithoutReplacingExistingReceipt() {
        TrackedMediaObject object = tracked(KEY);
        Instant safeAfter = object.getSafeAfter();
        lifecycle.attached(KEY);
        assertThat(object.getUnreferencedSince()).isEqualTo(NOW);
        assertThat(object.getSafeAfter()).isEqualTo(safeAfter);
        assertThat(object.getState()).isEqualTo(MediaObjectState.ACTIVE);
        verify(objects, never()).save(any(TrackedMediaObject.class));
    }

    @Test
    void untrackedLegacyAttachmentCreatesActiveReceiptWithOneHourSafetyWindow() {
        when(objects.findByBucketAndR2Key(BUCKET, KEY)).thenReturn(Optional.empty());
        lifecycle.attached(KEY);
        ArgumentCaptor<TrackedMediaObject> saved = ArgumentCaptor.forClass(TrackedMediaObject.class);
        verify(objects).save(saved.capture());
        assertThat(saved.getValue().getBucket()).isEqualTo(BUCKET);
        assertThat(saved.getValue().getR2Key()).isEqualTo(KEY);
        assertThat(saved.getValue().getState()).isEqualTo(MediaObjectState.ACTIVE);
        assertThat(saved.getValue().getUnreferencedSince()).isEqualTo(NOW);
        assertThat(saved.getValue().getSafeAfter()).isEqualTo(NOW.plus(Duration.ofHours(1)));
    }

    @Test
    void untrackedLegacyKeysRemainUsableWhenSavingReferences() {
        when(objects.findByBucketAndR2Key(BUCKET, KEY)).thenReturn(Optional.empty());
        lifecycle.assertUsable(Set.of(KEY));
        lifecycle.referencesChanged(Set.of(), Set.of(KEY));
        verify(objects, never()).save(any(TrackedMediaObject.class));
    }

    @Test
    void anyGlobalReferenceRefusesDeletionBeforeLookingUpOrMutatingReceipt() {
        when(references.allKeys()).thenReturn(Set.of(OTHER, KEY));
        assertThatThrownBy(() -> lifecycle.requestDeletion(KEY)).isInstanceOf(ConflictException.class);
        verify(references).allKeys();
        verifyNoInteractions(objects, guard);
    }

    @Test
    void unreferencedDeletionOnlySchedulesFreshGraceWithoutDeletingReceipt() {
        TrackedMediaObject object = tracked(KEY);
        object.protect();
        when(references.allKeys()).thenReturn(Set.of());
        lifecycle.requestDeletion(KEY);
        assertThat(object.getUnreferencedSince()).isEqualTo(NOW);
        assertThat(object.getState()).isEqualTo(MediaObjectState.ACTIVE);
        assertThat(object.getDeletedAt()).isNull();
        assertThat(object.eligible(NOW.plus(Duration.ofDays(7)).minusNanos(1), Duration.ofDays(7))).isFalse();
        verify(references).allKeys();
        verify(objects).findByBucketAndR2Key(BUCKET, KEY);
        // No physical object deletion is a lifecycle dependency; only the receipt is changed.
        verifyNoMoreInteractions(objects, references);
        verifyNoInteractions(guard);
    }

    @Test
    void untrackedDeletionRequestCreatesReceiptInsteadOfDeletingImmediately() {
        when(references.allKeys()).thenReturn(Set.of());
        when(objects.findByBucketAndR2Key(BUCKET, KEY)).thenReturn(Optional.empty());
        lifecycle.requestDeletion(KEY);
        ArgumentCaptor<TrackedMediaObject> saved = ArgumentCaptor.forClass(TrackedMediaObject.class);
        verify(objects).save(saved.capture());
        assertThat(saved.getValue().getState()).isEqualTo(MediaObjectState.ACTIVE);
        assertThat(saved.getValue().getR2Key()).isEqualTo(KEY);
        assertThat(saved.getValue().getBucket()).isEqualTo(BUCKET);
        assertThat(saved.getValue().getUnreferencedSince()).isEqualTo(NOW);
        assertThat(saved.getValue().getSafeAfter()).isEqualTo(NOW.plus(Duration.ofHours(1)));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"some-key", "../photo.jpg", "invitations/not-a-uuid/photo.jpg", "https://example.com/photo.jpg"})
    void unmanagedKeyRefusesDeletionBeforeAnyReferenceOrReceiptWork(String key) {
        assertThatThrownBy(() -> lifecycle.requestDeletion(key)).isInstanceOf(ConflictException.class);
        verifyNoInteractions(objects, references, guard);
    }

    @ParameterizedTest
    @EnumSource(value = MediaObjectState.class, names = {"DELETING", "DELETED"})
    void deletionRequestCannotClearRetiredStateOrClaimOrRestartAge(MediaObjectState state) {
        TrackedMediaObject object = retired(state);
        UUID claim = object.getDeletionClaimId();
        Instant expiry = object.getClaimExpiresAt();
        Instant age = object.getUnreferencedSince();
        Instant deletedAt = object.getDeletedAt();
        when(references.allKeys()).thenReturn(Set.of());
        lifecycle.requestDeletion(KEY);
        assertThat(object.getState()).isEqualTo(state);
        assertThat(object.getDeletionClaimId()).isEqualTo(claim);
        assertThat(object.getClaimExpiresAt()).isEqualTo(expiry);
        assertThat(object.getUnreferencedSince()).isEqualTo(age);
        assertThat(object.getDeletedAt()).isEqualTo(deletedAt);
        verify(objects, never()).save(any(TrackedMediaObject.class));
    }
}
