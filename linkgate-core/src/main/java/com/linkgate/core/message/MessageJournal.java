package com.linkgate.core.message;

import java.util.List;
import java.util.UUID;

/**
 * 消息日志服务：为"发往某用户"的消息分配服务端顺序号并落日志，供会话续传回补。
 *
 * <p>流按收件箱划分（{@code inbox:<userId>}），因此客户端只需记住"最后收到的 seq"即可要回合口。</p>
 */
public final class MessageJournal {

    private static final String INBOX_PREFIX = "inbox:";

    private final Sequencer sequencer;
    private final MessageLog log;

    public MessageJournal(Sequencer sequencer, MessageLog log) {
        this.sequencer = sequencer;
        this.log = log;
    }

    /** 记一条发往 {@code toUserId} 的消息：分配服务端 seq、落日志，返回可投递的 {@link Message}。 */
    public Message append(String fromUserId, String toUserId, byte[] payload) {
        return append(UUID.randomUUID().toString(), fromUserId, toUserId, payload);
    }

    /** 记一条指定 messageId 的消息（客户端可传幂等 id）。 */
    public Message append(String messageId, String fromUserId, String toUserId, byte[] payload) {
        String streamId = streamId(toUserId);
        long seq = sequencer.next(streamId);
        Message message = new Message(messageId, fromUserId, toUserId, seq, payload);
        log.append(streamId, message);
        return message;
    }

    /** 回补 {@code userId} 收件箱中 {@code seq > lastSeq} 的消息。 */
    public List<Message> since(String userId, long lastSeq) {
        return log.since(streamId(userId), lastSeq);
    }

    /** {@code userId} 收件箱当前最大 seq。 */
    public long latestSeq(String userId) {
        return log.latestSeq(streamId(userId));
    }

    private static String streamId(String userId) {
        return INBOX_PREFIX + userId;
    }
}
