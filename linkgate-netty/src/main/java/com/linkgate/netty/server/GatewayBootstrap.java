package com.linkgate.netty.server;

import com.linkgate.ai.llm.LlmClient;
import com.linkgate.ai.llm.OpenAiCompatibleLlmClient;
import com.linkgate.core.session.SessionManager;
import com.linkgate.store.mysql.MysqlStore;
import com.linkgate.store.redis.ConnectionRouter;
import com.linkgate.store.redis.PresenceService;
import com.linkgate.store.redis.RedisClient;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 启动装配根：按 {@link GatewayConfig} 创建 Redis / MySQL / LLM 适配，装配组件并启停网关。
 *
 * <p>未配置的中间件会自动降级（例如没有 Redis 就不写在线状态/路由）。</p>
 */
public final class GatewayBootstrap implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(GatewayBootstrap.class);
    private static final Duration PRESENCE_TTL = Duration.ofSeconds(60);

    private final GatewayServer server;
    private final GatewayComponents components;
    private final RedisClient redis;
    private final MysqlStore mysql;

    private GatewayBootstrap(GatewayServer server, GatewayComponents components, RedisClient redis, MysqlStore mysql) {
        this.server = server;
        this.components = components;
        this.redis = redis;
        this.mysql = mysql;
    }

    public static GatewayBootstrap create(GatewayConfig config) throws Exception {
        RedisClient redis = null;
        PresenceService presence = null;
        ConnectionRouter router = null;
        if (config.redisUri() != null) {
            redis = new RedisClient(config.redisUri());
            presence = new PresenceService(redis.commands(), PRESENCE_TTL);
            router = new ConnectionRouter(redis.commands());
            log.info("redis enabled: {}", config.redisUri());
        }

        MysqlStore mysql = null;
        if (config.mysqlUrl() != null) {
            mysql = new MysqlStore(config.mysqlUrl(), config.mysqlUser(), config.mysqlPassword());
            mysql.initSchema();
            log.info("mysql enabled: {}", config.mysqlUrl());
        }

        LlmClient llm = null;
        if (config.llmBaseUrl() != null) {
            llm = new OpenAiCompatibleLlmClient(config.llmBaseUrl(), config.llmApiKey() == null ? "" : config.llmApiKey());
            log.info("llm enabled: {}", config.llmBaseUrl());
        }

        GatewayComponents components = new GatewayComponents(
                new SessionManager(),
                new ConnectionRegistry(),
                new ConnectionLimiter(GatewayComponents.DEFAULT_MAX_CONNECTIONS,
                        GatewayComponents.DEFAULT_CONNECTIONS_PER_SECOND),
                llm, presence, router, mysql, config.nodeId());

        GatewayServer server = new GatewayServer(config.port(), components);
        return new GatewayBootstrap(server, components, redis, mysql);
    }

    public void start() throws InterruptedException {
        server.start();
    }

    public int port() {
        return server.port();
    }

    public GatewayComponents components() {
        return components;
    }

    @Override
    public void close() {
        server.stop();
        components.close();
        if (redis != null) {
            redis.close();
        }
        if (mysql != null) {
            mysql.close();
        }
    }
}
