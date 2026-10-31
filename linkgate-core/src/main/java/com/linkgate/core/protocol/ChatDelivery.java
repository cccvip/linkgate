package com.linkgate.core.protocol;

/**
 * 投递给目标用户的聊天消息（服务端 → 客户端）。
 *
 * @param fromUserId 发送方用户标识
 * @param text       消息内容
 */
public record ChatDelivery(String fromUserId, String text) {
}
