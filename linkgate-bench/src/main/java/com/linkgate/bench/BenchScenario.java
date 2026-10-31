package com.linkgate.bench;

/**
 * 压测场景：必须区分「空闲连接」与「活跃消息」——两者资源曲线完全不同。
 */
public enum BenchScenario {

    /** 大量连接挂着不发消息（考察连接数上限与内存）。 */
    IDLE,

    /** 连接上持续收发（考察吞吐与延迟）。 */
    ACTIVE
}
