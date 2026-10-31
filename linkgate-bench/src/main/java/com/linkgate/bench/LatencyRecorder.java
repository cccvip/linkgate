package com.linkgate.bench;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 延迟采样：定长数组记录，结束后算分位数（P50 / P99）。
 */
public final class LatencyRecorder {

    private final long[] samples;
    private final AtomicInteger count = new AtomicInteger();

    public LatencyRecorder(int capacity) {
        this.samples = new long[capacity];
    }

    public void record(long nanos) {
        int index = count.getAndIncrement();
        if (index < samples.length) {
            samples[index] = nanos;
        }
    }

    public int size() {
        return Math.min(count.get(), samples.length);
    }

    /** 分位数（毫秒）。 */
    public double percentileMillis(double percentile) {
        int size = size();
        if (size == 0) {
            return 0.0;
        }
        long[] copy = Arrays.copyOf(samples, size);
        Arrays.sort(copy);
        int index = (int) Math.ceil(percentile * size) - 1;
        index = Math.max(0, Math.min(index, size - 1));
        return copy[index] / 1_000_000.0;
    }
}
