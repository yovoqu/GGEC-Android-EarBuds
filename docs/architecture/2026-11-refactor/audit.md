# 架构审查报告（设计前 · 2026-11）

> 方法：YoAgentDocs `task.architecture-review`（六段式）+ `playbook/architecture-review`。
> 结论等级：**CLEAN** / **HAS_ISSUES**。本报告为**只审不改**阶段的记录，改后需另出复审。
> 证据等级标注沿用 rule：`已读到`（仅代码位置，证明边存在，不证明路径被走到）/ `已验证`（代码位置 + 运行观察）。本仓无真机、无运行观察，因此全部节点最高为 `已读到`。

## 范围

| 范围 | 所有权 | 覆盖文件 |
|------|--------|----------|
| R1 lib_bluetooth | 蓝牙栈 | `provider/lib_common/*`、`internal/common/permission/*`、`internal/BluetoothSdkCore.java`、`build.gradle` |
| R2 biz_ggec_hs | HS 协议 SDK | 根包 `YoHsSdk`/`YoHsBroadcast`、`api/*`、`model/*`、`assembly/*`、`internal/session/*`、`provider/lib_bluetooth/*`、`build.gradle` |
| R3 app 设备域 | 应用壳 | `provider/lib_bluetooth/*`、`provider/lib_common/*`、`data/device/*`、`domain/device/*`、`ui/device/*`、`wiring/DeviceGraph.java`、`build.gradle` |

---

## R1 lib_bluetooth

### 1. 设计前链路

1. 客户端创建 → 权限安装：`YoBluetoothSDK.ble()` → `BluetoothSdkCore.ble()`（`internal/BluetoothSdkCore.java:34`）→ `BluetoothProviderInstaller.installAll()` → `YoBluetoothPermissionChecker.setPort(PermissionProvider.INSTANCE)`（`provider/lib_common/BluetoothProviderInstaller.java:25`）。`已读到`
2. 权限查询：`YoCbtAndroidModuleGraph`（`:193`）、`YoBleAndroidCapabilityInspector`（`:31`）各自 `new YoBluetoothPermissionChecker()`，读取全局静态 `port`（`internal/common/permission/YoBluetoothPermissionChecker.java:15`）。`已读到`

### 2. 清单逐项

| 项 | 结果 | 证据 |
|----|------|------|
| 模块职责单一 | 通过 | 蓝牙协议/链路库，无业务能力判断 |
| 文件单一职责 | 通过 | `provider/lib_common` 仅 2 类，各 1 责 |
| 公开门面不含实现 | **失败** | `BluetoothProviderInstaller` 是**导出给外部调用的安装入口**，并写全局静态 |
| 无兼容层 | 通过 | 未见适配器/双轨 |
| 无防御性修补 | **失败** | 端口未安装时抛 `IllegalStateException`（`BluetoothPermissionPort.UNINSTALLED`），把装配缺失变成运行期异常；`setPort` 是服务定位器 |
| 无散落硬编码 | 通过 | — |
| 无越级 | 通过 | `api`/`model` 未 import `internal`（`checkLibBluetoothApiSurface` 仅是白名单式校验） |
| 依赖单向 | **失败** | 权限端口由库导出、由外部安装：装配方向反了（外部 → 库内部状态） |
| 模型跟随层 | 通过 | — |
| 无技术债残留 | **失败** | 门禁白名单：`lib_bluetooth/build.gradle` 的 `checkLibBluetoothProviderBoundary` 排除 `provider/lib_common/**` 与 `YoBtLogger.java`；`checkLibBluetoothLogSeam`/`checkNoStrayLogging` 同样点名豁免 |

### 3. 问题清单（级别只表示本阶段动手顺序，列出即须闭合）

- **P0** `provider/lib_common/BluetoothProviderInstaller.java:17-26` — 公共安装器 + 全局静态端口写入。正确层：provider 内构造注入，装配点在 `internal/BluetoothSdkCore`。
- **P0** `internal/common/permission/YoBluetoothPermissionChecker.java:15-19` — `static volatile port` + `setPort`，进程级可变全局状态。正确层：实例字段 + 构造参数。
- **P1** `internal/ble/device/YoBleAndroidCapabilityInspector.java:31`、`internal/classic/internal/android/YoCbtAndroidModuleGraph.java:193` — 内部自行 `new` checker，绕过装配根。正确层：构造参数注入。
- **P1** `internal/common/diagnostics/YoBtLogger.java` — 位于 `internal/` 却承担 lib_common 适配职责；provider 目录规则要求按依赖模块归位（`provider/lib_common/log/`）。
- **P1** `lib_bluetooth/build.gradle` 4 个检查任务的类名/路径白名单。正确层：结构性规则（按包名与目录判断）。

### 4. 已通过项

- `api/`（`YoBleClient`/`YoCbtClient`）与 `model/**` 均为纯契约，无实现、无 `lib_common`/`internal` import。
- 日志单点缝设计成立（`YoBtLogger` 之外的代码不直接调 `YoLog`），只是落位目录错了。

### 5. 结论

**HAS_ISSUES**

---

## R2 biz_ggec_hs

### 1. 设计前链路

1. 打开运行时：`YoHsSdk.open(context)`（`YoHsSdk.java:20`）→ `HsAssembly.open`（`assembly/HsAssembly.java:17`）→ `YoBleCommandLink.open` + `YoHsClientImpl`。
2. 扫描：`HsNearbyScanner.start`（`provider/lib_bluetooth/HsNearbyScanner.java:38`）→ `YoBleClient.subscribeScan` → `HsNearbyScanCallbacks` → `HsNearbyDevices.map`（`:33`）→ `YoHsBroadcast.classify`（`YoHsBroadcast.java:31`）→ `HsBroadcastAssembly` → `internal/broadcast`。
3. 控制：`YoHsClient` 15 个 setter（`api/YoHsClient.java`）→ `internal/session/YoHsClientImpl` → `internal/protocol/CommandScheduler`(1011 行) → `HsCommandLink`。
4. 设备事件：`internal/protocol/inbound/DeviceEventRouter`(328 行) → `DeviceDomainStore`(463 行) → `HsDeviceObserver`（10 个 default 回调）。全部 `已读到`。

### 2. 清单逐项

| 项 | 结果 | 证据 |
|----|------|------|
| 模块职责单一 | 通过（协议 SDK） | — |
| 文件单一职责 | **失败** | `model/PairedDevice.java` 同时是协议常量表 + 数据模型 + `toString` 协议诊断；`DualConnectionPageData` 是可变的"页面数据"塞进 SDK |
| 公开门面不含实现 | **失败** | 三层转发：根 `YoHsSdk` → `assembly/HsAssembly` → `internal/graph/HsGraph`；根 `YoHsBroadcast` → `HsBroadcastAssembly` → `HsBroadcastRuntime`，均为纯转发 |
| 无兼容层 | 通过 | 未见版本 if |
| 无防御性修补 | **失败** | `HsResultCallback` + `HsCommandResult(int code)` 用整数码 + `Object data` 承载一切结果（`model/HsCommandResult.java:15-27`），调用方必须做类型猜测；`HsDeviceObserver.onPairedDevices(List<Map<String,String>>)` 用未类型化 Map 表达结构 |
| 无散落硬编码 | **失败** | 协议命令字面量散布在 `internal/protocol/**`（属实现层，可接受）；但 `model/PairedDevice` 把 TLV 常量公开为 API |
| 无越级 | **失败** | `api/` 与根门面 import `assembly`；`api/YoHsBroadcastSession` 暴露 `model.broadcast.HsScanSnapshot`（广播层内部输入，含 `byte[] rawAdvertisingView()`）给外部 |
| 依赖单向 | 通过 | provider → internal 单向 |
| 模型跟随层 | **失败** | 帧级模型（`HsScanSnapshot`、`HsBroadcastReport`）与业务模型（`HsNearbyDevice`）同处 `model/` 且都对外 |
| 能力描述缺失 | **失败** | 没有任何"设备类型/支持能力"的对外描述；该知识现在硬编码在 app（见 R3） |
| 无技术债残留 | **失败** | `api/` 6 文件 + 根包 2 文件 + `assembly/` 2 文件，公共面共 14 个类型入口，其中 4 个是转发或内部模型 |

### 3. 问题清单

- **P0** `api/YoHsClient.java` — 15 个平铺 setter，无能力归属、无能力是否支持的判定，调用方（app）只能全调一遍；缺设备目录/能力描述。正确层：`api/HsSession` + `api/HsDeviceCatalog` + `internal/device/HsCapabilityRouter`。
- **P0** `assembly/HsAssembly.java`、`assembly/HsBroadcastAssembly.java`、根 `YoHsSdk`/`YoHsBroadcast` — 同一入口三层转发。正确层：单一门面直接依赖内部装配。
- **P1** `model/PairedDevice.java:15-40` — 协议常量公开为 API 常量。正确层：`api/HsPairedDevice` 枚举 + `internal/protocol/dualconnectioncmd` 解析。
- **P1** `model/HsCommandResult.java`、`model/HsResultCallback.java` — 整数码 + `Object` 载荷。正确层：`api/HsResult<T>` 密封类型 + `api/HsFailure` 分类。
- **P1** `model/DualConnectionPageData.java` — 可变 + "Page" 命名。正确层：`api/HsDualConnectionState` 不可变记录。
- **P1** `api/YoHsBroadcastSession` + `model/broadcast/*` 对外暴露帧模型。正确层：下沉 `internal/broadcast`。
- **P2** `model/HsDeviceObserver` 未类型化 `Map<String,String>`。正确层：`api/HsDeviceEvent` 密封事件。
- **P2** `biz_ggec_hs/build.gradle` 的 `checkBizGgecHsBoundary` 用 `rootFacade` 正则 + 路径字符串判断，规则不可读且随文件重命名漂移。

### 4. 已通过项

- 协议层内聚度高：命令、调度、入站事件分目录清晰，未发现 UI/Android 依赖泄漏到 `internal/protocol`。
- `provider/lib_bluetooth` 确实是模块内唯一 import `lib_bluetooth` 的位置（门禁可验证）。

### 5. 结论

**HAS_ISSUES**

---

## R3 app 设备域

### 1. 设计前链路

1. 扫描：`ScanScreen` → `ScanViewModel`(266 行) → `ScanDevices` → `DevicePort` 实现 `HsDevicePort`（`provider/lib_bluetooth/HsDevicePort.java:95`）→ `YoHsSdk.open`。
2. 机型与能力：`DeviceHomeViewModel` → `LoadDeviceProfiles` → `DeviceProfileCatalog` 实现 `DeviceProfileCatalogImpl`（硬编码 `HS01/HS02`、`v1..v3`，`data/device/DeviceProfileCatalogImpl.java:51-71`）。
3. 配置写入：`DeviceSettingsViewModel` → `EditDeviceControls` → `DeviceControlsPort` 实现 `EmptyDeviceControlsPort`（`wiring/DeviceGraph.java:50`）→ `update()` 空实现（`data/device/EmptyDeviceControlsPort.java:35-37`）。**写入链路未接通。**
4. 权限：`BluetoothScanPermission`（`provider/lib_common`）→ `YoPermission.isGranted`；与库内 `PermissionProvider` 各自实现一套蓝牙权限语义。全部 `已读到`。

### 2. 清单逐项

| 项 | 结果 | 证据 |
|----|------|------|
| 中上层不含项目信息 | **失败** | 机型表、变种数量、能力数组均在 app `data/device`；`DeviceModel.of("HS","01")` 在 app 里做型号拼接（`domain/device/model/DeviceModel.java:14`） |
| 模块职责单一 | **失败** | `provider/lib_bluetooth` 目录名与真实依赖不符（只 import biz_ggec_hs） |
| 文件单一职责 | **失败** | `DeviceControls`(159 行) 把 30 项能力的可写状态塞进一个类；`ScanScreen`(373 行)、`CustomEqSheet`(284 行)、`MusicSectionView`(269 行) 把渲染+交互+数据装配混在一起 |
| 公开门面不含实现 | 不适用（app 无 api 层） | — |
| 无兼容层 | **失败** | `EmptyDeviceControlsPort` / `IdleDevicePort` 是"未接入时先顶住"的占位实现（文件注释自述"未接入真实协议控制通道"），属防御性占位 |
| 无防御性修补 | **失败** | 同上：占位端口的 `update()` 静默丢弃写入；`DeviceControlPolicy` 按能力推断，掩盖"能力无写入通道"的事实 |
| 无散落硬编码 | **失败** | `DeviceProfileCatalogImpl` 的机型/变种/能力数组 |
| 无越级 | **失败** | `ui/device` 直连 `provider.lib_common.PermissionProvider`（靠 `app/build.gradle` 白名单放行）；`HsDevicePort` 在 provider 内直接 `import lib_common.log.YoLog` |
| 依赖单向 | 通过 | ui → domain → data/port |
| 模型跟随层 | **失败** | `DeviceControls`/`DeviceSwitch` 是"UI 控件模型"当领域模型用 |
| 能力驱动缺失 | **失败** | UI 用 `profile.supports(Capability.X)` 逐项硬判断（约 30 处），新增机型必须改 app |
| 无技术债残留 | **失败** | 门禁白名单：`checkDeviceLayerImports` 放行 `PermissionProvider`；`checkAppSdkProviderBoundary` 放行 `lib_common.event`；`checkAppMvvmLayers` 与前者规则重叠 |

### 3. 问题清单

- **P0** `data/device/DeviceProfileCatalogImpl.java:51-71` — 项目机型/变种/能力表在 app。正确层：`biz_ggec_hs` 设备目录。
- **P0** `domain/device/model/{Capability,CapabilitySet,DeviceModel,DeviceVariant,DeviceProfile,ProfileParams}` — 与 biz 重复的能力/机型定义，且含 `family+type` 拼接。正确层：删除，由 biz 目录经 provider 投影为 app 呈现契约。
- **P0** `provider/lib_bluetooth/**` — 目录名与被依赖模块不符（实际只依赖 `biz_ggec_hs`）。正确层：`provider/biz_ggec_hs/`。
- **P0** `data/device/EmptyDeviceControlsPort.java` + `wiring/DeviceGraph.java:50` — 配置写入整条链路是空实现，且用占位端口掩盖。正确层：`DevicePort.write/perform` 直连 biz 会话；`DeviceControls` 废弃。
- **P1** `ui/device/**` 约 30 处 `profile.supports(Capability.X)` — UI 承担机型能力判断。正确层：能力描述（group/kind/options）驱动。
- **P1** `provider/lib_bluetooth/HsDevicePort.java:29` 直接 import `com.yovo.lib_common.log.YoLog` — 跨依赖混用。正确层：`provider/lib_common/log/AppLog`。
- **P1** 5 处 `import com.yovo.lib_common.event.OneShotLiveData`（`ui/ai` ×4、`ui/device/scan/ScanViewModel.java:17`）— 非 provider 直连 lib_common。正确层：`provider/lib_common/event/AppEventSlot`（继承语义，单一实现）。
- **P1** `app/build.gradle` 的白名单与规则重叠（`PermissionProvider`、`lib_common.event`）。正确层：结构性 provider 规则。
- **P2** `EmptyDeviceControlsPort.update()` 静默丢弃、`IdleDevicePort` 返回空快照，测试把占位行为当契约断言（`IdleDevicePortTest`、`EmptyDeviceControlsPortTest`）。正确层：删除占位与其测试，改测真实投影与错误映射。

### 4. 已通过项

- `ui/device` 不 import `data`（除白名单项），MVVM 骨架成立；`DeviceSnapshot` 只承载状态，无机型判断。
- `HsDevicePort` 的失败映射是穷尽 `switch`（无 default 兜底），值得保留为 provider 适配范式。

### 5. 结论

**HAS_ISSUES**

---

## 汇总

| 范围 | 结论 | 本阶段必须闭合的 P0 |
|------|------|---------------------|
| R1 lib_bluetooth | HAS_ISSUES | 安装器 + 全局静态端口 |
| R2 biz_ggec_hs | HAS_ISSUES | 胖 YoHsClient / 三层转发 / 无能力目录 |
| R3 app 设备域 | HAS_ISSUES | 机型能力硬编码 / provider 目录错位 / 配置写入空实现 |

修复方案见同目录 `design.md`；改后须由独立复审出六段报告（`review.md`），三范围同时 CLEAN 才算收口。
