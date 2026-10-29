package com.invi.api.media;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Separate bean so every observation, claim and completion has its own committed transaction. */
@Service
public class MediaCleanupTransactions {
    public record Observation(String key, Instant modifiedAt, String etag) {
        public Observation {
            // LIST uses ISO timestamps, HEAD uses second-precision HTTP dates.
            if (modifiedAt != null) modifiedAt = modifiedAt.truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        }
    }
    public record Claim(String key, UUID token, boolean exists) {}
    public record Plan(int tracked, int referenced, int waiting, int eligible, int deleting, List<String> candidates) {}

    private final MediaMutationGuard guard;
    private final TrackedMediaObjectRepository objects;
    private final MediaAssetRepository assets;
    private final MediaReferences references;
    private final Clock clock;
    private final MediaCleanupProperties properties;
    private final String bucket;

    public MediaCleanupTransactions(MediaMutationGuard guard, TrackedMediaObjectRepository objects,
            MediaAssetRepository assets, MediaReferences references, Clock clock,
            MediaCleanupProperties properties, @Value("${invi.r2.bucket:invi-media}") String bucket) {
        this.guard = guard; this.objects = objects; this.assets = assets;
        this.references = references; this.clock = clock; this.properties = properties; this.bucket = bucket;
    }

    @Transactional
    public Plan observe(List<Observation> inventory, String prefix) {
        guard.lock();
        Instant now = clock.instant();
        Set<String> used = references.allKeys(); // Any failure aborts the entire plan, never a partial set.
        Map<String, TrackedMediaObject> tracked = new LinkedHashMap<>();
        for (TrackedMediaObject object : objects.findByBucketAndR2KeyStartingWith(bucket, prefix)) {
            if (managedInScope(object.getR2Key(), prefix)) tracked.put(object.getR2Key(), object);
        }
        List<TrackedMediaObject> discovered = new ArrayList<>();
        // Legacy attachments whose PUT never happened must also age through a fresh grace period.
        for (MediaAsset asset : assets.findAll()) {
            if (managedInScope(asset.getR2Key(), prefix)) {
                findOrDiscover(tracked, discovered, asset.getR2Key(), now);
            }
        }
        for (Observation observation : inventory) {
            if (!managedInScope(observation.key(), prefix)) continue;
            TrackedMediaObject object = findOrDiscover(tracked, discovered, observation.key(), now);
            object.observeObject(observation.modifiedAt(), observation.etag(), now);
        }
        int referenced = 0, waiting = 0, eligible = 0, deleting = 0;
        List<TrackedMediaObject> candidates = new ArrayList<>();
        for (TrackedMediaObject object : tracked.values()) {
            if (used.contains(object.getR2Key())) {
                object.protect(); referenced++;
                continue;
            }
            object.unused(now);
            if (object.getState() == MediaObjectState.DELETED) continue;
            if (object.getState() == MediaObjectState.DELETING) deleting++;
            if (object.eligible(now, properties.gracePeriod())) {
                eligible++;
                if (object.claimAvailable(now)) candidates.add(object);
            } else waiting++;
        }
        objects.saveAll(discovered);
        List<String> keys = candidates.stream()
                .sorted(Comparator.comparing(TrackedMediaObject::getUnreferencedSince).thenComparing(TrackedMediaObject::getR2Key))
                .limit(properties.batchSize()).map(TrackedMediaObject::getR2Key).toList();
        return new Plan(tracked.size(), referenced, waiting, eligible, deleting, keys);
    }

    private TrackedMediaObject findOrDiscover(Map<String, TrackedMediaObject> tracked,
            List<TrackedMediaObject> discovered, String key, Instant now) {
        return tracked.computeIfAbsent(key, ignored -> {
            TrackedMediaObject object = TrackedMediaObject.discovered(bucket, key, now, now.plus(Duration.ofHours(1)));
            discovered.add(object);
            return object;
        });
    }

    @Transactional
    public Claim claim(String key, Observation currentObject) {
        guard.lock();
        if (!MediaReferences.isManagedKey(key)) return null;
        TrackedMediaObject object = objects.findByBucketAndR2Key(bucket, key).orElse(null);
        if (object == null) return null;
        if (references.allKeys().contains(key)) { object.protect(); return null; }
        Instant now = clock.instant();
        if (currentObject != null) {
            if (!key.equals(currentObject.key()) || currentObject.modifiedAt() == null
                    || currentObject.etag() == null || currentObject.etag().isBlank()) return null;
            object.observeObject(currentObject.modifiedAt(), currentObject.etag(), now);
        }
        if (!object.eligible(now, properties.gracePeriod()) || !object.claimAvailable(now)) return null;
        UUID token = UUID.randomUUID();
        object.claim(token, now);
        return new Claim(key, token, currentObject != null);
    }

    @Transactional
    public boolean finish(Claim claim) {
        guard.lock();
        TrackedMediaObject object = objects.findByBucketAndR2Key(bucket, claim.key()).orElse(null);
        if (object == null || object.getState() != MediaObjectState.DELETING || !object.ownsClaim(claim.token())) return false;
        if (references.allKeys().contains(claim.key())) {
            throw new IllegalStateException("A persisted media reference bypassed the mutation guard.");
        }
        assets.deleteAll(assets.findByR2Key(claim.key()));
        // Retain a tombstone: an old unsaved editor must not restore a deleted URL.
        object.deleted(clock.instant());
        return true;
    }

    @Transactional
    public void failed(Claim claim) {
        guard.lock();
        objects.findByBucketAndR2Key(bucket, claim.key()).ifPresent(object -> {
            if (object.getState() == MediaObjectState.DELETING && object.ownsClaim(claim.token())) {
                // Keep the fence: an ambiguous storage failure may already have deleted the file.
                object.retryClaim(clock.instant());
            }
        });
    }

    private static boolean managedInScope(String key, String prefix) {
        return key != null && key.startsWith(prefix) && MediaReferences.isManagedKey(key);
    }
}
