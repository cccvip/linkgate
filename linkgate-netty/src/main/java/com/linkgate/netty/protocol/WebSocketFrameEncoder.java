package com.linkgate.netty.protocol;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import java.util.List;

/** {@link Frame} → WebSocket 文本帧。 */
public final class WebSocketFrameEncoder extends MessageToMessageEncoder<Frame> {

    @Override
    protected void encode(ChannelHandlerContext ctx, Frame frame, List<Object> out) {
        out.add(new TextWebSocketFrame(WebSocketCodec.encode(frame)));
    }
}
