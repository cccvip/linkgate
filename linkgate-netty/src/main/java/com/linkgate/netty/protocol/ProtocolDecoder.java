package com.linkgate.netty.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.CorruptedFrameException;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;

/**
 * 帧定界解码器：基于 {@link LengthFieldBasedFrameDecoder} 处理 TCP 粘包 / 半包。
 *
 * <p>只负责「切出一个完整帧」并校验魔数，输出仍是包含帧头的 {@link ByteBuf}；
 * 帧头各字段解析为 {@link Frame} 由 {@code GatewayMessageDecoder}（T1.5）完成。</p>
 */
public class ProtocolDecoder extends LengthFieldBasedFrameDecoder {

    public ProtocolDecoder() {
        super(
                Frame.HEADER_LENGTH + Frame.MAX_BODY_LENGTH, // maxFrameLength
                Frame.LENGTH_FIELD_OFFSET,                   // lengthFieldOffset
                Frame.LENGTH_FIELD_LENGTH,                   // lengthFieldLength
                0,                                           // lengthAdjustment（bodyLength 仅为 body）
                0);                                          // initialBytesToStrip（保留完整帧）
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        ByteBuf frame = (ByteBuf) super.decode(ctx, in);
        if (frame == null) {
            return null;
        }
        try {
            short magic = frame.getShort(frame.readerIndex());
            if (magic != Frame.MAGIC) {
                throw new CorruptedFrameException(
                        "invalid magic: 0x" + Integer.toHexString(magic & 0xFFFF));
            }
            return frame;
        } catch (Exception e) {
            frame.release();
            throw e;
        }
    }
}
