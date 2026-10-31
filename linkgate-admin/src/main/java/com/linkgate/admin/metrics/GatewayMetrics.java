package com.linkgate.admin.metrics;

import com.linkgate.netty.server.ConnectionRegistry;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * 把网关的实时指标注册进 Spring Boot 的 Micrometer {@link MeterRegistry}，
 * 由 actuator 的 {@code /actuator/prometheus} 暴露。
 */
public final class GatewayMetrics {

    public GatewayMetrics(MeterRegistry meterRegistry, ConnectionRegistry connectionRegistry) {
        Gauge.builder("linkgate.connections.active", connectionRegistry, ConnectionRegistry::connectionCount)
                .description("当前活跃连接数")
                .register(meterRegistry);
    }
}
