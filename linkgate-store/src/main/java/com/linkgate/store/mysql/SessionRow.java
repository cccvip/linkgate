package com.linkgate.store.mysql;

/**
 * 会话持久化行。
 */
public record SessionRow(String sessionId, String userId, String deviceId, String state) {
}
