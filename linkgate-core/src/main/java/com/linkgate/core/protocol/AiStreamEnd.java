package com.linkgate.core.protocol;

/**
 * AI 流式结束载荷。
 *
 * @param streamId    流标识
 * @param totalTokens 本次流式产出的 token 总数
 */
public record AiStreamEnd(String streamId, int totalTokens) {
}
