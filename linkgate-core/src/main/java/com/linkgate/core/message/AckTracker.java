package com.linkgate.core.message;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * ACK 跟踪：为每条已投递消息挂一个超时，收到 ACK 则销账，超时未收到则触发重传（有次数上限）。
 */
public final class AckTracker {

    public static final int DEFAULT_MAX_ATTEMPTS = 3;

    private final RetryQueue retryQueue;
    private final long timeoutMillis;
    private final int maxAttempts;
    private final Consumer<Message> onTimeout;
    private final Map<String, Entry> pending = new ConcurrentHashMap<>();

    public AckTracker(RetryQueue retryQueue, long timeoutMillis, Consumer<Message> onTimeout) {
        this(retryQueue, timeoutMillis, DEFAULT_MAX_ATTEMPTS, onTimeout);
    }

    public AckTracker(RetryQueue retryQueue, long timeoutMillis, int maxAttempts, Consumer<Message> onTimeout) {
        if (timeoutMillis <= 0) {
            throw new IllegalArgumentException("timeoutMillis must be > 0");
        }
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts must be > 0");
        }
        this.retryQueue = retryQueue;
        this.timeoutMillis = timeoutMillis;
        this.maxAttempts = maxAttempts;
        this.onTimeout = onTimeout;
    }

    /** 开始跟踪一条消息；同一 messageId 重复跟踪只生效一次。 */
    public void track(Message message) {
        Entry entry = new Entry(message);
        if (pending.putIfAbsent(message.messageId(), entry) != null) {
            return;
        }
        entry.handle = schedule(message.messageId());
    }

    /** 收到 ACK：取消超时；返回此前是否在跟踪中。 */
    public boolean acknowledge(String messageId) {
        Entry entry = pending.remove(messageId);
        if (entry == null) {
            return false;
        }
        RetryQueue.RetryHandle handle = entry.handle;
        if (handle != null) {
            handle.cancel();
        }
        return true;
    }

    public int pendingCount() {
        return pending.size();
    }

    private RetryQueue.RetryHandle schedule(String messageId) {
        return retryQueue.schedule(() -> fireTimeout(messageId), timeoutMillis, TimeUnit.MILLISECONDS);
    }

    private void fireTimeout(String messageId) {
        Entry entry = pending.get(messageId);
        if (entry == null) {
            return;
        }
        if (entry.attempts >= maxAttempts) {
            pending.remove(messageId);
            return;
        }
        entry.attempts++;
        onTimeout.accept(entry.message);

        // 重传后若仍未被 ACK，再挂一次超时
        if (pending.get(messageId) == entry) {
            entry.handle = schedule(messageId);
        }
    }

    private static final class Entry {

        private final Message message;
        private volatile int attempts;
        private volatile RetryQueue.RetryHandle handle;

        private Entry(Message message) {
            this.message = message;
        }
    }
}
