package com.invi.api.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.invi.api.common.TooManyRequestsException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PlaceSearchGuardTest {

    private Instant now = Instant.parse("2026-11-27T05:00:00Z");
    private final Clock clock = new Clock() {
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    };

    private PlaceSearchGuard guard(int perMember, int dailyCalls, Duration cacheTtl) {
        return new PlaceSearchGuard(new PlaceSearchProperties(perMember, Duration.ofMinutes(10), dailyCalls, cacheTtl, 10, 100), clock);
    }

    @Test
    void limitsEachAccount_withinItsWindow_withoutAffectingOthers() {
        PlaceSearchGuard guard = guard(2, 100, Duration.ZERO);
        guard.acquireSearch("a");
        guard.acquireSearch("a");
        assertThatThrownBy(() -> guard.acquireSearch("a"))
                .isInstanceOf(TooManyRequestsException.class)
                .extracting(e -> ((TooManyRequestsException) e).getRetryAfterSeconds()).isEqualTo(600L);
        guard.acquireSearch("b");

        now = now.plus(Duration.ofMinutes(10));
        guard.acquireSearch("a");
    }

    @Test
    void capsKakaoCallsPerKoreanDay_andResetsAtKoreanMidnight() {
        PlaceSearchGuard guard = guard(100, 2, Duration.ZERO);
        now = Instant.parse("2026-11-27T14:30:00Z"); // 23:30 KST
        guard.acquireKakaoCall();
        guard.acquireKakaoCall();
        assertThatThrownBy(guard::acquireKakaoCall)
                .isInstanceOf(PlaceSearchUnavailableException.class)
                .hasMessage(PlaceSearchGuard.DAILY_LIMIT_MESSAGE);

        now = Instant.parse("2026-11-27T15:00:00Z"); // 00:00 KST next day
        guard.acquireKakaoCall();
    }

    @Test
    void answersRepeatedSearchesFromTheCache_untilTheyExpire() {
        PlaceSearchGuard guard = guard(100, 100, Duration.ofHours(1));
        AtomicInteger calls = new AtomicInteger();
        PlaceDto place = new PlaceDto("1", "웨딩홀", "예식장", "주소", null, null, 37, 127, null);

        assertThat(guard.cached("venue|웨딩홀", () -> { calls.incrementAndGet(); return List.of(place); })).containsExactly(place);
        assertThat(guard.cached("venue|웨딩홀", () -> { calls.incrementAndGet(); return List.of(); })).containsExactly(place);
        assertThat(calls).hasValue(1);

        now = now.plus(Duration.ofHours(1));
        assertThat(guard.cached("venue|웨딩홀", () -> { calls.incrementAndGet(); return List.of(); })).isEmpty();
        assertThat(calls).hasValue(2);
    }

    @Test
    void doesNotCacheFailures() {
        PlaceSearchGuard guard = guard(100, 100, Duration.ofHours(1));
        assertThatThrownBy(() -> guard.cached("k", () -> { throw new PlaceSearchUnavailableException("down"); }))
                .isInstanceOf(PlaceSearchUnavailableException.class);
        assertThat(guard.cached("k", List::of)).isEmpty();
    }
}
