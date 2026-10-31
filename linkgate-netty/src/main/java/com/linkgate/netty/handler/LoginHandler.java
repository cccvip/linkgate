package com.linkgate.netty.handler;

import com.linkgate.core.codec.JsonCodec;
import com.linkgate.core.message.DeliveryService;
import com.linkgate.core.protocol.ErrorResponse;
import com.linkgate.core.protocol.LoginRequest;
import com.linkgate.core.protocol.LoginResponse;
import com.linkgate.core.session.ReconnectPolicy;
import com.linkgate.core.session.Session;
import com.linkgate.core.session.SessionConnection;
import com.linkgate.netty.protocol.CommandHandler;
import com.linkgate.netty.protocol.Frame;
import com.linkgate.netty.protocol.MsgType;
import com.linkgate.netty.server.ConnectionRegistry;
import com.linkgate.netty.session.ChannelSessionConnection;
import com.linkgate.store.mysql.SessionDao;
import com.linkgate.store.mysql.SessionRow;
import com.linkgate.store.redis.ConnectionRouter;
import com.linkgate.store.redis.PresenceService;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 登录处理：建会话 / 续接 → 登记连接 → 写在线状态与路由（可选）→ 回 LOGIN_ACK → 回补离线消息。
 */
public class LoginHandler implements CommandHandler {

    private static final Logger log = LoggerFactory.getLogger(LoginHandler.class);
    private static final String DEFAULT_DEVICE = "default";

    private final ConnectionRegistry registry;
    private final DeliveryService deliveryService;
    private final ReconnectPolicy reconnectPolicy;
    private final PresenceService presence;
    private final ConnectionRouter router;
    private final SessionDao sessionDao;
    private final String nodeId;

    public LoginHandler(ConnectionRegistry registry, DeliveryService deliveryService, ReconnectPolicy reconnectPolicy,
                        PresenceService presence, ConnectionRouter router, SessionDao sessionDao, String nodeId) {
        this.registry = registry;
        this.deliveryService = deliveryService;
        this.reconnectPolicy = reconnectPolicy;
        this.presence = presence;
        this.router = router;
        this.sessionDao = sessionDao;
        this.nodeId = nodeId;
    }

    @Override
    public void handle(ChannelHandlerContext ctx, Frame frame) {
        long seq = frame.seq();
        try {
            LoginRequest request = JsonCodec.decode(frame.body(), LoginRequest.class);
            if (request.userId() == null || request.userId().isBlank()) {
                throw new IllegalArgumentException("userId is required");
            }
            String deviceId = (request.deviceId() == null || request.deviceId().isBlank())
                    ? DEFAULT_DEVICE
                    : request.deviceId();

            SessionConnection connection = new ChannelSessionConnection(ctx.channel());
            Session session = reconnectPolicy.connect(request.userId(), deviceId, connection);
            registry.bindSession(ctx.channel(), session.sessionId());

            markPresence(request.userId(), deviceId, session);

            log.info("LOGIN ok sessionId={} userId={} deviceId={}", session.sessionId(), request.userId(), deviceId);
            reply(ctx, seq, MsgType.LOGIN_ACK,
                    new LoginResponse(session.sessionId(), request.userId(), deviceId));

            int replayed = deliveryService.replayOffline(request.userId());
            if (replayed > 0) {
                log.info("replayed {} offline message(s) to userId={}", replayed, request.userId());
            }
        } catch (RuntimeException e) {
            log.warn("LOGIN failed from {}: {}", ctx.channel(), e.toString());
            reply(ctx, seq, MsgType.ERROR, new ErrorResponse("LOGIN_FAILED: " + e.getMessage()));
        }
    }

    private void markPresence(String userId, String deviceId, Session session) {
        if (presence != null) {
            safely("presence", () -> presence.online(userId, session.sessionId()));
        }
        if (router != null) {
            safely("route", () -> router.register(userId, deviceId, nodeId));
        }
        if (sessionDao != null) {
            safely("session-snapshot", () -> sessionDao.upsert(
                    new SessionRow(session.sessionId(), userId, deviceId, session.state().name())));
        }
    }

    private static void safely(String what, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            log.warn("{} update failed: {}", what, e.toString());
        }
    }

    private static void reply(ChannelHandlerContext ctx, long seq, MsgType type, Object payload) {
        ctx.writeAndFlush(Frame.of(type.code(), seq, JsonCodec.encode(payload)));
    }
}
