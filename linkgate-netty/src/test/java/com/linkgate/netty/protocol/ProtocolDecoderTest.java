package com.linkgate.netty.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

class ProtocolDecoderTest {

    private static ByteBuf encodeFrame(int type, long seq, byte[] body) {
        ByteBuf buf = Unpooled.buffer();
        buf.writeShort(Frame.MAGIC);
        buf.writeByte(Frame.VERSION);
        buf.writeByte(Frame.FLAG_NONE);
        buf.writeByte(type);
        buf.writeByte(0); // reserved
        buf.writeLong(seq);
        buf.writeInt(body.length);
        buf.writeBytes(body);
        return buf;
    }

    @Test
    void decodesSingleFrame() {
        EmbeddedChannel ch = new EmbeddedChannel(new ProtocolDecoder());

        assertTrue(ch.writeInbound(encodeFrame(1, 1L, new byte[] {10, 20})));

        ByteBuf out = ch.readInbound();
        assertNotNull(out);
        assertEquals(Frame.HEADER_LENGTH + 2, out.readableBytes());
        assertEquals(Frame.MAGIC, out.getShort(out.readerIndex()));
        out.release();

        assertNull(ch.readInbound());
        ch.finishAndReleaseAll();
    }

    @Test
    void handlesSplitFrame() {
        EmbeddedChannel ch = new EmbeddedChannel(new ProtocolDecoder());

        byte[] bytes = ByteBufUtil.getBytes(encodeFrame(7, 42L, new byte[] {1, 2, 3}));

        // 前半段：不足以成帧
        assertFalse(ch.writeInbound(Unpooled.wrappedBuffer(bytes, 0, 10)));
        assertNull(ch.readInbound());

        // 后半段：补齐
        assertTrue(ch.writeInbound(Unpooled.wrappedBuffer(bytes, 10, bytes.length - 10)));

        ByteBuf out = ch.readInbound();
        assertNotNull(out);
        assertEquals(bytes.length, out.readableBytes());
        out.release();

        ch.finishAndReleaseAll();
    }

    @Test
    void handlesBatchedFrames() {
        EmbeddedChannel ch = new EmbeddedChannel(new ProtocolDecoder());

        ByteBuf a = encodeFrame(1, 1L, new byte[] {1});
        ByteBuf b = encodeFrame(2, 2L, new byte[] {2, 2});

        assertTrue(ch.writeInbound(Unpooled.wrappedBuffer(a, b)));

        ByteBuf first = ch.readInbound();
        ByteBuf second = ch.readInbound();
        assertNotNull(first);
        assertNotNull(second);
        assertEquals(Frame.HEADER_LENGTH + 1, first.readableBytes());
        assertEquals(Frame.HEADER_LENGTH + 2, second.readableBytes());
        first.release();
        second.release();

        assertNull(ch.readInbound());
        ch.finishAndReleaseAll();
    }

    @Test
    void rejectsInvalidMagic() {
        EmbeddedChannel ch = new EmbeddedChannel(new ProtocolDecoder());

        ByteBuf bad = encodeFrame(1, 1L, new byte[] {1});
        bad.setShort(0, 0x0000);

        Throwable thrown = assertThrows(Throwable.class, () -> ch.writeInbound(bad));
        assertTrue(rootCauseMessage(thrown).contains("magic"), "应报告魔数错误，实际: " + thrown);

        ch.finishAndReleaseAll();
    }

    private static String rootCauseMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur.getMessage() == null ? "" : cur.getMessage();
    }
}
