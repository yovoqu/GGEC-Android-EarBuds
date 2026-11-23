# 架构设计（2026-11 BLE / 命令系统大整理）

> 依据：同目录 `audit.md`（本报告）+ 4 份分报告证据。
> 本文件是本轮重构的**唯一权威契约**；与本文冲突的实现视为未完成。
> 目标口径：**严禁兼容旧代码、严禁双轨**（沿用 `2026-11-refactor/design.md` D9）。

## 0. 决策（用户确认 + 审查结论）

| 编号 | 决策 | 依据 |
|------|------|------|
| E1 | **移除自动回连**。断开即断开，不自动重连；重建只由上层显式 `connect()` 发起。 | 用户指令“清理自动回连，暂时不开发，断开就是断开”。实证：唯一自动回连在 `lib_bluetooth` A1（`YoBleReconnectPolicy.shouldAutoReconnectOnUnexpectedDrop`），且现网 `autoConnect=false` 不触发（autoreconnect.md §E）。 |
| E2 | **保留连接尝试失败的有界重试（A2）**。它不是“回连”，是单次 `connect()` 内的建连退避（HS maxRetry 2）。移除会降低首连成功率，属功能回退。 | autoreconnect.md §A2；用户“断开即断开”针对链路已建立后的自动回连。 |
| E3 | **命令系统全量内聚为独立子系统**；协议层（帧编解码/命令定义）从中抽离。命令系统只依赖 `Command`/`CommandSpec`/`Transport` 抽象。 | 用户“将 Gatt 收发命令系统解耦、全量内聚、协议层抽离”；audit R1。 |
| E4 | **命令自描述调度语义**：kind/merge/semanticKey/cacheTtl/responseTimeout/responseFamilies/lateGuard 由命令自身（`CommandSpec`）声明，废除 372 行外部注册表的“载荷下标魔法”。 | Q3/Q6。 |
| E5 | **协议引擎不再认识具体命令类**：删除 `HsProtocolEngine.persistCompletion` 的 `instanceof`；结果落地改由命令声明的类型化结果（`CommandCompletion` 携带 `DomainPatch`/`ResultKind`）驱动。 | Q1。 |
| E6 | **先修正确性、再拆结构**：P0 缺陷（丢帧/挂死/响应族静默失效/同 MAC 竞态/订阅恢复）先落地并加测试，再做包级拆分；每一步保持 397 tests 全绿。 | 用户“严格保证稳定性与功能完整性”。 |

## 1. 目标结构

### 1.1 biz_ggec_hs：协议层 ⟂ 命令系统 ⟂ 会话层

```
internal/center/
  protocol/                        # 协议层（纯）：只编码/解码/匹配 + 自描述规格，无队列/计时/缓存
    Command.java                   # 纯命令：encode()/matches(frame)/decode(frame)->Outcome + spec()
    CommandSpec.java               # 自描述调度规格（kind/merge/semanticKey/cacheTtl/responseTimeout/responseFamilies/lateGuard）
    CommandCompletion.java         # 完成结果（携带 ResultKind/DomainPatch，供命令系统无 instanceof 落域）
    ProtocolFrame.java             # 帧信封值对象：prefix/length/payload/尾标；统一组合电量特例
    protocol/{musiccmd,functioncmd,...}  # 各命令子类，实现 codec + spec()
    inbound/                       # 拼帧/解帧/领域存储/事件（协议侧）
  command/                         # 命令系统（运行期，新增包）：只跑一发一收
    CommandSystem.java             # 对外门面：submit/onFrame/setSession/release；只依赖抽象
    RequestQueue.java              # 队列 + 唯一在途
    SetMerger.java                 # LATEST_WINS/SINGLE_FLIGHT 合并（读 CommandSpec）
    ResponseCache.java             # GET 缓存 + 单飞分组
    LateFrameGuard.java            # 迟到帧隔离（族由 CommandSpec 显式声明）
    CommandTransport.java          # 传输契约（send(bytes, callback)）
  exchange/                        # 帧流量角色（REQUEST_REPLY / DEVICE_REPORT）
  port/HsCommandLink.java          # 链路契约（不变）
internal/link/                     # 会话层：能力→命令路由、快照、事件；不 import command 具体类型
internal/interact/HsCapabilityRouter.java   # 能力↔命令唯一映射点（成为唯一命令构造点）
```

### 1.2 命令系统内部职责边界（替代 1130 行单类）

| 协作对象 | 职责 | 不做什么 |
|---------|------|----------|
| `CommandSystem` | 编排：会话生命周期、推进、完成收尾、状态广播 | 不做合并/缓存/隔离的具体判定 |
| `RequestQueue` | 待发队列、唯一在途、僵尸与代次 | 不做匹配/编码 |
| `SetMerger` | LATEST_WINS 合并与同值 join（按 `CommandSpec.semanticKey/valueFingerprint`） | 不碰传输 |
| `ResponseCache` | GET 缓存 TTL + 单飞跟随者分组 | 不碰超时 |
| `LateFrameGuard` | 迟到帧族登记/查询/过期（族必须显式声明，缺失快速失败） | 不猜族 |

### 1.3 协议层纯度规则（门禁强制）

- `protocol/**` 不得 import `command.**`、`provider.**`、`lib_bluetooth.**`、`android.bluetooth.**`。
- `command/**` 只允许 import `protocol` 的 `Command/CommandSpec/CommandCompletion/ProtocolFrame` 与 `exchange`、`java.*`。
- `Command` 子类不得出现计时/缓存字段（超时/族/合并全部经 `CommandSpec`）。

## 2. 关键机制设计

### 2.1 响应族（Q3 修复）
- `CommandSpec.responseFamilies` 必填（构造期校验：每个族必须是 6 位十六进制前缀；缺失即抛）。
- 删除 `Command.getResponseFamily()` 的“重跑编码器/退化类名”路径；族来源只来自命令显式声明（多数命令可在构造器用 `setCommandPrefix` 后由默认 `spec()` 取该前缀为单族）。
- 迟到帧隔离族查表命中即隔离；不再静默失效。

### 2.2 写窗口丢帧（Q4 修复）
- `onResponseCandidate` 在“命令已 send、尚未 onStarted/onCompleted”窗口内，收到的匹配帧**一律进 `bufferedFrames`**（不再 `return false`），待 `writeCompleted` 后按序回放；写完成前超时也回放后判定。

### 2.3 发送看门狗（Q5 修复）
- 命令从 `SENT`（已发起 `transport.send`，未拿到完成）起即启动“发送看门狗”（`responseTimeoutMs` 上限，默认取 `spec.responseTimeoutMs` 或 `Command.getTimeoutMs()`）；超时则 `failCurrent(TIMEOUT)`，保证链路不回调也不永久堵队列。

### 2.4 协议引擎去 instanceof（Q1/E5 修复）
- `commandCompleted(command, completion)` 只读 `completion.kind()` 与 `completion.domainPatch()`；命令在 `decode`/`completed(...)` 时携带 `ResultKind`（如 `HOME_STATUS/BATTERY/EAR_COLOR/LDAC/…`）与已解析的值。
- `HsProtocolEngine` 删除 `import *Command`（`EarColorCommand/LDACQueryCommand/BeginnerModeCommand`）与 `persistCompletion` 的 `instanceof`。

### 2.5 命令构造唯一化（L6）
- 录音子系统的 `new *RecordCommand` 迁到 `HsCapabilityRouter`（新增 `recordCommands(...)`），`internal/record` 只 `engine.submit(router.xxx)`。
- 引擎的 `EarColorCommand` 构造迁到 router（`router.earColorQuery()`）。

## 3. lib_bluetooth 变更（BLE 层）

### 3.1 移除自动回连（E1/B3）
- 删除 `YoBleReconnectPolicy.shouldAutoReconnectOnUnexpectedDrop`（两重载）。
- `YoBleGattSession`：删 `backgroundAutoConnect` 字段、`onDisconnected` 的 auto 分支、`launchConnectAttempt` 的 `platformAuto`、`startConnect/finishInit` 的重置；**保留 A2 的 `retryConnect` 分支**（共享 `releaseGatt`，注意 `:483` 条件改为仅 `retryConnect`）。
- `connectGatt(deviceId, autoConnect, listener)` 的 `autoConnect` 参数链（`YoBleConnectionPlatform/YoBleAndroidConnectionPlatform/YoBleAndroidGattCalls.connectLe`）删除参数，恒 `false`，避免死参数。
- `BleConnectionConfig.autoConnect()` 字段与 builder 方法删除；`YoBleRecordLink` 的 `.autoConnect(false)` 调用移除；`HsBleSettings` 不受影响（用默认 build）。
- `YoBleClient.reconnect()` 保留为**显式手动** API（非自动），不改语义（无自动调用者）。

### 3.2 会话替换回收（B1）
- `YoBleConnectionManager.connect`：旧会话若非 ended（含 retry-wait）在 `openSession` 前 `previous.cancelPendingRetry()+forceClose()`，由 `whenEnded` 串行化替换，杜绝双 `connectGatt`。
- 新增 `YoBleGattSession.cancelPendingRetry()`：取消 `retryHandle`、置 `retryAttempt` 终态。

### 3.3 单包写重试（B2）
- `YoBleGattOperationHandler`：单包 write/read 遇 `retryable && attempt < maxAttempts(默认1次)` 且连接 ready，重新 enqueue（沿用 opId 语义）；超时同样受最大次数约束。可配置，默认仅 1 次以控风险。

### 3.4 QUICK_SETUP 二次回调（B4）
- `YoBleNotificationRouter` QUICK_SETUP 分支：`finishChange` 延后到 CCCD 写成功，或该分支直接 `return`（不写 CCCD）。避免 ENABLED→DISABLED 翻转。

### 3.5 其它边界（B6/B7/B5）
- NotificationRouter 删除按实际存储 key；`dispatchCharacteristicChanged` 每个订阅者 `copy.clone()`；`writeSplit` 每片按当前 MTU 重算。

## 4. 门禁（gradle 结构性检查）

- `biz_ggec_hs`：`checkBizGgecHsBoundary` 增规则：
  - `internal/center/protocol/**` 禁止 import `internal.center.command.**` / `provider.**` / `android.bluetooth.**`；
  - `internal/center/command/**` 只允许 import `internal.center.protocol + exchange + java.*`；
  - `Command` 子类文件禁止出现 `getTimeoutMs|getResponseFamily|getLateResponseGuardMs|cache`（迁移后）。
- `lib_bluetooth`：`checkLibBluetoothBoundary` 增规则：`internal/ble/**` 禁止出现 `shouldAutoReconnectOnUnexpectedDrop|backgroundAutoConnect|autoConnect`（B3 闭合）。

## 5. 验收（缺一不算完成）

1. `.\gradlew.bat :biz_ggec_hs:testDebugUnitTest :lib_bluetooth:testDebugUnitTest :app:assembleDebug --offline` 通过。
2. `biz_ggec_hs` ≥167 tests、`lib_bluetooth` ≥230 tests 全绿；新增缺陷回归测试；不得 `@Ignore`/删测。
3. 全仓 `grep`：`shouldAutoReconnectOnUnexpectedDrop`、`backgroundAutoConnect` 零残留（可执行代码）。
4. `biz` 中 `CommandScheduler.persistCompletion instanceof`、`CommandPolicyRegistry` 下标魔法零残留（按分批闭合）。
5. `audit.md` 每条 P0 有对应实现点与测试。

## 6. 分批落地（每批独立可验证，保持全绿）

| 批次 | 内容 | 验收 |
|------|------|------|
| B0 | 本报告 + audit.md | 文档就位 |
| B1 | lib_bluetooth 移除自动回连（3.1）+ 会话回收（3.2） | 230 tests 全绿 + 门禁 |
| B2 | biz P0 正确性：响应族（2.1）、写窗口丢帧（2.2）、发送看门狗（2.3） | 167 tests 全绿 + 新回归测试 |
| B3 | biz 协议引擎去 instanceof（2.4）+ 命令构造唯一化（2.5） | tests 全绿 + 门禁 |
| B4 | 命令系统拆包（1.1/1.2）+ 协议纯度门禁 | tests 全绿 + 门禁 |
| B5 | lib_bluetooth 其余边界（3.3/3.4/3.5） | 230 tests 全绿 |
| B6 | 复审六段报告，全绿 | `review.md` |

## 7. B4b 执行细则（semanticKey / responseTimeout 去魔法映射，实证于 CommandPolicyRegistry.java:246-307）

目标：把 `CommandPolicyRegistry` 的“载荷下标魔法”（`semanticKey(Class,payload)` 的 8 段 if 链 + `responseTimeoutMs` 的 `isRecordIdDelete` 解析）下沉到命令自身，Registry 只保留声明式表（kind/merge/cache）。

新增 `Command` 钩子（默认=原默认行为）：
```java
public String semanticKey(String payload) { return getClass().getName(); }        // 默认
public long responseTimeoutMs(long declaredResponseTimeoutMs) { return declaredResponseTimeoutMs; }
```
Registry.resolve 改为 `command.semanticKey(payload)` 与 `command.responseTimeoutMs(definition.responseTimeoutMs)`；删除 `semanticKey`/`responseTimeoutMs`/`isRecordIdDelete` 三方法与相关未用 import/常量。

按命令覆写（复刻原字符串，`payload == command.getCommandData()`）：
| 命令类 | 覆写 semanticKey(payload) | 依据(原行号) |
|--------|---------------------------|--------------|
| BeginnerModeCommand | `getClass().getName()+":"+payload.substring(4,6)` | 271-274 |
| SimultaneousInterpretationCommand | `getClass().getName()+":"+payload.substring(10,12)` | 275-278 |
| WearDetectionPromptToneCommand | `getClass().getName()+(("02".equals(payload.substring(10,12)))?":query":":set")` | 280-284 |
| GetStorageCommand | `getClass().getName()+":"+payload.substring(10,12)` | 285-288 |
| GetRecordIdListCommand | `getClass().getName()+":"+payload` | 289-291 |
| ControlCommand / ControlCommandV11 | `getClass().getName()+":"+payload.substring(10,12)` | 292-295 |
| LongPressSet / ShortPressSet / DoublePressSet Command | `getClass().getName()+":"+payload.substring(10,12)` | 296-301 |
| CustomEQRealtimeCommand / CustomEQCancelRealtimeCommand | `"custom-eq-realtime"` | 302-305 |
| SyncRecordFileIdOpCommand | 覆写 `responseTimeoutMs(declared)`：flags=payload.substring(10,12) 为 DELETE_LEFT/RIGHT → 5000L，否则 declared | 246-267 |

等价性证据要求：新增 `CommandSemanticKeyTest`，对每个覆写命令构造代表性实例，断言 `semanticKey(getCommandData())` 等于原公式的硬编码期望串；`SyncRecordFileIdOpCommand` 断言 delete/export 两种 payload 的 `responseTimeoutMs()` 分别 5000/declared。测试通过=行为零变化。

