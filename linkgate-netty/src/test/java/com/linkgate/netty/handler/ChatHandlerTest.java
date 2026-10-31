package com.linkgate.netty.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.linkgate.core.codec.JsonCodec;
import com.linkgate.core.message.AckTracker;
import com.linkgate.core.message.Deduplicator;
import com.linkgate.core.message.DeliveryService;
import com.linkgate.core.message.InMemoryMessageLog;
import com.linkgate.core.message.MessageJournal;
import com.linkgate.core.message.RetryQueue;
import com.linkgate.core.message.Sequencer;
import com.linkgate.core.protocol.AckResponse;
import com.linkgate.core.protocol.ChatDelivery;
import com.linkgate.core.protocol.ChatRequest;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ChatHandlerTest {

    private final SessionManager sessionManager = new SessionManager();
    private final ConnectionRegistry registry = new ConnectionRegistry();
    private final DeliveryService deliveryService =
            new DeliveryService(sessionManager, new ChannelMessageSender(registry));
    private final MessageJournal journal = new MessageJournal(new Sequencer(), new InMemoryMessageLog(100));
    private final RetryQueue retryQueue = new RetryQueue();
    private final AckTracker ackTracker = new AckTracker(retryQueue, 5000, 3, message -> {
    });
    private final Deduplicator deduplicator = new Deduplicator(1000);

    @AfterEach
    void tearDown() {
        retryQueue.close();
    }

    private void login(EmbeddedChannel ch, String sessionId, String userId) {
        Session session = sessionManager.create(sessionId, userId, "d1", new ChannelSessionConnection(ch));
        session.transitionTo(SessionState.AUTHENTICATED);
        registry.bindSession(ch, sessionId);
    }

    private EmbeddedChannel chatChannel() {
        EmbeddedChannel ch = new EmbeddedChannel(new CommandDispatcher()
                .register(MsgType.CHAT, new ChatHandler(
                        sessionManager, registry, deliveryService, journal, deduplicator, ackTracker)));
        registry.add(ch);
        return ch;
    }

    @Test
    void deliversToTargetAndAcks() {
        EmbeddedChannel alice = chatChannel();
        EmbeddedChannel bob = new EmbeddedChannel();
        registry.add(bob);
        login(alice, "sa", "alice");
        login(bob, "sb", "bob");

        alice.writeInbound(Frame.of(MsgType.CHAT.code(), 3L, JsonCodec.encode(new ChatRequest("bob", "hello"))));

        Frame delivered = bob.readOutbound();
        assertNotNull(delivered);
        assertEquals(MsgType.CHAT.code(), delivered.type());
        ChatDelivery body = JsonCodec.decode(delivered.body(), ChatDelivery.class);
        assertEquals("alice", body.fromUserId());
        assertEquals("hello", body.text());

        Frame ack = alice.readOutbound();
        assertEquals(MsgType.CHAT_ACK.code(), ack.type());
        assertEquals(3L, ack.seq());
        assertEquals("DELIVERED", JsonCodec.decode(ack.body(), AckResponse.class).status());

        alice.finishAndReleaseAll();
        bob.finishAndReleaseAll();
    }

    @Test
    void duplicateMessageIdIsDeliveredOnce() {
        EmbeddedChannel alice = chatChannel();
        EmbeddedChannel bob = new EmbeddedChannel();
        registry.add(bob);
        login(alice, "sa", "alice");
        login(bob, "sb", "bob");

        ChatRequest request = new ChatRequest("idem-1", "bob", "hi");
        alice.writeInbound(Frame.of(MsgType.CHAT.code(), 1L, JsonCodec.encode(request)));
        alice.writeInbound(Frame.of(MsgType.CHAT.code(), 2L, JsonCodec.encode(request)));

        Frame ack1 = alice.readOutbound();
        Frame ack2 = alice.readOutbound();
        assertEquals("DELIVERED", JsonCodec.decode(ack1.body(), AckResponse.class).status());
        assertEquals("DUPLICATE", JsonCodec.decode(ack2.body(), AckResponse.class).status());

        assertNotNull(bob.readOutbound(), "首次应送达");
        assertNull(bob.readOutbound(), "重复不应再次送达");

        alice.finishAndReleaseAll();
        bob.finishAndReleaseAll();
    }

    @Test
    void offlineTargetStillAcksWithOfflineStatus() {
        EmbeddedChannel alice = chatChannel();
        login(alice, "sa", "alice");

        alice.writeInbound(Frame.of(MsgType.CHAT.code(), 4L, JsonCodec.encode(new ChatRequest("nobody", "hey"))));

        Frame ack = alice.readOutbound();
        assertEquals(MsgType.CHAT_ACK.code(), ack.type());
        assertEquals("OFFLINE", JsonCodec.decode(ack.body(), AckResponse.class).status());

        alice.finishAndReleaseAll();
    }

    @Test
    void senderNotLoggedInRepliesError() {
        EmbeddedChannel alice = chatChannel();

        alice.writeInbound(Frame.of(MsgType.CHAT.code(), 5L, JsonCodec.encode(new ChatRequest("bob", "hi"))));

        Frame reply = alice.readOutbound();
        assertEquals(MsgType.ERROR.code(), reply.type());
        assertNull(alice.readOutbound());

        alice.finishAndReleaseAll();
    }

    @Test
    void deliveriesToAllDevicesOfTarget() {
        EmbeddedChannel alice = chatChannel();
        EmbeddedChannel bobPhone = new EmbeddedChannel();
        EmbeddedChannel bobLaptop = new EmbeddedChannel();
        registry.add(bobPhone);
        registry.add(bobLaptop);
        login(alice, "sa", "alice");
        login(bobPhone, "s-bob-1", "bob");
        login(bobLaptop, "s-bob-2", "bob");

        alice.writeInbound(Frame.of(MsgType.CHAT.code(), 6L, JsonCodec.encode(new ChatRequest("bob", "multi"))));

        assertEquals("multi", JsonCodec.decode(bobPhone.<Frame>readOutbound().body(), ChatDelivery.class).text());
        assertEquals("multi", JsonCodec.decode(bobLaptop.<Frame>readOutbound().body(), ChatDelivery.class).text());

        alice.finishAndReleaseAll();
        bobPhone.finishAndReleaseAll();
        bobLaptop.finishAndReleaseAll();
    }
}
