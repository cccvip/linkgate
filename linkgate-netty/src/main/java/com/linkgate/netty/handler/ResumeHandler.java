package com.linkgate.netty.handler;

import com.linkgate.core.codec.JsonCodec;
import com.linkgate.core.message.Message;
import com.linkgate.core.message.MessageJournal;
import com.linkgate.core.message.MessageSender;
import com.linkgate.core.protocol.ErrorResponse;
import com.linkgate.core.protocol.ResumeRequest;
import com.linkgate.core.protocol.ResumeResponse;
import com.linkgate.core.session.Session;
import com.linkgate.core.session.SessionManager;
import com.linkgate.netty.protocol.CommandHandler;
import com.linkgate.netty.protocol.Frame;
import com.linkgate.netty.protocol.MsgType;
import com.linkgate.netty.server.ConnectionRegistry;
import io.netty.channel.ChannelHandlerContext;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 会话续传：客户端上报 {@code lastSeq}，回补该用户收件箱中 {@code seq > lastSeq} 的消息。
 *
 * <p>断线重连后用它无缝补齐缺口，不丢不乱。</p>
 */
public class ResumeHandler implements CommandHandler {

    private static final Logger log = LoggerFactory.getLogger(ResumeHandler.class);

    private final SessionManager sessionManager;
    private final ConnectionRegistry registry;
    private final MessageJournal journal;
    private final MessageSender sender;

    public ResumeHandler(SessionManager sessionManager, ConnectionRegistry registry,
                         MessageJournal journal, MessageSender sender) {
        this.sessionManager = sessionManager;
        this.registry = registry;
        this.journal = journal;
        this.sender = sender;
    }

    @Override
    public void handle(ChannelHandlerContext ctx, Frame frame) {
        long requestSeq = frame.seq();
        try {
            Session session = requireSession(ctx);
            ResumeRequest request = JsonCodec.decode(frame.body(), ResumeRequest.class);
            if (request.lastSeq() < 0) {
                throw new IllegalArgumentException("lastSeq must be >= 0");
            }

            List<Message> missing = journal.since(session.userId(), request.lastSeq());
            int replayed = 0;
            for (Message message : missing) {
                if (sender.send(session, message)) {
                    replayed++;
                }
            }
            log.info("RESUME userId={} lastSeq={} replayed={}", session.userId(), request.lastSeq(), replayed);

            reply(ctx, requestSeq, MsgType.RESUME_ACK,
                    new ResumeResponse(replayed, journal.latestSeq(session.userId())));
        } catch (RuntimeException e) {
            reply(ctx, requestSeq, MsgType.ERROR, new ErrorResponse("RESUME_FAILED: " + e.getMessage()));
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
