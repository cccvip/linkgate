package com.linkgate.netty.handler;

import com.linkgate.ai.llm.LlmRequest;
import com.linkgate.ai.stream.AiStreamBridge;
import com.linkgate.ai.stream.CheckpointingTokenSink;
import com.linkgate.ai.stream.StreamCheckpointStore;
import com.linkgate.core.codec.JsonCodec;
import com.linkgate.core.protocol.AiChatRequest;
import com.linkgate.core.protocol.ErrorResponse;
import com.linkgate.core.session.Session;
import com.linkgate.core.session.SessionManager;
import com.linkgate.core.stream.TokenSink;
import com.linkgate.netty.protocol.CommandHandler;
import com.linkgate.netty.protocol.Frame;
import com.linkgate.netty.protocol.MsgType;
import com.linkgate.netty.server.ConnectionRegistry;
import com.linkgate.netty.stream.BackpressureController;
import com.linkgate.netty.stream.ChannelTokenSink;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AI 流式入口：收到 {@code AI_CHAT} 后发起生成，把 token 流经
 * 检查点 → 背压 → {@code AI_STREAM} 帧桥接到当前会话。
 */
public class AiChatHandler implements CommandHandler {

    private static final Logger log = LoggerFactory.getLogger(AiChatHandler.class);

    private final SessionManager sessionManager;
    private final ConnectionRegistry registry;
    private final AiStreamBridge bridge;
    private final StreamCheckpointStore checkpoints;
    private final String model;

    public AiChatHandler(SessionManager sessionManager, ConnectionRegistry registry,
                         AiStreamBridge bridge, StreamCheckpointStore checkpoints, String model) {
        this.sessionManager = sessionManager;
        this.registry = registry;
        this.bridge = bridge;
        this.checkpoints = checkpoints;
        this.model = model;
    }

    @Override
    public void handle(ChannelHandlerContext ctx, Frame frame) {
        long requestSeq = frame.seq();
        try {
            Session session = requireSession(ctx);
            AiChatRequest request = JsonCodec.decode(frame.body(), AiChatRequest.class);
            if (request.streamId() == null || request.streamId().isBlank()) {
                throw new IllegalArgumentException("streamId is required");
            }
            if (request.prompt() == null || request.prompt().isBlank()) {
                throw new IllegalArgumentException("prompt is required");
            }

            Channel channel = ctx.channel();
            TokenSink sink = new CheckpointingTokenSink(request.streamId(), session.userId(), checkpoints,
                    new BackpressureController(channel, new ChannelTokenSink(request.streamId(), channel)));
            bridge.bridge(LlmRequest.of(model, request.prompt()), sink);
            log.info("AI_CHAT streamId={} userId={}", request.streamId(), session.userId());
        } catch (RuntimeException e) {
            log.warn("AI_CHAT failed: {}", e.toString());
            reply(ctx, requestSeq, MsgType.ERROR, new ErrorResponse("AI_CHAT_FAILED: " + e.getMessage()));
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
