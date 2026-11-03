package com.linkgate.netty.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class WebSocketCodecTest {

    @Test
    void roundTripsFrameWithBody() {
        Frame frame = Frame.of((byte) 5, 42L, "{\"text\":\"hi\"}".getBytes(StandardCharsets.UTF_8));
        String json = WebSocketCodec.encode(frame);

        Frame decoded = WebSocketCodec.decode(json);
        assertEquals(frame.type(), decoded.type());
        assertEquals(frame.seq(), decoded.seq());
        assertEquals(new String(frame.body(), StandardCharsets.UTF_8),
                new String(decoded.body(), StandardCharsets.UTF_8));
    }

    @Test
    void heartbeatEmptyBodyRoundTrips() {
        Frame decoded = WebSocketCodec.decode(WebSocketCodec.encode(Frame.of(MsgType.HEARTBEAT.code(), 0L)));
        assertEquals(MsgType.HEARTBEAT.code(), decoded.type());
        assertEquals(0, decoded.bodyLength());
    }

    @Test
    void decodeIncludesTypeSeq() {
        Frame decoded = WebSocketCodec.decode("{\"type\":7,\"seq\":9,\"body\":{\"messageId\":\"m1\"}}");
        assertEquals(MsgType.ACK.code(), decoded.type());
        assertEquals(9L, decoded.seq());
        assertTrue(new String(decoded.body(), StandardCharsets.UTF_8).contains("m1"));
    }
}
