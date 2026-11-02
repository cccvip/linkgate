package com.linkgate.netty.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.linkgate.core.codec.JsonCodec;
import com.linkgate.core.message.DeliveryService;
import com.linkgate.core.message.InMemoryOfflineStore;
import com.linkgate.core.message.Message;
import com.linkgate.core.message.RetryQueue;
import com.linkgate.core.protocol.ChatDelivery;
import com.linkgate.core.protocol.LoginRequest;
import com.linkgate.core.protocol.LoginResponse;
import com.linkgate.core.session.ReconnectPolicy;
import com.linkgate.core.session.Session;
import com.linkgate.core.session.SessionManager;
import com.linkgate.core.session.SessionState;
import com.linkgate.netty.protocol.CommandDispatcher;
import com.linkgate.netty.protocol.Frame;
import com.linkgate.netty.protocol.MsgType;
import com.linkgate.netty.server.ConnectionRegistry;
import com.linkgate.netty.session.ChannelMessageSender;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class LoginHandlerTest {

    private final SessionManager sessionManager = new SessionManager();
    private final ConnectionRegistry registry = new ConnectionRegistry();
    private final InMemoryOfflineStore offlineStore = new InMemoryOfflineStore(100);
    private final DeliveryService deliveryService =
            new DeliveryService(sessionManager, new ChannelMessageSender(registry), offlineStore);
    private final RetryQueue retryQueue = new RetryQueue();
    private final ReconnectPolicy reconnectPolicy = new ReconnectPolicy(sessionManager, retryQueue, 60_000);

    @AfterEach
    void tearDown() {
        retryQueue.close();
    }

    private EmbeddedChannel channel() {
        CommandDispatcher dispatcher = new CommandDispatcher()
                .register(MsgType.LOGIN, new LoginHandler(registry, deliveryService, reconnectPolicy,
                        null, null, null, "node-local"));
        EmbeddedChannel ch = new EmbeddedChannel(dispatcher);
        registry.add(ch);
        return ch;
    }

    private LoginResponse login(EmbeddedChannel ch, String userId, String deviceId, long seq) {
        ch.writeInbound(Frame.of(MsgType.LOGIN.code(), seq, JsonCodec.encode(new LoginRequest(userId, deviceId))));
        Frame ack = ch.readOutbound();
        assertNotNull(ack);
        assertEquals(MsgType.LOGIN_ACK.code(), ack.type());
        return JsonCodec.decode(ack.body(), LoginResponse.class);
    }

    @Test
    void loginCreatesSessionAndAcknowledges() {
        EmbeddedChannel ch = channel();
        LoginResponse response = login(ch, "u1", "d1", 5L);

        assertEquals("u1", response.userId());
        assertEquals("d1", response.deviceId());
        assertNotNull(response.sessionId());

        assertEquals(1, sessionManager.size());
        Session session = sessionManager.require(response.sessionId());
        assertEquals(SessionState.AUTHENTICATED, session.state());
        assertEquals(response.sessionId(), registry.sessionIdOf(ch).orElseThrow());
        assertNull(ch.readOutbound(), "无离线消息时不应额外下发");

        ch.finishAndReleaseAll();
    }

    @Test
    void reloginSameDeviceReusesSession() {
        EmbeddedChannel first = channel();
        LoginResponse r1 = login(first, "u1", "d1", 1L);

        EmbeddedChannel second = channel();
        LoginResponse r2 = login(second, "u1", "d1", 1L);

        assertEquals(r1.sessionId(), r2.sessionId(), "同设备重登应复用会话");
        assertEquals(1, sessionManager.size(), "不应产生重复会话");
        assertEquals(r1.sessionId(), registry.sessionIdOf(second).orElseThrow(), "新连接应重绑");

        first.finishAndReleaseAll();
        second.finishAndReleaseAll();
    }

    @Test
    void blankDeviceFallsBackToDefault() {
        EmbeddedChannel ch = channel();

        ch.writeInbound(Frame.of(MsgType.LOGIN.code(), 1L, JsonCodec.encode(new LoginRequest("u2", " "))));

        LoginResponse response = JsonCodec.decode(ch.<Frame>readOutbound().body(), LoginResponse.class);
        assertEquals("default", response.deviceId());

        ch.finishAndReleaseAll();
    }

    @Test
    void replaysOfflineMessagesOnLogin() {
        offlineStore.store(new Message("m1", "alice", "u1", 1L,
                JsonCodec.encode(new ChatDelivery("msg-offline-1", "alice", "see you online"))));

        EmbeddedChannel ch = channel();
        login(ch, "u1", "d1", 7L);

        Frame replayed = ch.readOutbound();
        assertNotNull(replayed, "登录后应回补离线消息");
        assertEquals(MsgType.CHAT.code(), replayed.type());
        assertEquals("see you online", JsonCodec.decode(replayed.body(), ChatDelivery.class).text());
        assertEquals(0, offlineStore.size("u1"), "回补后离线队列应清空");

        ch.finishAndReleaseAll();
    }

    @Test
    void invalidPayloadRepliesErrorWithoutSession() {
        EmbeddedChannel ch = channel();

        ch.writeInbound(Frame.of(MsgType.LOGIN.code(), 9L, new byte[] {1, 2, 3}));

        Frame error = ch.readOutbound();
        assertNotNull(error);
        assertEquals(MsgType.ERROR.code(), error.type());
        assertEquals(9L, error.seq());
        assertEquals(0, sessionManager.size());

        ch.finishAndReleaseAll();
    }

    @Test
    void missingUserIdRepliesError() {
        EmbeddedChannel ch = channel();
        byte[] body = "{\"deviceId\":\"d1\"}".getBytes(StandardCharsets.UTF_8);

        ch.writeInbound(Frame.of(MsgType.LOGIN.code(), 2L, body));

        Frame error = ch.readOutbound();
        assertEquals(MsgType.ERROR.code(), error.type());
        assertTrue(new String(error.body(), StandardCharsets.UTF_8).contains("userId"));
        assertEquals(0, sessionManager.size());

        ch.finishAndReleaseAll();
    }
}
