package com.linkgate.netty.handler;

import com.linkgate.core.codec.JsonCodec;
import com.linkgate.core.message.Deduplicator;
import com.linkgate.core.protocol.AckResponse;
import com.linkgate.core.protocol.ChatDelivery;
import com.linkgate.core.protocol.ErrorResponse;
import com.linkgate.core.protocol.GroupChatRequest;
import com.linkgate.core.route.GroupFanout;
import com.linkgate.core.session.Session;
import com.linkgate.core.session.SessionManager;
import com.linkgate.netty.protocol.CommandHandler;
import com.linkgate.netty.protocol.Frame;
import com.linkgate.netty.protocol.MsgType;
import com.linkgate.netty.server.ConnectionRegistry;
import io.netty.channel.ChannelHandlerContext;
import java.util.UUID;

/** 群消息：去重后广播给群成员（每个成员独立 seq + ACK 记账）。 */
public class GroupChatHandler implements CommandHandler {

    private final SessionManager sessionManager;
    private final ConnectionRegistry registry;
    private final GroupFanout fanout;
    private final Deduplicator deduplicator;

    public GroupChatHandler(SessionManager sessionManager, ConnectionRegistry registry,
                            GroupFanout fanout, Deduplicator deduplicator) {
        this.sessionManager = sessionManager;
        this.registry = registry;
        this.fanout = fanout;
        this.deduplicator = deduplicator;
    }

    @Override
    public void handle(ChannelHandlerContext ctx, Frame frame) {
        long seq = frame.seq();
        try {
            Session sender = requireSession(ctx);
            GroupChatRequest request = JsonCodec.decode(frame.body(), GroupChatRequest.class);
            if (request.groupId() == null || request.groupId().isBlank()) {
                throw new IllegalArgumentException("groupId is required");
            }
            if (request.text() == null) {
                throw new IllegalArgumentException("text is required");
            }

            String messageId = (request.messageId() == null || request.messageId().isBlank())
                    ? UUID.randomUUID().toString()
                    : request.messageId();
            if (!deduplicator.firstSeen(messageId)) {
                reply(ctx, seq, MsgType.GROUP_ACK, new AckResponse("DUPLICATE"));
                return;
            }

            byte[] payload = JsonCodec.encode(new ChatDelivery(messageId, request.groupId(), sender.userId(), request.text()));
            int delivered = fanout.broadcast(request.groupId(), sender.userId(), messageId, payload);
            reply(ctx, seq, MsgType.GROUP_ACK, new AckResponse(delivered > 0 ? "DELIVERED" : "EMPTY"));
        } catch (RuntimeException e) {
            reply(ctx, seq, MsgType.ERROR, new ErrorResponse("GROUP_CHAT_FAILED: " + e.getMessage()));
        }
    }

    private Session requireSession(ChannelHandlerContext ctx) {
        String sessionId = registry.sessionIdOf(ctx.channel())
                .orElseThrow(() -> new IllegalStateException("not logged in"));
        return sessionManager.require(sessionId);
    }

    private static void reply(ChannelHandlerContext ctx, long seq, MsgType type, Object payload) {
        ctx.writeAndFlush(Frame.of(type.code(), seq, JsonCodec.encode(payload)));
    }
}
