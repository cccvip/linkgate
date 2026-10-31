package com.linkgate.netty.session;

import com.linkgate.core.message.Message;
import com.linkgate.core.message.MessageSender;
import com.linkgate.core.session.Session;
import com.linkgate.netty.protocol.Frame;
import com.linkgate.netty.protocol.MsgType;
import com.linkgate.netty.server.ConnectionRegistry;
import io.netty.channel.Channel;
import java.util.Optional;

/**
 * 基于 Netty 的 {@link MessageSender}：把消息编码为 CHAT 帧写到会话所在连接。
 */
public final class ChannelMessageSender implements MessageSender {

    private final ConnectionRegistry registry;

    public ChannelMessageSender(ConnectionRegistry registry) {
        this.registry = registry;
    }

    @Override
    public boolean send(Session session, Message message) {
        Optional<Channel> channel = registry.channelOf(session.sessionId());
        if (channel.isEmpty() || !channel.get().isActive()) {
            return false;
        }
        channel.get().writeAndFlush(Frame.of(MsgType.CHAT.code(), message.seq(), message.payload()));
        return true;
    }
}
