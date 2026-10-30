package com.invi.api.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.invi.api.common.GuestWriteRateLimiter.Kind;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GuestWriteRateLimiterTest {

    private static final UUID INVITATION = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_INVITATION = UUID.fromString("22222222-2222-2222-2222-222222222222");

    /** Mutable clock so windows can be advanced without sleeping. */
    private static final class MovableClock extends Clock {
        private Instant now = Instant.parse("2027-06-12T03:00:00Z");
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
        void advance(Duration duration) { now = now.plus(duration); }
    }

    private final MovableClock clock = new MovableClock();

    private GuestWriteRateLimiter limiter(int perClient, int perInvitation, int maxClients) {
        return new GuestWriteRateLimiter(new GuestWriteRateLimitProperties(true, perClient, Duration.ofMinutes(10),
                perInvitation, Duration.ofHours(1), maxClients), clock);
    }

    private static void acquireTimes(GuestWriteRateLimiter limiter, int times, Kind kind, UUID invitation, String client) {
        for (int i = 0; i < times; i++) limiter.acquire(kind, invitation, client);
    }

    @Test
    void clientIsLimitedPerInvitationAndKindAndRecoversAfterTheWindow() {
        GuestWriteRateLimiter limiter = limiter(3, 100, 100);
        acquireTimes(limiter, 3, Kind.GUESTBOOK, INVITATION, "198.51.100.1");

        assertThatThrownBy(() -> limiter.acquire(Kind.GUESTBOOK, INVITATION, "198.51.100.1"))
                .isInstanceOf(TooManyRequestsException.class)
                .satisfies(error -> assertThat(((TooManyRequestsException) error).getRetryAfterSeconds()).isEqualTo(600));
        // Other kinds, invitations and clients have their own counters.
        assertThatCode(() -> {
            limiter.acquire(Kind.RSVP, INVITATION, "198.51.100.1");
            limiter.acquire(Kind.GUESTBOOK, OTHER_INVITATION, "198.51.100.1");
            limiter.acquire(Kind.GUESTBOOK, INVITATION, "198.51.100.2");
        }).doesNotThrowAnyException();

        clock.advance(Duration.ofMinutes(10));
        assertThatCode(() -> limiter.acquire(Kind.GUESTBOOK, INVITATION, "198.51.100.1")).doesNotThrowAnyException();
    }

    @Test
    void rotatingClientAddressesStillHitTheInvitationCeiling() {
        GuestWriteRateLimiter limiter = limiter(3, 5, 100);
        for (int i = 0; i < 5; i++) limiter.acquire(Kind.RSVP, INVITATION, "spoofed-" + i);

        assertThatThrownBy(() -> limiter.acquire(Kind.RSVP, INVITATION, "spoofed-new"))
                .isInstanceOf(TooManyRequestsException.class);
        assertThatCode(() -> limiter.acquire(Kind.RSVP, OTHER_INVITATION, "spoofed-new")).doesNotThrowAnyException();
    }

    @Test
    void rejectedWritesDoNotUseUpTheInvitationBudget() {
        GuestWriteRateLimiter limiter = limiter(2, 4, 100);
        acquireTimes(limiter, 2, Kind.GUESTBOOK, INVITATION, "spammer");
        for (int i = 0; i < 10; i++) {
            assertThatThrownBy(() -> limiter.acquire(Kind.GUESTBOOK, INVITATION, "spammer"))
                    .isInstanceOf(TooManyRequestsException.class);
        }

        assertThatCode(() -> acquireTimes(limiter, 2, Kind.GUESTBOOK, INVITATION, "guest"))
                .doesNotThrowAnyException();
    }

    @Test
    void fullClientTableFallsBackToTheInvitationCeilingInsteadOfBlockingEveryone() {
        GuestWriteRateLimiter limiter = limiter(1, 1000, 100);
        for (int i = 0; i < 100; i++) limiter.acquire(Kind.RSVP, INVITATION, "flood-" + i);

        // Table full and nothing expired: a new guest is still let through.
        assertThatCode(() -> limiter.acquire(Kind.RSVP, INVITATION, "real-guest")).doesNotThrowAnyException();

        // Once windows expire the table is purged and per-client tracking resumes.
        clock.advance(Duration.ofMinutes(10));
        limiter.acquire(Kind.RSVP, INVITATION, "real-guest");
        assertThatThrownBy(() -> limiter.acquire(Kind.RSVP, INVITATION, "real-guest"))
                .isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void slidingWindowAllowsNoBurstAcrossAWindowBoundary() {
        GuestWriteRateLimiter limiter = limiter(3, 100, 100);
        clock.advance(Duration.ofMinutes(9).plusSeconds(59));
        acquireTimes(limiter, 3, Kind.RSVP, INVITATION, "a");
        clock.advance(Duration.ofSeconds(2));

        // A fixed window would have reset here; the sliding log still sees three writes 2s ago.
        assertThatThrownBy(() -> limiter.acquire(Kind.RSVP, INVITATION, "a"))
                .isInstanceOf(TooManyRequestsException.class)
                .satisfies(error -> assertThat(((TooManyRequestsException) error).getRetryAfterSeconds()).isEqualTo(598));
    }

    @Test
    void releasedPermitsCanBeUsedAgain() {
        GuestWriteRateLimiter limiter = limiter(2, 2, 100);
        acquireTimes(limiter, 2, Kind.GUESTBOOK, INVITATION, "a");
        limiter.release(Kind.GUESTBOOK, INVITATION, "a");

        assertThatCode(() -> limiter.acquire(Kind.GUESTBOOK, INVITATION, "a")).doesNotThrowAnyException();
        assertThatThrownBy(() -> limiter.acquire(Kind.GUESTBOOK, INVITATION, "a"))
                .isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void disabledLimiterAllowsEverything() {
        GuestWriteRateLimiter limiter = new GuestWriteRateLimiter(new GuestWriteRateLimitProperties(false, 1,
                Duration.ofMinutes(10), 1, Duration.ofHours(1), 100), clock);
        assertThatCode(() -> acquireTimes(limiter, 5, Kind.RSVP, INVITATION, "a")).doesNotThrowAnyException();
    }

    @Test
    void invalidLimitsFailConfiguration() {
        assertThatThrownBy(() -> new GuestWriteRateLimitProperties(true, 10, Duration.ofMinutes(10), 5,
                Duration.ofHours(1), 100)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GuestWriteRateLimitProperties(true, 1, Duration.ZERO, 5,
                Duration.ofHours(1), 100)).isInstanceOf(IllegalArgumentException.class);
    }
}
