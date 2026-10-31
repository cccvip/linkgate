package com.linkgate.ai.stream;

import com.linkgate.core.stream.TokenSink;

/**
 * 会做检查点的 {@link TokenSink}：每片 token 落 {@link StreamCheckpointStore}，再转发给下游。
 *
 * <p>下游（通常是 {@code BackpressureController} → {@code ChannelTokenSink}）负责实际投递。</p>
 */
public final class CheckpointingTokenSink implements TokenSink {

    private final String streamId;
    private final String userId;
    private final StreamCheckpointStore store;
    private final TokenSink delegate;
    private int total;

    public CheckpointingTokenSink(String streamId, String userId, StreamCheckpointStore store, TokenSink delegate) {
        this.streamId = streamId;
        this.userId = userId;
        this.store = store;
        this.delegate = delegate;
    }

    @Override
    public void emit(String token) {
        store.append(streamId, userId, token);
        total++;
        delegate.emit(token);
    }

    @Override
    public void complete() {
        store.complete(streamId, total);
        delegate.complete();
    }

    @Override
    public void error(Throwable error) {
        delegate.error(error);
    }
}
