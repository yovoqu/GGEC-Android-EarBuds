# BLE 扫描模块架构与状态机设计（本轮落地版）

> 依据 Antigravity 会话《BLE Scan Architecture Review》的完整要求整理；本文件记录三层自底向上架构、扫描状态机、已落地修复与验证证据，以及待真机闭环的残余项。

## 1. 目标与硬约束

- 设备模块自底向上：`lib_bluetooth`（BLE 协议栈）→ `biz_ggec_hs`（HS01/HS02 广播与命令协议）→ `app`（Domain/Provider/UI）。
- 扫描不得卡死；可正常切换扫描设备类型（全部 / HS01 / HS02）；扫描出设备时页面不得卡顿。
- HS01/HS02 仅走 BLE；`ble` 与 `classic(cbt)` 严禁耦合（源码级 0 跨 import 已核验）。
- 严格 MVVM 与 Gradle 边界；清理冗余补丁与防御性代码。
- 每个问题/修改需 file:line 证据链 + Gradle 校验 + 单测 + 真机前后对比。

## 2. 三层数据链路（自底向上）

```
[Android BLE 系统服务 / Binder]
  → YoBleAndroidScanPlatform.ScanCallback.onScanResult          (主线程回调)
      → (本轮新增) yo-ble-scan-parse 线程：YoBleScanCompat.toBleScanResult
          · AD TLV 解析(YoBleScanRecordParser) → BleScanRecord
          · 名称优先取 AD Local Name；无则 dev.getName()
      → YoBleRuntime.state 线程：YoBleScanController.handlePlatformResult
          · withinScanModeWindow / YoBleScanFilterMatcher.matchesConfig
          · YoBleDeviceSnapshotStore.recordScanResult
          · YoBleScanCallbackEmulator.onResult (FIRST_MATCH/MATCH_LOST/去重)
      → yo-ble-callback 线程：BleScanObserver.onDeviceFound / onBatchScanResults
[biz_ggec_hs]
  YoHsNearby(HsNearbyScanner 门面) → HsNearbyScanSession(会话代次/订阅句柄)
      → HsNearbyScanCallbacks(代次放行 + 回调翻译) → HsNearbyDevices(帧→附近设备)
      → HsScanSnapshots.from(BleScanRecord) → YoHsBroadcast.classify / 会话组装
      → HsNearbyScanLedger(lost 记账) / HsScanFailures(错误映射) → HsNearbyObserver
[app]
  HsDevicePort(投影 HsNearbyDevice→领域 ScanResult) → ScanDevices(batch) → ScanViewModel(观察表)
      → ScanUiMapper → ScanUiState(ScanItem) → ScanScreen(DiffUtil + payload) → ScanDeviceCard
```

### 数据模型归属与防泄漏
- `lib_bluetooth.model.ble.*` 只表达 Android 扫描事实；`BleScanRecord` 为不可变 VO，新增 `rawBytesView()/getServiceDataView()/getManufacturerSpecificDataView()` 只读视图供库内高频只读路径使用（调用方严禁修改），`rawBytes()` 保持防御性拷贝。
- `biz_ggec_hs.model.broadcast.*` 只含纯 Java 类型（String/UUID/byte[]），不引用 `lib_bluetooth`。`HsScanSnapshot` 只保留 `rawAdvertisingView()` 作为广播字节的唯一取值入口（调用方严禁修改），跨模块需要副本时走 `lib_bluetooth` 的 `rawBytes()`。
- `app.domain.device.*` 零 Android/UI 依赖；UI 零 `data` 依赖。

## 3. BLE 扫描状态机（YoBleScanController）

状态：`IDLE / STARTING / THROTTLED_WAIT / SCANNING / STOPPING / FAILED`。

| 起始 | 事件 | 目标 | 说明 |
|---|---|---|---|
| IDLE | scan() | STARTING | 受理后 state 线程内进入 |
| STARTING | preflight 失败 | FAILED | 回 onScanFailed |
| STARTING | 节流 DELAY | THROTTLED_WAIT | 到点再 launch |
| STARTING/THROTTLED_WAIT | launch 成功 | SCANNING | 记录 statStarts |
| STARTING/THROTTLED_WAIT | launch 失败 | FAILED | 回失败 |
| SCANNING | stopScan/timeout | STOPPING → IDLE | 回 onScanStopped |
| STARTING/THROTTLED_WAIT | stopScan | IDLE | **本轮修复：不再卡死** |
| SCANNING | adapter OFF | FAILED | 暂存 resume，回 BLUETOOTH_DISABLED |
| FAILED | adapter ON（有 resume） | STARTING | 自动恢复 |
| SCANNING | 25min 长扫描 | SCANNING | 透明重启 |

不变量：
1. 所有状态/会话字段写入只在 `yo-ble-state` 单线程（新增 `runtime.checkStateThread()` 断言）。
2. `activeGeneration` 代次栅栏：过期平台回调一律丢弃；停扫置 -1。
3. 有实际会话（SCANNING / STARTING 已设置 observer / THROTTLED_WAIT）时必须收敛到 IDLE 并回调终态。
4. `lostSweep` 仅在 `activeDeviceCount()>0` 时轮询（对齐 Nordic），空集停表。

## 4. 已落地修复（本仓库工作区）

### lib_bluetooth
- `YoBleScanCompat.toAndroidScanFilters`：修复**空过滤器陷阱**——纯软件过滤（`minRssi`/`deviceNameContains`/`requireConnectable`）不再下推空 `ScanFilter`（AOSP 空 filter 匹配全部、会废掉同组原生过滤）。
- `YoBleScanCompat.toBleScanResult`：优先取 AD Local Name，命中即跳过 `dev.getName()` 同步 Binder IPC。
- `YoBleAndroidScanPlatform`：新增 `yo-ble-scan-parse` 单线程，把 `toBleScanResult`（AD 解析 + 4 个同步 Binder IPC）移出 MainLooper。这是真机 trace 中 **14.4s 单帧卡死 / Skipped 1433 frames** 的直接根因。
- `BleScanRecord`：`empty()` 单例；新增只读视图访问器，过滤热路径去逐包 clone。
- `YoBleScanController`：`stopActiveInternal` 对 STARTING/THROTTLED_WAIT/SCANNING 均收敛 IDLE 并补发 onScanStopped；`scan()` 状态写入收敛到 state 线程；补 `checkStateThread()`；`lostSweep` 空集停表；`onAdapterTurnedOff` 覆盖中间态。

### biz_ggec_hs
- `HsScanSnapshot.rawAdvertisingView()` + `HsScanSnapshots`/`Hs02BroadcastAssembler`/`Hs02BroadcastDecoder` 改用只读视图，减少广播解码 `byte[]` clone。
- `Hs02BroadcastDecoder.containsHs02Segment()` **零分配快速判定**（仅偏移遍历原始 AD，不复制、不建对象）：`claim()` 在无 `0xFFFF "HC"` 厂商段时立即 `unrecognized`，`Hs02BroadcastAssembler.fold()` 在“无历史建档且无 HS02 段”时直接透传。环境非 HS02 广播每包 AD 解码次数由 **2 次降为 0 次**，HS 包单次解析。
- `YoBleCommandLink`：`listener` 改 `volatile`；`write()` 的 `onStarted()` 提到写之前，修复同步回调导致的回调顺序违约。

### app
- `ScanDeviceCard`：`name/detail` 宽度 `WRAP_CONTENT→MATCH_PARENT`，RSSI 变化不再触发逐项 `requestLayout`；新增 `bindRssi()` 轻量局部刷新。
- `ScanScreen`：`DiffUtil.ItemCallback.getChangePayload` + 局部 `onBindViewHolder(payloads)`；`recyclerView.setHasFixedSize(true)`。
- `ScanViewModel`：修复 `if (nonRssiChanged || structuralChanged)` 批量误伤（<3dB 抖动不再被同批新设备连带覆写）；`ScanUiMapper` 去掉与 `ScanUiState` 构造器的双重 `List.copyOf`。
- `HsDevicePort`：`lastMappedByAddress` 键由 `mac` 改为 `mac|modelId`（避免同 MAC 的 HS01/HS02 互相覆盖）；`dispatchRemoved` 支持移除同 MAC 的全部领域项；`targetModelId/VariantId/Mac` 与连接代次同锁写入，`publish()` 同锁快照后回调；新增 `release()`（停扫 + 关 `YoHsClient` + `bleClient.release()`），`DeviceGraph.release()` / `AppContainer.release()` / `EarbudsApplication.onTerminate()` 接线。

## 5. 业界基准对齐

| 维度 | Nordic ScannerCompat | Kotlin-BLE-Library | RxAndroidBle | 本仓库本轮 |
|---|---|---|---|---|
| 回调线程 | 专用 handler；热路径不取 name/alias/type | startScan 走 IO 线程；Flow buffer 背压 | 懒包装原生 ScanRecord | 转换移出 MainLooper（yo-ble-scan-parse） |
| 过滤 | 硬件下推 + 软件兜底 | 同 | HW + SW 双方案 | 修空过滤器陷阱；软件匹配保留 |
| Binder IPC | 热路径 0 次 getName | `scanRecord.deviceName ?: device.name` | 懒包装 | AD 名优先，跳过 getName |
| matchLost | 有设备时才调度 | — | — | 仅 activeDeviceCount>0 轮询 |
| UI | DiffUtil + ViewModel | Compose/Flow | 示例 | DiffUtil payload + 固定宽 + hasFixedSize |

## 6. 验证证据

- `:lib_bluetooth:compileDebugJavaWithJavac` + `:lib_bluetooth:testDebugUnitTest` → BUILD SUCCESSFUL
- `:biz_ggec_hs:compileDebugJavaWithJavac` + `:biz_ggec_hs:testDebugUnitTest` → BUILD SUCCESSFUL
- `:app:compileDebugJavaWithJavac` + `:app:testDebugUnitTest` → BUILD SUCCESSFUL
- `:app:assembleDebug` → BUILD SUCCESSFUL（debug APK）
- 边界任务全部通过：`checkLibBluetoothApiSurface / checkLibBluetoothProviderBoundary / checkLibBluetoothLogSeam / checkNoStrayLogging / checkBizGgecHsBoundary / checkAppMvvmLayers / checkExplicitImports / checkDeviceLayerImports / checkComponentOwnership / checkAppSdkProviderBoundary`
- 新增单测：`YoBleScanControllerTest.stopImmediatelyAfterScanDoesNotLeaveScanningLatch` 增加“状态必须回到 IDLE”断言；新增 `BleScanRecordTest`（empty 单例 / rawBytesView 稳定 / rawBytes 拷贝）。

### 6.1 真机前后对比（NEPI4E0106 / motorola edge 60 pro / Android 15，2026-11-11）

环境：周围 10 台 HS01 耳机（Vidda/Hisense），扫描过滤 `filters=0`（软件匹配），`LOW_LATENCY / ALL_MATCHES / reportDelay=0`。

| 指标 | 修复前基线 | 本轮修复后 | 结论 |
|---|---|---|---|
| Janky frames（现代） | 40.00% | 8.85%（进入扫描）/ **1.26%**（切换 HS01 后长窗口 4213 帧） | 大幅下降 |
| Janky frames（legacy） | 54% | 12.31% / 21.84% | 大幅下降 |
| 90th / 95th / 99th | 250ms / 1400ms / 2750ms | 14ms / 26ms / 42ms（窗口）→ 14/15/22ms（切换后） | 帧时延降到正常水平 |
| 单帧最长卡死 | 14416ms（Skipped 1433 frames） | 无 14s 级卡死（BufferQueue 最大 ~1.2s 过渡帧） | 主线程 Binder 风暴消除 |
| 设备类型切换 | 易卡/掉设备 | 顶部 “全部设备”→“HS01” 即时生效，10 台设备保持，无卡顿 | 通过 |
| 退出扫描（Back） | `IllegalStateException: material surface is closed` | 返回 MainActivity，**NO CRASH** | 当前工作区已不再复现 |

截图证据：`E:\System\Temp\agg-profiling\scan_all.png`（全部设备）、`scan_hs01.png`（切换 HS01）；日志/帧数据：`gfx_after.txt`、`gfx_filter.txt`、`logcat_after.txt`、`btm_after.txt`。

> 说明：修复后主线程不再承担每包 AD 解析 + 4 个同步 Binder IPC（已移到 `yo-ble-scan-parse` 线程），这是帧时延从 2750ms 降到 42ms 量级的直接原因。硬件 ScanFilter 仍未启用（filters=0），后续可在真机验证 HS02 `SCAN_RSP` 行为后再决定是否下推。

### 6.2 协议层优化后复测（2026-11-11）
- `:biz_ggec_hs` 零分配快速拒绝上线后：真机扫描发现 7 台 HS01，`gfxinfo` **Janky 5.46% / 90th 10ms / 95th 20ms / 99th 44ms**，Back 退出 **NO FATAL**。

### 6.3 `:app:lintDebug` 归因（预存在、非设备模块）
`:app:check` 因 4 个**既存且与设备/扫描无关**的 lint 错误失败（基线任务-929 同样失败）：
- `platform/ai/voice/AndroidMicAudioSource.java:259` MissingPermission（AI 语音麦克风）
- `data/ai/settings/AiSettingsRepositoryImpl.java:174/180` NewApi `CompletableFuture#failedFuture`（AI 设置）
- `AndroidManifest.xml` NotificationPermission（`lib_common` 下载通知）

设备扫描相关代码（`lib_bluetooth`、`biz_ggec_hs`、`app/ui/device`、`app/provider/lib_bluetooth`、`app/domain/device`）**0 lint 错误**。`lib_bluetooth:check` / `biz_ggec_hs:check`（含 lint）均通过。


## 7. 残余项（后续轮次闭环）

1. ~~跨层重复 YoBleClient~~ **已修复**：`HsGraph.open(YoBleCommandLink)` + `YoHsSdk.create(YoBleCommandLink)` 新增重载（边界允许：`internal.graph` 可引用 provider；root facade 不引用 lib_bluetooth），`HsDevicePort.ensureSession()` 改为 `YoHsSdk.create(new YoBleCommandLink(bleClient))`，扫描与命令共享同一个 `YoBleClient`（不再产生第二个 runtime 线程池/广播接收器/GATT 会话）。`release()` 时由 `YoHsClient.close()→link.release()→client.release()` 统一释放；`checkBizGgecHsBoundary`/`checkAppSdkProviderBoundary` 均通过，真机扫描冒烟通过、无崩溃。
2. **ScanActivity 退出崩溃**：真机 Back 退出已 **NO CRASH**（当前工作区不复发）；仍建议对“点设备行连接后 finish”路径做一次真机回归（下一轮）。
3. **HS02 硬件 ScanFilter**：需真机验证 `0xFFFF "HC"` 硬件过滤是否吞掉分离的 `SCAN_RSP 0x09` 名称包，再决定是否启用硬件过滤（当前保持软件匹配，实测性能已达标）。

## 8. 本轮变更文件清单（关键）

- lib_bluetooth：`internal/ble/internal/android/compat/YoBleScanCompat.java`、`internal/ble/internal/android/YoBleAndroidScanPlatform.java`、`internal/ble/scan/YoBleScanController.java`、`internal/ble/scan/YoBleScanCallbackEmulator.java`、`internal/ble/scan/YoBleScanFilterMatcher.java`、`model/ble/BleScanRecord.java`
- biz_ggec_hs：`model/broadcast/HsScanSnapshot.java`、`provider/lib_bluetooth/HsScanSnapshots.java`、`provider/lib_bluetooth/YoBleCommandLink.java`、`internal/broadcast/Hs02BroadcastDecoder.java`、`internal/broadcast/Hs02BroadcastAssembler.java`、`internal/graph/HsGraph.java`、`YoHsSdk.java`
- app：`ui/device/scan/{ScanScreen,ScanDeviceCard,ScanViewModel,ScanUiMapper}.java`、`provider/lib_bluetooth/HsDevicePort.java`、`wiring/{DeviceGraph,AppContainer}.java`、`EarbudsApplication.java`
- 测试：`lib_bluetooth/.../scan/YoBleScanControllerTest.java`、`lib_bluetooth/.../model/ble/BleScanRecordTest.java`
- 文档：本文件

> 说明：仓库工作区在此前轮次已有大量未提交改动；以上为本轮工程直接触碰并验证的文件。清理的冗余/防御代码包括：`BleScanRecord.empty()` 单例化、`ScanUiMapper`+`ScanUiState` 双重 `List.copyOf`、过滤热路径逐包 clone、`withCallbackType` 重复全拷贝、协议层环境广播的双重 AD 解码、跨层第二个 `YoBleClient`、`ScanDeviceCard` 的 `WRAP_CONTENT` 重排。



