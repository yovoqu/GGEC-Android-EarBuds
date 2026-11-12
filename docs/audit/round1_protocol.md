# round1 协议层审计 — `biz_ggec_hs`（HS01/HS02 设备协议层）

> 审计范围：`biz_ggec_hs` 模块「协议层 → 蓝牙层」接入链路 + 协议内部引擎/数据结构。
> 方式：只读静态审查（read/grep/glob），未修改任何源码，未执行 Gradle/真机。
> 定位：设备模块「蓝牙层 → 协议层 → 应用层」全量审查的**协议层**部分。

---

## 1. 概述

`biz_ggec_hs` 是 HS01/HS02 耳机的 BLE 协议层。对外只有两个根入口：

| 入口 | 职责 | 位置 |
|---|---|---|
| `YoHsSdk.create(Context)` → `HsGraph.open` → `YoHsClientImpl` | 命令会话（连接 + 命令收发 + 主动上报） | `YoHsSdk.java:19-21` / `HsGraph.java:17-23` |
| `YoHsBroadcast.classify/openSession` | 广播识别（只判是不是 HS 设备与代际，不连接） | `YoHsBroadcast.java:18-26` |

整个命令链路被严格收口在一条抽象上：`HsCommandLink`（`internal/contract/HsCommandLink.java`），
其唯一生产实现为 `provider/lib_bluetooth/YoBleCommandLink`，后者才接触 `lib_bluetooth` 的 `YoBleClient`。
协议层内部由三块组成：`CommandScheduler`（调度/合并/缓存/超时/迟到帧隔离）、`InboundFrameDemux`（入站分帧扇出）、
`DeviceEventPipeline/DeviceDomainStore`（主动事件 → 域状态）。

结论速览：**命令数据链路（App→命令→BLE 写→BLE 通知→命令完成）在协议层是闭合的**；
但**连接生命周期对 App 完全不可观测**（无回调，只 `isReady()` 轮询），且存在若干死代码、冗余值对象与入站/出站分片风险。

---

## 2. 协议层结构与端到端数据链路

### 2.1 结构分层

```
api/        YoHsClient(接口) ── 只有业务动作，不出现 GATT/UUID/lib_bluetooth
internal/session/  YoHsClientImpl  命令组装 + 回调切主线程(mainHandler)
internal/graph/    HsGraph         唯一装配：HsProtocolEngine(new YoBleCommandLink)
internal/protocol/ HsProtocolEngine + CommandScheduler + InboundFrameDemux + 各 *Command
                   inbound/(DeviceEventPipeline, DeviceDomainStore, DeviceEventRouter, EventCatalog, InboundFrameDemux)
                   Command/CommandCompletion/CommandTransaction/CommandPolicy/CommandPolicyRegistry/...
internal/contract/ HsCommandLink   open/close/release/write/setListener —— 唯一链路抽象
provider/lib_bluetooth/ YoBleCommandLink  唯一命令链路实现；HsScanSnapshots 广播快照适配
model/      HsBattery/HsCommandResult/HsDeviceObserver/... 纯值/回调
```

### 2.2 端到端链路（命令下行 / 应答上行）

下行（写）：
```
YoHsClient.xxx(...)                          api/YoHsClient.java:34-52
  → YoHsClientImpl.engine.submit(cmd, onMain) internal/session/YoHsClientImpl.java:83-131
  → HsProtocolEngine.submit(cmd, cb)          internal/protocol/HsProtocolEngine.java:81-95
  → executor("yo-hs-protocol").execute        (ProtocolThreadFactory) HsProtocolEngine.java:232-238
  → CommandScheduler.submit → encode → 入队     CommandScheduler.java:138-190
  → executeNext → startCommand → transport.send CommandScheduler.java:493-612
  → LinkTransport.send → link.write(bytes)     HsProtocolEngine.java:177-206
  → YoBleCommandLink.write(client.write)       provider/lib_bluetooth/YoBleCommandLink.java:124-163
  → YoBleClient.write(WRITE_UUID, WITHOUT_RESPONSE)  YoBleCommandLink.java:136-157
  → GATT WRITE_UUID 65786365-...-2e636f820104
```

上行（通知）：
```
GATT NOTIFY_UUID 65786365-...-2e636f820105
  → YoBleClient.subscribe(NOTIFY_TARGET, DEFAULT, NotifyWatcher)  YoBleCommandLink.java:165-183
  → NotifyWatcher.onCharacteristicChanged(value)                  YoBleCommandLink.java:286-296
  → Listener.onInbound(value) → HsProtocolEngine.LinkListener       HsProtocolEngine.java:154-165
  → HsHex.encode(value) → executor → demux.onFrame(sessionId, frame) HsProtocolEngine.java:158-164
  → InboundFrameDemux.onFrame: 事件目录? → eventPort.onEvent         InboundFrameDemux.java:62-80
                              否则/允许双投 → commandPort.onResponseCandidate  InboundFrameDemux.java:72-80
  → CommandScheduler.onResponseCandidate → matches → processFrame → command.completeResponse  CommandScheduler.java:383-407,649-667
  → CompletionListener → callback.onCompleted(toResult)            HsProtocolEngine.java:81-95,139-145
  → YoHsClientImpl.onMain → mainHandler.post → 用户回调             YoHsClientImpl.java:133-139
```

### 2.3 `onReady` 何时触发（就绪信号的真实语义）

`YoBleCommandLink` 的「就绪」不是「GATT 连上」，而是「**通知特征已使能**」：

1. `client.connect(...)` 回调 `ConnectionWatcher.onConnectionStateChanged`，仅当 `snapshot.isReady()`（=`BleConnectionState.READY`）才 `ensureSubscribed()`（`YoBleCommandLink.java:252-265`）。
2. `ensureSubscribed` → `client.subscribe(NOTIFY_TARGET, DEFAULT, NotifyWatcher)`（`YoBleCommandLink.java:165-183`）。
3. `NotifyWatcher.onNotificationEnabled` → `markReady` → `notifyReady(true)` → `HsCommandLink.Listener.onReady(true)`（`YoBleCommandLink.java:280-283`, `211-219`）。
4. `HsProtocolEngine.LinkListener.onReady(true)` → `onLinkReady` 在协议线程上 `sessionId++`、`commandReady=true`、`scheduler.setSession(sessionId,true)`（`HsProtocolEngine.java:119-137`）。
5. 断开/失败：`DISCONNECTED/DISCONNECTING/FAILED/IDLE` 或 `onConnectionFailed/onNotificationFailed` → `markNotReady` → `onReady(false)`（`YoBleCommandLink.java:185-209,252-302`）。

即：`YoHsClient.isReady()` 为真 **且仅当** GATT 已 READY 且 NOTIFY CCCD 已写入成功。

---

## 3. 缺陷 / 风险清单

> 严重度：P0=阻断/数据错误且必现；P1=重要功能/可靠性缺陷；P2=健壮性/可维护性。
> 所有结论均为静态证据，行号为 `相对路径:行`。

| ID | 级别 | 文件:行 | 问题 | 证据 | 建议 |
|---|---|---|---|---|---|
| H-01 | **P1** | `api/YoHsClient.java:16-53`；`model/HsDeviceObserver.java:10-38`；`internal/session/YoHsClientImpl.java:40-57`；`internal/protocol/HsProtocolEngine.java:168-174` | **连接生命周期对 App 零回调**：`connect()`/`disconnect()`/`close()` 均 `void`，`isReady()` 仅轮询；`HsDeviceObserver` 只有电量/EQ/按键等设备事件，无连接成功/失败/断开；`onLinkError` 仅 `scheduler.abortCurrent(reason)`，不上抛 | `YoHsClient` 全接口无 onConnected/onDisconnected；`setDeviceObserver` 回传的全部是设备域事件；`LinkListener.onLinkError` 只 abortCurrent | 增加 `HsConnectionObserver{onConnected(mac),onDisconnected(reason),onConnectFailed(err)}`，在 `onLinkReady(true/false)` 与 `onLinkError` 处上抛；或 `connect(mac, callback)` |
| H-02 | **P1** | `internal/protocol/inbound/DeviceEventPipeline.java:67-113`；对照 `internal/protocol/HsProtocolEngine.java:81-95` | **首页/全局状态查询结果不写回域存储**：`applyAggregatedHomeStatus(...)` 全仓库无调用者；`HsProtocolEngine.submit` 只把 `CommandCompletion.OBJECT` 原样交给回调，从不调用聚合落地。因此 C7/98/首页多帧查询拿到的电量/EQ/服务状态**不会**进入 `DeviceDomainStore`，`battery()`/`HsDeviceObserver` 只有「主动上报」帧会更新 | `grep applyAggregatedHomeStatus` 仅命中定义 `DeviceEventPipeline.java:67`；`HsProtocolEngine` 无任何对该方法的引用 | 在 `HsProtocolEngine` 对 `HomePageStatusV12*/GlobalStatusCommand` 的成功结果调用 `events.applyAggregatedHomeStatus(...)`；或删除该死方法并明确「查询不落地」契约 |
| H-03 | P2 | `provider/lib_bluetooth/YoBleCommandLink.java:286-296`；`internal/protocol/HsProtocolEngine.java:154-164`；`internal/protocol/inbound/InboundFrameDemux.java:47-86` | **入站无重装/去粘包缓冲**：每次 `onCharacteristicChanged` 的整段 `byte[]` 被当作「一个完整帧」直接 hex 后交 demux；若 BLE 通知分片（跨包头）或粘包（一包多帧），`ProtocolValues.isProtocolFrame` 会判失败→丢帧（日志「未识别入站帧」） | 入站链路无 `StringBuilder`/buffer，`onInbound` 直接 `HsHex.encode(value)` 成单帧 | 在 demux 前加按 `前缀+长度字段+1234 尾标` 的流式拼帧；或确认 lib 已按帧投递后补充注释/断言 |
| H-04 | P2 | `provider/lib_bluetooth/YoBleCommandLink.java:136-157`；`lib_bluetooth/.../BleConnectionConfig.java:127-129` | **单写 + WITHOUT_RESPONSE 不分片**：`client.write(...,WITHOUT_RESPONSE)` 一次写整包，未用 `writeSplit`。默认 `requestMtu=517` 通常够用；但若协商回落到 ATT 23（MTU-3=20B 上限），长帧（改名/CustomEQ/Raw/配对列表回包等 >20B）可能被栈截断且不报错 | `write` 只用基础重载；`BleConnectionConfig.Builder.requestMtu=517` 提供缓解 | 对超过 `maxWritePayloadBytes` 的 payload 走 `writeSplit`；或校验 MTU 后拒绝超长单写 |
| H-05 | P2 | `provider/lib_bluetooth/YoBleCommandLink.java:280-283`；`internal/protocol/HsProtocolEngine.java:119-137` | **重复 ready(true) 会误切会话**：`onNotificationEnabled` 每次使能都 `markReady→notifyReady(true)`；`onLinkReady(true)` 无条件 `sessionId++` 并 `scheduler.setSession(new,true)`，`setSession` 会话切换会 `abortAll` 在途命令。重连/重复使能场景会打断在途命令 | `markReady` 无「已 ready」短路；`onLinkReady` 无幂等判断 | `markReady` 里 `if(ready) return;` 幂等；或 `onLinkReady` 对相同 session 不递增 |
| H-06 | P2 | `provider/lib_bluetooth/YoBleCommandLink.java:59-60,73-75` | `listener` 字段非 `volatile`，`setListener` 无同步；BLE 回调线程读取与构造线程写入的可见性依赖安全的构造发布 | `private Listener listener;`，`setListener` 直接赋值 | 声明 `volatile`（或纳入 `lock`） |
| H-07 | P2 | `provider/lib_bluetooth/YoBleCommandLink.java:158-160` | `write` 的 `catch (RuntimeException ignored) { return false; }` 静默吞异常、无日志，排障困难 | 空 catch 直接返回 false | 至少 `HsLog.w` 记录异常 |
| H-08 | P2 | 多处（见 §7） | 死代码/空钩子/无操作监听器，见第 7 节 | — | 删除或接线 |
| H-09 | P2 | `model/HsCommandResult.java:8-49` vs `internal/protocol/CommandCompletion.java:14-110`；`model/HsBattery.java:8-55` vs `internal/protocol/inbound/DeviceDomainStore.java:46-96` | 值对象双份：`CommandCompletion`(shape/code/data/resultValue/message) 与 `HsCommandResult`(code/value/message/data) 几乎同构，`toResult` 每次搬迁；`HsBattery` 与 `BatterySnapshot` 字段一一对应，`engine.battery()` 每次复制 | `HsProtocolEngine.toResult` 映射；`battery()` 逐字段 new | 合并为单一值对象，或在边界层用不可变视图共享 |
| H-10 | P2 | `internal/protocol/inbound/InboundFrameDemux.java:51` + `internal/protocol/CommandScheduler.java:387` | 会话门禁重复：demux `sessionGate.isCurrent` 与 scheduler `frameSessionId!=sessionId` 各判一次（防御性重复，非错误） | 同帧两次判 session | 保留一处即可，注释说明纵深防御 |
| H-11 | P2 | `provider/lib_bluetooth/YoBleCommandLink.java:78-102`；`internal/protocol/HsProtocolEngine.java:54-56` | `open()` 在**调用线程（通常主线程）**直接抛 `IllegalArgumentException`/`IllegalStateException`，`connect()` 无返回值/无异常契约文档；App 侧难统一处理 | `link.open` 未捕获异常 | 在 `connect` 文档标注可抛异常，或捕获后转回调失败 |
| H-12 | P2 | `provider/lib_bluetooth/YoBleCommandLink.java:185-209` | 断开原因丢失：状态机断开分支 `markNotReady(currentGeneration, null)` 不携带 error；只有 `onConnectionFailed/onNotificationFailed` 才带 message | 两处传 `null` | 统一携带 `BleFailure`/原因字符串并上抛（与 H-01 合并解决） |

---

## 4. 连接状态可观测性结论

**App 能否知道连接成功/失败/断开？—— 协议层当前不能。**

可用 API（`api/YoHsClient.java`）：
- `void connect(String mac)`（`:16`）、`void disconnect()`（`:18`）、`void close()`（`:20`）
- `boolean isReady()`（`:22`）——**唯一**可查询连接态的手段（轮询）
- `void setDeviceObserver(HsDeviceObserver)`（`:27`）——**只**回传设备域事件（`HsDeviceObserver.java:10-38`：电量/EQ/游戏/提示音/语音助手/新手模式/换耳/服务状态/配对列表），**无任何连接事件**
- `HsBattery battery()`（`:25`）——域电量快照

缺失的 API 面：
1. **无连接成功回调**：`onReady(true)` 只到 `HsProtocolEngine`，不出 `YoHsClient`。
2. **无连接失败回调**：`onLinkError` 仅 `scheduler.abortCurrent`（`HsProtocolEngine.java:168-174`），错误被吞。
3. **无断开回调/原因**：断开走 `onReady(false)`（`YoBleCommandLink.java:259-264`），App 不可见。
4. **`connect()` 无结果/失败面**：`void`，异常在主线程直接抛（H-11）。
5. **无重连状态**：lib 有 `maxRetryCount`，协议层不暴露「重连中/重试第 N 次」。

应用侧现状（佐证）：`app/.../provider/lib_bluetooth/HsDevicePort.java:109-117` 的 `connect()`/`disconnect()` 是**空实现**（注释「连接会话在后续阶段接入」），App 目前并未真正接入 `YoHsClient`。因此缺失回调是**下一步接入的硬阻塞**，需在接入前补齐连接生命周期 API。

**建议接口**（示例）：
```java
public interface HsConnectionObserver {
    void onConnected(@NonNull String mac);
    void onDisconnected(@Nullable String reason);
    void onConnectFailed(@Nullable String error, boolean willRetry);
}
// YoHsClient 增加 setConnectionObserver(...)；在 onLinkReady(true/false)、onLinkError 处驱动
```

---

## 5. 数据链路闭合性结论

**协议层命令链路闭合**：所有写请求都有对应的「通知回帧 → 匹配 → 完成」路径，且命令生命周期由 `Command` 自身完成订阅驱动，调度器只观察不越权（`Command.java:178-290`，`CommandScheduler.java:669-718`）。关键闭环点：

- 写窗口缓冲：`onResponseCandidate` 在 `writeStarted && !writeCompleted` 时把帧塞进 `bufferedFrames`，`onWriteCompleted` 后回放——解决「设备比 GATT 写回调更快回帧」的竞态（`CommandScheduler.java:398-401,637-646`）。
- 超时统一由调度器管理：首帧用目录 `responseTimeoutMs`，未声明回退 `Command.getTimeoutMs()`；多帧用 `getInterFrameTimeoutMs()`（`CommandScheduler.java:748-780`）。
- 迟到帧隔离：`ResponseQuarantine` 按响应族（前缀前 6 hex）在窗口内丢弃，防串包（`CommandScheduler.java:802-889`）。
- 事件与应答分离：`InboundFrameDemux` 先走事件目录，未命中或允许双投才问当前命令（`InboundFrameDemux.java:62-86`）。

**闭合性缺口（非断链，是健壮性风险）**：H-03（入站无重装/去粘包）、H-04（长帧单写不分片）、H-05（重复 ready 切会话）。这三项决定「在真实 BLE 时序/分片下是否仍闭合」，建议在接入 App 前用真机回归验证（见未知项）。

---

## 6. CBT / BLE 解耦结论（带证据）

**结论：HS(BLE) 路径与 CBT(Classic) 无代码级耦合；边界由 Gradle 任务强制。**

证据：
1. **全模块无 `android.bluetooth.*` 引用**：`grep "import android\.bluetooth" biz_ggec_hs/**/*.java` → 无匹配。
2. **`lib_bluetooth` 仅在 `provider/lib_bluetooth/` 内被引用**：
   - `provider/lib_bluetooth/YoBleCommandLink.java:9-23`（`YoBluetoothSDK`/`YoBleClient`/`Ble*`）
   - `provider/lib_bluetooth/HsScanSnapshots.java:7`（`BleScanRecord`，广播方向）
   - 无 `import com.yovo.lib_bluetooth.internal.`（未触达库内部）。
   > 注：任务前提「YoBleCommandLink 是唯一 import lib_bluetooth 的类」需修正——**`HsScanSnapshots` 也 import `lib_bluetooth`**，但它属广播扫描适配，同样位于 `provider/lib_bluetooth/`，符合边界；命令链路方向确实只有 `YoBleCommandLink`。
3. **协议/内部包不引用蓝牙/Provider/Android BT**：`internal/protocol/**`、`internal/broadcast/**` 无 `import com.yovo.lib_bluetooth` / `provider.` / `android.bluetooth`（`build.gradle:70-80` 规则，实测无匹配）。
4. **无 `cbt` 字面量**：`grep "cbt|CBT"` → 无。`classic` 仅出现在：
   - `internal/protocol/devicecmd/ClassicBtAddressCommand.java:14`（一个**通过 BLE 协议查询设备经典蓝牙地址**的命令，是数据字段）
   - `model/broadcast/Hs01Identity.java|Hs02Identity.java` 的 `classicAddress`（广播/扫描响应里携带的 BR/EDR 地址字段）
   → 均为「地址数据」，**不是** CBT 客户端/连接耦合，无 CBT 概念渗透到 BLE 命令路径。
5. **边界任务**：`biz_ggec_hs/build.gradle:44-115` 定义 `checkBizGgecHsBoundary`，`tasks.named('check'){ dependsOn ... }`（`:117-119`）在 `check` 阶段强制执行（禁 lib_bluetooth 越界、禁 protocol↔broadcast 互引、禁 api/model 引 internal 等）。本次为只读环境未执行该任务，但按规则逐条人工核对 import 均合规。

**解耦评价**：✅ 协议层对 BLE 的依赖被收敛为 1 个接口（`HsCommandLink`）+ 1 个实现（`YoBleCommandLink`），广播方向另有 1 个薄适配（`HsScanSnapshots`）。CBT 概念未进入 HS(BLE) 路径。

---

## 7. 冗余 / 防御性代码清单

### 7.1 死代码（定义存在、无调用者）

| 项 | 位置 | 说明 |
|---|---|---|
| `CommandScheduler.StateListener` + `Snapshot` 全量构建 | `HsProtocolEngine.java:44` 传入 `snapshot -> { }`；`CommandScheduler.java:980-991` | **运行时监听器是 no-op**，但每次状态变化仍 `collectCommands` + new Snapshot + unmodifiableList——纯开销，无功能 |
| `InboundFrameDemux.pulse` | `HsProtocolEngine.java:49` 传 `() -> { }`；`InboundFrameDemux.java:31,58` | 「入站续命」钩子为空实现 |
| `CommandQueryGroup`（整类） | `internal/protocol/CommandQueryGroup.java:1-97` | 全仓库无引用（第三种并发抽象，与 `CommandTransaction`/单命令并列，未被使用） |
| `Command.CommandCallback` + `addCommandCallback` + `dispatchCallback` | `Command.java:40-46,195-200,233-248` | 无调用者（`CompletionListener` 已取代） |
| `Command.getCommandPrefix()` | `Command.java:103-105` | 仅 `setCommandPrefix` 被各命令调用，getter 无读取者 |
| `CommandScheduler.setReady(boolean)` | `CommandScheduler.java:333-335` | 无调用者（引擎用 `setSession`） |
| `CommandScheduler.onResponseFrame(String)` / `onResponseFrame(long,String)` | `CommandScheduler.java:409-415` | 无调用者（demux 走 `onResponseCandidate`） |
| `DeviceEventPipeline.applyAggregatedHomeStatus` | `DeviceEventPipeline.java:67-113` | 无调用者（见 H-02，属功能缺口而非纯死码） |
| `DeviceEventPipeline.notifyFileExportSucceeded/Failed` 调用链 | `DeviceEventPipeline.java:160-166`→`DeviceEventRouter.java:87-100` | Pipeline 层两方法无调用者，整条「文件导出状态」入口未被使用 |
| `DeviceEventPipeline.domain()` | `DeviceEventPipeline.java:49-51` | 无调用者 |
| `DeviceEventPipeline.clearListeners()/resetState()/resetStateKeepingBattery()/clearBattery()` | `DeviceEventPipeline.java:168-182` | 无调用者（会话清理相关，当前无调用方） |

### 7.2 防御性代码（保留合理，但需知晓）

- 空 catch：`YoBleCommandLink.java:158`（write 吞异常，见 H-07）；`CommandScheduler.java:809,867,883,895`（quarantine/responseFamily/matches 的保守 catch）；`DeviceDomainStore.java:439`（Integer 解析降级 `--`）；`HsProtocolEngine.java:186`（hex 解码失败返回 false）。
- 多处 `catch (RuntimeException)` + `HsLog`：`Command.notifyListener`、`DeviceDomainStore.safe`、`DeviceEventPipeline.dispatch`、`DeviceEventRouter.route`——事件/回调失败不扩散，合理。
- `Command` 的双重完成保护 `completionRecorded.compareAndSet`（`Command.java:218-221`）、`CommandScheduler.isCurrentAttempt` 的 attemptId 校验（`:796-800`）——**正确的**防御，用于抵御迟到回调/重入。

### 7.3 并发/线程模型评价

- 线程模型**正确**：单线程 `ScheduledExecutorService("yo-hs-protocol")` 串行化调度、分帧、事件、写回调（`HsProtocolEngine.java:38,232-238`）；BLE 各回调一律 `executor.execute` 切回协议线程（`LinkListener`/`ExecutorDispatcher`）；用户回调统一 `mainHandler.post` 切主线程（`YoHsClientImpl.java:79,133-139,148-192`）。**未发现跨线程直接操作调度状态，未发现回调重入或死锁**（`mainHandler` 异步，命令完成监听器在协议线程触发并经 `dispatcher.execute` 再入队）。
- 唯一并发瑕疵：`YoBleCommandLink.listener` 非 `volatile`（H-06）。

---

## 8. 未知项（本次静态审查无法定论）

| ID | 未知 | 影响 | 建议验证 |
|---|---|---|---|
| U1 | `lib_bluetooth` 是否对入站通知做**按帧重装/分片合并** | 决定 H-03 是否真实丢帧 | 读 lib 通知分发实现或在真机打印每包 hex 长度 |
| U2 | 默认 `requestMtu=517` 在目标机型是否稳定协商成功 | 回落 23 时 H-04 长帧截断风险 | 真机日志确认最终 MTU；覆盖长帧命令（改名/CustomEQ/Raw） |
| U3 | `CommandPolicyRegistry` 与全部命令类是否 1:1（约 100+ 命令类，本次仅抽查公开 API 命令均登记） | 未登记命令将在 `resolve` 抛「命令未登记调度策略」 | 写脚本对比 `extends Command` 与注册表 `.class` 集合 |
| U4 | 真机上 `onNotificationEnabled` 是否**只回调一次** | 决定 H-05 是否触发会话误切 | 真机记录 `onReady(true)` 次数 |
| U5 | App `HsDevicePort.connect` 何时真正接入 `YoHsClient` | 决定 H-01/H-02 是否成为上线阻塞 | 与设备模块负责人确认接入排期 |
| U6 | `checkBizGgecHsBoundary` 实际执行结果 | 本次只读环境未运行 Gradle，仅人工核对 import | 在有构建环境执行 `./gradlew :biz_ggec_hs:checkBizGgecHsBoundary` |

---

### 附：关键链路最短证据索引

- 装配：`HsGraph.java:17-23`、`YoHsSdk.java:19-21`
- 链路抽象：`internal/contract/HsCommandLink.java:11-35`
- BLE 适配：`provider/lib_bluetooth/YoBleCommandLink.java:78-102`（connect）、`:165-183`（subscribe）、`:124-163`（write）、`:245-302`（watchers）
- 引擎：`internal/protocol/HsProtocolEngine.java:33-52`（ctor）、`:119-137`（ready）、`:147-175`（listener）、`:177-206`（transport）
- 调度：`internal/protocol/CommandScheduler.java:138-190`（submit）、`:383-407`（应答入口）、`:544-612`（start）、`:669-718`（完成）
- 分帧：`internal/protocol/inbound/InboundFrameDemux.java:47-86`
- 域状态：`internal/protocol/inbound/DeviceDomainStore.java`、`DeviceEventPipeline.java`、`DeviceEventRouter.java`
- 边界：`biz_ggec_hs/build.gradle:44-119`
