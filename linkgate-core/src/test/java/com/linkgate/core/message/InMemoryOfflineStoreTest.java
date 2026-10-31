package com.linkgate.core.message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class InMemoryOfflineStoreTest {

    private static Message message(String id, String toUserId) {
        return new Message(id, "alice", toUserId, 1L, new byte[] {1});
    }

    @Test
    void storesAndDrainsInOrder() {
        InMemoryOfflineStore store = new InMemoryOfflineStore(10);

        store.store(message("m1", "bob"));
        store.store(message("m2", "bob"));
        assertEquals(2, store.size("bob"));

        List<Message> drained = store.drain("bob");

        assertEquals(List.of("m1", "m2"), drained.stream().map(Message::messageId).toList());
        assertEquals(0, store.size("bob"), "drain 应清空");
        assertTrue(store.drain("bob").isEmpty());
    }

    @Test
    void isolatesUsers() {
        InMemoryOfflineStore store = new InMemoryOfflineStore(10);
        store.store(message("m1", "bob"));
        store.store(message("m2", "carol"));

        assertEquals(1, store.size("bob"));
        assertEquals(1, store.size("carol"));
        assertEquals(0, store.size("nobody"));
    }

    @Test
    void dropsOldestWhenCapacityExceeded() {
        InMemoryOfflineStore store = new InMemoryOfflineStore(2);

        store.store(message("m1", "bob"));
        store.store(message("m2", "bob"));
        store.store(message("m3", "bob"));

        assertEquals(2, store.size("bob"), "每用户容量受限");
        assertEquals(List.of("m2", "m3"), store.drain("bob").stream().map(Message::messageId).toList());
    }

    @Test
    void rejectsInvalidCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new InMemoryOfflineStore(0));
    }
}
