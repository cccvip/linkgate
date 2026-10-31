package com.linkgate.core.message;

import java.util.List;

/**
 * 离线消息存储：目标不在线时暂存，用户上线后回补。
 *
 * <p>抽象出接口，内存实现用于开发/单测；持久化实现（MySQL）后续接入。</p>
 */
public interface OfflineStore {

    /** 暂存一条离线消息。 */
    void store(Message message);

    /** 取出并清空某用户的全部离线消息（按入队顺序）。 */
    List<Message> drain(String userId);

    /** 某用户当前暂存的消息数。 */
    int size(String userId);
}
