package com.invi.api.invitation;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Free invitations are deleted after weddingDate + retentionDays (Korea time). Dry-run until explicitly disabled. */
@ConfigurationProperties("invi.invitation.expiry")
public record InvitationExpiryProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("true") boolean dryRun,
        @DefaultValue("10") int retentionDays,
        @DefaultValue("100") int batchSize) {
    public InvitationExpiryProperties {
        if (retentionDays < 1 || retentionDays > 365) {
            throw new IllegalArgumentException("Invitation expiry retention-days must be between 1 and 365.");
        }
        if (batchSize < 1 || batchSize > 1000) {
            throw new IllegalArgumentException("Invalid invitation expiry batch size.");
        }
    }
}
