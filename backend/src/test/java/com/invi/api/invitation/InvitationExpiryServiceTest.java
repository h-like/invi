package com.invi.api.invitation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.invi.api.common.TestEntityIds;
import com.invi.api.guestbook.GuestbookEntryRepository;
import com.invi.api.media.MediaAssetRepository;
import com.invi.api.media.MediaLifecycle;
import com.invi.api.rsvp.RsvpRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.domain.Limit;

@ExtendWith(MockitoExtension.class)
class InvitationExpiryServiceTest {

    private static final UUID INVITATION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String PAGE_KEY =
            "invitations/11111111-1111-1111-1111-111111111111/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa.jpg";
    private static final String LANG_KEY =
            "invitations/11111111-1111-1111-1111-111111111111/bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb.png";
    private static final String ASSET_KEY =
            "invitations/11111111-1111-1111-1111-111111111111/cccccccc-cccc-4ccc-8ccc-cccccccccccc.webp";
    /** 2027-06-22 23:30 in Korea: the last minutes of the tenth day after a 6/12 wedding. */
    private static final Instant KST_JUNE_22_LATE = Instant.parse("2027-06-22T14:30:00Z");
    /** 2027-06-23 00:30 in Korea while UTC is still on 6/22. */
    private static final Instant KST_JUNE_23_EARLY = Instant.parse("2027-06-22T15:30:00Z");

    @Mock private InvitationRepository invitations;
    @Mock private RsvpRepository rsvps;
    @Mock private GuestbookEntryRepository guestbook;
    @Mock private MediaAssetRepository mediaAssets;
    @Mock private MediaLifecycle lifecycle;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private InvitationExpiryService service(boolean dryRun, Instant now) {
        return new InvitationExpiryService(invitations, rsvps, guestbook, mediaAssets, lifecycle,
                new InvitationExpiryProperties(true, dryRun, 10, 100), Clock.fixed(now, ZoneOffset.UTC));
    }

    private Invitation expiredInvitation() throws Exception {
        Invitation invitation = Invitation.builder()
                .slug("expired")
                .weddingDate(LocalDate.of(2027, 6, 12))
                .pageData(objectMapper.readTree("{\"blocks\":[{\"content\":{\"coverImage\":\"https://img.example/" + PAGE_KEY + "\"}}]}"))
                .langVariants(objectMapper.readTree("{\"en\":{\"photo\":\"" + LANG_KEY + "\"}}"))
                .build();
        TestEntityIds.setId(invitation, INVITATION_ID);
        return invitation;
    }

    @Test
    void weddingIsKeptThroughTheTenthDayInKoreaTime() {
        service(false, KST_JUNE_22_LATE).run();

        // A 6/12 wedding is not "before 6/12", so it survives all of 6/22 KST.
        verify(invitations).findByPlanAndWeddingDateBeforeOrderByWeddingDateAsc(
                Plan.FREE, LocalDate.of(2027, 6, 12), Limit.of(100));
    }

    @Test
    void koreanMidnightStartsTheNextDayEvenWhileUtcHasNot() {
        service(false, KST_JUNE_23_EARLY).run();

        verify(invitations).findByPlanAndWeddingDateBeforeOrderByWeddingDateAsc(
                Plan.FREE, LocalDate.of(2027, 6, 13), Limit.of(100));
    }

    @Test
    void dryRunReportsExpiredIdsWithoutDeletingOrReleasingMedia() throws Exception {
        when(invitations.findByPlanAndWeddingDateBeforeOrderByWeddingDateAsc(any(), any(), any()))
                .thenReturn(List.of(expiredInvitation()));

        InvitationExpiryReport report = service(true, KST_JUNE_23_EARLY).run();

        assertThat(report.dryRun()).isTrue();
        assertThat(report.expired()).containsExactly(INVITATION_ID);
        assertThat(report.deleted()).isZero();
        verify(lifecycle).lock();
        verify(lifecycle, never()).referencesChanged(any(), any());
        verify(rsvps, never()).deleteAllByInvitationId(any());
        verify(guestbook, never()).deleteAllByInvitationId(any());
        verify(mediaAssets, never()).deleteAllByInvitationId(any());
        verify(invitations, never()).delete(any());
    }

    @Test
    void deletionLocksFirstReleasesEveryMediaKeyAndRemovesChildrenBeforeTheInvitation() throws Exception {
        Invitation invitation = expiredInvitation();
        when(invitations.findByPlanAndWeddingDateBeforeOrderByWeddingDateAsc(any(), any(), any()))
                .thenReturn(List.of(invitation));
        when(mediaAssets.findR2KeysByInvitationId(INVITATION_ID)).thenReturn(List.of(ASSET_KEY));

        InvitationExpiryReport report = service(false, KST_JUNE_23_EARLY).run();

        assertThat(report.dryRun()).isFalse();
        assertThat(report.deleted()).isEqualTo(1);
        InOrder order = inOrder(lifecycle, invitations, rsvps, guestbook, mediaAssets);
        order.verify(lifecycle).lock();
        order.verify(invitations).findByPlanAndWeddingDateBeforeOrderByWeddingDateAsc(any(), any(), any());
        order.verify(lifecycle).referencesChanged(Set.of(PAGE_KEY, LANG_KEY, ASSET_KEY), Set.of());
        order.verify(rsvps).deleteAllByInvitationId(INVITATION_ID);
        order.verify(guestbook).deleteAllByInvitationId(INVITATION_ID);
        order.verify(mediaAssets).deleteAllByInvitationId(INVITATION_ID);
        order.verify(invitations).delete(invitation);
    }

    @Test
    void onlyFreeInvitationsAreQueried() {
        service(false, KST_JUNE_23_EARLY).run();

        verify(invitations).findByPlanAndWeddingDateBeforeOrderByWeddingDateAsc(eq(Plan.FREE), any(), any());
    }

    @Test
    void invalidRetentionOrBatchSizeFailsConfiguration() {
        assertThatThrownBy(() -> new InvitationExpiryProperties(true, true, 0, 100))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InvitationExpiryProperties(true, true, 10, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static ApplicationContextRunner context() {
        return new ApplicationContextRunner()
                .withUserConfiguration(InvitationExpiryConfiguration.class, InvitationExpiryJob.class)
                .withBean(InvitationExpiryService.class, () -> mock(InvitationExpiryService.class));
    }

    @Test
    void defaultsRunTheJobInDryRunWithTenDays() {
        context().run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(InvitationExpiryJob.class);
            InvitationExpiryProperties properties = context.getBean(InvitationExpiryProperties.class);
            assertThat(properties.dryRun()).isTrue();
            assertThat(properties.retentionDays()).isEqualTo(10);
        });
    }

    @Test
    void jobCanBeTurnedOff() {
        context().withPropertyValues("invi.invitation.expiry.enabled=false")
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(InvitationExpiryJob.class));
    }
}
