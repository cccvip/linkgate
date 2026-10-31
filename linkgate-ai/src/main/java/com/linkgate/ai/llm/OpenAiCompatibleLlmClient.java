package com.linkgate.ai.llm;

import com.linkgate.core.codec.JsonCodec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Iterator;
import java.util.concurrent.CancellationException;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OpenAI 兼容的流式 LLM 客户端：POST {@code /chat/completions}（{@code stream=true}），
 * 逐行解析 SSE 并把 token 推给 {@link LlmTokenHandler}。
 *
 * <p>基于 JDK {@link HttpClient}，无额外 HTTP 依赖。</p>
 */
public final class OpenAiCompatibleLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleLlmClient.class);
    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";

    private final HttpClient http;
    private final String baseUrl;
    private final String apiKey;
    private final OpenAiStreamParser parser;

    public OpenAiCompatibleLlmClient(String baseUrl, String apiKey) {
        this(baseUrl, apiKey, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build());
    }

    public OpenAiCompatibleLlmClient(String baseUrl, String apiKey, HttpClient http) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
        this.http = http;
        this.parser = new OpenAiStreamParser();
    }

    @Override
    public LlmCall streamChat(LlmRequest request, LlmTokenHandler handler) {
        CancellableCall call = new CancellableCall();
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + CHAT_COMPLETIONS_PATH))
                .timeout(Duration.ofMinutes(5))
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(
                        new String(JsonCodec.encode(request.toWire()), StandardCharsets.UTF_8)))
                .build();

        http.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofLines())
                .thenAccept(response -> consume(response, call, handler))
                .exceptionally(error -> {
                    notifyError(handler, error);
                    return null;
                });
        return call;
    }

    private void consume(HttpResponse<Stream<String>> response, CancellableCall call, LlmTokenHandler handler) {
        if (response.statusCode() != 200) {
            notifyError(handler, new IllegalStateException("LLM HTTP " + response.statusCode()));
            return;
        }
        try (Stream<String> lines = response.body()) {
            Iterator<String> iterator = lines.iterator();
            while (iterator.hasNext() && !call.isCancelled()) {
                String line = iterator.next();
                if (parser.isDone(line)) {
                    break;
                }
                String token = parser.tokenOf(line);
                if (token != null && !token.isEmpty()) {
                    handler.onToken(token);
                }
            }
            if (call.isCancelled()) {
                notifyError(handler, new CancellationException("llm stream cancelled"));
            } else {
                handler.onComplete();
            }
        } catch (RuntimeException e) {
            notifyError(handler, e);
        }
    }

    private static void notifyError(LlmTokenHandler handler, Throwable error) {
        try {
            handler.onError(error);
        } catch (RuntimeException e) {
            log.warn("token handler threw on error: {}", e.toString());
        }
    }

    private static final class CancellableCall implements LlmCall {

        private volatile boolean cancelled;

        @Override
        public void cancel() {
            cancelled = true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }
    }
}
