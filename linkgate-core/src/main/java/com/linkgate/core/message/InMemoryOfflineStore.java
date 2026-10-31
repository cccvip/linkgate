package com.linkgate.core.message;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存版离线存储：每用户一个有界队列，超出容量丢弃最旧的消息。
 *
 * <p>线程安全；用于开发与单测，持久化版见后续 store 模块实现。</p>
 */
public final class InMemoryOfflineStore implements OfflineStore {

    private final int maxPerUser;
    private final Map<String, Deque<Message>> byUser = new ConcurrentHashMap<>();

    public InMemoryOfflineStore(int maxPerUser) {
        if (maxPerUser <= 0) {
            throw new IllegalArgumentException("maxPerUser must be > 0");
        }
        this.maxPerUser = maxPerUser;
    }

    @Override
    public void store(Message message) {
        Deque<Message> queue = byUser.computeIfAbsent(message.toUserId(), k -> new ArrayDeque<>());
        synchronized (queue) {
            while (queue.size() >= maxPerUser) {
                queue.pollFirst();
            }
            queue.addLast(message);
        }
    }

    @Override
    public List<Message> drain(String userId) {
        Deque<Message> queue = byUser.remove(userId);
        if (queue == null) {
            return List.of();
        }
        synchronized (queue) {
            return new ArrayList<>(queue);
        }
    }

    @Override
    public int size(String userId) {
        Deque<Message> queue = byUser.get(userId);
        if (queue == null) {
            return 0;
        }
        synchronized (queue) {
            return queue.size();
        }
    }
}
