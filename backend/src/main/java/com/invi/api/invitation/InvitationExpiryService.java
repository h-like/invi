package com.invi.api.invitation;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Physically deletes expired free invitations with their RSVPs, guestbook (including soft-deleted
 * entries) and media rows. R2 files are not deleted here: their keys are released to the media
 * lifecycle, and the R2 cleanup job removes them after its own grace period.
 */
@Service
public class InvitationExpiryService {
    private static final Logger log = LoggerFactory.getLogger(InvitationExpiryService.class);

    /** Wedding dates are Korean calendar dates. */
    static final ZoneId WEDDING_ZONE = ZoneId.of("Asia/Seoul");

    private final InvitationExpiryTransactions transactions;
    private final InvitationExpiryProperties properties;
    private final Clock clock;

    public InvitationExpiryService(InvitationExpiryTransactions transactions, InvitationExpiryProperties properties,
            Clock clock) {
        this.transactions = transactions; this.properties = properties; this.clock = clock;
    }

    public InvitationExpiryReport run() {
        // A wedding on 6/12 with 10 retention days is kept through 6/22 and deleted from 6/23.
        LocalDate today = LocalDate.now(clock.withZone(WEDDING_ZONE));
        LocalDate weddingDateBefore = today.minusDays(properties.retentionDays());
        List<UUID> expired = transactions.findExpired(weddingDateBefore, properties.batchSize());
        if (properties.dryRun()) {
            return new InvitationExpiryReport(true, weddingDateBefore, expired, 0, List.of());
        }
        int deleted = 0;
        List<UUID> failed = new ArrayList<>();
        for (UUID id : expired) {
            try {
                if (transactions.deleteIfExpired(id, weddingDateBefore)) deleted++;
            } catch (RuntimeException error) {
                // Skip and retry next run; the id makes a stuck invitation findable without logging content.
                failed.add(id);
                log.warn("Invitation expiry skipped {} ({})", id, error.getClass().getSimpleName());
            }
        }
        return new InvitationExpiryReport(false, weddingDateBefore, expired, deleted, failed);
    }
}
