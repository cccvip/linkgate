package com.linkgate.admin.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.util.function.Supplier;

/**
 * 指标注册中心：连接数、消息投递、ack 超时、写水位等。
 *
 * <p>基于 Micrometer 的 Prometheus 注册表；指标名与 Prometheus 文本格式对齐。</p>
 */
public final class MetricsRegistry {

    public static final String ACTIVE_CONNECTIONS = "linkgate.connections.active";

    private final PrometheusMeterRegistry registry;
    private final Counter connectionsAccepted;
    private final Counter connectionsRejected;
    private final Counter messagesDelivered;
    private final Counter ackTimeouts;

    public MetricsRegistry() {
        this(new PrometheusMeterRegistry(PrometheusConfig.DEFAULT));
    }

    public MetricsRegistry(PrometheusMeterRegistry registry) {
        this.registry = registry;
        this.connectionsAccepted = Counter.builder("linkgate.connections.accepted")
                .description("累计接纳连接数").register(registry);
        this.connectionsRejected = Counter.builder("linkgate.connections.rejected")
                .description("累计拒绝连接数").register(registry);
        this.messagesDelivered = Counter.builder("linkgate.messages.delivered")
                .description("累计投递消息数").register(registry);
        this.ackTimeouts = Counter.builder("linkgate.ack.timeouts")
                .description("累计 ack 超时（触发重传）次数").register(registry);
    }

    public void recordConnectionAccepted() {
        connectionsAccepted.increment();
    }

    public void recordConnectionRejected() {
        connectionsRejected.increment();
    }

    public void recordMessageDelivered() {
        messagesDelivered.increment();
    }

    public void recordAckTimeout() {
        ackTimeouts.increment();
    }

    /** 注册"当前活跃连接数"仪表。 */
    public void registerActiveConnections(Supplier<Number> supplier) {
        Gauge.builder(ACTIVE_CONNECTIONS, supplier)
                .description("当前活跃连接数")
                .register(registry);
    }

    /** 注册自定义仪表（如写缓冲水位）。 */
    public void registerGauge(String name, String description, Supplier<Number> supplier) {
        Gauge.builder(name, supplier).description(description).register(registry);
    }

    /** 导出 Prometheus 文本。 */
    public String scrape() {
        return registry.scrape();
    }

    public PrometheusMeterRegistry registry() {
        return registry;
    }
}
