# LinkGate 验收证据（Evidence）

> 对照 spec §0 的五条验收标准，标注**已被测试证明**的部分与**需真机产出**的部分。

## §0 验收标准 → 证据

| # | 验收标准 | 状态 | 证据 |
|---|---|---|---|
| 1 | 连接数压到**十万级**（写清机器规格、空闲 vs 活跃） | 🟡 工具就绪，数据待真机 | `BenchClient` / `BenchMain` + [`linkgate-bench-baseline.md`](linkgate-bench-baseline.md)；本机仅做小规模自测（`BenchClientTest`） |
| 2 | 断线重连后，**会话与流式输出都能无缝续接** | ✅ 已证明 | `GatewayEndToEndTest.resumeReplaysMissingMessagesAcrossReconnect`（会话复用 + RESUME 补齐 seq 4,5）；`AiStreamEndToEndTest.resumesAiStreamAfterReconnect`（AI 流回补 index 2..5） |
| 3 | 消息可靠性：**不丢、不乱、不重** | ✅ 已证明 | `ReliabilityScenarioTest`：丢包→ACK 超时重传→送达；重复→去重；乱序→按 seq 重排 |
| 4 | **一次真实调优**的 before/after | 🔴 待真机 | #46 性能分析 / #47 调优（需独立压测机与 async-profiler/JFR） |
| 5 | LLM token 流端到端，**慢客户端不拖垮服务** | ✅ 已证明 | `AiStreamEndToEndTest.streamsAllTokensWhenClientStaysConnected`；`SlowClientBackpressureTest`（有界缓冲抑制生产，`maxAhead ≤ buffer+2`） |

## 测试分布（`mvn -B verify`）

| 模块 | 数量 | 重点 |
|---|---|---|
| core | 61 | 顺序号并发、去重、ACK 重传、离线、续传日志、重连、可靠性场景 |
| ai | 21 | SSE 解析、LLM 客户端（本地 SSE 服务）、推送→拉取、检查点、慢客户端背压 |
| netty | 69 | 编解码、粘包/半包、pipeline、心跳、连接限制、注册表、分发、端到端 |
| admin | 3 | 指标导出、`/metrics` 抓取 |
| bench | 2 | IDLE / ACTIVE 场景 |

## 关键 e2e 输出（可直接复现）

```
# 会话续传
bob 收 seq 1,2,3 → 断线 → 服务端发 4,5
bob 重连(RESUME lastSeq=3) → 回补 4,5 → RESUME_ACK{replayed=2}

# AI 流续接
bob AI_CHAT(s1) → 收 index 0,1 → 断线
服务端继续生成完（检查点记全 6 片）
bob 重连(AI_RESUME lastIndex=1) → 回补 2,3,4,5 → AI_STREAM_END(6)
```

## 待补（需你的环境）

1. **十万连接曲线**：按 `linkgate-bench-baseline.md` 在 Linux 压测机跑 `BenchMain`，区分 IDLE/ACTIVE，填记录表。
2. **一次调优 before/after**：async-profiler/JFR 采样 → 定位 → 改 → 复测，填调优记录表。
3. **Grafana 面板渲染**：导入 `docs/grafana/linkgate-dashboard.json` 并接 Prometheus。
