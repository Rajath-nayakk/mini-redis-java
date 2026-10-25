package com.miniredis.storage;

/**
 * Thrown when an operation is performed against an incompatible data type.
 */
public class WrongTypeException extends StorageException {
    public static final String DEFAULT_MESSAGE = "WRONGTYPE Operation against a key holding the wrong kind of value";

    public WrongTypeException() {
        super(DEFAULT_MESSAGE);
    }

    public WrongTypeException(String message) {
        super(message);
    }
}
