package com.linkgate.store.redis;

import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

/**
 * Redis 连接封装（Lettuce）。
 *
 * <p>注意：类名与 {@code io.lettuce.core.RedisClient} 同名，内部一律用全限定名引用 Lettuce。</p>
 */
public final class RedisClient implements AutoCloseable {

    public static final String DEFAULT_URI = "redis://127.0.0.1:6379";

    private final io.lettuce.core.RedisClient delegate;
    private final StatefulRedisConnection<String, String> connection;

    public RedisClient() {
        this(DEFAULT_URI);
    }

    public RedisClient(String uri) {
        this.delegate = io.lettuce.core.RedisClient.create(uri);
        this.connection = delegate.connect();
    }

    public RedisCommands<String, String> commands() {
        return connection.sync();
    }

    @Override
    public void close() {
        connection.close();
        delegate.shutdown();
    }
}
