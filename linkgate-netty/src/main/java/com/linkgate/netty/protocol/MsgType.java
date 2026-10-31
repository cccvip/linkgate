package com.linkgate.netty.protocol;

/**
 * 消息类型：帧头 {@code type} 字段的语义映射。
 *
 * <p>code 为 1 字节，须全局唯一。</p>
 */
public enum MsgType {

    LOGIN((byte) 1),
    LOGIN_ACK((byte) 2),
    HEARTBEAT((byte) 3),
    HEARTBEAT_ACK((byte) 4),
    CHAT((byte) 5),
    CHAT_ACK((byte) 6),
    ACK((byte) 7),
    AI_STREAM((byte) 8),
    AI_STREAM_END((byte) 9),
    ERROR((byte) 10),
    RESUME((byte) 11),
    RESUME_ACK((byte) 12),
    AI_CHAT((byte) 13),
    AI_RESUME((byte) 14),
    AI_RESUME_ACK((byte) 15),
    GROUP_JOIN((byte) 16),
    GROUP_LEAVE((byte) 17),
    GROUP_CHAT((byte) 18),
    GROUP_ACK((byte) 19),
    MULTI_SYNC((byte) 20),
    MULTI_SYNC_ACK((byte) 21);

    private static final MsgType[] BY_CODE = new MsgType[256];

    static {
        for (MsgType type : values()) {
            BY_CODE[type.code & 0xFF] = type;
        }
    }

    private final byte code;

    MsgType(byte code) {
        this.code = code;
    }

    public byte code() {
        return code;
    }

    /** 未知 code 抛出 {@link IllegalArgumentException}。 */
    public static MsgType fromCode(byte code) {
        MsgType type = BY_CODE[code & 0xFF];
        if (type == null) {
            throw new IllegalArgumentException("unknown MsgType code: " + (code & 0xFF));
        }
        return type;
    }

    /** 未知 code 返回 {@code null}。 */
    public static MsgType fromCodeOrNull(byte code) {
        return BY_CODE[code & 0xFF];
    }
}
