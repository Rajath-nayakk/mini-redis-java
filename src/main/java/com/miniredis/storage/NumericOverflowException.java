package com.miniredis.storage;

/**
 * Thrown when an increment or decrement operation exceeds 64-bit integer limits.
 */
public class NumericOverflowException extends StorageException {
    public static final String DEFAULT_MESSAGE = "ERR increment or decrement would overflow";

    public NumericOverflowException() {
        super(DEFAULT_MESSAGE);
