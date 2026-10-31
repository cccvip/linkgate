package com.linkgate.netty.handler;

import com.linkgate.core.codec.JsonCodec;
import com.linkgate.core.protocol.AckResponse;
import com.linkgate.core.protocol.ErrorResponse;
import com.linkgate.core.session.Session;
import com.linkgate.core.session.SessionManager;
import com.linkgate.netty.protocol.CommandHandler;
import com.linkgate.netty.protocol.Frame;
import com.linkgate.netty.protocol.MsgType;
import com.linkgate.netty.server.ConnectionRegistry;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 心跳处理（业务侧）：刷新会话活跃时间，并回 HEARTBEAT_ACK。
 *
 * <p>未登录即心跳视为异常，回 ERROR。</p>
 */
public class HeartbeatHandler implements CommandHandler {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatHandler.class);

    private final SessionManager sessionManager;
    private final ConnectionRegistry registry;

    public HeartbeatHandler(SessionManager sessionManager, ConnectionRegistry registry) {
        this.sessionManager = sessionManager;
        this.registry = registry;
    }

    @Override
    public void handle(ChannelHandlerContext ctx, Frame frame) {
        String sessionId = registry.sessionIdOf(ctx.channel()).orElse(null);
        log.info("heartbeat <- {} session={}", ctx.channel(), sessionId);
        if (sessionId == null) {
            reply(ctx, frame.seq(), MsgType.ERROR, new ErrorResponse("HEARTBEAT before LOGIN"));
            return;
        }
        sessionManager.find(sessionId).ifPresent(Session::touch);
        reply(ctx, frame.seq(), MsgType.HEARTBEAT_ACK, new AckResponse("OK"));
    }

    private static void reply(ChannelHandlerContext ctx, long seq, MsgType type, Object payload) {
        ctx.writeAndFlush(Frame.of(type.code(), seq, JsonCodec.encode(payload)));
    }
}
