package com.linkgate.netty.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.linkgate.core.codec.JsonCodec;
import com.linkgate.core.message.InMemoryMessageLog;
import com.linkgate.core.message.MessageJournal;
import com.linkgate.core.message.Sequencer;
import com.linkgate.core.protocol.ChatDelivery;
import com.linkgate.core.protocol.ResumeRequest;
import com.linkgate.core.protocol.ResumeResponse;
import com.linkgate.core.session.Session;
import com.linkgate.core.session.SessionManager;
import com.linkgate.core.session.SessionState;
import com.linkgate.netty.protocol.CommandDispatcher;
import com.linkgate.netty.protocol.Frame;
import com.linkgate.netty.protocol.MsgType;
import com.linkgate.netty.server.ConnectionRegistry;
import com.linkgate.netty.session.ChannelMessageSender;
import com.linkgate.netty.session.ChannelSessionConnection;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

class ResumeHandlerTest {

    private final SessionManager sessionManager = new SessionManager();
    private final ConnectionRegistry registry = new ConnectionRegistry();
    private final MessageJournal journal = new MessageJournal(new Sequencer(), new InMemoryMessageLog(100));
    private final ChannelMessageSender sender = new ChannelMessageSender(registry);

    private EmbeddedChannel channel() {
        EmbeddedChannel ch = new EmbeddedChannel(new CommandDispatcher()
                .register(MsgType.RESUME, new ResumeHandler(sessionManager, registry, journal, sender)));
        registry.add(ch);
        return ch;
    }

    private void login(EmbeddedChannel ch, String sessionId, String userId) {
        Session session = sessionManager.create(sessionId, userId, "d1", new ChannelSessionConnection(ch));
        session.transitionTo(SessionState.AUTHENTICATED);
        registry.bindSession(ch, sessionId);
    }

    @Test
    void replaysMessagesAfterLastSeq() {
        EmbeddedChannel ch = channel();
        login(ch, "s1", "bob");
        journal.append("alice", "bob", JsonCodec.encode(new ChatDelivery("m1", null, "alice", "m1")));
        journal.append("alice", "bob", JsonCodec.encode(new ChatDelivery("m2", null, "alice", "m2")));
        journal.append("alice", "bob", JsonCodec.encode(new ChatDelivery("m3", null, "alice", "m3")));

        ch.writeInbound(Frame.of(MsgType.RESUME.code(), 9L, JsonCodec.encode(new ResumeRequest(1))));

        Frame first = ch.readOutbound();
        Frame second = ch.readOutbound();
        Frame ack = ch.readOutbound();
        assertNotNull(first);
        assertEquals(MsgType.CHAT.code(), first.type());
        assertEquals(2L, first.seq());
        assertEquals(3L, second.seq());
        assertEquals(MsgType.RESUME_ACK.code(), ack.type());

        ResumeResponse response = JsonCodec.decode(ack.body(), ResumeResponse.class);
        assertEquals(2, response.replayed());
        assertEquals(3L, response.latestSeq());

        ch.finishAndReleaseAll();
    }

    @Test
    void noGapReplaysNothing() {
        EmbeddedChannel ch = channel();
        login(ch, "s1", "bob");
        journal.append("alice", "bob", new byte[] {1});

        ch.writeInbound(Frame.of(MsgType.RESUME.code(), 2L, JsonCodec.encode(new ResumeRequest(5))));

        Frame ack = ch.readOutbound();
        assertEquals(MsgType.RESUME_ACK.code(), ack.type());
        assertEquals(0, JsonCodec.decode(ack.body(), ResumeResponse.class).replayed());

        ch.finishAndReleaseAll();
    }

    @Test
    void notLoggedInRepliesError() {
        EmbeddedChannel ch = channel();

        ch.writeInbound(Frame.of(MsgType.RESUME.code(), 1L, JsonCodec.encode(new ResumeRequest(0))));

        Frame reply = ch.readOutbound();
        assertEquals(MsgType.ERROR.code(), reply.type());

        ch.finishAndReleaseAll();
    }
}
