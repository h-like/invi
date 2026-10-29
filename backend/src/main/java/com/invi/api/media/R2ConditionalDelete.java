package com.invi.api.media;

import com.invi.api.common.StorageUnavailableException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/** Never assume an S3-compatible endpoint enforces If-Match on DELETE. */
@Component
public class R2ConditionalDelete {
    public void verifyCapability(S3Client client, String bucket) {
        // This private disposable namespace is never signed or exposed to invitation content.
        String key = "invi-cleanup-probes/" + UUID.randomUUID();
        try {
            client.putObject(PutObjectRequest.builder().bucket(bucket).key(key)
                    .contentType("application/octet-stream").build(),
                    RequestBody.fromBytes("invi cleanup capability check".getBytes(StandardCharsets.UTF_8)));
            String staleEtag = client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build()).eTag();
            if (staleEtag == null || staleEtag.isBlank()) throw unavailable();
            // Use a genuine stale ETag, not a fabricated header that could be rejected as malformed.
            client.putObject(PutObjectRequest.builder().bucket(bucket).key(key)
                    .contentType("application/octet-stream").build(),
                    RequestBody.fromBytes("invi cleanup capability check replacement".getBytes(StandardCharsets.UTF_8)));
            String etag = client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build()).eTag();
            if (etag == null || etag.isBlank() || etag.equals(staleEtag)) throw unavailable();
            boolean rejected = false;
            try {
                deleteIfUnchanged(client, bucket, key, staleEtag);
            } catch (S3Exception error) {
                if (error.statusCode() == 412) rejected = true;
                else throw error;
            }
            // A successful unconditional DELETE here destroyed ONLY the disposable probe.
            if (!rejected) throw unavailable();
            String after = client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build()).eTag();
            if (!etag.equals(after)) throw unavailable();
            deleteIfUnchanged(client, bucket, key, etag);
            boolean gone = false;
            try { client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build()); }
            catch (S3Exception error) {
                if (error.statusCode() == 404) gone = true;
                else throw error;
            }
            if (!gone) throw unavailable();
        } catch (RuntimeException error) {
            // No SDK messages/URLs/credentials in user-visible errors.
            throw unavailable();
        } finally {
            // Only the uniquely generated disposable probe may use unconditional deletion.
            try { client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build()); }
            catch (RuntimeException ignored) { /* Operator may remove the isolated probe namespace later. */ }
        }
    }

    public void deleteIfUnchanged(S3Client client, String bucket, String key, String etag) {
        if (etag == null || etag.isBlank()) throw unavailable();
        client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key)
                .overrideConfiguration(override -> override.putHeader("If-Match", etag)).build());
    }

    private static StorageUnavailableException unavailable() {
        return new StorageUnavailableException("저장소의 조건부 삭제 안전성을 확인하지 못해 실제 삭제를 중단했습니다. 점검 모드로 유지해 주세요.");
    }
}
