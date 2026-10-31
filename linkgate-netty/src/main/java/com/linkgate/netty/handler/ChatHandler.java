package com.linkgate.netty.handler;

import com.linkgate.core.codec.JsonCodec;
import com.linkgate.core.message.AckTracker;
import com.linkgate.core.message.Deduplicator;
import com.linkgate.core.message.DeliveryResult;
import com.linkgate.core.message.DeliveryService;
import com.linkgate.core.message.Message;
import com.linkgate.core.message.MessageJournal;
import com.linkgate.core.protocol.AckResponse;
import com.linkgate.core.protocol.ChatDelivery;
import com.linkgate.core.protocol.ChatRequest;
import com.linkgate.core.protocol.ErrorResponse;
import com.linkgate.core.session.Session;
import com.linkgate.core.session.SessionManager;
import com.linkgate.netty.protocol.CommandHandler;
import com.linkgate.netty.protocol.Frame;
import com.linkgate.netty.protocol.MsgType;
import com.linkgate.netty.server.ConnectionRegistry;
import io.netty.channel.ChannelHandlerContext;
import java.util.UUID;

/**
 * 点对点聊天：幂等去重 → 记入消息日志拿服务端 seq → 投递 → 记账等 ACK。
 *
 * <p>状态：{@code DUPLICATE} / {@code DELIVERED} / {@code OFFLINE}。</p>
 */
public class ChatHandler implements CommandHandler {

    private final SessionManager sessionManager;
    private final ConnectionRegistry registry;
    private final DeliveryService deliveryService;
    private final MessageJournal journal;
    private final Deduplicator deduplicator;
    private final AckTracker ackTracker;

    public ChatHandler(SessionManager sessionManager, ConnectionRegistry registry,
                       DeliveryService deliveryService, MessageJournal journal,
                       Deduplicator deduplicator, AckTracker ackTracker) {
        this.sessionManager = sessionManager;
        this.registry = registry;
        this.deliveryService = deliveryService;
        this.journal = journal;
        this.deduplicator = deduplicator;
        this.ackTracker = ackTracker;
    }

    @Override
    public void handle(ChannelHandlerContext ctx, Frame frame) {
        long requestSeq = frame.seq();
        try {
            Session sender = requireSession(ctx);
            ChatRequest request = JsonCodec.decode(frame.body(), ChatRequest.class);
            if (request.toUserId() == null || request.toUserId().isBlank()) {
                throw new IllegalArgumentException("toUserId is required");
            }
            if (request.text() == null) {
                throw new IllegalArgumentException("text is required");
            }

            String messageId = (request.messageId() == null || request.messageId().isBlank())
                    ? UUID.randomUUID().toString()
                    : request.messageId();
            if (!deduplicator.firstSeen(messageId)) {
                reply(ctx, requestSeq, MsgType.CHAT_ACK, new AckResponse("DUPLICATE"));
                return;
            }

            byte[] payload = JsonCodec.encode(new ChatDelivery(sender.userId(), request.text()));
            Message message = journal.append(messageId, sender.userId(), request.toUserId(), payload);
            DeliveryResult result = deliveryService.deliver(message);
            if (result.delivered()) {
                ackTracker.track(message);
            }

            reply(ctx, requestSeq, MsgType.CHAT_ACK,
                    new AckResponse(result.delivered() ? "DELIVERED" : "OFFLINE"));
        } catch (RuntimeException e) {
            reply(ctx, requestSeq, MsgType.ERROR, new ErrorResponse("CHAT_FAILED: " + e.getMessage()));
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
