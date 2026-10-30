package com.invi.api.common;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Limits for public guest writes (RSVP, guestbook), counted per kind. The per-client limit is
 * best-effort because the client address can be spoofed behind some proxies; the per-invitation
 * limit is the hard ceiling. Guests at the venue often share one Wi-Fi or carrier NAT address,
 * so the per-client limit stays generous.
 */
@ConfigurationProperties("invi.rate-limit.guest-write")
public record GuestWriteRateLimitProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("10") int perClient,
        @DefaultValue("PT10M") Duration clientWindow,
        @DefaultValue("300") int perInvitation,
        @DefaultValue("PT1H") Duration invitationWindow,
        @DefaultValue("10000") int maxTrackedClients) {
    public GuestWriteRateLimitProperties {
        if (perClient < 1 || perInvitation < perClient || maxTrackedClients < 100) {
            throw new IllegalArgumentException("Invalid guest write rate limits.");
        }
        if (clientWindow == null || clientWindow.isNegative() || clientWindow.isZero()
                || invitationWindow == null || invitationWindow.isNegative() || invitationWindow.isZero()) {
            throw new IllegalArgumentException("Guest write rate-limit windows must be positive.");
        }
    }
}
