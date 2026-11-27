package com.invi.api.place;

import com.invi.api.common.TooManyRequestsException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Per-account limit, app-wide daily Kakao call budget and a short result cache for place search
 * (see {@link PlaceSearchProperties}). In memory, assuming one app instance like
 * {@code GuestWriteRateLimiter}; a restart forgets counts, which only ever loosens the limits.
 */
@Component
@EnableConfigurationProperties(PlaceSearchProperties.class)
class PlaceSearchGuard {

    /** Kakao's daily quota resets on the Korean calendar day. */
    static final ZoneId KAKAO_DAY = ZoneId.of("Asia/Seoul");

    static final String DAILY_LIMIT_MESSAGE =
            "오늘 장소 검색 한도에 도달했어요. 내일 다시 검색하거나 주소를 직접 입력해 주세요.";

    private record Cached(List<PlaceDto> places, Instant expiresAt) {}

    private final PlaceSearchProperties properties;
    private final Clock clock;
    private final Map<String, Deque<Instant>> members = new HashMap<>();
    private final LinkedHashMap<String, Cached> cache = new LinkedHashMap<>(16, 0.75f, true);
    private LocalDate day;
    private int callsToday;

    PlaceSearchGuard(PlaceSearchProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /** Counts one search for this account or throws 429. */
    synchronized void acquireSearch(String memberKey) {
        Instant now = clock.instant();
        Instant oldestAllowed = now.minus(properties.memberWindow());
        if (members.size() >= properties.maxTrackedMembers()) {
            members.values().removeIf(log -> trim(log, oldestAllowed).isEmpty());
        }
        Deque<Instant> log = trim(members.computeIfAbsent(memberKey, k -> new ArrayDeque<>()), oldestAllowed);
        if (log.size() >= properties.perMember()) {
            long seconds = Duration.between(now, log.peekFirst().plus(properties.memberWindow())).toSeconds();
            throw new TooManyRequestsException(Math.max(1, seconds));
        }
        log.addLast(now);
    }

    /** Counts one call to Kakao against today's budget, or refuses it before it is made. */
    synchronized void acquireKakaoCall() {
        LocalDate today = LocalDate.ofInstant(clock.instant(), KAKAO_DAY);
        if (!today.equals(day)) {
            day = today;
            callsToday = 0;
        }
        if (callsToday >= properties.dailyCalls()) throw new PlaceSearchUnavailableException(DAILY_LIMIT_MESSAGE);
        callsToday++;
    }

    /** The cached answer for this search, or {@code search} run and cached on success. */
    List<PlaceDto> cached(String key, Supplier<List<PlaceDto>> search) {
        synchronized (this) {
            Cached hit = cache.get(key);
            if (hit != null && clock.instant().isBefore(hit.expiresAt())) return hit.places();
            if (hit != null) cache.remove(key);
        }
        List<PlaceDto> places = List.copyOf(search.get());
        synchronized (this) {
            if (properties.cacheSize() > 0 && !properties.cacheTtl().isZero()) {
                cache.put(key, new Cached(places, clock.instant().plus(properties.cacheTtl())));
                while (cache.size() > properties.cacheSize()) cache.remove(cache.keySet().iterator().next());
            }
        }
        return places;
    }

    private static Deque<Instant> trim(Deque<Instant> log, Instant oldestAllowed) {
        while (!log.isEmpty() && !log.peekFirst().isAfter(oldestAllowed)) log.pollFirst();
        return log;
    }
}
