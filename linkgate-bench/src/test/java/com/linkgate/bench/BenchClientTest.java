package com.linkgate.bench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.linkgate.netty.server.GatewayServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class BenchClientTest {

    @Test
    @Timeout(60)
    void activeScenarioExercisesRoundTrip() throws Exception {
        GatewayServer server = new GatewayServer(0);
        server.start();
        try (BenchClient client = new BenchClient("127.0.0.1", server.port(), 2)) {
            client.connect(20);
            assertEquals(20, client.connectedCount());

            BenchResult result = client.run(BenchScenario.ACTIVE, 400);

            assertEquals(20, result.connections());
            assertTrue(result.sent() > 0, "ACTIVE 应发出消息");
            assertTrue(result.received() > 0, "ACTIVE 应收到响应");
            assertTrue(result.p99Millis() >= 0);
        } finally {
            server.stop();
        }
    }

    @Test
    @Timeout(60)
    void idleScenarioOnlyHoldsConnections() throws Exception {
        GatewayServer server = new GatewayServer(0);
        server.start();
        try (BenchClient client = new BenchClient("127.0.0.1", server.port(), 2)) {
            client.connect(10);
            assertEquals(10, client.connectedCount());

            BenchResult result = client.run(BenchScenario.IDLE, 200);

            assertEquals(10, result.connections());
            assertEquals(0, result.received(), "IDLE 不发消息");
        } finally {
            server.stop();
        }
    }
}
