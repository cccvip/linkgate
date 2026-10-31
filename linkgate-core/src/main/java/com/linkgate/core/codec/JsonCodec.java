package com.linkgate.core.codec;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;

/**
 * JSON 编解码工具：控制类消息（登录、ACK、错误等）的载荷序列化。
 *
 * <p>共享一个线程安全的 {@link ObjectMapper}。失败统一抛 {@link IllegalArgumentException}。</p>
 */
public final class JsonCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private JsonCodec() {
    }

    public static byte[] encode(Object value) {
        try {
            return MAPPER.writeValueAsBytes(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("json encode failed: " + e.getOriginalMessage(), e);
        }
    }

    public static <T> T decode(byte[] data, Class<T> type) {
        try {
            return MAPPER.readValue(data, type);
        } catch (IOException e) {
            throw new IllegalArgumentException("json decode failed: " + e.getMessage(), e);
        }
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }
}
