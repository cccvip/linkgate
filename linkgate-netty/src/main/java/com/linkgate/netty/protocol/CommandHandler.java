package com.linkgate.netty.protocol;

import io.netty.channel.ChannelHandlerContext;

/**
 * 单条命令的业务处理器。
 */
@FunctionalInterface
public interface CommandHandler {

    void handle(ChannelHandlerContext ctx, Frame frame);
}
