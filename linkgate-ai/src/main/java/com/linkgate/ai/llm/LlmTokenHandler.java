package com.linkgate.ai.llm;

/**
 * token 流回调。
 *
 * <p>回调在 LLM 客户端的 IO 线程上触发，实现应尽快返回、不要阻塞。</p>
 */
public interface LlmTokenHandler {

    void onToken(String token);

    void onComplete();

    void onError(Throwable error);
}
