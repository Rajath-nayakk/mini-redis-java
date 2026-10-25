package com.miniredis.storage;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Represents a Redis Set value type.
 * Thread-safety is guaranteed through synchronized methods.
 */
public final class SetValue implements RedisValue {

    private final Set<String> members;

    public SetValue() {
        this.members = new LinkedHashSet<>();
    }

    public SetValue(Collection<String> initialMembers) {
        this.members = new LinkedHashSet<>(initialMembers);
    }

    public synchronized int sadd(List<String> newMembers) {
        int added = 0;
        for (String m : newMembers) {
            if (members.add(m)) {
                added++;
            }
        }
        return added;
    }

    public synchronized int srem(List<String> toRemove) {
        int removed = 0;
        for (String m : toRemove) {
            if (members.remove(m)) {
                removed++;
            }
        }
        return removed;
    }

    public synchronized boolean sismember(String member) {
        return members.contains(member);
    }

    public synchronized Set<String> smembers() {
        return new LinkedHashSet<>(members);
    }

    public synchronized int scard() {
        return members.size();
    }

    public synchronized boolean isEmpty() {
        return members.isEmpty();
    }

    @Override
    public RedisType getType() {
        return RedisType.SET;
    }

    @Override
    public synchronized long estimatedMemoryBytes() {
        long bytes = 32L;
        for (String m : members) {
            bytes += 32L + m.getBytes(StandardCharsets.UTF_8).length;
        }
        return bytes;
    }
}
