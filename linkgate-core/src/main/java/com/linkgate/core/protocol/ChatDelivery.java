package com.linkgate.core.protocol;

/**
 * 投递给目标用户的聊天消息（服务端 → 客户端）。
 *
 * @param messageId  消息 id（接收方据此回 ACK）
 * @param groupId    群组 id（群聊时非空；单聊为 null）
 * @param fromUserId 发送方用户标识
 * @param text       消息内容
 */
public record ChatDelivery(String messageId, String groupId, String fromUserId, String text) {
}
