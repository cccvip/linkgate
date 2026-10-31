package com.linkgate.core.message;

import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;
import io.netty.util.Timer;
import io.netty.util.concurrent.DefaultThreadFactory;
import java.lang.ref.Cleaner;
import java.util.concurrent.TimeUnit;

/**
 * 定时重传队列：基于 Netty {@link HashedWheelTimer} 的延迟任务调度。
 *
 * <p>面向"海量短命定时器"场景（每条待 ACK 消息一个超时），O(1) 入队、单线程推进、低内存开销。</p>
 *
 * <p>默认实现自带一个时间轮并负责其关闭；也可传入外部时间轮共享（此时不负责关闭）。</p>
 */
public final class RetryQueue implements AutoCloseable {

    private static final long DEFAULT_TICK_MILLIS = 100;
    private static final int DEFAULT_WHEEL_SIZE = 512;

    private static final Cleaner CLEANER = Cleaner.create();

    private final Timer timer;
    private final Cleaner.Cleanable cleanable;

    public RetryQueue() {
        this(new HashedWheelTimer(
                new DefaultThreadFactory("linkgate-timer", true),
                DEFAULT_TICK_MILLIS, TimeUnit.MILLISECONDS, DEFAULT_WHEEL_SIZE), true);
    }

    /** 共享外部时间轮；{@link #close()} 不会关闭它。 */
    public RetryQueue(HashedWheelTimer timer) {
        this((Timer) timer, false);
    }

    private RetryQueue(Timer timer, boolean ownsTimer) {
        this.timer = timer;
        this.cleanable = ownsTimer ? CLEANER.register(this, new StopTimer(timer)) : null;
    }

    /** 延迟 {@code delay} 后执行 {@code task}，返回可取消的句柄。 */
    public RetryHandle schedule(Runnable task, long delay, TimeUnit unit) {
        Timeout timeout = timer.newTimeout(ignored -> task.run(), delay, unit);
        return new RetryHandle(timeout);
    }

    @Override
    public void close() {
        if (cleanable != null) {
            cleanable.clean();
        }
    }

    /** GC 兜底：调用方忘记 close() 时，仍在 GC 阶段停止时间轮，避免线程泄漏。 */
    private static final class StopTimer implements Runnable {

        private final Timer timer;

        private StopTimer(Timer timer) {
            this.timer = timer;
        }

        @Override
        public void run() {
            timer.stop();
        }
    }

    /** 定时任务句柄。 */
    public static final class RetryHandle {

        private final Timeout timeout;

        RetryHandle(Timeout timeout) {
            this.timeout = timeout;
        }

        public boolean cancel() {
            return timeout.cancel();
        }

        public boolean isCancelled() {
            return timeout.isCancelled();
        }

        public boolean isExpired() {
            return timeout.isExpired();
        }
    }
}
