package com.linkgate.admin.config;

import com.linkgate.netty.server.GatewayServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

/**
 * 把 {@link GatewayServer} 纳入 Spring 生命周期：上下文启动时 start，关闭时 stop。
 */
public class GatewayServerLifecycle implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(GatewayServerLifecycle.class);

    private final GatewayServer server;
    private volatile boolean running;

    public GatewayServerLifecycle(GatewayServer server) {
        this.server = server;
    }

    @Override
    public void start() {
        try {
            server.start();
            running = true;
            log.info("LinkGate gateway started on port {}", server.port());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("gateway start interrupted", e);
        }
    }

    @Override
    public void stop() {
        server.stop();
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
