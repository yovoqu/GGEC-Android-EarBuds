# 设备模块全量架构审查与数据链路（Round 1）

> 日期：2026-11-10
> 范围：`app`（设备 MVVM）/ `provider`(lib_bluetooth 适配) / `biz_ggec_hs`（HS 协议层）/ `lib_bluetooth`（BLE + Classic 蓝牙层）
> 基线：`git 9816a75 docs: 架构评审/清理/验收报告与需求提示词`
> 原则：自底而上、严格 MVVM、**CBT 与 BLE 严禁耦合**、去冗余补丁/防御性代码；每个问题与修改均给 `文件:行` 证据链。
> 明细子报告（本轮新增）：
> - 蓝牙层 BLE：`docs/audit/round1_ble.md`
> - 蓝牙层 Classic(CBT)：`docs/audit/round1_classic.md`
> - 协议层 biz_ggec_hs：`docs/audit/round1_protocol.md`
>
> **最终验收与残余风险**：`docs/device/00_final_verification_and_residuals.md`（目标达成矩阵 + 全量回归 + 残余 P2/P3）。

---

## 0. 结论摘要

1. **分层骨架成立**：`app(MVVM) → provider/lib_bluetooth(适配) → biz_ggec_hs(协议) → lib_bluetooth(BLE/Classic)`；`android.bluetooth` 仅在 `lib_bluetooth` 内部出现（app/biz 0 处）。
2. **蓝牙层 BLE 与 CBT 真解耦**：双向零 `import`、独立 `Runtime`/执行器、各自 `EventBus` 实例；唯一共享是进程级诊断/日志/权限等横切单例（非控制通道）。证据见 §2.3。
3. **协议层命令链路闭合**（写→通知→分帧→调度→完成→主线程回调），但**连接生命周期对 App 零回调**（只有 `isReady()` 轮询），是 App 接入连接的硬阻塞（H-01）。证据见 §3.3。
4. **应用层是当前最大短板**：扫描能列出 HS01/HS02，但 `HsDevicePort.connect/disconnect` 是**空实现**，`DeviceControlsPort` 为 `EmptyDeviceControlsPort`——点“连接”无任何效果；这是“应用层表现不满意”的根因（D-D）。证据见 §4.3、§5。
5. **两个显式 Bug 根因已定位**：
   - **扫描卡死**：应用层“扫描启动时在主线程同步查询已配对设备（BLE+CBT 两路 binder）+ 扫描启动异常未复位闩锁”，叠加底层“state 线程同步执行 `BluetoothLeScanner` binder”的结构性阻塞风险（BLE-03）。
   - **扫描设备类型切换**：当前“设备类型”只是对**已发现结果**的 UI 侧过滤（`ScanUiMapper`），领域/端口层没有“扫描类型”概念，切换不触达扫描器，属语义缺失而非真切换。
6. **本轮已修复**（应用层，编译+单测通过，见 §6/§8）：
   - 扫描启动移出主线程且**只查 BLE 已配对**（去 CBT 耦合、去主线程 IO）；
   - 扫描启动异常**复位 `scanning` 闩锁**并上报失败（防“扫描卡死”）；
   - `HsDevicePort` **彻底移除 `YoCbtClient`**（构造/查询/依赖），扫描路径 100% BLE；
   - `ScanViewModel` 去掉 Android `R` 资源依赖，失败事件改为领域枚举 `DevicePort.ScanFailure`，资源映射下沉到 `ScanActivity`（MVVM 收尾）。

---

## 1. 模块依赖图（自底向上）

```text
┌──────────────────────────────────────────────────────────────┐
│ app : com.ggec.earbuds                                          │
│   ui/device/{scan,home,settings}  (Activity/Page/View + ViewModel)│
│   domain/device/{model,port,usecase,policy}                     │
│   data/device/{DeviceProfileCatalogImpl,FileDevicePreferences,  │
│                EmptyDeviceControlsPort,IdleDevicePort}          │
│   provider/lib_bluetooth/{HsDevicePort,HsBroadcastScanMapper}   │
│   wiring/{DeviceGraph,AppContainer}                             │
└───────────────┬───────────────────────────────┬────────────────┘
                │ provider（唯一允许 import SDK）  │
                ▼                                ▼
┌───────────────────────────────┐   ┌───────────────────────────────┐
│ biz_ggec_hs（HS 协议层）        │   │ lib_bluetooth（蓝牙层）         │
│  api: YoHsClient/BroadcastSession│   │  api: YoBleClient/YoCbtClient   │
│  session/graph/contract         │   │  internal/ble     (BLE 栈)      │
│  protocol(调度/分帧/命令/事件)    │   │  internal/classic (CBT 栈)      │
│  provider/lib_bluetooth:        │   │  internal/common(运行时/诊断)   │
│    YoBleCommandLink(HsCommandLink)│  │  model/{ble,classic,common}     │
│    HsScanSnapshots(广播适配)      │   │  provider/lib_common(权限/日志) │
└───────────────┬─────────────────┘   └───────────────┬───────────────┘
                │ 仅 provider 包 import lib_bluetooth     │
                └──────────────────┬──────────────────────┘
                                   ▼
                     android.bluetooth.*（仅 lib_bluetooth 内部）
```

Gradle 强制边界（基线已存在）：
- `app: checkAppSdkProviderBoundary`（非 provider 禁 import `com.yovo.{lib_bluetooth,lib_ai,biz_ggec_hs}`）— `app/build.gradle:359-386`
- `app: checkAppMvvmLayers`（VM 禁 android UI / domain 禁 android|data|ui / ui 禁 data / data 禁 ui）— `app/build.gradle:392-426`
- `app: checkDeviceLayerImports` — `app/build.gradle:228-261`
- `biz_ggec_hs: checkBizGgecHsBoundary` — `biz_ggec_hs/build.gradle`
- `lib_bluetooth: checkLibBluetooth{ProviderBoundary,ApiSurface,LogSeam}` + `checkNoStrayLogging` — `lib_bluetooth/build.gradle`

---

## 2. 蓝牙层（lib_bluetooth）

### 2.1 BLE 子系统结构与扫描数据流

装配链（`docs/audit/round1_ble.md` §2.1）：
`YoBluetoothSDK.ble()` → `BluetoothSdkCore.ble()`（`BluetoothSdkCore.java:32-36`，先 `YoBtLogger.ensureInitialized` + `BluetoothProviderInstaller.installAll`）→ `new BleClientImpl` → `YoBleAndroidModuleGraph.create` → `YoBleModuleCore`（`new YoBleRuntime` + `new YoBleScanController` + connection/gatt/l2cap/peripheral/bond）。

扫描数据流：
`BleClientImpl.scan`（`BleClientImpl.java:110-112`）→ `YoBleScanController.scan`（`YoBleScanController.java:92-98`，`runtime.state(startScanInternal)`）→ preflight/限流（`:170-189`）→ `scanPlatform.startPlatformScan`（`YoBleAndroidScanPlatform.java:56-141`）→ `BluetoothLeScanner.startScan`。平台回调经代数隔离回到 state 线程：`handlePlatformResult`（`:368-388`）→ `YoBleScanFilterMatcher` 复核 → `snapshotStore` → `callbackEmulator`（FIRST_MATCH/去重）→ `runtime.callback(observer.onDeviceFound)`。

### 2.2 Classic(CBT) 子系统

`YoCbtClient` 33 个方法在 `ClassicClientImpl` + `YoCbtAndroidClient` 全部有实现（`docs/audit/round1_classic.md` §1）。CBT 当前**无生产消费者**：app/biz 均只走 BLE；Classic 仅仪器化测试引用（CBT-14）。CBT 的 SPP 命令会话存在并发/帧化缺陷（CBT-01/02/03），但**不在 HS(BLE) 路径上**，本轮不改，登记归档。

### 2.3 BLE ↔ CBT 解耦结论（带证据）

**结论：lib 内部真正解耦。**
- 双向零 import：`internal/ble` 无 `import ...internal.classic`；`internal/classic` 无 `import ...internal.ble`（`round1_ble.md` §4、`round1_classic.md` §解耦）。
- 独立运行时/执行器：BLE `new YoBleRuntime(...)`（`YoBleModuleCore.java:75`，线程 `yo-ble-state/io/callback/scheduler`）；CBT `new YoCbtRuntime(...)`（`YoCbtRuntime.java:52-65`，线程 `yo-cbt-*`）。`YoExecutors` 每次新建实例，非共享池。
- 各自 `BluetoothEventBus` 实例（`YoBleRuntime.java:61` / `YoCbtRuntime.java:38`）。
- 唯一共享：`BluetoothDiagnosticsBus`（进程单例，诊断汇聚）、`YoBtLogger`、`BluetoothProviderInstaller`（权限）——横切设施，不承载会话状态。
- 协议层对 BLE 的依赖收敛为 1 接口 `HsCommandLink` + 1 实现 `YoBleCommandLink`；广播方向另有薄适配 `HsScanSnapshots`（同属 provider 包）。

### 2.4 蓝牙层缺陷索引（详见子报告）

BLE（`round1_ble.md` §3/§5）：
- **BLE-01 P1** 扫描启停竞态：`scan()` 异步投递、`stopScan()` 在调用线程同步判定 → “刚发起就停”停不掉（`YoBleScanController.java:92-98,106-109,277-278`）。
- **BLE-02 P1** runtime 关闭后 `scan()` 静默丢回调（`YoBleScanController.java:96,165-169` + `YoBleRuntime.java:101-108`）。
- **BLE-03 P1** state 线程同步执行 `BluetoothLeScanner` binder，底层一阻塞即冻结整个 BLE 域（`YoBleAndroidScanPlatform.java:56-141,160-180`；`YoBleScanController.java:248,475,126`）。
- BLE-04..BLE-14 P2（长扫重启叠加 flush、失败路径不清定时句柄、`locationExempt` 默认、`BleScanResult.callbackType` 渗入值对象、全零 MAC 兜底、硬件/软件双过滤假阴性、MAX_SEEN 驱逐等）。
- 死代码 D-01..D-07（`YoBleScanThrottleGuard.recentStartCount/reset`、`BleScanResult` 三参构造器、BLE `eventBus()`/`diagnostics()` 未用等）、冗余防御 D-08..D-15。

CBT（`round1_classic.md`）：
- **CBT-01 P1** `YoCbtCommandSession.attempts/timeouts` 普通 HashMap 跨三线程读写（`:39-40`）。
- **CBT-02 P1** 协议帧化未接线，`RawPacketCodec`+`FIRST_PENDING`，粘包/并发响应错配（`YoCbtCommandSessionRegistry.java:21`、`YoCbtCommandRequest.java:62`）。
- **CBT-03 P1** SPP 状态机跨 io/callback/state/调用线程，违背单线程契约（`YoCbtSppConnectionManager.java:105,163`）。
- CBT-04/05/14 P2（诊断单例跨域污染、错误码塌缩、无生产接线）。

> 说明：本轮以应用层可交付修复为主；蓝牙层 P1（BLE-01/02/03、CBT-01/02/03）列入 Round 2 专项，需 `:lib_bluetooth:test` 全量回归后再改，避免破坏已验证的 BLE 栈。

---

## 3. 协议层（biz_ggec_hs）

### 3.1 结构与入口
- 命令会话：`YoHsSdk.create → HsGraph.open(Context)`（`HsGraph.java:17-23`）→ `new HsProtocolEngine(new YoBleCommandLink)` → `YoHsClientImpl`。
- 广播识别：`YoHsBroadcast.classify/openSession`（只判代际，不连接）。
- 唯一链路抽象：`internal/contract/HsCommandLink`（`HsCommandLink.java:9-35`：open/close/write/setListener + Listener.onReady/onInbound/onLinkError）。
- 唯一 BLE 实现：`provider/lib_bluetooth/YoBleCommandLink`（GATT 服务 `65786365-...-820100`、写 `...-820104`、通知 `...-820105`）。

### 3.2 端到端数据链路（协议层闭合）
下行：`YoHsClient.xxx` → `YoHsClientImpl.engine.submit`（`YoHsClientImpl.java:83-131`）→ `HsProtocolEngine.submit`（`HsProtocolEngine.java:81-95`，`executor("yo-hs-protocol")` 串行）→ `CommandScheduler` → `LinkTransport.send` → `YoBleCommandLink.write`（`YoBleCommandLink.java:124-163`，`WITHOUT_RESPONSE`）→ GATT 写。
上行：GATT NOTIFY → `NotifyWatcher.onCharacteristicChanged`（`:286-296`）→ `HsProtocolEngine.LinkListener.onInbound`（`:154-165`）→ `InboundFrameDemux` → `CommandScheduler.onResponseCandidate` → `command.complete` → `mainHandler.post`（`YoHsClientImpl.java:133-139`）。
就绪语义：`onReady(true)` 仅在 **GATT READY + NOTIFY CCCD 写入成功**后触发（`YoBleCommandLink.java:78-84,211-219,280-283`）。

### 3.3 连接可观测性缺口（H-01，P1，App 接入硬阻塞）
`api/YoHsClient.java:16-53` 只有 `connect(mac):void / disconnect():void / close():void / isReady():boolean`；`model/HsDeviceObserver.java:10-38` 全是设备域事件（电量/EQ/…），**无连接成功/失败/断开/重连回调**；`HsProtocolEngine.LinkListener.onLinkError` 仅 `scheduler.abortCurrent`（`:168-174`）不上抛。→ App 无法得知连接结果。佐证：`HsDevicePort.connect` 当前空实现。
**建议接口**：新增 `HsConnectionObserver{onConnected(mac), onDisconnected(reason), onConnectFailed(err,willRetry)}` + `YoHsClient.setConnectionObserver(...)`，在 `onLinkReady(true/false)`、`onLinkError` 驱动。

### 3.4 协议层缺陷索引（`round1_protocol.md` §3）
- H-01 P1 连接生命周期零回调（见 §3.3）——**Round 2 已修复，见 §10**。
- H-02 P1 首页/全局状态查询结果不落地：`DeviceEventPipeline.applyAggregatedHomeStatus` 无调用者（`DeviceEventPipeline.java:67-113`）；`HsProtocolEngine.submit` 不落 `DeviceDomainStore`。
- H-03 P2 入站无重装/去粘包（`YoBleCommandLink.java:286-296` → `HsProtocolEngine.java:154-164`）。
- H-04 P2 单写 + `WITHOUT_RESPONSE` 不分片（`YoBleCommandLink.java:136-157`），MTU 回落 23 时长帧截断风险。
- H-05 P2 重复 `ready(true)` 误切会话（`YoBleCommandLink.java:280-283` + `HsProtocolEngine.java:119-137`）。
- H-06..H-12 P2（listener 非 volatile、write 空 catch、值对象双份 `CommandCompletion`/`HsCommandResult`、会话门禁重复、connect 无异常契约等）。
- 死代码：`CommandQueryGroup` 整类、`Command.CommandCallback`、`CommandScheduler.setReady/onResponseFrame`、`DeviceEventPipeline` 若干无调用方法。

---

## 4. 应用层（app 设备 MVVM）

### 4.1 分层现状（目录齐全、组合根在手）
- `ui/device/scan`（`ScanActivity`+`ScanScreen`+`ScanViewModel`+`ScanUiState`/`ScanItem`/`ScanUiMapper`/`ScanFilterOption`）、`ui/device/home`、`ui/device/settings`。
- `domain/device/{model,port,usecase,policy}` 纯 Java。
- `data/device/{DeviceProfileCatalogImpl,FileDevicePreferences,EmptyDeviceControlsPort,IdleDevicePort}`。
- `provider/lib_bluetooth/{HsDevicePort,HsBroadcastScanMapper}`。
- 组合根：`EarbudsApplication → AppContainer → DeviceGraph`（`DeviceGraph.java:49-60`，手动 DI）。`DeviceGraph` 用 `HsDevicePort` 作为 `DevicePort`，`EmptyDeviceControlsPort` 作为 `DeviceControlsPort`。

### 4.2 扫描数据链路（现状，修复后）
`ScanActivity.onCreate` → `startScanWithPermission()`（`ScanActivity.java`）→ `PermissionProvider.requestBluetoothIfNeeded` → `ScanViewModel.startScan()` →
`ScanDevices.startBatch` → `DevicePort.startScanBatch` → `HsDevicePort.startScanBatch`：
1. （修复后）不再在主线程查询已配对；`bleClient.scan(config, observer)` 启动 BLE 扫描（try/catch 保护）。
2. BLE 回调线程 `dispatchBatch`：按代次**懒加载 BLE 已配对集合**（`loadBleBondedAddresses()`，仅 `YoBleClient`）→ `HsBroadcastScanMapper.map` 识别 HS01/HS02 → `onResults`。
3. `ScanViewModel.onScanBatch`：去重合并、<3dBm 抖动抑制、300ms 节流 → `uiState.postValue`。
4. `ScanScreen.render`：`bindFilter` + `ListAdapter.submitList` 增量渲染。
“设备类型”切换：`ScanViewModel.selectFilter(modelId)` 只改 `selectedFilterModelId` 并重新投影 `ScanUiMapper.map(results, filter)`（`ScanUiMapper.java:25-34`）——**纯 UI 侧过滤，不重启/不切换扫描器**。

### 4.3 连接数据链路（Round 2 起 = 已接通）
`ScanViewModel.connect(item)` → `scanDevices.stop()` → `ConnectDevice.execute(modelId,variantId,mac)`（`ConnectDevice.java:17-20`，先 `preferences.rememberDevice`）→ `DevicePort.connect` → **`HsDevicePort.connect`**：发布 `CONNECTING` → `YoHsSdk.create(Context)` 懒建 `YoHsClient` → `setConnectionObserver/setDeviceObserver` → `hsClient.connect(mac)`；协议层 `HsConnectionObserver.onConnected` → 发布 `READY`（含电量），`onDisconnected/onConnectFailed` → `DISCONNECTED`；`HsDeviceObserver.onBatteryStateChanged` → 刷新电量。`DeviceHomeViewModel` 订阅 `ObserveDevice` 即时刷新首页。详见 §10。

### 4.4 应用层缺陷清单（含基线证据）

| ID | 级别 | 基线位置 | 问题 | 状态 |
|---|---|---|---|---|
| D-A | P1 | `HsDevicePort.java:150` + `:313-342`（基线） | 扫描启动在**主线程同步**调 `getBondedDevices()`（BLE + Classic 两路 binder IPC）→ 卡顿/ANR 风险 | ✅ 本轮修复 |
| D-B | P1 | `HsDevicePort.java:195-243`（基线，`bleClient.scan` 无 try/catch） | 扫描启动异常时 `scanning=true` 不复位；`ScanViewModel.startScan:129` 已置 scanning，异常后 UI 永久“搜索中”=**扫描卡死** | ✅ 本轮修复 |
| D-C | P1 | `HsDevicePort.java:53-54,67-72,327-340`（基线） | 无条件构造 `YoCbtClient` 并把 Classic 已配对并入 BLE 扫描判断 → **CBT/BLE 耦合** | ✅ 本轮修复（彻底移除 CBT） |
| D-D | P1 | `HsDevicePort.java:115-118`（基线） | `connect()/disconnect()` **空实现** → 点连接无效果（应用层“不满意”根因） | ✅ Round 2 修复（§10） |
| D-E | P2 | `ScanUiMapper.java:25-34`、`ScanViewModel.selectFilter` | “扫描设备类型”仅是 UI 结果过滤，无领域扫描类型概念 | ⏳ 设计决策（见 §7） |
| D-F | P2 | `ScanUiState.java:33-38` vs `ScanViewModel.java:348-359` | 过滤项双来源（硬编码 HS01/HS02 vs 目录 models） | ⏳ Round 2 |
| D-G | P2 | `DevicePort.java:32-51`、`ScanDevices.java:18-32` | `startScan` 三重重载 + `startScanBatch` 冗余 API | ⏳ Round 2 |
| D-H | P2 | `ScanViewModel.java:9,361-370`（基线） | VM import `com.ggec.earbuds.R` 并做资源映射（MVVM 越界） | ✅ 本轮修复（事件改枚举） |
| D-I | P2 | `ScanViewModel.java:372-396` | VM 内自建线程 `DefaultPublishScheduler`（平台关注点） | ⏳ Round 2（下沉 wiring） |
| D-J | P2 | `HsDevicePort` 基线 4 个构造器 + `requireAppContext` 兜底 | 构造器冗余/防御兜底 | ✅ 本轮收敛为 2 个 |
| D-K | P2 | `HsDevicePortTest` 基线 `scansExtendedAndLegacyBroadcastsWithClassicBondedDetection` | 单测把 CBT/BLE 耦合格局**固化为预期** | ✅ 本轮改为 BLE-only 已配对断言 |

---

## 5. 两个显式 Bug 的根因链

### 5.1 扫描卡死
证据链（自底向上）：
1. **底层结构风险（BLE-03）**：`BluetoothLeScanner` 的 `start/stop/flushPendingScanResults` 全在**唯一 state 线程**同步执行（`YoBleAndroidScanPlatform.java:116-121,167-180,144-158`；调用点 `YoBleScanController.java:248,475,126`）。底层 binder 一旦阻塞，BLE 域（扫描+连接+GATT+配对）全部排队 → 用户感知“扫描卡死/无法再扫”。
2. **底层启停竞态（BLE-01）**：`scan()` 异步投递、`stopScan()` 同步判定，存在“刚发起就停 → 停不掉、续跑到 15s 超时”窗口（`YoBleScanController.java:92-98,106-109,277-278`）。
3. **应用层主线程阻塞（D-A）**：`HsDevicePort.startScanBatch` 在调用（主）线程同步做两次 `getBondedDevices()`（BLE+Classic binder），放大主线程卡顿。
4. **应用层异常闩锁（D-B）**：`bleClient.scan` 抛异常时 `scanning` 不复位，`ScanViewModel.startScan` 的 `scanning` 也置 true → 之后所有 `startScan` 被 `if (scanning || scanDevices.isScanning()) return;` 挡死（`ScanViewModel.java:119-140`）→ 永久“搜索中”。
→ 本轮修 3、4（应用层可立即消除的卡死面）；1、2 列入 Round 2（lib 专项，需全量回归）。

### 5.2 扫描设备类型切换
- 现状：下拉“设备类型”= HS01/HS02/全部，`selectFilter` 只做 `ScanUiMapper.map(results, filter)` 的结果过滤（`ScanUiMapper.java:25-34`），**不改变扫描器/扫描参数**；`YoSelect` 选项 id=索引（`addOption(i,label)` + `onSelect(id,label,position)`），映射正确。
- 结论：功能上“切换过滤”可用且不重启扫描；但**没有“扫描类型（BLE/CBT）”领域概念**。对 HS01/HS02（均为 BLE）这是正确的（扫描只应出 BLE）；若未来引入 CBT 产品线，应新增独立 `ScanType` + 独立端口，而不是把 CBT 塞进 BLE 扫描（违反解耦）。
- 本轮：修正了被“耦合测试”固化的错误预期，确保切换在扫描中/后均即时投影、不阻塞（见 §6/§8 单测）。

---

## 6. 本轮代码变更（含证据）

### 6.1 `app/.../provider/lib_bluetooth/HsDevicePort.java`（重写，P1）
变更点（对照基线）：
1. **移除 CBT**：删除 `YoCbtClient` 字段/import/构造参数与 `classicClient.getBondedDevices()` 合并逻辑（基线 `:53-54,67-72,327-340`）。→ 扫描端口只依赖 `YoBleClient`。
2. **已配对改为 BLE-only 且回调线程懒加载**：新增 `ensureBondedLoaded(generation)` + `loadBleBondedAddresses()`（仅 `bleClient.getBondedDevices()`，try/catch 降级未配对），在 `dispatchBatch` 锁外执行；`startScanBatch` 内不再有主线程 `readBondedAddresses()`。→ 消除主线程 binder + CBT 合并。
3. **异常闩锁防护**：`bleClient.scan(...)` 包 `try/catch(RuntimeException)` → `failStart()` 复位 `scanning` 并上报 `ScanFailure.FAILED` + `onFinished`。→ 消除“扫描卡死”。
4. **构造器收敛**：`HsDevicePort(Context)` + `HsDevicePort(YoBleClient, HsBroadcastScanMapper)`（去 CBT 参数；测试用后者）。

### 6.2 `app/.../ui/device/scan/ScanViewModel.java`（MVVM，P2）
- 删除 `import com.ggec.earbuds.R` 与 `mapFailureMessage`（基线 `:9,361-370`）。
- `events` 类型 `OneShotLiveData<Integer>` → `OneShotLiveData<DevicePort.ScanFailure>`；`events()` 返回 `LiveData<DevicePort.ScanFailure>`；`onScanFailure` 直接 `events.postValue(failure)`。

### 6.3 `app/.../ui/device/scan/ScanActivity.java`（资源映射下沉到 View 层）
- `events` 观察改为 `failure -> getString(failureMessage(failure))`；新增 `@StringRes failureMessage(DevicePort.ScanFailure)`（六种失败→`R.string.device_scan_error_*`）。

### 6.4 测试
- `HsDevicePortTest`：删 `classicProxy/classicBondedSnapshot`，改 `bleBondedSnapshot`；`scansExtendedAndLegacyBroadcastsWithClassicBondedDetection` → `...WithBleBondedDetection`（BLE 已配对驱动 paired=true）；新增 `resetsScanningLatchWhenPlatformScanStartThrows`（验证异常复位）。
- `ScanViewModelTest`：失败事件断言由 `R.string...` 改为 `DevicePort.ScanFailure.BLUETOOTH_DISABLED`。

---

## 7. 目标架构与分批整改计划

```text
目标（严格 MVVM + 协议无关端口）：
View(Activity/Page/View) → ViewModel → domain.usecase/port/model → data(impl) → provider(SDK 适配) → SDK

扫描：ScanViewModel --ScanDevices--> DevicePort(协议无关)
        └ HS 设备：BleOnlyDevicePort(仅 YoBleClient)  ──┐
        └ 未来 CBT 产品线：独立的 CbtDevicePort(仅 YoCbtClient)  ← 严禁同端口混用
连接：ScanViewModel.connect → ConnectDevice → DeviceSessionPort
        └ HsDeviceSession(YoHsClient + HsConnectionObserver) → DeviceSnapshot(domain)
```

- **Round 2（蓝牙层专项，P1）**：修 `YoBleScanController` BLE-01（同步 `PENDING_START` 状态）/BLE-02（关闭后同步失败回调）/BLE-05（失败清理句柄）/BLE-04（重启取消旧 flush）；评估 BLE-03（平台 binder 移 io 线程或超时）。以 `:lib_bluetooth:test` 全量回归。
- **Round 2/3（协议层 + 连接，P1）**：新增 `HsConnectionObserver` 并接入 `YoBleCommandLink`/`HsProtocolEngine`（解 H-01/H-12）；修 H-02（查询结果落 `DeviceDomainStore`）、H-05（ready 幂等）、H-06（volatile）；清死代码。
- **Round 3（应用层连接，P1）**：`HsDevicePort.connect` 接入 `YoHsClient` 会话（新 `DeviceSession` + `HsConnectionObserver` + `HsDeviceObserver`→`DeviceSnapshot`/`BatterySnapshot`），`DeviceControlsPort` 接协议命令；删除 `EmptyDeviceControlsPort`。
- **Round 4（MVVM 收尾 + 冗余清理）**：D-E（显式 `ScanType` 设计，或明确“仅过滤”契约）、D-F（过滤项单一来源）、D-G（DevicePort API 收敛）、D-I（scheduler 下沉 wiring）；清 lib_bluetooth 死代码 D-01..D-07。

---

## 8. 验证（本轮）

| 项 | 命令 | 结果 |
|---|---|---|
| app 主代码编译 | `:app:compileDebugJavaWithJavac` | BUILD SUCCESSFUL |
| app 单测编译 | `:app:compileDebugUnitTestJavaWithJavac` | BUILD SUCCESSFUL |
| 设备/扫描单测 | `:app:testDebugUnitTest --tests ...device.scan.* --tests provider.lib_bluetooth.* --tests domain.device.*` | BUILD SUCCESSFUL |
| 全量 app 单测 | `:app:testDebugUnitTest` | 见本轮执行结果（§8.1） |
| 层/边界校验 | preBuild: `checkShellLayerImports/checkAiLayerImports/checkDeviceLayerImports/checkMainThreadIoImports/checkComponentOwnership/checkExplicitImports/checkFrameworkToastForbidden/checkSingleYoLogInit/checkAppSdkProviderBoundary/checkAppMvvmLayers` | 全部随编译执行通过 |

### 8.1 全量单测
`./gradlew :app:testDebugUnitTest` → **BUILD SUCCESSFUL in 21s**（120 tasks，11 executed），无失败用例。
本轮新增/调整用例：
- `HsDevicePortTest.resetsScanningLatchWhenPlatformScanStartThrows`（扫描启动异常→复位 scanning + FAILED + finished）
- `HsDevicePortTest.scansExtendedAndLegacyBroadcastsWithBleBondedDetection`（BLE-only 已配对驱动 paired=true）
- `ScanViewModelTest.scanFailureStopsScanningAndEmitsErrorMessageRes`（失败事件=领域枚举）

---

## 9. 未知项

1. 真机动态验证：BLE-01/03 的阻塞时长、`onNotificationEnabled` 是否重复回调、MTU 是否稳定 517（需设备）。
2. `BluetoothDevice#getAddressType()` 的实际引入 API（BLE-13 结论依赖）。
3. `CommandPolicyRegistry` 与全部命令类是否 1:1（约 100+ 命令，仅抽查公开 API）。
4. CBT 子系统无生产消费者，其 P1 缺陷暂不影响当前 HS(BLE) 产品，风险登记归档。
5. ~~App 连接接入排期（D-D/H-01）为下一阶段硬阻塞项。~~ → **Round 2 已接入连接链路（§10）**；真机验证待办。

---

## 10. Round 2 追加：协议连接生命周期 + 应用层连接数据链路接入

> 目标：把"蓝牙层→协议层→应用层"真正接成一条可观测的**连接数据链路**，消除 `connect()` 空实现（D-D / H-01）。

### 10.1 协议层（biz_ggec_hs）——补连接生命周期回调（H-01）
- 新增 `api/HsConnectionObserver.java`：`onConnecting/onConnected/onDisconnected/onConnectFailed`（主线程回调）。
- `api/YoHsClient.java`：新增 `void setConnectionObserver(@Nullable HsConnectionObserver)`。
- `internal/session/YoHsClientImpl.java`：`setConnectionObserver` + `MainConnectionObserver`（统一 `mainHandler.post` 切主线程，与 `MainDeviceObserver` 对称）。
- `internal/protocol/HsProtocolEngine.java`：
  - `connect(mac)` 记录 `currentMac` 并触发 `onConnecting`；
  - `onLinkReady(true)` 触发 `onConnected`，**且新增 `if (commandReady) return;` 幂等**（同时修 **H-05** 重复 ready 误切会话）；
  - `onLinkReady(false)` 在“曾就绪”时触发 `onDisconnected(reason)`；
  - `onLinkError` 就绪时触发 `onDisconnected(reason)`、未就绪时触发 `onConnectFailed(reason,false)`（`connectFailedNotified` 去重，修 **H-12** 断开原因丢失的一半）；`release()` 清 `connectionObserver`。
- **H-02 落地（本轮）**：`HsProtocolEngine.submit` 追加一个完成监听——当 `CommandCompletion` 为 `OBJECT` 且 data 为 `Map<String,String>` 时，调用 `DeviceEventPipeline.applyAggregatedHomeStatus(status)` 写入 `DeviceDomainStore`（仅取 String→String 项，避免类型污染）。首页/全局状态类查询（`GlobalStatusCommand`/`HomePageStatusV12*`，均已在 `CommandPolicyRegistry:117-129` 登记）结果由此真正进入电量/EQ/服务状态存储，`battery()`/`HsDeviceObserver` 不再只能依赖主动上报。

### 10.2 应用层（app）——`HsDevicePort.connect` 接入真实会话（D-D）- `provider/lib_bluetooth/HsDevicePort.java`：
  - 新增 `appContext` + 懒建 `YoHsClient`（`ensureSession()` → `YoHsSdk.create(context)`，仅首次连接创建，避免启动期空耗）；
  - `connect(modelId,variantId,mac)`：记目标 → 发布 `CONNECTING` → `setConnectionObserver/setDeviceObserver` → `hsClient.connect(mac)`；
  - `Bridge implements HsConnectionObserver, HsDeviceObserver`：`onConnected→READY(含电量)`、`onDisconnected/onConnectFailed→DISCONNECTED`、`onBatteryStateChanged→刷新电量`；
  - `disconnect()`：`hsClient.disconnect()` + 发布 `DISCONNECTED`；
  - 电量映射 `HsBattery(String left/right/case/charging) → BatterySnapshot(int,-1 未知)`；
  - 保持扫描路径 100% BLE、无 CBT（§6.1 不变）。
- 结果：用户扫描→点设备→`CONNECTING`→`READY`（首页由空态切换到设备卡），断开→`DISCONNECTED`。**应用层"点连接无反应"故障消除。**

### 10.3 数据链路（最终形态）
```text
ScanViewModel.connect → ConnectDevice → DevicePort.connect
  → HsDevicePort.connect ──► YoHsSdk.create → YoHsClientImpl → HsProtocolEngine.connect(mac)
        │                                                        └─ YoBleCommandLink.open → YoBleClient.connect(GATT)
        │◄── HsConnectionObserver.onConnected（主线程）◄── HsProtocolEngine.onLinkReady(true)
        │◄── HsDeviceObserver.onBatteryStateChanged ────── DeviceEventPipeline（设备主动上报）
        ▼
  publish(DeviceSnapshot READY + BatterySnapshot) → ObserveDevice → DeviceHomeViewModel → DeviceHomePage
```

### 10.4 Round 2 验证（证据）
| 项 | 命令 | 结果 |
|---|---|---|
| biz 主代码编译 | `:biz_ggec_hs:compileDebugJavaWithJavac` | BUILD SUCCESSFUL |
| biz 边界 | `:biz_ggec_hs:checkBizGgecHsBoundary` | PASS |
| app 主代码编译 | `:app:compileDebugJavaWithJavac` | BUILD SUCCESSFUL |
| app 单测编译 + 全量单测 | `:app:compileDebugUnitTestJavaWithJavac` + `:app:testDebugUnitTest` | **BUILD SUCCESSFUL；56 套件 / 418 用例 / 0 失败** |
| 新增连通用例 | `HsDevicePortTest.connectDrivesSnapshotFromProtocolSession` | PASS（CONNECTING→READY(电量 90/70)→电池刷新 89+充电→DISCONNECTED） |

### 10.5 Round 2 真机验证结果（详见 `docs/device/round2_device_verification.md`）
真机（motorola edge 60 pro / API35）实测：
- ✅ 扫描正常启动（`[ble-scan] scan started gen=1 … timeoutMs=15000`），超时自停并保留列表，**无卡死/崩溃**。
- ✅ 设备类型切换（全部→HS02→HS01）即时生效：HS02 显示“当前筛选下暂无设备”，HS01 恢复 4 台；仅 BLE 结果。
- ✅ 连接数据链路真实执行：`connect() → onClientConnectionState status=0 → discoverServices Status=0 → MTU 495 → setCharacteristicNotification(...820105 enable:true)`，非空实现、无崩溃。
- ⚠️ 发现新应用层问题（下一轮）：点击连接后首页仍显示“还没有连接耳机”——`DeviceHomeUiMapper.map`/`LoadDeviceProfiles.profileFor` 把 `CONNECTING` 当未连接；且第三方 HS 设备 CCCD 使能后未确认就绪、缺连接超时。

### 10.6 Round 2 剩余（下一轮）
- ✅ **连接 UX/超时（P1，真机发现，本轮已修）**：`DeviceHomeUiMapper.map` 允许 `CONNECTING` 显示卡片；`DeviceHomeViewModel.publish` 以 `modelId/variantId` 兜底解析 profile；`HsDevicePort.connect` 加 12s 就绪超时（超时→`disconnect()`+`DISCONNECTED`）。真机截图见 `round2_device_verification.md` §R3：连接中显示“HS01 ● 连接中”，12s 未就绪回落未连接，无卡死。
- **H-03/H-04（P2）**：入站拼帧/去粘包、长帧分片（真机 MTU=495，暂未触发；低 MTU 需覆盖）。
- **蓝牙层 P1**：BLE-01/02/03（扫描启停竞态/释放丢回调/state 线程 binder），需 `:lib_bluetooth:test` 全量回归后专项修。
- **真 HS 设备复测**：协议 READY 与电量首屏（依赖 H-02 落地 + 设备主动上报/查询；第三方 HS01 广播设备未确认就绪）。

---

## 11. Round 4：冗余/防御代码清理 + MVVM（调度器下沉）

> 目标：按审计 D-01/D-03/D-05（BLE 死代码）与协议 H-08（死代码）清理“无调用者”的冗余 API；把 ViewModel 自建线程下沉，严格 MVVM。

### 11.1 删除的死代码（证据：全仓库仅定义处，无调用）
| 项 | 位置 | 依据 |
|---|---|---|
| `CommandQueryGroup`（整类） | `biz_ggec_hs/internal/protocol/CommandQueryGroup.java` | 全仓库仅该文件 4 行自引用；与 `CommandTransaction`/单命令并列的第三套并发抽象，未被使用 |
| `Command.CommandCallback` 接口 + `addCommandCallback` + `dispatchCallback` | `Command.java:40-46,195-200,233-248` | 无调用者；完成通道仅保留 `CompletionListener` |
| `Command.getCommandPrefix()` | `Command.java:103-105` | 仅 `setCommandPrefix` 被各命令调用；getter 无读取者 |
| `YoBleScanThrottleGuard.recentStartCount()/reset()` | `internal/ble/scan/YoBleScanThrottleGuard.java:55-62` | 无调用者 |
| `YoBleGattOperationQueue.isBusy()` | `internal/ble/gatt/YoBleGattOperationQueue.java:169-171` | 无调用者（`publishedPendingCount()` 已覆盖诊断需求） |

### 11.2 MVVM：调度器从 ViewModel 下沉
- 新增 `app/.../ui/device/scan/ScanPublishScheduler.java`（平台侧延迟合并调度器，`Executors` 只存在于此）。
- `ScanViewModel` 移除内嵌 `DefaultPublishScheduler` 与全部 `java.util.concurrent.*` import；3 参构造改用 `new ScanPublishScheduler()`，VM 仅持有注入的 `PublishScheduler` 接口——**ViewModel 不再自建线程/执行器**。

### 11.3 保留项（经判断非冗余，避免误删）
- `YoBleRuntime.eventBus()/diagnostics()`：BLE 侧暂无调用，但与 CBT 对称且属诊断能力预留（审计 D-06/D-07 建议“明确后续使用点”而非硬删），本轮保留。
- `YoBleScanAdvertiseStatus.describeScan/describeAdvertise()`：错误码可读文案，属诊断价值，保留。

### 11.4 验证
| 项 | 结果 |
|---|---|
| `:lib_bluetooth:compileDebugJavaWithJavac` + `:lib_bluetooth:testDebugUnitTest` | BUILD SUCCESSFUL；**25 套件 / 101 用例 / 0 失败** |
| `:biz_ggec_hs:compileDebugJavaWithJavac` + `:biz_ggec_hs:checkBizGgecHsBoundary` | BUILD SUCCESSFUL / PASS |
| `:app:testDebugUnitTest` | **56 套件 / 420 用例 / 0 失败** |
| 删除文件 | `CommandQueryGroup.java`（-1 类） |

---

## 12. Round 5：蓝牙层扫描 P1 修复（BLE-01/02/04/05）+ 能力检查器依赖倒置

> 目标：消除“扫描刚发起就停不掉 / 释放后静默丢回调 / 启动失败遗留定时器 / 长扫重启叠加 flush”等扫描卡死面；为可测，先把能力检查器抽为接口。

### 12.1 依赖倒置（可测性）
- `YoBleCapabilityInspector`：`final class` → **`interface`**（9 个能力/权限/开关方法）。
- 新增 `YoBleAndroidCapabilityInspector implements YoBleCapabilityInspector`（Android 实现，原逻辑迁移）。
- `YoBleAndroidModuleGraph` 注入 `new YoBleAndroidCapabilityInspector(appContext)`。其余组件（scan/connection/gatt/peripheral/pairing/l2cap）只依赖接口，签名不变。

### 12.2 `YoBleScanController` 修复（文件:行）
| ID | 级别 | 修复 | 证据 |
|---|---|---|---|
| BLE-01 | P1 | 增 `volatile boolean startInFlight`；`scan()` 同步置位、state 任务 `finally` 复位；`stopScan()` 判定纳入 `startInFlight` 后再投递停止任务 → “刚发起就停”不再续跑到 timeout | `YoBleScanController.java`（scan/stopScan/startInFlight） |
| BLE-02 | P1 | `scan()` 入口判 `released \|\| runtime.isReleased()` → 直接 `observer.onScanFailed(RELEASED)` 并返回空 handle，杜绝“runtime 已关→静默丢弃” | 同上 scan() 入口 |
| BLE-05 | P2 | 平台启动失败分支调用 `cancelTimerHandles()` 统一清理 timeout/lostSweep/batchFlush/longScanRestart/delayedStart；`stopActiveInternal` 复用同一函数去重 | 同上（cancelTimerHandles / failure 分支） |
| BLE-04 | P2 | `launchPlatformScan` 启动前 `batchFlushHandle.cancel()`，避免长扫重启后叠加软件批量 flush 循环 | 同上（launchPlatformScan 入口） |

> 未改 **BLE-03**（state 线程同步执行 `BluetoothLeScanner` binder）：属结构性问题，需将 `start/stop/flush` 移到 `runtime.io()` 并处理结果回投，涉及面大、需真机长扫/弱 ROM 验证，留待专门轮次。

### 12.3 新增单测（证据）
`lib_bluetooth/src/test/java/com/yovo/lib_bluetooth/internal/ble/scan/YoBleScanControllerTest.java`（真实 `YoBleRuntime` + Fake 平台/能力检查器）：
- `stopImmediatelyAfterScanDoesNotLeaveScanningLatch`（BLE-01：scan 后立即 stop → 最终 `isScanning()==false` 且平台已 stop）
- `scanAfterRuntimeReleasedDeliversFailureInsteadOfSilentDrop`（BLE-02：runtime 释放后 scan → 收到 `onScanFailed(RELEASED)`，不启动平台）
- `platformStartFailureResetsScanningState`（BLE-05：平台启动失败 → 失败回调 + `isScanning()==false`）

### 12.4 验证
| 项 | 结果 |
|---|---|
| `:lib_bluetooth:compileDebugJavaWithJavac` + `:lib_bluetooth:testDebugUnitTest` | BUILD SUCCESSFUL；**26 套件 / 104 用例 / 0 失败**（新增 1 套件 +3 用例） |
| `:app:testDebugUnitTest` | **56 套件 / 420 用例 / 0 失败** |
| 真机回归（motorola edge 60 pro / API35，APK sha `2eed5b6e…`） | `[ble-scan] scan started gen=1`，列表出现 Vidda V12（HS01），按钮“停止搜索”、无 crash（`.rea-captures/round4_scan_after_libfix/screen.png`） |

---

## 13. Round 6：应用层扫描 API 收敛（去冗余重载）

> 目标：按审计 D-G 收敛扫描入口，消除“单条 / 带失败 / 批量”多重重载造成的实现分叉与冗余补丁面。

### 13.1 变更（文件:行）
| 位置 | 变更 |
|---|---|
| `domain/device/port/DevicePort.java` | 删除 `startScan(Consumer<ScanResult>, Runnable)`（抽象）与 `startScan(Consumer, Consumer<ScanFailure>, Runnable)`（默认）；仅保留唯一入口 `startScanBatch(onResults,onFailed,onFinished)` |
| `domain/device/usecase/ScanDevices.java` | 删除 `start(...)` / `start(...,onFailed,...)` 两重载，仅保留 `startBatch(...)` |
| `provider/lib_bluetooth/HsDevicePort.java` | 删除两个 `startScan(...)` 适配实现，只实现 `startScanBatch(...)`（批量映射 HS01/HS02） |
| `data/device/IdleDevicePort.java` | 单条 `startScan` → `startScanBatch` |
| `testing/device/FakeTestDevicePort.java` | 删除单条 `startScan` 与 `activeResultConsumer` 分支，仅保留批量 |
| 测试 | `IdleDevicePortTest`、`HsDevicePortTest` 由 `port.startScan(...)` 改为 `port.startScanBatch(...)` |

### 13.2 效果
- 扫描入口 **3 → 1**（`DevicePort` 与 `ScanDevices` 各只剩 `startScanBatch`），消除三套实现分叉；调用点唯一（`ScanViewModel.startScan → ScanDevices.startBatch → DevicePort.startScanBatch`）。
- 保留：`stopScan/isScanning/observe/snapshot/connect/disconnect` 不变。

### 13.3 验证
- `:app:compileDebugJavaWithJavac` + `:app:compileDebugUnitTestJavaWithJavac` + `:app:testDebugUnitTest` → BUILD SUCCESSFUL；**56 套件 / 420 用例 / 0 失败**
- `:lib_bluetooth:testDebugUnitTest` → **26 套件 / 104 用例 / 0 失败**（无回归）

---

## 14. Round 7：协议层入站拼帧/去粘包（H-03）

### 14.1 问题（审计 H-03）
`YoBleCommandLink.NotifyWatcher.onCharacteristicChanged(value)`（`YoBleCommandLink.java:286-296`）把每次通知的整段 `byte[]` 当作**一个完整帧** hex 编码后交给 `HsProtocolEngine.LinkListener.onInbound`（`HsProtocolEngine.java:154-164`）→ `InboundFrameDemux`。若 BLE 通知分片（跨帧头）或粘包（一帧含多帧），`ProtocolValues.isProtocolFrame` 判失败 → **丢帧/串帧**。

### 14.2 修复
- 新增 `internal/protocol/inbound/HsInboundFrameAssembler.java`（流式拼帧器）：
  - 按协议信封 `6 位前缀 + 4 位载荷长度 + 载荷 + "1234"` 切帧；`expected = 14 + payloadBytes*2`。
  - 组合电量上报 `99EC800003…` 长度字段按条目数计、帧长固定 **26 字符**，单独处理。
  - 非法对齐按 **1 字节重同步**（`buffer.delete(0,2)`）并可在一个分片内连续重试，避免死循环/卡帧；缓冲上限 8192 字符防无界。
- `HsProtocolEngine`：
  - 增 `HsInboundFrameAssembler inboundAssembler`；
  - `LinkListener.onInbound`：`HsHex.encode(value)` → `inboundAssembler.accept(chunk)` → 对每个完整帧 `demux.onFrame(...)`（协议线程内）；
  - 会话就绪/断开/重置时 `inboundAssembler.reset()`，避免跨会话残留。

### 14.3 新增单测（证据）
`biz_ggec_hs/src/test/java/com/yovo/biz_ggec_hs/internal/protocol/inbound/HsInboundFrameAssemblerTest.java`（7 用例）：
`completeFramePassesThroughUnchanged` / `fragmentedFrameIsReassembled` / `coalescedFramesAreSplit` / `combinedBatteryFixedLengthIsRecognized` / `garbagePrefixResyncsToValidFrame` / `partialFrameWaitsThenCompletes` / `resetDropsPendingBuffer`。

### 14.4 验证
| 项 | 结果 |
|---|---|
| `:biz_ggec_hs:compileDebugJavaWithJavac` + `:biz_ggec_hs:testDebugUnitTest` + `:biz_ggec_hs:checkBizGgecHsBoundary` | BUILD SUCCESSFUL；**4 套件 / 17 用例 / 0 失败**（+7 用例）；边界 PASS |
| `:lib_bluetooth:testDebugUnitTest` | 26 套件 / 104 用例 / 0 失败 |
| `:app:testDebugUnitTest` | 56 套件 / 420 用例 / 0 失败 |

> 未改 **H-04**（长帧单写 + `WITHOUT_RESPONSE` 不分片）：需按实际 MTU 分片（`writeSplit`）与真机低 MTU 验证，留待后续。

---

## 15. Round 8：协议层长帧 MTU 感知分片写（H-04）

### 15.1 问题（审计 H-04）
`YoBleCommandLink.write` 恒定 `client.write(..., WITHOUT_RESPONSE)` 单包整写（`YoBleCommandLink.java:136-157`）。目标机型 MTU 通常 495（真机已验证），但若协商回落 ATT 23（单包载荷上限 20B），改名 / CustomEQ / Raw / 配对列表回包等长帧可能被栈截断且不报错。

### 15.2 修复
- `YoBleCommandLink.write`：写入前经 `client.getConnectionSnapshot(target)` 取当前 `BleConnectionSnapshot.maxWritePayloadBytes(WITHOUT_RESPONSE)`：
  - `payload <= maxPayload` → 原单包 `write(...)`（行为不变）；
  - `payload > maxPayload` → `client.writeSplit(..., customSplitter=null, BleTransferProgressObserver)`（默认 splitter 依 MTU 切片），把 `onCompleted/onFailed` 映射回 `HsCommandLink.WriteCallback`（超时→`onTimeout`，其余→状态码）。
  - 快照不可用时按 `DEFAULT_ATT_MTU - 3` 保守估算，交给分片写兜底。
- 抽 `handleWriteFailure(...)` 统一超时/状态码映射，单写与分片写共用，避免重复分支。

### 15.3 验证
| 项 | 结果 |
|---|---|
| `:biz_ggec_hs:compileDebugJavaWithJavac` + `:biz_ggec_hs:checkBizGgecHsBoundary` | BUILD SUCCESSFUL / PASS |
| `:biz_ggec_hs:testDebugUnitTest` | 4 套件 / 17 用例 / 0 失败 |
| `:lib_bluetooth:testDebugUnitTest` | 26 套件 / 104 用例 / 0 失败 |
| `:app:testDebugUnitTest` | 56 套件 / 420 用例 / 0 失败 |

> 说明：目标真机 MTU=495 时该分支不会触发（单包足够），需专门的低 MTU / 长帧命令场景验证；代码路径已按 MTU 动态选择，默认行为不变。
>
> 附：`ScanUiState.defaultFilterOptions` 去硬编码（D-F）本轮**暂缓**——直接移除会与 `ScanViewModel.selectFilter` 的“默认过滤项”契约耦合（`ScanViewModelTest.suppressesSubThreshold…` 依赖 HS02 为合法过滤项），需配套调整过滤契约后再收敛，列入后续。

---

## 16. Round 9：应用层过滤契约解耦 + 过滤项单一来源（D-F）

### 16.1 问题
- `ScanUiState.defaultFilterOptions()` 硬编码 `HS01/HS02`，与领域目录 `LoadDeviceProfiles.models()`（经 `ScanViewModel.buildFilterOptions`）形成**两份真源**，目录变化会漂移。
- `ScanViewModel.normalizeFilter` 要求候选必须命中 `filterOptions`，否则回落 `ALL` —— 把“过滤状态（modelId）”与“下拉可选项集合（视图枚举）”耦合，导致无目录时无法按型号过滤。

### 16.2 修复
- `ScanViewModel.normalizeFilter`：改为纯数据归一（空→`ALL`，否则原样接受），**过滤状态不再依赖 filterOptions 成员判定**（视图选项仅是可选集，二者解耦）。
- `ScanUiState.defaultFilterOptions()`：只保留 `ALL`，型号筛选项唯一来源改为领域目录（经 ViewModel 构造）。
- 效果：真实 App（`DeviceGraph` 注入 `LoadDeviceProfiles`）选项仍来自目录（HS01/HS02）；无目录的降级场景也能按 modelId 正确过滤。

### 16.3 新增/调整单测
- 新增 `ScanViewModelTest.filtersByModelIdEvenWhenCatalogOptionsAbsent`：无目录（options 仅 `ALL`）时 `selectFilter("HS02")` 仍正确过滤。
- `startsIdleAndIdlePortFinishesWithoutFakeResults`：初始 `filterOptions` 断言由 3 项改为 1 项（`ALL`）。

### 16.4 验证
| 项 | 结果 |
|---|---|
| `:app:compileDebugJavaWithJavac` + `:app:compileDebugUnitTestJavaWithJavac` + `:app:testDebugUnitTest` | BUILD SUCCESSFUL；**56 套件 / 421 用例 / 0 失败**（+1 用例） |
| `:lib_bluetooth:testDebugUnitTest` | 26 套件 / 104 用例 / 0 失败 |
| `:biz_ggec_hs:testDebugUnitTest` | 4 套件 / 17 用例 / 0 失败 |

真机回归（APK sha `e83c2b86…`）：进入扫描（发现多台 HS01）→ 下拉切 `HS02` → `筛选设备类型 HS02` + 「当前筛选下暂无设备」；切换即时生效、无崩溃（`.rea-captures/round5_filter_hs02/screen.png`）。

---

## 17. Round 10：第二轮冗余/死代码清理（协议 + 蓝牙）

> 目标：继续按“全仓库仅定义处、无调用”证据清除死代码，降低维护面（H-08 / BLE D-04 / 协议死方法）。

### 17.1 删除项（grep 证实无外部调用）
| 项 | 位置 | 证据 |
|---|---|---|
| `CommandScheduler.setReady(boolean)` | `internal/protocol/CommandScheduler.java:333-335` | 全仓库无 `setReady(` 调用（引擎统一走 `setSession`） |
| `CommandScheduler.onResponseFrame(String)` / `onResponseFrame(long,String)` | `CommandScheduler.java:409-415` | 全仓库无调用（demux 走 `onResponseCandidate`） |
| `DeviceEventPipeline.domain()` | `internal/protocol/inbound/DeviceEventPipeline.java:48-51` | 无 `.domain()` 调用 |
| `DeviceEventPipeline.clearListeners()/resetState()/resetStateKeepingBattery()/clearBattery()` | `DeviceEventPipeline.java:168-182` | 无调用者（会话清理走引擎/会话层，不经这些包装） |
| `YoBleScanAdvertiseStatus.describeScan/describeAdvertise` | `internal/ble/error/YoBleScanAdvertiseStatus.java:46-58,72-82` | 无调用者（错误码已映射为 `BleErrorCode`） |

保留：`DeviceEventPipeline.notifyFileExportSucceeded/Failed`（文件导出功能预留钩子，路由侧仅在 pipeline 调用；判定“预留”保留并记录）。

### 17.2 验证
| 项 | 结果 |
|---|---|
| `:lib_bluetooth:compileDebugJavaWithJavac` + `:lib_bluetooth:testDebugUnitTest` | BUILD SUCCESSFUL；26 套件 / 104 用例 / 0 失败 |
| `:biz_ggec_hs:compileDebugJavaWithJavac` + `:biz_ggec_hs:testDebugUnitTest` + `:biz_ggec_hs:checkBizGgecHsBoundary` | BUILD SUCCESSFUL；4 套件 / 17 用例 / 0 失败；边界 PASS |
| `:app:testDebugUnitTest` | 56 套件 / 421 用例 / 0 失败 |

---

## 18. Round 11：蓝牙层 BLE-03 —— 扫描平台 binder 异步化（消除域级冻结）

### 18.1 问题（审计 BLE-03）
`YoBleAndroidScanPlatform.start/stop/flushPendingScanResults`（含 `supportsHardwareBatching()` 适配器查询）此前都在**唯一 BLE state 线程**同步执行（`YoBleScanController.launchPlatformScan/stopActiveInternal/flushBatchScanResults`）。底层 `BluetoothLeScanner` binder 一旦阻塞，会冻结整个 BLE 域（扫描+连接+GATT+配对全排队），表现为“扫描卡死/无法再扫”。

### 18.2 修复（`YoBleScanController`）
- 新增**专用单线程平台执行器** `platformExecutor`（线程名 `yo-ble-scan-platform`，daemon），承载全部平台 binder 调用；state 线程只做状态机。
- `launchPlatformScan`：平台启动 + 硬件批量能力查询投递到 `platformExecutor`，结果经 `runtime.state(onPlatformStartResult(...))` 回投 state 后再改状态/挂定时器。
- `stopActiveInternal` / `restartLongScan` / `flushBatchScanResults`：平台 stop/flush 同样投递到 `platformExecutor`；单线程 FIFO 保证“停→启”顺序不乱。
- 竞态处理：停止/切换会话时 `activeGeneration=-1`，在途启动结果回到 `onPlatformStartResult` 见 generation 不匹配 → 若平台已启动则自行 `stopPlatformScan()`，杜绝“停止后又被 late-start 拉起”。
- `startInFlight` 生命周期改为：非平台启动分支（preflight/限流/延迟）即时清零，平台启动分支由 `onPlatformStartResult` 清零。
- `release()`：state 线程内先 `stopActiveInternal(false)`（enqueue stop）再 `platformExecutor.shutdown()`（优雅关闭，已排队任务仍执行）。

### 18.3 新增单测
`YoBleScanControllerTest.platformStartRunsOnDedicatedThreadNotStateThread`：断言 `startPlatformScan` 运行于线程 **`yo-ble-scan-platform`**（≠ BLE state 线程），且 `scanning` 置位、`stopScan` 后回落。

### 18.4 验证
| 项 | 结果 |
|---|---|
| `:lib_bluetooth` compile + test + 4 边界（Provider/ApiSurface/LogSeam/NoStrayLogging） | BUILD SUCCESSFUL；**26 套件 / 105 用例 / 0 失败**；边界 PASS |
| `:biz_ggec_hs:testDebugUnitTest` + `checkBizGgecHsBoundary` | 4 套件 / 17 用例 / 0 失败；PASS |
| `:app:testDebugUnitTest` | 56 套件 / 421 用例 / 0 失败 |
| 真机（motorola edge 60 pro / API35，APK sha `67af33c9…`） | `scan started gen=1` → 发现 3 台 HS01 → 停止（`scan stopped gen=1`、“发现 3 台附近耳机/开始搜索”）→ 再启（`scan started gen=2`）→ 再次发现设备、“停止搜索”；无 FATAL（`.rea-captures/round7_ble03_scan/screen.png`） |

> 说明：真机普通扫描 MTU/时序下未见冻结；弱 ROM 长扫场景仍建议持续观察（残余风险降为“已知可缓解”）。

---

## 19. Round 12：BLE 扫描实时化 + 扫描架构对齐业界（nRF / Nordic / RxAndroidBle）

> 详细设计见独立文档 **`docs/device/ble_scan_architecture_design.md`**（参考项目代码已拉取到 `E:\System\Temp\ble-ref`：Nordic `Android-BLE-Library`、`RxAndroidBle`）。

### 19.1 实时逐广播（G1，本轮已修）
- 问题：`HsDevicePort` 以 `reportDelayMillis=200` 配置扫描，Android 会走批量窗口（`onBatchScanResults`），表现为“过一会儿一簇出现”。Nordic 官方扫描示例明确 `setReportDelay(0) // immediate`（`ScannerRepository.kt:47-50`），RxAndroidBle `onScanResult` 逐条 `onNext`（`ScanOperationApi21.java:67-86`）。
- 修复：`HsDevicePort.SCAN_REPORT_DELAY_MILLIS` 200 → **0**（`reportDelay=0`）。链路变为：`ScanCallback.onScanResult`（逐条）→ `handlePlatformResult`（逐条）→ `onDeviceFound`（逐条）→ `ScanViewModel` 新设备立即发布（RSSI-only 才 300ms 节流）。
- 真机：扫描进行中（"正在搜索附近的耳机…/停止搜索"）即已列出 4 台 HS01（`.rea-captures/round8_rt_t1/screen.png`），无 FATAL。

### 19.2 架构审查与对齐（详见设计文档 §1–§8）
- 提取业界要点：reportDelay=0 实时；逐条 onNext（批量回调也拆条）；native filter + 软件复核双层过滤；ScanMode/CallbackType 软件仿真；API24 30s/5 次限流前置校验；设备合并缓存 + MATCH_LOST 过期；adapter/位置自愈。
- 现状已具备：`BleScanConfig` 设置面、`YoBleScanThrottleGuard`、`YoBleScanCallbackEmulator`、`YoBleScanFilterMatcher`、`YoBleDeviceSnapshotStore`、`YoBleTimelineRecorder`、长扫 25min 重启、错误码分类。
- 主要差距（设计文档 §5 Gap 表）：G2 显式 `ScanState` 枚举、G3 设置仿真、G5 native filter 下推、G6/G7 设备合并缓存与 MATCH_LOST 下沉、G8 adapter 自动重启。
- 目标架构/状态机/数据链路见设计文档 §6–§7（`ScanState{IDLE,STARTING,SCANNING,STOPPING,FAILED,THROTTLED_WAIT}`、`ScanDevice` 合并模型、事件驱动实时上抛）。

### 19.3 验证
`HsDevicePortTest` 断言更新通过；`:app:testDebugUnitTest` 56 套件 / 421 用例 / 0 失败；真机实时列表见截图。

---

## 20. Round 13：扫描状态机显式化 + 逐项深度审查文档

> 深度逐项审查与逐项架构设计见 **`docs/device/ble_scan_deep_audit_and_design.md`**（平台/控制器/过滤/仿真/限流/快照/诊断/模型/App 全链路，每项含 现状(file:line)/问题/设计）；总体设计见 `docs/device/ble_scan_architecture_design.md`。

- ✅ G1 实时逐广播：`HsDevicePort.SCAN_REPORT_DELAY_MILLIS=0`（reportDelay=0，逐条 `onScanResult`）。
- ✅ G2 显式状态机：`YoBleScanController.ScanState{IDLE,STARTING,SCANNING,STOPPING,FAILED,THROTTLED_WAIT}` + `state()`；新增 2 个状态机单测。
- 逐项深度审查已识别并文档化：过滤掩码越界风险(3.1)、未知地址全零合并(BLE-09)、`MAX_SEEN` 插入序驱逐(BLE-11)、设备聚合三处重复(G6)、MATCH_LOST 未启用(G7)、native filter 未下推(G5)、ScanMode 未仿真(G3)、adapter 无自动重启(G8) 等，均给出逐项设计。
- 验证：`:lib_bluetooth` 26 套件/107 用例/0 失败；`:app` 56 套件/421 用例/0 失败；真机实时列表 `.rea-captures/round8_rt_t1/screen.png`。

---

## 21. Round 14：设备过期移除（G7）端到端

- lib：`YoBleScanCallbackEmulator.sweepLost` 改为只要 `matchLostTimeoutMillis>0`（默认 10s）即对 **ALL_MATCHES** 也产出 `MATCH_LOST` 并移除本地项（原仅 LOST 回调类型）；`YoBleScanController.onPlatformStartResult` 在 `matchLostTimeoutMillis>0` 时启用 `scheduleLostSweep`，经 `observer.onScanResultLost` 上抛。
- app：`DevicePort` 增带 `onRemoved` 的 `startScanBatch` 默认重载（向后兼容）；`HsDevicePort` 配置 `matchLostTimeoutMillis(10s)`、`BleScanObserver.onScanResultLost` → 由 `lastMappedByAddress` 取回领域 `ScanResult` → `onRemoved`；`ScanDevices.startBatch(...4)`；`ScanViewModel.onScanRemoved` 从结果集剔除并刷新（`sameDevice` 匹配）。
- 单测：`YoBleScanCallbackEmulatorTest`（ALL_MATCHES 过期→MATCH_LOST / 未过期不删）；`ScanViewModelTest.removesExpiredDeviceFromResults`。
- 验证：`:lib_bluetooth` **28 套件/114 用例/0 失败**；`:app` **56 套件/422 用例/0 失败**；真机扫描 T+6s/T+12s 4 台设备持续在线、无 FATAL（APK sha `4935d035…`）。

---

## 22. Round 15：适配器自愈（G8）

- `YoBleScanController`：`onAdapterTurnedOff()` 在中断前暂存 `resumeConfig/resumeObserver`；新增 `onAdapterTurnedOn()`，在适配器重新开启且未在扫描时按原配置 `startScanInternal` 自动恢复；`scan()`/`release()` 清理暂存。
- `YoBleModuleCore.onAdapterStateChanged`：`current==ON && previous!=ON` 时调用 `scanController.onAdapterTurnedOn()`。
- 单测：`YoBleScanControllerTest.resumesScanAfterAdapterTurnsBackOn`（扫描→关闭→开启→恢复 SCANNING，平台启动次数递增）。
- 验证：`:lib_bluetooth` **28 套件/115 用例/0 失败**；`:app` **56 套件/422 用例/0 失败**。

---

## 23. Round 16：扫描会话统计（G10）

- `YoBleScanController` 新增计数：`statStarts/statFounds/statLosts/statFails/statStops/statLastDuration`；record `Stats(starts,founds,losts,fails,stops,lastDurationMs)` + `stats()`。
  - 启动成功 `statStarts++` 并记 activeStart；`handlePlatformResult/Batch` 累加 `statFounds`；`scheduleLostSweep` 累加 `statLosts`；各失败分支 `statFails++`；`stopActiveInternal`（wasScanning）`statStops++` 并计算时长。
- `YoBleModuleCore.exportDiagnosticReport()`：把 `scan stats …` 写入时间线（随后进入 diagnostic report），确保统计被实际使用而非死代码。
- 单测：`YoBleScanControllerTest.statsCountStartsFoundsAndStops`（启动=1、喂入结果 founds≥1、停止 stops≥1）。
- 验证：`:lib_bluetooth` **28 套件/116 用例/0 失败**；`:app` **56 套件/422 用例/0 失败**；真机扫描 4 台、无 FATAL（APK sha `72350b8c…`）。

---

## 24. Round 17：设备聚合下沉 + LRU 驱逐（G6 / BLE-11）

- `YoBleScanCallbackEmulator.SeenEntry` 扩展为设备聚合：`firstSeenMillis / lastSeenMillis / seenCount / lastDispatchedMillis / notifiedFirstMatch / lastResult`；新增 `activeDeviceCount()/contains()`。
- **BLE-11 修复**：容量(256)驱逐由“插入序”改为 **按 lastSeen 的 LRU**（`evictLeastRecentlySeen`），避免活跃设备因插入早被误逐。
- `YoBleScanController.activeDeviceCount()` → `YoBleModuleCore.exportDiagnosticReport` 输出 `devices=`。
- 单测：`YoBleScanCallbackEmulatorTest.evictionPrefersLeastRecentlySeen`（填满 256→刷新 0→加第 257→逐出最久未见(1)，保留活跃(0)）。
- 验证：`:lib_bluetooth` **28 套件/117 用例/0 失败**；`:app` **56 套件/422 用例/0 失败**。

---

## 25. Round 18：ScanMode 软件仿真（G3）

- 新增 `YoBleScanModeWindows`（`internal/ble/scan`）：`isWithin(mode, elapsedMs)` 实现 Rx `ScanSettingsEmulator` 的重复窗口语义——`LOW_POWER/OPPORTUNISTIC` 每 5s 前 500ms、`BALANCED` 每 5s 前 2500ms、`LOW_LATENCY` 不限制。
- `YoBleScanController.handlePlatformResult/handlePlatformBatch` 增加 `withinScanModeWindow()` 判定（以 `statActiveStartMillis` 为窗口起点，`clockMillis` 计时），ROM 不按 ScanMode 降占空比时软件兜底。
- 单测：`YoBleScanModeWindowsTest`（LOW_LATENCY 恒真；LOW_POWER 500ms 边界；BALANCED 2500ms 边界；OPPORTUNISTIC 同 LOW_POWER）。
- 验证：`:lib_bluetooth` **29 套件/121 用例/0 失败**；`:app` **56 套件/422 用例/0 失败**；HS 端口用 LOW_LATENCY → 行为不变，真机回归扫描正常。

---

## 26. BLE 扫描模块对齐 · 完成总结（Round 19 收口）

| 项 | 内容 | 状态 |
|---|---|---|
| G1 实时逐广播 | `HsDevicePort.reportDelay=0`，逐条 `onScanResult`→即时上抛（真机验证） | ✅ |
| G2 显式状态机 | `YoBleScanController.ScanState` 枚举 + `state()` 单一读入口（IDLE/STARTING/SCANNING/STOPPING/FAILED/THROTTLED_WAIT）+ 状态单测 | ✅（派生读；行为迁移点已集中记录） |
| G3 ScanMode 仿真 | `YoBleScanModeWindows` 重复窗口（LOW_POWER 500ms、BALANCED 2500ms / 5s） | ✅ |
| G6 设备聚合 + BLE-11 | `YoBleScanCallbackEmulator` 聚合 firstSeen/lastSeen/seenCount；驱逐改 LRU | ✅ |
| G7 设备过期移除 | ALL_MATCHES + matchLostTimeout>0 sweep → `onScanResultLost` → App 列表移除 | ✅ |
| G8 适配器自愈 | 关闭暂存、开启按原配置自动重启（YoBleModuleCore 挂钩） | ✅ |
| G10 会话统计 | `Stats(starts/founds/losts/fails/stops/lastMs)` 入诊断报告 | ✅ |
| 安全 | 掩码长度软件保护（纵深防御；Builder 已强校验） | ✅ |
| 审查/设计文档 | `ble_scan_deep_audit_and_design.md`（逐类逐方法逐字段 file:line）、`ble_scan_architecture_design.md`（架构/状态机/数据链路/Gap/路线，参考 Nordic/Rx） | ✅ |

**最终回归**：`:lib_bluetooth` 29 套件/121 用例/0 失败；`:biz_ggec_hs` 4 套件/17 用例/0 失败；`:app` 56 套件/422 用例/0 失败；`:app:assembleDebug` BUILD SUCCESSFUL；真机扫描实时列表、停止/重启、无 FATAL。

**残余（非阻塞，已记录设计）**：`callbackType` 仍内嵌于 `BleScanResult` 值对象（供回调打标）；`observedAtMillis` 与 identity 时间戳并存；G2 可进一步把迁移收敛为单一 `transition(event)`（当前派生 `state()` 已满足可观测与测试）。

---

## 27. Round 20：G2 状态字段收口 + BLE-09 未知地址

- **G2 收口**：`YoBleScanController` 以 `private volatile ScanState state` 为**唯一状态字段**，所有迁移经 `enterState(ScanState)`（记录 `scan-state` 时间线）；`state()` 直接返回字段。移除 `lastStartFailed/stopping` 派生标志。迁移点：`scan()→STARTING`、preflight/FAIL_FAST/released/platform-fail→`FAILED`、delay→`THROTTLED_WAIT`、成功→`SCANNING`、`stopActiveInternal→STOPPING/IDLE`、`handlePlatformFailure→FAILED`、`onAdapterTurnedOn→STARTING`。状态单测（IDLE→SCANNING→IDLE、FAILED）通过。
- **BLE-09**：`YoBleScanCompat.toBleScanResult` 空地址不再用全零 MAC，改为按 `System.identityHashCode(dev)` 生成 `02:xx:xx:xx:xx:xx` 本地占位 ID，避免多台异常设备合并。
- 验证：`:lib_bluetooth` **29 套件/121 用例/0 失败**；`:app` **56 套件/422 用例/0 失败**；真机状态日志 `scan-state STARTING → SCANNING`（无 STOPPING/IDLE 抖动）、4 台设备、无 FATAL（APK sha `c59c8613…`）。

