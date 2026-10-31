package com.linkgate.netty.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.linkgate.core.codec.JsonCodec;
import com.linkgate.core.protocol.AiStreamChunk;
import com.linkgate.core.protocol.AiStreamEnd;
import com.linkgate.netty.protocol.Frame;
import com.linkgate.netty.protocol.MsgType;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

class ChannelTokenSinkTest {

    @Test
    void writesChunksThenEndFrame() {
        EmbeddedChannel channel = new EmbeddedChannel();
        ChannelTokenSink sink = new ChannelTokenSink("s1", channel);

        sink.emit("Hel");
        sink.emit("lo");
        sink.complete();

        Frame first = channel.readOutbound();
        Frame second = channel.readOutbound();
        Frame end = channel.readOutbound();

        assertNotNull(first);
        assertEquals(MsgType.AI_STREAM.code(), first.type());
        assertEquals(0L, first.seq());
        AiStreamChunk chunk0 = JsonCodec.decode(first.body(), AiStreamChunk.class);
        assertEquals("s1", chunk0.streamId());
        assertEquals(0, chunk0.index());
        assertEquals("Hel", chunk0.token());

        assertEquals(1L, second.seq());
        assertEquals("lo", JsonCodec.decode(second.body(), AiStreamChunk.class).token());

        assertEquals(MsgType.AI_STREAM_END.code(), end.type());
        AiStreamEnd endBody = JsonCodec.decode(end.body(), AiStreamEnd.class);
        assertEquals("s1", endBody.streamId());
        assertEquals(2, endBody.totalTokens());

        channel.finishAndReleaseAll();
    }

    @Test
    void skipsWriteWhenChannelInactive() {
        EmbeddedChannel channel = new EmbeddedChannel();
        ChannelTokenSink sink = new ChannelTokenSink("s1", channel);
        channel.close();
        channel.runPendingTasks();

        sink.emit("Hel");
        sink.complete();

        assertNull(channel.readOutbound(), "断开后不再写帧");

        channel.finishAndReleaseAll();
    }
}
