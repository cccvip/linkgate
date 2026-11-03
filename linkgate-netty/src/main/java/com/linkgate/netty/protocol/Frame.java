package com.linkgate.netty.protocol;

import java.util.Arrays;

/**
 * 协议帧：网关与业务层之间传递的单元（经 WebSocket 以 JSON 信封传输）。
 */
public final class Frame {

    public static final byte FLAG_NONE = 0;
    /** 高优先级，优先下发。 */
    public static final byte FLAG_URGENT = 1;
    /** 流式分片的最后一帧。 */
    public static final byte FLAG_END_OF_STREAM = 1 << 1;

    private static final byte[] EMPTY_BODY = new byte[0];

    private final byte flags;
    private final byte type;
    private final long seq;
    private final byte[] body;

    public Frame(byte flags, byte type, long seq, byte[] body) {
        this.flags = flags;
        this.type = type;
        this.seq = seq;
        this.body = body == null ? EMPTY_BODY : body;
    }

    /** 便捷构造：无 flags 的普通帧。 */
    public static Frame of(byte type, long seq, byte[] body) {
        return new Frame(FLAG_NONE, type, seq, body);
    }

    /** 便捷构造：空 body 帧（如 HEARTBEAT）。 */
    public static Frame of(byte type, long seq) {
        return new Frame(FLAG_NONE, type, seq, EMPTY_BODY);
    }

    public byte flags() {
        return flags;
    }

    public byte type() {
        return type;
    }

    public long seq() {
        return seq;
    }

    /**
     * 返回 body 原始数组（不拷贝，避免吞吐路径上的额外分配）。
     * 调用方不得修改返回内容。
     */
    public byte[] body() {
        return body;
    }

    public int bodyLength() {
        return body.length;
    }

    public boolean hasFlag(byte flag) {
        return (flags & flag) != 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Frame other)) {
            return false;
        }
        return flags == other.flags
                && type == other.type
                && seq == other.seq
                && Arrays.equals(body, other.body);
    }

    @Override
    public int hashCode() {
        int result = Byte.hashCode(flags);
        result = 31 * result + Byte.hashCode(type);
        result = 31 * result + Long.hashCode(seq);
        result = 31 * result + Arrays.hashCode(body);
        return result;
    }

    @Override
    public String toString() {
        return "Frame{type=" + (type & 0xFF)
                + ", flags=" + (flags & 0xFF)
                + ", seq=" + seq
                + ", bodyLength=" + body.length + '}';
    }
}
