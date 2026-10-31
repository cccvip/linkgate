package com.linkgate.netty.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.linkgate.core.codec.JsonCodec;
import com.linkgate.core.protocol.AckResponse;
import com.linkgate.core.protocol.ChatDelivery;
import com.linkgate.core.protocol.ChatRequest;
import com.linkgate.core.protocol.LoginRequest;
import com.linkgate.core.protocol.LoginResponse;
import com.linkgate.core.protocol.ResumeRequest;
import com.linkgate.core.protocol.ResumeResponse;
import com.linkgate.netty.client.TestClient;
import com.linkgate.netty.protocol.Frame;
import com.linkgate.netty.protocol.MsgType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Phase 1 DoD：真起服务器，两个客户端登录后互发消息。
 */
class GatewayEndToEndTest {

    @Test
    @Timeout(30)
    void twoClientsLoginChatAndHeartbeat() throws Exception {
        GatewayServer server = new GatewayServer(0);
        server.start();
        try (TestClient alice = new TestClient("127.0.0.1", server.port()).connect();
             TestClient bob = new TestClient("127.0.0.1", server.port()).connect()) {

            assertTrue(alice.isActive());
            assertTrue(bob.isActive());

            Frame aliceAck = login(alice, "alice");
            assertEquals(MsgType.LOGIN_ACK.code(), aliceAck.type());
            assertNotNull(JsonCodec.decode(aliceAck.body(), LoginResponse.class).sessionId());

            Frame bobAck = login(bob, "bob");
            assertEquals(MsgType.LOGIN_ACK.code(), bobAck.type());

            assertEquals(2, server.components().sessionManager().size());
            assertEquals(2, server.components().registry().boundSessionCount());

            alice.send(Frame.of(MsgType.HEARTBEAT.code(), 2L));
            Frame heartbeat = alice.awaitFrame(3000);
            assertNotNull(heartbeat);
            assertEquals(MsgType.HEARTBEAT_ACK.code(), heartbeat.type());

            alice.send(Frame.of(MsgType.CHAT.code(), 3L,
                    JsonCodec.encode(new ChatRequest("bob", "hello bob"))));

            Frame chatAck = alice.awaitFrame(3000);
            assertNotNull(chatAck);
            assertEquals(MsgType.CHAT_ACK.code(), chatAck.type());
            assertEquals("DELIVERED", JsonCodec.decode(chatAck.body(), AckResponse.class).status());

            Frame delivered = bob.awaitFrame(3000);
            assertNotNull(delivered);
            assertEquals(MsgType.CHAT.code(), delivered.type());
            ChatDelivery body = JsonCodec.decode(delivered.body(), ChatDelivery.class);
            assertEquals("alice", body.fromUserId());
            assertEquals("hello bob", body.text());
        } finally {
            server.stop();
        }
    }

    @Test
    @Timeout(30)
    void offlineMessageIsReplayedOnLogin() throws Exception {
        GatewayServer server = new GatewayServer(0);
        server.start();
        try {
            // alice 在线，bob 离线：消息应落入离线存储
            try (TestClient alice = new TestClient("127.0.0.1", server.port()).connect()) {
                login(alice, "alice");
                alice.send(Frame.of(MsgType.CHAT.code(), 3L,
                        JsonCodec.encode(new ChatRequest("bob", "catch you later"))));

                Frame ack = alice.awaitFrame(3000);
                assertNotNull(ack);
                assertEquals(MsgType.CHAT_ACK.code(), ack.type());
                assertEquals("OFFLINE", JsonCodec.decode(ack.body(), AckResponse.class).status());
            }

            // bob 上线：应收到回补的离线消息
            try (TestClient bob = new TestClient("127.0.0.1", server.port()).connect()) {
                Frame loginAck = login(bob, "bob");
                assertEquals(MsgType.LOGIN_ACK.code(), loginAck.type());

                Frame replayed = bob.awaitFrame(3000);
                assertNotNull(replayed, "bob 上线后应收到离线消息");
                assertEquals(MsgType.CHAT.code(), replayed.type());
                assertEquals("catch you later", JsonCodec.decode(replayed.body(), ChatDelivery.class).text());
            }
        } finally {
            server.stop();
        }
    }

    @Test
    @Timeout(30)
    void resumeReplaysMissingMessagesAcrossReconnect() throws Exception {
        GatewayServer server = new GatewayServer(0);
        server.start();
        try (TestClient alice = new TestClient("127.0.0.1", server.port()).connect()) {
            login(alice, "alice");

            // bob 在线，收到 seq 1..3
            String[] firstSessionId = new String[1];
            try (TestClient bob = new TestClient("127.0.0.1", server.port()).connect()) {
                firstSessionId[0] =
                        JsonCodec.decode(login(bob, "bob").body(), LoginResponse.class).sessionId();
                for (int i = 1; i <= 3; i++) {
                    sendChat(alice, "bob", "m" + i);
                }
                for (long expected = 1; expected <= 3; expected++) {
                    Frame frame = bob.awaitFrame(3000);
                    assertNotNull(frame);
                    assertEquals(MsgType.CHAT.code(), frame.type());
                    assertEquals(expected, frame.seq());
                }
            }

            // bob 断线，期间服务端再发出 2 条（bob 未收到）
            sendChat(alice, "bob", "m4");
            sendChat(alice, "bob", "m5");

            // bob 重连，上报 lastSeq=3，应回补 seq 4、5，无缺口
            try (TestClient bob = new TestClient("127.0.0.1", server.port()).connect()) {
                String resumedSessionId =
                        JsonCodec.decode(login(bob, "bob").body(), LoginResponse.class).sessionId();
                assertEquals(firstSessionId[0], resumedSessionId, "重连应复用同一会话");
                bob.send(Frame.of(MsgType.RESUME.code(), 100L, JsonCodec.encode(new ResumeRequest(3))));

                Frame first = bob.awaitFrame(3000);
                Frame second = bob.awaitFrame(3000);
                Frame ack = bob.awaitFrame(3000);
                assertNotNull(first);
                assertNotNull(second);
                assertNotNull(ack);
                assertEquals(MsgType.CHAT.code(), first.type());
                assertEquals(4L, first.seq());
                assertEquals(5L, second.seq());
                assertEquals(MsgType.RESUME_ACK.code(), ack.type());
                assertEquals(2, JsonCodec.decode(ack.body(), ResumeResponse.class).replayed());
            }
        } finally {
            server.stop();
        }
    }

    private static void sendChat(TestClient from, String toUserId, String text) {
        from.send(Frame.of(MsgType.CHAT.code(), 1L, JsonCodec.encode(new ChatRequest(toUserId, text))));
    }

    private static Frame login(TestClient client, String userId) throws InterruptedException {
        client.send(Frame.of(MsgType.LOGIN.code(), 1L, JsonCodec.encode(new LoginRequest(userId, "d1"))));
        Frame ack = client.awaitFrame(3000);
        assertNotNull(ack, "未收到 LOGIN_ACK");
        return ack;
    }
}
