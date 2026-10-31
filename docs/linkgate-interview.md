# LinkGate 面试叙事（Interview Story）

> 把三层串成一个故事：长连接护城河 → 工程硬属性 → AI 实时桥。

## 1. 故事弧

1. **起点**：长连接是 AI 实时应用的地基，我要把它做扎实。
2. **工程**：做一个能扛十万连接、消息不丢、可观测的网关。
3. **桥（转折）**：把 LLM token 流接进来，撞上背压 / 续传 / 慢客户端 → 用长连接那套（水位、背压、重连）解决。
4. **证据**：压测曲线 + 一次调优 before/after + 端到端设计图。
5. **落点**：既够 JD4（AI + 实时通信）的门槛，又证明懂 JD3 的 AI 应用。

## 2. 可复用话术（已由代码支撑）

- 「我把 LLM 流式输出当**消息流**设计：分片、背压、续传。」
  → `TokenStream` 有界缓冲 + `BackpressureController` + `StreamCheckpoint`。
- 「多端 / 断连一致性，我用**会话状态机**收敛。」
  → `SessionState`（CONNECTED/AUTHENTICATED/RECONNECTING/CLOSED）+ `ReconnectPolicy` 宽限期续接。
- 「调优时瓶颈在 X，火焰图定位到 Y，改成 Z，P99 从 A 降到 B。」
  → 待真机跑出数据（#46/#47），模板见 `linkgate-bench-baseline.md`。
- 「十万连接是在〔机器规格〕上压的，区分了空闲与活跃。」
  → `BenchScenario.IDLE` / `ACTIVE` 两套场景。

## 3. 追问与回答（结合本工程实现）

**Q：为什么用 Netty？**
A：长连接高并发需要事件驱动的多路复用 IO。Netty 提供成熟的 EventLoop、`ByteToMessageDecoder` 家族（粘包/半包）、`ChannelGroup`、写水位与背压原语；自研这些成本高、坑多。我们把 Netty 限制在 `linkgate-netty` 一个模块，业务逻辑放 core，可脱网单测。

**Q：重连幂等怎么保证？**
A：登录不再无条件建会话。`ReconnectPolicy.connect` 按 `(userId, deviceId)` 查找已有会话：命中则**接管**（重绑连接、关闭旧连接、取消宽限期销毁），否则新建。断连时不是立刻销毁，而是置 `RECONNECTING` 并挂宽限期（默认 60s），期间可被重连接管。结果：同一设备永远只有一个会话（`ReconnectPolicyTest` / `LoginHandlerTest.reloginSameDeviceReusesSession`）。

**Q：慢客户端怎么办？**
A：三层防线。① `BackpressureController` 在写之前检查 `channel.isWritable()`，不可写就**阻塞桥接线程**；② `TokenStream` 是有界队列，桥接线程一停，队列就满，`offer` 阻塞，停止读 LLM 的 HTTP body；③ TCP 反压回 LLM 服务端。于是 token 不会堆在内存里，慢客户端只影响它自己那条流（`SlowClientBackpressureTest`）。

**Q：消息顺序怎么保证？**
A：每条流（如用户收件箱 `inbox:<userId>`）由 `Sequencer` 分配**服务端单调递增 seq**，随消息下发。接收端按 seq 排序即可；乱序/重复由 `Deduplicator` 与按 seq 重排处理（`ReliabilityScenarioTest.noReorder_*`）。续传也用同一个 seq：客户端只上报 lastSeq，服务端回补其后全部（`RESUME`）。

**Q：单机十万连接改了哪些内核参数？**
A：`ulimit -n`、`net.ipv4.ip_local_port_range`（压测机源端口）、`net.core.somaxconn`、`tcp_max_syn_backlog`、`tcp_tw_reuse`、收发缓冲；JVM 侧 `-Xms/-Xmx`、`MaxDirectMemorySize`、G1/ZGC。完整清单与记录模板见 `docs/linkgate-bench-baseline.md`。

**Q：消息可靠性怎么做到"不丢、不乱、不重"？**
A：不丢——投递前 `AckTracker` 记账并挂超时，收到 ACK 才销账，超时用 `HashedWheelTimer` 重传；不乱——`Sequencer` 服务端顺序号；不重——`Deduplicator` 按 messageId 去重；离线——`OfflineStore` 暂存，上线回补；断线——`MessageJournal` + `RESUME` 精确补缺口。三条属性各有场景测试。

**Q：AI 生成的流断了怎么续？**
A：生成在服务端继续（不因客户端断线而中止）。`CheckpointingTokenSink` 把每片 token 记入 `StreamCheckpointStore`（按 index），`ChannelTokenSink` 在连接断开时静默跳过写入。客户端重连后发 `AI_RESUME(streamId, lastIndex)`，服务端回补 index > lastIndex 的分片，流已完成则补 `AI_STREAM_END`（`AiStreamEndToEndTest`）。
