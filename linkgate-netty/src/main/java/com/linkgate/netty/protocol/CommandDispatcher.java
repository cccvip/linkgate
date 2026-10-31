package com.linkgate.netty.protocol;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import java.util.EnumMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 命令分发：按 {@link MsgType} 路由到注册的 {@link CommandHandler}。
 *
 * <p>未知 / 未注册的消息类型只记录日志、不中断连接，保证可扩展与健壮。</p>
 */
public class CommandDispatcher extends SimpleChannelInboundHandler<Frame> {

    private static final Logger log = LoggerFactory.getLogger(CommandDispatcher.class);

    private final Map<MsgType, CommandHandler> handlers = new EnumMap<>(MsgType.class);

    public CommandDispatcher register(MsgType type, CommandHandler handler) {
        handlers.put(type, handler);
        return this;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Frame frame) {
        MsgType type = MsgType.fromCodeOrNull(frame.type());
        if (type == null) {
            log.warn("drop frame with unknown type={} from {}", frame.type() & 0xFF, ctx.channel());
            return;
        }
        CommandHandler handler = handlers.get(type);
        if (handler == null) {
            log.warn("no handler registered for type={} from {}", type, ctx.channel());
            return;
        }
        handler.handle(ctx, frame);
    }
}
