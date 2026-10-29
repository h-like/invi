package com.invi.api.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MediaCleanupPropertiesTest {
    @ParameterizedTest
    @ValueSource(longs = {-1, 0, 1, 23})
    void rejectsGraceBelowTwentyFourHours(long hours) {
        assertThatThrownBy(() -> new MediaCleanupProperties(false, true, Duration.ofHours(hours), 10000, 100))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsGraceOneNanosecondBelowTwentyFourHours() {
        assertThatThrownBy(() -> new MediaCleanupProperties(false, true,
                Duration.ofDays(1).minusNanos(1), 10000, 100))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingGrace() {
        assertThatThrownBy(() -> new MediaCleanupProperties(false, true, null, 10000, 100))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 100001, Integer.MAX_VALUE})
    void rejectsInvalidInventoryLimits(int maxObjects) {
        assertThatThrownBy(() -> new MediaCleanupProperties(false, true, Duration.ofDays(7), maxObjects, 100))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 1001, Integer.MAX_VALUE})
    void rejectsInvalidBatchLimits(int batchSize) {
        assertThatThrownBy(() -> new MediaCleanupProperties(false, true, Duration.ofDays(7), 10000, batchSize))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsSevenDaySafetySettingsWithoutChangingFlagsOrLimits() {
        MediaCleanupProperties properties = new MediaCleanupProperties(false, true, Duration.ofDays(7), 10000, 100);
        assertThat(properties.enabled()).isFalse();
        assertThat(properties.dryRun()).isTrue();
        assertThat(properties.gracePeriod()).isEqualTo(Duration.ofDays(7));
        assertThat(properties.maxObjects()).isEqualTo(10000);
        assertThat(properties.batchSize()).isEqualTo(100);
    }

    @Test
    void acceptsExactMinimumGraceAndInclusiveLimitBoundaries() {
        MediaCleanupProperties minimum = new MediaCleanupProperties(true, false, Duration.ofDays(1), 1, 1);
        MediaCleanupProperties maximum = new MediaCleanupProperties(true, false, Duration.ofDays(7), 100000, 1000);
        assertThat(minimum.gracePeriod()).isEqualTo(Duration.ofDays(1));
        assertThat(minimum.maxObjects()).isEqualTo(1);
        assertThat(minimum.batchSize()).isEqualTo(1);
        assertThat(maximum.maxObjects()).isEqualTo(100000);
        assertThat(maximum.batchSize()).isEqualTo(1000);
        assertThat(maximum.enabled()).isTrue();
        assertThat(maximum.dryRun()).isFalse();
    }
}
