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
import com.linkgate.core.protocol.GroupChatRequest;
import com.linkgate.core.protocol.GroupJoinRequest;
import com.linkgate.core.protocol.MultiSyncRequest;
import com.linkgate.core.route.GroupFanout;
import com.linkgate.core.route.MultiDeviceSync;
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

class GroupAndMultiSyncHandlerTest {

    private final SessionManager sessionManager = new SessionManager();
    private final ConnectionRegistry registry = new ConnectionRegistry();
    private final DeliveryService deliveryService =
            new DeliveryService(sessionManager, new ChannelMessageSender(registry));
    private final MessageJournal journal = new MessageJournal(new Sequencer(), new InMemoryMessageLog(100));
    private final RetryQueue retryQueue = new RetryQueue();
    private final AckTracker ackTracker = new AckTracker(retryQueue, 5000, 3, m -> {
    });
    private final Deduplicator deduplicator = new Deduplicator(1000);
    private final GroupFanout fanout = new GroupFanout(deliveryService, journal, ackTracker);
    private final MultiDeviceSync multiDeviceSync =
            new MultiDeviceSync(sessionManager, new ChannelMessageSender(registry), journal);

    @AfterEach
    void tearDown() {
        retryQueue.close();
    }

    private EmbeddedChannel channel() {
        CommandDispatcher dispatcher = new CommandDispatcher()
                .register(MsgType.GROUP_JOIN, new GroupJoinHandler(sessionManager, registry, fanout))
                .register(MsgType.GROUP_CHAT, new GroupChatHandler(sessionManager, registry, fanout, deduplicator))
                .register(MsgType.MULTI_SYNC, new MultiSyncHandler(sessionManager, registry, multiDeviceSync, deduplicator));
        EmbeddedChannel ch = new EmbeddedChannel(dispatcher);
        registry.add(ch);
        return ch;
    }

    private void login(EmbeddedChannel ch, String sessionId, String userId) {
        Session session = sessionManager.create(sessionId, userId, "d1", new ChannelSessionConnection(ch));
        session.transitionTo(SessionState.AUTHENTICATED);
        registry.bindSession(ch, sessionId);
    }

    @Test
    void groupChatFansOutToJoinedMembers() {
        EmbeddedChannel alice = channel();
        EmbeddedChannel bob = channel();
        EmbeddedChannel carol = channel();
        login(alice, "sa", "alice");
        login(bob, "sb", "bob");
        login(carol, "sc", "carol");

        bob.writeInbound(Frame.of(MsgType.GROUP_JOIN.code(), 1L, JsonCodec.encode(new GroupJoinRequest("g1"))));
        carol.writeInbound(Frame.of(MsgType.GROUP_JOIN.code(), 1L, JsonCodec.encode(new GroupJoinRequest("g1"))));
        assertEquals("JOINED", JsonCodec.decode(bob.<Frame>readOutbound().body(), AckResponse.class).status());
        assertEquals("JOINED", JsonCodec.decode(carol.<Frame>readOutbound().body(), AckResponse.class).status());

        alice.writeInbound(Frame.of(MsgType.GROUP_CHAT.code(), 2L,
                JsonCodec.encode(new GroupChatRequest("gm1", "g1", "hello group"))));

        Frame ack = alice.readOutbound();
        assertEquals(MsgType.GROUP_ACK.code(), ack.type());
        assertEquals("DELIVERED", JsonCodec.decode(ack.body(), AckResponse.class).status());

        Frame bobGot = bob.readOutbound();
        Frame carolGot = carol.readOutbound();
        assertNotNull(bobGot);
        assertEquals("hello group", JsonCodec.decode(bobGot.body(), ChatDelivery.class).text());
        assertEquals("hello group", JsonCodec.decode(carolGot.body(), ChatDelivery.class).text());

        alice.finishAndReleaseAll();
        bob.finishAndReleaseAll();
        carol.finishAndReleaseAll();
    }

    @Test
    void groupChatToEmptyGroupAcksEmpty() {
        EmbeddedChannel alice = channel();
        login(alice, "sa", "alice");

        alice.writeInbound(Frame.of(MsgType.GROUP_CHAT.code(), 1L,
                JsonCodec.encode(new GroupChatRequest("gm1", "nobody-group", "hi"))));

        Frame ack = alice.readOutbound();
        assertEquals("EMPTY", JsonCodec.decode(ack.body(), AckResponse.class).status());

        alice.finishAndReleaseAll();
    }

    @Test
    void multiSyncReachesOtherDevicesOnly() {
        EmbeddedChannel phone = channel();
        EmbeddedChannel laptop = channel();
        login(phone, "sp", "u1");
        login(laptop, "sl", "u1");

        phone.writeInbound(Frame.of(MsgType.MULTI_SYNC.code(), 1L,
                JsonCodec.encode(new MultiSyncRequest("ms1", "read-receipt"))));

        Frame ack = phone.readOutbound();
        assertEquals(MsgType.MULTI_SYNC_ACK.code(), ack.type());
        assertEquals("SYNCED:1", JsonCodec.decode(ack.body(), AckResponse.class).status());

        Frame onLaptop = laptop.readOutbound();
        assertNotNull(onLaptop);
        assertEquals("read-receipt", JsonCodec.decode(onLaptop.body(), ChatDelivery.class).text());
        assertNull(phone.readOutbound(), "来源设备不应收到同步消息");

        phone.finishAndReleaseAll();
        laptop.finishAndReleaseAll();
    }
}
