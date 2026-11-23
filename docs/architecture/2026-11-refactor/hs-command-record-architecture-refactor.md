# HS 命令系统 / 随录随传 架构重构设计（停止打补丁，回到分层）

> 背景：此前对 HS02 的多轮修复（write 类型、多源结算、`HsStreamStartGate`、停止兜底、`trustResponseAsWriteCompletion`、日志限流…）本质都是**补丁**。本文只谈架构：找出分层错误、给出目标结构、列出要删除的旧代码、分阶段迁移。**先定 design，再动核心代码。**

---

## 1. 补丁堆积暴露的真实设计缺陷

### 1.1 缺陷 A：把「传输写完成」当成了「协议命令完成」的前置条件（分层违例）

`CommandScheduler` 的整套复杂度都围绕 `node.writeCompleted` 打转：

- `CommandNode.bufferedFrames`：写完成前的匹配帧先缓冲，`onWriteCompleted` 再回放（H-14）。
- `sendWatchdog`（15s）：链路不回写完成回调时兜底。
- `node.writeStarted` / `node.writeCompleted` / `attemptId`：多处双写、双回放循环、判据不一致。
- `trustResponseAsWriteCompletion`（本次新增的开关）+ 快速路径 + `onWriteCompleted` 幂等：为了让「设备已回响应帧」绕开「GATT 写完成不回」。

**问题本质**：BLE 请求-应答里，**协议成功的权威信号是设备回的响应帧（或设备侧动作），不是 GATT 的 `onCharacteristicWrite`**。把传输层的写完成塞进协议命令状态机，导致：写完成可靠与否决定了命令能否完成；固件不回写完成 → 命令 10s 超时。所有补丁都在弥补这个错误耦合。

### 1.2 缺陷 B：把「随录随传」建模成一发一收的命令事务（语义错配）

`StartStreamRecordCommand` / `StopStreamRecordCommand` 被当作 REQUEST_REPLY 命令；但设备真实信号是**音频出流**与**控制上报 0x8004/0x8005**。于是需要：

- `HsStreamStartGate`：命令回执 ∪ 0x8004 ∪ 首片音频「多源结算」，还要 `replayCompletion` 主动把在途命令结算掉。
- 停止 `best-effort` + `STOP_FALLBACK_MS` 兜底计时器。

**问题本质**：随录随传是**流式/事件驱动的状态机**（IDLE→STARTING→RECORDING→STOPPING→IDLE），不是一次请求-回复事务。硬塞进命令事务，才需要闸门、多源、回放、兜底。

### 1.3 缺陷 C：设备怪癖散落成散装开关/常量

`trustResponseAsWriteCompletion`、`STOP_FALLBACK_MS`、`SYNC_TOTAL_TIMEOUT_MS`、音频日志限流字段、`WRITE_TYPE` —— 同一类设备事实（“写完成不可靠、应答走 Notify、音频即开始”）被拆到多个类的私有常量/开关里，无法统一治理。

### 1.4 缺陷 D：完成路径重复、竞态点多、可观测性差

`writeCompleted` 在两处置位、`bufferedFrames` 在两处回放、`attemptId` 判据一处用局部一处用 `node.attemptId`（恒真）、`StateListener` 线程不定。每一步都要额外守卫，维护成本高。

---

## 2. 目标架构

### 2.0 分层原则（唯一真源）

```
协议(protocol)  ── 只编码/解码/匹配；不认识队列/计时/传输
命令(command)   ── 协议事务：发送(fire) + 匹配入站帧 + 有界响应超时 → 完成/失败
传输(transport) ── 只回报“发送是否被受理(ACCEPTED/REJECTED)”，不回报“写完成”
流(stream)      ── 事件驱动状态机(录音/文件同步)，由设备信号驱动，不占用命令事务
编排(center)    ── 组合根：demux 入站帧、把流与命令分派
```

**核心不变量**：
1. **命令完成只由入站匹配帧驱动**；传输写完成不参与命令完成。
2. **发送是 fire-and-verify-accepted**：`write` 同步返回“受理/拒绝”；拒绝立即失败；受理后只等响应帧 + 有界超时。→ 删除 `writeCompleted/写窗口缓冲/sendWatchdog`。
3. **流（录音/文件同步）用独立状态机**，开始/停止用**单向发送命令**（one-way），启动/停止的“完成”由设备信号（首片音频、0x8004/0x8005、音频静默）驱动，不产生“命令回执”。

### 2.1 命令子系统：`CommandScheduler` → 拆分并重写为 `CommandExchange`

保留成熟的部分（`Command` 协议、`CommandPolicy` 声明式策略、`SetMerger`/`ResponseCache`/`RequestQueue` 容器思想），重写调度核心：

| 目标组件 | 职责 | 相对现状 |
|---|---|---|
| `CommandQueue` | 串行待发 + 唯一在途 + SET LATEST_WINS 合并 + GET 单飞 | 直接沿用 `RequestQueue`/`SetMerger` 容器与决策 |
| `CommandExchange` | 发送→等待→匹配→完成；**单一有界响应超时**；发送被拒立即失败 | 取代 `CommandScheduler` 的 writeCompleted/缓冲/sendWatchdog 机制 |
| `ResponseMatcher` | 对在途命令做 `matches()` + `parseResponse()` | 抽自现 `onResponseCandidate`/`processFrame`，**去掉 writeCompleted 分支** |
| `LateFrameIsolator` | 迟到帧族隔离 | 沿用 `LateFrameGuard`，但**不再依赖 writeStarted** |
| `Command` | 状态收敛为 `QUEUED → SENT → COMPLETED/FAILED`（多帧才 `RECEIVING`） | 删除 `SENDING`/写窗口相关语义 |
| `SendResult`（新） | 传输受理结果 `ACCEPTED/REJECTED(reason)` | 取代 `WriteCallback.onCompleted/onTimeout` 的“写完成”语义 |

**删除清单（旧代码）**：`CommandNode.writeStarted/writeCompleted/bufferedFrames`、`CommandScheduler.sendWatchdog`/`WRITE_COMPLETION_WATCHDOG_MS`、`onWriteStarted/onWriteCompleted` 的回放逻辑、`trustResponseAsWriteCompletion`、`Command.SENT` 作为“写完成”语义、`StateListener`（改为命令完成回调内直接给出阶段）。

### 2.2 传输端口：`HsCommandLink` 明确“只回报受理”

```java
interface HsCommandLink {
  interface SendCallback { void onAccepted(); void onRejected(Failure reason); }
  boolean send(byte[] bytes, SendCallback cb);   // 受理即 onAccepted，不再有“写完成/写超时”
}
```
- `YoBleCommandLink`：`client.write` 受理即 `onAccepted`（WITHOUT_RESPONSE）；未就绪 `onRejected`。**不再等待 `onCharacteristicWrite`**（这是设备事实，写进传输层一处即可，不再散落到 scheduler）。

### 2.3 流子系统：`HsStreamRecordController` 状态机（取代 `HsStreamRecordSession` 事务式）

```
IDLE ──start()──► STARTING ──首片音频/0x8004──► RECORDING
                      │(超时)                     │stop()/0x8005
                      ▼                           ▼
                    FAILED ◄──(超时)──────────  STOPPING ──音频静默/0x8005──► IDLE
```
- 开始/停止命令走 **one-way send**（不等回执、不占命令队列、不匹配帧）。
- `STARTING` 由“第一段非控制音频切片”或 `0x8004` 进入 `RECORDING`；`STOPPING` 由 `0x8005` 或“音频静默 N ms”进入 `IDLE`；两者都有单一有界超时。
- **删除** `HsStreamStartGate`、`replayCompletion` 主动结算、`STOP_FALLBACK_MS` 兜底（并入状态机的 STOPPING 超时）、`HsStreamRecordSession` 内 `startGate/stopSettled/reply*` 多源结算。
- 文件同步 `HsDeviceRecordSyncSession` 同样升为流状态机（进度速率 + 总超时）。

### 2.4 编排层 `HsProtocolEngine`

- 入站帧 demux：命中“命令响应族”→ 交给 `CommandExchange`；命中“设备事件/流控制”→ 交给 `StreamRegistry`/`DeviceEventPipeline`。**不再有 `onResponseCandidate` 的写窗口分支**。
- 命令完成 → 落域（沿用 `CommandCompletion`/`ResultKind`，去 `instanceof`）。
- 连接生命周期不变。

### 2.5 APP 层

- 现有 `WriteState{IDLE/SENDING/PROCESSING/CONFIRMED/FAILED}` 基本合理：`SENT`(受理) → `PROCESSING`，完成 → `CONFIRMED`。命令状态简化后映射更干净。
- 保留 seq 守卫、同能力在途去重、成功后保持目标值（产品已确认）。
- 顺带把 `DeviceHomeUiState.WriteState`（旧三态、无守卫）统一到一套写入状态模型。

---

## 3. 迁移阶段（每阶段独立可验证、保持全绿 + 真机回归）

| 阶段 | 内容 | 验收 |
|---|---|---|
| P0 | 本设计评审通过 | design 定稿 |
| P1 | 传输端口 `SendResult`：`HsCommandLink` 改为受理回调；`YoBleCommandLink` 首接受理；旧 `WriteCallback` 兼容层过渡 | 单测 + 真机连接/普通命令可用 |
| P2 | `CommandExchange` 重写（去 writeCompleted/缓冲/sendWatchdog/trustFlag）；迁移 `RequestQueue/SetMerger/ResponseCache/LateFrameGuard`；重写受影响的命令系统单测 | `biz_ggec_hs` 单测全绿；真机功能设置/查询正常 |
| P3 | 随录随传改 `HsStreamRecordController` 状态机 + one-way 命令；删除 `HsStreamStartGate`/replay/stopFallback；文件同步流化 + 总超时 | 真机 开始/停止、音频落盘正常 |
| P4 | 清理死代码（`Command.SENDING/writeStarted/writeCompleted` 相关）、日志/常量归位到设备事实单一处；APP 写入状态统一 | 全仓 grep 0 残留；`:app:assembleDebug` |
| P5 | 全量复审 + 真机回归（HS02） + 文档定稿 | 190+ 单测、真机三步验证 |

---

## 4. 验收与风险

- **验收**：`biz_ggec_hs / biz_ggec_wy / app` 单测全绿；边界门禁通过；`:app:assembleDebug`；HS02 真机：功能设置 `code=0`、随录随传 开始/停止/落盘正常、停止 ≤ 有界超时。
- **主要风险**：P2 `CommandExchange` 重写触及所有命令（核心）。缓解：保持 `Command`/`CommandPolicy`/`CommandCompletion` 契约不变，只换“调度+传输完成语义”；旧调度器单测改写到新语义并补充“发送被拒→立即失败”“只等响应帧→完成”“无写完成回调也能完成”等用例；每阶段真机回归。
- **兼容**：不保留旧调度双轨（遵既有“严禁兼容旧代码/严禁双轨”约定），一次性替换并把旧类删除。

---

## 5. 旧代码清理清单（汇总）

- 删除：`CommandScheduler` 的写窗口缓冲/sendWatchdog/双重置位/`trustResponseAsWriteCompletion`；`CommandNode` 的 `writeStarted/writeCompleted/bufferedFrames`；`HsStreamStartGate`；`HsStreamRecordSession` 的 `startGate/stopSettled/replay*/stopFallback`；`HsBleSettings` 的写类型“实验”痕迹（归入传输层一处常量）。
- 收敛：设备事实（写受理即完成、应答走 Notify、音频即开始）集中到「传输层 + 流状态机」两处，不再散落常量/开关。
- 统一：APP 侧写入状态模型（设置页 + 首页）。

> 一句话：**把“写完成”从命令完成里拿掉（缺陷 A），把“录音”从事务里拿掉（缺陷 B），把设备事实收敛到传输/流两处（缺陷 C），完成路径只剩一条（缺陷 D）。**

---

## 6. 落地记录（本轮已实施 P1–P3）

### 6.1 命令层（缺陷 A 修复）
- `CommandScheduler.Transport` 改为 `boolean send(long sessionId, String payload)`（同步“是否受理”，删除 `Transport.Callback`）。
- `startCommand`：`setState(SENT)` → `transport.send(...)` 受理后启动**唯一有界响应超时**；未受理立即失败。**入站匹配帧即处理完成**（`onResponseCandidate` 去掉 `!writeCompleted` 缓冲/快速路径/trustFlag）。
- 删除整套写完成机制：`WRITE_COMPLETION_WATCHDOG_MS`、`sendWatchdog`、`trustResponseAsWriteCompletion`(+setter)、`onWriteStarted/onWriteCompleted/armSendWatchdog/cancelSendWatchdog`、`CommandNode.writeStarted/writeCompleted/bufferedFrames`；`LateFrameGuard.register` 去 `writeStarted` 门槛。
- `HsProtocolEngine.LinkTransport.send` 返回 `link.write(bytes, NOOP)`；新增 `sendOneWay(Command)`（直接写链路、不进命令队列）。

### 6.2 随录随传（缺陷 B 修复）
- `HsStreamRecordSession` 重写为事件驱动状态机 `STARTING→RECORDING→STOPPING→IDLE`：
  - 开始/停止 **单向发送**（`sendOneWay`），不进命令队列、不等命令回执；
  - `STARTING` 由「首段音频出流」或控制上报 `0x8004` 进入 `RECORDING`（有界 2s 超时）；
  - `STOPPING` 由控制上报 `0x8005` 或「音频静默 800ms」进入 `IDLE`（有界 3s 兜底）；
  - 删除 `HsStreamStartGate`（多源结算）、`replayCompletion` 主动结算、停止兜底常量等补丁。
- `CommandPolicyRegistry`：为 `StartStreamRecord/StopStreamRecord/StartTransmitRecord` 声明 10s 响应窗口（协议自描述，非补丁）。

### 6.3 验证
- 单测/门禁：`biz_ggec_hs 184 / biz_ggec_wy 34 / app 627` 全绿；`checkBizGgecHsBoundary`、`checkBizProtocolPurity`、`:app:assembleDebug` 通过。
- HS02 真机：功能设置（佩戴检测）`code=0`；随录随传 **启动 148ms（首段音频驱动）**、**停止 ≤3s 有界兜底并落盘**（218 帧/4.36s）。
 - 注：`HsSessionImplTest.beginnerModeToggleReflectsCommandAck` 曾在一次全量运行中偶发（隔离运行与复跑均通过），列为 flaky 观察项，后续 P5 复审关注。

### 6.4 待后续（P4/P5）
- P4 清理：删除 `Command.State.SENDING` 死枚举、APP `DeviceHomeUiState` 旧写入状态统一、日志/常量归位、`HsCommandSystemHardeningTest` 与设计对齐复核。
- P5：全量复审（子代理）+ HS02 真机回归 + 文档定稿。

---

## 7. P5 全量复审与收尾（已执行）

### 7.1 P4 完成
- `Command.State.SENDING` 删除（命令状态现为 `QUEUED/SENT/RECEIVING/COMPLETED/FAILED`）。
- APP 写入状态统一：新增顶层 `ui/device/WriteState`，设置页与首页共用；首页 `DeviceHomeViewModel` 增 seq 守卫 + `SENDING→PROCESSING→CONFIRMED/FAILED`；`DeviceHomeUiMapper` 复用 `TogglePresentation`；两页语义一致（点击即切换/处理中禁用/成功后保持/失败回退）。

### 7.2 复审发现与修复
- **flaky 根因（已修）**：`HsProtocolEngine.submit` 完成监听器注册顺序为“先消费回调、后落域”，在测试内联主线程替身下 `read` 可能先于落域 → `beginnerModeToggleReflectsCommandAck` 偶发失败。已调整为**先注册 `persistCompletion` 再注册对外回调**（落域先于回执）。`HsSessionImplTest` 连跑 3 次全绿。
- **随录随传健壮性（已修）**：开始回执（含失败）统一派发主线程（避免 BLE 回调栈内 detach 重入）；`STARTING` 计时从进入即起（覆盖 Notify 就绪等待，3s）；`stop()` 幂等（重复停止不覆盖在途 reply）。
- **APP 清理（已修）**：删除首页死方法 `putWrite`；`roster` 改 `volatile`；订正 `TogglePresentation`/`LevelPresentation` Javadoc 漂移。

### 7.3 验证
- 全量门禁（`--rerun-tasks` 强制重跑）：`biz_ggec_hs 184 / biz_ggec_wy 34 / app 627` 全绿；`checkBizGgecHsBoundary`、`checkBizProtocolPurity`、`:app:assembleDebug` 通过。
- `HsSessionImplTest` 连跑 3 次全绿（flaky 消除）。
- HS02 真机：佩戴检测开关 `code=0`（off→on）；随录随传 启动 157ms（首段音频驱动）、停止 ≤3s 有界兜底、落盘 222 帧/4.44s。

### 7.4 已知余项（记录，不阻塞）
1. **设置页 `RECORD_STREAM` 动作仍走命令调度器**：`DeviceSettingsActivity.onStartRecordStream → perform(StartRecord(STREAM)) → CommandScheduler`。但设备「录音」入口实际由 `RecordActivity`/`RecordPort`（事件驱动）承载，`DeviceSettingsActivity` 不加载 RECORD 组，故该动作是**不可达的旧路径**。属“双轨残留”，建议后续随产品接线一并删除或改走 `RecordPort`。
2. **测试缺口**：随录随传状态机（STARTING 超时/0x8004/0x8005/音频静默/幂等 stop）与命令核心（多帧 inter-frame、GET 单飞+缓存、SET 合并、会话切换中止）尚无直接单测；`DeviceHomeViewModel` 三态守卫无单测（首页内联开关当前 `QUICK_TOGGLE_IDS` 为空、不可达）。
3. **无主线程退化**：`HsStreamRecordSession` 的计时依赖 `mainHandler`，无 Looper 环境退化（生产不涉及）。
4. **文件同步**：`StartTransmitRecordCommand` 仍走调度器 10s 窗口，若设备同样不回执属同类隐患，真机未覆盖。

---

## 8. 一步到位收尾（A/B/C 已实施）

### 8.1 A：删除 RECORD_STREAM 命令调度死双轨
- `HsCapabilityRouter.commands(Action)`：`StartRecord/StopRecord(STREAM)` 返回**空列表**（不再生成 `Start/StopStreamRecordCommand`）；`LOCAL` 不变；`capabilityOf(STREAM)` 仍 `RECORD_STREAM`。
- `HsSessionImpl.perform`：命令列表为空→立即 `Err(UNSUPPORTED_CAPABILITY)`（避免空命令挂起）。
- `HsCapabilityRouterTest`：STREAM 断言改为空列表。
- 随录随传命令类保留（`HsStreamRecordSession` 经 `sendOneWay` 使用）。**真实入口 `RecordActivity`/`RecordPort` 事件驱动不变**。

### 8.2 B：文件同步事件驱动
- `HsDeviceRecordSyncSession`：`sendOneWay(StartTransmitRecordCommand/CancelTransmitRecordCommand)` 启动/取消，不再 `engine.submit` 等命令回执；完成以**数据进度**驱动——`receivedBytes>=expectedTotalBytes`（已知大小时）或**数据静默 1.5s 判定传完**（设备文件大小当前调用方传 0，故为主路径），外加 120s 总超时兜底；收尾统一主线程退订 + 单线程关资源。
- 未新增单测（Android/native 依赖，JVM 单测无法构造）；**未真机覆盖**（缺设备文件传输触发路径）。

### 8.3 C：命令核心补测
- `HsCommandSystemHardeningTest` +4 用例（确定性 `ManualDispatcher` 手动时钟/`fireScheduled`）：多帧 inter-frame 重臂与完成后忽略、GET 单飞+缓存 TTL、SET 同值合并（JOIN_CURRENT）、SET 异值顶替（SUPERSEDE）。

### 8.4 验证
- 全量门禁：`biz_ggec_hs 188 / biz_ggec_wy 34 / app 627` 全绿；`checkBizGgecHsBoundary`、`checkBizProtocolPurity`、`:app:assembleDebug` 通过。
- HS02 真机：佩戴检测 `code=0`（off→on）；随录随传 启动 162ms（首帧音频驱动）、停止 ≤3s 有界兜底、落盘 222 帧/4.44s。

### 8.5 余项（记录）
- APP 设置页 `RECORD_STREAM` 死按钮（RECORD 组经 `DeviceSettingsActivity` 不可达）未删除；命令层已切断，若触达仅返回 `UNSUPPORTED_CAPABILITY`，不影响真实 `RecordActivity` 入口。彻底移除需改 `HsControl.Action` 公共 API 与 UI 接线，属产品决策。
- 文件同步缺真机覆盖；`HsRecordManager.syncDeviceRecord` 仍传 `expectedTotalBytes=0`，现依赖数据静默判定（1.5s），建议后续从录音列表带出真实大小。

---

## 9. 文件录音（设备本地录音）链路核对与真机

### 9.1 两个录音功能（产品口径）
- **随录随传（边录边传）**：实时音频流→App。`HsStreamRecordSession`（事件驱动流状态机）+ `StreamRecordPanel`。
- **文件录音（先录后取）**：设备自己录（开始/结束）→ 拉设备录音列表 → 展示 → 「获取」把某文件传回手机。
  App 链路：`RecordPort.startDeviceRecord / stopDeviceRecord / refreshDeviceRecords / syncDeviceRecord / deleteDeviceRecord` → `HsRecordAdapter` → `HsRecordManager`：
  - 开始/结束：`StartLocalRecordCommand(99ECBB)` / `StopLocalRecordCommand(99ECBC)`（命令事务，设备本地录制）；
  - 列表：`GetRecordIdListCommand`（GET，落 `recordFiles` 快照，`LocalRecordPanel` 展示）；
  - 获取：`HsDeviceRecordSyncSession`（820003 Notify → Opus 解码 → 落盘，本轮已改事件驱动）。
  UI：`RecordActivity` 两个 Tab —— `StreamRecordPanel`（随录随传）/ `LocalRecordPanel`（文件录音：设备录音列表 + 获取）。

### 9.2 真机发现（HS02，16:45）
- 点「本地录音」Tab →「开始录音」：下发 `StartLocalRecordCommand 99ECBB0001001234`，**2s 内设备未在 Notify 回 `99ECBB`** → 默认 2s 响应窗口 `FAILED：等待设备响应超时`。与随录随传早期同类（声明窗口过短）。
- 已把 `StartLocalRecordCommand/StopLocalRecordCommand` 的响应窗口从默认 2s 提到 10s（`CommandPolicyRegistry` 的 `STREAM_RECORD_RESPONSE_TIMEOUT_MS` 组）——协议自描述响应超时，非补丁。

### 9.3 待办（需真机稳定时复测）
- 复测 文件录音「开始/结束/列表/获取」全链路（本轮 HS02 链路不稳：A2DP/HEADSET 反复 connect/disconnect、App BLE 未到 READY，端到端未完成）。
- 确认设备是否真会回 `99ECBB/99ECBC`：若仅为“慢回执”，10s 窗口可解；若**永不回执**，则本地录音成功判定缺少设备信号（命令 ack 是唯一信号），需与固件确认或改判定策略。
- 文件传输（获取，`HsDeviceRecordSyncSession` 事件驱动 + 数据静默判定）亦未真机覆盖。

