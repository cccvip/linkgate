package com.linkgate.ai.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class OpenAiCompatibleLlmClientTest {

    private static String baseUrl(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static HttpServer sseServer(long initialDelayMillis, String... chunks) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            if (initialDelayMillis > 0) {
                try {
                    Thread.sleep(initialDelayMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream out = exchange.getResponseBody()) {
                for (String chunk : chunks) {
                    out.write(chunk.getBytes(StandardCharsets.UTF_8));
                    out.flush();
                }
            }
        });
        return server;
    }

    private static final class RecordingHandler implements LlmTokenHandler {

        final List<String> tokens = Collections.synchronizedList(new ArrayList<>());
        final AtomicReference<Throwable> error = new AtomicReference<>();
        final CountDownLatch done = new CountDownLatch(1);

        @Override
        public void onToken(String token) {
            tokens.add(token);
        }

        @Override
        public void onComplete() {
            done.countDown();
        }

        @Override
        public void onError(Throwable e) {
            error.set(e);
            done.countDown();
        }
    }

    @Test
    @Timeout(15)
    void streamsTokensUntilDone() throws Exception {
        HttpServer server = sseServer(0,
                "data: {\"choices\":[{\"delta\":{\"content\":\"Hel\"}}]}\n\n",
                "data: {\"choices\":[{\"delta\":{\"content\":\"lo\"}}]}\n\n",
                "data: [DONE]\n\n");
        server.start();
        try {
            OpenAiCompatibleLlmClient client = new OpenAiCompatibleLlmClient(baseUrl(server), "test-key");
            RecordingHandler handler = new RecordingHandler();

            client.streamChat(LlmRequest.of("gpt-4o-mini", "hi"), handler);

            assertTrue(handler.done.await(5, TimeUnit.SECONDS), "流应正常结束");
            assertEquals(List.of("Hel", "lo"), handler.tokens);
            assertEquals(null, handler.error.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    @Timeout(15)
    void cancelStopsStreamWithoutTokens() throws Exception {
        HttpServer server = sseServer(200,
                "data: {\"choices\":[{\"delta\":{\"content\":\"hi\"}}]}\n\n",
                "data: [DONE]\n\n");
        server.start();
        try {
            OpenAiCompatibleLlmClient client = new OpenAiCompatibleLlmClient(baseUrl(server), "test-key");
            RecordingHandler handler = new RecordingHandler();

            LlmCall call = client.streamChat(LlmRequest.of("gpt-4o-mini", "hi"), handler);
            call.cancel();
            assertTrue(call.isCancelled());

            assertTrue(handler.done.await(5, TimeUnit.SECONDS));
            assertTrue(handler.tokens.isEmpty(), "取消后不应推送 token");
            assertTrue(handler.error.get() instanceof CancellationException);
        } finally {
            server.stop(0);
        }
    }

    @Test
    @Timeout(15)
    void non200RepliesError() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            exchange.sendResponseHeaders(401, -1);
            exchange.close();
        });
        server.start();
        try {
            OpenAiCompatibleLlmClient client = new OpenAiCompatibleLlmClient(baseUrl(server), "bad-key");
            RecordingHandler handler = new RecordingHandler();

            client.streamChat(LlmRequest.of("gpt-4o-mini", "hi"), handler);

            assertTrue(handler.done.await(5, TimeUnit.SECONDS));
            assertTrue(handler.error.get() != null, "非 200 应报错");
        } finally {
            server.stop(0);
        }
    }
}
