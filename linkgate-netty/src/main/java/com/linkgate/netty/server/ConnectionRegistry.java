package com.linkgate.netty.server;

import io.netty.channel.Channel;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.GlobalEventExecutor;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 连接注册表：以 {@link ChannelGroup} 维护全部在线连接，并建立 channel ↔ sessionId 双向索引。
 *
 * <p>线程安全。channel 关闭时会自动从 {@link ChannelGroup} 移除。</p>
 *
 * <p>索引以 {@link Channel} 自身（身份等价）为 key，而非 {@code ChannelId}：后者在
 * {@code EmbeddedChannel} 下是单例，会让所有测试连接碰撞到同一个 key。</p>
 */
public class ConnectionRegistry {

    private final ChannelGroup allChannels;
    private final Map<Channel, String> channelToSession = new ConcurrentHashMap<>();
    private final Map<String, Channel> sessionToChannel = new ConcurrentHashMap<>();

    public ConnectionRegistry() {
        this(new DefaultChannelGroup(GlobalEventExecutor.INSTANCE));
    }

    ConnectionRegistry(ChannelGroup allChannels) {
        this.allChannels = allChannels;
    }

    /** 登记一条新连接。 */
    public void add(Channel channel) {
        allChannels.add(channel);
    }

    /**
     * 绑定 sessionId 与 channel；同 sessionId 重复绑定时，旧 channel 的映射被清除（幂等重连友好）。
     */
    public void bindSession(Channel channel, String sessionId) {
        Channel previous = sessionToChannel.put(sessionId, channel);
        if (previous != null && previous != channel) {
            channelToSession.remove(previous);
        }
        channelToSession.put(channel, sessionId);
    }

    public Optional<String> sessionIdOf(Channel channel) {
        return Optional.ofNullable(channelToSession.get(channel));
    }

    public Optional<Channel> channelOf(String sessionId) {
        return Optional.ofNullable(sessionToChannel.get(sessionId));
    }

    /** 解除 sessionId 绑定，返回是否存在。 */
    public boolean unbind(String sessionId) {
        Channel channel = sessionToChannel.remove(sessionId);
        if (channel == null) {
            return false;
        }
        channelToSession.remove(channel);
        return true;
    }

    /** 移除一条连接（含其 sessionId 绑定）。 */
    public void remove(Channel channel) {
        allChannels.remove(channel);
        String sessionId = channelToSession.remove(channel);
        if (sessionId != null) {
            sessionToChannel.remove(sessionId, channel);
        }
    }

    /** 在线连接数。 */
    public int connectionCount() {
        return allChannels.size();
    }

    /** 已绑定会话数。 */
    public int boundSessionCount() {
        return sessionToChannel.size();
    }

    public ChannelGroup channels() {
        return allChannels;
    }
}
