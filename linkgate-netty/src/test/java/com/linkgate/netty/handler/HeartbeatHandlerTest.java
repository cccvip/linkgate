package com.linkgate.netty.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.linkgate.core.codec.JsonCodec;
import com.linkgate.core.protocol.AckResponse;
import com.linkgate.core.session.Session;
import com.linkgate.core.session.SessionManager;
import com.linkgate.core.session.SessionState;
import com.linkgate.netty.protocol.CommandDispatcher;
import com.linkgate.netty.protocol.Frame;
import com.linkgate.netty.protocol.MsgType;
import com.linkgate.netty.server.ConnectionRegistry;
import com.linkgate.netty.session.ChannelSessionConnection;
import io.netty.channel.embedded.EmbeddedChannel;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class HeartbeatHandlerTest {

    private final SessionManager sessionManager = new SessionManager();
    private final ConnectionRegistry registry = new ConnectionRegistry();

    private EmbeddedChannel channel() {
        CommandDispatcher dispatcher = new CommandDispatcher()
                .register(MsgType.HEARTBEAT, new HeartbeatHandler(sessionManager, registry));
        EmbeddedChannel ch = new EmbeddedChannel(dispatcher);
        registry.add(ch);
        return ch;
    }

    private Session login(EmbeddedChannel ch, String sessionId, String userId) {
        Session session = sessionManager.create(sessionId, userId, "d1", new ChannelSessionConnection(ch));
        session.transitionTo(SessionState.AUTHENTICATED);
        registry.bindSession(ch, sessionId);
        return session;
    }

    @Test
    void heartbeatBeforeLoginRepliesError() {
        EmbeddedChannel ch = channel();

        ch.writeInbound(Frame.of(MsgType.HEARTBEAT.code(), 1L));

        Frame reply = ch.readOutbound();
        assertNotNull(reply);
        assertEquals(MsgType.ERROR.code(), reply.type());

        ch.finishAndReleaseAll();
    }

    @Test
    void heartbeatRefreshesSessionAndAcks() {
        EmbeddedChannel ch = channel();
        Session session = login(ch, "s1", "u1");
        Instant before = session.lastActiveAt();

        ch.writeInbound(Frame.of(MsgType.HEARTBEAT.code(), 2L));

        Frame reply = ch.readOutbound();
        assertNotNull(reply);
        assertEquals(MsgType.HEARTBEAT_ACK.code(), reply.type());
        assertEquals(2L, reply.seq());
        assertEquals("OK", JsonCodec.decode(reply.body(), AckResponse.class).status());
        assertFalse(session.lastActiveAt().isBefore(before));

        ch.finishAndReleaseAll();
    }
}
