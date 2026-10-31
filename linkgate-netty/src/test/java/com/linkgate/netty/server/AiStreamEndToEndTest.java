package com.linkgate.netty.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.linkgate.core.codec.JsonCodec;
import com.linkgate.core.protocol.AiChatRequest;
import com.linkgate.core.protocol.AiResumeRequest;
import com.linkgate.core.protocol.AiResumeResponse;
import com.linkgate.core.protocol.AiStreamChunk;
import com.linkgate.core.protocol.AiStreamEnd;
import com.linkgate.core.protocol.LoginRequest;
import com.linkgate.core.session.SessionManager;
import com.linkgate.netty.client.TestClient;
import com.linkgate.netty.protocol.Frame;
import com.linkgate.netty.protocol.MsgType;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Phase 4 DoD：真起服务器，AI 流式端到端 —— 请求 → token 流回推 → 断线 → 重连续接。
 */
class AiStreamEndToEndTest {

    private static final List<String> TOKENS = List.of("Hel", "lo", " wor", "ld", "!", "?");

    private static GatewayServer server() {
        GatewayComponents components = new GatewayComponents(new SessionManager(), new ConnectionRegistry(),
                new ConnectionLimiter(1000, 1000), new ScriptedLlmClient(TOKENS, 30));
        return new GatewayServer(0, components);
    }

    private static void login(TestClient client) throws InterruptedException {
        client.send(Frame.of(MsgType.LOGIN.code(), 1L, JsonCodec.encode(new LoginRequest("bob", "d1"))));
        Frame ack = client.awaitFrame(3000);
        assertNotNull(ack, "未收到 LOGIN_ACK");
        assertEquals(MsgType.LOGIN_ACK.code(), ack.type());
    }

    private static void awaitStreamCompleted(GatewayComponents components, String streamId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            boolean done = components.streamCheckpoints().find(streamId)
                    .map(cp -> cp.completed()).orElse(false);
            if (done) {
                return;
            }
            Thread.sleep(20);
        }
        assertTrue(false, "AI 流未在预期时间内完成");
    }

    @Test
    @Timeout(30)
    void resumesAiStreamAfterReconnect() throws Exception {
        GatewayServer server = server();
        server.start();
        try {
            // 在线阶段：发起 AI_CHAT，收到前 2 片后断线
            try (TestClient bob = new TestClient("127.0.0.1", server.port()).connect()) {
                login(bob);
                bob.send(Frame.of(MsgType.AI_CHAT.code(), 2L,
                        JsonCodec.encode(new AiChatRequest("s1", "hi"))));

                Frame first = bob.awaitFrame(3000);
                Frame second = bob.awaitFrame(3000);
                assertNotNull(first);
                assertNotNull(second);
                assertEquals(MsgType.AI_STREAM.code(), first.type());
                assertEquals(0, JsonCodec.decode(first.body(), AiStreamChunk.class).index());
                assertEquals(1, JsonCodec.decode(second.body(), AiStreamChunk.class).index());
            }

            // 服务端在断开后继续生成，检查点记全
            awaitStreamCompleted(server.components(), "s1");

            // 重连 + 续接 from index 1 → 回补 2..5
            try (TestClient bob = new TestClient("127.0.0.1", server.port()).connect()) {
                login(bob);
                bob.send(Frame.of(MsgType.AI_RESUME.code(), 3L,
                        JsonCodec.encode(new AiResumeRequest("s1", 1))));

                List<Integer> replayed = new ArrayList<>();
                for (int i = 0; i < 4; i++) {
                    Frame frame = bob.awaitFrame(3000);
                    assertNotNull(frame, "缺少回补分片");
                    assertEquals(MsgType.AI_STREAM.code(), frame.type());
                    replayed.add(JsonCodec.decode(frame.body(), AiStreamChunk.class).index());
                }
                assertEquals(List.of(2, 3, 4, 5), replayed, "应无缝补齐缺口");

                Frame end = bob.awaitFrame(3000);
                assertEquals(MsgType.AI_STREAM_END.code(), end.type());
                assertEquals(TOKENS.size(), JsonCodec.decode(end.body(), AiStreamEnd.class).totalTokens());

                Frame ack = bob.awaitFrame(3000);
                assertEquals(MsgType.AI_RESUME_ACK.code(), ack.type());
                assertEquals(4, JsonCodec.decode(ack.body(), AiResumeResponse.class).replayed());
            }
        } finally {
            server.stop();
        }
    }

    @Test
    @Timeout(30)
    void streamsAllTokensWhenClientStaysConnected() throws Exception {
        GatewayServer server = server();
        server.start();
        try (TestClient bob = new TestClient("127.0.0.1", server.port()).connect()) {
            login(bob);
            bob.send(Frame.of(MsgType.AI_CHAT.code(), 2L,
                    JsonCodec.encode(new AiChatRequest("s2", "hi"))));

            StringBuilder text = new StringBuilder();
            Frame frame;
            while ((frame = bob.awaitFrame(3000)) != null && frame.type() == MsgType.AI_STREAM.code()) {
                text.append(JsonCodec.decode(frame.body(), AiStreamChunk.class).token());
            }
            assertEquals(String.join("", TOKENS), text.toString());
            assertNotNull(frame);
            assertEquals(MsgType.AI_STREAM_END.code(), frame.type());
        } finally {
            server.stop();
        }
    }
}
