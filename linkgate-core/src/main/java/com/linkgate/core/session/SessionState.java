package com.linkgate.core.session;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 会话状态机。
 *
 * <pre>
 *  CONNECTED ──> AUTHENTICATED ──> RECONNECTING ──> AUTHENTICATED
 *      │              │                 │
 *      └──────────────┴─────────────────┴──────> CLOSED（终态）
 * </pre>
 */
public enum SessionState {

    /** TCP 已建立，尚未登录。 */
    CONNECTED,
    /** 已登录，可用于投递。 */
    AUTHENTICATED,
    /** 连接暂时丢失，等待续接。 */
    RECONNECTING,
    /** 已关闭（终态）。 */
    CLOSED;

    private static final Map<SessionState, Set<SessionState>> ALLOWED = Map.of(
            CONNECTED, EnumSet.of(AUTHENTICATED, CLOSED),
            AUTHENTICATED, EnumSet.of(RECONNECTING, CLOSED),
            RECONNECTING, EnumSet.of(AUTHENTICATED, CLOSED),
            CLOSED, EnumSet.noneOf(SessionState.class));

    public boolean canTransitionTo(SessionState target) {
        if (target == null) {
            return false;
        }
        return ALLOWED.get(this).contains(target);
    }

    public boolean isTerminal() {
        return this == CLOSED;
    }
}
