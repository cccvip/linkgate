package com.linkgate.core.session;

import java.time.Instant;
import java.util.Objects;

/**
 * 会话：一条已登录（或登录中）的连接。
 *
 * <p>不可变身份字段 + 可变状态字段；状态通过 {@link #transitionTo(SessionState)} 受控迁移。</p>
 */
public final class Session {

    private final String sessionId;
    private final String userId;
    private final String deviceId;
    private volatile SessionConnection connection;
    private final Instant createdAt;

    private volatile SessionState state = SessionState.CONNECTED;
    private volatile Instant lastActiveAt;

    public Session(String sessionId, String userId, String deviceId, SessionConnection connection) {
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.userId = Objects.requireNonNull(userId, "userId");
        this.deviceId = Objects.requireNonNull(deviceId, "deviceId");
        this.connection = Objects.requireNonNull(connection, "connection");
        this.createdAt = Instant.now();
        this.lastActiveAt = this.createdAt;
    }

    /**
     * 迁移状态；非法迁移抛 {@link IllegalStateException}。
     */
    public void transitionTo(SessionState target) {
        Objects.requireNonNull(target, "target");
        SessionState current = this.state;
        if (!current.canTransitionTo(target)) {
            throw new IllegalStateException("illegal transition: " + current + " -> " + target);
        }
        this.state = target;
    }

    /** 刷新活跃时间（心跳 / 收到消息时调用）。 */
    public void touch() {
        this.lastActiveAt = Instant.now();
    }

    /** 重新绑定连接（断线重连 / 同设备接管）。 */
    public void rebind(SessionConnection connection) {
        this.connection = Objects.requireNonNull(connection, "connection");
    }

    public String sessionId() {
        return sessionId;
    }

    public String userId() {
        return userId;
    }

    public String deviceId() {
        return deviceId;
    }

    public SessionConnection connection() {
        return connection;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public SessionState state() {
        return state;
    }

    public Instant lastActiveAt() {
        return lastActiveAt;
    }

    public boolean isClosed() {
        return state == SessionState.CLOSED;
    }

    @Override
    public String toString() {
        return "Session{sessionId=" + sessionId
                + ", userId=" + userId
                + ", deviceId=" + deviceId
                + ", state=" + state + '}';
    }
}
