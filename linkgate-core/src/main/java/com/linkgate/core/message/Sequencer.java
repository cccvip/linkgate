package com.linkgate.core.message;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 会话顺序号：为每个会话维护单调递增的序列号。
 *
 * <p>会话 key 用 {@link #conversationKey} 归一化，保证 1:1 场景下双方算出同一个 key。</p>
 */
public final class Sequencer {

    private final Map<String, AtomicLong> sequences = new ConcurrentHashMap<>();

    /** 取下一个顺序号（从 1 开始，线程安全、无重复）。 */
    public long next(String conversationId) {
        return sequences.computeIfAbsent(conversationId, k -> new AtomicLong()).incrementAndGet();
    }

    /** 当前最大顺序号（未出现过返回 0）。 */
    public long current(String conversationId) {
        AtomicLong counter = sequences.get(conversationId);
        return counter == null ? 0L : counter.get();
    }

    /** 重置某会话的顺序号（续传 / 重连时按需调用）。 */
    public void reset(String conversationId) {
        sequences.remove(conversationId);
    }

    /** 归一化 1:1 会话 key：与参数顺序无关。 */
    public static String conversationKey(String userA, String userB) {
        return userA.compareTo(userB) <= 0 ? userA + '|' + userB : userB + '|' + userA;
    }
}
