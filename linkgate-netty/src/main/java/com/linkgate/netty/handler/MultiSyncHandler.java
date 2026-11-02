package com.linkgate.netty.handler;

import com.linkgate.core.codec.JsonCodec;
import com.linkgate.core.message.Deduplicator;
import com.linkgate.core.protocol.AckResponse;
import com.linkgate.core.protocol.ChatDelivery;
import com.linkgate.core.protocol.ErrorResponse;
import com.linkgate.core.protocol.MultiSyncRequest;
import com.linkgate.core.route.MultiDeviceSync;
import com.linkgate.core.session.Session;
import com.linkgate.core.session.SessionManager;
import com.linkgate.netty.protocol.CommandHandler;
import com.linkgate.netty.protocol.Frame;
import com.linkgate.netty.protocol.MsgType;
import com.linkgate.netty.server.ConnectionRegistry;
import io.netty.channel.ChannelHandlerContext;
import java.util.UUID;

/** 多端同步：把一次行为同步给同用户的其它设备。 */
public class MultiSyncHandler implements CommandHandler {

    private final SessionManager sessionManager;
    private final ConnectionRegistry registry;
    private final MultiDeviceSync multiDeviceSync;
    private final Deduplicator deduplicator;

    public MultiSyncHandler(SessionManager sessionManager, ConnectionRegistry registry,
                            MultiDeviceSync multiDeviceSync, Deduplicator deduplicator) {
        this.sessionManager = sessionManager;
        this.registry = registry;
        this.multiDeviceSync = multiDeviceSync;
        this.deduplicator = deduplicator;
    }

    @Override
    public void handle(ChannelHandlerContext ctx, Frame frame) {
        long seq = frame.seq();
        try {
            Session session = requireSession(ctx);
            MultiSyncRequest request = JsonCodec.decode(frame.body(), MultiSyncRequest.class);
            if (request.text() == null) {
                throw new IllegalArgumentException("text is required");
            }

            String messageId = (request.messageId() == null || request.messageId().isBlank())
                    ? UUID.randomUUID().toString()
                    : request.messageId();
            if (!deduplicator.firstSeen(messageId)) {
                reply(ctx, seq, MsgType.MULTI_SYNC_ACK, new AckResponse("DUPLICATE"));
                return;
            }

            byte[] payload = JsonCodec.encode(new ChatDelivery(messageId, session.userId(), request.text()));
            int delivered = multiDeviceSync.syncToOtherDevices(
                    session.userId(), session.sessionId(), messageId, payload);
            reply(ctx, seq, MsgType.MULTI_SYNC_ACK, new AckResponse("SYNCED:" + delivered));
        } catch (RuntimeException e) {
            reply(ctx, seq, MsgType.ERROR, new ErrorResponse("MULTI_SYNC_FAILED: " + e.getMessage()));
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
