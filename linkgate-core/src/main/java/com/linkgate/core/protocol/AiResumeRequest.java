package com.linkgate.core.protocol;

/**
 * 断线后请求续接某个 AI 流。
 *
 * @param streamId  流标识
 * @param lastIndex 客户端已收到的最后一片 index（没收到传 -1）
 */
public record AiResumeRequest(String streamId, int lastIndex) {
}
