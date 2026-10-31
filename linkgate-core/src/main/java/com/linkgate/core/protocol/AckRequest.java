package com.linkgate.core.protocol;

/**
 * 投递确认（接收方 → 服务端）。
 *
 * @param messageId 已收到的消息 id
 */
public record AckRequest(String messageId) {
}
