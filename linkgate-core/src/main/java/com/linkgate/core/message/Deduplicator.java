package com.linkgate.core.message;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 消息去重：按 messageId 记录近期已投递的消息，越界按 LRU 淘汰。
 *
 * <p>线程安全（方法级同步）；容量有界，避免无限增长。</p>
 */
public final class Deduplicator {

    private final Map<String, Boolean> seen;

    public Deduplicator(int maxSize) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxSize must be > 0");
        }
        this.seen = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                return size() > maxSize;
            }
        };
    }

    /**
     * 首次见到该消息返回 {@code true}（可继续投递）；重复返回 {@code false}。
     */
    public synchronized boolean firstSeen(String messageId) {
        return seen.put(messageId, Boolean.TRUE) == null;
    }

    public synchronized boolean isDuplicate(String messageId) {
        return seen.containsKey(messageId);
    }

    public synchronized int size() {
        return seen.size();
    }
}
