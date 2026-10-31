package com.linkgate.ai.llm;

/** 一次流式调用的句柄，用于取消。 */
public interface LlmCall {

    void cancel();

    boolean isCancelled();
}
