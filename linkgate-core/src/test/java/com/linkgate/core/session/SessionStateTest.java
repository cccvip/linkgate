package com.linkgate.core.session;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SessionStateTest {

    @Test
    void connectedTransitions() {
        assertTrue(SessionState.CONNECTED.canTransitionTo(SessionState.AUTHENTICATED));
        assertTrue(SessionState.CONNECTED.canTransitionTo(SessionState.CLOSED));
        assertFalse(SessionState.CONNECTED.canTransitionTo(SessionState.RECONNECTING));
        assertFalse(SessionState.CONNECTED.canTransitionTo(SessionState.CONNECTED));
    }

    @Test
    void authenticatedTransitions() {
        assertTrue(SessionState.AUTHENTICATED.canTransitionTo(SessionState.RECONNECTING));
        assertTrue(SessionState.AUTHENTICATED.canTransitionTo(SessionState.CLOSED));
        assertFalse(SessionState.AUTHENTICATED.canTransitionTo(SessionState.CONNECTED));
    }

    @Test
    void reconnectingTransitions() {
        assertTrue(SessionState.RECONNECTING.canTransitionTo(SessionState.AUTHENTICATED));
        assertTrue(SessionState.RECONNECTING.canTransitionTo(SessionState.CLOSED));
    }

    @Test
    void closedIsTerminal() {
        assertTrue(SessionState.CLOSED.isTerminal());
        assertFalse(SessionState.CONNECTED.isTerminal());
        for (SessionState target : SessionState.values()) {
            assertFalse(SessionState.CLOSED.canTransitionTo(target));
        }
    }

    @Test
    void nullTargetIsRejected() {
        assertFalse(SessionState.CONNECTED.canTransitionTo(null));
    }
}
