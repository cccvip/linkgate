package com.linkgate.core.message;

import java.util.List;

/**
 * 消息日志：按「流」（如某用户的收件箱）保存带服务端顺序号的消息，支持按 seq 回补。
 *
 * <p>是会话续传的来源真相；内存实现用于开发/单测，持久化实现后续接入。</p>
 */
public interface MessageLog {

    /** 追加一条已分配 seq 的消息。 */
    void append(String streamId, Message message);

    /** 返回该流中 {@code seq > fromSeqExclusive} 的消息（按 seq 升序）。 */
    List<Message> since(String streamId, long fromSeqExclusive);

    /** 该流当前最大 seq（空流返回 0）。 */
    long latestSeq(String streamId);

    /** 该流当前消息数。 */
    int size(String streamId);
}
