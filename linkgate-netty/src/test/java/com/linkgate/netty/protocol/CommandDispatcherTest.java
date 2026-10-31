package com.linkgate.netty.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.netty.channel.embedded.EmbeddedChannel;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class CommandDispatcherTest {

    @Test
    void dispatchesToRegisteredHandler() {
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<Frame> seen = new AtomicReference<>();
        CommandDispatcher dispatcher = new CommandDispatcher()
                .register(MsgType.CHAT, (ctx, frame) -> {
                    calls.incrementAndGet();
                    seen.set(frame);
                });

        EmbeddedChannel ch = new EmbeddedChannel(dispatcher);
        Frame frame = Frame.of(MsgType.CHAT.code(), 7L, new byte[] {1, 2});

        ch.writeInbound(frame);

        assertEquals(1, calls.get());
        assertEquals(frame, seen.get());
        ch.finishAndReleaseAll();
    }

    @Test
    void unknownTypeDoesNotThrow() {
        CommandDispatcher dispatcher = new CommandDispatcher()
                .register(MsgType.CHAT, (ctx, frame) -> {
                    throw new AssertionError("不应被调用");
                });

        EmbeddedChannel ch = new EmbeddedChannel(dispatcher);
        assertFalse(ch.writeInbound(Frame.of((byte) 99, 1L)));

        ch.finishAndReleaseAll();
    }

    @Test
    void knownTypeWithoutHandlerDoesNotThrow() {
        CommandDispatcher dispatcher = new CommandDispatcher();

        EmbeddedChannel ch = new EmbeddedChannel(dispatcher);
        assertFalse(ch.writeInbound(Frame.of(MsgType.CHAT.code(), 1L)));

        ch.finishAndReleaseAll();
    }
}
