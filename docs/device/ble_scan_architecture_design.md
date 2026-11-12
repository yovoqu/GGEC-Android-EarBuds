# BLE 扫描模块架构设计 · 状态机 · 数据链路（对齐业界优秀项目）

> 日期：2026-11-10
> 目标：审查并重构 BLE 扫描模块，对齐 nRF Connect / Nordic Android-BLE-Library / RxAndroidBle 的扫描架构；整理扫描状态机与全部数据链路。
> 参考代码（本轮已拉取到 `E:\System\Temp\ble-ref`）：
> - Nordic `Android-BLE-Library`（nRF Connect 的 BLE 基础库）：`examples/trivia/.../ScannerRepository.kt`
> - `RxAndroidBle`（dariuszseweryn）：`internal/operations/ScanOperationApi21.java`、`internal/scan/ScanSettingsEmulator.java`、`internal/scan/ScanPreconditionsVerifierApi24.java`、`internal/scan/AndroidScanObjectsConverter.java` 等
> 本文档同时作为实现依据；本轮完成项标记 ✅。

---

## 1. 参考项目的关键扫描设计（提取要点）

### 1.1 Nordic 官方扫描示例（`ScannerRepository.kt`）
- `ScanSettings.Builder().setReportDelay(0)  // Set to 0 to be notified of scan results immediately`（`:47-50`）——**实时逐结果**。
- 通过 `ScanFilter` 下推服务 UUID 到硬件过滤（`:52-56`）；`onScanResult` 收到即处理并 `stopScan`（`:32-37`）。
- 结论：**reportDelay=0 是“实时逐广播”的官方做法**；扫描结果不应依赖批量窗口。

### 1.2 RxAndroidBle（`ScanOperationApi21.java`）
- `onScanResult(callbackType, result)`：逐条 `internalScanResultCreator.create` → `emulatedScanFilterMatcher.matches` → `emitter.onNext`（`ScanOperationApi21.java:67-86`）——**每条广播即时上抛**。
- `onBatchScanResults(results)`：遍历逐条 `onNext`（`:88-99`）——即使厂商批量回调也拆成逐条。
- `onScanFailed(errorCode)` → `BleScanException(errorCodeToBleErrorCode)`（`:101-108`）——错误码统一分类。
- **双层过滤**：native filters 下推 + `EmulatedScanFilterMatcher` 软件复核（`:112-121`）。
- `ScanSettingsEmulator`：当 ROM 不支持某 ScanMode/CallbackType 时用 Rx 窗口仿真（`ScanSettingsEmulator.java:34-220`，如 FIRST_MATCH 10s 窗口、MATCH_LOST 10s debounce、LOW_POWER 500ms/5s、BALANCED 2500ms/5s）。
- `ScanPreconditionsVerifierApi24`：API24+ 未公开的 **30s/5 次 扫描限流**前置校验（`ScanPreconditionsVerifierApi24.java:21-56`）。

### 1.3 业界通用行为（nRF Connect 等调试器）
实时列表：每收到一条广播即更新对应设备条目（名称/RSSI/广播数据/地址/可连接/广播类型），并按地址合并；设备按最近一次广播时间维持/过期（MATCH_LOST/超时移除）；扫描状态（Stopped/Starting/Scanning/Stopping）驱动 UI；适配器关闭/权限/位置服务变化能自愈。

---

## 2. 本仓库现状架构（自底向上）

```
Android BluetoothLeScanner
  └─ YoBleAndroidScanPlatform (internal/ble/internal/android)          // 平台适配：ScanCallback→BleScanResult
       └─ YoBleScanPlatform (端口)  YoBleScanCompat (settings/filter 转换)
            └─ YoBleScanController (internal/ble/scan)                  // 状态机/限流/超时/批量/重启
                 ├─ YoBleScanThrottleGuard (30s/5次滑窗)
                 ├─ YoBleScanCallbackEmulator (FIRST_MATCH/MATCH_LOST 仿真)
                 ├─ YoBleScanFilterMatcher (config/filter 软件复核)
                 ├─ YoBleDeviceSnapshotStore (设备聚合快照)
                 └─ YoBleTimelineRecorder (诊断)
                      └─ BleClientImpl.scan → YoBleClient.scan (api)
                           └─ app: HsDevicePort → HsBroadcastScanMapper → ScanViewModel → ScanScreen
```

关键类型：
- `BleScanConfig`（`model/ble/BleScanConfig.java`）：`timeoutMillis/requireConnectable/scanMode/callbackType/matchMode/numOfMatches/reportDelayMillis/legacyOnly/phy/locationExempt/deduplicationWindowMillis/matchLostTimeoutMillis/throttlePolicy/filters`——设置面已较完整。
- `BleScanResult`（`model/ble/BleScanResult.java`）：`identity/rssi/scanRecord/observedAtMillis/connectable/legacy/primaryPhy/secondaryPhy/advertisingSid/txPower/periodicAdvertisingIntervalUnits/callbackType`。
- `BleScanRecord`：AD 解析后的结构（flags/serviceUuids/serviceData/manufacturerData/txPower/deviceName/raw）。

---

## 3. 当前扫描状态机（隐式，基于字段）

字段（`YoBleScanController.java` 顶部）：`scanning`、`pendingStart`、`startInFlight`、`activeGeneration`、`activeConfig`、`activeObserver`、`softwareBatching`、`timeoutHandle/lostSweepHandle/batchFlushHandle/longScanRestartHandle/delayedStartHandle`。

```
                 scan(config,observer)
   [IDLE] ───────────────────────────────► [PREFLIGHT/THROTTLE]
     ▲                                          │ pass
     │ stop/release/fail                        ▼
     │                                   [STARTING] ──platform start─► [SCANNING]
     │                                          │ fail                    │ timeout(15s)/stop/adaptor-off
     │                                          ▼                         ▼
     └──────────────────────────────────── [FAILED]/[STOPPING] ◄──────────┘
   timeout→stopGeneration→stopActiveInternal；长扫(>25min)自动 restart
```
- 事件：`scan()`（`startInFlight=true`→`state(startScanInternal)`）、`stopScan()`/`stopGeneration`→`stopActiveInternal`、平台 `onPlatformScanFailed`→`handlePlatformFailure`、`onAdapterTurnedOff`、`release()`。
- BLE-03 后：平台 `start/stop/flush` 在专用 `yo-ble-scan-platform` 线程执行，结果回投 state。

> 现存问题：状态是**多个布尔/句柄隐式组合**，缺少显式 `ScanState` 枚举，难穷举与测试；这是对齐业界的主要差距之一。

---

## 4. 当前数据链路（逐段）

**配置下行**：`HsDevicePort.startScanBatch` 构造 `BleScanConfig`（`callbackType=ALL_MATCHES`、`legacyOnly=false`、`reportDelayMillis=0`(本轮改)、`timeoutMillis=15000`）→ `YoBleClient.scan` → `BleClientImpl.scan` → `YoBleScanController.scan` → `launchPlatformScan`（platform 线程）→ `YoBleScanCompat.toAndroidScanSettings/Filters` → `BluetoothLeScanner.startScan`。

**结果上行（每广播）**：`ScanCallback.onScanResult`（`YoBleAndroidScanPlatform.java:76-83`）→ `YoBleScanCompat.toBleScanResult` → `PlatformCallback.onScanResult` → `runtime.state(handlePlatformResult)`（`YoBleScanController:390-410`）→ `YoBleScanFilterMatcher.matchesConfig` → `snapshotStore.recordScanResult` → `callbackEmulator.onResult` → `runtime.callback(observer.onDeviceFound)` → `HsDevicePort.dispatchBatch`（`HsBroadcastScanMapper.map`→`ScanResult`）→ `ScanViewModel.onScanBatch`（去重/合并/节流）→ `uiState.postValue` → `ScanScreen.render`（RecyclerView DiffUtil）。

**失败/超时/停止**：`onScanFailed`→`BleErrorCode`映射→`ScanFailure`；`timeoutHandle`→`stopGeneration`→`onScanStopped`；`stopScan`→`stopActiveInternal`；`onAdapterTurnedOff`→失败回调。

---

## 5. 对齐差距分析（Gap Analysis）

| # | 维度 | 业界做法 | 本仓库现状 | 状态 |
|---|---|---|---|---|
| G1 | 实时逐广播 | reportDelay=0，每条即上抛（Nordic/Rx） | 原 200ms 批量 → **已改 0** | ✅ 本轮 |
| G2 | 显式扫描状态机 | Stopped/Starting/Scanning/Stopping（UI 稳定态） | 隐式布尔/句柄组合 | ⏳ 建议 |
| G3 | ScanMode/CallbackType 仿真 | Rx `ScanSettingsEmulator` 窗口仿真 | 未做（依赖 ROM 原生；ROM 不支持退化） | ✅ 本轮：`YoBleScanModeWindows`（LOW_POWER/OPPORTUNISTIC 500ms、BALANCED 2500ms / 5s；LOW_LATENCY 不限）在 handlePlatformResult/Batch 生效 |
| G4 | 前置限流校验 | 30s/5次 显式校验+错误日期 | `YoBleScanThrottleGuard` 有 30s/5 滑窗，但无“解锁时间”回传 | 部分 |
| G5 | 双层过滤 | native filter 下推 + 软件复核 | 有 `BleScanFilter`+`YoBleScanFilterMatcher`，但 HS 端口未下推 native filter（filters=0，全量上抛后软件过滤） | 部分：HS01 UUID 含 FeatureCode 无法静态下推；仅 HS02 可按 0xFFFF+H​C 可选下推，软件过滤为主 |
| G6 | 设备合并缓存 | ExtendedDevice(address→最近 record/rssi/name/lastSeen) | `YoBleDeviceSnapshotStore` 有聚合；App 侧 `ScanViewModel` 去重合并 | ✅ 本轮：`YoBleScanCallbackEmulator` 聚合下沉（firstSeen/lastSeen/seenCount，LRU 驱逐 BLE-11），`activeDeviceCount` 入诊断 |
| G7 | MATCH_LOST/过期 | 10s debounce 生成 lost / 列表过期 | `BleScanCallbackEmulator.sweepLost` + `matchLostTimeoutMillis` 有能力，HS 端口未启用 | ✅ 本轮启用：ALL_MATCHES + matchLostTimeout>0 也 sweep；`onScanResultLost`→App 列表移除 |
| G8 | 适配器/位置/权限自愈 | adapter off 中止并可重启；位置服务校验 | 有 preflight + `onAdapterTurnedOff`；无自动重启 | ✅ 本轮：`onAdapterTurnedOff` 暂存配置，`onAdapterTurnedOn` 自动恢复扫描（YoBleModuleCore 挂钩） |
| G9 | 错误码分类 | 完整 BleScanException 原因 | `YoBleScanAdvertiseStatus.toScanErrorCode` 有分类 | ✅ |
| G10 | 诊断/时间线 | 结构化日志/统计 | `YoBleTimelineRecorder`、`BluetoothDiagnosticsBus` | ✅ 本轮新增扫描会话统计（`YoBleScanController.Stats` 并入 `exportDiagnosticReport` 时间线） |
| G11 | 扫描保活/长扫降级 | 30min opportunistic 前重启 | `LONG_SCAN_RESTART_MS=25min` 重启 | ✅ |
| G12 | `callbackType` 语义 | FIRST_MATCH/ALL_MATCHES/MATCH_LOST | `YoBleScanCallbackEmulator` 仿真 | ✅ |

---

## 6. 目标架构设计

### 6.1 显式扫描状态机（推荐实现）
```
enum ScanState { IDLE, STARTING, SCANNING, STOPPING, FAILED, THROTTLED_WAIT }
```
迁移（单线程 state 线程）：
- `scan()`：IDLE/FAILED → 校验 → STARTING（或 THROTTLED_WAIT→延迟 STARTING）→（platform 成功）SCANNING /（失败）FAILED。
- `stopScan()/timeout/adapter-off`：SCANNING/STARTING → STOPPING → IDLE（adapter-off → FAILED）。
- 长扫：SCANNING →（内部 restart）SCANNING（不对外暴露抖动，仅保持 SCANNING）。
- 对外 `isScanning = (STARTING||SCANNING||STOPPING)`，`onScanStarted` 仅 STARTING→SCANNING 一次。

### 6.2 事件驱动 + 实时上抛
- 平台 `onScanResult` → state 线程 → 过滤 → `ScanDevice` 合并更新 → **立即** `onScanResult(result)` 回调（reportDelay=0）。
- 每设备维护 `ScanDevice{ address, name, rssi, firstSeenMs, lastSeenMs, bond, connectable, record, advType }`；`onScanResult` 每次更新 `lastSeen/rssi/name`。
- 定时 `lostSweep`：`now-lastSeen > matchLostTimeout` → `onScanResultLost(ScanDevice)` 并从活动集移除（可选保留历史）。

### 6.3 设置与过滤
- `ScanSettings` 显式建模（scanMode/callbackType/matchMode/numOfMatches/reportDelay/legacy/phy/throttle/matchLost）。
- 双过滤：native `ScanFilter` 下推 ＋ `EmulatedScanFilterMatcher` 软件复核。
- **可行性修正（重要）**：HS01 的 128-bit Service UUID 内嵌 `FeatureCode`（每台不同，见 `Hs01BroadcastDecoder`/`HsBroadcastScanMapper`），**无法用静态 UUID 做 native 过滤**；HS02 可按 0xFFFF 厂商 ID + `"HC"` 前缀下推 native filter。因此 **软件过滤为主**，native 过滤仅作为“不得排除 HS01”的可选优化（如需下推，只能在已知仅扫 HS02 的场景启用）。

### 6.4 数据链路（目标）
```
start(settings) ──► [state] IDLE→STARTING ──platform(start)──► SCANNING
platform.onScanResult ─► [state] filter → ScanDevice.merge → onScanResult(实时)
lostSweep ─► onScanResultLost
fail/timeout/stop ─► STOPPING/FAILED → onScanStopped/onScanFailed
adapter off/on ─► 中止；on 后按 last settings 自动重启（可选）
```

---

## 7. 本轮代码变更与证据（G1 实时）

| 变更 | 文件 | 说明 |
|---|---|---|
| 扫描上报延迟 200ms → **0** | `app/.../provider/lib_bluetooth/HsDevicePort.java`（`SCAN_REPORT_DELAY_MILLIS=0L`） | `reportDelay=0` → `BluetoothLeScanner` 每条广播 `onScanResult` 即时回调；`YoBleScanController` 不再走软件批量窗口 |
| 单测断言更新 | `HsDevicePortTest.configuresBackgroundBleCallbacksAndRealtimeReporting` | 断言 `SCAN_REPORT_DELAY_MILLIS==0` |
| 真机验证 | motorola edge 60 pro / API35，APK sha `afc074c6…` | 扫描进行中（"正在搜索附近的耳机…/停止搜索"）即已列出 4 台 HS01（`.rea-captures/round8_rt_t1/screen.png`）；`[ble-scan] scan started gen=1` 后 15s `scan stopped`，无 FATAL |

> 数据链路保证：`ScanCallback.onScanResult`（逐条）→ `handlePlatformResult`（逐条）→ `observer.onDeviceFound`（逐条）→ `HsDevicePort.dispatchBatch`（单元素批次）→ `ScanViewModel` 新设备立即发布（RSSI-only 变化才 300ms 节流）。故“过滤后逐设备实时出现”。

---

## 8. 迁移路线（后续轮次）
- P1：引入显式 `ScanState` 枚举替换隐式布尔（G2），补状态机穷举单测。
- P1：设备合并缓存下沉到 lib（`ScanDevice` + MATCH_LOST 过期，G6/G7），App 侧只做投影。
- P2：ScanMode/CallbackType 软件仿真（G3，参照 `ScanSettingsEmulator`）。
- P2：HS01/HS02 native filter 下推 + 软件复核（G5）。
- P2：适配器 on 后按上次配置自动重启（G8）。
- P3：后台/前台扫描策略（nRF 式）、导出诊断。
