package com.linkgate.core.protocol;

/**
 * 发起一次 AI 流式生成。
 *
 * @param streamId 客户端指定的流标识（用于续传）
 * @param prompt   提示词
 */
public record AiChatRequest(String streamId, String prompt) {
}
