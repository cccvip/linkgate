# LinkGate 验收测试文档

> 目的：区分「代码正确性」与「业务完整性」。单元 / 集成测试证明代码写得对；本文档给出**业务级验收点**，
> 逐条验证后才有资格说"业务目标达成"。

## 1. 测试分层与现状

| 层 | 回答什么问题 | 现状 |
|---|---|---|
| 单元测试 | 单个类逻辑对不对 | ✅ 已覆盖，`mvn verify` 176 个 |
| 集成测试 | 模块之间、与真实 Redis/MySQL/socket 协作对不对 | ✅ 真连本地容器 |
| **业务验收** | 业务要的能力是否真的能交付、可演示、有证据 | ⚠️ 见下表，**部分需真机/真实 LLM** |

一句话：**单测保证代码质量，验收保证业务交付**。两者不能互相替代。

## 2. 验收环境

```powershell
docker compose up -d     # Redis(6379) + MySQL(3306)
mvn -B verify            # 176 个单测/集成测试
```

业务验收额外需要：

- **真实 LLM**：一个 OpenAI 兼容 endpoint + API key（验证 TP-D4）。
- **压测机**：Linux、可调 `ulimit` / 端口 / JVM（验证 TP-E4/E5）。

---

## 3. 业务测试点

状态图例：✅ 已有自动化覆盖　⚠️ 需人工 / 真实环境验收。

### A. 接入与协议（Phase 1）

| 编号 | 业务验收点 | 怎么执行 | 预期 | 证据 | 状态 |
|---|---|---|---|---|---|
| TP-A1 | 两个客户端登录后互发消息 | `GatewayEndToEndTest` | A 发、B 收，各回 ACK | 测试通过 | ✅ |
| TP-A2 | 未登录发消息/心跳被拒 | `ChatHandlerTest` / `HeartbeatHandlerTest` | 回 ERROR，不建会话 | 测试通过 | ✅ |
| TP-A3 | 心跳保活 + 读空闲断开 | `IdleHeartbeatHandlerTest` | 写空闲发心跳、读空闲关闭 | 测试通过 | ✅ |
| TP-A4 | 连接数 / 建连速率限制 | `ConnectionLimiterTest` / `ConnectionLimitHandlerTest` | 超限拒绝、断开归还 | 测试通过 | ✅ |

### B. 消息可靠性（Phase 2）

| 编号 | 业务验收点 | 怎么执行 | 预期 | 证据 | 状态 |
|---|---|---|---|---|---|
| TP-B1 | 不丢：丢包后重传送达 | `ReliabilityScenarioTest.noLoss_*` | 首次丢包 → 超时重传 → 送达并销账 | 测试通过 | ✅ |
| TP-B2 | 不重：同 messageId 幂等 | `ChatHandlerTest.duplicateMessageIdIsDeliveredOnce` | 第二次回 DUPLICATE，不重复投递 | 测试通过 | ✅ |
| TP-B3 | 不乱：服务端 seq 单调 | `SequencerTest` + `ReliabilityScenarioTest.noReorder_*` | 并发无重复、按 seq 重排恢复原序 | 测试通过 | ✅ |
| TP-B4 | 断线重连会话续接 | `GatewayEndToEndTest.resumeReplaysMissingMessagesAcrossReconnect` | 同一 sessionId + RESUME 补缺口 | 测试通过 | ✅ |
| TP-B5 | 离线消息补投 | `GatewayEndToEndTest.offlineMessageIsReplayedOnLogin` | 离线发、上线收到 | 测试通过 | ✅ |
| TP-B6 | 消息流 RESUME 续传 | `GatewayEndToEndTest` | lastSeq 回补其后消息，无缺口 | 测试通过 | ✅ |

### C. 路由 / 多端（Phase 2）

| 编号 | 业务验收点 | 怎么执行 | 预期 | 证据 | 状态 |
|---|---|---|---|---|---|
| TP-C1 | 多端同收 | `ChatHandlerTest.deliveriesToAllDevicesOfTarget` | 同用户多设备都收到 | 测试通过 | ✅ |
| TP-C2 | 群聊扇出 | `GroupAndMultiSyncHandlerTest.groupChatFansOutToJoinedMembers` | 群成员收到、非成员不收 | 测试通过 | ✅ |
| TP-C3 | 多端同步 | `GroupAndMultiSyncHandlerTest.multiSyncReachesOtherDevicesOnly` | 其它设备收到、来源不收 | 测试通过 | ✅ |
| TP-C4 | Redis 在线/路由 + MySQL 快照 | `MiddlewareIntegrationTest`（真 Redis/MySQL） | 登录写入、断连清理 | 测试通过 | ✅ |
| TP-C5 | **跨节点投递** | 无 | 多节点间转发 | 无 | ⚠️ 单机未实现 |

### D. AI 流式（Phase 4）

| 编号 | 业务验收点 | 怎么执行 | 预期 | 证据 | 状态 |
|---|---|---|---|---|---|
| TP-D1 | AI_CHAT → token 流回推 | `AiStreamEndToEndTest.streamsAllTokensWhenClientStaysConnected` | 收全分片 + AI_STREAM_END | 测试通过 | ✅ |
| TP-D2 | AI 流断线续接 | `AiStreamEndToEndTest.resumesAiStreamAfterReconnect` | 重连 AI_RESUME 补 index 缺口 | 测试通过 | ✅ |
| TP-D3 | 慢客户端背压不拖垮 | `SlowClientBackpressureTest` | 不丢 token，生产不越缓冲 | 测试通过 | ✅ |
| TP-D4 | **真实 LLM 调用** | `GatewayMain` + 真实 key，手动发 AI_CHAT | 收到真实模型流式回答 | 截图/日志 | ⚠️ 需真实 API |

### E. 观测 / 压测（Phase 3，最后执行）

| 编号 | 业务验收点 | 怎么执行 | 预期 | 证据 | 状态 |
|---|---|---|---|---|---|
| TP-E1 | Prometheus 指标暴露 | `MetricsReporterTest` | `/metrics` 可抓取 | 测试通过 | ✅ |
| TP-E2 | IDLE 空闲连接压测 | `BenchMain <host> <port> N IDLE 60` | 连接建满、CPU/内存曲线 | 记录表 | ⚠️ 需压测机 |
| TP-E3 | ACTIVE 活跃消息 P50/P99 | `BenchMain <host> <port> N ACTIVE 60` | 吞吐 + 延迟分位 | 记录表 | ⚠️ 需压测机 |
| TP-E4 | **十万连接** | 压测机按基线调参后跑 IDLE/ACTIVE | 十万连接扛住 | 曲线+机器规格 | ⚠️ 需压测机 |
| TP-E5 | **一次真实调优 before/after** | async-profiler/JFR 采样 → 改 → 复测 | P99 或吞吐有可量化改善 | 火焰图+对比表 | ⚠️ 需压测机 |

---

## 4. 业务验收 Checklist（面试前逐条打钩）

对应 spec §0 的五条：

- [ ] ① 十万连接：机器规格 + 空闲/活跃两套曲线（TP-E2/E3/E4）
- [x] ② 断线重连无缝续接（TP-B4/B6 + TP-D2）
- [x] ③ 消息不丢/不乱/不重（TP-B1/B2/B3）
- [ ] ④ 一次真实调优 before/after（TP-E5）
- [x] ⑤ LLM token 流端到端 + 慢客户端不拖垮（TP-D1/D3）
  - [ ] 补充：真实 LLM 冒烟（TP-D4）

## 5. 尚未闭环的验收点（诚实清单）

| 缺口 | 为什么 | 补法 |
|---|---|---|
| 十万连接 | 无压测机数据 | 压测机跑 TP-E4 |
| 一次调优 | 无火焰图/对比 | 压测机跑 TP-E5 |
| 真实 LLM | 测试用的是脚本假客户端 | 填真实 key 跑 TP-D4 |
| 跨节点投递 | 当前单体，`ConnectionRouter` 写了路由但无跨节点转发 | 需要则后续加 |
| 观测"定时上报" | 实现为 Prometheus pull 端点（更符合 Prometheus 模型） | 按需接 Grafana |

> 结论：**代码完整性 ✅，业务完整性已达成 3/5 条验收，剩余 2 条 + 真实 LLM 冒烟需要你的压测机 / API key。**
