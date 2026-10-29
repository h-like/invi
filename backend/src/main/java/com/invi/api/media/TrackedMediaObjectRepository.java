package com.invi.api.media;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrackedMediaObjectRepository extends JpaRepository<TrackedMediaObject, UUID> {
    Optional<TrackedMediaObject> findByBucketAndR2Key(String bucket, String r2Key);
    List<TrackedMediaObject> findByBucketAndR2KeyStartingWith(String bucket, String prefix);
}
