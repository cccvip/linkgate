package com.linkgate.core.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.linkgate.core.message.DeliveryService;
import com.linkgate.core.message.Message;
import com.linkgate.core.session.SessionManager;
import com.linkgate.core.testing.FakeConnection;
import com.linkgate.core.testing.RecordingSender;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GroupFanoutTest {

    private final SessionManager sessionManager = new SessionManager();
    private final RecordingSender sender = new RecordingSender();
    private final GroupFanout fanout = new GroupFanout(new DeliveryService(sessionManager, sender));

    @Test
    void fansOutToAllMembers() {
        sessionManager.create("sb", "bob", "d1", new FakeConnection("cb"));
        sessionManager.create("sc", "carol", "d1", new FakeConnection("cc"));

        fanout.join("g1", "bob");
        fanout.join("g1", "carol");

        int delivered = fanout.fanout("g1", new Message("m1", "alice", "g1", 1L, new byte[] {1}));

        assertEquals(2, delivered);
        assertEquals(Set.of("sb", "sc"), Set.copyOf(sender.sessionIds()));
    }

    @Test
    void offlineMembersAreSkipped() {
        sessionManager.create("sb", "bob", "d1", new FakeConnection("cb"));
        fanout.join("g1", "bob");
        fanout.join("g1", "carol");   // carol 无会话

        int delivered = fanout.fanout("g1", new Message("m1", "alice", "g1", 1L, new byte[] {1}));

        assertEquals(1, delivered);
    }

    @Test
    void leaveRemovesMember() {
        fanout.join("g1", "bob");
        fanout.join("g1", "carol");
        fanout.leave("g1", "bob");

        assertEquals(Set.of("carol"), fanout.members("g1"));
        assertTrue(fanout.members("g2").isEmpty());
    }
}
