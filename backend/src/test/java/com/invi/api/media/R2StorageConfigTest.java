package com.invi.api.media;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.mock.env.MockEnvironment;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class R2StorageConfigTest {
    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    void blankAccountDoesNotCreateStorageBeans(String account) {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.setEnvironment(new MockEnvironment()
                    .withProperty("invi.r2.account-id", account)
                    .withProperty("invi.r2.access-key-id", "test-key")
                    .withProperty("invi.r2.secret-access-key", "test-secret"));
            context.register(R2StorageConfig.class);
            context.refresh();
            assertThat(context.getBeansOfType(S3Client.class)).isEmpty();
            assertThat(context.getBeansOfType(S3Presigner.class)).isEmpty();
        }
    }

    @Test
    void missingCredentialsDoNotCreateStorageBeans() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.setEnvironment(new MockEnvironment().withProperty("invi.r2.account-id", "test-account"));
            context.register(R2StorageConfig.class);
            context.refresh();
            assertThat(context.getBeansOfType(S3Client.class)).isEmpty();
            assertThat(context.getBeansOfType(S3Presigner.class)).isEmpty();
        }
    }
}
