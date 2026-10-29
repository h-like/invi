package com.invi.api.media;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "invi.r2.cleanup.enabled", havingValue = "true")
public class MediaCleanupJob {
    private static final Logger log = LoggerFactory.getLogger(MediaCleanupJob.class);
    private final MediaCleanupService cleanup;

    public MediaCleanupJob(MediaCleanupService cleanup) { this.cleanup = cleanup; }

    @Scheduled(fixedDelayString = "${invi.r2.cleanup.interval:PT1H}",
            initialDelayString = "${invi.r2.cleanup.initial-delay:PT5M}")
    public void run() {
        try {
            log.info("R2 media cleanup: {}", cleanup.scheduledRun());
        } catch (RuntimeException error) {
            // Do not log SDK exception bodies, object URLs, signed URLs or credentials.
            log.warn("R2 media cleanup stopped safely ({})", error.getClass().getSimpleName());
        }
    }
}
