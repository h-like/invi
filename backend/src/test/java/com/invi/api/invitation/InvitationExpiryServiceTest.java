package com.invi.api.invitation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.invi.api.common.SchedulingConfiguration;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@ExtendWith(MockitoExtension.class)
class InvitationExpiryServiceTest {

    private static final UUID FIRST = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SECOND = UUID.fromString("22222222-2222-2222-2222-222222222222");
    /** 2027-06-22 23:30 in Korea: the last minutes of the tenth day after a 6/12 wedding. */
    private static final Instant KST_JUNE_22_LATE = Instant.parse("2027-06-22T14:30:00Z");
    /** 2027-06-23 00:30 in Korea while UTC is still on 6/22. */
    private static final Instant KST_JUNE_23_EARLY = Instant.parse("2027-06-22T15:30:00Z");
    private static final LocalDate JUNE_13 = LocalDate.of(2027, 6, 13);

    @Mock private InvitationExpiryTransactions transactions;

    private InvitationExpiryService service(boolean dryRun, Instant now) {
        return new InvitationExpiryService(transactions, new InvitationExpiryProperties(true, dryRun, 10, 100),
                Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void weddingIsKeptThroughTheTenthDayInKoreaTime() {
        service(false, KST_JUNE_22_LATE).run();

        // A 6/12 wedding is not "before 6/12", so it survives all of 6/22 KST.
        verify(transactions).findExpired(LocalDate.of(2027, 6, 12), 100);
    }

    @Test
    void koreanMidnightStartsTheNextDayEvenWhileUtcHasNot() {
        service(false, KST_JUNE_23_EARLY).run();

        verify(transactions).findExpired(JUNE_13, 100);
    }

    @Test
    void dryRunReportsExpiredIdsWithoutDeleting() {
        when(transactions.findExpired(JUNE_13, 100)).thenReturn(List.of(FIRST));

        InvitationExpiryReport report = service(true, KST_JUNE_23_EARLY).run();

        assertThat(report.dryRun()).isTrue();
        assertThat(report.expired()).containsExactly(FIRST);
        assertThat(report.deleted()).isZero();
        verify(transactions, never()).deleteIfExpired(any(), any());
    }

    @Test
    void oneFailingInvitationDoesNotStopTheOthers() {
        when(transactions.findExpired(JUNE_13, 100)).thenReturn(List.of(FIRST, SECOND));
        when(transactions.deleteIfExpired(FIRST, JUNE_13)).thenThrow(new IllegalStateException("fk"));
        when(transactions.deleteIfExpired(SECOND, JUNE_13)).thenReturn(true);

        InvitationExpiryReport report = service(false, KST_JUNE_23_EARLY).run();

        assertThat(report.deleted()).isEqualTo(1);
        assertThat(report.failed()).containsExactly(FIRST);
    }

    @Test
    void invitationChangedSinceListingIsNotCounted() {
        when(transactions.findExpired(JUNE_13, 100)).thenReturn(List.of(FIRST));
        when(transactions.deleteIfExpired(FIRST, JUNE_13)).thenReturn(false);

        InvitationExpiryReport report = service(false, KST_JUNE_23_EARLY).run();

        assertThat(report.deleted()).isZero();
        assertThat(report.failed()).isEmpty();
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
                .withUserConfiguration(SchedulingConfiguration.class, InvitationExpiryConfiguration.class,
                        InvitationExpiryJob.class)
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
