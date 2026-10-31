package com.linkgate.core.session;

import com.linkgate.core.message.RetryQueue;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 重连策略：保证同一设备幂等重连，不产生重复会话。
 *
 * <ul>
 *   <li>{@link #connect}：登录 / 重连。同 (userId, deviceId) 已有会话则接管续接，否则新建；</li>
 *   <li>{@link #onDisconnect}：断线不立刻销毁，先置 RECONNECTING 进入宽限期，超时才销毁。</li>
 * </ul>
 */
public final class ReconnectPolicy {

    public static final long DEFAULT_GRACE_MILLIS = 60_000;

    private final SessionManager sessionManager;
    private final RetryQueue retryQueue;
    private final long graceMillis;
    private final Supplier<String> sessionIdGenerator;
    private final Map<String, RetryQueue.RetryHandle> pendingDestroy = new ConcurrentHashMap<>();

    public ReconnectPolicy(SessionManager sessionManager, RetryQueue retryQueue, long graceMillis) {
        this(sessionManager, retryQueue, graceMillis, () -> UUID.randomUUID().toString());
    }

    public ReconnectPolicy(SessionManager sessionManager, RetryQueue retryQueue, long graceMillis,
                           Supplier<String> sessionIdGenerator) {
        this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager");
        this.retryQueue = Objects.requireNonNull(retryQueue, "retryQueue");
        this.graceMillis = graceMillis;
        this.sessionIdGenerator = Objects.requireNonNull(sessionIdGenerator, "sessionIdGenerator");
    }

    /** 登录 / 重连：同设备已有会话则接管续接，否则新建。 */
    public Session connect(String userId, String deviceId, SessionConnection connection) {
        Optional<Session> existing = sessionManager.findByDevice(userId, deviceId);
        if (existing.isEmpty()) {
            Session session = sessionManager.create(sessionIdGenerator.get(), userId, deviceId, connection);
            session.transitionTo(SessionState.AUTHENTICATED);
            return session;
        }

        Session session = existing.get();
        cancelPendingDestroy(session.sessionId());
        SessionConnection previous = session.connection();
        session.rebind(connection);
        if (session.state() == SessionState.RECONNECTING) {
            session.transitionTo(SessionState.AUTHENTICATED);
        }
        if (previous != connection && previous.isActive()) {
            previous.close();
        }
        return session;
    }

    /** 连接断开：置 RECONNECTING 并安排宽限期销毁（期间可被 {@link #connect} 续接取消）。 */
    public void onDisconnect(String sessionId) {
        Optional<Session> maybe = sessionManager.find(sessionId);
        if (maybe.isEmpty()) {
            return;
        }
        Session session = maybe.get();
        if (!session.state().canTransitionTo(SessionState.RECONNECTING)) {
            return;
        }
        session.transitionTo(SessionState.RECONNECTING);
        RetryQueue.RetryHandle handle = retryQueue.schedule(() -> {
            pendingDestroy.remove(sessionId);
            sessionManager.destroy(sessionId);
        }, graceMillis, TimeUnit.MILLISECONDS);
        pendingDestroy.put(sessionId, handle);
    }

    private void cancelPendingDestroy(String sessionId) {
        RetryQueue.RetryHandle handle = pendingDestroy.remove(sessionId);
        if (handle != null) {
            handle.cancel();
        }
    }
}
