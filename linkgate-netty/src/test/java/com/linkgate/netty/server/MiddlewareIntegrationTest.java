package com.linkgate.netty.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.linkgate.core.codec.JsonCodec;
import com.linkgate.core.protocol.LoginRequest;
import com.linkgate.core.protocol.LoginResponse;
import com.linkgate.netty.client.TestClient;
import com.linkgate.netty.protocol.Frame;
import com.linkgate.netty.protocol.MsgType;
import com.linkgate.store.mysql.MysqlStore;
import com.linkgate.store.mysql.SessionRow;
import com.linkgate.store.redis.ConnectionRouter;
import com.linkgate.store.redis.PresenceService;
import com.linkgate.store.redis.RedisClient;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * 中间件接入的端到端验证：登录写 Redis 在线状态/路由 + MySQL 会话快照；断开清理。
 */
class MiddlewareIntegrationTest {

    @Test
    @Timeout(30)
    void loginWritesPresenceRouteAndSessionSnapshot() throws Exception {
        assumeTrue(MiddlewareTestEnv.redisAvailable() && MiddlewareTestEnv.mysqlAvailable(),
                "本地无 Redis/MySQL，跳过");

        GatewayConfig config = new GatewayConfig(0, "node-test",
                MiddlewareTestEnv.REDIS_URI,
                MiddlewareTestEnv.MYSQL_URL, MiddlewareTestEnv.MYSQL_USER, MiddlewareTestEnv.MYSQL_PASSWORD,
                null, null);

        RedisClient redis = new RedisClient(MiddlewareTestEnv.REDIS_URI);
        MysqlStore mysql = new MysqlStore(MiddlewareTestEnv.MYSQL_URL,
                MiddlewareTestEnv.MYSQL_USER, MiddlewareTestEnv.MYSQL_PASSWORD);
        mysql.initSchema();
        redis.commands().flushall();
        String sessionId;

        try (GatewayBootstrap bootstrap = GatewayBootstrap.create(config)) {
            bootstrap.start();

            PresenceService presence = new PresenceService(redis.commands(), Duration.ofSeconds(60));
            ConnectionRouter router = new ConnectionRouter(redis.commands());

            try (TestClient bob = new TestClient("127.0.0.1", bootstrap.port()).connect()) {
                bob.send(Frame.of(MsgType.LOGIN.code(), 1L,
                        JsonCodec.encode(new LoginRequest("bob", "phone"))));
                Frame ack = bob.awaitFrame(3000);
                assertNotNull(ack);
                sessionId = JsonCodec.decode(ack.body(), LoginResponse.class).sessionId();

                assertEquals(Set.of(sessionId), presence.onlineSessions("bob"), "登录应写在线状态");
                assertEquals("node-test", router.nodeOf("bob", "phone").orElseThrow(), "登录应写路由");
                assertEquals("AUTHENTICATED",
                        mysql.sessions().find(sessionId).map(SessionRow::state).orElseThrow(), "登录应写会话快照");
            }

            awaitRedisGone(presence, router, sessionId);
            assertTrue(presence.onlineSessions("bob").isEmpty(), "断开应清在线状态");
            assertTrue(router.nodeOf("bob", "phone").isEmpty(), "断开应清路由");
        } finally {
            redis.close();
            mysql.close();
        }
    }

    private static void awaitRedisGone(PresenceService presence, ConnectionRouter router, String sessionId)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline) {
            if (!presence.onlineSessions("bob").contains(sessionId)) {
                return;
            }
            Thread.sleep(20);
        }
    }
}
