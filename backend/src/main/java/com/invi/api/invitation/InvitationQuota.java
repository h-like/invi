package com.invi.api.invitation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * How many invitations one account may keep. Signup is open in production and every invitation
 * can hold photos in R2, the only part of hosting that can be billed, so this caps one account's
 * share. Production sets it low (application-prod.yml); local development keeps room to try things.
 */
@Component
public class InvitationQuota {

    private final int maxPerMember;

    public InvitationQuota(@Value("${invi.invitation.max-per-member:20}") int maxPerMember) {
        if (maxPerMember < 1) {
            throw new IllegalStateException("Invalid configuration: invi.invitation.max-per-member");
        }
        this.maxPerMember = maxPerMember;
    }

    public int maxPerMember() {
        return maxPerMember;
    }
}
