package com.linkgate.netty.server;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 在建连时应用 {@link ConnectionLimiter}：超限直接关闭连接，并在断开时归还许可。
 *
 * <p>被拒绝的连接不再向下游 {@code fireChannelActive}，因此不会登记进注册表。</p>
 */
public class ConnectionLimitHandler extends ChannelInboundHandlerAdapter {

    private static final Logger log = LoggerFactory.getLogger(ConnectionLimitHandler.class);

    private final ConnectionLimiter limiter;
    private boolean acquired;

    public ConnectionLimitHandler(ConnectionLimiter limiter) {
        this.limiter = limiter;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        if (!limiter.tryAcquire()) {
            log.warn("connection rejected (active={}/{}), closing {}",
                    limiter.activeConnections(), limiter.maxConnections(), ctx.channel());
            ctx.close();
            return;
        }
        acquired = true;
        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (acquired) {
            limiter.release();
            acquired = false;
        }
        ctx.fireChannelInactive();
    }
}
