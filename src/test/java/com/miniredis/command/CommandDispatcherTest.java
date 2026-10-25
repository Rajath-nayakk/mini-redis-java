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

    @Test
    void testPing() {
        RespValue r1 = dispatcher.dispatch(cmd("PING"));
        assertEquals(RespType.SIMPLE_STRING, r1.getType());
        assertEquals("PONG", ((RespValue.RespSimpleString) r1).value());

        RespValue r2 = dispatcher.dispatch(cmd("ping", "hello"));
        assertEquals(RespType.BULK_STRING, r2.getType());
        assertEquals("hello", ((RespValue.RespBulkString) r2).asUtf8String());

        RespValue r3 = dispatcher.dispatch(cmd("PING", "arg1", "arg2"));
        assertEquals(RespType.ERROR, r3.getType());
    }

    @Test
    void testEcho() {
        RespValue r1 = dispatcher.dispatch(cmd("ECHO", "test-message"));
        assertEquals(RespType.BULK_STRING, r1.getType());
        assertEquals("test-message", ((RespValue.RespBulkString) r1).asUtf8String());

        RespValue r2 = dispatcher.dispatch(cmd("ECHO"));
        assertEquals(RespType.ERROR, r2.getType());
    }

    @Test
    void testSetAndGet() {
        RespValue setResp = dispatcher.dispatch(cmd("SET", "mykey", "myval"));
        assertEquals(RespType.SIMPLE_STRING, setResp.getType());
        assertEquals("OK", ((RespValue.RespSimpleString) setResp).value());

        RespValue getResp = dispatcher.dispatch(cmd("GET", "mykey"));
        assertEquals(RespType.BULK_STRING, getResp.getType());
        assertEquals("myval", ((RespValue.RespBulkString) getResp).asUtf8String());

        RespValue missResp = dispatcher.dispatch(cmd("GET", "nonexistent"));
        assertEquals(RespType.BULK_STRING, missResp.getType());
        assertTrue(((RespValue.RespBulkString) missResp).isNull());
    }

    @Test
    void testDelAndExists() {
        dispatcher.dispatch(cmd("SET", "k1", "v1"));
        dispatcher.dispatch(cmd("SET", "k2", "v2"));

        RespValue ex1 = dispatcher.dispatch(cmd("EXISTS", "k1", "k2", "k3"));
