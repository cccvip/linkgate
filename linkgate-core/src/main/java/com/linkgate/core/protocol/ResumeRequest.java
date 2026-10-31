package com.linkgate.core.protocol;

/**
 * 会话续传请求：客户端上报"最后收到的 seq"。
 *
 * @param lastSeq 客户端已收到的最大 seq（全新客户端传 0）
 */
public record ResumeRequest(long lastSeq) {
}
