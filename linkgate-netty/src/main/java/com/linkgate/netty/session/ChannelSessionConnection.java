package com.linkgate.netty.session;

import com.linkgate.core.session.SessionConnection;
import io.netty.channel.Channel;

/**
 * 把 Netty {@link Channel} 适配为 core 的 {@link SessionConnection}。
 */
public final class ChannelSessionConnection implements SessionConnection {

    private final Channel channel;

    public ChannelSessionConnection(Channel channel) {
        this.channel = channel;
    }

    @Override
    public String connectionId() {
        return channel.id().asLongText();
    }

    @Override
    public boolean isActive() {
        return channel.isActive();
    }

    @Override
    public void close() {
        channel.close();
    }
}
