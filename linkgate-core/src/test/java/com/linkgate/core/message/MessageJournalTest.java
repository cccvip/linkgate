package com.linkgate.core.message;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class MessageJournalTest {

    private final MessageJournal journal = new MessageJournal(new Sequencer(), new InMemoryMessageLog(100));

    @Test
    void assignsMonotonicSeqPerInbox() {
        Message m1 = journal.append("alice", "bob", new byte[] {1});
        Message m2 = journal.append("carol", "bob", new byte[] {2});
        Message m3 = journal.append("alice", "dave", new byte[] {3});

        assertEquals(1L, m1.seq());
        assertEquals(2L, m2.seq(), "同一收件箱共享递增序号");
        assertEquals(1L, m3.seq(), "不同收件箱独立编号");
    }

    @Test
    void sinceReturnsExactlyTheGap() {
        for (int i = 0; i < 5; i++) {
            journal.append("alice", "bob", new byte[] {(byte) i});
        }

        List<Message> gap = journal.since("bob", 3);

        assertEquals(List.of(4L, 5L), gap.stream().map(Message::seq).toList());
        assertEquals(5L, journal.latestSeq("bob"));
    }

    @Test
    void emptyInboxHasZeroLatest() {
        assertEquals(0L, journal.latestSeq("nobody"));
    }
}
