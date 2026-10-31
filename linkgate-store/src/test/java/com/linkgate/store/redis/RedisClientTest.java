package com.linkgate.store.redis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.linkgate.store.TestEnv;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RedisClientTest {

    private RedisClient client;

    @BeforeEach
    void setUp() {
        assumeTrue(TestEnv.redisAvailable(), "本地无 Redis(6379)，跳过");
        client = new RedisClient(TestEnv.REDIS_URI);
        client.commands().flushall();
    }

    @AfterEach
    void tearDown() {
        if (client != null) {
            client.close();
        }
    }

    @Test
    void pingAndSetGet() {
        assertEquals("PONG", client.commands().ping());
        client.commands().set("k", "v");
        assertEquals("v", client.commands().get("k"));
    }

    @Test
    void hashOperations() {
        client.commands().hset("h", "f", "1");
        assertEquals("1", client.commands().hget("h", "f"));
        assertTrue(client.commands().hgetall("h").containsKey("f"));
    }
}
