package com.linkgate.store.redis;

import io.lettuce.core.api.sync.RedisCommands;
import java.util.Map;
import java.util.Optional;

/**
 * 连接路由（Redis）：记录 {@code (userId, deviceId) → nodeId}，供跨节点投递使用。
 *
 * <p>key：{@code linkgate:route:<userId>}，hash：deviceId → nodeId。</p>
 */
public final class ConnectionRouter {

    private static final String KEY_PREFIX = "linkgate:route:";

    private final RedisCommands<String, String> redis;

    public ConnectionRouter(RedisCommands<String, String> redis) {
        this.redis = redis;
    }

    /** 设备登录后登记其所在节点。 */
    public void register(String userId, String deviceId, String nodeId) {
        redis.hset(key(userId), deviceId, nodeId);
    }

    public void unregister(String userId, String deviceId) {
        redis.hdel(key(userId), deviceId);
    }

    public Optional<String> nodeOf(String userId, String deviceId) {
        return Optional.ofNullable(redis.hget(key(userId), deviceId));
    }

    /** 该用户所有设备的路由。 */
    public Map<String, String> routes(String userId) {
        Map<String, String> routes = redis.hgetall(key(userId));
        return routes == null ? Map.of() : routes;
    }

    private static String key(String userId) {
        return KEY_PREFIX + userId;
    }
}
