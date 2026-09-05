package com.invi.api.account;

import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/** Reads the member id that JwtAuthenticationFilter put in Authentication#getName(). */
@Component
public class CurrentMember {

    public UUID requireId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("로그인이 필요합니다.");
        }
        return UUID.fromString(authentication.getName());
    }
}
