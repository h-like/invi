package com.invi.api.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

class MediaMutationGuardTest {
    @Test
    void usesTransactionScopedPostgresLockWithAnIntegerResult() {
        EntityManager entityManager = mock(EntityManager.class);
        Query query = mock(Query.class);
        String sql = "select 1 from pg_advisory_xact_lock(528225105453241249)";
        when(entityManager.createNativeQuery(sql, Integer.class)).thenReturn(query);
        when(query.getSingleResult()).thenReturn(1);
        new MediaMutationGuard(entityManager).lock();
        verify(query).getSingleResult();
    }

    @Test
    void refusesToAcquireAnUnscopedAutocommitLock() throws Exception {
        Transactional annotation = MediaMutationGuard.class.getMethod("lock").getAnnotation(Transactional.class);
        assertThat(annotation.propagation()).isEqualTo(Propagation.MANDATORY);
    }
}
