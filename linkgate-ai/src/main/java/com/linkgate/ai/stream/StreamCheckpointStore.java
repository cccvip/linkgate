package com.linkgate.ai.stream;

import java.util.List;
import java.util.Optional;

/**
 * 流式检查点存储：按流保存已产出的分片，支持按 index 回补。
 *
 * <p>与 T2.8 的会话续传同构，只是对象换成了 AI 流的 token 分片。</p>
 */
public interface StreamCheckpointStore {

    /** 追加一片 token，返回其 index（从 0 递增）。 */
    int append(String streamId, String userId, String token);

    /** 返回 {@code index > fromIndexExclusive} 的分片（按序）。 */
    List<String> since(String streamId, int fromIndexExclusive);

    /** 标记流完成并记录总数。 */
    void complete(String streamId, int totalTokens);

    Optional<StreamCheckpoint> find(String streamId);

    void remove(String streamId);
}
