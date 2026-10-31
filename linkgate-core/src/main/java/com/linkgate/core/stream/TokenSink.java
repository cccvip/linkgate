package com.linkgate.core.stream;

/**
 * token 出站口（端口）：AI 层把 token 交给它写到某个会话。
 *
 * <p>由接入层实现（例如写成 AI_STREAM 帧发到 channel）；与 {@code MessageSender} 同属
 * "core 定义端口、接入层实现" 的模式，避免 core / 接入反向依赖 AI 层。</p>
 *
 * <p>实现需自行处理背压：慢客户端下 {@link #emit} 允许阻塞，从而反向抑制 token 生产。</p>
 */
public interface TokenSink {

    void emit(String token);

    void complete();

    void error(Throwable error);
}
