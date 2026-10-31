package com.linkgate.netty.server;

import com.linkgate.ai.llm.LlmCall;
import com.linkgate.ai.llm.LlmClient;
import com.linkgate.ai.llm.LlmRequest;
import com.linkgate.ai.llm.LlmTokenHandler;
import java.util.List;

/** 测试用 LLM：在独立线程上按脚本推送 token。 */
final class ScriptedLlmClient implements LlmClient {

    private final List<String> tokens;
    private final long delayMillis;

    private volatile boolean cancelled;

    ScriptedLlmClient(List<String> tokens, long delayMillis) {
        this.tokens = tokens;
        this.delayMillis = delayMillis;
    }

    @Override
    public LlmCall streamChat(LlmRequest request, LlmTokenHandler handler) {
        Thread thread = new Thread(() -> run(handler), "scripted-llm");
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
                if (delayMillis > 0) {
                    Thread.sleep(delayMillis);
                }
            }
            if (!cancelled) {
                handler.onComplete();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
