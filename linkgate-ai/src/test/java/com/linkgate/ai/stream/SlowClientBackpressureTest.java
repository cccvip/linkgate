package com.linkgate.ai.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.linkgate.ai.llm.LlmCall;
import com.linkgate.ai.llm.LlmClient;
import com.linkgate.ai.llm.LlmRequest;
import com.linkgate.ai.llm.TokenStreamProducer;
import com.linkgate.core.stream.TokenSink;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * 慢客户端背压：有界缓冲把生产抑制在消费之后，不丢 token、不在内存里堆积。
 */
class SlowClientBackpressureTest {

    @Test
    @Timeout(20)
    void boundedBufferThrottlesProducerWithoutLoss() throws Exception {
        int tokenCount = 200;
        List<String> tokens = IntStream.range(0, tokenCount).mapToObj(i -> "t" + i).toList();
        AtomicInteger produced = new AtomicInteger();
        AtomicInteger consumed = new AtomicInteger();
        AtomicInteger maxAhead = new AtomicInteger();

        LlmClient llm = (LlmRequest request, com.linkgate.ai.llm.LlmTokenHandler handler) -> {
            Thread thread = new Thread(() -> {
                for (String token : tokens) {
                    produced.incrementAndGet();
                    handler.onToken(token);
                }
                handler.onComplete();
            }, "throttled-llm");
            thread.setDaemon(true);
            thread.start();
            return new LlmCall() {
                @Override
                public void cancel() {
                }

                @Override
                public boolean isCancelled() {
                    return false;
                }
            };
        };

        int bufferSize = 4;
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            AiStreamBridge bridge = new AiStreamBridge(new TokenStreamProducer(llm, bufferSize), executor);
            List<String> received = Collections.synchronizedList(new ArrayList<>());
            CountDownLatch done = new CountDownLatch(1);

            TokenSink slowSink = new TokenSink() {
                @Override
                public void emit(String token) {
                    received.add(token);
                    int c = consumed.incrementAndGet();
                    maxAhead.accumulateAndGet(produced.get() - c, Math::max);
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }

                @Override
                public void complete() {
                    done.countDown();
                }

                @Override
                public void error(Throwable error) {
                    done.countDown();
                }
            };

            bridge.bridge(LlmRequest.of("m", "hi"), slowSink);

            assertTrue(done.await(10, TimeUnit.SECONDS), "慢消费者下流仍应完成");
            assertEquals(tokens, received, "不应丢 token");
            assertTrue(maxAhead.get() <= bufferSize + 2,
                    "生产不应越过有界缓冲：maxAhead=" + maxAhead.get());
        } finally {
            executor.shutdownNow();
        }
    }
}
