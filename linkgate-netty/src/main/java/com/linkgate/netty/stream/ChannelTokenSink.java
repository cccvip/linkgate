package com.linkgate.netty.stream;

import com.linkgate.core.codec.JsonCodec;
import com.linkgate.core.protocol.AiStreamChunk;
import com.linkgate.core.protocol.AiStreamEnd;
import com.linkgate.core.protocol.ErrorResponse;
import com.linkgate.core.stream.TokenSink;
import com.linkgate.netty.protocol.Frame;
import com.linkgate.netty.protocol.MsgType;
import io.netty.channel.Channel;

/**
 * 把 token 写成 {@code AI_STREAM} 帧发送到会话连接。
 *
 * <p>分片带递增 index；结束时发 {@code AI_STREAM_END}。连接已断时静默跳过
 * （内容仍记在检查点里，由 AI_RESUME 续接）。</p>
 */
public final class ChannelTokenSink implements TokenSink {

    private final String streamId;
    private final Channel channel;
    private int index;

    public ChannelTokenSink(String streamId, Channel channel) {
        this.streamId = streamId;
        this.channel = channel;
    }

    @Override
    public void emit(String token) {
        int current = index++;
        write(MsgType.AI_STREAM, current, new AiStreamChunk(streamId, current, token));
    }

    @Override
    public void complete() {
        write(MsgType.AI_STREAM_END, index, new AiStreamEnd(streamId, index));
    }

    @Override
    public void error(Throwable error) {
        write(MsgType.ERROR, index, new ErrorResponse("AI_STREAM_FAILED: " + error.getMessage()));
    }

    private void write(MsgType type, int seq, Object payload) {
        if (!channel.isActive()) {
            return;
        }
        channel.writeAndFlush(Frame.of(type.code(), seq, JsonCodec.encode(payload)));
    }
}
