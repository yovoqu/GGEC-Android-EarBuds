# 进度 / 复审记录（2026-11 BLE / 命令系统大整理）

> 对应 `design.md` 的“分批落地”。每批必须全绿才算收口。本文件记录已落地批次的证据。

## B0 审查 + 设计（完成）

- `docs/architecture/2026-11-ble-command-refactor/audit.md`（审查报告，四范围 + 汇总）
- `docs/architecture/2026-11-ble-command-refactor/design.md`（权威契约）
- 4 份逐行证据分报告：`.scratch/audit/{command,link,libbt,autoreconnect}.md`
- 基线：`biz_ggec_hs` 167 tests、`lib_bluetooth` 230 tests 全绿（`.scratch/baseline_test.log`）。

## B1 lib_bluetooth 移除自动回连 + 会话回收（完成）

依据 design §3.1/§3.2；审计 `autoreconnect.md` §A1、`libbt.md` §5。

改动（仅 `lib_bluetooth`）：
1. `internal/ble/connection/YoBleReconnectPolicy.java`：删除 `shouldAutoReconnectOnUnexpectedDrop`（两重载，A1）；保留 `shouldRetryOnConnectFailure`/`computeWaitMillis`（A2）。
2. `internal/ble/connection/YoBleGattSession.java`：
   - 删 `backgroundAutoConnect` 字段及 `startConnect`/`finishInit` 重置；
   - `launchConnectAttempt` 去 `platformAuto`，`connectGatt(...,false,...)`；
   - `onDisconnected` 删 `auto` 分支，`releaseGatt(reason,!(retryConnect))`，A2 `retryConnect` 分支完整保留；
   - 新增 `cancelPendingRetry()`（取消 `retryHandle`、置 retryAttempt 终态）。
3. `internal/ble/connection/YoBleConnectionManager.java`：`openSession` 替换旧会话前，若旧会话非 ended 则 `cancelPendingRetry()+forceClose()`，杜绝 retry-wait 期双 `connectGatt`（B1 修复）。

证据 / 验证：
- `:lib_bluetooth:compileDebugJavaWithJavac` → BUILD SUCCESSFUL（impl-libbt 自检）。
- `:lib_bluetooth:testDebugUnitTest` → **230 tests / 0 failures**（tests 目录零引用 reconnect/autoConnect，行为影响近零，符合审计预测）。
- `grep shouldAutoReconnectOnUnexpectedDrop / backgroundAutoConnect`：源码零残留（仅历史文档/本记录文字）。

## B2 biz 命令系统 P0 正确性（完成）

依据 design §2.2/§2.3；审计 `command.md` §2.2/§2.6（Q4/Q5）。

改动：
1. `internal/center/protocol/CommandScheduler.java`
   - **Q4 写窗口丢帧**：`onResponseCandidate` 改为“写完成前到达的匹配帧一律缓冲”（原 `writeStarted=false` 时 `return false` 丢帧）。
   - **Q5 发送看门狗**：新增 `WRITE_COMPLETION_WATCHDOG_MS=15s` 与 `sendWatchdog`；`startCommand` 下发后 arm，`onWriteCompleted`/`onCommandCompletion`/`cancelTimers` 取消；写既不回 `onCompleted` 也不回 `onTimeout` 时以 TIMEOUT 收尾，杜绝串行队列永久堵死（原响应超时只在写完成后才启动）。
2. 新增 `src/test/.../protocol/HsCommandSystemHardeningTest.java`（2 例，确定性、无 sleep）：
   - `responseBeforeWriteStartedIsBufferedNotDropped`
   - `writeThatNeverCompletesTimesOutViaWatchdog`

验证：
- `:biz_ggec_hs:testDebugUnitTest` → **169 tests / 0 failures**（167 基线 + 2 新增）。
- `:app:assembleDebug` → BUILD SUCCESSFUL（跨模块无回归）。
- `:biz_ggec_hs:checkBizGgecHsBoundary`、`:lib_bluetooth:checkLibBluetoothBoundary` → 通过。

## B3 biz 协议引擎去 `instanceof`（完成，Q1/E5 核心）

依据 design §2.4；审计 `command.md` §1.3。

改动：
1. `internal/center/protocol/Command.java`：新增嵌套枚举 `ResultKind { AGGREGATED_HOME_STATUS, EAR_COLOR, LDAC_TOGGLE, BEGINNER_MODE_TOGGLE }` 与实例方法 `resultKind()`（默认 `AGGREGATED_HOME_STATUS`）、`resultingToggleValue()`（默认 null）。
2. 三个特殊命令自声明（类级常量，等价 `instanceof` 但引擎不 import）：
   - `devicecmd/EarColorCommand.resultKind()=EAR_COLOR`；
   - `musiccmd/LDACQueryCommand.resultKind()=LDAC_TOGGLE`；
   - `functioncmd/BeginnerModeCommand.resultKind()=BEGINNER_MODE_TOGGLE` + `resultingToggleValue()=enter`。
3. `internal/center/protocol/HsProtocolEngine.java`：`persistCompletion` 改为 `switch(command.resultKind())` 穷尽四分支；`persistBeginnerMode` 改签名 `(Command, CommandCompletion)`、读 `resultingToggleValue()`；**删除** `import ...BeginnerModeCommand` 与 `...LDACQueryCommand`（引擎不再认识这两个具体命令类；`EarColorCommand` 仅因 READY 自动补查构造而保留 import，属“引擎发起一次协议查询”，非业务分发，L6 命令构造集中化在 B4 处理）。
   - 默认 `AGGREGATED_HOME_STATUS` 分支仍按 OBJECT+Map 聚合首页状态（保留原“任意聚合 Map”语义，行为零变化）。

验证：
- `:biz_ggec_hs:testDebugUnitTest` → **169 tests / 0 failures**。
- 引擎 `instanceof (EarColor|LDACQuery|BeginnerMode)Command` → **零残留**。
- 说明：中途一次 `EOFException` 经 `--stacktrace` 定位为**上次崩溃残留的损坏测试报告二进制**（`SerializableTestResultStore` Buffer underflow），非代码/测试失败；清理 `build/test-results` 后复跑 BUILD SUCCESSFUL。

## B4a 命令系统瘦身（Q8 去 instanceof + Q7 删死代码，完成）

依据 design §2.4/§1.1；审计 `command.md` §2.5（Q7）、§1.3（Q8）。

**Q8 `HsCapabilityRouter.deviceEvent` 去具体命令 `instanceof`**
- `Command` 新增嵌套枚举 `DeviceEventKind { NONE, DUAL_CONNECTION_PAGE, RECORD_FILE_LIST }` + `deviceEventKind()`（默认 NONE）。
- `GetDualConnectionPageCommand.deviceEventKind()=DUAL_CONNECTION_PAGE`；`GetRecordIdListCommand.deviceEventKind()=RECORD_FILE_LIST`。
- `HsCapabilityRouter.deviceEvent` 改为 `switch(command.deviceEventKind())`，从回包数据（`ProtocolDual` / `List<ProtocolRecording>`）构造事件；不再 `instanceof` 具体命令类。

**Q7 删除死代码 `CommandTransaction`（全仓无 `new`，分支不可达）**
- 删除 `CommandTransaction.java`。
- `CommandScheduler` 移除 12 处事务触点（`submit(CommandTransaction)`×2、`PreparedCommand`、`Node` 事务字段/构造/`forTransaction`、`collectCommands` 事务分支、`executeNext` 事务分支、`startTransactionCommand`/`finishTransaction`/`scheduleTransactionNext`/`abortTransactionRemainder`/`abortPrepared`、`onCommandCompletion` 事务分支、`abortCurrent`/`abortAll` 事务处理、`canJoinCurrentSet` 事务判断）。
- 单命令路径（准入/合并/缓存/隔离/超时/写缓冲/sendWatchdog/完成/followers/abort）语义不变。

验证：
- `grep CommandTransaction|transactionCommands|forTransaction|abortTransactionRemainder`（main+test）→ **零残留**。
- `CommandScheduler.java` **1160 → 876 行**（净减约 284 行，含事务类删除）。
- `:biz_ggec_hs:testDebugUnitTest` → **169 tests / 0 failures**（lead 独立复跑确认）。
- `:app:assembleDebug` + `checkBizGgecHsBoundary` + `checkLibBluetoothBoundary` → **BUILD SUCCESSFUL**。

## 待办（下一批，按 design §6）

- **B4b 命令系统拆包 + 协议抽离（核心剩余）**：
  - `CommandSpec` 自描述调度语义（kind/merge/cacheTtl/responseTimeout/semanticKey/responseFamilies/lateGuard），把 `CommandPolicyRegistry` 的 `payload.substring(x,y)` 下标魔法下沉为命令自身的 `semanticKey()`/`responseFamilies()`（Q3/Q6）；
  - `internal/center/protocol`（纯协议：Command/CommandCompletion/帧编解码）⟂ 新 `internal/center/command`（运行期：scheduler/policy/merge/cache/quarantine）；
  - 拆 `CommandScheduler` 剩余职责为 `RequestQueue/SetMerger/ResponseCache/LateFrameGuard` 协作对象；
  - 命令构造唯一化（录音 8 处 + 引擎 `EarColorCommand` 1 处并入唯一命令工厂，L6）；
  - 加 protocol⟂command 纯度门禁。
- **B5** lib_bluetooth 其余边界（libbt.md §5）：单包写重试（B2）、QUICK_SETUP 二次回调（B4）、writeSplit 每片 MTU（B5）、NotificationRouter key/copy（B6/B7）。
- **B6** 复审六段报告 `review.md`，全绿收口。

---

## Round3 追加：lib 边界 + 命令语义去魔法

### B5-lib（部分完成：B4/B7；writeSplit 延后）
依据 `libbt.md` §5 P2/P3。仅改 `YoBleNotificationRouter.java`：
- **B7（共享 byte[]）**：`dispatchCharacteristicChanged` 对每个 interceptor/subscriber/gattObserver 回调传 `copy.clone()`，各消费者独立，调用次数/顺序不变。
- **B4（QUICK_SETUP 双终态）**：QUICK_SETUP 分支 `localSwitch+finishChange(...true,null)` 后 `return;`，不再入队 CCCD 写；清理不可达的 `onSuccess` QUICK_SETUP 早返回。本 App `NOTIFY_MODE=DEFAULT` 不受影响。
- **B5（writeSplit 每片 MTU）延后**：子代理曾改为“动态切片”整段重写，但 `YoBleLongWriteCoordinator` **无任何单测**、且属 OTA/录音长写主链路，属“未验证高风险”改动，已回退原实现。结论：需先建立长写专用测试台再做，暂不改（稳定性优先），列为待办 B5-writeSplit。
- 验证：`:lib_bluetooth:testDebugUnitTest` **230 tests / 0 failures**（回退后复跑）。

### B4b-1 命令调度语义去“载荷下标魔法”（完成）
依据 design §7。`CommandPolicyRegistry` 的 `semanticKey(Class,payload)` 8 段 if 链与 `responseTimeoutMs/isRecordIdDelete` 下标解析**全量下沉为命令钩子**：
- `Command` 新增默认 `semanticKey(payload)`（=类名）与 `responseTimeoutMs(declared)`（=原值）。
- 12 个命令按 §7 覆写 `semanticKey`；`SyncRecordFileIdOpCommand` 覆写 `responseTimeoutMs`（delete→5000）。
- Registry `resolve` 改用命令钩子，**删除三段魔法方法**；声明式 register 表逐字未改。
- 新增 `CommandSemanticKeyTest`（9 用例）对每个覆写命令断言与 legacy 公式硬编码串一致 + SyncRecord delete/export 超时；证明**行为零变化**。
- 验证：`:biz_ggec_hs:testDebugUnitTest` **178 tests（169+9）/ 0 failures**（lead 独立复跑）；`:app:assembleDebug` + 两模块边界门禁 BUILD SUCCESSFUL。

### 修订待办（按“大刀阔斧重架构”方向）
- **B4b-2（核心剩余，须大胆结构重构）**：`CommandScheduler`(876 行) 拆为 `RequestQueue/SetMerger/ResponseCache/LateFrameGuard` 协作对象；`internal/center/protocol`（纯：Command/CommandCompletion/帧编解码）⟂ 新 `internal/center/command`（运行期），为解包需重构 `Command` 调度面可见性（抽出调度控制 API）；命令构造唯一化（L6）；加 protocol⟂command 纯度门禁。
- **B5-writeSplit**：先建长写测试台，再做每片 MTU 刷新。
- **B6** 复审六段报告 `review.md` 收口。

### 验证边界：`app` 全量构建受阻于**既有**大块 WIP（非 B1–B4b 引入）
`git status` 证据（本轮开始前即存在的未提交/未跟踪 app 改动，均非本次所改）：
- 未跟踪新增：`app/.../ui/device/home/{DeviceBatteryStrip,DeviceHeroSectionView,DeviceHomeSectionView,DeviceHomeSectionsView}.java`
- 已修改：`DeviceHomePage.java`、`DeviceHomeUiMapper.java`、`DeviceHomeUiState.java`、`DeviceHomeViewModel.java`
- 已删除：`DeviceEntryCardView.java`
- 该 WIP 尚不一致：`DeviceHomeSectionView.java:93` 调用其自身 `Listener.onOpenPage(String)`（该方法在其使用的 Listener 上不存在；`DeviceHomePage.Listener` 才有），`DeviceHomeUiMapper.java:119` 引用未创建资源 `R.string.wear_settings_title`。
- 结论：`:\app:assembleDebug` 的 fresh 编译会在这两处（及可能更多 WIP 处）失败；此前几轮 `app:assembleDebug` 显示成功是**命中缓存未真正重编 app 源**。此 app 半成品重构需其原作者按设计意图完成，**不能猜测修补**（违反“严禁猜测”）。
- 因此本轮**改以上述两模块的编译/单测/边界门禁作为我改动的验收证据**；`app` 全量构建待该 WIP 完成后再验。

**Round3 我责任模块验收**：
- `:biz_ggec_hs:testDebugUnitTest` → **178 tests / 0 failures**
- `:lib_bluetooth:testDebugUnitTest` → **230 tests / 0 failures**
- `:biz_ggec_hs:checkBizGgecHsBoundary` + `:lib_bluetooth:checkLibBluetoothBoundary` → **BUILD SUCCESSFUL**

---

## Round4 追加：B4b-2 协议层 ⟂ 命令系统「包分离」（完成，大刀阔斧）

依据 design §1.1/§1.2/§6。把“命令系统（运行期）”从协议包物理拆出到新包 `internal/center/command`，`internal/center/protocol` 只留纯协议（Command/CommandCompletion/命令子类/帧编解码/inbound）。

**移动文件**（`protocol/` → `command/`，改 package 声明）：
`CommandScheduler.java`、`CommandPolicy.java`、`CommandPolicyResolver.java`、`CommandPolicyRegistry.java`、`CommandMergeRules.java`；测试 `HsCommandSystemHardeningTest.java` 同步迁到测试包 `...center.command`。

**可见性重构（跨包必需）**：
- `CommandScheduler`/`CommandPolicy`/`CommandPolicyResolver`/`CommandPolicyRegistry` → `public`，构造器 `public`（`submit/setSession/onResponseCandidate/release` 等调度器对外入口 `public`）；
- 嵌套 `Dispatcher/Transport/Transport.Callback/Cancelable/StateListener/Snapshot` → `public`；
- `Command`（协议包）暴露跨包“调度控制 API”：`tryClaimForScheduling/abort/replayCompletion/completeResponse/completeSuperseded` 改 `public`。
- `CommandMergeRules` 仅包内用，保持包可见。

**装配更新**：`HsProtocolEngine` 补 `internal.center.command.*` import；无其它跨模块影响（`biz_ggec_hs` 对 app 的 `api` 公共面未变）。

**验证（lead 独立复跑）**：
- 结构：`protocol/` 已无 `CommandScheduler/CommandPolicy*/CommandMergeRules`（零残留）；`command/` 含 5 主源 + 1 测试。
- `:biz_ggec_hs:testDebugUnitTest` → **178 tests / 0 failures**；`:biz_ggec_hs:checkBizGgecHsBoundary` → BUILD SUCCESSFUL。
- 旧包路径引用 `internal.center.protocol.{CommandScheduler|CommandPolicy|CommandMergeRules}` → **零残留**。

**架构结果**：`protocol`（纯协议）与 `command`（运行期调度）成两个物理包；`HsProtocolEngine` 只依赖 `command` 的公开门面 + `protocol` 的 `Command/CommandCompletion`。后续 B4b-3 可继续把 `CommandScheduler` 内部拆为 `RequestQueue/SetMerger/ResponseCache/LateFrameGuard` 协作对象。

## 修订待办（继续大改）
- **B4b-3**：拆 `CommandScheduler`(仍在 command 包) 内部为 `RequestQueue/SetMerger/ResponseCache/LateFrameGuard` 协作对象；命令构造唯一化（L6）；加 protocol⟂command 纯度门禁（structure 规则）。
- **B5-writeSplit**：先建长写测试台，再做每片 MTU 刷新。
- **B6** 复审 `review.md` 收口（注：`:app:assembleDebug` 受阻于既有 app WIP，见上）。

---

## Round5 追加：B4b-3 神类拆分 + 协议纯度门禁（完成）

依据 design §1.2。从 `CommandScheduler` 抽出两个内聚协作对象到 `internal/center/command/`：
- `LateFrameGuard.java`（139 行）：迟到帧隔离（原 `responseQuarantines` 全逻辑：`register/delayFor/isQuarantined/clear` + `prune/responseFamily/responseFamilies`，`Expiry` 替代 `ResponseQuarantine`）。
- `ResponseCache.java`（46 行）：GET 缓存（`get/put/clear`，`CacheEntry` 迁入）。
- `CommandScheduler.java` 973 → **848 行**，全部改走 `lateFrameGuard.*` / `responseCache.*`；**SetMerger/RequestQueue/pending/合并逻辑原样未动**（与核心状态机深耦合，留状态机重写专项）。

**协议纯度门禁（结构规则）**：`build.gradle` 新增 `checkBizProtocolPurity`——扫描 `internal/center/protocol/**`，命中 `import ...internal.center.command` 即失败（注释豁免）；挂 `preBuild` + `check`。未削弱既有门禁。

**分层修正（关键）**：为使“协议层不依赖命令运行期”成立，`HsProtocolEngine` 由 `protocol/` 上移到 **`internal/center/`（运行时编排层，与 `HsGraph` 同层）**——引擎本就是“命令运行期 + 协议 + 入站 + 连接生命周期”的编排点，不属于纯协议层。迁移仅改包声明/可见性/import，零逻辑变更；引擎测试仅改 import（未删除/未 `@Ignore`）。

**验证（lead 独立复跑）**：
- `:biz_ggec_hs:testDebugUnitTest` → **178 tests / 0 failures / 0 skipped**（26 suites，0 `@Ignore`）。
- `:biz_ggec_hs:checkBizGgecHsBoundary` + `:biz_ggec_hs:checkBizProtocolPurity` → **BUILD SUCCESSFUL**。
- 结构：`center/` = `HsGraph`+`HsProtocolEngine`；`protocol/` 无引擎；`command/` = 5 调度 + `LateFrameGuard`+`ResponseCache`；旧包引用 0。

**分层结果**：`protocol`（纯，不可依赖 command）⟂ `command`（运行期，依赖 protocol）⟂ `center`（编排：`HsProtocolEngine`/`HsGraph`，依赖两者）。由 `checkBizProtocolPurity` 结构锁死。

## 修订待办
- **B4b-3b（可选深化）**：将 `CommandScheduler` 的 `RequestQueue/SetMerger` 拆为协作对象（需重写核心状态机，风险高，专项评估后再做）。
- **L6**：命令构造唯一化（录音 8 处 + 引擎 `EarColorCommand` 1 处并入唯一命令工厂）。
- **lib BLE P1**：单包 write/read 重试（B2）、NotificationRouter Record key（B6）、writeSplit 每片 MTU（B5，先建长写测试台）。
- **B6** 复审 `review.md` 收口（`:app:assembleDebug` 仍受阻于既有 app WIP）。

---

## Round6 追加：L6 命令构造唯一化（完成）

新增 `internal/center/command/HsCommandFactory.java`（`public final` + 私有构造，仅 `return new`），为录音 8 类命令 + 耳机颜色查询提供 9 个静态工厂方法。把**绕过能力路由的 9 处外部构造**收敛到工厂：
- `HsRecordManager`（4：start/stop local record、getRecordIdList、deleteRecordFile）
- `HsDeviceRecordSyncSession`（2：startTransmitRecord、cancelTransmitRecord）
- `HsStreamRecordSession`（2：start/stop stream record）
- `HsProtocolEngine.queryEarColorOnce`（1：earColorQuery）
`HsCapabilityRouter` 保持不变（能力→命令映射点）。对象/参数逐一等价，行为零变化。

验证：`internal/record/**` + `HsProtocolEngine` 中 `new (StartLocalRecord|…|EarColor)Command` → **零残留**；`:biz_ggec_hs:testDebugUnitTest` **178 tests / 0 failures**；`checkBizGgecHsBoundary` + `checkBizProtocolPurity` BUILD SUCCESSFUL。

**结果**：命令构造收敛为「能力路由（能力→命令）+ 命令工厂（脚本化/内部命令）」两个明确来源，消除同语义命令在多处各自 `new` 的重复。

## 修订待办
- **B6 复审报告 `review.md`**（六段，三模块）+ 纳入 lib 边界决策（单包写重试=由命令系统负责、lib 不重试；writeSplit 每片 MTU=需长写测试台，已知限制；NotificationRouter key=潜在 P3 无复现，记录不改）。
- （可选）**B4b-3b**：`CommandScheduler` 的 `RequestQueue/SetMerger` 深化拆分（需核心状态机重写，专项）。
- `:app:assembleDebug` 仍受既有 app WIP 阻塞（等授权或原作者完成）。

---

## Round8 追加：lib `writeSplit` 每片按当前 MTU（完成，task3 数据收发收口）

依据审计 `libbt.md` §5 P2（B5）、design §3.5。
- `YoBleMtuPayloadCalculator` 新增纯函数 `chunkEnd(payloadLength, offset, maxChunk)`（非法入参退化不抛；`effectiveMaxChunkSize` 未动）。
- `YoBleLongWriteCoordinator.writeSplit` 拆两路径：`customSplitter!=null || value.length==0` 走原**预切**路径（逐字保留）；默认分片器且非空走**动态逐片**——不预切，每片发送前按 `effectiveMaxChunkSize(currentMtu, overriddenMtu, writeType)` 重算 `maxChunk`，`chunkEnd` 定界、`Arrays.copyOfRange` 取片、`sentSoFar` 推进；`maxChunk<=0` 以 `INVALID_OFFSET_OR_LENGTH` 一次性失败。串行在途、逐片确认、`GATT_BUSY/retryable` 同片重试≤2、`terminalEmitted` 去重、各回调与 `currentHandle` 取消均保留；进度 `totalChunks` 用 `ceil(total/maxChunk)` 近似。
- 新增纯单测 `YoBleMtuPayloadCalculatorTest`（13 用例：chunkEnd 中间/末片/整除/越界/非法 + effectiveMaxChunkSize 多场景）。

验证：`:lib_bluetooth:testDebugUnitTest` → **243 tests（230+13）/ 0 failures**；`:lib_bluetooth:checkLibBluetoothBoundary` → BUILD SUCCESSFUL。

**task3 数据收发结论**：单包写重试=命令系统（biz）职责（lib 不重试，设计决策）；writeSplit 每片 MTU=已修复+纯测；通知边界 B4/B7=已修。lib BLE 层收口。

## 最终待办
- 整体收口：`:app:assembleDebug` 受**既有** app WIP 阻塞（非本轮改动，待授权/原作者）；其余 biz/lib 全部验证绿。
- （可选）`CommandScheduler` 内部 `RequestQueue/SetMerger` 深化（专项）。

---

## 最终收口（Final · round 9）

**最终合并验证（一次 fresh，`--no-daemon`）**：
- `:biz_ggec_hs:testDebugUnitTest` → **178 tests / 0 failures / 0 skipped**
- `:lib_bluetooth:testDebugUnitTest` → **243 tests / 0 failures / 0 skipped**
- `checkBizGgecHsBoundary` + `checkBizProtocolPurity` + `checkLibBluetoothBoundary` → **BUILD SUCCESSFUL**

**架构不变量（grep 实证）**：`shouldAutoReconnectOnUnexpectedDrop|backgroundAutoConnect`(lib)=**0**；`CommandTransaction`(biz)=**0**；引擎 `instanceof(EarColor/LDACQuery/BeginnerMode)Command`=**0**；`protocol` import `...center.command`=**0**；`HsCommandFactory`=存在。

**三任务达成对照**：
| 任务 | 状态 | 关键证据 |
|------|------|----------|
| (1) APP/Biz 清理自动回连（断开即断开） | **达成** | lib A1 删除（零残留）；app/biz 本无自动回连编排（audit §R4）；保留 A2 建连尝试重试 |
| (2) biz 命令系统内聚解耦 + 协议层抽离，严禁兼容旧代码 | **达成** | `protocol`⟂`command`⟂`center` 三层物理分离 + `checkBizProtocolPurity`；引擎/router 去 `instanceof`；registry 下标魔法下沉；死代码删除；`HsCommandFactory` 唯一构造；178 全绿 |
| (3) lib BLE 逐层审查 + 重构补齐 | **达成** | 逐层审计（audit §R3）；去回连+会话替换双连接修复+通知边界 B4/B7+writeSplit 每片 MTU（纯测）；单包重试定为命令系统职责（设计决策）；243 全绿 |

**外部条件（非本任务范围、非本轮改动）**：`:app:assembleDebug` fresh 编译受阻于 `app/ui/device/home` 的**既有半成品重构**（`??` 新文件 + `M` 修改 + 缺 `Listener.onOpenPage`/`R.string.wear_settings_title`），需原作者按设计意图完成或经用户授权后补完。

**交付文档**：`audit.md`（审查）· `design.md`（设计契约）· `progress.md`（逐批落地）· `review.md`（重构后六段复审）。

---

## 清理（Cleanup）

- **未用 import 清零**：对 `biz_ggec_hs` + `lib_bluetooth` 主源码全量扫描并清理 13 处未用 import（`Hs02BroadcastAssembler`/`A2dpCodeTypeCommand`/`DeviceDomainStore`/`BleScanCapabilities`/`YoBleAndroidConnectionPlatform`/`YoCbtAndroidDiscoveryPlatform`/`YoBtRadioPlatform`/`CbtLinkSessionHandle`/`CbtLinkSessionObserver`）；复扫 **0**。每文件净删 1–3 行，中文注释完好（用 UTF-8 安全方式，避免了 shell 重写造成的编码损坏——一次错误改写已 `git checkout` 回滚）。
- **死私有成员扫描**：本次改动的关键类（`CommandScheduler/LateFrameGuard/ResponseCache/CommandPolicyRegistry/HsCommandFactory/HsProtocolEngine/HsCapabilityRouter/YoBleLongWriteCoordinator/YoBleNotificationRouter`）→ 无“仅声明一次”的死私有方法/字段。
- **临时构建日志清理**：`.scratch/*.log` 57 个已删除。
- 复验：`:biz_ggec_hs:testDebugUnitTest` 178 / `:lib_bluetooth:testDebugUnitTest` 243，0 失败；三处边界门禁 BUILD SUCCESSFUL。

---

## 全仓一次性收尾（Final closure）

- **全仓可构建**：`:app:assembleDebug` **BUILD SUCCESSFUL（APK 产出）**。早前 `:app:compileDebugJavaWithJavac` 的 `onOpenPage`/`wear_settings_title` “找不到符号”经全量重编（`--rerun-tasks`）确认为**增量编译陈旧产物**（符号/资源实际已存在），非源码缺陷。
- **全量单测通过**：`:app:testDebugUnitTest` **618** + `:biz_ggec_hs:testDebugUnitTest` **178** + `:lib_bluetooth:testDebugUnitTest` **243**，合计 **1039 tests / 0 failures / 0 skipped**。
- **边界门禁**：`checkBizGgecHsBoundary` + `checkBizProtocolPurity` + `checkLibBluetoothBoundary` → BUILD SUCCESSFUL。
- **清理**：biz/lib 未用 import 0；app 设备域（`DeviceHomeUiMapper`/`DeviceSettingsViewModelTest`）未用 import 已清；临时构建日志清理。
- 说明：app 全仓仍有部分**既有**未用 import（`ui/ai` 等无关模块，历史技术债），未在本轮重构范围、为避免无关大改保留。

**最终验收命令**：`.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :biz_ggec_hs:testDebugUnitTest :lib_bluetooth:testDebugUnitTest :biz_ggec_hs:checkBizGgecHsBoundary :biz_ggec_hs:checkBizProtocolPurity :lib_bluetooth:checkLibBluetoothBoundary --offline --no-daemon` → **BUILD SUCCESSFUL**。

---

## 全仓技术债清理（分模块，智能体团队）

按模块分派 4 个并行子代理（仅编辑、集中验证），清理**未用 Java import** 与**保守判定的未用私有方法**（`除声明外 0 次出现 + 无注解 + 非重写`）：
| 模块组 | 删除未用 import | 删除未用私有方法 |
|--------|----------------|------------------|
| app | 34 | 1 |
| lib_common + yotheme | 38 | 3 |
| biz_ggec_wy + biz_appupdate + biz_ggec_hs + lib_ggec_audio | 16 | 0 |
| lib_ai + lib_bluetooth | 10 | 1 |
| **合计** | **98** | **5** |

- 全仓 `TODO/FIXME/HACK = 0`、`@Deprecated = 0`（扫描确认，无遗留标记债务）。
- 保留 1 项不确定（`AlignedSettingsView.actionOf`，带 `@Nullable` 注解 → 按规则保留）。
- 编辑全程用 UTF-8 安全编辑（无 shell 重写，中文完好）。

**集中验证（全模块 fresh）**：`app` 619、`biz_ggec_hs` 178、`lib_bluetooth` 243、`lib_common` 182、`lib_ai` 411、`biz_appupdate` 59、`biz_ggec_wy` 34、`lib_ggec_audio` 34、`yotheme` 1 —— **全部 0 失败**；`:app:assembleDebug` + 三门禁 BUILD SUCCESSFUL。

---

## 状态机重写（B4b-3b：拆 RequestQueue / SetMerger）

把命令系统核心调度器 `CommandScheduler` 的“队列/合并”职责拆为协作对象（同包、包可见）：
- `CommandNode.java`（59 行）：原内部 `Node` 顶层化，供调度器与协作对象共用。
- `RequestQueue.java`（73 行）：独占 `pending`/`pendingSetSlots`/`queryGroups` 三索引与索引维护（offerLast/pollFirst/peekLast/offerSetSlot/getSetSlot/removeSetSlot/putQueryGroup/getQueryGroup/removeQueryGroup/removeIndexes/clear/Iterable）。
- `SetMerger.java`（60 行）：SET 合并**纯决策**（`JOIN_CURRENT / NEW_NODE / REUSE_SLOT_SAME_VALUE / SUPERSEDE`），判定次序/短路对应原 `canJoinCurrentSet`+`canReusePendingSet`+`sameValue`；落执行仍在 `admitSet`。
- `CommandScheduler`：**848 → 692 行**（-156），经 `requestQueue.*`/`setMerger.decide` 驱动，无残留 `pending` 队列字段。

行为等价（单命令准入 / GET 单飞缓存 / SET 合并 / setSession 中止 / executeNext / 写缓冲 H-14 / sendWatchdog / 超时 / 迟到帧隔离 / followers / abort 全链路保持）；`biz_ggec_hs` **178 tests 全绿**，两门禁通过。

**最终全仓验证（9 模块，`--max-workers=1`）**：合计 **1761 tests / 0 failures** —— `app` 619、`biz_ggec_hs` 178、`lib_bluetooth` 243、`lib_ai` 411、`lib_common` 182、`biz_appupdate` 59、`biz_ggec_wy` 34、`lib_ggec_audio` 34、`yotheme` 1；`:app:assembleDebug` + 三门禁 BUILD SUCCESSFUL。
注：`lib_ai` 的 `RealtimeWebSocketLinkTest.gaHoldsBufferedAudioUntilSessionUpdatedArrives` 为 **MockWebServer 网络时序**测试，在极端并发（`--max-workers=2`）下偶发 flaky，隔离/单并发/重跑均稳定通过——属既有测试时序敏感，非本轮改动。

## 说明

- 工作树中 `biz_ggec_hs` 的 `HsGraph.java`/`HsCatalogImpl.java`/`HsRecordPathResolver.java`/`HsAudioPacketClassifierTest.java` 等为**本轮开始前既有的未提交改动**（`git status` 起始即存在），非本次 B1/B2 所改，故不在本记录责任范围。
