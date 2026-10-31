package com.linkgate.core.message;

/**
 * 投递结果。
 *
 * @param messageId      消息 id
 * @param deliveredCount 成功投递的会话数（多端会 &gt; 1）
 */
public record DeliveryResult(String messageId, int deliveredCount) {

    public boolean delivered() {
        return deliveredCount > 0;
    }
}
