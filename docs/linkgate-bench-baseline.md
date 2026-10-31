# LinkGate 压测基线（Benchmark Baseline）

> 用途：让十万连接压测**可复现**。压测机与开发机不同，所有参数必须记录。
> 配套工具：`linkgate-bench` 的 `BenchMain`。

## 1. 机器规格（每次压测必须填）

| 项 | 值 |
|---|---|
| CPU / 核数 | |
| 内存 | |
| OS / 内核 | |
| 网关与压测机是否同机 | |
| 网络（带宽/是否同机房） | |

## 2. 操作系统参数

```bash
# 单进程可打开文件数（连接数上限的关键）
ulimit -n 1048576

# 全系统文件句柄
sysctl -w fs.file-max=2097152

# 端口范围（压测机作为客户端时需要大量源端口）
sysctl -w net.ipv4.ip_local_port_range="1024 65535"

# 连接队列
sysctl -w net.core.somaxconn=65535
sysctl -w net.ipv4.tcp_max_syn_backlog=65535

# TIME_WAIT 复用
sysctl -w net.ipv4.tcp_tw_reuse=1

# 收发缓冲（按需）
sysctl -w net.core.rmem_max=16777216
sysctl -w net.core.wmem_max=16777216
```

> ⚠️ 十万连接 = **网关侧**和**压测客户端侧**都要调 `ulimit` 与源端口范围。

## 3. JVM 参数

```bash
java \
  -Xms8g -Xmx8g \
  -XX:+UseG1GC \                    # 或 -XX:+UseZGC（低延迟优先时对比）
  -XX:MaxDirectMemorySize=4g \      # Netty 堆外内存上限
  -XX:+HeapDumpOnOutOfMemoryError \
  -Dio.netty.leakDetection.level=DISABLED \
  -Dio.netty.allocator.type=pooled \
  -jar linkgate-*.jar
```

观测 GC：`-Xlog:gc*:file=gc.log:time,uptime`（或 `-XX:+PrintGCDetails`）。

## 4. Netty 参数（本工程默认值）

| 参数 | 值 | 位置 |
|---|---|---|
| `SO_BACKLOG` | 1024 | `GatewayServer` |
| `SO_KEEPALIVE` | true | `GatewayServer` |
| `TCP_NODELAY` | true | `GatewayServer` |
| 读空闲 | 60s | `GatewayChannelInitializer` |
| 写空闲 | 30s | `GatewayChannelInitializer` |
| 连接数上限 | 100_000 | `GatewayComponents` |
| 建连速率 | 20_000/s | `GatewayComponents` |

> 写水位 `WriteBufferWaterMark` 与 flush 策略属于调优项，压测时按需改并记录。

## 5. 运行

```bash
# 空闲连接：考察连接数上限与内存
java -cp linkgate-bench.jar com.linkgate.bench.BenchMain <host> <port> 10000 IDLE 60

# 活跃消息：考察吞吐与 P50/P99
java -cp linkgate-bench.jar com.linkgate.bench.BenchMain <host> <port> 1000 ACTIVE 60
```

输出示例：

```
connected 10000/10000 in 4210ms
connections=10000 scenario=IDLE elapsed=60000ms sent=0 received=0 throughput=0/s p50=0.00ms p99=0.00ms
```

## 6. 记录表模板

按连接档位逐档记录（**区分空闲与活跃**）：

| 连接数 | 场景 | CPU% | RSS(MB) | Direct(MB) | GC 次数/停顿 | 建连速率(/s) | 吞吐(msg/s) | P50(ms) | P99(ms) |
|---|---|---|---|---|---|---|---|---|---|
| 1k | IDLE | | | | | | | | |
| 10k | IDLE | | | | | | | | |
| 100k | IDLE | | | | | | | | |
| 1k | ACTIVE | | | | | | | | |

## 7. 调优记录模板（before / after）

| 项 | before | after |
|---|---|---|
| 瓶颈（火焰图定位） | | |
| 改动（代码/参数） | | |
| 吞吐 | | |
| P99 | | |
| RSS / GC | | |

> spec §0 要求「一次真实调优的 before/after 数据」——本表即证据载体。
