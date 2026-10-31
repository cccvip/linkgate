package com.linkgate.netty.stream;

import com.linkgate.core.stream.TokenSink;
import io.netty.channel.Channel;
import java.util.function.BooleanSupplier;

/**
 * 背压控制：向会话写 token 前先确认 channel 可写，不可写则**阻塞桥接线程**等待。
 *
 * <p>阻塞会经有界 {@code TokenStream} 一路反压回 LLM，从而「慢客户端不拖垮服务」——
 * 既不会在 Netty 写缓冲里堆积，也不会把 token 堆在内存里。</p>
 *
 * <p>连接已断则不再等待（内容仍记在检查点，交由 AI_RESUME 续接）；等待超过上限则抛错中止该流。</p>
 */
public final class BackpressureController implements TokenSink {

    private static final long DEFAULT_POLL_MILLIS = 10;
    private static final long DEFAULT_MAX_WAIT_MILLIS = 30_000;

    private final BooleanSupplier writable;
    private final BooleanSupplier active;
    private final TokenSink delegate;
    private final long maxWaitMillis;
    private final long pollMillis;

    public BackpressureController(Channel channel, TokenSink delegate) {
        this(channel::isWritable, channel::isActive, delegate, DEFAULT_MAX_WAIT_MILLIS, DEFAULT_POLL_MILLIS);
    }

    BackpressureController(BooleanSupplier writable, BooleanSupplier active, TokenSink delegate,
                           long maxWaitMillis, long pollMillis) {
        this.writable = writable;
        this.active = active;
        this.delegate = delegate;
        this.maxWaitMillis = maxWaitMillis;
        this.pollMillis = pollMillis;
    }

    public boolean isWritable() {
        return writable.getAsBoolean();
    }

    @Override
    public void emit(String token) {
        awaitWritable();
        delegate.emit(token);
    }

    @Override
    public void complete() {
        delegate.complete();
    }

    @Override
    public void error(Throwable error) {
        delegate.error(error);
    }

    private void awaitWritable() {
        if (writable.getAsBoolean()) {
            return;
        }
        long deadline = System.currentTimeMillis() + maxWaitMillis;
        while (!writable.getAsBoolean()) {
            if (!active.getAsBoolean()) {
                return;   // 连接已断：不再等待，交由下游处理（记检查点 / 跳过写入）
            }
            if (System.currentTimeMillis() > deadline) {
                throw new IllegalStateException("channel not writable after " + maxWaitMillis + "ms");
            }
            try {
                Thread.sleep(pollMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while waiting for writability", e);
            }
        }
    }
}
