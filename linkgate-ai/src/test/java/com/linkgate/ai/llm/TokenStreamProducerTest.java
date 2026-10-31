package com.linkgate.ai.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.linkgate.ai.testing.FakeLlmClient;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class TokenStreamProducerTest {

    private static List<String> drain(TokenStream stream) throws InterruptedException {
        List<String> got = new ArrayList<>();
        String token;
        while ((token = stream.next()) != null) {
            got.add(token);
        }
        return got;
    }

    @Test
    @Timeout(10)
    void producesTokensInOrderThenEnds() throws Exception {
        TokenStreamProducer producer = new TokenStreamProducer(new FakeLlmClient(List.of("a", "b", "c"), false), 8);

        try (TokenStream stream = producer.start(LlmRequest.of("m", "hi"))) {
            assertEquals(List.of("a", "b", "c"), drain(stream));
        }
    }

    @Test
    @Timeout(10)
    void surfacesUpstreamError() throws Exception {
        TokenStreamProducer producer = new TokenStreamProducer(new FakeLlmClient(List.of("a"), true), 8);

        try (TokenStream stream = producer.start(LlmRequest.of("m", "hi"))) {
            assertEquals("a", stream.next());
            assertThrows(LlmStreamException.class, stream::next);
        }
    }

    @Test
    @Timeout(10)
    void boundedBufferLosesNothingUnderBackpressure() throws Exception {
        List<String> many = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            many.add("t" + i);
        }
        TokenStreamProducer producer = new TokenStreamProducer(new FakeLlmClient(many, false), 4);

        try (TokenStream stream = producer.start(LlmRequest.of("m", "hi"))) {
            assertEquals(many, drain(stream), "有界缓冲不应丢 token");
        }
    }

    @Test
    @Timeout(10)
    void cancelStopsStream() throws Exception {
        TokenStreamProducer producer =
                new TokenStreamProducer(new FakeLlmClient(List.of("a", "b", "c"), false, 300), 8);

        TokenStream stream = producer.start(LlmRequest.of("m", "hi"));
        assertEquals("a", stream.next());
        stream.cancel();
        assertNull(stream.next(), "取消后应结束");
    }
}
