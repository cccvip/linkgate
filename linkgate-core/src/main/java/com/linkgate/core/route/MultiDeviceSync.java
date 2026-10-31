package com.linkgate.core.route;

import com.linkgate.core.message.Message;
import com.linkgate.core.message.MessageJournal;
import com.linkgate.core.message.MessageSender;
import com.linkgate.core.session.Session;
import com.linkgate.core.session.SessionManager;

/**
 * 多端同步：把一次行为的消息同步给同一用户的**其它设备**（来源会话除外）。
 */
public final class MultiDeviceSync {

    private final SessionManager sessionManager;
    private final MessageSender sender;
    private final MessageJournal journal;

    public MultiDeviceSync(SessionManager sessionManager, MessageSender sender) {
        this(sessionManager, sender, null);
    }

    public MultiDeviceSync(SessionManager sessionManager, MessageSender sender, MessageJournal journal) {
        this.sessionManager = sessionManager;
        this.sender = sender;
        this.journal = journal;
    }

    /** 同步给该用户除 {@code originSessionId} 外的其它在线设备，返回投递数。 */
    public int syncToOtherDevices(String userId, String originSessionId, Message template) {
        int delivered = 0;
        for (Session session : sessionManager.sessionsOfUser(userId)) {
            if (session.sessionId().equals(originSessionId)) {
                continue;
            }
            Message perDevice = new Message(
                    template.messageId() + '@' + session.sessionId(),
                    template.fromUserId(),
                    userId,
                    template.seq(),
                    template.payload());
            if (sender.send(session, perDevice)) {
                delivered++;
            }
        }
        return delivered;
    }

    /**
     * 同步一次行为给其它设备：分配 seq / 落日志一次（同一用户收件箱），再发给除来源外的设备。
     *
     * @return 成功投递的设备数
     */
    public int syncToOtherDevices(String userId, String originSessionId, String messageId, byte[] payload) {
        Message message = (journal != null)
                ? journal.append(messageId, userId, userId, payload)
                : new Message(messageId, userId, userId, 0L, payload);
        int delivered = 0;
        for (Session session : sessionManager.sessionsOfUser(userId)) {
            if (session.sessionId().equals(originSessionId)) {
                continue;
            }
            if (sender.send(session, message)) {
                delivered++;
            }
        }
        return delivered;
    }
}
