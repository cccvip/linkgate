package com.linkgate.netty.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

class ChannelSessionConnectionTest {

    @Test
    void reflectsChannelState() {
        EmbeddedChannel ch = new EmbeddedChannel();
        ChannelSessionConnection connection = new ChannelSessionConnection(ch);

        assertTrue(connection.isActive());
        assertEquals(ch.id().asLongText(), connection.connectionId());

        connection.close();
        ch.runPendingTasks();

        assertFalse(connection.isActive());
        ch.finishAndReleaseAll();
    }
}
