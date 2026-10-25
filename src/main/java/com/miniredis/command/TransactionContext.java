package com.miniredis.command;

import com.miniredis.protocol.RespValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Encapsulates the per-client transaction state for MULTI / EXEC / DISCARD.
 */
public class TransactionContext {

    public static final int MAX_QUEUED_COMMANDS = 10_000;

    private boolean inTransaction = false;
    private final List<RespValue> queuedCommands = new ArrayList<>();

    public boolean isInTransaction() {
        return inTransaction;
    }

    public void startTransaction() {
        this.inTransaction = true;
        this.queuedCommands.clear();
    }

    public void discard() {
        this.inTransaction = false;
        this.queuedCommands.clear();
    }

    public void queue(RespValue command) {
        if (queuedCommands.size() >= MAX_QUEUED_COMMANDS) {
            throw new IllegalStateException("ERR transaction queue limit reached");
        }
        queuedCommands.add(command);
    }

    public List<RespValue> getQueuedCommands() {
        return Collections.unmodifiableList(queuedCommands);
    }

    public void reset() {
        this.inTransaction = false;
        this.queuedCommands.clear();
    }
}
