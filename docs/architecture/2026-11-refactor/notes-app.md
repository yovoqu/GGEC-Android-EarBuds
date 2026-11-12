# app 侧实施记录（2026-11 重构，core 层）

> 权威契约：`docs/architecture/2026-11-refactor/design.md`（§1.2、§6、§6.1、§7.2、§8）。
> 写作用域：`app/**`（UI 渲染层 `ui/device/**` 由 appui 负责）。
> 本文只记录 app core 层（provider / data / domain / wiring / 门禁 / 测试）的落地证据。

## 1. 数据链路（设计前 / 设计后，按 design.md §7 格式）

证据等级口径（YoAgentDocs `rules/common/architecture.md`「数据链路证据的粒度」）：

| 等级 | 拿到什么 | 能否支撑「这条路径被走到」 |
|------|----------|--------------------------|
| 已验证 | 代码位置 + 可复现运行观察（真机日志、断点、抓包） | 能 |
| 已读到 | 只有代码位置 | 不能，只能说明「这条边存在」 |
| 推断 | 命名 / 相邻代码 / 注释 | 不能 |
| 假设 | 无出处 | 不能，不得写进结论 |

**本仓现状：未连接真机（见 §7），因此所有设备侧节点一律标「已读到」。** 单测（`HsDevicePortAdapterTest`、`HsCapabilityCatalogAdapterTest`、`HsValueMapperTest`）能证明「biz 事件 → 领域快照」的**变换本身**在受控输入下被执行；它证明不了真实 BLE/协议路径被走到，所以不升级任何节点等级。

### 1.1 链路 A：扫描

**设计前**

| # | 节点 | 代码位置 | 证据等级 |
|---|------|----------|----------|
| A1 | 入口 `ScanScreen`（用户点「扫描」） | `ui/device/scan/ScanScreen.java` | 已读到 |
| A2 | 状态持有 `ScanViewModel.toggleScan/enterScan` | `ui/device/scan/ScanViewModel.java` | 已读到 |
| A3 | 用例 `ScanDevices.startBatch` | `domain/device/usecase/ScanDevices.java` | 已读到 |
| A4 | 端口 `DevicePort`（旧签名，失败类型为内嵌 `ScanFailure`） | `domain/device/port/DevicePort.java`（旧版，已重写） | 已读到 |
| A5 | 适配 `HsDevicePort.startScanBatch` | `provider/lib_bluetooth/HsDevicePort.java`（已删除） | 已读到 |
| A6 | SDK 入口 `YoHsSdk.open → HsAssembly → YoHsNearby` | `biz_ggec_hs/.../api/HsRuntime.java`、`assembly/HsAssembly.java`（均已删除） | 已读到 |
| A7 | 副作用终点 `YoBleCommandLink.bleClient().subscribeScan` | `biz_ggec_hs/provider/lib_bluetooth/YoBleCommandLink.java` | 已读到 |

**设计后**

| # | 节点 | 代码位置 | 证据等级 |
|---|------|----------|----------|
| A1' | 入口 `ScanScreen` | `ui/device/scan/ScanScreen.java` | 已读到 |
| A2' | 状态持有 `ScanViewModel` | `ui/device/scan/ScanViewModel.java` | 已读到 |
| A3' | 用例 `ScanDevices.startBatch(Consumer<DeviceFailure>)` | `domain/device/usecase/ScanDevices.java` | 已读到 |
| A4' | 端口 `DevicePort.startScanBatch`（唯一四参签名） | `domain/device/port/DevicePort.java` | 已读到 |
| A5' | 装配 `DeviceGraph` → `HsDeviceProviders.open(app)`（一条 BLE 客户端） | `wiring/DeviceGraph.java`、`provider/biz_ggec_hs/HsDeviceProviders.java` | 已读到 |
| A6' | 适配 `HsDevicePortAdapter.startScanBatch` → `HsNearbyObserver` 四回调 → `ScanResult`/`DeviceFailure` | `provider/biz_ggec_hs/HsDevicePortAdapter.java` | 已读到（变换有单测；真实回调未观察） |
| A7' | SDK `YoHsSdk.nearby()` → `HsNearbyScanner` | `biz_ggec_hs/YoHsSdk.java`、`biz_ggec_hs/provider/lib_bluetooth/HsNearbyScanner.java` | 已读到 |
| A8' | 副作用终点 `lib_bluetooth` BLE 扫描订阅 | `lib_bluetooth`（biz 侧 `YoBleCommandLink`） | 已读到 |

### 1.2 链路 B：机型 / 能力目录

**设计前**

| # | 节点 | 代码位置 | 证据等级 |
|---|------|----------|----------|
| B1 | 入口 `DeviceHomePage` | `ui/device/home/DeviceHomePage.java` | 已读到 |
| B2 | 状态持有 `DeviceHomeViewModel` | `ui/device/home/DeviceHomeViewModel.java` | 已读到 |
| B3 | 用例 `LoadDeviceProfiles.models/profileFor` | `domain/device/usecase/LoadDeviceProfiles.java`（已删除） | 已读到 |
| B4 | 目录端口 `DeviceProfileCatalog` | `domain/device/port/DeviceProfileCatalog.java`（已删除） | 已读到 |
| B5 | 数据源（app 硬编码机型表 `HS01_CAPS`/`HS02_EXTRA`） | `data/device/DeviceProfileCatalogImpl.java`（已删除） | 已读到 |

**设计后**

| # | 节点 | 代码位置 | 证据等级 |
|---|------|----------|----------|
| B1' | 入口 `DeviceHomePage` | `ui/device/home/DeviceHomePage.java` | 已读到 |
| B2' | 状态持有 `DeviceHomeViewModel(catalog, …)` | `ui/device/home/DeviceHomeViewModel.java` | 已读到 |
| B3' | 目录端口 `DeviceCapabilityCatalog.models()/model(id)` | `domain/device/port/DeviceCapabilityCatalog.java` | 已读到 |
| B4' | 投影 `HsCapabilityCatalogAdapter`（构造期一次投影，无静态机型表） | `provider/biz_ggec_hs/HsCapabilityCatalogAdapter.java` | 已读到（投影有单测） |
| B5' | 数据源 `YoHsSdk.catalog()` → `HsDeviceCatalog`（HS01/HS02 知识已迁入 biz `internal/device/HsCatalogImpl`） | `biz_ggec_hs/YoHsSdk.java`、`biz_ggec_hs/internal/device/HsCatalogImpl.java` | 已读到 |

### 1.3 链路 C：配置写入 / 动作

**设计前**

| # | 节点 | 代码位置 | 证据等级 |
|---|------|----------|----------|
| C1 | 入口 `DeviceSettingsScreen` | `ui/device/settings/DeviceSettingsScreen.java` | 已读到 |
| C2 | 状态持有 `DeviceSettingsViewModel` | `ui/device/settings/DeviceSettingsViewModel.java` | 已读到 |
| C3 | 用例 `EditDeviceControls.update` | `domain/device/usecase/EditDeviceControls.java`（已删除） | 已读到 |
| C4 | 端口 `DeviceControlsPort` | `domain/device/port/DeviceControlsPort.java`（已删除） | 已读到 |
| C5 | 实现 `EmptyDeviceControlsPort.update`（**空实现，写入从未落地**） | `data/device/EmptyDeviceControlsPort.java`（已删除） | 已读到 |

**设计后**

| # | 节点 | 代码位置 | 证据等级 |
|---|------|----------|----------|
| C1' | 入口 `DeviceSettingsScreen` | `ui/device/settings/DeviceSettingsScreen.java` | 已读到 |
| C2' | 状态持有 `DeviceSettingsViewModel(group, catalog, …)` | `ui/device/settings/DeviceSettingsViewModel.java` | 已读到 |
| C3' | 用例 `WriteDeviceControl.execute(capabilityId, ControlValue)` / `RunDeviceAction.execute(DeviceAction)` | `domain/device/usecase/{WriteDeviceControl,RunDeviceAction}.java` | 已读到（有空 id / null 参数单测） |
| C4' | 端口 `DevicePort.write/perform` | `domain/device/port/DevicePort.java` | 已读到 |
| C5' | 适配 `HsDevicePortAdapter.write/perform` → `HsValueMapper.capabilityOf/toHsControlValue/toHsAction` | `provider/biz_ggec_hs/{HsDevicePortAdapter,HsValueMapper}.java` | 已读到（映射穷尽有单测） |
| C6' | SDK `HsSession.write/perform` | `biz_ggec_hs/api/HsSession.java` | 已读到 |
| C7' | 能力路由 `HsCapabilityRouter` → 协议命令 | `biz_ggec_hs/internal/device/HsCapabilityRouter.java` | 已读到 |
| C8' | 副作用终点 `provider/lib_bluetooth` 帧下发 | `biz_ggec_hs/provider/lib_bluetooth/YoBleCommandLink.java` | 已读到 |

### 1.4 链路 D：扫描权限

**设计前**

| # | 节点 | 代码位置 | 证据等级 |
|---|------|----------|----------|
| D1 | `ScanActivity.requestScanPermissions` | `ui/device/scan/ScanActivity.java`（旧版） | 已读到 |
| D2 | app `PermissionProvider.requestBluetoothIfNeeded`（**ui 直连 provider**，门禁里靠白名单行放行） | `provider/lib_common/PermissionProvider.java`、`app/build.gradle` 旧 `checkDeviceLayerImports` | 已读到 |
| D3 | `YoPermission.requestIfNeeded` | `lib_common/system/permission` | 已读到 |

**设计后**

| # | 节点 | 代码位置 | 证据等级 |
|---|------|----------|----------|
| D1' | `ScanActivity`：只拿 `ScanPermissionRequest`，`onRequestPermissionsResult` 转发 `onResult` | `ui/device/scan/ScanActivity.java` | 已读到 |
| D2' | ui 自有宿主接口 `ScanPermissionRequest/ScanPermissionResult`（不含任何 SDK 类型） | `ui/device/scan/ScanPermissionRequest.java`、`ScanPermissionResult.java` | 已读到 |
| D3' | 装配注入 `DeviceGraph.scanPermissionRequest(Activity)`（按宿主构造，不缓存） | `wiring/DeviceGraph.java` | 已读到 |
| D4' | 适配 `BluetoothScanPermissionRequest` → `PermissionProvider`（`lib_common` 调用单点） | `provider/lib_common/permission/{BluetoothScanPermissionRequest,PermissionProvider}.java` | 已读到 |
| D5' | 副作用终点 `YoPermission.requestIfNeeded / handleRequestPermissionsResult` | `lib_common/system/permission/YoPermission.java` | 已读到 |

门禁现在把「ui → provider」这条边直接禁死（`checkAppLayering`），因此 D2 那类直连不会再出现。

### 1.5 链路 E：双连接 / 录音列表（LIST 类能力）

| # | 节点 | 代码位置 | 证据等级 |
|---|------|----------|----------|
| E1 | 设备主动上报 | `biz_ggec_hs/internal/protocol/event/ProtocolEventRouter.java` → `HsDeviceEvent` | 已读到 |
| E2 | 会话派发 `HsSessionObserver.onDeviceEvent` | `biz_ggec_hs/api/HsSessionObserver.java` | 已读到 |
| E3 | 适配 `HsDevicePortAdapter.Observer.onDeviceEvent`：`DualConnectionChanged`/`PairedDevicesChanged`/`RecordFilesChanged` → `DeviceSnapshot.dualConnection`/`recordFiles` | `provider/biz_ggec_hs/HsDevicePortAdapter.java` | 已读到（事件→快照有单测） |
| E4 | 展示读取 `DeviceSnapshot.dualConnection()` / `recordFiles()` | `domain/device/model/DeviceSnapshot.java` | 已读到 |

不新增端口方法：LIST 数据随快照走（design.md §6.1 明确要求）。

## 2. 新增文件

`app/src/main/java/com/ggec/earbuds/`

domain/device/model：

- `DeviceCapabilityGroup.java`、`DeviceCapabilityKind.java`、`CapabilityOption.java`、`ControlRange.java`
- `DeviceCapabilityView.java`、`DeviceVariantView.java`、`DeviceProfileView.java`
- `ControlValue.java`、`DeviceState.java`、`DeviceFailure.java`、`DeviceWriteResult.java`
- `DeviceAction.java`（`RecordMode` 提到顶层）、`PairedDeviceView.java`、`DualConnectionView.java`、`RecordFileView.java`

domain/device/port：

- `DeviceCapabilityCatalog.java`、`DevicePort.java`（重写：scan/connect/observe/write/perform/close）

domain/device/usecase：

- `WriteDeviceControl.java`、`RunDeviceAction.java`（`ObserveDevice`/`ScanDevices`/`ConnectDevice` 改写为新契约）

provider：

- `provider/biz_ggec_hs/HsValueMapper.java`
- `provider/biz_ggec_hs/HsCapabilityCatalogAdapter.java`
- `provider/biz_ggec_hs/HsDevicePortAdapter.java`
- `provider/biz_ggec_hs/HsDeviceProviders.java`（一次 `YoHsSdk` 实例的装配与生命周期持有者，供装配根取两个领域端口用；避免 wiring 出现 SDK 类型）
- `provider/lib_common/log/{YoLogProvider,AppLog}.java`（迁入 + 新增门面）
- `provider/lib_common/permission/{PermissionProvider,BluetoothScanPermission,BluetoothScanPermissionRequest}.java`
- `provider/lib_common/refresh_rate/WindowProvider.java`

wiring：

- `DeviceGraph.java` 重写（只用领域用例与 provider 适配器，无 SDK 类型）

## 3. 删除文件

main（20）：

- `data/device/{DeviceProfileCatalogImpl,EmptyDeviceControlsPort,IdleDevicePort}.java`（`data/device/` 目录消失）
- `domain/device/model/{Capability,CapabilitySet,DeviceModel,DeviceVariant,DeviceProfile,ProfileParams,DeviceControls,DeviceSwitch,EqPreset,GestureAction,GestureSlot,SoundQuality}.java`
- `domain/device/policy/DeviceControlPolicy.java`（目录消失）
- `domain/device/port/{DeviceProfileCatalog,DeviceControlsPort}.java`
- `domain/device/usecase/{LoadDeviceProfiles,EditDeviceControls}.java`
- `provider/lib_bluetooth/HsDevicePort.java`（目录消失）

test（9）：

- `data/device/{DeviceProfileCatalogImplTest,EmptyDeviceControlsPortTest,IdleDevicePortTest}.java`
- `domain/device/model/{DeviceControlsTest,DeviceVariantTest}.java`
- `domain/device/usecase/LoadDeviceProfilesTest.java`
- `domain/device/port/DevicePortContractTest.java`（其结构断言并入 `HsDevicePortAdapterTest`）
- `provider/lib_bluetooth/{HsDevicePortTest,HsDeviceInfoContractTest}.java`（在 `provider/biz_ggec_hs/` 下按新契约重写）
- `testing/device/FakeTestDeviceControlsPort.java`（实现已删除的端口）

## 4. 映射表（`HsValueMapper`，全部穷尽，无 `default`）

| 方向 | biz | app |
|------|-----|-----|
| 能力 id | `HsCapability.name()` | `DeviceCapabilityView.id`；解析 `HsCapability.valueOf(id)`，非法抛 `IllegalArgumentException` |
| 分组 | `HsCapabilityGroup`（6） | `DeviceCapabilityGroup`（6，同名同序） |
| 控件类型 | `HsControlKind`（7） | `DeviceCapabilityKind`（7，同名）：`TOGGLE/LEVEL/CHOICE/CURVE/ACTION/LIST/GATE` |
| 描述符 | `HsCapabilityDescriptor(cap,title,group,kind,options,Optional<range>)` | `DeviceCapabilityView(...,options,Optional<ControlRange>)` |
| 控制值 | `HsControlValue.{Toggle,Level,Choice,Curve}` | `ControlValue.{Toggle,Level,Choice,Curve}`（双向） |
| 失败 | `HsFailure`（16） | `DeviceFailure`（12）：见下 |
| 结果 | `HsResult<Void>` | `DeviceWriteResult.{Ok,Failed}` |
| 动作 | `HsAction`（14 种） | `DeviceAction`（14 种，`RecordMode` 顶层枚举） |
| 已配对设备 | `HsPairedDevice` + 两个内嵌枚举 | `PairedDeviceView` + 两个内嵌枚举 |
| 双连接 | `HsDualConnectionState` | `DualConnectionView` |
| 录音文件 | `HsRecordFile` | `RecordFileView` |
| 在仓状态 | `HsEarInCase`（5） | `WearState`：`BOTH → NOT_WORN`，`UNKNOWN/NONE/LEFT/RIGHT → UNKNOWN` |
| 扫描 | `HsNearbyDevice` | `ScanResult(modelId,variantId,displayName,bleAddress,rssi)` |
| 连接目标 | `HsTarget(modelId,variantId,bleAddress)`（**不含 displayName**） | `DevicePort.connect(modelId,variantId,bleAddress,displayName)`：前三个进连接目标，`displayName` 只进快照/界面 |
| 重置手势 | `HsAction.ResetControl()` | `DeviceAction.ResetControl()` |

`HsFailure → DeviceFailure` 逐项：

- `BLUETOOTH_DISABLED / PERMISSION_DENIED / LOCATION_DISABLED / BLE_UNSUPPORTED / SCAN_THROTTLED` → 同名
- `SCAN_START_FAILED / SCAN_FEATURE_UNSUPPORTED / SCAN_OUT_OF_RESOURCES / SCAN_INTERNAL_ERROR` → `SCAN_FAILED`（app 侧扫描失败只有一种呈现语义，逐分支显式合并）
- `RELEASED` → `NOT_READY`（SDK 已释放＝会话不可能就绪；可能发生在扫描或写入路径，不伪装成扫描失败）
- `NOT_READY / TIMEOUT / REJECTED / UNSUPPORTED_CAPABILITY / DEVICE_ERROR / INTERNAL_ERROR` → 同名

`WearState` 推导规则（`HsDevicePortAdapter.wearStateOf`）：只有「两耳都在仓」（`HsEarInCase.BOTH`）能证明「确知未佩戴」；`NONE/LEFT/RIGHT` 只说明离开了充电盒，协议无法证明戴在耳朵上，一律 `UNKNOWN`；`UNKNOWN`（未上报）与 `NONE`（明确都不在仓）语义分开，不合并。

## 5. 门禁（`app/build.gradle`，无白名单）

- `checkAppDependencyBoundary`（扫 `src/main/java`）
  - 非 `provider/**`：禁止 `import com.yovo.{lib_bluetooth,lib_ai,lib_common,biz_ggec_hs,biz_appupdate}.`
  - R6 平台基线：`com.yovo.lib_common.event.` 按**包名分类常量** `platformBaselinePackages` 放行（不是文件名/类名白名单），任何 ui 类可用，规则对称。
  - `provider/<dependency>/**`：只允许 `import com.yovo.<dependency>.*`；目录名与依赖名不等即失败（R4 目录即事实）。
- `checkAppLayering`（扫 `src/main/java`）
  - `ui/**` 禁止 `provider.**`；`data/**` 禁止 `provider.**` 与 `android.*`；`domain/**` 禁止 `provider.**`（到 data/ui/android 的禁止项由 `checkAppMvvmLayers` 单点负责，避免同一规则写两处）。
  - `provider.**` 只允许 `wiring/**` 引用（R2 只向内）。
  - `*ViewModel.java` 禁止 `com.yohu.youi.*` 与 `com.ggec.earbuds.wiring.*`。
  - 测试源集不在本任务范围：测试的职责是触达被测对象与依赖桩，套生产边界会逼出「为过门禁改测试结构」的假门禁（任务描述里已写明）。
- 保留并仍全绿：`checkAiLayerImports`、`checkMainThreadIoImports`、`checkComponentOwnership`、`checkExplicitImports`、`checkFrameworkToastForbidden`、`checkSingleYoLogInit`、`checkAppMvvmLayers`。
- 删除：`checkShellLayerImports`（含 `ShellGraph.java` 文件白名单）、`checkDeviceLayerImports`（含 `PermissionProvider` 白名单）。其未被新规则覆盖的项（`ui`→`data`、`domain`→`android/data/ui`）已由 `checkAppMvvmLayers` 覆盖。

## 6. 测试

新增/改写（`app/src/test/java/com/ggec/earbuds/…`）：

- `domain/device/model/DeviceStateTest`（唯一读取口、空值、不可变副本、Curve 拷贝）
- `domain/device/model/ControlRangeTest`（步长/边界校验）
- `domain/device/model/DeviceSnapshotTest`（12 字段构造契约、idle 语义、LIST 数据承载）
- `domain/device/model/ScanResultTest`（新字段名、地址大小写）
- `domain/device/scan/DiscoveredDevicesTest`（`displayName` 覆盖规则）
- `domain/device/usecase/WriteDeviceControlTest`、`RunDeviceActionTest`
- `provider/biz_ggec_hs/HsValueMapperTest`（分组/控件类型/失败/结果/动作/控制值穷尽）
- `provider/biz_ggec_hs/HsCapabilityCatalogAdapterTest`（目录投影、按机型过滤、LEVEL range、未知机型/能力失败）
- `provider/biz_ggec_hs/HsDevicePortAdapterTest`（连接态、电量/设备信息/控制值投影、佩戴推导、双连接与录音事件、断开清理、写入/动作映射、扫描四类回调、close 解绑、端口 `startScanBatch` 唯一签名）
- `provider/biz_ggec_hs/HsDeviceInfoContractTest`（跨模块未知标记同值锁）
- `provider/lib_ai/RealtimeLinkAdapterTest`（`YoAiErrorKind` 17 → `AiFailureKind` 穷尽映射；原先写在 `data/ai/voice/RealtimeVoiceSessionTest` 里，属 provider 职责，已归位）
- `testing/device/FakeTestDevicePort`（按新 `DevicePort` 契约改写，供 domain 与 ui 测试共用）

## 7. 验证（实跑）

```
.\gradlew.bat :app:compileDebugJavaWithJavac --offline --console=plain   # BUILD SUCCESSFUL
.\gradlew.bat :app:preBuild --offline --console=plain                    # BUILD SUCCESSFUL（9 条门禁全绿）
.\gradlew.bat :app:testDebugUnitTest --offline --console=plain           # BUILD SUCCESSFUL（68 suites / 540 tests / 0 failed / 0 skipped）
```

本层切片另跑过一次 `--tests 'com.ggec.earbuds.provider.*' --tests 'com.ggec.earbuds.domain.*'`：28 suites / 221 tests / 0 failed（新增的真实目录测试含在内）。

**未实机**：本轮验证只到编译、单测与门禁；未连接真实耳机，BLE 扫描/连接/写入链路没有运行观察证据（对应节点证据等级最高到「已读到」）。`:app:assembleDebug` 由 lead 统一复跑。

## 8. 已知边界（本任务范围内已收口，仅记录设计取舍）

1. `provider/lib_common/permission/BluetoothScanPermissionRequest` import `ui/device/scan/ScanPermissionRequest`：这是「宿主声明能力、适配器实现」的端口方向（Android 运行时权限只能由 Activity 发起），`ui` 从不 import `provider`，包依赖仍无环。
2. `HsDevicePortAdapter` 收到的 `HsDeviceEvent` 中有 6 类（服务状态、远程停录、查找耳机、新手模式、换耳、语音助手）在 app 契约里没有独立快照字段，其状态由能力控制值（`DeviceSnapshot.state`）承载；因此这些事件分支显式留空，不另立第二份状态。
3. 能力门控在 biz：会话按机型声明集合判定写入/动作；目录未登记机型照常建链，但 `write`/`perform` 一律 `UNSUPPORTED_CAPABILITY`。适配层不做任何机型判断，只把 biz 的 `HsFailure.UNSUPPORTED_CAPABILITY` 映射成 `DeviceFailure.UNSUPPORTED_CAPABILITY`（`HsDevicePortAdapterTest.unknownModelStillConnectsButEveryWriteIsUnsupported` 锁此语义）。
4. `PairedDevicesChanged` 只有在拿过完整 `DualConnectionChanged`（开关 + 优先设备 + 列表）之后才更新列表：单独一份列表填不满 `DualConnectionView` 的 `dualEnabled`/`priorityAddress`，用假值补齐就是替设备编造状态。
5. `GATE` 的呈现语义由 `DeviceCapabilityKind` 的 javadoc 定死，provider 只做同名映射、不折叠：只供 `DeviceProfileView.supports(id)` 门控、不生成通用行；`LEVEL` 是带 `range` 的可写控件，值未知仍渲染控件（同 `TOGGLE`）；`LIST` 的数据来自 `DeviceSnapshot` 的列表字段（双连接设备、录音文件）。biz 有构造期不变量（ACTION 必须可达、GATE 不得写入/进快照），因此 app 不把「拿不到值」再降级成不可操作。
6. 只读信息（电量、连接态、固件、服务状态）**不是能力值**：由首页摘要卡与 About 信息行直接从 `DeviceSnapshot`/事件渲染，不占能力 id。`DeviceCapabilityKind` 因此回到 7 名（无 `INFO`）。
7. 双连接由两个能力承担：`DUAL_CONNECTION`（`TOGGLE`，开关）与 `DUAL_CONNECTION_DEVICES`（`LIST`，已配对设备列表）。app 侧只作为普通能力 id 透传（`HsCapability.valueOf`），不往 `DeviceState` 掺 LIST 值——列表数据走 `DeviceSnapshot.dualConnection`。
8. GATE 类能力的 `write`/`perform` 会被 biz 判为 `UNSUPPORTED_CAPABILITY`；适配层原样把 `DeviceWriteResult.Failed(UNSUPPORTED_CAPABILITY)` 交回调用方，不静默吞（`HsDevicePortAdapterTest.readOnlyCapabilitiesSurfaceUnsupportedWriteInsteadOfSwallowingIt` 锁此语义）。
9. **反「假形状」**：域内其它测试用手写 fake 描述符，fake 的 `kind` 与真实目录不一致时（例如把双连接设备列表写成别的 kind）用例仍会全绿。因此新增 `HsCapabilityCatalogAdapterRealCatalogTest`：直接用 biz 的真实目录（`new HsCatalogImpl()`）驱动 app 的投影，期望值全部取自 biz 目录本身——机型能力数量/id 集合逐个比对（HS01/HS02 分开）、每个能力的 `kind/group/title/options/range` 与 biz 描述符逐字段一致、GATE 无 range/options、app 与 biz 的控件类型词汇集合必须相等。app 的 test 源集允许依赖 biz（门禁只扫 `src/main`），这条测试就是该许可的正当理由。
10. 保真度职责分账：本层测试锁「biz 目录 → app 契约」（逐字段相等）；UI 测试用自己的显式描述符锁「app 契约 → 行/控件」，不 import `provider.**`。两边各锁一段，投影链路的失败不会被算成渲染失败。

## 9. 裁定记录

| 事项 | 裁定 | 依据 |
|------|------|------|
| `provider/biz_ggec_hs/HsDeviceProviders` 保留（§6 清单外的第 4 个文件） | **保留**（lead 裁定） | `YoHsSdk.open(context)` 只创建一条 BLE 客户端，设备端口与能力目录必须来自同一实例；wiring 不得出现 SDK 类型。它持有 SDK 单实例所有权与 `open → 装配 → close`（先解绑端口再关 SDK）顺序，属 R5 允许的「适配与生命周期持有」，不是纯转发。 |
| 事件槽 `OneShotLiveData` | ui 直接使用，不建 provider 转发缝，删除已写的 `AppEventSlot` | §1.2 R6：`com.yovo.lib_common.event` 是共享 UI 基线，按依赖分类放行（门禁里是包名分类常量，不是文件白名单）。 |
| `RecordMode` 位置 | `DeviceAction.RecordMode` 顶层枚举 | `StartRecord` 与 `StopRecord` 共用同一模式枚举；与 biz `HsAction.StartRecord.RecordMode` 一一映射。 |
| 门禁扫描范围 | `src/main/java`；测试源集不套生产边界 | 测试源集的职责是触达被测对象与依赖桩，强行套生产边界会逼出「为过门禁改测试结构」的假门禁。 |
| `DeviceState` 读取口 | 唯一 `Optional<ControlValue> value(String)` | 不设哨兵值、不抛异常、不留 fallback 参数；UI 用 sealed switch 分支渲染「未上报」。 |
| `YoLogProvider` / `LibAiProvider` 的启动方法 | `bootstrap(Context)`，**不自建 `installed` 静态标志**，只被装配根调用一次 | R3 禁「静态安装器 + 可写静态 + 第二份幂等状态」。幂等已在库内：`YoLogBootstrap.initOnce` 双检、`YoAiRuntime.install`（含 `YoAiDiagnosticsInstaller`）自带幂等；provider 只提供 App 配置/启动，不再叠一层判重。`WindowProvider.installHighRefresh` 同型（库内幂等），同样保持无状态。 |
| `RecordMode` 位置（更新） | `DeviceAction.RecordMode` 与 `HsAction.RecordMode` 都在各自 sealed 接口顶层 | `StartRecord`/`StopRecord` 共用同一模式枚举，两侧同名同形一一映射。 |
| 重构前 `DeviceSwitch`/`SettingId`/旧 API 的用户可见能力 | 一个不丢：`LDAC`、`VOLUME_ADAPTIVE`、`RESET_CONTROL`、`REBOOT` 由 biz 能力集提供；`ResetControl` 走 `DeviceAction.ResetControl() → HsAction.ResetControl()` | design.md §5 能力集注记（丢一个即功能回退）。app 侧不为这四项写分支：能力 id 经 `HsCapability.valueOf` 透传，`LDAC`/`VOLUME_ADAPTIVE` 用 `ControlValue.Toggle`，`RESET_CONTROL`/`REBOOT` 用动作通道。 |

