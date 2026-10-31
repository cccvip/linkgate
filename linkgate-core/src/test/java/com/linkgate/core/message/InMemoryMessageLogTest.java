package com.linkgate.core.message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class InMemoryMessageLogTest {

    private static Message message(String id, long seq) {
        return new Message(id, "alice", "bob", seq, new byte[] {1});
    }

    @Test
    void appendAndQuerySince() {
        InMemoryMessageLog log = new InMemoryMessageLog(10);
        log.append("s", message("m1", 1));
        log.append("s", message("m2", 2));
        log.append("s", message("m3", 3));

        assertEquals(List.of(2L, 3L), log.since("s", 1).stream().map(Message::seq).toList());
        assertEquals(3L, log.latestSeq("s"));
        assertEquals(3, log.size("s"));
    }

    @Test
    void unknownStreamIsEmpty() {
        InMemoryMessageLog log = new InMemoryMessageLog(10);

        assertTrue(log.since("nope", 0).isEmpty());
        assertEquals(0L, log.latestSeq("nope"));
        assertEquals(0, log.size("nope"));
    }

    @Test
    void evictsOldestWhenBounded() {
        InMemoryMessageLog log = new InMemoryMessageLog(2);
        log.append("s", message("m1", 1));
        log.append("s", message("m2", 2));
        log.append("s", message("m3", 3));

        assertEquals(2, log.size("s"), "每流容量受限");
        assertEquals(List.of(2L, 3L), log.since("s", 0).stream().map(Message::seq).toList());
    }
}
