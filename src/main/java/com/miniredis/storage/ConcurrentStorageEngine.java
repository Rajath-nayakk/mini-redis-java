package com.miniredis.storage;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Thread-safe, non-blocking in-memory storage engine backed by ConcurrentHashMap.
