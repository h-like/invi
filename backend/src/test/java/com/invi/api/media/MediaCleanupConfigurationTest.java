package com.invi.api.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class MediaCleanupConfigurationTest {
    private ApplicationContextRunner context() {
        return new ApplicationContextRunner()
                .withUserConfiguration(MediaCleanupConfiguration.class, MediaCleanupJob.class)
                .withBean(MediaCleanupService.class, () -> mock(MediaCleanupService.class));
    }

    @Test
    void safeDefaultsBindAndDoNotStartAutomaticJob() {
        context().run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(MediaCleanupProperties.class).hasSingleBean(Clock.class);
            MediaCleanupProperties properties = context.getBean(MediaCleanupProperties.class);
            assertThat(properties.enabled()).isFalse();
            assertThat(properties.dryRun()).isTrue();
            assertThat(properties.gracePeriod()).isEqualTo(Duration.ofDays(7));
            assertThat(context).doesNotHaveBean(MediaCleanupJob.class);
        });
    }

    @Test
    void enablingJobStillDefaultsToDryRun() {
        context().withPropertyValues("invi.r2.cleanup.enabled=true").run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(MediaCleanupJob.class);
            assertThat(context.getBean(MediaCleanupProperties.class).dryRun()).isTrue();
        });
    }

    @Test
    void physicalModeRequiresAnExplicitSeparateFlag() {
        context().withPropertyValues("invi.r2.cleanup.enabled=true", "invi.r2.cleanup.dry-run=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(MediaCleanupProperties.class).dryRun()).isFalse();
                });
    }

    @Test
    void unsafeShortGraceFailsConfigurationInsteadOfStarting() {
        context().withPropertyValues("invi.r2.cleanup.grace-period=PT1H").run(context -> assertThat(context).hasFailed());
    }
}
