package com.linkgate.netty.client;

import com.linkgate.core.codec.JsonCodec;
import com.linkgate.core.protocol.AckRequest;
import com.linkgate.core.protocol.ChatDelivery;
import com.linkgate.netty.protocol.Frame;
import com.linkgate.netty.protocol.MsgType;
import com.linkgate.netty.protocol.WebSocketCodec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 简易 WebSocket 客户端：连接网关、发送 {@link Frame}、阻塞等待回帧。
 *
 * <p>用于集成测试与手工调试。</p>
 */
public final class TestClient implements AutoCloseable {

    private final String host;
    private final int port;
    private final BlockingQueue<Frame> inbound = new LinkedBlockingQueue<>();
    private final StringBuilder partial = new StringBuilder();
    private final AtomicLong ackSeq = new AtomicLong(1);

    private WebSocket webSocket;

    public TestClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public TestClient connect() {
        webSocket = HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .buildAsync(URI.create("ws://" + host + ":" + port + "/ws"), new Listener())
                .join();
        return this;
    }

    public boolean isActive() {
        return webSocket != null && !webSocket.isOutputClosed() && !webSocket.isInputClosed();
    }

    public void send(Frame frame) {
        webSocket.sendText(WebSocketCodec.encode(frame), true).join();
    }

    /** 阻塞等待下一帧，超时返回 {@code null}。 */
    public Frame awaitFrame(long timeoutMillis) throws InterruptedException {
        return inbound.poll(timeoutMillis, TimeUnit.MILLISECONDS);
    }

    /** 收到 CHAT 投递时自动回 ACK，避免服务端超时重传。 */
    private void autoAck(Frame frame) {
        try {
            ChatDelivery delivery = JsonCodec.decode(frame.body(), ChatDelivery.class);
            if (delivery.messageId() != null && !delivery.messageId().isBlank()) {
                String json = WebSocketCodec.encode(Frame.of(MsgType.ACK.code(), ackSeq.getAndIncrement(),
                        JsonCodec.encode(new AckRequest(delivery.messageId()))));
                webSocket.sendText(json, true);
            }
        } catch (RuntimeException ignored) {
            // 非 JSON 投递或解码失败则忽略
        }
    }

    @Override
    public void close() {
        if (webSocket != null) {
            try {
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "bye").join();
            } catch (Exception ignored) {
                // 忽略关闭异常
            }
            webSocket = null;
        }
    }

    private final class Listener implements WebSocket.Listener {

        @Override
        public void onOpen(WebSocket ws) {
            ws.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            partial.append(data);
            if (last) {
                String text = partial.toString();
                partial.setLength(0);
                try {
                    Frame frame = WebSocketCodec.decode(text);
                    if (frame.type() == MsgType.CHAT.code()) {
                        autoAck(frame);
                    }
                    inbound.add(frame);
                } catch (RuntimeException ignored) {
                    // 忽略坏帧
                }
            }
            ws.request(1);
            return null;
        }
    }
}
