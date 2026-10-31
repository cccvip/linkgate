package com.linkgate.ai.llm;

/**
 * LLM 客户端：发起流式补全，逐 token 回调。
 *
 * <p>非阻塞：{@link #streamChat} 立即返回 {@link LlmCall}，token 通过 {@link LlmTokenHandler} 推送。</p>
 */
public interface LlmClient {

    LlmCall streamChat(LlmRequest request, LlmTokenHandler handler);
}
