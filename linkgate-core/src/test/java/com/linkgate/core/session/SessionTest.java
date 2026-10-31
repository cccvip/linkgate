package com.linkgate.core.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.linkgate.core.testing.FakeConnection;
import org.junit.jupiter.api.Test;

class SessionTest {

    private static Session newSession() {
        return new Session("s1", "u1", "d1", new FakeConnection("c1"));
    }

    @Test
    void startsConnected() {
        Session session = newSession();
        assertEquals(SessionState.CONNECTED, session.state());
        assertFalse(session.isClosed());
        assertNotNull(session.createdAt());
    }

    @Test
    void validTransition() {
        Session session = newSession();
        session.transitionTo(SessionState.AUTHENTICATED);
        assertEquals(SessionState.AUTHENTICATED, session.state());
    }

    @Test
    void illegalTransitionThrows() {
        Session session = newSession();
        assertThrows(IllegalStateException.class, () -> session.transitionTo(SessionState.RECONNECTING));
    }

    @Test
    void closedReflectsState() {
        Session session = newSession();
        session.transitionTo(SessionState.CLOSED);
        assertTrue(session.isClosed());
    }

    @Test
    void touchDoesNotMoveBackwards() {
        Session session = newSession();
        session.touch();
        assertFalse(session.lastActiveAt().isBefore(session.createdAt()));
    }

    @Test
    void rejectsNullArguments() {
        assertThrows(NullPointerException.class, () -> new Session(null, "u", "d", new FakeConnection("c")));
        assertThrows(NullPointerException.class, () -> new Session("s", null, "d", new FakeConnection("c")));
        assertThrows(NullPointerException.class, () -> new Session("s", "u", null, new FakeConnection("c")));
        assertThrows(NullPointerException.class, () -> new Session("s", "u", "d", null));
    }
}
