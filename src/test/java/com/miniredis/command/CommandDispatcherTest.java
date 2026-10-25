package com.miniredis.command;

import com.miniredis.protocol.RespType;
import com.miniredis.protocol.RespValue;
import com.miniredis.storage.ConcurrentStorageEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CommandDispatcherTest {

    private CommandDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new CommandDispatcher(new ConcurrentStorageEngine());
    }

    private RespValue cmd(String... parts) {
        List<RespValue> list = new java.util.ArrayList<>();
        for (String p : parts) {
            list.add(RespValue.bulkString(p));
        }
        return RespValue.array(list);
    }
