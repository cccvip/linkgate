package com.linkgate.admin.metrics;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MetricsRegistryTest {

    @Test
    void countsAreExported() {
        MetricsRegistry metrics = new MetricsRegistry();

        metrics.recordConnectionAccepted();
        metrics.recordConnectionAccepted();
        metrics.recordConnectionRejected();
        metrics.recordMessageDelivered();
        metrics.recordAckTimeout();
        metrics.registerActiveConnections(() -> 3);

        String scrape = metrics.scrape();

        assertTrue(scrape.contains("linkgate_connections_accepted_total 2.0"), scrape);
        assertTrue(scrape.contains("linkgate_connections_rejected_total 1.0"), scrape);
        assertTrue(scrape.contains("linkgate_messages_delivered_total 1.0"), scrape);
        assertTrue(scrape.contains("linkgate_ack_timeouts_total 1.0"), scrape);
        assertTrue(scrape.contains("linkgate_connections_active 3.0"), scrape);
    }

    @Test
    void customGaugeIsExported() {
        MetricsRegistry metrics = new MetricsRegistry();
        metrics.registerGauge("linkgate.write.watermark", "写缓冲水位", () -> 64);

        assertTrue(metrics.scrape().contains("linkgate_write_watermark 64.0"));
    }
}
