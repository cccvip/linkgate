package com.linkgate.ai.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class InMemoryStreamCheckpointStoreTest {

    @Test
    void assignsIncreasingIndexAndReplaysGap() {
        InMemoryStreamCheckpointStore store = new InMemoryStreamCheckpointStore(100);

        assertEquals(0, store.append("s1", "u1", "a"));
        assertEquals(1, store.append("s1", "u1", "b"));
        assertEquals(2, store.append("s1", "u1", "c"));

        assertEquals(List.of("b", "c"), store.since("s1", 0));
        assertEquals(List.of("c"), store.since("s1", 1));
        assertTrue(store.since("s1", 2).isEmpty());
    }

    @Test
    void completeRecordsTotalTokens() {
        InMemoryStreamCheckpointStore store = new InMemoryStreamCheckpointStore(100);
        store.append("s1", "u1", "a");
        store.append("s1", "u1", "b");
        store.complete("s1", 2);

        StreamCheckpoint checkpoint = store.find("s1").orElseThrow();
        assertTrue(checkpoint.completed());
        assertEquals(2, checkpoint.totalTokens());
        assertEquals(1, checkpoint.lastIndex());
        assertEquals("u1", checkpoint.userId());
    }

    @Test
    void boundedEvictionKeepsAbsoluteIndex() {
        InMemoryStreamCheckpointStore store = new InMemoryStreamCheckpointStore(2);
        store.append("s1", "u1", "a");  // 0
        store.append("s1", "u1", "b");  // 1
        store.append("s1", "u1", "c");  // 2 → 淘汰 a

        assertEquals(List.of("c"), store.since("s1", 1), "只保留最近两片，index 不回退");
        assertEquals(2, store.find("s1").orElseThrow().lastIndex());
    }

    @Test
    void unknownStreamIsEmpty() {
        InMemoryStreamCheckpointStore store = new InMemoryStreamCheckpointStore(10);
        assertTrue(store.since("nope", 0).isEmpty());
        assertFalse(store.find("nope").isPresent());
    }

    @Test
    void removeDropsStream() {
        InMemoryStreamCheckpointStore store = new InMemoryStreamCheckpointStore(10);
        store.append("s1", "u1", "a");
        store.remove("s1");
        assertFalse(store.find("s1").isPresent());
    }
}
