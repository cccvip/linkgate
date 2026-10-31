package com.linkgate.netty.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.linkgate.core.stream.TokenSink;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class BackpressureControllerTest {

    private static final class RecordingSink implements TokenSink {

        final List<String> tokens = new ArrayList<>();
        boolean completed;
        Throwable error;

        @Override
        public void emit(String token) {
            tokens.add(token);
        }

        @Override
        public void complete() {
            completed = true;
        }

        @Override
        public void error(Throwable e) {
            error = e;
        }
    }

    @Test
    void emitsImmediatelyWhenWritable() {
        RecordingSink sink = new RecordingSink();
        BackpressureController controller = new BackpressureController(() -> true, () -> true, sink, 1000, 5);

        controller.emit("a");
        controller.emit("b");

        assertEquals(List.of("a", "b"), sink.tokens);
    }

    @Test
    void waitsUntilWritableThenEmits() throws InterruptedException {
        AtomicBoolean writable = new AtomicBoolean(false);
        RecordingSink sink = new RecordingSink();
        BackpressureController controller = new BackpressureController(writable::get, () -> true, sink, 2000, 5);

        Thread flipper = new Thread(() -> {
            try {
                Thread.sleep(150);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            writable.set(true);
        });
        flipper.setDaemon(true);
        flipper.start();

        long start = System.currentTimeMillis();
        controller.emit("a");
        long waited = System.currentTimeMillis() - start;

        assertEquals(List.of("a"), sink.tokens, "可写后应补发");
        assertTrue(waited >= 100, "应确实等待过，实际 " + waited + "ms");
    }

    @Test
    void doesNotBlockWhenChannelInactive() {
        RecordingSink sink = new RecordingSink();
        BackpressureController controller = new BackpressureController(() -> false, () -> false, sink, 30_000, 5);

        long start = System.currentTimeMillis();
        controller.emit("a");
        long waited = System.currentTimeMillis() - start;

        assertEquals(List.of("a"), sink.tokens, "连接已断应直接放行（不再等待）");
        assertTrue(waited < 100, "不应等待，实际 " + waited + "ms");
    }

    @Test
    void abortsWhenClientNeverBecomesWritable() {
        RecordingSink sink = new RecordingSink();
        BackpressureController controller = new BackpressureController(() -> false, () -> true, sink, 50, 5);

        assertThrows(IllegalStateException.class, () -> controller.emit("a"));
        assertTrue(sink.tokens.isEmpty());
    }
}
