package com.invi.api.common;

/** Thrown when an R2-backed operation (media presign/delete) runs before invi.r2.* is configured. */
public class StorageUnavailableException extends RuntimeException {

    public StorageUnavailableException(String message) {
        super(message);
    }
}
