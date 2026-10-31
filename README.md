# LinkGate — AI 实时会话网关

一个能扛量、消息不丢、可观测的 Netty 长连接网关，并把 LLM 的 token 流接进来（分片 / 背压 / 续传）。

> 设计文档见 [`docs/linkgate-spec.md`](docs/linkgate-spec.md)，任务台账见 [`docs/linkgate-tasks.md`](docs/linkgate-tasks.md)。

## 模块

```
linkgate
├── linkgate-core      消息、会话、可靠性、端口抽象（无网络栈，可脱网单测）
├── linkgate-netty     接入 + 协议 + 会话 + pipeline（唯一碰 Netty 网络栈的模块）
├── linkgate-ai        LLM 客户端、token 流、背压、检查点、流式桥接
├── linkgate-store     存储抽象（Redis / MySQL）—— 待接入
├── linkgate-admin     指标注册与 Prometheus 端点（可选 Spring Boot）
└── linkgate-bench     压测客户端（IDLE / ACTIVE 两种场景）
```

依赖方向：`core ← ai ← netty`，`core ← bench`，`core/netty/ai ← admin`（无环）。
`core` 允许使用 `netty-common` 这类**无 IO 工具库**（如 `HashedWheelTimer`），但不引网络栈。

## 构建与测试

```bash
mvn -B verify          # 编译 + 全部单测/集成测试
```

## 已实现能力

| 层 | 内容 |
|---|---|
| 协议 | 定长帧头（magic/version/flags/type/seq/bodyLength）+ 编解码 + 粘包/半包处理 |
| 接入 | `GatewayServer`、`IdleStateHandler` 心跳、连接数与建连速率限制 |
| 会话 | `Session` 状态机、`SessionManager`、幂等重连（同设备续接 + 宽限期） |
| 消息 | `DeliveryService` 投递、`AckTracker`+`RetryQueue` 重传、`Deduplicator` 去重、`Sequencer` 顺序号、`OfflineStore` 离线补投、`MessageJournal`+`RESUME` 会话续传 |
| AI | OpenAI 兼容 SSE 客户端、`TokenStream`（推送→拉取 + 有界缓冲背压）、`AiStreamBridge`、`BackpressureController`、`StreamCheckpoint`+`AI_RESUME` 续接 |
| 观测 | `MetricsRegistry`（Prometheus 指标）、`MetricsReporter`（`/metrics`）、Grafana dashboard JSON |
| 压测 | `BenchClient` / `BenchMain`（IDLE 与 ACTIVE 分开） |

## 协议帧格式

大端序，帧头 18 字节：

```
offset  0      2      3      4      5      6            14          18
        +------+------+------+------+------+-------------+-----------+
        | magic| ver  | flags| type | rsvd | seq (int64) | bodyLen   |
        | 2B   | 1B   | 1B   | 1B   | 1B   | 8B          | 4B (int32)|
        +------+------+------+------+------+-------------+-----------+
        | body (bodyLen bytes, 当前为 JSON)                           |
        +------------------------------------------------------------+
```

`magic = 0x4C47 ('L''G')`，`bodyLen` 仅表示 body 长度。

### 消息类型

| code | 类型 | 说明 |
|---|---|---|
| 1/2 | LOGIN / LOGIN_ACK | 登录，返回 sessionId |
| 3/4 | HEARTBEAT / HEARTBEAT_ACK | 心跳 |
| 5/6 | CHAT / CHAT_ACK | 点对点消息 |
| 8/9 | AI_STREAM / AI_STREAM_END | AI 流式分片 / 结束 |
| 11/12 | RESUME / RESUME_ACK | 消息流续传 |
| 13/14/15 | AI_CHAT / AI_RESUME / AI_RESUME_ACK | AI 生成 / 续接 |
| 16/17/18/19 | GROUP_JOIN / GROUP_LEAVE / GROUP_CHAT / GROUP_ACK | 群组 |
| 20/21 | MULTI_SYNC / MULTI_SYNC_ACK | 多端同步 |

## 快速上手

```java
// 启动网关
GatewayServer server = new GatewayServer(8080);
server.start();
```

```bash
# 压测（需先按 docs/linkgate-bench-baseline.md 调好环境）
java -cp linkgate-bench.jar com.linkgate.bench.BenchMain 127.0.0.1 8080 10000 IDLE 60
```

## 状态

- Phase 0/1/2(核心)/4 完成；Phase 3 代码与文档就绪，**十万连接曲线与一次调优需在独立压测机上产出**。
- Redis / MySQL 相关中间件尚未接入。
