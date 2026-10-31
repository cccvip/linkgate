package com.linkgate.core.protocol;

/**
 * 多端同步请求：把一次行为同步给同用户的其它设备。
 *
 * @param messageId 幂等 id（可空）
 * @param text      内容
 */
public record MultiSyncRequest(String messageId, String text) {
}
