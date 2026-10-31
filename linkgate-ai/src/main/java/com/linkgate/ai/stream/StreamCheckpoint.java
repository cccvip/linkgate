package com.linkgate.ai.stream;

import java.time.Instant;

/**
 * 流式进度快照：用于断线后按 {@code lastIndex} 续接。
 *
 * @param streamId    流标识
 * @param userId      该流归属用户
 * @param lastIndex   最后一片的 index（尚无分片为 -1）
 * @param totalTokens 完成时的 token 总数（未完成 -1）
 * @param completed   是否已完成
 * @param updatedAt   快照时间
 */
public record StreamCheckpoint(String streamId, String userId, int lastIndex, int totalTokens,
                               boolean completed, Instant updatedAt) {
}
