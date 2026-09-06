package com.invi.api.common;

import java.lang.reflect.Field;
import java.util.UUID;

/**
 * BaseEntity's id is only ever assigned by Hibernate on persist, so tests that need a
 * stable id on an unmanaged entity (e.g. to exercise an ownership check) set it via
 * reflection instead.
 */
public final class TestEntityIds {

    private TestEntityIds() {}

    public static void setId(Object entity, UUID id) {
        try {
            Field field = BaseEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
