package com.linkgate.netty.handler;

import com.linkgate.core.codec.JsonCodec;
import com.linkgate.core.protocol.AckResponse;
import com.linkgate.core.protocol.ErrorResponse;
import com.linkgate.core.protocol.GroupJoinRequest;
import com.linkgate.core.route.GroupFanout;
import com.linkgate.core.session.Session;
import com.linkgate.core.session.SessionManager;
import com.linkgate.netty.protocol.CommandHandler;
import com.linkgate.netty.protocol.Frame;
import com.linkgate.netty.protocol.MsgType;
import com.linkgate.netty.server.ConnectionRegistry;
import io.netty.channel.ChannelHandlerContext;

/** 加入群组。 */
public class GroupJoinHandler implements CommandHandler {

    private final SessionManager sessionManager;
    private final ConnectionRegistry registry;
    private final GroupFanout fanout;

    public GroupJoinHandler(SessionManager sessionManager, ConnectionRegistry registry, GroupFanout fanout) {
        this.sessionManager = sessionManager;
        this.registry = registry;
        this.fanout = fanout;
    }

    @Override
    public void handle(ChannelHandlerContext ctx, Frame frame) {
        long seq = frame.seq();
        try {
            Session session = requireSession(ctx);
            GroupJoinRequest request = JsonCodec.decode(frame.body(), GroupJoinRequest.class);
            if (request.groupId() == null || request.groupId().isBlank()) {
                throw new IllegalArgumentException("groupId is required");
            }
            fanout.join(request.groupId(), session.userId());
            reply(ctx, seq, MsgType.GROUP_ACK, new AckResponse("JOINED"));
        } catch (RuntimeException e) {
            reply(ctx, seq, MsgType.ERROR, new ErrorResponse("GROUP_JOIN_FAILED: " + e.getMessage()));
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
