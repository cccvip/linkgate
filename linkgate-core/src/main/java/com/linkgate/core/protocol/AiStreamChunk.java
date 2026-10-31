package com.linkgate.core.protocol;

/**
 * AI 流式分片载荷。
 *
 * @param streamId 流标识
 * @param index    分片序号（从 0 递增，也用作续传定位）
 * @param token     本片 token
 */
public record AiStreamChunk(String streamId, int index, String token) {
}
