package com.miniredis.storage;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Represents a Redis List value type.
 * Thread-safety is guaranteed through synchronized methods.
 */
public final class ListValue implements RedisValue {

    private final List<String> elements;

    public ListValue() {
        this.elements = new LinkedList<>();
    }

    public ListValue(Collection<String> initialElements) {
        this.elements = new LinkedList<>(initialElements);
    }

    public synchronized int lpush(List<String> values) {
        for (String v : values) {
            elements.add(0, v);
        }
        return elements.size();
    }

    public synchronized int rpush(List<String> values) {
        elements.addAll(values);
        return elements.size();
    }

    public synchronized String lpop() {
        if (elements.isEmpty()) {
            return null;
        }
        return elements.remove(0);
    }

    public synchronized String rpop() {
        if (elements.isEmpty()) {
            return null;
        }
        return elements.remove(elements.size() - 1);
    }

    public synchronized int size() {
        return elements.size();
    }

    public synchronized boolean isEmpty() {
        return elements.isEmpty();
    }

    public synchronized String get(long index) {
        int size = elements.size();
        if (index < 0) {
            index = size + index;
        }
        if (index < 0 || index >= size) {
            return null;
        }
        return elements.get((int) index);
    }

    public synchronized void set(long index, String value) {
        int size = elements.size();
        if (index < 0) {
            index = size + index;
        }
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("ERR index out of range");
        }
        elements.set((int) index, value);
    }

    public synchronized List<String> range(long start, long stop) {
        int size = elements.size();
        if (size == 0) {
            return Collections.emptyList();
        }

        if (start < 0) {
            start = size + start;
        }
        if (stop < 0) {
            stop = size + stop;
        }

        if (start < 0) {
            start = 0;
        }
        if (stop >= size) {
            stop = size - 1;
        }

        if (start > stop || start >= size) {
            return Collections.emptyList();
        }

        return new ArrayList<>(elements.subList((int) start, (int) stop + 1));
    }

    public synchronized void trim(long start, long stop) {
        int size = elements.size();
        if (size == 0) {
            return;
        }

        if (start < 0) {
            start = size + start;
        }
        if (stop < 0) {
            stop = size + stop;
        }

        if (start < 0) {
            start = 0;
        }
        if (stop >= size) {
            stop = size - 1;
        }

        if (start > stop || start >= size) {
            elements.clear();
            return;
        }

        List<String> retained = new ArrayList<>(elements.subList((int) start, (int) stop + 1));
        elements.clear();
        elements.addAll(retained);
    }

    @Override
    public RedisType getType() {
        return RedisType.LIST;
    }

    @Override
    public synchronized long estimatedMemoryBytes() {
        long bytes = 32L;
        for (String s : elements) {
            bytes += 24L + s.getBytes(StandardCharsets.UTF_8).length;
        }
        return bytes;
    }

    public synchronized List<String> toList() {
        return new ArrayList<>(elements);
    }
}
