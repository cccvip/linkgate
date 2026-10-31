package com.linkgate.admin.config;

import com.linkgate.admin.metrics.GatewayMetrics;
import com.linkgate.ai.llm.LlmClient;
import com.linkgate.ai.llm.OpenAiCompatibleLlmClient;
import com.linkgate.core.session.SessionManager;
import com.linkgate.netty.server.ConnectionLimiter;
import com.linkgate.netty.server.ConnectionRegistry;
import com.linkgate.netty.server.GatewayComponents;
import com.linkgate.netty.server.GatewayServer;
import com.linkgate.store.mysql.MysqlStore;
import com.linkgate.store.redis.ConnectionRouter;
import com.linkgate.store.redis.PresenceService;
import com.linkgate.store.redis.RedisClient;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.SQLException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 网关组装配置：把 netty / core / store / ai 的组件声明为 Bean，数据面保持框架无关。
 */
@Configuration
@EnableConfigurationProperties(LinkgateProperties.class)
public class GatewayConfiguration {

    @Bean
    public SessionManager sessionManager() {
        return new SessionManager();
    }

    @Bean
    public ConnectionRegistry connectionRegistry() {
        return new ConnectionRegistry();
    }

    @Bean
    public ConnectionLimiter connectionLimiter() {
        return new ConnectionLimiter(
                GatewayComponents.DEFAULT_MAX_CONNECTIONS,
                GatewayComponents.DEFAULT_CONNECTIONS_PER_SECOND);
    }

    @Configuration
    @ConditionalOnProperty(name = "linkgate.redis.enabled", havingValue = "true")
    static class RedisConfig {

        @Bean(destroyMethod = "close")
        public RedisClient redisClient(LinkgateProperties properties) {
            return new RedisClient(properties.getRedis().getUri());
        }

        @Bean
        public PresenceService presenceService(RedisClient redis, LinkgateProperties properties) {
            return new PresenceService(redis.commands(), properties.getRedis().getTtl());
        }

        @Bean
        public ConnectionRouter connectionRouter(RedisClient redis) {
            return new ConnectionRouter(redis.commands());
        }
    }

    @Configuration
    @ConditionalOnProperty(name = "linkgate.mysql.enabled", havingValue = "true")
    static class MysqlConfig {

        @Bean(destroyMethod = "close")
        public MysqlStore mysqlStore(LinkgateProperties properties) throws SQLException {
            MysqlStore store = new MysqlStore(
                    properties.getMysql().getUrl(),
                    properties.getMysql().getUsername(),
                    properties.getMysql().getPassword());
            store.initSchema();
            return store;
        }
    }

    @Configuration
    @ConditionalOnProperty(name = "linkgate.llm.enabled", havingValue = "true")
    static class LlmConfig {

        @Bean
        public LlmClient llmClient(LinkgateProperties properties) {
            return new OpenAiCompatibleLlmClient(
                    properties.getLlm().getBaseUrl(),
                    properties.getLlm().getApiKey());
        }
    }

    @Bean(destroyMethod = "close")
    public GatewayComponents gatewayComponents(
            SessionManager sessionManager,
            ConnectionRegistry registry,
            ConnectionLimiter connectionLimiter,
            ObjectProvider<LlmClient> llm,
            ObjectProvider<PresenceService> presence,
            ObjectProvider<ConnectionRouter> router,
            ObjectProvider<MysqlStore> mysql,
            LinkgateProperties properties) {
        return new GatewayComponents(
                sessionManager,
                registry,
                connectionLimiter,
                llm.getIfAvailable(),
                presence.getIfAvailable(),
                router.getIfAvailable(),
                mysql.getIfAvailable(),
                properties.getNodeId());
    }

    @Bean
    public GatewayServer gatewayServer(GatewayComponents components, LinkgateProperties properties) {
        return new GatewayServer(properties.getPort(), components);
    }

    @Bean
    public GatewayServerLifecycle gatewayServerLifecycle(GatewayServer server) {
        return new GatewayServerLifecycle(server);
    }

    @Bean
    public GatewayMetrics gatewayMetrics(MeterRegistry meterRegistry, ConnectionRegistry registry) {
        return new GatewayMetrics(meterRegistry, registry);
    }
}
