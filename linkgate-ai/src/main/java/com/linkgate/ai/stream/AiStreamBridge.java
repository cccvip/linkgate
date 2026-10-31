package com.linkgate.ai.stream;

import com.linkgate.ai.llm.LlmRequest;
import com.linkgate.ai.llm.TokenStream;
import com.linkgate.ai.llm.TokenStreamProducer;
import com.linkgate.core.stream.TokenSink;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * 流式桥接：把 LLM 的 token 流桥接到网关会话的 {@link TokenSink}。
 *
 * <p>消费在独立线程池上跑（不占用 Netty EventLoop）；慢客户端造成的阻塞会经有界
 * {@link TokenStream} 反压回 LLM，从而"慢客户端不拖垮服务"。</p>
 */
public final class AiStreamBridge {

    private final TokenStreamProducer producer;
    private final ExecutorService executor;

    public AiStreamBridge(TokenStreamProducer producer, ExecutorService executor) {
        this.producer = producer;
        this.executor = executor;
    }

    /** 异步桥接，返回可取消句柄。 */
    public AiStreamHandle bridge(LlmRequest request, TokenSink sink) {
        TokenStream stream = producer.start(request);
        Future<?> future = executor.submit(() -> pump(stream, sink));
        return new AiStreamHandle(stream, future);
    }

    private static void pump(TokenStream stream, TokenSink sink) {
        try {
            String token;
            while ((token = stream.next()) != null) {
                sink.emit(token);
            }
            sink.complete();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            sink.error(e);
        } catch (RuntimeException e) {
            sink.error(e);
        } finally {
            stream.close();
        }
    }
}
