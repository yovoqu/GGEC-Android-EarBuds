# lib_bluetooth · BLE 子系统只读审计（第 1 轮）

## 1. 概述

- **范围**：`lib_bluetooth` 模块 BLE 子系统——`com.yovo.lib_bluetooth.internal.ble`（含 `scan`/`connection`/`gatt`/`channel`/`transfer`/`pairing`/`peripheral`/`device`/`diagnostics`/`error`/`runtime`/`internal.android` 子包）、`model.ble` 数据模型、`api.YoBleClient` 对外接口。
- **方法**：只读源码；先目录（`glob`）后逐类（`read`/`grep`）；每条结论附 `相对路径:行号` 与行内要点。未执行代码、未修改源码。仅新增本报告文件。
- **本轮重点**：扫描生命周期、四上下文线程模型、扫描数据结构、BLE↔CBT 解耦、死代码/防御性代码、扫描与连接/GATT 边界。

## 2. 模块结构与数据流

### 2.1 关键调用链（App → YoBleClient → BleClientImpl → scanController → platform）

```
App
 └─ YoBluetoothSDK.ble(context, config)          YoBluetoothSDK.java:37-38
     └─ BluetoothSdkCore.ble(...)                BluetoothSdkCore.java:32-36
         ├─ YoBtLogger.ensureInitialized          BluetoothSdkCore.java:33
         ├─ BluetoothProviderInstaller.installAll BluetoothSdkCore.java:34
         └─ new BleClientImpl(context, config)    BluetoothSdkCore.java:35
             ├─ YoBleAndroidModuleGraph.create    BleClientImpl.java:60
             │    ├─ new YoBleRuntimeConfig(callbackOnMainThread)  YoBleAndroidModuleGraph.java:42-44
             │    ├─ new YoBleModuleCore(...)     YoBleAndroidModuleGraph.java:46-55
             │    │    ├─ new YoBleRuntime(runtimeConfig)          YoBleModuleCore.java:75
             │    │    ├─ new YoBleScanController(runtime, config, inspector, scanPlatform, ...)  YoBleModuleCore.java:82-83
             │    │    └─ new YoBleConnectionManager / L2cap / Peripheral / Bond  YoBleModuleCore.java:84-92
             │    ├─ new YoBleAndroidScanPlatform(adapterProvider) YoBleAndroidModuleGraph.java:50
             │    └─ 注册 AdapterState/BondState 广播          YoBleAndroidModuleGraph.java:57-64
             └─ core = androidGraph.core          BleClientImpl.java:61
```

### 2.2 扫描数据流

```
scan(config, observer)  BleClientImpl.java:110-112
 └─ YoBleScanController.scan()                 YoBleScanController.java:92-98
     └─ runtime.state(startScanInternal)       YoBleScanController.java:96
         ├─ checkPreflight(BLE/开关/权限/位置) YoBleScanController.java:170,485-514
         ├─ throttleGuard.requiredDelayMillis  YoBleScanController.java:177-189
         ├─ stopActiveInternal(scanning)        YoBleScanController.java:191
         └─ launchPlatformScan(...)            YoBleScanController.java:214/223-300
             └─ scanPlatform.startPlatformScan  YoBleScanController.java:248
                 └─ YoBleAndroidScanPlatform.startPlatformScan  YoBleAndroidScanPlatform.java:56-141
                     └─ BluetoothLeScanner.startScan(settings/filters, androidCallback)  118-121
```

平台回调经代数隔离后回到 state 线程：

```
ScanCallback.onScanResult  YoBleAndroidScanPlatform.java:76-83（gen/activeCallback 守卫 77）
 └─ PlatformCallback.onScanResult  YoBleScanController.java:230-234
     └─ runtime.state(handlePlatformResult)   YoBleScanController.java:233
         ├─ YoBleScanFilterMatcher.matchesConfig      368-374
         ├─ snapshotStore.recordScanResult            375
         ├─ callbackEmulator.onResult（去重/FIRST_MATCH 仿真） 376-377
         └─ runtime.callback(observer.onDeviceFound)  382-387
```

定时器：timeout `286-290`、lost sweep `349-366`、software flush `325-337`、long-scan restart `302-312`。停止/释放统一收口于 `stopActiveInternal` `437-483`。

## 3. 缺陷 / 风险清单

> 级别：P0=致命（必然卡死/崩溃/数据错误）；P1=高（特定时序/场景可复现的卡死、停扫失败、回调丢失）；P2=中低（兼容/冗余/设计/边界）。

| ID | 级别 | 文件:行 | 问题 | 证据（行内要点） | 建议 |
|----|------|---------|------|------------------|------|
| BLE-01 | P1 | `internal/ble/scan/YoBleScanController.java:92-98,106-109,133-135,277-278` | **scan() 异步启动 vs stopScan() 同步判定的竞态**：`scan()` 只把 `startScanInternal` 投递到 state 线程；`scanning`/`pendingStart` 仅在 state 任务内被置位。调用方在启动任务执行前调用 `stopScan()` 会命中 `!scanning && !pendingStart` 而返回 `ILLEGAL_STATE`，但已排队的启动任务随后仍会真正启动 → “刚发起就停”无法停止，扫描持续到 timeout。`isScanning()`/`flushBatchScanResults()` 在同样窗口返回 false/错误。 | `scan(): runtime.state(() -> startScanInternal(gen,...))`；`stopScan(): if (!scanning && !pendingStart) return failure(ILLEGAL_STATE)`；`launchPlatformScan: scanning = true`（277）。 | 引入同步的“启动中/运行中”状态机（如 `PENDING_START`）并在 `scan()` 内同步置位；`stopScan()` 对 `PENDING_START` 也走 state 停止；或让 `scan()/stopScan()` 返回可等待的完成信号。 |
| BLE-02 | P1 | `internal/ble/scan/YoBleScanController.java:96,165-169` + `internal/ble/runtime/YoBleRuntime.java:101-108` | **runtime 关闭后 scan() 静默丢回调**：`scan()` 无条件 `runtime.state(...)`；state executor 已 shutdown 时 `state()` 捕获 `RejectedExecutionException` 直接丢弃任务，`startScanInternal` 中的 `RELEASED` 失败回调分支永不执行；`scan()` 仍返回 handle，用户既无 `onScanStarted` 也无 `onScanFailed` → 回调悬挂。`stopScan()` 仅在 `controller.released`（由 state 任务置位）时防护，CLOSED 后该标志可能仍为 false。 | `YoBleRuntime.state()`：`catch (RejectedExecutionException closed) { pendingStateTasks.decrementAndGet(); }`（105-107）；`startScanInternal`：`if (released || runtime.isReleased()) { emitFailure(...); return; }`（165-169）。 | `scan()` 入口先同步检查 `core.released/runtime.isReleased()` 并直接在调用线程回调失败；handle 返回前保证“要么已受理、要么已失败回调”。 |
| BLE-03 | P1 | `internal/ble/scan/YoBleScanController.java:217-300,368-411` + `internal/ble/internal/android/YoBleAndroidScanPlatform.java:56-141,160-180` | **state 线程内同步执行 BluetoothLeScanner binder 调用，存在“域级冻结”风险**：`startPlatformScan`（`getBluetoothLeScanner()/startScan`）、`stopPlatformScan`、`flushPendingScanResults` 全部在 state 线程执行（`launchPlatformScan`→248、`stopActiveInternal`→475、`flushBatchScanResults`→126）。一旦底层 binder 阻塞/异常慢，唯一 state 线程被占，扫描、连接、GATT、配对全部排队 → 表现为“扫描卡死/无法再次扫描”。 | `launchPlatformScan: scanPlatform.startPlatformScan(...)`（248）；`stopActiveInternal: scanPlatform.stopPlatformScan()`（475）；`flushBatchScanResults: scanPlatform.flushPendingScanResults()`（126）。 | 评估将 `start/stop/flush` 移到 `runtime.io()`（结果再回 state），或对平台调用设超时/异步化；至少文档化该阻塞面。 |
| BLE-04 | P2 | `internal/ble/scan/YoBleScanController.java:256-272,296-299,314-323,325-337` | **长扫重启后软件批量 flush 循环叠加**：`restartLongScan` 未取消旧 `batchFlushHandle`，而 `launchPlatformScan` 每次（含 `firstStart=false`）都 `scheduleSoftwareFlush`；旧循环在 `softwareBatching && scanning && activeGeneration==generation` 仍成立时继续自递归，每 25 分钟重启再叠加一条循环；`batchFlushHandle` 只保存最新一条，`stopActiveInternal` 只能取消最新（454-457），历史循环靠任务内 `!scanning` 自灭。不构成永久 latch，但存在重复 flush/叠加定时器。 | `armLongScanRestart/restartLongScan`(302-323) 只处理长扫句柄；`if (softwareBatching) scheduleSoftwareFlush(generation);`(297-299)；旧循环判据 `if (!softwareBatching || !scanning || activeGeneration != generation) return;`(331-333) 在重启后仍为真。 | 重启前先 `batchFlushHandle.cancel()` 并在 `launchPlatformScan` 中统一重置批处理循环；或让 flush 循环以“代际+唯一 token”单实例运行。 |
| BLE-05 | P2 | `internal/ble/scan/YoBleScanController.java:256-272` | **平台启动失败路径未清理定时句柄**：失败分支只重置 `scanning/pendingStart/activeConfig/activeObserver`，未 cancel `timeoutHandle/lostSweepHandle/batchFlushHandle/longScanRestartHandle`。若 `timeoutMillis==0`（无限扫描）则无 timeout 自愈，遗留句柄需各自触发后才因 `!scanning` 退出。 | 失败分支 256-272 无 `cancel*` 调用；对比 `stopActiveInternal` 438-457 才集中 cancel。 | 失败路径复用 `stopActiveInternal`（或先 cancel 全部句柄）再置终态。 |
| BLE-06 | P2 | `internal/ble/scan/YoBleScanController.java:170-189,191` | **preflight 失败/FAIL_FAST 限流直接 return，不停止既有扫描**：既有 active scan 继续运行，调用方可能误判“新 scan 已失败/结束”而旧 scan 仍在收包。 | `checkPreflight` 失败 `return`（170-175）；`FAIL_FAST` 限流 `return`（179-189）；`stopActiveInternal(scanning)` 在其后（191）才执行。 | 明确语义：拒绝新请求时是否保留旧扫描；如需替换，将 stop 提前或在文档/接口说明。 |
| BLE-07 | P2 | `model/ble/BleScanConfig.java:136,500` (`YoBleScanController.java:499-512`) | **`locationExempt` 默认 true 可能跳过位置前置校验**：`requiresLocation = sdkInt()<31 \|\| !locationExempt`，API31+ 默认跳过位置权限/位置服务检查；若 App 实际需要位置（未声明 `neverForLocation`）会漏检，底层扫描可能失败或行为异常。 | `private boolean locationExempt = true;`（136）；`boolean requiresLocation = capabilityInspector.sdkInt() < 31 \|\| !config.locationExempt();`（500）。 | 默认改为“按 Manifest 能力显式声明”，或提供工厂时强制调用方明确；在文档中标注该默认值风险。 |
| BLE-08 | P2 | `model/ble/BleScanResult.java:31-32,114-117` + `internal/ble/internal/android/compat/YoBleScanCompat.java:165-166,191` | **数据结构职责渗漏/字段重复**：`BleScanResult` 携带 `callbackType`（分发语义而非设备数据）；`observedAtMillis` 同时存在于 `BleDeviceIdentity.updatedAtMillis` 与 `BleScanResult.observedAtMillis`，构造时由同一值重复写入。 | `BleScanResult` 字段 `callbackType`（31-32）+ `withCallbackType`（119-124）；compat 同时向 identity 与 result 传 `observedAtMillis`（166、191）。 | `callbackType` 移出值对象（仅作回调参数）；时间戳单一来源，identity 不复刻扫描时间。 |
| BLE-09 | P2 | `internal/ble/internal/android/compat/YoBleScanCompat.java:133` | **空地址兜底为全零 MAC**：`dev.getAddress()` 为空时用 `"00:00:00:00:00:00"` 作 `BleDeviceId`，多台异常设备会合并到同一 deviceId → 快照/回调串号。 | `String address = dev != null && dev.getAddress() != null ? dev.getAddress() : "00:00:00:00:00:00";` | 保留“未知地址”独立标识（含来源/计数），不要用可合法存在的全零 MAC 合并。 |
| BLE-10 | P2 | `internal/ble/scan/YoBleScanController.java:372-374` + `internal/ble/internal/android/compat/YoBleScanCompat.java:72-126` | **硬件 filter 下推 + 软件复核的双重过滤潜在假阴性**：filter 已下推 `startScan(filters,...)`，`handlePlatformResult` 又用 `YoBleScanFilterMatcher` 复核；若个别 ROM 在硬件过滤时返回的 `ScanRecord` 不含对应 UUID/serviceData（被裁剪），软件复核会丢弃硬件已匹配结果 → 回调丢失。 | `matchesConfig(activeConfig, result)` 前置过滤（372-374）；`toAndroidScanFilters` 下推（72-126）。 | 对“已由硬件匹配的 filter”在软件侧放宽/跳过；或在文档标注已知 ROM 风险并提供关闭软件复核开关。 |
| BLE-11 | P2 | `internal/ble/scan/YoBleScanCallbackEmulator.java:35,57-64,78-85` | **MAX_SEEN=256 驱逐活跃设备导致 FIRST_MATCH 重复**：驱逐按插入序，设备数 >256 时仍活跃设备被逐出，其下一条结果视作新设备再次 `FIRST_MATCH`。 | `MAX_SEEN=256`（35）；`while (seen.size() >= MAX_SEEN) { ... oldest.remove(); }`（57-64）；`if (isNew \|\| !entry.notifiedFirstMatch) { ... FIRST_MATCH }`（78-85）。 | 驱逐改为 LRU/按 `lastSeenMillis` 淘汰最久未见者；或按业务上限扩容。 |
| BLE-12 | P2 | `internal/ble/scan/YoBleScanController.java`（全类） | **缺线程封闭断言**：`YoBleScanController` 各状态方法均未调用 `runtime.checkStateThread()`，而 connection/gatt 组件普遍调用；仅靠“都由 runtime.state 投递”的约定，后续回归易误用。 | 对比 `YoBleGattSession.java:218/231/243/250/256`、`YoBleGattOperationQueue.java:181/209/227/251/276/290/312` 均有 `runtime.checkStateThread()`；扫描控制器无。 | 在 `startScanInternal/handlePlatformResult/handlePlatformBatch/stopActiveInternal` 等入口加 `runtime.checkStateThread()`。 |
| BLE-13 | P2 | `internal/ble/internal/android/compat/YoBleScanCompat.java:207-220` | **地址类型仅在 API35+ 解析**：`getAddressType()` 被 `VANILLA_ICE_CREAM`（API35）守卫，API<35 恒返回 `UNKNOWN`；若该 API 在 33 已可用，则 API33/34 过度保守丢地址类型（需按 compileSdk 核实）。 | `if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) { switch (dev.getAddressType()) ... }` | 按实际引入版本下调守卫（如 `TIRAMISU`）；并在文档标注未知场景。 |
| BLE-14 | P2 | `internal/ble/YoBleModuleCore.java:200-214` | **释放顺序与快照清空时序**：`releaseCoordinator` 逆序释放，先注册的 `scanController` 最后释放；`snapshotStore::clear` 最后注册→最先执行（清空），随后 connection 释放中的 `publish()` 会重新写入快照到已清空的 store。功能影响小，但释放期快照/诊断语义不干净。 | 注册顺序 `94-99`（scan, connection, l2cap, peripheral, bond, snapshotStore::clear）；`releaseAll()` 逆序执行（`YoBleReleaseCoordinator.java:42-49`）。 | 将 `snapshotStore::clear` 改为最后注册（最先清理之后执行）或放 `core.release` 末尾显式清理。 |

### 3.1 线程模型核查结论（重点：回调重入死锁 / release-CLOSING）

- **回调不会在 state 线程执行**：`YoBleRuntime.callback` 仅经 `callbackDispatcher.dispatch`（主线程或独立 callback 线程）投递（`YoBleRuntime.java:128-133,90-97`），领域状态只在 `state()` 单线程读写（`24-33` 注释与实现）。因此**不存在“回调在 state 线程内重入导致 state 锁死”的路径**——这是正向结论。
- **主线程不会阻塞等待 state**：同步 API（`getDeviceSnapshot/getDeviceSnapshots/getAdapterState/getConnectionSnapshot/getServices/getPendingOperationCount`）均读 volatile/`synchronized` 快照表（`YoBleDeviceSnapshotStore.java:106-119`），不向 state 线程发请求再等待；`scan/stopScan/connect/...` 均为“投递后返回”。故 main↔state 无循环等待。
- **release/CLOSING 语义基本成立**：`release()` 不阻塞调用线程；`isReleased()` 在 CLOSING 即 true（`YoBleRuntime.java:162-174`）；state 线程排空后再 `shutdown` 各执行器（`186-203`）。**但**存在 BLE-02（CLOSED 后投递被静默丢弃）与 BLE-01（启动任务与同步判定竞态）两个缺口。
- **唯一 state 线程承担全部平台 binder 调用**，是“域级冻结/扫描卡死”的主要结构风险（BLE-03）。

## 4. BLE ↔ CBT 解耦结论（带证据）

**结论：BLE 与 Classic(CBT) 在 lib 内部真正解耦；二者无源码级依赖、无共享 runtime/executor/可变状态，仅共享 common 层的跨切面单例（权限端口/诊断总线/日志），属基础设施而非协议耦合。**

证据：

1. BLE 包内 **零** 引用 classic 包：`grep "import com.yovo.lib_bluetooth.internal.classic"` 于 `internal/ble` → No matches。
2. classic 包内 **零** 引用 BLE 包：`grep "import com.yovo.lib_bluetooth.internal.ble"` 于 `internal/classic` → No matches。
3. BLE 包内 `grep "YoCbt|classic"` → No matches。
4. 扫描子包只 import BLE 自身 `device/diagnostics/error/runtime`（`internal/ble/scan` 内 `^import com.yovo.lib_bluetooth.internal.ble.` 命中 6 条，均为自身子包，`YoBleScanController.java:6-11`）；不 import connection/gatt/channel/peripheral/transfer/pairing。
5. 各自创建独立 runtime：BLE `new YoBleRuntime(runtimeConfig)`（`YoBleModuleCore.java:75`）；CBT `new YoCbtRuntime(...)`，executor 由 `YoExecutors.single/fixed/scheduled` 各自新建（`classic/runtime/YoCbtRuntime.java:52-65`）。`YoExecutors` 各方法每次 `Executors.*` 新建实例（`common/concurrency/YoExecutors.java:22-41`），非共享池。
6. 共享单例仅限：`BluetoothProviderInstaller`（权限端口，`provider/lib_common/BluetoothProviderInstaller.java:12-28`）、`BluetoothDiagnosticsBus`（`common/diagnostics/BluetoothDiagnosticsBus.java:31` 静态 INSTANCE）、`YoBtLogger`——均为进程级横切设施，不承载 BLE/CBT 会话状态，不构成解耦缺陷。

补充：BLE 的 `YoBleRuntime` 自带 `BluetoothEventBus`（`YoBleRuntime.java:61,141-143`），但 BLE 包内无任何 `eventBus()` 使用（仅 CBT 使用），属 BLE 侧冗余能力（见第 5 节）。

## 5. 冗余 / 防御性代码清单（带证据与处置建议）

| 编号 | 类型 | 位置 | 说明 | 处置建议 |
|------|------|------|------|----------|
| D-01 | 死代码（方法） | `internal/ble/scan/YoBleScanThrottleGuard.java:55-58` | `recentStartCount()` 全库无调用（grep 仅定义处）。 | 删除或接入诊断导出。 |
| D-02 | 死代码（方法） | `internal/ble/scan/YoBleScanThrottleGuard.java:60-62` | `reset()` 全库无调用。 | 删除；若需在 adapter 重启时清零再接入。 |
| D-03 | 死代码（构造器） | `model/ble/BleScanResult.java:34-38` | 三参构造器无调用（`new BleScanResult(` 仅 121/187 两处）。 | 删除。 |
| D-04 | 死代码（方法） | `internal/ble/error/YoBleScanAdvertiseStatus.java:47-58,73-82` | `describeScan()/describeAdvertise()` 无调用。 | 删除或用于错误文案/诊断。 |
| D-05 | 死代码（方法） | `internal/ble/gatt/YoBleGattOperationQueue.java:169-171` | `isBusy()` 无调用。 | 删除或供诊断。 |
| D-06 | 死代码（能力） | `internal/ble/runtime/YoBleRuntime.java:61,141-143,195` | BLE `eventBus` 字段与 `eventBus()` 访问器在 BLE 包内无任何使用（仅 CBT 使用自己的 runtime）。 | BLE 移除 eventBus 或明确后续使用点。 |
| D-07 | 死代码（方法） | `internal/ble/runtime/YoBleRuntime.java:145-148` | `diagnostics()` 无调用。 | 删除或接入。 |
| D-08 | 冗余防御 | `internal/ble/internal/android/YoBleAndroidScanPlatform.java:35,67,145,162` | `scanLock` 仅序列化 state 线程自身发起的 start/stop/flush（所有调用方都在 state 线程），对跨线程无保护价值。 | 若坚持 state 单线程调用，可移除锁并加 `checkStateThread` 断言；否则标注用途。 |
| D-09 | 冗余防御 | `internal/ble/scan/YoBleScanController.java:318-321,473-482` | `try { scanPlatform.stopPlatformScan(); } catch (Throwable ignored) {}`：平台层 `stopPlatformScanLocked` 已内部 `catch(Throwable)`（`YoBleAndroidScanPlatform.java:174-179`），双层吞异常。 | 保留一层即可；上层可记录日志。 |
| D-10 | 冗余防御 | `internal/ble/internal/android/compat/YoBleScanCompat.java:155-160,217-221,239-241` | `getAlias()/getAddressType()/getType()` 的 `catch(Throwable ignored)`——跨 API 兼容必要；但吞掉后恒返回 UNKNOWN，掩盖真实异常。 | 必要兼容保留，但至少 `YoBtLogger.d` 记录一次。 |
| D-11 | 冗余防御 | `internal/ble/connection/YoBleGattSession.java:587-601` | `closeLocal()` 对同一 handle 先 `disconnect()` 再 `close()` 各包一层 `catch(Throwable ignored)`——拆链兜底必要。 | 保留。 |
| D-12 | 冗余防御 | `internal/ble/YoBleModuleCore.java:206-211`、`internal/ble/internal/android/receiver/YoBleAndroidReceiverRegistry.java:47-55` | 释放期 `platformTeardownHook`/`unregisterReceiver` 的 `catch(Throwable ignored)`——释放不阻断必要。 | 保留，建议记日志。 |
| D-13 | 一致性 | `internal/ble/runtime/YoBleRuntime.java:74-98` | BLE 直接用 `Executors.newSingleThreadExecutor/newFixedThreadPool/newSingleThreadScheduledExecutor`，而 CBT 复用 `YoExecutors.*`（`YoCbtRuntime.java:52-65`）。命名/线程工厂风格与 CBT 不一致（BLE 的 io 线程用 `numbered()`，scheduler 用裸 `new Thread`）。 | 统一到 `YoExecutors`，减少两套线程工厂分叉。 |
| D-14 | 冗余字段 | `model/ble/BleScanResult.java:28-30` vs `model/ble/BleScanRecord.java:28,123-126` | `BleScanResult.txPower`（来自 `ScanResult.getTxPower()`）与 `BleScanRecord.txPowerLevel`（AD 0x0A）语义相近但来源不同，调用方易混。 | 文档区分“扩展广播 TX Power” vs “AD TX Power Level”，或合并命名。 |
| D-15 | 冗余语义 | `model/ble/BleScanConfig.java:16,57` + `model/ble/BleScanFilter.java:43,134` + `YoBleScanFilterMatcher.java:24,41` | `requireConnectable` 在 config 与 filter 两级重复，两处都参与匹配。 | 明确优先级（filter 覆盖 config）并在文档声明。 |

**“必要兼容 vs 冗余补丁”归类**：D-08~D-12 属“必要兼容/防御”，保留但建议补日志或断言；D-01~D-07 属“纯死代码”，应删除；D-13 属一致性债务；D-14/D-15 属模型冗余。

## 6. 尚未覆盖 / 未知项

1. **动态行为未验证**：本轮纯静态。`BluetoothLeScanner.startScan/stopScan/flushPendingScanResults` 的真实阻塞时长、`onScanFailed` 在某些 ROM 是否上报，只能在设备上复现（BLE-03、BLE-10 的真实影响需真机/多 ROM 验证）。
2. **`scan()`/`stopScan()` 竞态的可复现窗口**未量化：需 instrumentation 并发压测确认窗口概率与用户可感知程度（BLE-01）。
3. **API 版本事实待核实**：`BluetoothDevice#getAddressType()` 的实际引入 API（33 还是 35）未在本仓库 compileSdk/文档中确认，BLE-13 的“过度保守”结论以此为条件。
4. **连接/GATT/传输/配对/外设内部细节**本轮仅做边界与关键接口抽样（`YoBleConnectionManager`、`YoBleGattSession`、`YoBleGattOperationQueue`、`YoBleAndroidConnectionPlatform`、`YoBleAndroidGattCallbackBridge`、`YoBleL2capManager` 头部）。其重连退避、可靠写、通知路由、L2CAP 读循环、外设 GATT Server 的完整缺陷面留待后续轮次。
5. **`YoBlePeripheralManager` / `YoBleL2capManager` / `YoBleBondController` 的 `onAdapterTurnedOff` 与 release 细节**未逐行审计（仅确认存在且经 `runtime.state` 投递）。
6. **`YoBleScanRecordParser` 的极端/恶意 AD 载荷边界**（超长、嵌套重复、同厂商 ID 多段合并）已按代码审查未见越界，但未做 fuzz。
7. **测试覆盖未知**：`lib_bluetooth/src/androidTest` 未见 BLE 扫描专门用例（本轮未展开）。

## 7. 扫描 / 连接边界结论（补充第 6 项重点）

扫描子包与连接/GATT/通道/传输/配对/外设子包 **边界清晰**：
- `YoBleScanController` 仅依赖 `YoBleCapabilityInspector`、`YoBleDeviceSnapshotStore`、`YoBleTimelineRecorder`、`YoBleErrorMapper`、`YoBleRuntime`、`YoBleScanPlatform`（import 6 条，`YoBleScanController.java:6-11`）；不 import `connection/gatt/channel/transfer/pairing/peripheral`。
- 连接侧 `YoBleConnectionManager`/`YoBleGattSession` 不 import 扫描包；二者仅通过 `YoBleModuleCore` 组装为兄弟组件（`YoBleModuleCore.java:82-92`）。
- 唯一共享点为 `YoBleRuntime`（同一 state/io/callback/scheduler 四上下文）、`YoBleDeviceSnapshotStore`（synchronized 聚合快照）、`YoBleTimelineRecorder`——均为“共享基础设施”而非“逻辑耦合”，符合分层设计。
- 需留意：BLE-03 的“state 线程被扫描 binder 调用阻塞”会同时拖慢连接/GATT（共享 state 线程），这是**共享 runtime 的副作用**，建议以 BLE-03 处置缓解。
