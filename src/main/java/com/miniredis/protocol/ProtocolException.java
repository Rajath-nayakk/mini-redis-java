package com.miniredis.protocol;

/**
 * Thrown when incoming bytes violate the RESP protocol specification.
 */
public class ProtocolException extends RuntimeException {
    public ProtocolException(String message) {
        super(message);
    }
