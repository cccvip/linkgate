package com.linkgate.netty.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MsgTypeTest {

    @Test
    void codesAreUnique() {
        Set<Byte> codes = new HashSet<>();
        for (MsgType type : MsgType.values()) {
            assertEquals(true, codes.add(type.code()), "duplicate code: " + type);
        }
    }

    @Test
    void fromCodeRoundTrips() {
        for (MsgType type : MsgType.values()) {
            assertEquals(type, MsgType.fromCode(type.code()));
        }
    }

    @Test
    void fromCodeRejectsUnknown() {
        assertThrows(IllegalArgumentException.class, () -> MsgType.fromCode((byte) 99));
    }

    @Test
    void fromCodeOrNullReturnsNullForUnknown() {
        assertNull(MsgType.fromCodeOrNull((byte) 99));
        assertEquals(MsgType.CHAT, MsgType.fromCodeOrNull(MsgType.CHAT.code()));
    }

    @Test
    void keyTypesPresent() {
        assertEquals((byte) 1, MsgType.LOGIN.code());
        assertEquals((byte) 3, MsgType.HEARTBEAT.code());
        assertEquals((byte) 5, MsgType.CHAT.code());
        assertEquals((byte) 8, MsgType.AI_STREAM.code());
    }
}
