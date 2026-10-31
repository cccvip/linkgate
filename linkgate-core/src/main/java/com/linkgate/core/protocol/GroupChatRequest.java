package com.linkgate.core.protocol;

/**
 * 群消息（客户端 → 服务端）。
 *
 * @param messageId 幂等 id（可空）
 * @param groupId   群标识
 * @param text      内容
 */
public record GroupChatRequest(String messageId, String groupId, String text) {
}
