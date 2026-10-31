package com.linkgate.netty.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

class ConnectionRegistryTest {

    @Test
    void addTracksConnectionCount() {
        ConnectionRegistry registry = new ConnectionRegistry();
        EmbeddedChannel ch = new EmbeddedChannel();

        registry.add(ch);
        assertEquals(1, registry.connectionCount());

        ch.finishAndReleaseAll();
    }

    @Test
    void bindLinksChannelAndSession() {
        ConnectionRegistry registry = new ConnectionRegistry();
        EmbeddedChannel ch = new EmbeddedChannel();
        registry.add(ch);
        registry.bindSession(ch, "s1");

        assertEquals("s1", registry.sessionIdOf(ch).orElseThrow());
        assertSame(ch, registry.channelOf("s1").orElseThrow());
        assertEquals(1, registry.boundSessionCount());

        ch.finishAndReleaseAll();
    }

    @Test
    void rebindMovesMappingToNewChannel() {
        ConnectionRegistry registry = new ConnectionRegistry();
        EmbeddedChannel oldCh = new EmbeddedChannel();
        EmbeddedChannel newCh = new EmbeddedChannel();

        registry.bindSession(oldCh, "s1");
        registry.bindSession(newCh, "s1");

        assertSame(newCh, registry.channelOf("s1").orElseThrow());
        assertTrue(registry.sessionIdOf(oldCh).isEmpty(), "旧 channel 的映射应被清除");
        assertEquals("s1", registry.sessionIdOf(newCh).orElseThrow());

        oldCh.finishAndReleaseAll();
        newCh.finishAndReleaseAll();
    }

    @Test
    void unbindRemovesBothDirections() {
        ConnectionRegistry registry = new ConnectionRegistry();
        EmbeddedChannel ch = new EmbeddedChannel();
        registry.bindSession(ch, "s1");

        assertTrue(registry.unbind("s1"));
        assertTrue(registry.channelOf("s1").isEmpty());
        assertTrue(registry.sessionIdOf(ch).isEmpty());
        assertFalse(registry.unbind("s1"));

        ch.finishAndReleaseAll();
    }

    @Test
    void removeCleansChannelAndBinding() {
        ConnectionRegistry registry = new ConnectionRegistry();
        EmbeddedChannel ch = new EmbeddedChannel();
        registry.add(ch);
        registry.bindSession(ch, "s1");

        registry.remove(ch);

        assertEquals(0, registry.connectionCount());
        assertEquals(0, registry.boundSessionCount());
        assertTrue(registry.channelOf("s1").isEmpty());

        ch.finishAndReleaseAll();
    }
}
