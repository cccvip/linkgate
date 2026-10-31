package com.linkgate.core.protocol;

/**
 * 点对点聊天请求（客户端 → 服务端）。
 *
 * @param messageId 客户端幂等 id（可空；为空则服务端生成，不做去重）
 * @param toUserId  目标用户标识
 * @param text      消息内容
 */
public record ChatRequest(String messageId, String toUserId, String text) {

    /** 便捷构造：不带幂等 id。 */
    public ChatRequest(String toUserId, String text) {
        this(null, toUserId, text);
    }
}
