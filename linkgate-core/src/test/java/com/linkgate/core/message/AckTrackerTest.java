package com.linkgate.core.message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class AckTrackerTest {

    private static Message message(String id) {
        return new Message(id, "alice", "bob", 1L, new byte[] {1});
    }

    @Test
    void triggersRetransmitOnTimeout() throws InterruptedException {
        try (RetryQueue queue = new RetryQueue()) {
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<Message> retried = new AtomicReference<>();
            AckTracker tracker = new AckTracker(queue, 100, m -> {
                retried.set(m);
                latch.countDown();
            });

            Message message = message("m1");
            tracker.track(message);
            assertEquals(1, tracker.pendingCount());

            assertTrue(latch.await(2, TimeUnit.SECONDS), "超时应触发重传回调");
            assertEquals(message, retried.get());
            assertEquals(1, tracker.pendingCount(), "重传后仍等待 ACK");
            assertTrue(tracker.acknowledge("m1"), "ACK 后应销账");
            assertEquals(0, tracker.pendingCount());
        }
    }

    @Test
    void acknowledgeCancelsTimeout() throws InterruptedException {
        try (RetryQueue queue = new RetryQueue()) {
            AtomicInteger retried = new AtomicInteger();
            AckTracker tracker = new AckTracker(queue, 150, m -> retried.incrementAndGet());

            tracker.track(message("m1"));
            assertTrue(tracker.acknowledge("m1"), "首次 ACK 应命中");
            assertFalse(tracker.acknowledge("m1"), "重复 ACK 应未命中");
            assertEquals(0, tracker.pendingCount());

            CountDownLatch wait = new CountDownLatch(1);
            assertFalse(wait.await(400, TimeUnit.MILLISECONDS));
            assertEquals(0, retried.get(), "已 ACK 的消息不应重传");
        }
    }

    @Test
    void retransmitsUpToMaxAttemptsThenGivesUp() throws InterruptedException {
        try (RetryQueue queue = new RetryQueue()) {
            AtomicInteger attempts = new AtomicInteger();
            AckTracker tracker = new AckTracker(queue, 50, 2, m -> attempts.incrementAndGet());

            tracker.track(message("m1"));

            Thread.sleep(800);
            assertEquals(2, attempts.get(), "最多重传 maxAttempts 次");
            assertEquals(0, tracker.pendingCount(), "达到上限后应放弃跟踪");
        }
    }

    @Test
    void duplicateTrackIsIdempotent() {
        try (RetryQueue queue = new RetryQueue()) {
            AckTracker tracker = new AckTracker(queue, 1000, m -> {
            });
            Message message = message("m1");

            tracker.track(message);
            tracker.track(message);

            assertEquals(1, tracker.pendingCount());
        }
    }
}
