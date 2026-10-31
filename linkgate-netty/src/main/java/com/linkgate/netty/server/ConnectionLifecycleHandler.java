package com.linkgate.netty.server;

import com.linkgate.core.session.ReconnectPolicy;
import com.linkgate.core.session.Session;
import com.linkgate.core.session.SessionManager;
import com.linkgate.store.redis.ConnectionRouter;
import com.linkgate.store.redis.PresenceService;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 连接生命周期：连上时登记连接；断开时清理在线状态/路由，并让会话进入宽限期（可续接）。
 */
public class ConnectionLifecycleHandler extends ChannelInboundHandlerAdapter {

    private static final Logger log = LoggerFactory.getLogger(ConnectionLifecycleHandler.class);

    private final ReconnectPolicy reconnectPolicy;
    private final ConnectionRegistry registry;
    private final SessionManager sessionManager;
    private final PresenceService presence;
    private final ConnectionRouter router;

    public ConnectionLifecycleHandler(GatewayComponents components) {
        this.reconnectPolicy = components.reconnectPolicy();
        this.registry = components.registry();
        this.sessionManager = components.sessionManager();
        this.presence = components.presence();
        this.router = components.router();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        registry.add(ctx.channel());
        log.debug("connection active: {}", ctx.channel());
        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        Channel channel = ctx.channel();
        registry.sessionIdOf(channel).ifPresent(sessionId -> {
            sessionManager.find(sessionId).ifPresent(this::clearPresence);
            log.debug("session {} disconnected, entering grace period", sessionId);
            reconnectPolicy.onDisconnect(sessionId);
        });
        registry.remove(channel);
        log.debug("connection inactive: {}", channel);
        ctx.fireChannelInactive();
    }

    private void clearPresence(Session session) {
        if (presence != null) {
            safely("presence", () -> presence.offline(session.userId(), session.sessionId()));
        }
        if (router != null) {
            safely("route", () -> router.unregister(session.userId(), session.deviceId()));
        }
    }

    private static void safely(String what, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            log.warn("{} cleanup failed: {}", what, e.toString());
        }
    }
}
