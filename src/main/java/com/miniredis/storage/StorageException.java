package com.miniredis.storage;

/**
 * Base exception for storage engine operations.
 */
public class StorageException extends RuntimeException {
    public StorageException(String message) {
        super(message);
    }
}
