package com.linkgate.store.redis;

import io.lettuce.core.api.sync.RedisCommands;
import java.time.Duration;
import java.util.Set;

/**
 * 在线状态（Redis）：每用户一个在线会话集合，靠心跳续 TTL。
 *
 * <p>key：{@code linkgate:presence:<userId>}，value：sessionId 集合。TTL 到期即视为离线。</p>
 */
public final class PresenceService {

    private static final String KEY_PREFIX = "linkgate:presence:";

    private final RedisCommands<String, String> redis;
    private final Duration ttl;

    public PresenceService(RedisCommands<String, String> redis, Duration ttl) {
        this.redis = redis;
        this.ttl = ttl;
    }

    /** 标记会话在线并刷新 TTL。 */
    public void online(String userId, String sessionId) {
        redis.sadd(key(userId), sessionId);
        redis.expire(key(userId), ttl.toSeconds());
    }

    /** 心跳：仅续 TTL。 */
    public void heartbeat(String userId) {
        redis.expire(key(userId), ttl.toSeconds());
    }

    /** 移除某个在线会话。 */
    public void offline(String userId, String sessionId) {
        redis.srem(key(userId), sessionId);
    }

    /** 该用户当前在线的 sessionId 集合。 */
    public Set<String> onlineSessions(String userId) {
        Set<String> sessions = redis.smembers(key(userId));
        return sessions == null ? Set.of() : sessions;
    }

    public boolean isOnline(String userId) {
        Long count = redis.scard(key(userId));
        return count != null && count > 0;
    }

    private static String key(String userId) {
        return KEY_PREFIX + userId;
    }
}
