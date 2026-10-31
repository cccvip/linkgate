package com.linkgate.core.session;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话管理：创建、查找、销毁会话，并维护 {@code (userId, deviceId) → 会话} 与 {@code userId → 会话} 索引。
 *
 * <p>线程安全；纯逻辑，可脱离网络单测。</p>
 */
public final class SessionManager {

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Map<String, Session> byDevice = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> byUser = new ConcurrentHashMap<>();

    /**
     * 创建会话；sessionId 重复时抛 {@link IllegalStateException}。
     */
    public Session create(String sessionId, String userId, String deviceId, SessionConnection connection) {
        Session session = new Session(sessionId, userId, deviceId, connection);
        if (sessions.putIfAbsent(sessionId, session) != null) {
            throw new IllegalStateException("duplicate sessionId: " + sessionId);
        }
        byDevice.put(deviceKey(userId, deviceId), session);
        byUser.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(sessionId);
        return session;
    }

    public Optional<Session> find(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    /** 查找会话，缺失时抛 {@link NoSuchElementException}。 */
    public Session require(String sessionId) {
        Session session = sessions.get(sessionId);
        if (session == null) {
            throw new NoSuchElementException("no session: " + sessionId);
        }
        return session;
    }

    public Optional<Session> findByDevice(String userId, String deviceId) {
        return Optional.ofNullable(byDevice.get(deviceKey(userId, deviceId)));
    }

    /** 某用户当前的全部会话（多端）。 */
    public Collection<Session> sessionsOfUser(String userId) {
        Set<String> ids = byUser.get(userId);
        if (ids == null) {
            return List.of();
        }
        List<Session> result = new ArrayList<>(ids.size());
        for (String id : ids) {
            Session session = sessions.get(id);
            if (session != null) {
                result.add(session);
            }
        }
        return result;
    }

    /** 销毁会话：状态置 CLOSED 并关闭连接。返回是否存在。 */
    public boolean destroy(String sessionId) {
        Session session = sessions.remove(sessionId);
        if (session == null) {
            return false;
        }
        byDevice.remove(deviceKey(session.userId(), session.deviceId()), session);
        removeFromUserIndex(session.userId(), sessionId);
        if (!session.isClosed()) {
            session.transitionTo(SessionState.CLOSED);
        }
        session.connection().close();
        return true;
    }

    private void removeFromUserIndex(String userId, String sessionId) {
        Set<String> ids = byUser.get(userId);
        if (ids != null && ids.remove(sessionId) && ids.isEmpty()) {
            byUser.remove(userId, ids);
        }
    }

    public Collection<Session> all() {
        return List.copyOf(sessions.values());
    }

    public int size() {
        return sessions.size();
    }

    private static String deviceKey(String userId, String deviceId) {
        return userId + '\u0000' + deviceId;
    }
}
