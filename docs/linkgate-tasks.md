# LinkGate — 任务拆分（Task Breakdown）

> 依据 `docs/linkgate-spec.md` 拆分。按 Phase 递进，每个 Task 标注：模块 / 关键产物 / 验收点 / 依赖。
> 原则：先单体跑通闭环，再加硬属性，再压测调优，最后接 AI 流式。

## 约定

- **任务编号**：`T<phase>.<序号>`，如 `T1.4`。
- **状态**：`[ ]` 未开始，`[~]` 进行中，`[x]` 完成。
- **验收点**：可执行、可量化（能跑测试 / 有数据 / 有图）。
- **模块**：`netty` / `core` / `ai` / `store` / `admin` / `bench`。
- **依赖规则**：core 不依赖任何内部模块，也不依赖网络栈（`netty-transport` / `netty-handler` / `netty-codec`）；但**可以用 `netty-common` 等无 IO 工具库**（如 `HashedWheelTimer`），只要保持可脱离网络单测。

---

## 进度总览

| Phase | 完成 / 总数 | 状态 |
|---|---|---|
| Phase 0 — 脚手架 | 5 / 5 | ✅ 完成 |
| Phase 1 — 最小闭环 | 18 / 18 | ✅ 完成（里程碑 **M1** 达成） |
| Phase 2 — 硬属性 | 16 / 16 | ✅ 完成（里程碑 **M2** 达成，含 Redis/MySQL） |
| **Phase 4 — AI 流式** | 7 / 7 | ✅ 完成（里程碑 **M4** 达成） |
| Phase 3 — 压测/观测/调优 | 6 / 8 | 🚧 代码与文档就绪；**#46/#47 需真机**出数据 |
| 横切任务 | 2 / 3 | 🚧 TX.1/TX.3 完成；TX.2 待真机证据 |
| **合计** | **54 / 57** | |

> **执行顺序（调整后）**：Phase 0 → 1 → 2（核心）→ **Phase 4（AI 流式）** → Phase 3（压测/观测/调优）→ 2（Redis/MySQL 中间件）。
> 原因：压测与调优依赖**独立压测机**（`ulimit`、端口范围、JVM 内存），与开发机不同；故把压测相关整体后置，先补齐功能闭环。

- 测试：`mvn -B verify` 全绿，**176 个**（core 67 + ai 21 + store 9 + netty 74 + admin 3 + bench 2）。
- 集成测试：Redis/MySQL 相关用例**真连本地容器**（`docker compose up -d`）；无服务时自动跳过。
- **中间件已接入主链路**：`GatewayBootstrap` / `GatewayMain` 按配置装配 Redis/MySQL/LLM；登录写在线状态+路由+会话快照，断连清理；聊天走「去重 → 服务端 seq → 投递 → ACK 跟踪（超时重传）」；消息日志可切 MySQL 持久化。
- **群聊/多端同步已接入命令**：`GROUP_JOIN` / `GROUP_LEAVE` / `GROUP_CHAT`（成员扇出，每成员独立 seq+ACK）/ `MULTI_SYNC`（同步给同用户其它设备）。
- 里程碑：**M1**（Phase 1 最小闭环）、**M2**（Phase 2 不丢/不乱/不重 + 重连续传）、**M4**（Phase 4 AI 流端到端 + 背压 + 续接）均达成。
- 入口类：协议/接入/会话/消息层已就绪；`GatewayEndToEndTest` 证明两客户端可登录互发。

---

## Phase 0 — 工程脚手架（地基）

| ID | 任务 | 模块 | 验收点 |
|---|---|---|---|
| [x] T0.1 | 初始化 Maven 父 POM（Java 17，`<dependencyManagement>` 统一版本） | root | `mvn -B package` 通过 |
| [x] T0.2 | 建 6 个子模块骨架（netty / core / ai / store / admin / bench） | all | `mvn -B package` 全绿 |
| [x] T0.3 | 引入依赖：Netty 4.1.115、Jackson 2.17、Lettuce 6.4、MySQL Connector/J 8.4、HikariCP、Micrometer/Prometheus、JUnit 5.11、Logback | all | 依赖解析成功 |
| [x] T0.4 | 统一日志/配置约定（logback + 简单配置加载）；模块依赖方向约束（netty→core，ai→core，store→core，core 不反向依赖） | all | 无循环依赖 |
| [x] T0.5 | 基础 CI：`mvn verify`（编译 + 单测） | root | 一条命令验证（`.github/workflows/ci.yml`） |

**依赖**：无。**产出**：可编译运行的空骨架。

---

## Phase 1 — 最小闭环（L1–L2 接入 + 协议 + 会话）

> 目标：**两个客户端能登录、心跳、互发 CHAT 消息。**

### 接入层（linkgate-netty）

| ID | 任务 | 关键产物 | 验收点 | 依赖 |
|---|---|---|---|---|
| [x] T1.1 | Netty 服务器启动 | `GatewayServer`（boss/worker `EventLoopGroup`、bind、优雅关闭） | 启动监听 + 接受连接（集成测试通过） | T0.2 |
| [x] T1.2 | Pipeline 装配 | `GatewayChannelInitializer`（编解码链） | 新连接走完整 pipeline（2/2 通过） | T1.1 |
| [x] T1.3 | 协议帧定义 | `Frame`（magic/version/flags/type/seq/bodyLength/body） | 帧结构文档 + 单测（8/8 通过） | T0.3 |
| [x] T1.4 | 帧解码 | `ProtocolDecoder`（`LengthFieldBasedFrameDecoder` + magic 校验） | 半包/粘包/整帧单测（4/4 通过） | T1.3 |
| [x] T1.5 | 编解码器 | `GatewayMessageDecoder` / `GatewayMessageEncoder`（POJO↔帧） | 往返编解码单测（5/5 通过） | T1.3 |
| [x] T1.6 | 心跳 | `IdleHeartbeatHandler` + `IdleStateHandler`（读空闲断开 / 写空闲发心跳） | 空闲触发心跳 / 超时断开（2/2 通过） | T1.2 |
| [x] T1.7 | 连接限制 | `ConnectionLimiter`（连接数 CAS + 令牌桶限速）+ `ConnectionLimitHandler` | 超限被拒 / 断开归还（5/5 通过） | T1.1 |
| [x] T1.8 | 连接注册表 | `ConnectionRegistry`（`ChannelGroup` + channel↔sessionId 双向索引） | 查询/绑定/重绑/移除单测（5/5 通过） | T1.2 |

### 协议 / 命令层

| ID | 任务 | 关键产物 | 验收点 | 依赖 |
|---|---|---|---|---|
| [x] T1.9 | 消息类型枚举 | `MsgType`（LOGIN/HEARTBEAT/CHAT/ACK/AI_STREAM…） | 枚举唯一性 + code 往返（5/5 通过） | T1.3 |
| [x] T1.10 | 命令分发 | `CommandDispatcher` / `CommandHandler`（`MsgType`→Handler 映射） | 未知类型不崩、可扩展（3/3 通过） | T1.9 |
| [x] T1.11 | 登录 | `LoginHandler`（建立会话、登记连接、JSON 载荷） | 登录成功返回 sessionId / 失败回 ERROR（4/4 通过） | T1.8 |
| [x] T1.12 | 心跳业务 | `HeartbeatHandler`（业务侧，刷新存活 + HEARTBEAT_ACK） | 心跳刷新会话 / 未登录回 ERROR（2/2 通过） | T1.11 |
| [x] T1.13 | 聊天 | `ChatHandler`（点对点投递，多端扇出 + CHAT_ACK） | A→B 收到消息 / 离线回 OFFLINE（4/4 通过） | T1.11 |

### 会话层

| ID | 任务 | 关键产物 | 验收点 | 依赖 |
|---|---|---|---|---|
| [x] T1.14 | 会话模型 | `Session`（sessionId/userId/deviceId/connection/状态） | 字段 + 状态迁移（6/6 通过） | T1.1 |
| [x] T1.15 | 会话管理 | `SessionManager`（创建/销毁/查找 + 设备索引） | 单测（7/7 通过，纯逻辑脱离网络） | T1.14 |
| [x] T1.16 | 状态机 | `SessionState`（CONNECTED/AUTHENTICATED/RECONNECTING/CLOSED） | 非法迁移被拒单测（5/5 通过） | T1.14 |

### 验证

| ID | 任务 | 关键产物 | 验收点 | 依赖 |
|---|---|---|---|---|
| [x] T1.17 | 测试客户端 | `TestClient`（Netty，可脚本化建连/收发/阻塞等帧） | 可编程建连 + 收发 | T1.5 |
| [x] T1.18 | 端到端闭环 | 集成测试（真起服务器 + 两客户端） | **两客户端登录后互发 CHAT 成功**（Phase 1 DoD） | T1.13,T1.17 |

---

## Phase 2 — 硬属性（L3–L4 可靠性 + 路由 + 离线）

> 目标：**不丢 / 不乱 / 不重 + 幂等重连 + 会话续传 + 多端同步。**

### 消息层（linkgate-core）

| ID | 任务 | 关键产物 | 验收点 | 依赖 |
|---|---|---|---|---|
| [x] T2.1 | 投递服务 | `DeliveryService` + `MessageSender` / `Message` / `DeliveryResult`（投递到目标会话） | 在线投递 / 离线 / 部分成功单测（3/3 通过） | T1.15 |
| [x] T2.2 | ACK 跟踪 | `AckTracker`（记账 + 超时重传 + ACK 销账） | 超时触发重传 / ACK 取消 / 幂等（3/3 通过） | T2.1 |
| [x] T2.3 | 去重 | `Deduplicator`（按 messageId，LRU 有界） | 重复仅投递一次 / LRU 淘汰（3/3 通过） | T2.1 |
| [x] T2.4 | 顺序号 | `Sequencer`（per-conversation 单调递增，线程安全） | 并发 8×2000 无重复且连续（5/5 通过） | T2.1 |
| [x] T2.5 | 重传队列 | `RetryQueue`（Netty `HashedWheelTimer`） | 定时触发 / 取消生效（2/2 通过） | T2.2 |
| [x] T2.6 | 离线存储 | `OfflineStore` / `InMemoryOfflineStore`（有界队列）+ 投递兜底 + 登录回补 | 离线暂存 / 上线补齐（4/4 + 4/5 单测 + e2e 通过） | T2.5 |

### 会话增强

| ID | 任务 | 关键产物 | 验收点 | 依赖 |
|---|---|---|---|---|
| [x] T2.7 | 重连策略 | `ReconnectPolicy`（同设备续接 + 断线宽限期 + 超时销毁） | 重复登录不产生重复会话 / 宽限期续接（6/6 通过） | T1.16 |
| [x] T2.8 | 会话续传协议 | 服务端 seq（`MessageJournal`）+ 有界 `MessageLog` + `RESUME/RESUME_ACK` 回补 | 断线→重连→RESUME 补齐无缺口（e2e 通过） | T2.4,T2.7 |

### 存储层（linkgate-store）

| ID | 任务 | 关键产物 | 验收点 | 依赖 |
|---|---|---|---|---|
| [x] T2.9 | Redis 抽象 | `RedisClient` 封装（Lettuce） | 本地 Redis 连通（2/2，真连） | T0.3 |
| [x] T2.10 | 在线状态 | `PresenceService`（在线集合 + 心跳 TTL） | 上下线 / TTL 续期（2/2，真连） | T2.9 |
| [x] T2.11 | 连接路由 | `ConnectionRouter`（userId/deviceId→nodeId） | 路由查询 / 注销（2/2，真连） | T2.9 |
| [x] T2.12 | 元数据/消息持久化 | schema（`lg_session`/`lg_message`）+ `MessageDao`/`SessionDao`（Hikari） | 落库回查 / 幂等（3/3，真连 MySQL） | T0.3 |

### 路由层

| ID | 任务 | 关键产物 | 验收点 | 依赖 |
|---|---|---|---|---|
| [x] T2.13 | 群组扩散 | `GroupFanout`（成员管理 + 扇出） | 扇出 / 离线跳过 / 退群（3/3） | T2.10,T2.11 |
| [x] T2.14 | 多端同步 | `MultiDeviceSync`（同步给其它设备） | 仅同步其它设备（2/2） | T2.11 |

### 验证

| ID | 任务 | 关键产物 | 验收点 | 依赖 |
|---|---|---|---|---|
| [x] T2.15 | 可靠性测试 | `ReliabilityScenarioTest`（注入丢包/重复/乱序） | **不丢、不乱、不重**（3/3 通过，Phase 2 DoD） | T2.2–T2.8 |
| [x] T2.16 | 重连续传测试 | e2e：会话复用 + RESUME 补齐无缺口 | 会话 + 消息流无缝续接（e2e 通过） | T2.8 |

---

## Phase 3 — 压测 + 观测 + 调优（⏸️ 调整到 Phase 4 之后）

> 目标：**十万级连接数据 + 一次真实调优 before/after。**

| ID | 任务 | 关键产物 | 验收点 | 依赖 |
|---|---|---|---|---|
| [x] T3.1 | 指标注册 | `MetricsRegistry`（连接/投递/ack 超时/自定义 gauge） | 计数与 gauge 导出（2/2 通过） | T1.8,T2.2 |
| [x] T3.2 | 指标上报 | `MetricsReporter`（JDK `HttpServer` 暴露 `/metrics`） | `/metrics` 可抓取（1/1 通过） | T3.1 |
| [x] T3.3 | 面板 | `docs/grafana/linkgate-dashboard.json`（4 个面板） | JSON 就绪（**渲染查看需你的 Grafana**） | T3.2 |
| [x] T3.4 | 压测客户端 | `BenchClient` / `BenchMain`（Netty，可建大量连接，P50/P99 采样） | 建连 + 施压（本地 20 连接自测通过） | T1.5 |
| [x] T3.5 | 压测场景 | `BenchScenario`（IDLE / ACTIVE 分开） | 两套场景分别可跑（2/2 通过） | T3.4 |
| [x] T3.6 | 系统调优基线 | `docs/linkgate-bench-baseline.md`（ulimit/端口/JVM/Netty/记录模板） | 可复现的压测环境 | T3.5 |
| [ ] T3.7 | 性能分析 | async-profiler / JFR 采样，定位热点 | 火焰图 + 热点结论 | T3.5 |
| [ ] T3.8 | 一次调优 | 定位→改（ByteBuf池化/水位/flush/批处理/线程分离/GC）→复测 | **before/after 数据**（Phase 3 DoD） | T3.7 |

---

## Phase 4 — AI 流式（linkgate-ai）（▶️ 当前阶段）

> 目标：**端到端：请求 → token 流回推 → 断线续接；慢客户端不拖垮服务。**

| ID | 任务 | 关键产物 | 验收点 | 依赖 |
|---|---|---|---|---|
| [x] T4.1 | LLM 客户端 | `LlmClient` / `OpenAiCompatibleLlmClient`（JDK HttpClient + SSE）+ `OpenAiStreamParser` | 本地 SSE 收 token 流 / 取消 / 非 200（8/8 通过） | T0.3 |
| [x] T4.2 | 流生产者 | `TokenStreamProducer` / `TokenStream`（推送→拉取，有界缓冲反压） | 逐 token / 出错 / 取消（4/4 通过） | T4.1 |
| [x] T4.3 | 流桥接 | `AiStreamBridge` + `TokenSink`（core 端口）+ `ChannelTokenSink`（`AI_STREAM` 分片） | 会话收流式 token（2/2 + 2/2 通过） | T2.1,T4.2 |
| [x] T4.4 | 背压控制 | `BackpressureController`（`isWritable` 等待 + 断连放行 + 超时中止） | 可写前阻塞 / 断连放行 / 超时中止（4/4 通过） | T4.3 |
| [x] T4.5 | 流检查点 | `StreamCheckpoint` / `StreamCheckpointStore` / `CheckpointingTokenSink` | 断线后按 index 续接（6/6 通过） | T4.3 |
| [x] T4.6 | 端到端续接 | `AiChatHandler` / `AiResumeHandler` + `AI_CHAT`/`AI_RESUME`：请求→流→断线→续传 | **无缝续接**（e2e 2/2 通过，Phase 4 DoD） | T4.4,T4.5 |
| [x] T4.7 | 慢客户端测试 | `SlowClientBackpressureTest`（有界缓冲抑制生产） | 不丢 token / 生产不越缓冲（1/1 通过） | T4.4 |

---

## 横切任务（贯穿各 Phase）

| ID | 任务 | 验收点 |
|---|---|---|
| [x] TX.1 | `README.md`（模块/依赖/帧格式/协议表/快速上手） | 新人可读懂 |
| [~] TX.2 | `docs/linkgate-evidence.md`（测试证据已录；压测曲线 / 火焰图待真机） | 有据可查（部分） |
| [x] TX.3 | `docs/linkgate-interview.md`（故事弧 + §6 追问回答，结合实现） | 覆盖 spec §6 追问清单 |

---

## 里程碑（DoD 汇总）

| 里程碑 | 内容 | 对应验收标准（spec §0） |
|---|---|---|
| **M1 = Phase 1** | 两客户端互发 | 最小闭环可用 |
| **M2 = Phase 2** | 不丢/不乱/不重 + 重连续传 | 「无缝续接」「不丢不乱不重」 |
| **M3 = Phase 3** | 十万连接曲线 + 一次调优 | 「十万级」「before/after」 |
| **M4 = Phase 4** | AI token 流端到端 + 背压 | 「LLM 流端到端」「慢客户端不拖垮」 |

## 反模式提醒（spec §7）

- 先单体，别一上来微服务 + Kafka + 分库分表。
- 不做聊天 UI / 业务堆功能，重心在工程属性。
- 指标必须有数据支撑，别空吹。
