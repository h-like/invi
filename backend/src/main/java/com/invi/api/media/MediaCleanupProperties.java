package com.invi.api.media;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("invi.r2.cleanup")
public record MediaCleanupProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("true") boolean dryRun,
        @DefaultValue("P7D") Duration gracePeriod,
        @DefaultValue("10000") int maxObjects,
        @DefaultValue("100") int batchSize) {
    public MediaCleanupProperties {
        if (gracePeriod == null || gracePeriod.compareTo(Duration.ofDays(1)) < 0) {
            throw new IllegalArgumentException("Media cleanup grace-period must be at least 24 hours.");
        }
        if (maxObjects < 1 || maxObjects > 100000 || batchSize < 1 || batchSize > 1000) {
            throw new IllegalArgumentException("Invalid media cleanup inventory/batch limit.");
        }
    }
}
