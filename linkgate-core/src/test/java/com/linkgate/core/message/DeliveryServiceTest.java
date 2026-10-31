package com.linkgate.core.message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.linkgate.core.session.Session;
import com.linkgate.core.session.SessionManager;
import com.linkgate.core.testing.FakeConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DeliveryServiceTest {

    private final SessionManager sessionManager = new SessionManager();
    private final RecordingSender sender = new RecordingSender();
    private final DeliveryService deliveryService = new DeliveryService(sessionManager, sender);

    private Message message(String toUserId) {
        return new Message("m1", "alice", toUserId, 1L, new byte[] {1, 2});
    }

    @Test
    void deliversToAllOnlineSessions() {
        sessionManager.create("s1", "bob", "d1", new FakeConnection("c1"));
        sessionManager.create("s2", "bob", "d2", new FakeConnection("c2"));

        DeliveryResult result = deliveryService.deliver(message("bob"));

        assertEquals(2, result.deliveredCount());
        assertTrue(result.delivered());
        assertEquals(Set.of("s1", "s2"), Set.copyOf(sender.sessionIds));
    }

    @Test
    void reportsOfflineWhenNoSession() {
        DeliveryResult result = deliveryService.deliver(message("nobody"));

        assertEquals(0, result.deliveredCount());
        assertFalse(result.delivered());
        assertTrue(sender.sessionIds.isEmpty());
    }

    @Test
    void countsOnlySuccessfulSends() {
        sessionManager.create("s1", "bob", "d1", new FakeConnection("c1"));
        sessionManager.create("s2", "bob", "d2", new FakeConnection("c2"));
        sender.succeedFor = Set.of("s1");

        DeliveryResult result = deliveryService.deliver(message("bob"));

        assertEquals(1, result.deliveredCount());
    }

    @Test
    void storesMessageWhenTargetOffline() {
        InMemoryOfflineStore store = new InMemoryOfflineStore(10);
        DeliveryService service = new DeliveryService(sessionManager, sender, store);

        DeliveryResult result = service.deliver(message("bob"));

        assertEquals(0, result.deliveredCount());
        assertEquals(1, store.size("bob"), "离线消息应被暂存");
    }

    @Test
    void replayDeliversStoredMessagesOnLogin() {
        InMemoryOfflineStore store = new InMemoryOfflineStore(10);
        DeliveryService service = new DeliveryService(sessionManager, sender, store);

        service.deliver(message("bob"));
        assertEquals(1, store.size("bob"));

        sessionManager.create("s1", "bob", "d1", new FakeConnection("c1"));
        int replayed = service.replayOffline("bob");

        assertEquals(1, replayed);
        assertEquals(0, store.size("bob"), "回补后离线队列应清空");
        assertEquals(List.of("s1"), sender.sessionIds);
    }

    private static final class RecordingSender implements MessageSender {

        private final List<String> sessionIds = new ArrayList<>();
        private Set<String> succeedFor;

        @Override
        public boolean send(Session session, Message message) {
            sessionIds.add(session.sessionId());
            return succeedFor == null || succeedFor.contains(session.sessionId());
        }
    }
}
