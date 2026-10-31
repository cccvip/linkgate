package com.linkgate.core.message;

import com.linkgate.core.session.Session;

/**
 * 出站通道抽象：由接入层实现，把消息写到具体连接。
 *
 * <p>core 不依赖 Netty，所以投递的“最后一公里”由此接口反转出去。</p>
 */
@FunctionalInterface
public interface MessageSender {

    /** 尝试投递到该会话；成功返回 true。 */
    boolean send(Session session, Message message);
}
