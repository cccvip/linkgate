package com.linkgate.netty.handler;

import com.linkgate.core.codec.JsonCodec;
import com.linkgate.core.message.AckTracker;
import com.linkgate.core.protocol.AckRequest;
import com.linkgate.core.session.SessionManager;
import com.linkgate.netty.protocol.CommandHandler;
import com.linkgate.netty.protocol.Frame;
import com.linkgate.netty.server.ConnectionRegistry;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 投递确认：接收方回 ACK，销掉 {@link AckTracker} 里的超时跟踪。
 */
public class AckHandler implements CommandHandler {

    private static final Logger log = LoggerFactory.getLogger(AckHandler.class);

    private final SessionManager sessionManager;
    private final ConnectionRegistry registry;
    private final AckTracker ackTracker;

    public AckHandler(SessionManager sessionManager, ConnectionRegistry registry, AckTracker ackTracker) {
        this.sessionManager = sessionManager;
        this.registry = registry;
        this.ackTracker = ackTracker;
    }

    @Override
    public void handle(ChannelHandlerContext ctx, Frame frame) {
        try {
            requireSession(ctx);
            AckRequest request = JsonCodec.decode(frame.body(), AckRequest.class);
            if (request.messageId() == null || request.messageId().isBlank()) {
                throw new IllegalArgumentException("messageId is required");
            }
            boolean matched = ackTracker.acknowledge(request.messageId());
            log.debug("ACK messageId={} matched={}", request.messageId(), matched);
        } catch (RuntimeException e) {
            log.warn("ACK failed from {}: {}", ctx.channel(), e.toString());
        }
    }

    private void requireSession(ChannelHandlerContext ctx) {
        String sessionId = registry.sessionIdOf(ctx.channel())
                .orElseThrow(() -> new IllegalStateException("not logged in"));
        sessionManager.require(sessionId);
    }
}
