package com.linkgate.netty.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FrameTest {

    @Test
    void nullBodyBecomesEmpty() {
        Frame frame = new Frame(Frame.FLAG_NONE, (byte) 1, 1L, null);
        assertEquals(0, frame.bodyLength());
        assertArrayEquals(new byte[0], frame.body());
    }

    @Test
    void ofWithoutFlagsUsesNone() {
        Frame frame = Frame.of((byte) 7, 42L, new byte[] {1, 2, 3});
        assertEquals(Frame.FLAG_NONE, frame.flags());
        assertEquals(7, frame.type());
        assertEquals(42L, frame.seq());
        assertArrayEquals(new byte[] {1, 2, 3}, frame.body());
    }

    @Test
    void emptyBodyConvenience() {
        Frame frame = Frame.of((byte) 2, 9L);
        assertEquals(0, frame.bodyLength());
    }

    @Test
    void hasFlagChecksBits() {
        byte flags = (byte) (Frame.FLAG_URGENT | Frame.FLAG_END_OF_STREAM);
        Frame frame = new Frame(flags, (byte) 1, 1L, null);
        assertTrue(frame.hasFlag(Frame.FLAG_URGENT));
        assertTrue(frame.hasFlag(Frame.FLAG_END_OF_STREAM));
        assertFalse(frame.hasFlag(Frame.FLAG_NONE));
    }

    @Test
    void equalsAndHashCode() {
        Frame a = Frame.of((byte) 3, 100L, new byte[] {9, 8});
        Frame b = Frame.of((byte) 3, 100L, new byte[] {9, 8});
        Frame c = Frame.of((byte) 3, 101L, new byte[] {9, 8});

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }

    @Test
    void toStringDoesNotDumpBody() {
        Frame frame = Frame.of((byte) 5, 1L, new byte[] {(byte) 0xAB, (byte) 0xCD});
        String s = frame.toString();
        assertTrue(s.contains("bodyLength=2"));
        assertTrue(s.contains("type=5"));
    }
}
