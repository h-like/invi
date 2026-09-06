package com.invi.api.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET = "test-secret-key-at-least-32-bytes-long-0123456789";

    private final JwtService jwtService = new JwtService(SECRET, 60);

    @Test
    void issueThenParse_roundTripsTheSameMemberId() {
        UUID memberId = UUID.randomUUID();

        String token = jwtService.issue(memberId);

        assertThat(jwtService.parseMemberId(token)).contains(memberId);
    }

    @Test
    void parseMemberId_isEmpty_forGarbageToken() {
        assertThat(jwtService.parseMemberId("not-a-real-jwt")).isEmpty();
    }

    @Test
    void parseMemberId_isEmpty_forExpiredToken() {
        JwtService alreadyExpired = new JwtService(SECRET, -1);

        String token = alreadyExpired.issue(UUID.randomUUID());

        assertThat(jwtService.parseMemberId(token)).isEmpty();
    }

    @Test
    void parseMemberId_isEmpty_whenSignedWithADifferentSecret() {
        JwtService otherService = new JwtService("a-completely-different-secret-key-0123456789abcdef", 60);

        String token = otherService.issue(UUID.randomUUID());

        assertThat(jwtService.parseMemberId(token)).isEmpty();
    }
}
