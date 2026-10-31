package com.linkgate.core.message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class SequencerTest {

    @Test
    void incrementsMonotonically() {
        Sequencer sequencer = new Sequencer();
        assertEquals(1L, sequencer.next("c1"));
        assertEquals(2L, sequencer.next("c1"));
        assertEquals(3L, sequencer.next("c1"));
        assertEquals(3L, sequencer.current("c1"));
    }

    @Test
    void sequencesAreIndependentPerConversation() {
        Sequencer sequencer = new Sequencer();
        assertEquals(1L, sequencer.next("c1"));
        assertEquals(1L, sequencer.next("c2"));
        assertEquals(2L, sequencer.next("c2"));
        assertEquals(2L, sequencer.next("c1"));
    }

    @Test
    void resetClearsConversation() {
        Sequencer sequencer = new Sequencer();
        sequencer.next("c1");
        sequencer.reset("c1");
        assertEquals(0L, sequencer.current("c1"));
        assertEquals(1L, sequencer.next("c1"));
    }

    @Test
    void monotonicWithoutDuplicatesUnderConcurrency() throws InterruptedException {
        Sequencer sequencer = new Sequencer();
        int threads = 8;
        int perThread = 2000;
        Set<Long> seen = ConcurrentHashMap.newKeySet();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < perThread; i++) {
                        seen.add(sequencer.next("c1"));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        boolean finished = done.await(10, TimeUnit.SECONDS);
        pool.shutdownNow();

        assertEquals(true, finished);
        assertEquals(threads * perThread, seen.size(), "不应出现重复序列号");
        assertEquals(threads * perThread, sequencer.current("c1"), "应连续增长到总数");
    }

    @Test
    void conversationKeyIsOrderIndependent() {
        assertEquals(Sequencer.conversationKey("alice", "bob"), Sequencer.conversationKey("bob", "alice"));
        assertNotEquals(Sequencer.conversationKey("alice", "bob"), Sequencer.conversationKey("alice", "carol"));
    }
}
