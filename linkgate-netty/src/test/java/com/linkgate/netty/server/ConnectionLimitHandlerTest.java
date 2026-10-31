package com.linkgate.netty.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

class ConnectionLimitHandlerTest {

    @Test
    void rejectsConnectionsOverLimit() {
        ConnectionLimiter limiter = new ConnectionLimiter(1, 1000);

        EmbeddedChannel first = new EmbeddedChannel(new ConnectionLimitHandler(limiter));
        assertTrue(first.isActive());
        assertEquals(1, limiter.activeConnections());

        EmbeddedChannel second = new EmbeddedChannel(new ConnectionLimitHandler(limiter));
        second.runPendingTasks();
        assertFalse(second.isActive(), "超出上限的连接应被关闭");
        assertEquals(1, limiter.activeConnections());

        first.close();
        first.runPendingTasks();
        assertEquals(0, limiter.activeConnections(), "断开后应归还许可");

        first.finishAndReleaseAll();
        second.finishAndReleaseAll();
    }
}
