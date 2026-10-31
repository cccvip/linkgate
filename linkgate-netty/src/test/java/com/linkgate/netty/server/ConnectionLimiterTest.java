package com.linkgate.netty.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ConnectionLimiterTest {

    @Test
    void enforcesMaxConnections() {
        ConnectionLimiter limiter = new ConnectionLimiter(2, 1000);

        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
        assertFalse(limiter.tryAcquire());
        assertEquals(2, limiter.activeConnections());

        limiter.release();
        assertTrue(limiter.tryAcquire());
        assertEquals(2, limiter.activeConnections());
    }

    @Test
    void enforcesRateLimit() {
        ConnectionLimiter limiter = new ConnectionLimiter(1000, 0.001);

        assertTrue(limiter.tryAcquire());
        assertFalse(limiter.tryAcquire(), "桶已空，立刻的第二条应被限速");
        assertEquals(1, limiter.activeConnections(), "被限速的连接不应计入活跃数");
    }

    @Test
    void releaseNeverGoesNegative() {
        ConnectionLimiter limiter = new ConnectionLimiter(10, 100);
        limiter.release();
        assertEquals(0, limiter.activeConnections());
    }

    @Test
    void rejectsInvalidConfig() {
        assertThrows(IllegalArgumentException.class, () -> new ConnectionLimiter(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new ConnectionLimiter(1, 0));
    }
}
