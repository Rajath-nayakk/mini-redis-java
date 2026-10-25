package com.miniredis.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class StorageEngineTest {

    private StorageEngine engine;

    @BeforeEach
    void setUp() {
        engine = new ConcurrentStorageEngine();
