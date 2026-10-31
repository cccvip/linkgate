package com.linkgate.netty.server;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import com.linkgate.netty.protocol.Frame;
import com.linkgate.netty.protocol.MsgType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 空闲处理：配合 {@link io.netty.handler.timeout.IdleStateHandler} 使用。
 *
 * <ul>
 *   <li>读空闲 → 判定对端失联，关闭连接；</li>
 *   <li>写空闲 → 主动下发心跳，探测 / 维持链路。</li>
 * </ul>
 */
public class IdleHeartbeatHandler extends ChannelInboundHandlerAdapter {

    private static final Logger log = LoggerFactory.getLogger(IdleHeartbeatHandler.class);

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent idle) {
            if (idle.state() == IdleState.READER_IDLE) {
                log.info("reader idle, closing connection {}", ctx.channel());
                ctx.close();
                return;
            }
            if (idle.state() == IdleState.WRITER_IDLE) {
                ctx.writeAndFlush(Frame.of(MsgType.HEARTBEAT.code(), 0L));
                return;
            }
        }
        super.userEventTriggered(ctx, evt);
    }
}
