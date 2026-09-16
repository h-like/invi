package com.invi.api.media;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Cloudflare R2 is S3-compatible, so the AWS SDK v2 S3 client/presigner work against it
 * unmodified once pointed at the account's R2 endpoint (region must be "auto" — R2 has no
 * regions of its own).
 *
 * Beans only materialize when invi.r2.account-id is set (application-local.yml, gitignored,
 * or env vars) — same pattern as SecurityConfig's ClientRegistrationRepository check, so the
 * app still boots without real R2 credentials (CI, contextLoads(), a fresh clone before
 * secrets are set up). MediaService takes these as ObjectProvider and throws
 * StorageUnavailableException on actual use instead.
 */
@Configuration
@ConditionalOnProperty(prefix = "invi.r2", name = "account-id")
public class R2StorageConfig {

    @Bean
    public S3Presigner r2Presigner(
            @Value("${invi.r2.account-id}") String accountId,
            @Value("${invi.r2.access-key-id}") String accessKeyId,
            @Value("${invi.r2.secret-access-key}") String secretAccessKey) {
        return S3Presigner.builder()
                .endpointOverride(endpoint(accountId))
                .region(Region.of("auto"))
                .credentialsProvider(credentials(accessKeyId, secretAccessKey))
                .build();
    }

    @Bean
    public S3Client r2Client(
            @Value("${invi.r2.account-id}") String accountId,
            @Value("${invi.r2.access-key-id}") String accessKeyId,
            @Value("${invi.r2.secret-access-key}") String secretAccessKey) {
        return S3Client.builder()
                .endpointOverride(endpoint(accountId))
                .region(Region.of("auto"))
                .credentialsProvider(credentials(accessKeyId, secretAccessKey))
                .build();
    }

    private static URI endpoint(String accountId) {
        return URI.create("https://" + accountId + ".r2.cloudflarestorage.com");
    }

    private static StaticCredentialsProvider credentials(String accessKeyId, String secretAccessKey) {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKeyId, secretAccessKey));
    }
}
