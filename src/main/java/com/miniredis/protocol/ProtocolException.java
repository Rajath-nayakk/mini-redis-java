package com.miniredis.protocol;

/**
 * Thrown when incoming bytes violate the RESP protocol specification.
 */
public class ProtocolException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ProtocolException(String message) {
        super(message);
    }
}
