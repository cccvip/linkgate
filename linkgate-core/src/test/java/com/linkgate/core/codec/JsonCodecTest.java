package com.linkgate.core.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.linkgate.core.protocol.LoginRequest;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class JsonCodecTest {

    @Test
    void roundTripsRecord() {
        byte[] bytes = JsonCodec.encode(new LoginRequest("u1", "d1"));
        LoginRequest decoded = JsonCodec.decode(bytes, LoginRequest.class);
        assertEquals(new LoginRequest("u1", "d1"), decoded);
    }

    @Test
    void decodeInvalidJsonThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> JsonCodec.decode(new byte[] {1, 2, 3}, LoginRequest.class));
    }

    @Test
    void unknownPropertiesAreIgnored() {
        byte[] bytes = "{\"userId\":\"u1\",\"extra\":123}".getBytes(StandardCharsets.UTF_8);
        LoginRequest decoded = JsonCodec.decode(bytes, LoginRequest.class);
        assertEquals("u1", decoded.userId());
    }

    @Test
    void encodeFailureThrowsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> JsonCodec.encode(new Object() {
            @SuppressWarnings("unused")
            public String getBoom() {
                throw new RuntimeException("boom");
            }
        }));
    }
}
