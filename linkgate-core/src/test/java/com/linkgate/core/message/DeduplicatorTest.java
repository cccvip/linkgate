package com.linkgate.core.message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DeduplicatorTest {

    @Test
    void firstSeenThenDuplicate() {
        Deduplicator dedup = new Deduplicator(100);

        assertTrue(dedup.firstSeen("m1"));
        assertFalse(dedup.firstSeen("m1"));
        assertTrue(dedup.isDuplicate("m1"));
        assertEquals(1, dedup.size());
    }

    @Test
    void boundsSizeWithLruEviction() {
        Deduplicator dedup = new Deduplicator(2);

        assertTrue(dedup.firstSeen("m1"));
        assertTrue(dedup.firstSeen("m2"));
        assertEquals(2, dedup.size());

        assertTrue(dedup.firstSeen("m3"), "新消息仍可进入");
        assertEquals(2, dedup.size(), "容量必须受限");

        assertTrue(dedup.firstSeen("m1"), "m1 已被淘汰，视为新消息");
    }

    @Test
    void rejectsInvalidCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new Deduplicator(0));
    }
}
