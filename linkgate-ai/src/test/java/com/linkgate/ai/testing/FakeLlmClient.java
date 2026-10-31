package com.linkgate.ai.testing;

import com.linkgate.ai.llm.LlmCall;
import com.linkgate.ai.llm.LlmClient;
import com.linkgate.ai.llm.LlmRequest;
import com.linkgate.ai.llm.LlmTokenHandler;
import java.util.List;

/**
 * 测试用 LLM 客户端：在独立线程上按序推送 token，可选延迟与失败。
 */
public final class FakeLlmClient implements LlmClient {

    private final List<String> tokens;
    private final boolean fail;
    private final long tokenDelayMillis;

    private volatile boolean cancelled;

    public FakeLlmClient(List<String> tokens, boolean fail) {
        this(tokens, fail, 0);
    }

    public FakeLlmClient(List<String> tokens, boolean fail, long tokenDelayMillis) {
        this.tokens = tokens;
        this.fail = fail;
        this.tokenDelayMillis = tokenDelayMillis;
    }

    @Override
    public LlmCall streamChat(LlmRequest request, LlmTokenHandler handler) {
        Thread thread = new Thread(() -> run(handler), "fake-llm");
        thread.setDaemon(true);
        thread.start();
        return new LlmCall() {
            @Override
            public void cancel() {
                cancelled = true;
            }

            @Override
            public boolean isCancelled() {
                return cancelled;
            }
        };
    }

    private void run(LlmTokenHandler handler) {
        try {
            for (String token : tokens) {
                if (cancelled) {
                    return;
                }
                handler.onToken(token);
                if (tokenDelayMillis > 0) {
                    Thread.sleep(tokenDelayMillis);
                }
            }
            if (cancelled) {
                return;
            }
            if (fail) {
                handler.onError(new RuntimeException("llm boom"));
            } else {
                handler.onComplete();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
