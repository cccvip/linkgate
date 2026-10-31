package com.linkgate.store.redis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.linkgate.store.TestEnv;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PresenceServiceTest {

    private RedisClient client;
    private PresenceService presence;

    @BeforeEach
    void setUp() {
        assumeTrue(TestEnv.redisAvailable(), "本地无 Redis(6379)，跳过");
        client = new RedisClient(TestEnv.REDIS_URI);
        client.commands().flushall();
        presence = new PresenceService(client.commands(), Duration.ofSeconds(30));
    }

    @AfterEach
    void tearDown() {
        if (client != null) {
            client.close();
        }
    }

    @Test
    void onlineOfflineLifecycle() {
        presence.online("u1", "s1");
        presence.online("u1", "s2");

        assertTrue(presence.isOnline("u1"));
        assertEquals(Set.of("s1", "s2"), presence.onlineSessions("u1"));

        presence.offline("u1", "s1");
        assertEquals(Set.of("s2"), presence.onlineSessions("u1"));

        presence.offline("u1", "s2");
        assertFalse(presence.isOnline("u1"));
    }

    @Test
    void heartbeatRefreshesTtl() {
        presence.online("u1", "s1");
        long ttl = client.commands().ttl("linkgate:presence:u1");
        assertTrue(ttl > 0, "在线集合应带 TTL");

        presence.heartbeat("u1");
        assertTrue(client.commands().ttl("linkgate:presence:u1") > 0);
    }
}
