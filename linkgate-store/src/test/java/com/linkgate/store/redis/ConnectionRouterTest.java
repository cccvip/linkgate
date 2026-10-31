package com.linkgate.store.redis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.linkgate.store.TestEnv;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConnectionRouterTest {

    private RedisClient client;
    private ConnectionRouter router;

    @BeforeEach
    void setUp() {
        assumeTrue(TestEnv.redisAvailable(), "本地无 Redis(6379)，跳过");
        client = new RedisClient(TestEnv.REDIS_URI);
        client.commands().flushall();
        router = new ConnectionRouter(client.commands());
    }

    @AfterEach
    void tearDown() {
        if (client != null) {
            client.close();
        }
    }

    @Test
    void registersAndResolvesRoutes() {
        router.register("u1", "phone", "node-a");
        router.register("u1", "laptop", "node-b");

        assertEquals("node-a", router.nodeOf("u1", "phone").orElseThrow());
        assertEquals("node-b", router.nodeOf("u1", "laptop").orElseThrow());
        assertEquals(2, router.routes("u1").size());
        assertTrue(router.nodeOf("u1", "tablet").isEmpty());
    }

    @Test
    void unregisterRemovesRoute() {
        router.register("u1", "phone", "node-a");
        router.unregister("u1", "phone");
        assertTrue(router.nodeOf("u1", "phone").isEmpty());
        assertTrue(router.routes("u1").isEmpty());
    }
}
