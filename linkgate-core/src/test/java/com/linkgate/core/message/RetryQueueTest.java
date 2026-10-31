package com.linkgate.core.message;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class RetryQueueTest {

    @Test
    void firesAfterDelay() throws InterruptedException {
        try (RetryQueue queue = new RetryQueue()) {
            CountDownLatch latch = new CountDownLatch(1);
            queue.schedule(latch::countDown, 30, TimeUnit.MILLISECONDS);
            assertTrue(latch.await(2, TimeUnit.SECONDS), "定时任务应在延迟后触发");
        }
    }

    @Test
    void cancelPreventsFiring() throws InterruptedException {
        try (RetryQueue queue = new RetryQueue()) {
            CountDownLatch latch = new CountDownLatch(1);
            RetryQueue.RetryHandle handle = queue.schedule(latch::countDown, 200, TimeUnit.MILLISECONDS);

            assertTrue(handle.cancel());
            assertTrue(handle.isCancelled());

            assertFalse(latch.await(500, TimeUnit.MILLISECONDS), "取消后不应触发");
        }
    }
}
