package com.linkgate.ai.llm;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * token 流：把 {@link LlmClient} 的推送式回调转成拉取式消费。
 *
 * <p>有界缓冲：消费者拉得慢时队列会满，{@link #offer} 随之阻塞，形成对 LLM 上游的背压。</p>
 */
public final class TokenStream implements AutoCloseable {

    private static final long POLL_MILLIS = 100;

    private final BlockingQueue<String> tokens;
    private final AtomicReference<Throwable> error = new AtomicReference<>();
    private final AtomicBoolean completed = new AtomicBoolean();
    private final AtomicBoolean cancelled = new AtomicBoolean();

    private volatile LlmCall call;

    TokenStream(int bufferSize) {
        this.tokens = new ArrayBlockingQueue<>(bufferSize);
    }

    void bind(LlmCall call) {
        this.call = call;
    }

    /**
     * 阻塞取下一个 token。
     *
     * @return 下一个 token；流结束或已取消返回 {@code null}
     * @throws LlmStreamException 上游出错
     */
    public String next() throws InterruptedException {
        while (true) {
            String token = tokens.poll();
            if (token != null) {
                return token;
            }
            Throwable failure = error.get();
            if (failure != null) {
                throw new LlmStreamException(failure);
            }
            if (completed.get() || cancelled.get()) {
                return null;
            }
            String waited = tokens.poll(POLL_MILLIS, TimeUnit.MILLISECONDS);
            if (waited != null) {
                return waited;
            }
        }
    }

    public void cancel() {
        cancelled.set(true);
        LlmCall current = call;
        if (current != null) {
            current.cancel();
        }
    }

    @Override
    public void close() {
        cancel();
    }

    // ---- 由 producer 的回调线程调用 ----

    void offer(String token) {
        while (!cancelled.get()) {
            try {
                if (tokens.offer(token, POLL_MILLIS, TimeUnit.MILLISECONDS)) {
                    return;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    void complete() {
        completed.set(true);
    }

    void fail(Throwable cause) {
        error.set(cause);
    }
}
