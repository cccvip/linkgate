package com.linkgate.core.message;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存版消息日志：每条流一个有序有界队列，超出容量丢弃最旧。
 *
 * <p>线程安全；用于开发与单测。</p>
 */
public final class InMemoryMessageLog implements MessageLog {

    private final int maxPerStream;
    private final Map<String, Deque<Message>> byStream = new ConcurrentHashMap<>();

    public InMemoryMessageLog(int maxPerStream) {
        if (maxPerStream <= 0) {
            throw new IllegalArgumentException("maxPerStream must be > 0");
        }
        this.maxPerStream = maxPerStream;
    }

    @Override
    public void append(String streamId, Message message) {
        Deque<Message> queue = byStream.computeIfAbsent(streamId, k -> new ArrayDeque<>());
        synchronized (queue) {
            while (queue.size() >= maxPerStream) {
                queue.pollFirst();
            }
            queue.addLast(message);
        }
    }

    @Override
    public List<Message> since(String streamId, long fromSeqExclusive) {
        Deque<Message> queue = byStream.get(streamId);
        if (queue == null) {
            return List.of();
        }
        synchronized (queue) {
            List<Message> result = new ArrayList<>();
            for (Message message : queue) {
                if (message.seq() > fromSeqExclusive) {
                    result.add(message);
                }
            }
            return result;
        }
    }

    @Override
    public long latestSeq(String streamId) {
        Deque<Message> queue = byStream.get(streamId);
        if (queue == null) {
            return 0L;
        }
        synchronized (queue) {
            Message last = queue.peekLast();
            return last == null ? 0L : last.seq();
        }
    }

    @Override
    public int size(String streamId) {
        Deque<Message> queue = byStream.get(streamId);
        if (queue == null) {
            return 0;
        }
        synchronized (queue) {
            return queue.size();
        }
    }
}
