package com.invi.api.media;

import com.invi.api.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "tracked_media_object", uniqueConstraints = @UniqueConstraint(columnNames = {"bucket", "r2_key"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrackedMediaObject extends BaseEntity {
    @Column(nullable = false)
    private String bucket;
    @Column(name = "r2_key", nullable = false, length = 512)
    private String r2Key;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MediaObjectState state;
    private Instant unreferencedSince;
    @Column(nullable = false)
    private Instant safeAfter;
    private Instant objectModifiedAt;
    private String objectEtag;
    private Instant deletedAt;
    private UUID deletionClaimId;
    private Instant claimExpiresAt;

    public static TrackedMediaObject discovered(String bucket, String key, Instant now, Instant safeAfter) {
        TrackedMediaObject object = new TrackedMediaObject();
        object.bucket = bucket; object.r2Key = key; object.state = MediaObjectState.ACTIVE;
        object.unreferencedSince = now; object.safeAfter = safeAfter;
        return object;
    }

    public void protect() {
        // Persisted references always win over an unfinished deletion claim.
        state = MediaObjectState.ACTIVE; unreferencedSince = null; deletedAt = null;
        deletionClaimId = null; claimExpiresAt = null;
    }
    public void unused(Instant now) {
        if (unreferencedSince == null) unreferencedSince = now;
    }
    public void dropped(Instant now) {
        if (state == MediaObjectState.ACTIVE) unreferencedSince = now;
    }
    public void attached(Instant now) {
        if (state != MediaObjectState.ACTIVE) throw new IllegalStateException("Retired media cannot be attached.");
        // A late attach/retry gets a fresh full grace period for the editor to save.
        unreferencedSince = now;
    }
    public void observeObject(Instant modifiedAt, String etag, Instant now) {
        if (!Objects.equals(objectModifiedAt, modifiedAt) || !Objects.equals(objectEtag, etag)) {
            // Overwrites, including the first PUT after a presign receipt, restart grace.
            unreferencedSince = now;
            if (state == MediaObjectState.DELETED) { state = MediaObjectState.DELETING; deletedAt = null; }
        }
        objectModifiedAt = modifiedAt; objectEtag = etag;
    }
    public boolean eligible(Instant now, Duration grace) {
        return state != MediaObjectState.DELETED && unreferencedSince != null
                && !now.isBefore(unreferencedSince.plus(grace)) && !now.isBefore(safeAfter)
                && (objectModifiedAt == null || !now.isBefore(objectModifiedAt.plus(grace)));
    }
    public boolean claimAvailable(Instant now) {
        return deletionClaimId == null || claimExpiresAt == null || !now.isBefore(claimExpiresAt);
    }
    public void claim(UUID claimId, Instant now) {
        state = MediaObjectState.DELETING; deletionClaimId = claimId;
        claimExpiresAt = now.plus(Duration.ofMinutes(30));
    }
    public boolean ownsClaim(UUID claimId) { return Objects.equals(deletionClaimId, claimId); }
    public void retryClaim(Instant now) { claimExpiresAt = now; }
    public void deleted(Instant now) { state = MediaObjectState.DELETED; deletedAt = now; claimExpiresAt = null; }
}
