# LinkGate

AI 实时会话网关。基于 Netty 的长连接网关，负责消息可靠投递（不丢 / 不重 / 不乱）、会话续传、多端路由，并把 LLM 的 token 流接入（分片 / 背压 / 断点续接）。

> 设计文档 [`docs/linkgate-spec.md`](docs/linkgate-spec.md) · 任务台账 [`docs/linkgate-tasks.md`](docs/linkgate-tasks.md) · 业务验收 [`docs/linkgate-acceptance.md`](docs/linkgate-acceptance.md)

## 特性

- **二进制定长帧协议** + JSON 载荷，自动处理粘包 / 半包 / 最大帧限制
- **可靠投递**：投递确认（ACK）+ 超时重传 + 幂等去重 + 服务端顺序号
- **会话续传**：断线进入宽限期，同设备重连复用会话，`RESUME` 按 seq 回补缺口
- **多端 / 群组**：`MULTI_SYNC` 多端同步、`GROUP_JOIN / LEAVE / CHAT` 群组扇出
- **AI 流式**：OpenAI 兼容 SSE → token 分片下发 → 有界缓冲背压 → 检查点续接
- **观测**：Spring Boot actuator（`/actuator/health`、`/actuator/prometheus`）、滚动日志、Grafana dashboard
- **压测**：独立 bench 客户端，IDLE / ACTIVE 两种场景

## 模块

```
linkgate
├── linkgate-core      消息、会话、可靠性、端口抽象（无 IO 网络栈，可脱网单测）
├── linkgate-netty     接入 + 协议 + 会话 + pipeline（唯一碰 Netty 网络栈的模块）
├── linkgate-ai        LLM 客户端、token 流、背压、检查点、流式桥接
├── linkgate-store     Redis（在线状态 / 路由）与 MySQL（消息 / 元数据）
├── linkgate-admin     Spring Boot 组装根 + actuator 管理面
├── linkgate-bench     压测客户端（IDLE / ACTIVE）
└── linkgate-gui       JavaFX 桌面客户端
```

依赖方向（无环）：

- `core`：不依赖任何内部模块
- `ai` → `core`，`store` → `core`
- `netty` → `core` / `ai` / `store`
- `admin` → `core` / `netty` / `ai` / `store`
- `bench` → `core` / `netty`
- `gui` → `netty`

> `core` 允许使用 `netty-common` 这类**无 IO 工具库**（如 `HashedWheelTimer`），但不引网络栈；数据面保持裸 Netty，Spring Boot 只出现在 `admin` 组装层。

## 快速开始

### 方式一：Spring Boot（推荐）

一条命令起网关 + 管理面，配置走 `linkgate-admin/src/main/resources/application.yml`：

```powershell
docker compose up -d
mvn -B -pl linkgate-admin -am -DskipTests package
java -jar linkgate-admin\target\linkgate-admin-0.1.0-SNAPSHOT.jar
```

- 网关（数据面）：`8080`
- 管理面：`http://localhost:8090/actuator/health`、`/actuator/prometheus`
- 日志：`logs/linkgate.log`（滚动文件）

### 方式二：纯 Netty（无 Spring）

```powershell
docker compose up -d
mvn -B -pl linkgate-netty -am -DskipTests package

$env:LINKGATE_REDIS_URI='redis://127.0.0.1:6379'
$env:LINKGATE_MYSQL_URL='jdbc:mysql://127.0.0.1:3306/linkgate?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC'
$env:LINKGATE_MYSQL_USER='linkgate'
$env:LINKGATE_MYSQL_PASSWORD='linkgate'
java -jar linkgate-netty\target\linkgate-netty-0.1.0-SNAPSHOT-all.jar
```

> 本地开发也可用根目录的 `run-gateway.ps1`（内置上述默认值，已被 `.gitignore` 排除，不随仓库分发）。

## 客户端

| 客户端 | 入口 | 用途 |
|---|---|---|
| JavaFX GUI | `com.linkgate.gui.JavaFxChatClient` | 桌面演示：连接登录、收发消息、登出 |
| 脚本演示 | `com.linkgate.netty.client.ClientDemo` | 两个客户端完成登录 → 聊天 → 投递 |
| 可脚本化 | `com.linkgate.netty.client.TestClient` | 程序化收发 `Frame`，供集成测试 |
| 压测 | `com.linkgate.bench.BenchMain` | 多连接、P50/P99 采样 |

JavaFX 桌面客户端（先 install 依赖，再用 javafx-maven-plugin 启动）：

```powershell
mvn -pl linkgate-gui -am install -DskipTests
mvn -pl linkgate-gui javafx:run
```

- 顶部填 `host / port / userId / deviceId`，点「连接登录」
- 底部「to + text + 发送」发点对点消息
- 底部「命令」框支持 `login / chat / ack / raw / help / quit`
- 关闭窗口 = 登出（断开连接）；`quit` = 退出程序
- 客户端每 25 秒发一次心跳，避免被服务端 60 秒读空闲超时断开

压测客户端：

```powershell
mvn -pl linkgate-bench exec:java "-Dexec.mainClass=com.linkgate.bench.BenchMain" "-Dexec.args=127.0.0.1 8080 10000 IDLE 60"
```

## 协议

大端序，帧头 18 字节，body 当前为 JSON：

```
offset  0      2      3      4      5      6            14          18
        +------+------+------+------+------+-------------+-----------+
        | magic| ver  | flags| type | rsvd | seq (int64) | bodyLen   |
        | 2B   | 1B   | 1B   | 1B   | 1B   | 8B          | 4B (int32)|
        +------+------+------+------+------+-------------+-----------+
        | body (bodyLen bytes, JSON)                                |
        +------------------------------------------------------------+
```

`magic = 0x4C47`（字符 `LG`），`bodyLen` 仅表示 body 长度，上限 1 MiB。

### 消息类型

| code | 类型 | 说明 |
|---|---|---|
| 1 / 2 | LOGIN / LOGIN_ACK | 登录，返回 sessionId |
| 3 / 4 | HEARTBEAT / HEARTBEAT_ACK | 心跳保活 |
| 5 / 6 | CHAT / CHAT_ACK | 点对点消息 |
| 7 | ACK | 投递确认（销掉 ACK 跟踪） |
| 8 / 9 | AI_STREAM / AI_STREAM_END | AI 流式分片 / 结束 |
| 10 | ERROR | 错误 |
| 11 / 12 | RESUME / RESUME_ACK | 消息流续传 |
| 13 / 14 / 15 | AI_CHAT / AI_RESUME / AI_RESUME_ACK | AI 生成 / 续接 |
| 16 / 17 / 18 / 19 | GROUP_JOIN / GROUP_LEAVE / GROUP_CHAT / GROUP_ACK | 群组 |
| 20 / 21 | MULTI_SYNC / MULTI_SYNC_ACK | 多端同步 |

## 构建与测试

```bash
mvn -B verify
```

- 编译 + 全部单测 / 集成测试
- 集成测试会真连 Redis / MySQL；中间件不可用时自动跳过相关用例

## 配置与日志

- **Spring Boot**：`linkgate-admin/src/main/resources/application.yml`
  - `server.port`（管理面，默认 `8090`）
  - `linkgate.port`（网关，默认 `8080`）
  - `linkgate.redis.*` / `linkgate.mysql.*` / `linkgate.llm.*`
- **纯 Netty**：环境变量 `LINKGATE_*`（见上方快速开始）
- **日志**：admin 用 `logback-spring.xml`（控制台 + `logs/linkgate.log` 滚动）；纯 Netty 路径用 `linkgate-logback.xml`

## 状态

- Phase 0 / 1 / 2 / 4 完成；Phase 3 代码与文档就绪（6/8）
- Redis / MySQL 中间件已接入主链路：登录写在线状态 / 路由 / 会话快照，断连清理，消息日志可切 MySQL 持久化
- 十万连接曲线与一次真实调优需在独立压测机上产出（`#46` / `#47`，见 [`docs/linkgate-bench-baseline.md`](docs/linkgate-bench-baseline.md)）
