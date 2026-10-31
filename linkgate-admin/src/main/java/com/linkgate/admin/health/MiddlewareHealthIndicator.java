package com.linkgate.admin.health;

import com.linkgate.store.mysql.MysqlStore;
import com.linkgate.store.redis.RedisClient;
import java.sql.Connection;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * 中间件健康检查：Redis PING + MySQL 连通性（未启用则跳过）。
 */
@Component
public class MiddlewareHealthIndicator implements HealthIndicator {

    private final ObjectProvider<RedisClient> redis;
    private final ObjectProvider<MysqlStore> mysql;

    public MiddlewareHealthIndicator(ObjectProvider<RedisClient> redis, ObjectProvider<MysqlStore> mysql) {
        this.redis = redis;
        this.mysql = mysql;
    }

    @Override
    public Health health() {
        Health.Builder builder = Health.up();

        redis.ifAvailable(client -> {
            try {
                builder.withDetail("redis", "PONG".equals(client.commands().ping()) ? "UP" : "DOWN");
            } catch (Exception e) {
                builder.down().withDetail("redis", "unreachable");
            }
        });

        mysql.ifAvailable(store -> {
            try (Connection ignored = store.dataSource().getConnection()) {
                builder.withDetail("mysql", "UP");
            } catch (Exception e) {
                builder.down().withDetail("mysql", "unreachable");
            }
        });

        return builder.build();
    }
}
