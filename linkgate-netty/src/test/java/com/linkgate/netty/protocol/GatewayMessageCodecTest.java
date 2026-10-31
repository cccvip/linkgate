package com.linkgate.netty.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

class GatewayMessageCodecTest {

    private static ByteBuf encode(Frame frame) {
        EmbeddedChannel ch = new EmbeddedChannel(new GatewayMessageEncoder());
        assertTrue(ch.writeOutbound(frame));
        ByteBuf out = ch.readOutbound();
        assertNotNull(out);
        ch.finish();
        return out;
    }

    @Test
    void encoderWritesExpectedHeaderAndBody() {
        ByteBuf buf = encode(new Frame(Frame.FLAG_URGENT, (byte) 3, 0x0102030405060708L, new byte[] {9, 9}));

        assertEquals(Frame.HEADER_LENGTH + 2, buf.readableBytes());
        assertEquals(Frame.MAGIC, buf.readShort());
        assertEquals(Frame.VERSION, buf.readByte());
        assertEquals(Frame.FLAG_URGENT, buf.readByte());
        assertEquals((byte) 3, buf.readByte());
        assertEquals((byte) 0, buf.readByte()); // reserved
        assertEquals(0x0102030405060708L, buf.readLong());
        assertEquals(2, buf.readInt());
        assertArrayEquals(new byte[] {9, 9}, ByteBufUtil.getBytes(buf));

        buf.release();
    }

    @Test
    void roundTripPreservesFrame() {
        Frame original = new Frame(Frame.FLAG_END_OF_STREAM, (byte) 7, 123456789L, new byte[] {1, 2, 3, 4, 5});

        ByteBuf encoded = encode(original);

        EmbeddedChannel dec = new EmbeddedChannel(new GatewayMessageDecoder());
        assertTrue(dec.writeInbound(encoded));

        Frame decoded = dec.readInbound();
        assertEquals(original, decoded);
        assertEquals(original.flags(), decoded.flags());
        assertEquals(original.seq(), decoded.seq());

        dec.finishAndReleaseAll();
    }

    @Test
    void roundTripEmptyBody() {
        Frame original = Frame.of((byte) 2, 5L);

        ByteBuf encoded = encode(original);
        assertEquals(Frame.HEADER_LENGTH, encoded.readableBytes());

        EmbeddedChannel dec = new EmbeddedChannel(new GatewayMessageDecoder());
        assertTrue(dec.writeInbound(encoded));
        assertEquals(original, dec.readInbound());

        dec.finishAndReleaseAll();
    }

    @Test
    void decoderRejectsTruncatedBody() {
        ByteBuf buf = Unpooled.buffer();
        buf.writeShort(Frame.MAGIC);
        buf.writeByte(Frame.VERSION);
        buf.writeByte(0);
        buf.writeByte(1);
        buf.writeByte(0);
        buf.writeLong(1L);
        buf.writeInt(10);                 // 声称 10 字节 body
        buf.writeBytes(new byte[] {1, 2}); // 实际只有 2 字节

        EmbeddedChannel ch = new EmbeddedChannel(new GatewayMessageDecoder());
        assertThrows(Throwable.class, () -> ch.writeInbound(buf));
        ch.finishAndReleaseAll();
    }

    @Test
    void decoderRejectsUnsupportedVersion() {
        ByteBuf buf = Unpooled.buffer();
        buf.writeShort(Frame.MAGIC);
        buf.writeByte(9);                 // 未知版本
        buf.writeByte(0);
        buf.writeByte(1);
        buf.writeByte(0);
        buf.writeLong(1L);
        buf.writeInt(0);

        EmbeddedChannel ch = new EmbeddedChannel(new GatewayMessageDecoder());
        assertThrows(Throwable.class, () -> ch.writeInbound(buf));
        ch.finishAndReleaseAll();
    }
}
