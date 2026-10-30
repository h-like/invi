package com.invi.api.common;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * In-memory fixed-window limiter for public guest writes. One app instance is assumed (the
 * free test deployment); multiple instances would each keep their own counters.
 * Callers check only after the invitation is known to exist, so random ids cannot grow the maps.
 */
@Component
@EnableConfigurationProperties(GuestWriteRateLimitProperties.class)
public class GuestWriteRateLimiter {

    public enum Kind { RSVP, GUESTBOOK }

    private record Key(Kind kind, UUID invitationId, String client) {}
    private record Window(Instant start, int count) {}

    private final GuestWriteRateLimitProperties properties;
    private final Clock clock;
    private final Map<Key, Window> clients = new HashMap<>();
    private final Map<Key, Window> invitations = new HashMap<>();

    public GuestWriteRateLimiter(GuestWriteRateLimitProperties properties, Clock clock) {
        this.properties = properties; this.clock = clock;
    }

    /** Counts one write or throws 429. A rejected write is not counted against either limit. */
    public synchronized void acquire(Kind kind, UUID invitationId, String clientAddress) {
        if (!properties.enabled()) return;
        Instant now = clock.instant();
        Key invitationKey = new Key(kind, invitationId, null);
        Window invitation = current(invitations.get(invitationKey), now, properties.invitationWindow());
        if (invitation.count() >= properties.perInvitation()) {
            throw tooMany(invitation, now, properties.invitationWindow());
        }
        Key clientKey = new Key(kind, invitationId, clientAddress == null ? "" : clientAddress);
        Window client = current(clients.get(clientKey), now, properties.clientWindow());
        if (client.count() >= properties.perClient()) {
            throw tooMany(client, now, properties.clientWindow());
        }
        if (clients.containsKey(clientKey) || hasRoomForClient(now)) {
            clients.put(clientKey, new Window(client.start(), client.count() + 1));
        }
        // Otherwise (rotating spoofed addresses) per-client tracking is skipped; the invitation cap still holds.
        invitations.put(invitationKey, new Window(invitation.start(), invitation.count() + 1));
    }

    private boolean hasRoomForClient(Instant now) {
        if (clients.size() < properties.maxTrackedClients()) return true;
        clients.values().removeIf(window -> expired(window, now, properties.clientWindow()));
        invitations.values().removeIf(window -> expired(window, now, properties.invitationWindow()));
        return clients.size() < properties.maxTrackedClients();
    }

    private static Window current(Window window, Instant now, Duration length) {
        return window == null || expired(window, now, length) ? new Window(now, 0) : window;
    }

    private static boolean expired(Window window, Instant now, Duration length) {
        return !now.isBefore(window.start().plus(length));
    }

    private static TooManyRequestsException tooMany(Window window, Instant now, Duration length) {
        long seconds = Duration.between(now, window.start().plus(length)).toSeconds();
        return new TooManyRequestsException(Math.max(1, seconds));
    }
}
