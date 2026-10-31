package com.linkgate.ai.llm;

/**
 * token 流生产者：解析 {@link LlmClient} 的推送为拉取式 {@link TokenStream}。
 */
public final class TokenStreamProducer {

    private static final int DEFAULT_BUFFER_SIZE = 64;

    private final LlmClient llmClient;
    private final int bufferSize;

    public TokenStreamProducer(LlmClient llmClient) {
        this(llmClient, DEFAULT_BUFFER_SIZE);
    }

    public TokenStreamProducer(LlmClient llmClient, int bufferSize) {
        this.llmClient = llmClient;
        this.bufferSize = bufferSize;
    }

    /** 发起一次流式生成，返回可逐 token 拉取的 {@link TokenStream}。 */
    public TokenStream start(LlmRequest request) {
        TokenStream stream = new TokenStream(bufferSize);
        LlmCall call = llmClient.streamChat(request, new StreamSink(stream));
        stream.bind(call);
        return stream;
    }

    private static final class StreamSink implements LlmTokenHandler {

        private final TokenStream stream;

        private StreamSink(TokenStream stream) {
            this.stream = stream;
        }

        @Override
        public void onToken(String token) {
            stream.offer(token);
        }

        @Override
        public void onComplete() {
            stream.complete();
        }

        @Override
        public void onError(Throwable error) {
            stream.fail(error);
        }
    }
}
