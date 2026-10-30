package com.invi.api.common;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * In-memory sliding-log limiter for public guest writes: no window boundary lets a burst of twice
 * the limit through. One app instance is assumed (the free test deployment); multiple instances
 * would each keep their own counters. Callers check only after the invitation is known to exist,
 * so random ids cannot grow the maps, and release a permit when the write itself fails.
 */
@Component
public class GuestWriteRateLimiter {

    public enum Kind { RSVP, GUESTBOOK }

    /** Expired logs are swept at most this often, so a full table never costs a scan per request. */
    static final Duration PURGE_INTERVAL = Duration.ofMinutes(1);

    private record Key(Kind kind, UUID invitationId, String client) {}

    private final GuestWriteRateLimitProperties properties;
    private final Clock clock;
    private final Map<Key, Deque<Instant>> clients = new HashMap<>();
    private final Map<Key, Deque<Instant>> invitations = new HashMap<>();
    private Instant nextPurge = Instant.MIN;

    public GuestWriteRateLimiter(GuestWriteRateLimitProperties properties, Clock clock) {
        this.properties = properties; this.clock = clock;
    }

    /** Counts one write or throws 429. A rejected write is not counted against either limit. */
    public synchronized void acquire(Kind kind, UUID invitationId, String clientAddress) {
        if (!properties.enabled()) return;
        Instant now = clock.instant();
        purgeIfDue(now);
        Key invitationKey = new Key(kind, invitationId, null);
        Deque<Instant> invitation = trimmed(invitations.get(invitationKey), now, properties.invitationWindow());
        if (invitation.size() >= properties.perInvitation()) {
            throw tooMany(invitation, now, properties.invitationWindow());
        }
        Key clientKey = clientKey(kind, invitationId, clientAddress);
        Deque<Instant> client = trimmed(clients.get(clientKey), now, properties.clientWindow());
        if (client.size() >= properties.perClient()) {
            throw tooMany(client, now, properties.clientWindow());
        }
        if (clients.containsKey(clientKey) || clients.size() < properties.maxTrackedClients()) {
            client.addLast(now);
            clients.put(clientKey, client);
        }
        // Otherwise (rotating spoofed addresses) per-client tracking is skipped; the invitation cap still holds.
        invitation.addLast(now);
        invitations.put(invitationKey, invitation);
    }

    /** Returns the permit taken by {@link #acquire} when the write did not happen (e.g. a database error). */
    public synchronized void release(Kind kind, UUID invitationId, String clientAddress) {
        if (!properties.enabled()) return;
        removeNewest(clients, clientKey(kind, invitationId, clientAddress));
        removeNewest(invitations, new Key(kind, invitationId, null));
    }

    private static Key clientKey(Kind kind, UUID invitationId, String clientAddress) {
        return new Key(kind, invitationId, clientAddress == null ? "" : clientAddress);
    }

    private static void removeNewest(Map<Key, Deque<Instant>> logs, Key key) {
        Deque<Instant> log = logs.get(key);
        if (log == null) return;
        log.pollLast();
        if (log.isEmpty()) logs.remove(key);
    }

    private void purgeIfDue(Instant now) {
        if (now.isBefore(nextPurge)) return;
        nextPurge = now.plus(PURGE_INTERVAL);
        purge(clients, now, properties.clientWindow());
        purge(invitations, now, properties.invitationWindow());
    }

    private static void purge(Map<Key, Deque<Instant>> logs, Instant now, Duration window) {
        logs.values().removeIf(log -> trimmed(log, now, window).isEmpty());
    }

    /** Drops timestamps outside the window; returns a new empty log for an unseen key. */
    private static Deque<Instant> trimmed(Deque<Instant> log, Instant now, Duration window) {
        if (log == null) return new ArrayDeque<>();
        Instant oldestAllowed = now.minus(window);
        while (!log.isEmpty() && !log.peekFirst().isAfter(oldestAllowed)) log.pollFirst();
        return log;
    }

    private static TooManyRequestsException tooMany(Deque<Instant> log, Instant now, Duration window) {
        long seconds = Duration.between(now, log.peekFirst().plus(window)).toSeconds();
        return new TooManyRequestsException(Math.max(1, seconds));
    }
}
