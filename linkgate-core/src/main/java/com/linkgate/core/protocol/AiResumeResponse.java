package com.linkgate.core.protocol;

/**
 * AI 流续接响应。
 *
 * @param streamId 流标识
 * @param replayed 回补的分片数
 */
public record AiResumeResponse(String streamId, int replayed) {
}
