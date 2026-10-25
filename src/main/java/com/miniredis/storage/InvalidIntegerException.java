package com.miniredis.storage;

/**
 * Thrown when an integer parsing operation fails.
 */
public class InvalidIntegerException extends StorageException {
    public static final String DEFAULT_MESSAGE = "ERR value is not an integer or out of range";

    public InvalidIntegerException() {
        super(DEFAULT_MESSAGE);
    }

    public InvalidIntegerException(String message) {
