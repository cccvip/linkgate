package com.linkgate.core.session;

/**
 * 会话与底层传输的连接句柄（抽象）。
 *
 * <p>core 不依赖 Netty，由接入层提供实现（例如包装 {@code io.netty.channel.Channel}）。</p>
 */
public interface SessionConnection {

    /** 连接标识，通常为 channelId。 */
    String connectionId();

    /** 连接当前是否可用。 */
    boolean isActive();

    /** 关闭连接。 */
    void close();
}
