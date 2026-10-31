package com.linkgate.bench;

/**
 * 压测结果。
 *
 * @param connections         建立成功连接数
 * @param scenario            场景
 * @param elapsedMillis       持续时长
 * @param sent                发送消息数
 * @param received            收到响应数
 * @param throughputPerSecond 吞吐（响应数/秒）
 * @param p50Millis           P50 往返延迟
 * @param p99Millis           P99 往返延迟
 */
public record BenchResult(int connections, BenchScenario scenario, long elapsedMillis, long sent, long received,
                          double throughputPerSecond, double p50Millis, double p99Millis) {

    public String report() {
        return String.format(
                "connections=%d scenario=%s elapsed=%dms sent=%d received=%d throughput=%.0f/s p50=%.2fms p99=%.2fms",
                connections, scenario, elapsedMillis, sent, received, throughputPerSecond, p50Millis, p99Millis);
    }
}
