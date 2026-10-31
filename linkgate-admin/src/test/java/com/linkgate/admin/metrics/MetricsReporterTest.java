package com.linkgate.admin.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class MetricsReporterTest {

    @Test
    @Timeout(15)
    void exposesScrapeEndpoint() throws Exception {
        MetricsRegistry metrics = new MetricsRegistry();
        metrics.recordMessageDelivered();

        try (MetricsReporter reporter = new MetricsReporter(metrics)) {
            reporter.start(0);
            assertTrue(reporter.port() > 0);

            HttpResponse<String> response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + reporter.port() + "/metrics")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("linkgate_messages_delivered_total 1.0"), response.body());
        }
    }
}
