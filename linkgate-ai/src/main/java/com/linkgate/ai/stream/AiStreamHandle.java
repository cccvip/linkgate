package com.linkgate.ai.stream;

import java.util.concurrent.Future;
import com.linkgate.ai.llm.TokenStream;

/** 一次 AI 流式桥接的句柄，用于取消。 */
public final class AiStreamHandle {

    private final TokenStream stream;
    private final Future<?> future;

    AiStreamHandle(TokenStream stream, Future<?> future) {
        this.stream = stream;
        this.future = future;
    }

    public void cancel() {
        stream.cancel();
        future.cancel(true);
    }
}
