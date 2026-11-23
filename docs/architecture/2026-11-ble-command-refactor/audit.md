# 架构审查报告（2026-11 BLE / 命令系统大整理）

> 方法：YoAgentDocs `task.architecture-review`（六段式）+ 4 个只读子代理并行深读。
> 证据等级：本仓无真机、无运行观察，全部为 **已读到**（`文件:行` 证明边存在，不证明运行路径被走到）。
> 详细分报告（逐行证据，未删节）：
> - `.scratch/audit/command.md`（biz 命令系统/协议层，250 行）
> - `.scratch/audit/link.md`（biz 链路/会话/Gatt 收发，189 行）
> - `.scratch/audit/libbt.md`（lib_bluetooth BLE 逐层，224 行）
> - `.scratch/audit/autoreconnect.md`（全仓自动回连定位，138 行）
> 基线：`biz_ggec_hs` 167 tests / `lib_bluetooth` 230 tests，全绿（见 `.scratch/baseline_test.log`）。

## 范围

| 范围 | 所有权 | 覆盖 |
|------|--------|------|
| R1 biz_ggec_hs 命令系统 | 协议/调度 | `internal/center/protocol/**`、`internal/center/exchange/**`、`internal/center/port/HsCommandLink` |
| R2 biz_ggec_hs 链路/会话 | 栈对接/编排 | `provider/lib_bluetooth/**`、`internal/link/**`、`internal/interact/HsCapabilityRouter`、`internal/center/HsGraph` |
| R3 lib_bluetooth BLE | 蓝牙栈 | `internal/ble/{gatt,transfer,channel,connection,runtime,platform}`、`api/`、`model/ble/` |
| R4 自动回连 | 全仓 | app / 全部 biz / lib_bluetooth / lib_common |

---

## R1 biz_ggec_hs 命令系统与协议层

### 1. 设计前链路（已读到）

`HsGraph.open` → `new HsProtocolEngine(link)` → `new CommandScheduler(ExecutorDispatcher, LinkTransport, snapshot->{}, CommandPolicyRegistry)`（`HsProtocolEngine.java:92-96`）+ `new InboundFrameDemux(eventPort, scheduler::onResponseCandidate, ()->{}, sessionGate)`（`:97-101`）。命令提交方：`HsSessionImpl`、`HsRecordManager`、`HsDeviceRecordSyncSession`、`HsStreamRecordSession`。

### 2. 逐项清单

| 项 | 结果 | 证据 |
|----|------|------|
| 单一职责 | **失败** | `CommandScheduler` 单类身兼 15 类独立职责（见 §3 表） |
| 协议与调度解耦 | **失败** | `Command` 同时是 codec 与调度语义载体，见问题 Q2 |
| 隐式字符串约定 | **失败** | 6 字符前缀 + `"1234"` 尾标 + 长度字段全按 hex 下标切片；响应族=请求前缀前 6 字符/退化类名 |
| 无多源真值 | **失败** | 会话身份、命令完成、在仓状态、未知表示均多源（详见 command.md §2.7） |
| 无死代码 | **失败** | `CommandTransaction` 全仓无 `new`，事务分支不可达（command.md §2.5） |
| 无丢帧/挂死窗口 | **失败** | 写启动前应答丢帧；响应超时仅写完成后启动，链路不回回调则无限挂起 |
| 命令内聚/可扩展 | **失败** | 新增一条命令需改 2~8 处（command.md §4） |

### 3. `CommandScheduler` 的 15 类职责（`CommandScheduler.java` 行号区间）

| # | 职责 | 行区间 |
|---|------|--------|
| 1 | 单命令准入（会话校验/claim/编码/策略解析） | 139-199 |
| 2 | 事务准入（与 #1 平行的第二套） | 201-250 |
| 3 | GET 去重缓存 + 单飞分组 | 252-281 |
| 4 | SET 合并（LATEST_WINS/同值 join/覆盖） | 283-340 |
| 5 | 会话生命周期（setSession/abortAll/release） | 342-488 |
| 6 | 队列推进 / 事务步进 | 490-539 |
| 7 | 下发 + GATT 写状态机（含隔离延迟） | 541-610 |
| 8 | 写缓冲（写完成前帧暂存/回放） | 612-645 |
| 9 | 响应解析/完成推进 | 647-666 |
| 10 | 完成收尾（缓存/跟随者回放/事务汇总） | 668-741 |
| 11 | 超时（首帧/帧间） | 743-780 |
| 12 | 迟到帧隔离 | 802-899 |
| 13 | 响应匹配门 | 901-912 |
| 14 | 多索引维护 | 924-932 |
| 15 | 状态快照广播 | 995-1006 |

> 这 15 类共享同一组可变字段（`CommandScheduler.java:117-126`），仅靠“同一 Dispatcher 串行调用”隐式约定（`:19`、`:125`）维持一致。

### 4. 问题清单（证据锚点）

- **Q1（P0）协议引擎反向依赖具体命令**：`HsProtocolEngine.persistCompletion` 用 `instanceof EarColorCommand / LDACQueryCommand / BeginnerModeCommand` 分发，并以“任意 OBJECT+Map”兜底当首页状态（`HsProtocolEngine.java:197-269`，注册点 `:189`，import `:11-16`）。违反 design.md §2.1“中层不碰业务状态机”，开放-封闭被破坏。
- **Q2（P0）`Command` 混装 codec 与调度语义**：`Command` 同时有 `getCommandData/parseResponse/isResponseMatch`（codec）与 `traffic/getTimeoutMs/getInterFrameTimeoutMs/getResponseFamily/getResponseFamilies/getLateResponseGuardMs/onResponseTimeout`（调度），基类注释“子类只编码匹配解析”与实现相反（`Command.java:12-17 vs 100-163`）。`CommandPolicy` 只抽了一半（`CommandPolicy.java:20-40`）。
- **Q3（P0）响应族静默失效**：`getResponseFamily()` = 请求载荷前 6 字符，缺失退化类名（`Command.java:139-145`）；`isQuarantinedFrame` 按 `frame.substring(0,6)` 查表（`CommandScheduler.java:842-849`）→ 类名永不匹配 → 隔离静默失效（反例 `EarColorCommand.java:46-48`）。且 `getResponseFamily()` 调 `getCommandData()`，而部分命令编码器有副作用（`GlobalStatusCommand.java:77-91`）。
- **Q4（P0）丢帧窗口**：`onResponseCandidate` 在 `writeStarted=false` 时 `return false`，既不缓冲也不消费（`CommandScheduler.java:403-409`）→ 设备先回包后报 started 时丢帧。
- **Q5（P0）无发送看门狗**：响应超时只在 `onWriteCompleted` 后启动（`CommandScheduler.java:630-633`）；写既不回 `onCompleted` 也不回 `onTimeout` 时 `executing` 永真、队列永久堵死（`executeNext` `:490-493`）。
- **Q6（P1）策略表超大扇入 + 下标魔法**：`CommandPolicyRegistry` import 90 行、5 组 register、`semanticKey` 用 `payload.substring(4,6)/(10,12)` if 链（`CommandPolicyRegistry.java:3-307`）。
- **Q7（P1）事务死代码**：`CommandTransaction` 全仓无 `new`（command.md §2.5），调度器却保留两套准入 + 专用分支。
- **Q8（P1）`HsCapabilityRouter.deviceEvent` 同构 instanceof**：`GetDualConnectionPageCommand / GetRecordIdListCommand`（`HsCapabilityRouter.java:291-315`），与 Q1 重复。

### 5. 结论：**HAS_ISSUES**

---

## R2 biz_ggec_hs 链路 / 会话 / Gatt 收发

### 1. 设计前链路（已读到）

`HsGraph.open`（`HsGraph.java:42-61`）→ `YoBleCommandLink.open` + `HsProtocolEngine` + `HsSessionImpl`。入站：`YoBleCommandLink.NotifyWatcher.onCharacteristicChanged`（`:327-338`）→ `HsProtocolEngine.LinkListener.onInbound`（`HsHex.encode` + `engine` 线程，`:482-498`）→ `HsInboundFrameAssembler`（拼帧）→ `InboundFrameDemux`（事件/应答分流）→ `CommandScheduler.onResponseCandidate` / `DeviceEventPipeline`。

### 2. 问题清单（证据锚点）

- **L1（P0）同 MAC 重连拆除顺序竞态**：`open()` 先 `client.disconnect(旧同地址)` 再 `client.connect(新)`（`YoBleCommandLink.java:83-89` + `Retired.cancel :362-372`），lib 拆除顺序不受本层控制，可能拆掉新连接。
- **L2（P0）订阅丢失不自动恢复**：`markNotReady` 摘订阅后无重订阅（`:222-251`），唯一订阅入口依赖连接 `isReady()` 迁移（`:294-307`）；连接仍 ready 时永久卡死。
- **L3（P0）写失败被代次门吞 + 写句柄丢弃**：`onFailed` 首行 `if(!isCurrent) return`（`:174-186`）；`client.write/writeSplit` 返回句柄未保存（`:138/154`），无法 cancel。
- **L4（P0）看门狗 12s < 库连接 15s+2 重试**：`HsSessionImpl.CONNECT_READY_TIMEOUT_MILLIS=12_000`（`HsSessionImpl.java:51`）vs `BleConnectionConfig` 默认 15s+2（`BleConnectionConfig.java:120-125`），看门狗先触发截断库内重试（`HsProtocolEngine.java:428-439`）。
- **L5（P0）组合根装配空壳 intake**：`HsGraph` 注入 `new HsConnectionIntake()`（`HsGraph.java:49-50`：`acceptDeviceReports=false`、`probeEarColorOnReady=false`、`readyQueries=List.of()`，`HsConnectionIntake.java:13-29`），导致主动上报/颜色/首页补查全关；真正实现的 `HsDeviceInfoIntake`（`HsDeviceInfoIntake.java:16-38`）**全仓无引用=死代码**。
- **L6（P1）命令构造非唯一**：`HsCapabilityRouter` 为主（40+ 处），但录音子系统 8 处（`HsRecordManager.java:85/91/98/125`、`HsDeviceRecordSyncSession.java:113/137`、`HsStreamRecordSession.java:99/113`）+ 引擎 `EarColorCommand`（`HsProtocolEngine.java:468-474`）绕过 router。
- **L7（P1）会话层直穿协议 DTO**：`HsSessionImpl` import `ProtocolInfo/ProtocolPaired/Command/CommandCompletion/DeviceDomainStore`（`HsSessionImpl.java:12-25`）。
- **L8（P2）音频通道无代次**：`YoBleAudioDataLink.bindDevice(null)` 不清 consumer（`:42-50`），迟到旧帧可能投给消费者。
- **L9（P2）拼帧截断/重同步静默丢数据**：`HsInboundFrameAssembler.java:37-40`（超 8192 截断）、`:98-104`（逐 2 字符 resync）无计数/告警。
- **L10（P2）pulse 空实现**：`InboundFrameDemux.pulse` 注入空（`HsProtocolEngine.java:100`）。

### 3. 缺失能力对照（成熟 BLE 架构）

连接重试策略=部分（库默认、biz 不感知、看门狗冲突）；MTU 协商=部分（仅库默认）；写队列背压=部分（`write` 只回 boolean、丢 handle）；并发请求串行化=**已有**；断线原因分类=部分（设备主动断开 reason=null 退化为 DEVICE_ERROR）；订阅/指示区分=**缺失**；订阅丢失自动恢复=**缺失**；同 MAC 重连顺序=**缺失**。

### 4. 结论：**HAS_ISSUES**

---

## R3 lib_bluetooth BLE 层（自底向上）

### 1. 结论：**完成度高、分层干净**（多数优秀实践已具备）

- Gatt 生命周期 = 10 态受限状态机（`YoBleConnectionStateMachine.java:17-65`；枚举 `BleConnectionState.java:6-17`），`runtime.state` 单线程驱动（`YoBleGattSession.java:610-612`），`generation` 防串（`:107/317/568/811-818`），断开/失败经 HCI/GATT 归因（`YoBleErrorMapper.java:64-142`）。
- 数据收发：单包写 + `writeSplit` 分片均走每连接**唯一在途**串行队列（优先级+FIFO+僵尸槽+超时，`YoBleGattOperationQueue.java:243-400`）；MTU 建链协商（默认 517/上限 512，`YoBleGattSession.java:397-412`、`YoBleMtuPayloadCalculator.java:18-25`）；通知多播共享一次 CCCD、断链保留订阅重连恢复（`YoBleNotificationRouter.java:225-391`）。
- 并发：四上下文（state/io/scheduler/callback，`YoBleRuntime.java:23-39,75-98`）；释放逆序编排（`YoBleReleaseCoordinator.java`）。
- `internal/ble/**` 无 `TODO/FIXME/HACK/@Deprecated`（与 design D9 一致）。

### 2. 缺口（边界情形，非主链路）

- **B1（P1）会话替换不回收旧会话定时器 → 双 GATT 连接风险**：`connect` 在旧会话 retry-wait（DISCONNECTED 未 ended）时直接 `openSession` 顶替，仅 `sessions.remove`，未 `forceClose/cancelTimers`（`YoBleConnectionManager.java:111-125,458-474`；`YoBleGattSession.java:188-191` 的 `isLive` 不含 DISCONNECTED；`:484-499` retryHandle）。
- **B2（P1）单包 write/read 无 GATT_BUSY/超时自动重试**：`YoBleGattOperationHandler.java:100-121` 只 completion；`YoBleGattOperationQueue.java:365-374` 超时直接失败（长写分片有重试 `YoBleLongWriteCoordinator.java:202-212`）。
- **B3（P2）自动重连策略仍在**：`YoBleReconnectPolicy.shouldAutoReconnectOnUnexpectedDrop`（`:35-60`）+ `YoBleGattSession.onDisconnected` auto 分支（`:480-499`）+ `backgroundAutoConnect` → 平台 autoConnect。与“移除自动回连”取向冲突。
- **B4（P2）QUICK_SETUP 通知分支二次回调**：先 `finishChange(...true)` 又入队 CCCD 写，失败路径 `finishChange` 二次（`YoBleNotificationRouter.java:338-390`）。
- **B5（P2）`writeSplit` 起始一次性算 maxChunk**，传输中 MTU 变化不刷新（`YoBleLongWriteCoordinator.java:59-62`）。
- **B6（P3）NotificationRouter Record 复用 `matches` 与删除 `equals` 不一致**，可能残留空 Record（`YoBleNotificationRouter.java:473-491 vs 302-305/438`）。
- **B7（P3）`dispatchCharacteristicChanged` 向多订阅者共享同一 `byte[] copy`**（`:196,217,220`），就地改会互相污染。

### 3. 结论：**HAS_ISSUES（仅边界情形，主链路 CLEAN）**

---

## R4 自动回连（全仓）

### 1. 关键事实

1. 真正“掉线后自动回连”只在 `lib_bluetooth` BLE GATT 层（`YoBleReconnectPolicy` + `YoBleGattSession`），受 `BleConnectionConfig.autoConnect()` 控制。
2. **全仓无任何 `.autoConnect(true)`**：`HsBleSettings.CONNECTION=builder().build()`（默认 false，`HsBleSettings.java:35`），`YoBleRecordLink` 显式 `autoConnect(false)`。→ **A1 现网不触发**。
3. A2“连接尝试失败自动重试（有界退避）”生效（HS maxRetry 2 / record 3），与 autoConnect 无关。
4. **业务层（app / biz_ggec_hs / biz_ggec_wy / biz_appupdate）无任何自动回连编排**：断链即上报 DISCONNECTED，重建靠用户/上层再次 `connect()`（`DeviceSession.java:179-186`、`HsDeviceLinkAdapter.java:332-358`、`HsSessionImpl.java:411-419`）。
5. `Hs02Identity.reconnect()` 是广播解析字段，非本地回连。

### 2. 结论

**APP/Biz 侧零自动回连编排（无需清理）；唯一“自动回连”实现（lib_bluetooth A1）为未启用死能力，按“断开即断开”清理。**

---

## 汇总

| 范围 | 结论 | 必须闭合 |
|------|------|----------|
| R1 命令系统 | HAS_ISSUES | Q1~Q5 P0（协议引擎 instanceof、Command 混装、响应族静默失效、丢帧、无发送看门狗） |
| R2 链路会话 | HAS_ISSUES | L1~L5 P0（同 MAC 竞态、订阅恢复、写失败吞、看门狗、空壳 intake） |
| R3 lib_bluetooth | HAS_ISSUES（仅边界） | B1 双连接、B2 单包重试、B3 移除自动回连 |
| R4 自动回连 | 现状=零编排 | 清理 lib_bluetooth A1 |

修复方案见同目录 `design.md`；改后须复跑 `biz_ggec_hs` + `lib_bluetooth` 全测与边界门禁。
