package com.linkgate.netty.server;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 连接数与建连速率限制。
 *
 * <p>两者都必须通过才放行：</p>
 * <ul>
 *   <li>连接数上限：当前活跃连接数不得超过 {@code maxConnections}；</li>
 *   <li>建连速率：令牌桶限速，桶容量为 1 秒的许可量，允许突发。</li>
 * </ul>
 */
public final class ConnectionLimiter {

    private final int maxConnections;
    private final AtomicInteger active = new AtomicInteger();
    private final TokenBucket bucket;

    public ConnectionLimiter(int maxConnections, double permitsPerSecond) {
        if (maxConnections <= 0) {
            throw new IllegalArgumentException("maxConnections must be > 0");
        }
        if (permitsPerSecond <= 0) {
            throw new IllegalArgumentException("permitsPerSecond must be > 0");
        }
        this.maxConnections = maxConnections;
        this.bucket = new TokenBucket(permitsPerSecond);
    }

    /** 尝试接纳一条新连接；成功返回 true，需在断开时 {@link #release()}。 */
    public boolean tryAcquire() {
        while (true) {
            int current = active.get();
            if (current >= maxConnections) {
                return false;
            }
            if (active.compareAndSet(current, current + 1)) {
                break;
            }
        }
        if (!bucket.tryAcquire()) {
            active.decrementAndGet();
            return false;
        }
        return true;
    }

    public void release() {
        active.updateAndGet(current -> current > 0 ? current - 1 : 0);
    }

    public int activeConnections() {
        return active.get();
    }

    public int maxConnections() {
        return maxConnections;
    }

    private static final class TokenBucket {

        private final double capacity;
        private final double refillPerNano;
        private double tokens;
        private long lastRefillNanos;

        TokenBucket(double permitsPerSecond) {
            this.capacity = Math.max(1.0, permitsPerSecond);
            this.refillPerNano = permitsPerSecond / 1_000_000_000.0;
            this.tokens = capacity;
            this.lastRefillNanos = System.nanoTime();
        }

        synchronized boolean tryAcquire() {
            long now = System.nanoTime();
            tokens = Math.min(capacity, tokens + (now - lastRefillNanos) * refillPerNano);
            lastRefillNanos = now;
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }
            return false;
        }
    }
}
