package com.linkgate.store.mysql;

import com.linkgate.core.message.Message;
import com.linkgate.core.message.MessageLog;
import java.util.List;

/**
 * MySQL 版消息日志：把带 seq 的消息持久化，重启 / 多节点可回补。
 *
 * <p>流 id 约定为 {@code inbox:<userId>}（与 {@code MessageJournal} 一致）。</p>
 */
public final class MysqlMessageLog implements MessageLog {

    private static final String INBOX_PREFIX = "inbox:";

    private final MessageDao dao;

    public MysqlMessageLog(MessageDao dao) {
        this.dao = dao;
    }

    @Override
    public void append(String streamId, Message message) {
        dao.insert(message);
    }

    @Override
    public List<Message> since(String streamId, long fromSeqExclusive) {
        return dao.findSince(userIdOf(streamId), fromSeqExclusive);
    }

    @Override
    public long latestSeq(String streamId) {
        return dao.latestSeq(userIdOf(streamId));
    }

    @Override
    public int size(String streamId) {
        return (int) dao.countTo(userIdOf(streamId));
    }

    private static String userIdOf(String streamId) {
        return streamId.startsWith(INBOX_PREFIX) ? streamId.substring(INBOX_PREFIX.length()) : streamId;
    }
}
