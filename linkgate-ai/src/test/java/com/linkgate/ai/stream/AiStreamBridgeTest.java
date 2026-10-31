package com.linkgate.ai.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.linkgate.ai.llm.LlmRequest;
import com.linkgate.ai.llm.TokenStreamProducer;
import com.linkgate.ai.testing.FakeLlmClient;
import com.linkgate.core.stream.TokenSink;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class AiStreamBridgeTest {

    private static final class RecordingSink implements TokenSink {

        final List<String> tokens = Collections.synchronizedList(new ArrayList<>());
        final CountDownLatch done = new CountDownLatch(1);
        volatile boolean completed;
        volatile Throwable error;

        @Override
        public void emit(String token) {
            tokens.add(token);
        }

        @Override
        public void complete() {
            completed = true;
            done.countDown();
        }

        @Override
        public void error(Throwable e) {
            error = e;
            done.countDown();
        }
    }

    @Test
    @Timeout(10)
    void streamsTokensToSinkInOrderThenCompletes() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            TokenStreamProducer producer =
                    new TokenStreamProducer(new FakeLlmClient(List.of("Hel", "lo", "!"), false), 8);
            AiStreamBridge bridge = new AiStreamBridge(producer, executor);
            RecordingSink sink = new RecordingSink();

            bridge.bridge(LlmRequest.of("m", "hi"), sink);

            assertTrue(sink.done.await(5, TimeUnit.SECONDS));
            assertNull(sink.error);
            assertTrue(sink.completed);
            assertEquals(List.of("Hel", "lo", "!"), sink.tokens);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @Timeout(10)
    void propagatesErrorToSink() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            TokenStreamProducer producer = new TokenStreamProducer(new FakeLlmClient(List.of("a"), true), 8);
            AiStreamBridge bridge = new AiStreamBridge(producer, executor);
            RecordingSink sink = new RecordingSink();

            bridge.bridge(LlmRequest.of("m", "hi"), sink);

            assertTrue(sink.done.await(5, TimeUnit.SECONDS));
            assertNotNull(sink.error, "上游错误应传到 sink");
            assertEquals(List.of("a"), sink.tokens);
        } finally {
            executor.shutdownNow();
        }
    }
}
