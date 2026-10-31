# LinkGate — AI 实时会话网关 · 项目 Spec

> 一个能扛量、消息不丢、可观测的 Netty 长连接网关，并跑通 LLM 流式输出。
> 目标：用「长连接护城河 + AI 实时」同时够到 JD4（AI + 实时通信）与 JD3（AI 应用）。

---

## 0. 验收标准（面试可量化的）

- 连接数压到 **十万级**（写清条件：机器规格、空闲连接 vs 活跃消息）
- 断线重连后，**会话与流式输出都能无缝续接**
- 消息可靠性测试通过：**不丢、不乱、不重**
- **一次真实调优**的 before / after 数据
- LLM token 流端到端跑通，**慢客户端不拖垮服务**（背压有效）

---

## 1. 技术栈

| 用途 | 选型 |
|---|---|
| 语言 / 运行时 | Java 17+ |
| 网络 | Netty 4.x |
| 状态 / 路由 / 离线 | Redis |
| 消息 / 元数据 | MySQL |
| 分发（可选） | Kafka |
| 观测 | Prometheus + Grafana |
| 性能分析 | async-profiler / JFR |
| 压测 | 自研 Netty 客户端 |

**原则：先单体，需要再加。** 不要一上来就微服务 + Kafka + 分库分表。

---

## 2. 分层与模块（按 Maven 多模块组织）

```
linkgate
├── linkgate-netty     接入 + 协议 + 会话（唯一碰 Netty 的模块）
├── linkgate-core      消息、路由、可靠性（纯逻辑，可脱离网络单测）
├── linkgate-ai        LLM 流式桥接
├── linkgate-store     存储抽象（Redis / MySQL）
├── linkgate-admin     指标与管理 API（Spring Boot，可选）
└── linkgate-bench     压测客户端
```

逻辑分层（自上而下）：

1. **接入层**：连接、编解码、心跳、限流
2. **协议层**：自定义协议、命令路由
3. **会话层**：会话模型、在线状态、重连恢复
4. **消息层**：投递、可靠性、离线、顺序
5. **路由层**：连接路由、群组扩散、多端同步
6. **AI 层**：流式接入、背压、续传
7. **存储层 / 观测层**

---

## 3. 每一层要实现的类

### 接入层（linkgate-netty）

| 类 | 职责 |
|---|---|
| `GatewayServer` | Bootstrap + boss / worker EventLoopGroup |
| `GatewayChannelInitializer` | 装配 pipeline |
| `GatewayMessageDecoder` / `GatewayMessageEncoder` | 编解码 |
| `HeartbeatHandler` | 基于 `IdleStateHandler` 的心跳 |
| `ConnectionLimiter` | 连接数与速率限制 |
| `ConnectionRegistry` | `ChannelGroup` 维护 channelId ↔ sessionId |

### 协议层

| 类 | 职责 |
|---|---|
| `MsgType` | LOGIN / HEARTBEAT / CHAT / ACK / AI_STREAM … |
| `ProtocolDecoder` | `LengthFieldBasedFrameDecoder` + 自定义帧 |
| `CommandDispatcher` | `MsgType` → Handler 映射 |

### 会话层

| 类 | 职责 |
|---|---|
| `Session` | sessionId / userId / deviceId / channel / 状态 |
| `SessionManager` | 创建、销毁、重连恢复 |
| `ReconnectPolicy` | 幂等重连 |
| `SessionState` | 状态机 |

### 消息层（linkgate-core）

| 类 | 职责 |
|---|---|
| `DeliveryService` | 投递到目标会话 |
| `AckTracker` | ack 跟踪、超时重传 |
| `Deduplicator` | 按 messageId 去重 |
| `Sequencer` | per-conversation 顺序号 |
| `OfflineStore` / `RetryQueue` | 离线与重传（时间轮 `HashedWheelTimer`） |

### 路由层

| 类 | 职责 |
|---|---|
| `ConnectionRouter` | userId / deviceId → 节点 |
| `PresenceService` | Redis 在线状态 |
| `GroupFanout` | 群组扩散 |
| `MultiDeviceSync` | 多端同步 |

### AI 层（linkgate-ai）

| 类 | 职责 |
|---|---|
| `LlmClient` | 调用 LLM（OpenAI 兼容即可） |
| `TokenStreamProducer` | 消费 token 流 |
| `AiStreamBridge` | 把 token 流桥接到网关会话 |
| `BackpressureController` | 基于 `Channel.isWritable()` 与写缓冲水位 |
| `StreamCheckpoint` | 流式进度快照，用于续传 |

### 观测层

| 类 | 职责 |
|---|---|
| `MetricsRegistry` | 连接数、消息延迟、ack 超时、写水位 |
| `MetricsReporter` | 暴露 Prometheus 抓取端点 |

---

## 4. 分阶段实现

> 执行顺序：先打通功能闭环（Phase 1 / 2），再接 AI 流式（Phase 4），**压测与调优放最后**（Phase 3）。

### Phase 1 — 最小闭环（L1–L2）

Server + 编解码 + pipeline；LOGIN / HEARTBEAT / CHAT；`ConnectionRegistry` + `SessionManager`。
**目标：两个客户端能互发。**

### Phase 2 — 硬属性（L3–L4）

`AckTracker` + 重传 + 去重 + `Sequencer`；幂等重连 + 会话续传 + 离线消息；Redis 在线 / 路由；群组扩散；多端同步。

### Phase 4 — AI 流式

`AiStreamBridge` + `BackpressureController` + `StreamCheckpoint`；
端到端：请求 → token 流回推 → 断线续接。

### Phase 3 — 压测 + 观测 + 调优（最后执行）

`BenchClient` 建万级连接；指标面板；**一次调优**：定位 → 改 → 对比。

---

## 5. 面试怎么讲（三层串成一个故事）

**故事弧：**

1. **起点**：长连接是 AI 实时应用的地基，我要把它做扎实
2. **工程**：做一个能扛十万连接、消息不丢、可观测的网关
3. **桥（转折）**：把 LLM token 流接进来，撞上背压 / 续传 / 慢客户端 → 用长连接那套（水位、背压、重连）解决
4. **证据**：压测曲线 + 一次调优 before / after + 端到端设计图
5. **落点**：既够 JD4（AI + 实时通信）的门槛，又证明我懂 JD3 的 AI 应用

**已备好的话术：**

- 「我把 LLM 流式输出当消息流设计：分片、背压、续传。」
- 「多端 / 断连一致性，我用会话状态机收敛。」
- 「调优时瓶颈在 X，火焰图定位到 Y，改成 Z，P99 从 A 降到 B。」
- 「十万连接是在〔机器规格〕上压的，区分了空闲与活跃。」

**准备好被追问：**

- 为什么用 Netty？
- 重连幂等怎么保证？
- 慢客户端怎么办？
- 消息顺序怎么保证？
- 单机十万连接改了哪些内核参数？

---

## 6. 反模式（别做）

- 别一上来微服务 + Kafka + 分库分表，先单体
- 别做聊天 UI，那是噪音
- 别堆业务功能（群聊 / 朋友圈），要堆工程属性
- 别无数据地吹指标

---

## 7. 压测怎么做（最后执行）

- **工具**：自研 Netty `BenchClient`（长连接场景 wrk 不擅长）
- **指标**：连接数、建连速率、消息吞吐、**P50 / P99 延迟**、内存 RSS、GC、CPU
- **关键**：区分「空闲连接」和「活跃消息」两种模式 —— 大量教程把两者混为一谈
- **单机模拟十万连接**：调 `ulimit -n`、端口范围、JVM 内存；客户端也要省内存
- **如实记录**：机器规格、JVM 参数、Netty 参数、每档连接数的资源曲线

**调优通常的真实瓶颈：**

- ByteBuf 池化 / 堆外内存
- 写水位 `WriteBufferWaterMark` 与 flush 策略
- 消息批处理 / 合并写
- 业务线程与 EventLoop 分离（别阻塞 IO）
- GC（G1 / ZGC）与对象分配
- 序列化 / 编解码开销

---

## 附录：可复用的既有经验

`pi` 里见过的流式、取消、事件流设计（`event-stream`、`AbortSignal` ≈ 取消）**设计思路可直接迁移**，只是换成 Java 实现 —— 这是一个「看过真实系统」的加分点。
