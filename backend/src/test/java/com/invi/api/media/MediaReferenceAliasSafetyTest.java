package com.invi.api.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.invi.api.common.ConflictException;
import com.invi.api.invitation.Invitation;
import com.invi.api.invitation.InvitationRepository;
import com.invi.api.template.TemplateRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The actual extractor, lifecycle and cleanup transactions share browser-normalized key identity. */
class MediaReferenceAliasSafetyTest {
    private static final String BUCKET = "invi-media";
    private static final String INVITATION = "11111111-1111-4111-8111-111111111111";
    private static final String FILE = "22222222-2222-4222-8222-222222222222.webp";
    private static final String KEY = "invitations/" + INVITATION + "/" + FILE;
    private static final Instant NOW = Instant.parse("2026-10-28T09:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @ParameterizedTest
    @ValueSource(strings = {"./", "unused/../", "(unused)/../", "[unused]/../", "{unused}/../", "unused space/../", "%2e/", "%252e/"})
    void aliasesCannotRestoreADeletionFencedKey(String alias) {
        TrackedMediaObject object = TrackedMediaObject.discovered(BUCKET, KEY, NOW.minus(Duration.ofDays(9)), NOW.minusSeconds(1));
        object.claim(UUID.randomUUID(), NOW);
        TrackedMediaObjectRepository objects = mock(TrackedMediaObjectRepository.class);
        when(objects.findByBucketAndR2Key(BUCKET, KEY)).thenReturn(Optional.of(object));
        MediaLifecycle lifecycle = new MediaLifecycle(mock(MediaMutationGuard.class), objects,
                mock(MediaReferences.class), CLOCK, BUCKET);
        var page = new ObjectMapper().createObjectNode().put("futurePhoto", url(alias));

        assertThat(MediaReferences.extract(page)).contains(KEY);
        assertThatThrownBy(() -> lifecycle.referencesChanged(Set.of(), MediaReferences.extract(page)))
                .isInstanceOf(ConflictException.class);
        assertThat(object.getState()).isEqualTo(MediaObjectState.DELETING);
    }

    @ParameterizedTest
    @ValueSource(strings = {"./", "unused/../", "(unused)/../", "[unused]/../", "{unused}/../", "unused space/../", "%2e/", "%252e/"})
    void aliasesProtectOldObjectsInBothPlanningAndClaim(String alias) {
        InvitationRepository invitations = mock(InvitationRepository.class);
        TemplateRepository templates = mock(TemplateRepository.class);
        Invitation invitation = Invitation.builder().pageData(new ObjectMapper().createObjectNode().put("futurePhoto", url(alias))).build();
        when(invitations.findAll()).thenReturn(List.of(invitation));
        when(templates.findAll()).thenReturn(List.of());
        MediaReferences references = new MediaReferences(invitations, templates);
        TrackedMediaObject object = TrackedMediaObject.discovered(BUCKET, KEY, NOW.minus(Duration.ofDays(9)), NOW.minusSeconds(1));
        Instant modified = NOW.minus(Duration.ofDays(10));
        object.observeObject(modified, "etag", NOW.minus(Duration.ofDays(9)));
        TrackedMediaObjectRepository objects = mock(TrackedMediaObjectRepository.class);
        MediaAssetRepository assets = mock(MediaAssetRepository.class);
        when(objects.findByBucketAndR2KeyStartingWith(BUCKET, "invitations/")).thenReturn(List.of(object));
        when(objects.findByBucketAndR2Key(BUCKET, KEY)).thenReturn(Optional.of(object));
        when(assets.findAll()).thenReturn(List.of());
        when(objects.saveAll(any())).thenReturn(List.of());
        MediaCleanupTransactions transactions = new MediaCleanupTransactions(mock(MediaMutationGuard.class),
                objects, assets, references, CLOCK, new MediaCleanupProperties(false, true, Duration.ofDays(7), 10000, 100), BUCKET);

        var plan = transactions.observe(List.of(new MediaCleanupTransactions.Observation(KEY, modified, "etag")), "invitations/");
        assertThat(plan.referenced()).isEqualTo(1);
        assertThat(plan.eligible()).isZero();
        assertThat(plan.candidates()).isEmpty();
        assertThat(transactions.claim(KEY, new MediaCleanupTransactions.Observation(KEY, modified, "etag"))).isNull();
        assertThat(object.getUnreferencedSince()).isNull();
    }

    private static String url(String alias) {
        return "https://cdn.example/invitations/" + INVITATION + "/" + alias + FILE;
    }
}
