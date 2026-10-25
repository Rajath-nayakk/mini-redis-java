package com.miniredis.protocol;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Streaming RESP2 protocol parser.
 * Handles partial network reads, multi-command buffers, and frame boundary validation.
 */
public class RespParser {

    public static final int DEFAULT_MAX_BULK_STRING_LENGTH = 512 * 1024 * 1024; // 512MB
    public static final int DEFAULT_MAX_ARRAY_LENGTH = 1024 * 1024; // 1M elements

    private final int maxBulkStringLength;
    private final int maxArrayLength;

    private byte[] buffer = new byte[4096];
    private int readPos = 0;
    private int writePos = 0;

