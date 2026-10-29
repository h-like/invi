package com.invi.api.media;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** All asset-reference mutations and deletion claims take this lock before reading entities. */
@Component
@RequiredArgsConstructor
public class MediaMutationGuard {
    private final EntityManager entityManager;

    @Transactional(propagation = Propagation.MANDATORY)
    public void lock() {
        // PostgreSQL transaction-scoped advisory lock, shared by every app instance.
        // Select an integer rather than asking Hibernate to map PostgreSQL's void result.
        entityManager.createNativeQuery("select 1 from pg_advisory_xact_lock(528225105453241249)", Integer.class)
                .getSingleResult();
    }
}
