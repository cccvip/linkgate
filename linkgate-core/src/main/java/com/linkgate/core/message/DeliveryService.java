package com.linkgate.core.message;

import com.linkgate.core.session.Session;
import com.linkgate.core.session.SessionManager;

/**
 * 投递服务：优先在线投递；目标全部离线时落入 {@link OfflineStore}，待其上线上后回补。
 *
 * <p>纯编排逻辑（查找会话 → 交给 {@link MessageSender}），脱离网络可单测。
 * ACK / 顺序号 / 去重会在这一层继续组合进来。</p>
 */
public class DeliveryService {

    private final SessionManager sessionManager;
    private final MessageSender sender;
    private final OfflineStore offlineStore;

    public DeliveryService(SessionManager sessionManager, MessageSender sender) {
        this(sessionManager, sender, null);
    }

    public DeliveryService(SessionManager sessionManager, MessageSender sender, OfflineStore offlineStore) {
        this.sessionManager = sessionManager;
        this.sender = sender;
        this.offlineStore = offlineStore;
    }

    /** 在线投递；目标"完全没有会话"时才暂存离线（有会话但连接断开由消息日志 + RESUME 兜底）。 */
    public DeliveryResult deliver(Message message) {
        int delivered = deliverOnline(message);
        if (delivered == 0 && offlineStore != null
                && sessionManager.sessionsOfUser(message.toUserId()).isEmpty()) {
            offlineStore.store(message);
        }
        return new DeliveryResult(message.messageId(), delivered);
    }

    /** 用户上线时回补离线消息，返回成功投递的消息数。 */
    public int replayOffline(String userId) {
        if (offlineStore == null) {
            return 0;
        }
        int replayed = 0;
        for (Message message : offlineStore.drain(userId)) {
            if (deliverOnline(message) > 0) {
                replayed++;
            }
        }
        return replayed;
    }

    private int deliverOnline(Message message) {
        int delivered = 0;
        for (Session session : sessionManager.sessionsOfUser(message.toUserId())) {
            if (sender.send(session, message)) {
                delivered++;
            }
        }
        return delivered;
    }
}
