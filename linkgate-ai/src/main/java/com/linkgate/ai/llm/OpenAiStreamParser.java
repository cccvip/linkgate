package com.linkgate.ai.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkgate.core.codec.JsonCodec;

/**
 * OpenAI 风格 SSE 行解析：从 {@code data: {...}} 中取出 {@code choices[0].delta.content}。
 *
 * <p>纯函数，脱离网络可单测。</p>
 */
public final class OpenAiStreamParser {

    private static final String DATA_PREFIX = "data:";
    private static final String DONE = "[DONE]";

    private final ObjectMapper mapper;

    public OpenAiStreamParser() {
        this(JsonCodec.mapper());
    }

    public OpenAiStreamParser(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** 是否为结束标记 {@code data: [DONE]}。 */
    public boolean isDone(String line) {
        return DONE.equals(dataOf(line));
    }

    /**
     * 解析增量 token。
     *
     * @return token；若该行无 token（空行 / 注释 / 仅 role / finish_reason）返回 {@code null}
     * @throws IllegalArgumentException 载荷不是合法 JSON
     */
    public String tokenOf(String line) {
        String data = dataOf(line);
        if (data == null || data.isEmpty() || DONE.equals(data)) {
            return null;
        }
        try {
            JsonNode choices = mapper.readTree(data).path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                return null;
            }
            JsonNode content = choices.get(0).path("delta").path("content");
            return content.isMissingNode() || content.isNull() ? null : content.asText();
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid SSE chunk: " + data, e);
        }
    }

    private static String dataOf(String line) {
        if (line == null) {
            return null;
        }
        String trimmed = line.trim();
        if (!trimmed.startsWith(DATA_PREFIX)) {
            return null;
        }
        return trimmed.substring(DATA_PREFIX.length()).trim();
    }
}
