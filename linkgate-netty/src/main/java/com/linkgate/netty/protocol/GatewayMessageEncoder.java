package com.linkgate.netty.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import java.util.List;

/**
 * {@link Frame} → 帧 编码：按固定帧头布局写出字节。
 */
public class GatewayMessageEncoder extends MessageToMessageEncoder<Frame> {

    @Override
    protected void encode(ChannelHandlerContext ctx, Frame frame, List<Object> out) {
        ByteBuf buf = ctx.alloc().buffer(Frame.HEADER_LENGTH + frame.bodyLength());
        buf.writeShort(Frame.MAGIC);
        buf.writeByte(Frame.VERSION);
        buf.writeByte(frame.flags());
        buf.writeByte(frame.type());
        buf.writeByte(0); // reserved
        buf.writeLong(frame.seq());
        buf.writeInt(frame.bodyLength());
        buf.writeBytes(frame.body());
        out.add(buf);
    }
}
