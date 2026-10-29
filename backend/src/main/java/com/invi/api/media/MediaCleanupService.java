package com.invi.api.media;

import com.invi.api.common.StorageUnavailableException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

/** Inventory I/O is outside DB locks; a committed deletion fence precedes every physical delete. */
@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class MediaCleanupService {
    private final ObjectProvider<S3Client> clientProvider;
    private final MediaCleanupTransactions transactions;
    private final MediaCleanupProperties properties;
    private final R2ConditionalDelete conditionalDelete;
    private final String bucket;

    public MediaCleanupService(ObjectProvider<S3Client> clientProvider, MediaCleanupTransactions transactions,
            MediaCleanupProperties properties, R2ConditionalDelete conditionalDelete,
            @Value("${invi.r2.bucket:invi-media}") String bucket) {
        this.clientProvider = clientProvider; this.transactions = transactions;
        this.properties = properties; this.conditionalDelete = conditionalDelete; this.bucket = bucket;
    }

    /** Caller checks invitation ownership. This endpoint can NEVER enable physical deletion. */
    public MediaCleanupReport preview(UUID invitationId) {
        return run("invitations/" + invitationId + "/", true);
    }

    public MediaCleanupReport scheduledRun() {
        if (!properties.enabled()) return report(true, 0, 0, new MediaCleanupTransactions.Plan(0, 0, 0, 0, 0, List.of()), 0, 0);
        return run("invitations/", properties.dryRun());
    }

    private MediaCleanupReport run(String prefix, boolean dryRun) {
        S3Client client = clientProvider.getIfAvailable();
        if (client == null || bucket.isBlank()) {
            throw new StorageUnavailableException("이미지 저장소가 설정되지 않아 정리를 점검할 수 없습니다.");
        }
        List<MediaCleanupTransactions.Observation> inventory = new ArrayList<>();
        Set<String> tokens = new HashSet<>();
        String token = null;
        int scanned = 0, skipped = 0;
        try {
            do {
                ListObjectsV2Response page = client.listObjectsV2(ListObjectsV2Request.builder()
                        .bucket(bucket).prefix(prefix).maxKeys(1000).continuationToken(token).build());
                if (page == null) throw new IllegalStateException("Missing R2 inventory response.");
                for (S3Object object : page.contents()) {
                    if (++scanned > properties.maxObjects()) {
                        throw new IllegalStateException("R2 inventory exceeds configured safe scan limit.");
                    }
                    if (object.key() == null || !object.key().startsWith(prefix)
                            || !MediaReferences.isManagedKey(object.key())) { skipped++; continue; }
                    if (object.lastModified() == null || object.eTag() == null || object.eTag().isBlank()) {
                        throw new IllegalStateException("Incomplete R2 object metadata.");
                    }
                    inventory.add(new MediaCleanupTransactions.Observation(object.key(), object.lastModified(), object.eTag()));
                }
                token = Boolean.TRUE.equals(page.isTruncated()) ? page.nextContinuationToken() : null;
                if (Boolean.TRUE.equals(page.isTruncated()) && (token == null || token.isBlank() || !tokens.add(token))) {
                    throw new IllegalStateException("Incomplete R2 pagination.");
                }
            } while (token != null);
        } catch (SdkException | IllegalStateException error) {
            // No planning/claim/deletion after an incomplete inventory. Never return a partial success.
            throw new StorageUnavailableException("저장소 목록을 완전히 확인하지 못해 정리를 중단했습니다. 설정·권한·목록 제한을 확인해 주세요.");
        }
        MediaCleanupTransactions.Plan plan = transactions.observe(inventory, prefix);
        if (dryRun) return report(true, scanned, skipped, plan, 0, 0);
        // Probe only a disposable object, before any managed object can be claimed/deleted.
        if (!plan.candidates().isEmpty()) conditionalDelete.verifyCapability(client, bucket);
        int deleted = 0, failed = 0;
        for (String key : plan.candidates()) {
            MediaCleanupTransactions.Claim claim = null;
            try {
                MediaCleanupTransactions.Observation latest = head(client, key);
                claim = transactions.claim(key, latest); // Rechecks all persisted references after HEAD.
                if (claim == null) continue;
                if (claim.exists()) {
                    conditionalDelete.deleteIfUnchanged(client, bucket, key, latest.etag());
                    if (head(client, key) != null) {
                        throw new IllegalStateException("Object still exists after DELETE; keep the deletion fence.");
                    }
                }
                if (transactions.finish(claim)) deleted++;
                else failed++;
            } catch (RuntimeException error) {
                failed++;
                if (claim != null) {
                    try { transactions.failed(claim); }
                    catch (RuntimeException ignored) { /* Committed DELETING fence remains for retry. */ }
                }
            }
        }
        return report(false, scanned, skipped, plan, deleted, failed);
    }

    private MediaCleanupTransactions.Observation head(S3Client client, String key) {
        try {
            HeadObjectResponse head = client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            if (head == null || head.lastModified() == null || head.eTag() == null || head.eTag().isBlank()) {
                throw new IllegalStateException("Incomplete HEAD metadata; refusing deletion.");
            }
            return new MediaCleanupTransactions.Observation(key, head.lastModified(), head.eTag());
        } catch (S3Exception error) {
            if (error.statusCode() == 404) return null;
            throw error;
        }
    }

    private MediaCleanupReport report(boolean dryRun, int scanned, int skipped, MediaCleanupTransactions.Plan plan,
            int deleted, int failed) {
        return new MediaCleanupReport(dryRun, properties.gracePeriod().toHours(), scanned, skipped,
                plan.tracked(), plan.referenced(), plan.waiting(), plan.eligible(), plan.deleting(), deleted, failed);
    }
}
