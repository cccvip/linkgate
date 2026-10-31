package com.linkgate.core.protocol;

/**
 * 会话续传响应。
 *
 * @param replayed  本次回补的消息数
 * @param latestSeq 服务端该收件箱的当前最大 seq
 */
public record ResumeResponse(int replayed, long latestSeq) {
}
