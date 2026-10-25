package com.miniredis.storage;

/**
 * Base exception for storage engine operations.
 */
public class StorageException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public StorageException(String message) {
        super(message);
    }
}
