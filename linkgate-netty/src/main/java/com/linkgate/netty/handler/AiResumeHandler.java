package com.linkgate.netty.handler;

import com.linkgate.ai.stream.StreamCheckpoint;
import com.linkgate.ai.stream.StreamCheckpointStore;
import com.linkgate.core.codec.JsonCodec;
import com.linkgate.core.protocol.AiResumeRequest;
import com.linkgate.core.protocol.AiResumeResponse;
import com.linkgate.core.protocol.AiStreamChunk;
import com.linkgate.core.protocol.AiStreamEnd;
import com.linkgate.core.protocol.ErrorResponse;
import com.linkgate.core.session.SessionManager;
import com.linkgate.netty.protocol.CommandHandler;
import com.linkgate.netty.protocol.Frame;
import com.linkgate.netty.protocol.MsgType;
import com.linkgate.netty.server.ConnectionRegistry;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AI 流续接：客户端上报 {@code lastIndex}，回补该流中其后所有分片（流已完成则补发结束帧）。
 */
public class AiResumeHandler implements CommandHandler {

    private static final Logger log = LoggerFactory.getLogger(AiResumeHandler.class);

    private final SessionManager sessionManager;
    private final ConnectionRegistry registry;
    private final StreamCheckpointStore checkpoints;

    public AiResumeHandler(SessionManager sessionManager, ConnectionRegistry registry,
                           StreamCheckpointStore checkpoints) {
        this.sessionManager = sessionManager;
        this.registry = registry;
        this.checkpoints = checkpoints;
    }

    @Override
    public void handle(ChannelHandlerContext ctx, Frame frame) {
        long requestSeq = frame.seq();
        try {
            requireSession(ctx);
            AiResumeRequest request = JsonCodec.decode(frame.body(), AiResumeRequest.class);
            if (request.streamId() == null || request.streamId().isBlank()) {
                throw new IllegalArgumentException("streamId is required");
            }

            Channel channel = ctx.channel();
            List<String> missing = checkpoints.since(request.streamId(), request.lastIndex());
            int index = request.lastIndex() + 1;
            for (String token : missing) {
                channel.writeAndFlush(Frame.of(MsgType.AI_STREAM.code(), index,
                        JsonCodec.encode(new AiStreamChunk(request.streamId(), index, token))));
                index++;
            }
            int endIndex = index;
            checkpoints.find(request.streamId())
                    .filter(StreamCheckpoint::completed)
                    .ifPresent(done -> channel.writeAndFlush(Frame.of(MsgType.AI_STREAM_END.code(), endIndex,
                            JsonCodec.encode(new AiStreamEnd(request.streamId(), done.totalTokens())))));

            log.info("AI_RESUME streamId={} lastIndex={} replayed={}",
                    request.streamId(), request.lastIndex(), missing.size());
            reply(ctx, requestSeq, MsgType.AI_RESUME_ACK,
                    new AiResumeResponse(request.streamId(), missing.size()));
        } catch (RuntimeException e) {
            log.warn("AI_RESUME failed: {}", e.toString());
            reply(ctx, requestSeq, MsgType.ERROR, new ErrorResponse("AI_RESUME_FAILED: " + e.getMessage()));
        }
    }

    private void requireSession(ChannelHandlerContext ctx) {
        String sessionId = registry.sessionIdOf(ctx.channel())
                .orElseThrow(() -> new IllegalStateException("not logged in"));
        sessionManager.require(sessionId);
    }

    private static void reply(ChannelHandlerContext ctx, long seq, MsgType type, Object payload) {
        ctx.writeAndFlush(Frame.of(type.code(), seq, JsonCodec.encode(payload)));
    }
}
