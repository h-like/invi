package com.invi.api.invitation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Free hosting sleeps when idle and runs no timers while asleep, so the short initial delay
 * (shortly after each wake-up) is the main trigger; the interval covers long awake periods.
 */
@Component
@ConditionalOnProperty(name = "invi.invitation.expiry.enabled", havingValue = "true", matchIfMissing = true)
public class InvitationExpiryJob {
    private static final Logger log = LoggerFactory.getLogger(InvitationExpiryJob.class);
    private final InvitationExpiryService expiry;

    public InvitationExpiryJob(InvitationExpiryService expiry) { this.expiry = expiry; }

    @Scheduled(fixedDelayString = "${invi.invitation.expiry.interval:PT3H}",
            initialDelayString = "${invi.invitation.expiry.initial-delay:PT2M}")
    public void run() {
        try {
            log.info("Invitation expiry: {}", expiry.run());
        } catch (RuntimeException error) {
            log.warn("Invitation expiry stopped safely ({})", error.getClass().getSimpleName());
        }
    }
}
