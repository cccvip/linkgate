package com.linkgate.netty.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;

/**
 * WebSocket 文本帧 {@code <->} {@link Frame} 的 JSON 信封编解码。
 *
 * <p>信封格式：{@code {"type": N, "seq": N, "flags": N?, "body": {...}}}。</p>
 */
public final class WebSocketCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private WebSocketCodec() {
    }

    public static Frame decode(String text) {
        JsonNode node = read(text);
        byte type = (byte) node.path("type").asInt();
        long seq = node.path("seq").asLong(0);
        byte flags = (byte) node.path("flags").asInt(0);
        JsonNode body = node.path("body");
        byte[] bytes = (body.isMissingNode() || body.isNull())
                ? new byte[0]
                : body.toString().getBytes(StandardCharsets.UTF_8);
        return new Frame(flags, type, seq, bytes);
    }

    public static String encode(Frame frame) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("type", frame.type() & 0xFF);
        node.put("seq", frame.seq());
        if (frame.flags() != 0) {
            node.put("flags", frame.flags() & 0xFF);
        }
        if (frame.bodyLength() > 0) {
            JsonNode body = parse(frame.body());
            if (body != null) {
                node.set("body", body);
            }
        }
        return node.toString();
    }

    private static JsonNode read(String text) {
        try {
            return MAPPER.readTree(text);
        } catch (Exception e) {
            throw new IllegalArgumentException("bad websocket frame: " + text, e);
        }
    }

    private static JsonNode parse(byte[] body) {
        try {
            return MAPPER.readTree(body);
        } catch (Exception e) {
            return null;
        }
    }
}
