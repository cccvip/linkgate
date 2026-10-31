package com.linkgate.ai.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.linkgate.core.stream.TokenSink;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CheckpointingTokenSinkTest {

    private static final class RecordingSink implements TokenSink {

        final List<String> tokens = new ArrayList<>();
        boolean completed;

        @Override
        public void emit(String token) {
            tokens.add(token);
        }

        @Override
        public void complete() {
            completed = true;
        }

        @Override
        public void error(Throwable error) {
        }
    }

    @Test
    void recordsAndForwardsTokens() {
        InMemoryStreamCheckpointStore store = new InMemoryStreamCheckpointStore(100);
        RecordingSink delegate = new RecordingSink();
        CheckpointingTokenSink sink = new CheckpointingTokenSink("s1", "u1", store, delegate);

        sink.emit("Hel");
        sink.emit("lo");
        sink.complete();

        assertEquals(List.of("Hel", "lo"), delegate.tokens, "应转发给下游");
        assertTrue(delegate.completed);
        assertEquals(List.of("Hel", "lo"), store.since("s1", -1), "应记录检查点");
        assertTrue(store.find("s1").orElseThrow().completed());
        assertEquals(2, store.find("s1").orElseThrow().totalTokens());
    }
}
