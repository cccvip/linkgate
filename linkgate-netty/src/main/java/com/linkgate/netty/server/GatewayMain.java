package com.linkgate.netty.server;

import java.util.concurrent.CountDownLatch;

/**
 * 命令行入口：读取环境变量启动网关。
 *
 * <pre>
 * LINKGATE_PORT=8080
 * LINKGATE_NODE_ID=node-a
 * LINKGATE_REDIS_URI=redis://127.0.0.1:6379
 * LINKGATE_MYSQL_URL=jdbc:mysql://127.0.0.1:3306/linkgate?useSSL=false&allowPublicKeyRetrieval=true
 * LINKGATE_MYSQL_USER=linkgate
 * LINKGATE_MYSQL_PASSWORD=linkgate
 * LINKGATE_LLM_BASE_URL=https://api.openai.com/v1
 * LINKGATE_LLM_API_KEY=sk-...
 * </pre>
 */
public final class GatewayMain {

    private GatewayMain() {
    }

    public static void main(String[] args) throws Exception {
        GatewayConfig config = GatewayConfig.fromEnv();
        GatewayBootstrap bootstrap = GatewayBootstrap.create(config);
        Runtime.getRuntime().addShutdownHook(new Thread(bootstrap::close, "linkgate-shutdown"));
        bootstrap.start();
        System.out.printf("LinkGate listening on %d (node=%s)%n", bootstrap.port(), config.nodeId());
        new CountDownLatch(1).await();
    }
}
