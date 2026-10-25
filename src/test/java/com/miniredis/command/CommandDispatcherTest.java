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
        assertEquals(RespType.INTEGER, ex1.getType());
        assertEquals(2L, ((RespValue.RespInteger) ex1).value());

        RespValue delResp = dispatcher.dispatch(cmd("DEL", "k1", "k3"));
        assertEquals(RespType.INTEGER, delResp.getType());
        assertEquals(1L, ((RespValue.RespInteger) delResp).value());

        RespValue ex2 = dispatcher.dispatch(cmd("EXISTS", "k1"));
        assertEquals(0L, ((RespValue.RespInteger) ex2).value());
    }

    @Test
    void testDbsizeAndFlushDb() {
        assertEquals(0L, ((RespValue.RespInteger) dispatcher.dispatch(cmd("DBSIZE"))).value());
        dispatcher.dispatch(cmd("SET", "a", "1"));
        dispatcher.dispatch(cmd("SET", "b", "2"));
        assertEquals(2L, ((RespValue.RespInteger) dispatcher.dispatch(cmd("DBSIZE"))).value());

        dispatcher.dispatch(cmd("FLUSHDB"));
        assertEquals(0L, ((RespValue.RespInteger) dispatcher.dispatch(cmd("DBSIZE"))).value());
    }

    @Test
    void testIncrAndDecr() {
        assertEquals(1L, ((RespValue.RespInteger) dispatcher.dispatch(cmd("INCR", "num"))).value());
        assertEquals(2L, ((RespValue.RespInteger) dispatcher.dispatch(cmd("INCR", "num"))).value());
        assertEquals(7L, ((RespValue.RespInteger) dispatcher.dispatch(cmd("INCRBY", "num", "5"))).value());
        assertEquals(6L, ((RespValue.RespInteger) dispatcher.dispatch(cmd("DECR", "num"))).value());
        assertEquals(2L, ((RespValue.RespInteger) dispatcher.dispatch(cmd("DECRBY", "num", "4"))).value());
    }

    @Test
    void testInvalidIntegerErrors() {
        dispatcher.dispatch(cmd("SET", "nan", "text"));
        RespValue err = dispatcher.dispatch(cmd("INCR", "nan"));
        assertEquals(RespType.ERROR, err.getType());
        assertTrue(((RespValue.RespError) err).message().contains("not an integer"));

        RespValue err2 = dispatcher.dispatch(cmd("INCRBY", "key", "notanumber"));
        assertEquals(RespType.ERROR, err2.getType());
    }

    @Test
    void testNumericOverflowErrors() {
        dispatcher.dispatch(cmd("SET", "big", String.valueOf(Long.MAX_VALUE)));
        RespValue err = dispatcher.dispatch(cmd("INCR", "big"));
        assertEquals(RespType.ERROR, err.getType());
        assertTrue(((RespValue.RespError) err).message().contains("overflow"));
    }

    @Test
    void testTtlCommands() {
        dispatcher.dispatch(cmd("SET", "temp", "val"));
        assertEquals(-1L, ((RespValue.RespInteger) dispatcher.dispatch(cmd("TTL", "temp"))).value());

        assertEquals(1L, ((RespValue.RespInteger) dispatcher.dispatch(cmd("EXPIRE", "temp", "100"))).value());
        assertTrue(((RespValue.RespInteger) dispatcher.dispatch(cmd("TTL", "temp"))).value() > 0);

        assertEquals(1L, ((RespValue.RespInteger) dispatcher.dispatch(cmd("PERSIST", "temp"))).value());
        assertEquals(-1L, ((RespValue.RespInteger) dispatcher.dispatch(cmd("TTL", "temp"))).value());

        RespValue setex = dispatcher.dispatch(cmd("SETEX", "temp2", "60", "v2"));
        assertEquals("OK", ((RespValue.RespSimpleString) setex).value());
        assertTrue(((RespValue.RespInteger) dispatcher.dispatch(cmd("TTL", "temp2"))).value() > 0);
    }

    @Test
    void testListCommands() {
        assertEquals(2L, ((RespValue.RespInteger) dispatcher.dispatch(cmd("RPUSH", "mylist", "a", "b"))).value());
        assertEquals(3L, ((RespValue.RespInteger) dispatcher.dispatch(cmd("LPUSH", "mylist", "first"))).value());
        assertEquals(3L, ((RespValue.RespInteger) dispatcher.dispatch(cmd("LLEN", "mylist"))).value());

        RespValue lrange = dispatcher.dispatch(cmd("LRANGE", "mylist", "0", "-1"));
        assertEquals(RespType.ARRAY, lrange.getType());
        assertEquals(3, ((RespValue.RespArray) lrange).elements().size());

        RespValue lpop = dispatcher.dispatch(cmd("LPOP", "mylist"));
        assertEquals("first", ((RespValue.RespBulkString) lpop).asUtf8String());
    }

    @Test
    void testSetCommands() {
        assertEquals(2L, ((RespValue.RespInteger) dispatcher.dispatch(cmd("SADD", "myset", "x", "y"))).value());
        assertEquals(1L, ((RespValue.RespInteger) dispatcher.dispatch(cmd("SISMEMBER", "myset", "x"))).value());
        assertEquals(0L, ((RespValue.RespInteger) dispatcher.dispatch(cmd("SISMEMBER", "myset", "z"))).value());
        assertEquals(2L, ((RespValue.RespInteger) dispatcher.dispatch(cmd("SCARD", "myset"))).value());
        assertEquals(1L, ((RespValue.RespInteger) dispatcher.dispatch(cmd("SREM", "myset", "x"))).value());
        assertEquals(1L, ((RespValue.RespInteger) dispatcher.dispatch(cmd("SCARD", "myset"))).value());
    }

    @Test
    void testHashCommands() {
        assertEquals(2L, ((RespValue.RespInteger) dispatcher.dispatch(cmd("HSET", "myhash", "f1", "v1", "f2", "v2"))).value());
        assertEquals("v1", ((RespValue.RespBulkString) dispatcher.dispatch(cmd("HGET", "myhash", "f1"))).asUtf8String());
        assertEquals(1L, ((RespValue.RespInteger) dispatcher.dispatch(cmd("HEXISTS", "myhash", "f2"))).value());
        assertEquals(2L, ((RespValue.RespInteger) dispatcher.dispatch(cmd("HLEN", "myhash"))).value());
        assertEquals(10L, ((RespValue.RespInteger) dispatcher.dispatch(cmd("HINCRBY", "myhash", "num", "10"))).value());
    }

    @Test
    void testTransactions() {
        TransactionContext tx = new TransactionContext();
        assertEquals("OK", ((RespValue.RespSimpleString) dispatcher.dispatch(cmd("MULTI"), tx, true)).value());
        assertEquals("QUEUED", ((RespValue.RespSimpleString) dispatcher.dispatch(cmd("SET", "k", "v"), tx, true)).value());
        assertEquals("QUEUED", ((RespValue.RespSimpleString) dispatcher.dispatch(cmd("GET", "k"), tx, true)).value());

        RespValue exec = dispatcher.dispatch(cmd("EXEC"), tx, true);
        assertEquals(RespType.ARRAY, exec.getType());
        List<RespValue> results = ((RespValue.RespArray) exec).elements();
        assertEquals(2, results.size());
        assertEquals("OK", ((RespValue.RespSimpleString) results.get(0)).value());
        assertEquals("v", ((RespValue.RespBulkString) results.get(1)).asUtf8String());
    }

    @Test
    void testAuthentication() {
        dispatcher.setRequirePass("secret123");
        TransactionContext tx = new TransactionContext();

        // Any command before auth should be rejected with NOAUTH
        RespValue rejected = dispatcher.dispatch(cmd("GET", "foo"), tx, false);
        assertEquals(RespType.ERROR, rejected.getType());
        assertTrue(((RespValue.RespError) rejected).message().contains("NOAUTH"));

        // Wrong password
        RespValue badAuth = dispatcher.dispatch(cmd("AUTH", "wrong"), tx, false);
        assertEquals(RespType.ERROR, badAuth.getType());
        assertTrue(((RespValue.RespError) badAuth).message().contains("invalid password"));

        // Good password
        RespValue goodAuth = dispatcher.dispatch(cmd("AUTH", "secret123"), tx, false);
        assertEquals(RespType.SIMPLE_STRING, goodAuth.getType());
        assertEquals("OK", ((RespValue.RespSimpleString) goodAuth).value());
    }

    @Test
    void testUnknownCommandAndWrongArgs() {
        RespValue unk = dispatcher.dispatch(cmd("UNKNOWNCMD", "arg"));
        assertEquals(RespType.ERROR, unk.getType());
        assertTrue(((RespValue.RespError) unk).message().contains("unknown command"));

        RespValue wrongArgs = dispatcher.dispatch(cmd("GET", "k1", "k2"));
        assertEquals(RespType.ERROR, wrongArgs.getType());
        assertTrue(((RespValue.RespError) wrongArgs).message().contains("wrong number of arguments"));
    }
}
