package com.linkgate.netty.protocol;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import java.util.List;

/** WebSocket 文本帧 → {@link Frame}。 */
public final class WebSocketFrameDecoder extends MessageToMessageDecoder<TextWebSocketFrame> {

    @Override
    protected void decode(ChannelHandlerContext ctx, TextWebSocketFrame msg, List<Object> out) {
        out.add(WebSocketCodec.decode(msg.text()));
    }
}
