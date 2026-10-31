package com.linkgate.core.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.linkgate.core.message.RetryQueue;
import com.linkgate.core.testing.FakeConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ReconnectPolicyTest {

    private final SessionManager sessionManager = new SessionManager();
    private final RetryQueue retryQueue = new RetryQueue();

    private ReconnectPolicy policy(long graceMillis) {
        return new ReconnectPolicy(sessionManager, retryQueue, graceMillis);
    }

    @AfterEach
    void tearDown() {
        retryQueue.close();
    }

    @Test
    void firstConnectCreatesAuthenticatedSession() {
        Session session = policy(1000).connect("u1", "d1", new FakeConnection("c1"));

        assertEquals(SessionState.AUTHENTICATED, session.state());
        assertEquals(1, sessionManager.size());
    }

    @Test
    void repeatedConnectReusesSessionForSameDevice() {
        ReconnectPolicy policy = policy(1000);
        FakeConnection first = new FakeConnection("c1");
        Session a = policy.connect("u1", "d1", first);
        FakeConnection second = new FakeConnection("c2");
        Session b = policy.connect("u1", "d1", second);

        assertSame(a, b, "同设备重登应复用同一会话");
        assertEquals(1, sessionManager.size(), "不应产生重复会话");
        assertSame(second, b.connection(), "应接管新连接");
        assertTrue(first.isClosed(), "旧连接应被关闭");
    }

    @Test
    void differentDevicesGetDifferentSessions() {
        ReconnectPolicy policy = policy(1000);
        Session a = policy.connect("u1", "d1", new FakeConnection("c1"));
        Session b = policy.connect("u1", "d2", new FakeConnection("c2"));

        assertNotSame(a, b);
        assertEquals(2, sessionManager.sessionsOfUser("u1").size());
    }

    @Test
    void disconnectKeepsSessionDuringGracePeriod() {
        ReconnectPolicy policy = policy(10_000);
        Session session = policy.connect("u1", "d1", new FakeConnection("c1"));

        policy.onDisconnect(session.sessionId());

        assertEquals(SessionState.RECONNECTING, session.state());
        assertEquals(1, sessionManager.size(), "宽限期内会话仍保留");
    }

    @Test
    void reconnectWithinGraceResumesAndCancelsDestroy() throws InterruptedException {
        ReconnectPolicy policy = policy(200);
        Session first = policy.connect("u1", "d1", new FakeConnection("c1"));
        policy.onDisconnect(first.sessionId());

        Session resumed = policy.connect("u1", "d1", new FakeConnection("c2"));
        assertSame(first, resumed);
        assertEquals(SessionState.AUTHENTICATED, resumed.state());

        Thread.sleep(400);
        assertEquals(1, sessionManager.size(), "续接后不应被宽限期销毁");
        assertEquals(SessionState.AUTHENTICATED, sessionManager.require(first.sessionId()).state());
    }

    @Test
    void sessionDestroyedAfterGraceExpires() throws InterruptedException {
        ReconnectPolicy policy = policy(100);
        Session session = policy.connect("u1", "d1", new FakeConnection("c1"));

        policy.onDisconnect(session.sessionId());
        assertEquals(1, sessionManager.size());

        Thread.sleep(400);
        assertEquals(0, sessionManager.size(), "宽限期后应销毁会话");
    }
}
