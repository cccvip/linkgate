package com.linkgate.netty.server;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.linkgate.netty.protocol.Frame;
import com.linkgate.netty.protocol.MsgType;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class GatewayChannelInitializerTest {

    private final GatewayComponents components = new GatewayComponents();

    @AfterEach
    void tearDown() {
        components.close();
    }

    private EmbeddedChannel channelWithPipeline() {
        return new EmbeddedChannel(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) {
                GatewayChannelInitializer.initPipeline(ch.pipeline(), components);
            }
        });
    }

    @Test
    void pipelineContainsAllHandlers() {
        EmbeddedChannel ch = channelWithPipeline();
        List<String> names = ch.pipeline().names();

        for (String expected : List.of(
                "frameDecoder", "messageDecoder", "messageEncoder",
                "idleState", "idleHeartbeat", "connectionLimit", "lifecycle", "dispatcher")) {
            assertTrue(names.contains(expected), "缺少 handler: " + expected + " in " + names);
        }

        ch.finishAndReleaseAll();
    }

    @Test
    void outboundFrameIsEncoded() {
        EmbeddedChannel ch = channelWithPipeline();

        assertTrue(ch.writeOutbound(Frame.of(MsgType.HEARTBEAT.code(), 1L)));

        ByteBuf out = ch.readOutbound();
        assertNotNull(out);
        assertTrue(out.readableBytes() >= Frame.HEADER_LENGTH);
        out.release();

        ch.finishAndReleaseAll();
    }
}
