# BLE 扫描模块 · 逐项深度审查与架构设计（非简化版）

> 日期：2026-11-10
> 范围：`lib_bluetooth` 扫描子系统（平台/控制器/过滤/仿真/限流/快照/诊断/模型）+ `biz_ggec_hs` 广播解码 + `app` 扫描 MVVM。
> 参考：nRF Connect / Nordic `Android-BLE-Library`、`RxAndroidBle`（代码已拉取 `E:\System\Temp\ble-ref`）。
> 约定：每项含【现状(证据 file:line)】【问题(级别)】【逐项架构设计】；✅=本轮已实现，⏳=设计完成待实现。

---

## 0. 线程模型基线（所有项的前提）
- `YoBleRuntime`（`internal/ble/runtime/YoBleRuntime.java`）四上下文：`state()` 单线程串行（领域状态唯一写者）、`callback()` 用户回调（主线程或独立回调线程）、`io()` 阻塞 IO、`scheduler()` 定时器回 state。
- 扫描控制器所有可变状态只在 state 线程（`YoBleScanController` 注释 + 使用约定）；**BLE-03 后**平台 `start/stop/flush` 改到 `yo-ble-scan-platform` 单线程（`platformExecutor`），结果回投 state。
- 回调到 UI 后由 `ScanViewModel` 的独立 coalescer 线程/主线程分发。

---

## 1. 平台适配层

### 1.1 `YoBleAndroidScanPlatform`（`internal/ble/internal/android/YoBleAndroidScanPlatform.java`）
- 现状：`startPlatformScan`（`:56-141`）`synchronized(scanLock)` 内 `stopPlatformScanLocked()`→生成 `gen`→`YoBleScanCompat.toAndroidScanSettings/Filters`→`startScan`；回调 `onScanResult/onBatchScanResults/onScanFailed` 用 `gen/activeCallback` 代数隔离（`:76-110`）。`IllegalArgumentException` 时回退 `toFallbackScanSettings`（`:123-134`）。`stopPlatformScan`（`:160-180`）代数自增 + `stopScan`。
- 问题：
  - P2 `scanLock` 实际只在单一平台线程（BLE-03 后）使用，锁冗余（D-08）。
  - P2 默认地址兜底 `"00:00:00:00:00:00"`（见 1.2）。
  - P2 `onScanFailed` 仅有 rawCode→`YoBleScanAdvertiseStatus`，无失败历史/统计。
- 设计：平台层保持"纯适配"，只做 `ScanSettings/Filter/ScanResult` 转换与代数隔离；失败统一转 `BleFailure`；地址未知用**独立未知标识**（含来源计数）而非全零 MAC；平台线程单一即可去掉 `scanLock` 或加 `checkStateThread` 断言。

### 1.2 `YoBleScanCompat`（`internal/ble/internal/android/compat/YoBleScanCompat.java`）
- 现状：`toAndroidScanSettings`（`:41-60`）设置 scanMode、**callbackType 固定 ALL_MATCHES**（`:44`，依 emulator 仿真）、matchMode、numOfMatches、`reportDelay`、legacy、phy；`toFallbackScanSettings` reportDelay=0（`:63-69`）；`toAndroidScanFilters`（`:71-126`）下推 native filter（deviceNameContains 不下推，`:82-86`）；`toBleScanResult`（`:128-200`）解析 AD/身份/PHY/txPower/sid。
- 问题：
  - ✅（本轮修复）地址为空时原用 `"00:00:00:00:00:00"`（`:133`）→ 多台异常设备合并同一 deviceId（BLE-09）；现按 `System.identityHashCode(dev)` 生成 `02:xx` 本地占位地址，设备间可区分（`YoBleScanCompat.toBleScanResult`）。
  - P2 `resolveAddressType` 仅 API35+（`:203-221`，BLE-13 过度保守）。
  - P2 callbackType 恒 ALL_MATCHES 而把 FIRST/LOST 交给 emulator；若上层直接依赖原生 callbackType 会有偏差（但本仓统一走 emulator，可接受，需文档化）。
- 设计：未知地址用带来源的独立 ID；`getAddressType` 按实际引入 API 下调；callbackType 语义统一由 emulator 负责并在文档标注“原生恒 ALL_MATCHES”。

### 1.3 `YoBleScanRecordParser`
- 现状：解析 AD TLV → `BleScanRecord`（serviceUuids/serviceData/manufacturerData/deviceName/flags/txPower/raw）。
- 问题：P3 极端/恶意载荷未 fuzz（本轮静态未见越界）。
- 设计：加入长度上界与异常降级（返回 empty + 记录），保持纯函数可测。

---

## 2. 核心控制器 `YoBleScanController`（`internal/ble/scan/YoBleScanController.java`）

### 2.1 状态与字段（现状）
`scanning / pendingStart / startInFlight / activeGeneration / activeConfig / activeObserver / softwareBatching / 5 类定时句柄 / throttleGuard / callbackEmulator / platformExecutor`。✅ 本轮新增显式 `ScanState{IDLE,STARTING,SCANNING,STOPPING,FAILED,THROTTLED_WAIT}` + `state()`（由 `scanning/pendingStart/startInFlight/stopping/lastStartFailed` 派生）。

### 2.2 生命周期方法
- `scan()`（`:93-114`）：运行期关闭→直接失败（BLE-02 ✅）；`lastStartFailed=false`；`startInFlight=true`；`state(startScanInternal)`。
- `startScanInternal`（`:178-231`）：released/preflight/FAIL_FAST/throttle 判定→`stopActiveInternal(scanning)`→设置 active*→延迟或 `launchPlatformScan`。
- `launchPlatformScan`（`:239-…`）：平台任务投递到 `platformExecutor`（BLE-03 ✅），结果回 `onPlatformStartResult`。
- `onPlatformStartResult`：成功→`scanning=true`/定时器/onScanStarted；失败→复位+失败回调；过期→自停平台。
- `stopScan()`（`:116-128`）→`stopActiveInternal`；`stopActiveInternal`（约 `:520+`）：`stopping=true`→取消定时→`activeGeneration=-1`→enqueue 平台 stop→onScanStopped→`stopping=false`。
- `handlePlatformResult/Batch`（`:390-433`）：过滤→`snapshotStore.recordScanResult`→`callbackEmulator.onResult`→逐条 `observer.onDeviceFound`。
- `handlePlatformFailure`（`:435-450`）：失败→`stopActiveInternal(false)`→`onScanFailed`。
- `onAdapterTurnedOff`（`:162-176`）：中途中止+失败回调。
- `release()`：state 内 stop + `platformExecutor.shutdown()`。

### 2.3 问题
- P2 `stopActiveInternal` 用 `activeGeneration=-1` 让在途 start 失效（新引入的正确防御），但 `stopGeneration(generation)` 若在 -1 后调用会 no-op——已由 design 明确，需补注释/测试覆盖“停止后 late-start 自停”。
- P2 长扫 `restartLongScan`（`:336-`）用 platform stop+start，无 backoff；重连抖动期可能频繁重启（弱 ROM）。
- P2 `softwareBatching` 仅当 `reportDelay>0 && !hardwareBatching`；本轮 reportDelay=0 → 恒 false（硬件批量也关闭），保证逐条；但也意味着放弃硬件批量省电（调试场景可接受，量产可再评估）。
- P3 `matchLostTimeoutMillis` 默认值需确认（若 0 且启用 MATCH_LOST 会立即判丢）。

### 2.4 逐项架构设计（状态机）
| 事件\状态 | IDLE | STARTING | SCANNING | THROTTLED_WAIT | STOPPING | FAILED |
|---|---|---|---|---|---|---|
| scan() | →STARTING | 忽略/拒绝 | 拒绝(已扫) | 忽略 | 拒绝 | →STARTING |
| platform start ok | — | →SCANNING | — | — | →（自停）| — |
| platform start fail | — | →FAILED | — | — | — | — |
| 限流延迟 | →THROTTLED_WAIT | — | — | →STARTING(timer) | — | — |
| stopScan/timeout | — | →STOPPING | →STOPPING | →STOPPING | — | — |
| adapter off | — | →FAILED | →FAILED | →FAILED | — | — |
| 长扫 restart | — | — | →SCANNING(内部) | — | — | — |

不变量：① `scanning=true ⇒ activeConfig/activeObserver≠null`；② 任意时刻最多一个 `activeScanHandle`（平台层）；③ `activeGeneration` 非当前时所有平台回调/结果丢弃；④ 定时器与 state 线程一一对应，停止即清空。

设计要点：将上表落为显式 `enum` switch（当前以派生 `state()` 先落地可观测，后续把迁移写成单一 `transition(event)` 方法，禁止多路直接改布尔），并补穷举单测（每事件×每状态断言）。

---

## 3. 过滤子系统

### 3.1 `YoBleScanFilterMatcher`（`internal/ble/scan/YoBleScanFilterMatcher.java`）
- 现状：`matchesConfig`（`:22-37`，requireConnectable + filter OR）；`matchesFilter`（`:39-88`，address/name/nameContains/serviceUuid(+mask)/solicitation/serviceData(+mask)/manufacturer(+mask)/minRssi）；掩码工具（`:100-153`）。
- 问题：
  - ✅（本轮修复）`matchesMaskedBytes`（`:127-153`）原未校验 `mask.length >= expected.length`：短 mask 会 `ArrayIndexOutOfBounds`。已加“短 mask → 不匹配”保护（`YoBleScanFilterMatcher.java`），并新增 `YoBleScanFilterMatcherTest`（含短 mask 不崩溃用例）。
  - P2 `requireConnectable` 在 config 与 filter 两级重复（BLE-15）。
- 设计：掩码长度校验（短则视为不匹配/降级并记录）；明确 config.requireConnectable 优先/合并语义；**双层过滤**：native filter 下推（能下推的）+ 软件复核（nameContains/mask 兼容），与 Rx `EmulatedScanFilterMatcher` 对齐。

### 3.2 `BleScanFilter`（`model/ble/BleScanFilter.java`）
- 现状：字段见 `:16-43`；Builder 已在 `serviceData(...)`（`:243-245`）与 `manufacturerData(...)`（`:265-267`）强制 **mask 与 data 等长**（不满足抛 `IllegalArgumentException`）。
- 说明：因此 §3.1 中 `matchesMaskedBytes` 的短 mask 保护属**纵深防御**（防外部其它构造路径/将来变更），并非 Builder 缺校验。
- 设计：保持 Builder 强校验；文档化 AND(单 filter)/OR(多 filter) 语义。

---

## 4. 回调类型/去重仿真 `YoBleScanCallbackEmulator`（`internal/ble/scan/YoBleScanCallbackEmulator.java`）
- 现状：`onResult`（`:49-97`）维护 `Map<BleDeviceId,SeenEntry>`；ALL_MATCHES 按 `deduplicationWindowMillis` 去重（0=每次都发，实时）；FIRST/FIRST_AND_LOST 只首次；MATCH_LOST 不 found；`sweepLost`（`:103-122`）按 `matchLostTimeoutMillis` 生成 lost 并移除；`MAX_SEEN=256`（`:35`）按插入序驱逐。
- 问题：
  - P2 `MAX_SEEN` 按插入序驱逐活跃设备 → 设备>256 时活跃设备被逐出，下条结果再判 FIRST_MATCH（BLE-11）。
  - P2 无“设备聚合模型”（仅保留 lastResult），上层需自己合并（App 侧 `ScanViewModel` 做）。
- 设计：`ScanDevice` 聚合下沉 lib（见 §6），驱逐改 LRU(按 lastSeen)；去重窗口沿用；sweepLost 产生 `onScanResultLost`（nRF 式过期移除）。

---

## 5. 限流与前置校验
### 5.1 `YoBleScanThrottleGuard`（`internal/ble/scan/YoBleScanThrottleGuard.java`）
- 现状：30s/5 次滑窗（`:31-53`），`requiredDelayMillis()` 返回等待；`recordScanStarted()`。对标 Rx `ScanPreconditionsVerifierApi24`（30s/5）。
- 问题：P3 无“解锁时间点”回传给调用方（Rx 抛 `BleScanException(UNDOCUMENTED_SCAN_THROTTLE, date)`）。
- 设计：错误里带 `unlockAt`（`ScanFailure` 增加可选时间），便于 UI 提示“请 N 秒后重试”。

### 5.2 `checkPreflight`（`YoBleScanController:…`）
- 现状：BLE 支持/适配器开关/扫描权限/（<31 或 locationExempt=false）位置权限+位置服务。
- 问题：P2 `locationExempt` 默认 true 可能跳过位置校验（BLE-07）。
- 设计：默认按 Manifest neverForLocation 显式声明；文档标注风险。

---

## 6. 设备聚合与快照
### 6.1 `YoBleDeviceSnapshotStore`（`internal/ble/device/YoBleDeviceSnapshotStore.java`）
- 现状：`recordScanResult`（synchronized，`getOrCreate`+`mergeIdentity`+lastScanResult+updatedAt）；提供 getDeviceSnapshot(s) 同步读。
- 问题：P2 只有 lastScanResult，无 firstSeen/lastSeen/出现次数/去重合并策略；与 emulator 的 seen 表重复维护。
- 设计：统一为 lib 内 `ScanDevice` 聚合（address→{firstSeen,lastSeen,rssi,name,record,connectable,bond,advType,count}），emulator 与 snapshotStore 共用，避免双份状态。

---

## 7. 诊断 `YoBleTimelineRecorder` / `BluetoothDiagnosticsBus`
- 现状：环形时间线（`YoBleTimelineRecorder`），记录 scan started/stopped/preflight/throttle/restart；`BluetoothDiagnosticsBus` 失败/异常单出口（进程单例，CBT/BLE 共享，BLE-04/CBT-04 诊断跨域风险）。
- 问题：P2 无每设备/每次扫描统计（found 数、丢帧、耗时）；诊断单例跨域。
- 设计：扫描会话统计对象（started/found/updated/lost/failures/duration），导出到 `exportDiagnosticReport`；总线按 domain 分桶。

---

## 8. 数据模型（逐字段）
### 8.1 `BleScanConfig`（`model/ble/BleScanConfig.java:15-114`）
`timeoutMillis / requireConnectable / scanMode / callbackType / matchMode / numOfMatches / reportDelayMillis / legacyOnly / phy / locationExempt / deduplicationWindowMillis / matchLostTimeoutMillis / throttlePolicy / filters`。
- 设计：语义保留；补充“reportDelay=0 实时”与“numOfMatches/matchMode 仅原生支持时生效”的文档；`callbackType` 语义统一由 emulator 负责。

### 8.2 `BleScanResult`（`model/ble/BleScanResult.java:16-32`）
`identity / rssi / scanRecord / observedAtMillis / connectable / legacy / primaryPhy / secondaryPhy / advertisingSid / txPower / periodicAdvertisingIntervalUnits / callbackType`。
- 问题：P2 `callbackType` 属分发语义混入值对象（BLE-08）；`observedAtMillis` 与 `identity.updatedAtMillis` 重复。
- 设计：`callbackType` 移出值对象（仅作回调参数）；时间戳单一来源。

### 8.3 `BleScanRecord`
AD 解析结果（flags/serviceUuids/serviceData/manufacturerData/txPowerLevel/deviceName/raw）。设计：保持不可变 VO；对超长/重复 AD 做确定性合并规则并文档化。

### 8.4 设备聚合 `ScanDevice`（设计新增）
`address, addressType, name, rssi(last), txPower, connectable, legacy, record(last), firstSeenMs, lastSeenMs, seenCount, bond, advType`；`onScanResult` 更新、`isExpired(now,timeout)`。

---

## 8bis. App 侧（扫描 MVVM）
### 8bis.1 `HsDevicePort`（`app/.../provider/lib_bluetooth/HsDevicePort.java`）
- 现状：`startScanBatch` 构造 `BleScanConfig`（ALL_MATCHES / legacyOnly=false / **reportDelay=0 ✅本轮** / timeout 15s / dedupe 0）；回调线程懒加载 BLE 已配对；`dispatchBatch` 逐条/批映射；异常复位；连接走 `YoHsSdk`。
- 问题：P2 无 native filter 下推（filters=0，全量上抛后软件过滤，性能/耗电）；P2 15s 自动停（调试器通常持续扫描到用户停止）。
- 设计：HS01/HS02 native filter 下推 + 软件复核；扫描超时策略可配（默认持续/可设定时）。

### 8bis.2 `HsBroadcastScanMapper` + `HsBroadcastSessionImpl/Hs02BroadcastAssembler`
- 现状：HS01 直接识别→`ScanResult`；HS02 经 `Hs02BroadcastAssembler` 按地址拼 base/supplement（TTL 2s、MAX 256），`HsBroadcastSessionImpl.observe` synchronized。
- 问题：P2 HS02 分片需 base 到齐才可识别（协议固有，非“整周期”）；P2 assembler TTL/容量与 emulator/snapshot 三处各自维护设备表。
- 设计：HS01/HS02 解码保持；将“设备聚合”统一到 §6.1，assembler 只负责同一设备同一次广播的分片组装。

### 8bis.3 `ScanViewModel`（`app/.../ui/device/scan/ScanViewModel.java`）
- 现状：`onScanBatch` 按 `sameDevice(mac+modelId)` 合并、`structuralChanged` 立即发布、RSSI 变化≥3dB 300ms 合并；`selectFilter` 纯 modelId 过滤（与选项解耦 ✅§16）；结果在 startScan 清空。
- 问题：P2 列表在“重新开始扫描”时清空（调试器通常累积）；P2 无“显示 X 秒前/信号条”等调试视图。
- 设计：可选“累积模式”；展示 lastSeen/RSSI 趋势；与 nRF 式“实时条目更新”对齐（已有实时基础）。

---

## 9. 数据链路（逐段，含线程与变换）
1) **配置下行**：`ScanViewModel.startScan → ScanDevices.startBatch → DevicePort.startScanBatch → HsDevicePort(构造 BleScanConfig) → YoBleClient.scan → BleClientImpl.scan → YoBleScanController.scan → state → launchPlatformScan → platformExecutor → YoBleScanCompat → BluetoothLeScanner.startScan`。
2) **每广播上行（实时）**：`ScanCallback.onScanResult → YoBleScanCompat.toBleScanResult → PlatformCallback.onScanResult → state(handlePlatformResult) → FilterMatcher.matchesConfig → snapshotStore.recordScanResult → callbackEmulator.onResult → callback(observer.onDeviceFound) → HsDevicePort.dispatchBatch → HsBroadcastScanMapper.map → ScanViewModel.onScanBatch → uiState.postValue → ScanScreen.render`。
3) **批量上行**：同上但 `onBatchScanResults` 拆条（reportDelay=0 时基本不触发）。
4) **失败**：`onScanFailed → BleErrorCode → handlePlatformFailure/onPlatformStartResult(fail) → ScanFailure → events → ScanActivity Toast`。
5) **超时/停止**：`timeoutHandle → stopGeneration → stopActiveInternal → platform stop → onScanStopped`。
6) **适配器/权限**：`onAdapterTurnedOff → 失败回调`；preflight 权限/位置。
7) **诊断**：timeline/bus。

---

## 10. 对齐业界 Gap 表（逐项设计）
| ID | 项 | 业界 | 现状 | 设计/状态 |
|---|---|---|---|---|
| G1 | 实时逐广播 | reportDelay=0，逐条 onNext | ✅ 本轮 reportDelay=0 | 完成 |
| G2 | 显式状态机 | Stopped/Starting/Scanning/Stopping | ✅ 本轮 `ScanState` 唯一字段 + `enterState` 单写入口（IDLE/STARTING/SCANNING/STOPPING/FAILED/THROTTLED_WAIT） | 完成 |
| G3 | ScanMode/CallbackType 仿真 | Rx ScanSettingsEmulator | callbackType 已仿真；ScanMode 未仿真 | ✅ 本轮：`YoBleScanModeWindows` 重复窗口仿真（LOW_POWER/OPPORTUNISTIC 500ms、BALANCED 2500ms/5s），handlePlatformResult/Batch 生效 |
| G4 | 30s/5 限流 | 有+解锁时间 | 有滑窗 | 失败带 unlockAt |
| G5 | 双层过滤 | native+软件 | 有 matcher，未下推 HS filter | HS01/HS02 native filter 下推 |
| G6 | 设备聚合 | ExtendedDevice | snapshotStore+emulator+VM 三处 | ✅ 本轮：emulator 聚合 firstSeen/lastSeen/seenCount，驱逐改 LRU(BLE-11)，`activeDeviceCount` 入诊断；App 侧去重保留 |
| G7 | MATCH_LOST/过期 | debounce/过期移除 | 有能力未启用(HS 用 ALL_MATCHES) | ✅ 本轮启用 lostSweep（ALL_MATCHES + matchLostTimeout>0）→ `onScanResultLost` → App `ScanViewModel.onScanRemoved` 列表移除 |
| G8 | adapter 自愈 | 关闭中止/开启重启 | 关闭中止，无重启 | ✅ 本轮：`onAdapterTurnedOff` 暂存 config/observer，`onAdapterTurnedOn` 自动恢复；`YoBleModuleCore.onAdapterStateChanged` 挂钩 |
| G9 | 错误分类 | 完整 | ✅ | — |
| G10 | 诊断统计 | 有 | timeline/bus，无会话统计 | ✅ 本轮：`YoBleScanController.Stats(starts/founds/losts/fails/stops/lastMs)`，`exportDiagnosticReport` 写入时间线 |
| G11 | 长扫降级 | 30min 前重启 | ✅ 25min 重启 | backoff 优化 |
| G12 | callbackType 语义 | 完整 | ✅ emulator | — |

---

## 11. 本轮实现与验证
- ✅ G1：`HsDevicePort.SCAN_REPORT_DELAY_MILLIS` 200→0（逐广播实时）；`HsDevicePortTest.configuresBackgroundBleCallbacksAndRealtimeReporting`。
- ✅ G2：`YoBleScanController.ScanState` + `state()`（IDLE/STARTING/SCANNING/STOPPING/FAILED/THROTTLED_WAIT）；新增 `stateMachineReportsScanningThenIdleAfterStop`、`stateMachineReportsFailedOnPlatformStartFailure`。
- 验证：`:lib_bluetooth:testDebugUnitTest` **26 套件 / 107 用例 / 0 失败**；`:app:testDebugUnitTest` **56 套件 / 421 用例 / 0 失败**；真机实时列表 `.rea-captures/round8_rt_t1/screen.png`。

## 12. 后续实现路线（按本表逐项落地，不简化）
- P1：G2 迁移收口已完成（单一 `enterState`）；G6 `ScanDevice` 聚合已下沉 emulator；G7 lostSweep 已启用。
- P1：G5 HS01/HS02 native filter 下推 + 软件复核（维持全量兜底）。
- P2：G3 ScanMode 仿真；G8 adapter 自动重启；G10 扫描会话统计；G4 解锁时间。
- P2：数据模型清理（`callbackType` 出值对象、时间戳单一来源）；地址未知独立标识（BLE-09）；掩码长度校验（3.1）。
