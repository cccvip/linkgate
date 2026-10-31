package com.linkgate.core.message;

import java.util.Objects;

/**
 * 消息信封：消息层的统一投递单元（与传输无关）。
 *
 * @param messageId  全局唯一消息 id（去重用）
 * @param fromUserId 发送方用户
 * @param toUserId   接收方用户
 * @param seq        会话顺序号（由 Sequencer 填充）
 * @param payload    业务载荷（已序列化）
 */
public record Message(String messageId, String fromUserId, String toUserId, long seq, byte[] payload) {

    public Message {
        Objects.requireNonNull(messageId, "messageId");
        Objects.requireNonNull(toUserId, "toUserId");
        payload = payload == null ? new byte[0] : payload;
    }
}
