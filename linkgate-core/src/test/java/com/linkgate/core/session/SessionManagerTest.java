package com.linkgate.core.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.linkgate.core.testing.FakeConnection;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;

class SessionManagerTest {

    @Test
    void createFindAndSize() {
        SessionManager manager = new SessionManager();
        Session session = manager.create("s1", "u1", "d1", new FakeConnection("c1"));

        assertSame(session, manager.find("s1").orElseThrow());
        assertEquals(1, manager.size());
        assertTrue(manager.find("missing").isEmpty());
    }

    @Test
    void duplicateSessionIdRejected() {
        SessionManager manager = new SessionManager();
        manager.create("s1", "u1", "d1", new FakeConnection("c1"));
        assertThrows(IllegalStateException.class,
                () -> manager.create("s1", "u2", "d2", new FakeConnection("c2")));
    }

    @Test
    void requireThrowsWhenMissing() {
        SessionManager manager = new SessionManager();
        assertThrows(NoSuchElementException.class, () -> manager.require("missing"));
    }

    @Test
    void findByDevice() {
        SessionManager manager = new SessionManager();
        Session session = manager.create("s1", "u1", "d1", new FakeConnection("c1"));

        assertSame(session, manager.findByDevice("u1", "d1").orElseThrow());
        assertTrue(manager.findByDevice("u1", "d2").isEmpty());
    }

    @Test
    void destroyClosesConnectionAndUnindexes() {
        SessionManager manager = new SessionManager();
        FakeConnection conn = new FakeConnection("c1");
        Session session = manager.create("s1", "u1", "d1", conn);

        assertTrue(manager.destroy("s1"));
        assertTrue(conn.isClosed());
        assertTrue(session.isClosed());
        assertEquals(0, manager.size());
        assertTrue(manager.findByDevice("u1", "d1").isEmpty());
    }

    @Test
    void destroyUnknownReturnsFalse() {
        SessionManager manager = new SessionManager();
        assertFalse(manager.destroy("nope"));
    }

    @Test
    void allReturnsSnapshot() {
        SessionManager manager = new SessionManager();
        manager.create("s1", "u1", "d1", new FakeConnection("c1"));
        manager.create("s2", "u2", "d2", new FakeConnection("c2"));
        assertEquals(2, manager.all().size());
    }

    @Test
    void sessionsOfUserReturnsAllDevices() {
        SessionManager manager = new SessionManager();
        manager.create("s1", "u1", "d1", new FakeConnection("c1"));
        manager.create("s2", "u1", "d2", new FakeConnection("c2"));
        manager.create("s3", "u2", "d1", new FakeConnection("c3"));

        assertEquals(2, manager.sessionsOfUser("u1").size());
        assertEquals(1, manager.sessionsOfUser("u2").size());
        assertEquals(0, manager.sessionsOfUser("nobody").size());
    }

    @Test
    void destroyUpdatesUserIndex() {
        SessionManager manager = new SessionManager();
        manager.create("s1", "u1", "d1", new FakeConnection("c1"));
        manager.create("s2", "u1", "d2", new FakeConnection("c2"));

        manager.destroy("s1");

        assertEquals(1, manager.sessionsOfUser("u1").size());
    }
}
