package com.invi.api.media;

import com.invi.api.common.ConflictException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Called inside the same transaction as each reference/attachment mutation. */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class MediaLifecycle {
    private final MediaMutationGuard guard;
    private final TrackedMediaObjectRepository objects;
    private final MediaReferences references;
    private final Clock clock;
    private final String bucket;

    public MediaLifecycle(MediaMutationGuard guard, TrackedMediaObjectRepository objects,
            MediaReferences references, Clock clock, @Value("${invi.r2.bucket:invi-media}") String bucket) {
        this.guard = guard; this.objects = objects; this.references = references;
        this.clock = clock; this.bucket = bucket;
    }

    public void lock() { guard.lock(); }

    public void issued(String key, Instant expiresAt) {
        objects.save(TrackedMediaObject.discovered(bucket, key, clock.instant(), expiresAt.plus(Duration.ofHours(1))));
    }

    public void assertUsable(Set<String> keys) {
        for (String key : keys) {
            objects.findByBucketAndR2Key(bucket, key).ifPresent(object -> {
                if (object.getState() != MediaObjectState.ACTIVE) {
                    throw new ConflictException("정리 중이거나 삭제된 사진이 포함되어 있습니다. 해당 사진을 다시 업로드해 주세요.");
                }
            });
        }
    }

    public void referencesChanged(Set<String> before, Set<String> after) {
        assertUsable(after);
        for (String key : after) objects.findByBucketAndR2Key(bucket, key).ifPresent(TrackedMediaObject::protect);
        Set<String> removed = new HashSet<>(before);
        removed.removeAll(after);
        Instant now = clock.instant();
        for (String key : removed) objects.findByBucketAndR2Key(bucket, key).ifPresent(object -> object.dropped(now));
    }

    public void attached(String key) {
        Instant now = clock.instant();
        TrackedMediaObject object = objects.findByBucketAndR2Key(bucket, key)
                .orElseGet(() -> objects.save(TrackedMediaObject.discovered(bucket, key, now, now.plus(Duration.ofHours(1)))));
        if (object.getState() != MediaObjectState.ACTIVE) {
            throw new ConflictException("정리 중이거나 삭제된 사진은 연결할 수 없습니다. 사진을 다시 업로드해 주세요.");
        }
        object.attached(now);
    }

    public void requestDeletion(String key) {
        if (!MediaReferences.isManagedKey(key)) {
            throw new ConflictException("안전하게 확인할 수 없는 이미지 키는 자동 정리하지 않습니다.");
        }
        if (references.allKeys().contains(key)) {
            throw new ConflictException("초안·공개 청첩장 또는 템플릿에서 사용하는 사진은 삭제할 수 없습니다. 먼저 사진 연결을 해제하고 저장해 주세요.");
        }
        Instant now = clock.instant();
        TrackedMediaObject object = objects.findByBucketAndR2Key(bucket, key)
                .orElseGet(() -> objects.save(TrackedMediaObject.discovered(bucket, key, now, now.plus(Duration.ofHours(1)))));
        object.dropped(now);
    }
}
