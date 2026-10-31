package com.linkgate.admin.metrics;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * 指标上报：以 HTTP {@code /metrics} 暴露 Prometheus 抓取端点。
 *
 * <p>用 JDK 自带 {@code HttpServer}，不依赖 Spring；生产可交由 admin 的 actuator 复用同一
 * {@link MetricsRegistry}。</p>
 */
public final class MetricsReporter implements AutoCloseable {

    private final MetricsRegistry metrics;
    private HttpServer server;

    public MetricsReporter(MetricsRegistry metrics) {
        this.metrics = metrics;
    }

    /** 在指定端口启动抓取端点（端口 0 由系统分配）。 */
    public void start(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/metrics", exchange -> {
            byte[] body = metrics.scrape().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/plain; version=0.0.4");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
    }

    public int port() {
        if (server == null) {
            return -1;
        }
        return server.getAddress().getPort();
    }

    @Override
    public void close() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }
}
