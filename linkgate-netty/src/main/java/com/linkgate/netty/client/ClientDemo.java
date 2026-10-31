package com.linkgate.netty.client;

import com.linkgate.core.codec.JsonCodec;
import com.linkgate.core.protocol.AckResponse;
import com.linkgate.core.protocol.ChatDelivery;
import com.linkgate.core.protocol.ChatRequest;
import com.linkgate.core.protocol.LoginRequest;
import com.linkgate.core.protocol.LoginResponse;
import com.linkgate.netty.protocol.Frame;
import com.linkgate.netty.protocol.MsgType;

/**
 * 手工联调客户端：连到运行中的网关，演示「登录 → 点对点聊天 → 投递」。
 *
 * <p>用法（先启动网关 8080）：</p>
 * <pre>
 *   java -cp linkgate-netty-&lt;version&gt;-all.jar com.linkgate.netty.client.ClientDemo [host] [port]
 * </pre>
 */
public final class ClientDemo {

    private ClientDemo() {
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("logback.configurationFile", "linkgate-logback.xml");
        String host = args.length > 0 ? args[0] : "127.0.0.1";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 8080;

        try (TestClient alice = new TestClient(host, port).connect();
             TestClient bob = new TestClient(host, port).connect()) {

            login(alice, "alice", "phone");
            login(bob, "bob", "phone");

            ChatRequest request = new ChatRequest("demo-msg-001", "bob", "hello from alice");
            alice.send(Frame.of(MsgType.CHAT.code(), 1L, JsonCodec.encode(request)));

            Frame chatAck = alice.awaitFrame(5000);
            System.out.println("[alice] CHAT_ACK: " + JsonCodec.decode(chatAck.body(), AckResponse.class));

            Frame delivered = bob.awaitFrame(5000);
            if (delivered == null) {
                System.out.println("[bob]   no delivery received");
                return;
            }
            System.out.println("[bob]   delivered seq=" + delivered.seq()
                    + " body=" + JsonCodec.decode(delivered.body(), ChatDelivery.class));
        }
    }

    private static void login(TestClient client, String userId, String deviceId) throws InterruptedException {
        client.send(Frame.of(MsgType.LOGIN.code(), 1L,
                JsonCodec.encode(new LoginRequest(userId, deviceId))));
        Frame ack = client.awaitFrame(5000);
        System.out.println("[" + userId + "]  LOGIN_ACK: "
                + JsonCodec.decode(ack.body(), LoginResponse.class));
    }
}
