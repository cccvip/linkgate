package com.linkgate.netty.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.linkgate.netty.protocol.Frame;
import com.linkgate.netty.protocol.MsgType;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.timeout.IdleStateEvent;
import org.junit.jupiter.api.Test;

class IdleHeartbeatHandlerTest {

    @Test
    void readerIdleClosesConnection() {
        EmbeddedChannel ch = new EmbeddedChannel(new IdleHeartbeatHandler());

        ch.pipeline().fireUserEventTriggered(IdleStateEvent.READER_IDLE_STATE_EVENT);
        ch.runPendingTasks();

        assertFalse(ch.isActive(), "读空闲应关闭连接");
        ch.finishAndReleaseAll();
    }

    @Test
    void writerIdleSendsHeartbeatPing() {
        EmbeddedChannel ch = new EmbeddedChannel(new IdleHeartbeatHandler());

        ch.pipeline().fireUserEventTriggered(IdleStateEvent.WRITER_IDLE_STATE_EVENT);

        Frame ping = ch.readOutbound();
        assertNotNull(ping, "写空闲应下发心跳");
        assertEquals(MsgType.HEARTBEAT.code(), ping.type());

        ch.finishAndReleaseAll();
    }
}
