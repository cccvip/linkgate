package com.linkgate.netty.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.CorruptedFrameException;
import io.netty.handler.codec.MessageToMessageDecoder;
import java.util.List;

/**
 * 帧 → {@link Frame} 解码：解析帧头各字段，产出协议层的 {@link Frame} 对象。
 *
 * <p>上游必须已是「一个完整帧」的 {@link ByteBuf}（由 {@link ProtocolDecoder} 保证）。</p>
 */
public class GatewayMessageDecoder extends MessageToMessageDecoder<ByteBuf> {

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        if (in.readableBytes() < Frame.HEADER_LENGTH) {
            throw new CorruptedFrameException("frame shorter than header: " + in.readableBytes());
        }

        in.readShort();               // magic（ProtocolDecoder 已校验）
        byte version = in.readByte();
        byte flags = in.readByte();
        byte type = in.readByte();
        in.readByte();                // reserved
        long seq = in.readLong();
        int bodyLength = in.readInt();

        if (version != Frame.VERSION) {
            throw new CorruptedFrameException("unsupported version: " + version);
        }
        if (bodyLength < 0 || bodyLength > Frame.MAX_BODY_LENGTH) {
            throw new CorruptedFrameException("invalid body length: " + bodyLength);
        }
        if (in.readableBytes() < bodyLength) {
            throw new CorruptedFrameException(
                    "body truncated: need " + bodyLength + ", have " + in.readableBytes());
        }

        byte[] body = new byte[bodyLength];
        in.readBytes(body);
        out.add(new Frame(flags, type, seq, body));
    }
}
