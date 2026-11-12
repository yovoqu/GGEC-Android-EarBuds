# GGEC-Android-EarBuds 架构重画（2026-11）

> **后续演进（以新文档为准）**：
> - 扫描链路的完整三层设计（lib 机制 / biz 规则 / app 显示）见 `scan-architecture.md`，本文 §6 中"筛选在 app mapper 求值"的写法已被它取代。
> - 产品分类学（设备类型 / 项目 / 产品型号 / 变体）与三层职责见 `product-taxonomy.md`；本文 §5 的 `HsDeviceModel` 已改名 `HsProductModel` 并补 `projectId`/`deviceType`。


> 依据：YoAgentDocs `task.architecture-review` + `task.architecture-design`（角色 architect、playbook architecture-review/design、rule common/architecture、rule common/stack-layering、checklist architecture-review）。
> 本文件是本轮重构的**唯一权威契约**。实现与评审都以本文件为准；与本文件冲突的实现视为未完成。

## 0. 已确认决议（用户口头确认，不得再改）

| 编号 | 决议 |
|------|------|
| D1 | `provider/<被依赖模块>` 放在**消费方模块**内：该目录是模块内**唯一**允许 `import` 该依赖的位置，只服务本模块内部，**禁止导出给其他模块调用**（不得有 installer / 全局可写单例 / 跨模块 provider 定义）。 |
| D2 | `lib_bluetooth` 的权限判定由**库内部自装配**：保留库内 `provider/lib_common`，删除 `BluetoothProviderInstaller` 与全局静态 `setPort`，改**构造注入**。 |
| D3 | 设备类型与能力由 **biz_ggec_hs** 描述并对外提供（HS01 有哪些功能、HS02 有哪些功能）；biz 实现内聚、高内聚低耦合。 |
| D4 | APP 侧删除机型/变种/能力的"无端定义"（`DeviceModel` 的 family+type 拼接、变种表、per-model 能力数组等），改由 biz 设备层提供。 |
| D5 | biz SDK 重新整理：支持扫描、连接、控制、设备目录；内部内容内聚，**对外严格收敛 API**。biz 分三层：底层对接自有蓝牙栈、中层协议解析、上层把解析结果整理成 API。 |
| D6 | APP 侧能力表达**彻底通用化**：能力 id 驱动，biz 给完整能力描述与控制通道，app 只渲染 + 透传。 |
| D7 | 本轮包含 **app 设备 UI 全量重写**（home / scan / settings 的 page、section、row）。 |
| D8 | gradle 边界门禁重写为**结构性检查**，删除所有白名单例外。 |
| D9 | 旧代码、兼容层、双轨、TODO 一律不得保留；一次重构到底。 |

## 1. Provider 约定（全仓统一，替代现状各自为政）

### 1.1 定义

`provider/<dependency>/` 出现在**消费模块**中，含义是"`<dependency>` 把能力送进本模块"。

```
<module>/src/main/java/<pkg>/provider/
    <dependency>/                 # 被依赖模块只有一个完整大模块时，适配类直接放这里
        XxxAdapter.java
    <dependency>/<submodule>/     # 被依赖模块有多个子模块、且该子模块 API 文件较多时，一子模块一子目录
        YyyAdapter.java
```

### 1.2 硬规则

- **R1 单点接入**：模块内只有 `provider/**` 可以 `import <dependency>`。其他任何包（含 `api/`、`domain/`、`ui/`、`internal/` 的非 provider 部分）都不得出现该 import。
- **R2 只向内**：`provider/**` 只被本模块装配根（wiring / assembly / graph）引用；不得被本模块 `api/` 引用，不得被其他模块引用（跨模块由消费方门禁检查）。
- **R3 不导出安装**：provider 内不得出现**供其他模块调用**的静态安装器、`install()`/`installAll()` 公共入口、可被外部写入的静态字段、服务定位器注册表。依赖在**装配点构造并注入**。
- **R3b 启动步骤（允许形态）**：provider 可以提供"只被本模块装配根调用一次"的启动步骤（如日志初始化、刷新率安装、诊断安装）。允许条件：① 调用点唯一（wiring/assembly），不导出给其他模块；② **不得自建第二份幂等状态**（幂等由被依赖方已有的 `initOnce`/双检锁保证，provider 里再放一个 `installed` 布尔即违规）；③ 不解析依赖、不作为服务定位器。违反任一条即按 R3 处理。
- **R3a 硬件资源仲裁单例（唯一允许的静态可变状态形态）**：实现层可以持有"进程内独占硬件资源"的单例（如 `lib_bluetooth/internal/ble/scan/YoBleScanHub.current`，进程内只跑一个 BLE 扫描）。允许条件三条同时满足：① 只被本模块内部代码写入，外部模块无法访问；② 类文档写明锁归属、代次/终态归属与生命周期；③ 不承担依赖装配职责（不能靠它注入 provider 实现）。违反任一条即按 R3 处理。
- **R4 目录即事实**：目录名必须等于真实依赖模块名。没有 `import lib_bluetooth` 就不允许存在 `provider/lib_bluetooth`；反过来，`import biz_ggec_hs` 的适配类必须放在 `provider/biz_ggec_hs`。
- **R5 不做转发层**：provider 内的类只做"依赖 API ↔ 本模块端口/模型"的适配与生命周期持有，不承载本模块业务策略，也不再包一层纯转发（禁止 `FooAssembly` 只转发到 `FooRuntime`）。

- **R6 平台基线例外类别**（不是文件白名单）：`com.yovo.lib_common.event` 是**共享 UI 基线**（`OneShotLiveData` 同时被 app 与 biz_appupdate 两个 UI 模块使用，等价于 androidx 这类框架基线），按"依赖分类"放行，任何 `ui/**` 类都可使用，规则对称、不针对具体文件。业务能力域（`lib_common.log`/`permission`/`refresh_rate`/`download`/`archive`、`lib_bluetooth`、`lib_ai`、`biz_ggec_hs` 等）仍严格走 provider。禁止为基线类型再造 provider 转发缝或第二份实现。

### 1.3 全仓 provider 落位（目标态）

| 模块 | provider 目录 | 被依赖 | 内容 |
|------|---------------|--------|------|
| lib_bluetooth | `provider/lib_common/log/` | lib_common.log | `YoBtLogger`（唯一日志缝） |
| lib_bluetooth | `provider/lib_common/permission/` | lib_common.system.permission | `BluetoothPermissionProvider`（实现库内权限端口） |
| biz_ggec_hs | `provider/lib_bluetooth/` | lib_bluetooth | BLE 链路、扫描订阅、命令收发适配 |
| app | `provider/biz_ggec_hs/` | biz_ggec_hs | 设备能力目录、会话、扫描 → app 端口/呈现模型 |
| app | `provider/lib_common/log/` | lib_common.log | `YoLogProvider`（单点初始化）+ 日志门面 |
| app | `provider/lib_common/permission/` | lib_common 权限域 | 权限查询/请求适配 |
| app | `provider/lib_common/refresh_rate/` | lib_common 刷新率域 | `WindowProvider` |
| app | `provider/lib_ai/` | lib_ai | AI 网关适配（现状保留，仅按新规则归目录） |

`app/provider/lib_bluetooth/` **必须消失**：它的内容实际只依赖 biz_ggec_hs，目录名与事实不符（R4）。

## 2. 目标架构总图

```
app (应用壳：View / ViewModel / domain / wiring)
  └─ provider/biz_ggec_hs ──► biz_ggec_hs (设备能力供给方 / SDK)
                                 ├─ api/            上层：对外契约（纯类型 + 接口）
                                 ├─ internal/device/ 上层：会话状态机、设备状态、能力路由
                                 ├─ internal/protocol/ 中层：帧编解码、命令、入站事件
                                 ├─ internal/broadcast/ 中层：广播识别
                                 └─ provider/lib_bluetooth ──► lib_bluetooth (蓝牙栈)
                                                                 └─ provider/lib_common ──► lib_common
```

依赖方向单向：`app → biz → lib_bluetooth → lib_common`。反向引用、跨层直连、同层互相点名内部包全部禁止。

### 2.1 biz_ggec_hs 三层（D5）

| 层 | 目录 | 职责 | 不做什么 |
|----|------|------|----------|
| 底层（栈对接） | `provider/lib_bluetooth/` | 打开 BLE 客户端、订阅原始扫描、建立/维护链路、下发字节、链路错误归一 | 不解析协议语义、不持有业务状态机、不决定能力 |
| 中层（协议） | `internal/protocol/`、`internal/broadcast/`、`internal/contract/` | 帧结构、命令表、调度/事务/超时、入站事件解析、广播 TLV 识别 | 不碰 Android BLE API、不 import provider、不对外暴露 |
| 上层（呈现给 API） | `internal/device/` + `api/` | 会话状态机、设备状态快照、能力目录、能力↔命令路由、错误/结果归一 | 不写帧布局、不做字节拼接、不 import lib_bluetooth |

## 3. lib_bluetooth 变更清单

1. 删除 `provider/lib_common/BluetoothProviderInstaller.java`（含 `installAll()`、`installed` 静态标志）。
2. `internal/common/permission/YoBluetoothPermissionChecker`：删除 `static volatile BluetoothPermissionPort port` 与 `setPort`；改为实例持有端口，`new YoBluetoothPermissionChecker(port)`。
3. 所有内部使用点（`YoBleAndroidCapabilityInspector`、`YoCbtAndroidModuleGraph`、`YoCbtDiscoveryController`、`YoCbtBondedDeviceProvider`、`YoCbtBondOperationController`、`YoCbtAndroidDiscoveryPlatform`、`YoCbtAndroidBondedDeviceLoader` 等）改为**构造参数注入** checker，不再自行 `new`。
4. 装配在 `BluetoothSdkCore`：创建 `provider/lib_common/permission/BluetoothPermissionProvider` → 构造 checker → 沿客户端实现向下传。**不允许任何静态可写状态**。
5. `internal/common/diagnostics/YoBtLogger` 迁到 `provider/lib_common/log/YoBtLogger`（日志 API 文件多，按 R 规则建子目录）。
6. `provider/lib_common/PermissionProvider` 改名 `BluetoothPermissionProvider` 并移入 `permission/` 子目录。
7. `api/`、`model/` 不得 import `provider/**`；`provider/**` 不得被其他模块 import。
8. 更新 `lib_bluetooth/build.gradle` 门禁为结构性检查（见 §9），删掉 `YoBtLogger`、`provider/lib_common` 这类白名单字符串。

## 4. biz_ggec_hs 目标包结构（旧 → 新）

```
com.yovo.biz_ggec_hs
  YoHsSdk.java                          # 唯一入口（实例 + static open）
  api/                                  # 契约层，零实现
    HsNearby.java
    HsNearbyObserver.java
    HsNearbyDevice.java
    HsSession.java
    HsSessionObserver.java
    HsConnectionState.java
    HsSnapshot.java
    HsBattery.java
    HsDeviceInfo.java
    HsDeviceCatalog.java
    HsDeviceModel.java
    HsDeviceVariant.java
    HsCapability.java
    HsCapabilityDescriptor.java
    HsCapabilityGroup.java
    HsControlKind.java
    HsControlValue.java
    HsControlOption.java
    HsAction.java
    HsDeviceEvent.java
    HsFailure.java
    HsResult.java
    HsWriteCallback.java
  internal/
    device/                             # 上层
      HsSessionImpl.java                # 原 internal/session/YoHsClientImpl
      HsSessionState.java
      HsCapabilityRouter.java           # 能力 → 命令/状态 的唯一映射
      HsCatalogImpl.java                # 设备目录（机型 → 能力）
      HsSnapshotAssembler.java
      HsConnectReadyTimeout.java
    protocol/ ...                       # 中层，原位保留（命令/调度/入站）
    broadcast/ ...                      # 中层，原位保留
    contract/                           # 链路契约（HsCommandLink）
    graph/                              # 装配（HsGraph -> 组装 device + protocol + provider）
    log/
  provider/lib_bluetooth/               # 底层
    YoBleCommandLink.java
    HsNearbyScanner.java                 # 纯适配：订阅扫描 + BleScanResult → HsRawAdvert + 错误码归一
    HsRawAdverts.java
    HsScanFailures.java
  internal/contract/                     # 传输/协议边界端口与帧类型
    HsCommandLink.java / HsConnectionEvents.java / HsBleScanLink.java / HsRawAdvert.java
  internal/device/                       # 上层
    HsNearbyImpl.java                    # 扫描编排（会话/账本/识别映射，可依赖 internal.broadcast）
    HsNearbyScanSession.java / HsNearbyScanCallbacks.java / HsNearbyScanLedger.java / HsNearbyDevices.java
```

**删除**：`api/HsRuntime.java`（合并进 `YoHsSdk`）、`api/YoHsClient.java`（被 `HsSession` 取代）、`api/YoHsNearby.java`（被 `HsNearby` 取代）、`api/YoHsBroadcastSession.java`、根包 `YoHsBroadcast.java`、`assembly/HsAssembly.java`、`assembly/HsBroadcastAssembly.java`。广播识别只做中层内部能力，不再对外暴露。
**下沉**：`model/broadcast/HsScanSnapshot`、`HsBroadcastReport`、`Hs01Identity`、`Hs02Identity`、`HsGeneration`、`HsMarket`、`HsProductCatalog` 全部进 `internal/broadcast/`（对外只经 `HsNearbyDevice`/目录）。
**模型迁移**：`model/HsBattery`、`HsDeviceInfo`、`HsNearbyDevice`、`HsScanFailure` → `api/`（改名 `HsFailure`）；`model/PairedDevice` → `api/HsPairedDevice`（TLV 常量转枚举，解析留在 `internal/protocol/dualconnectioncmd/PairedDeviceTlvParser`）；`model/DualConnectionPageData` → `api/HsDualConnectionState`（不可变、去掉 `PageData` 命名与 setter）；`model/RecordFileInfo` → `api/HsRecordFile`；`model/HsCommandResult`/`HsResultCallback` → `api/HsResult`/`HsWriteCallback`；`model/PairedDeviceMapper` → `internal/device/`。`model/` 目录最终消失。

## 5. biz 对外 API 契约（权威，逐文件）

> 全是契约：只允许 `record` / `enum` / `interface` / `sealed interface` / 静态工厂，不允许实现逻辑、不允许 import `internal`/`provider`/`lib_bluetooth`/`android.bluetooth`。

```java
package com.yovo.biz_ggec_hs;

public final class YoHsSdk {
    public static YoHsSdk open(Context context);
    public HsNearby nearby();
    public HsSession session();
    public HsDeviceCatalog catalog();
    public void close();
}
```

```java
package com.yovo.biz_ggec_hs.api;

public interface HsNearby {
    void start(HsNearbyObserver observer);
    void stop();
    boolean isScanning();
}

public interface HsNearbyObserver {
    void onUpdated(List<HsNearbyDevice> devices);
    default void onLost(HsNearbyDevice device) { }
    void onFailed(HsFailure failure);
    void onFinished();
}

public record HsNearbyDevice(String modelId, String variantId, String displayName,
                             String bleAddress, int rssi) { }

/** 连接目标：机型/变种来自目录与扫描结果，会话据此判定该机型支持的能力集合。 */
public record HsTarget(String modelId, String variantId, String bleAddress) { }

public interface HsSession {
    void connect(HsTarget target);
    void disconnect();
    boolean isReady();
    HsConnectionState connectionState();
    HsSnapshot snapshot();
    void observe(HsSessionObserver observer);
    void removeObserver(HsSessionObserver observer);
    HsResult<HsControlValue> read(HsCapability capability);
    void write(HsCapability capability, HsControlValue value, HsWriteCallback callback);
    void perform(HsAction action, HsWriteCallback callback);
    void close();
}

public interface HsSessionObserver {
    default void onConnectionChanged(HsConnectionState state, HsFailure failure) { }
    default void onSnapshotChanged(HsSnapshot snapshot) { }
    default void onDeviceEvent(HsDeviceEvent event) { }
}

public enum HsConnectionState { IDLE, CONNECTING, READY, DISCONNECTED }

public record HsSnapshot(HsConnectionState connection,
                         HsBattery battery,
                         HsDeviceInfo deviceInfo,
                         Map<HsCapability, HsControlValue> values) { }

public record HsBattery(int leftLevel, int rightLevel, int caseLevel,
                        boolean caseCharging, int earInCaseStatus) {
    public static final int UNKNOWN_LEVEL = -1;
}

public record HsDeviceInfo(String firmware, int earColor, int earInCaseStatus) { }

public interface HsDeviceCatalog {
    List<HsDeviceModel> models();
    Optional<HsDeviceModel> model(String modelId);
    HsCapabilityDescriptor descriptor(HsCapability capability);
    List<HsCapabilityDescriptor> descriptors();
}

public record HsDeviceModel(String modelId, String displayName,
                            List<HsDeviceVariant> variants,
                            Set<HsCapability> capabilities) { }

public record HsDeviceVariant(String variantId, String displayName) { }

public enum HsCapability {
    CONNECTION, DUAL_CONNECTION, BATTERY, SOUND_QUALITY, EQ_MODE, CUSTOM_EQ, LDAC, VOLUME_ADAPTIVE,
    TOUCH_LONG, TOUCH_SHORT, TOUCH_DOUBLE, TOUCH_TAP, RESET_CONTROL, WEAR_DETECT, VOICE_WAKEUP,
    GAME_MODE, FALL_ALERT, FIND_EAR, VOLUME_REMIND, DEVICE_INFO, RENAME,
    CLEAR_PAIRING, REBOOT, RECORD_LOCAL, RECORD_STREAM, RECORD_LIST, RECORD_TRANSFER,
    AI_ASSISTANT, SERVICE_STATUS, BEGINNER_MODE, INTERPRET, OTA, OFFLINE_LOG,
    CLASSIC_BT
}

> 能力集必须覆盖重构前 app 的全部用户可见能力。原 `DeviceSwitch`（LDAC / VOLUME_ADAPTIVE / WEAR_DETECT / VOICE_WAKEUP / GAME_MODE / FALL_ALERT / VOLUME_REMIND / BEGINNER_MODE / INTERPRET / DUAL_CONNECTION）、原 `SettingId.RESET_CONTROL`（重置手势）与原 `YoHsClient.reboot()` 都有 UI 或旧 API 消费者，**一个都不许丢**；丢一个即为功能回退，按「严禁简化实现」处理。

public enum HsCapabilityGroup { MUSIC, CONTROL, FUNCTION, DUAL_CONNECTION, RECORD, ABOUT }

public enum HsControlKind { TOGGLE, LEVEL, CHOICE, CURVE, ACTION, LIST, GATE }

public record HsCapabilityDescriptor(HsCapability capability, String title,
                                     HsCapabilityGroup group, HsControlKind kind,
                                     List<HsControlOption> options) { }

public record HsControlOption(String optionId, String title) { }

public sealed interface HsControlValue {
    record Toggle(boolean enabled) implements HsControlValue { }
    record Level(int value) implements HsControlValue { }
    record Choice(String optionId) implements HsControlValue { }
    record Curve(List<Integer> points) implements HsControlValue { }
}

public sealed interface HsAction {
    record FindEar(Side side) implements HsAction { public enum Side { LEFT, RIGHT, BOTH } }
    record StopFindAlert() implements HsAction { }
    record Reboot() implements HsAction { }
    record Rename(String name) implements HsAction { }
    record ClearPairing() implements HsAction { }
    record ResetControl() implements HsAction { }
    record StartRecord(RecordMode mode) implements HsAction { public enum RecordMode { LOCAL, STREAM } }
    record StopRecord(RecordMode mode) implements HsAction { }
    record ReadRecordList() implements HsAction { }
    record TransferRecord(String fileId, boolean start) implements HsAction { }
    record ReadPairedDevices() implements HsAction { }
    record ForgetPairedDevice(String address) implements HsAction { }
    record SetPriorityPairedDevice(String address) implements HsAction { }
    record ConnectPairedDevice(String address) implements HsAction { }
    record DisconnectPairedDevice(String address) implements HsAction { }
}

public sealed interface HsDeviceEvent {
    record PairedDevicesChanged(List<HsPairedDevice> devices) implements HsDeviceEvent { }
    record DualConnectionChanged(HsDualConnectionState state) implements HsDeviceEvent { }
    record RecordFilesChanged(List<HsRecordFile> files) implements HsDeviceEvent { }
    record DeviceInfoChanged(HsDeviceInfo info) implements HsDeviceEvent { }
    record ServiceStatusChanged(Map<String, String> status) implements HsDeviceEvent { }
    record RemoteStreamStop() implements HsDeviceEvent { }
    record FindAlertChanged(int code, int state) implements HsDeviceEvent { }
    record BeginnerModeChanged(int earType, int actionType) implements HsDeviceEvent { }
    record EarExchangeChanged(int earType) implements HsDeviceEvent { }
    record VoiceAssistantChanged(int code, int status) implements HsDeviceEvent { }
}

public record HsPairedDevice(String name, String address, PairedDeviceType type,
                             boolean connected, PairedBusiness business,
                             boolean currentDevice, boolean priority) {
    public enum PairedDeviceType { OTHER, PHONE, COMPUTER, PAD, TV, WEARABLE }
    public enum PairedBusiness { IDLE, MUSIC, CALL }
}

public record HsDualConnectionState(boolean dualEnabled, String priorityAddress,
                                    List<HsPairedDevice> devices) { }

public record HsRecordFile(String fileId, String name, int durationSeconds, long sizeBytes) { }

public enum HsFailure {
    BLUETOOTH_DISABLED, PERMISSION_DENIED, LOCATION_DISABLED, BLE_UNSUPPORTED,
    SCAN_THROTTLED, SCAN_START_FAILED, SCAN_FEATURE_UNSUPPORTED, SCAN_OUT_OF_RESOURCES,
    SCAN_INTERNAL_ERROR, RELEASED,
    NOT_READY, TIMEOUT, REJECTED, UNSUPPORTED_CAPABILITY, DEVICE_ERROR, INTERNAL_ERROR
}

public sealed interface HsResult<T> {
    record Success<T>(T value) implements HsResult<T> { }
    record Failure<T>(HsFailure failure) implements HsResult<T> { }
}

public interface HsWriteCallback {
    void onResult(HsResult<Void> result);
}
```

### 5.1 能力 ↔ 命令路由（上层唯一映射点）

`internal/device/HsCapabilityRouter` 是能力到既有协议命令的唯一映射处。禁止在 `api/`、`provider/` 里出现能力判断分支。示例映射（实现时以现有命令类为准）：

| 能力 | 类型 | 写入命令 | 状态来源 |
|------|------|----------|----------|
| EQ_MODE | CHOICE | `EQModeCommand` | `DeviceDomainStore` eq 值 |
| CUSTOM_EQ | CURVE | `CustomEQSaveCommand`/`CustomEQRealtimeCommand` | 自定义 EQ 曲线 |
| SOUND_QUALITY | CHOICE | `SoundQualityModeCommand` | 音质模式 |
| GAME_MODE / WEAR_DETECT / VOICE_WAKEUP / FALL_ALERT / BEGINNER_MODE / INTERPRET / VOLUME_REMIND | TOGGLE | `functioncmd/*` | 对应状态 |
| TOUCH_LONG / TOUCH_SHORT / TOUCH_DOUBLE / TOUCH_TAP | CHOICE | `controlcmd/*PressSetCommand` | 手势配置 |
| FIND_EAR | ACTION | `FindAlert*Command` | 状态回包 |
| RENAME | ACTION | `DeviceRenameCommand` | — |
| CLEAR_PAIRING | ACTION | `ClearPairingCommand` | — |
| DUAL_CONNECTION | TOGGLE + LIST | `SetDualConnectionCommand` / `dualconnectioncmd/*` | `HsDualConnectionState` |
| RECORD_* | ACTION | `filerecordcmd/*` | `HsRecordFile` 列表 |
| OTA / OFFLINE_LOG / CLASSIC_BT / AI_ASSISTANT / SERVICE_STATUS / DEVICE_INFO / CONNECTION / BATTERY | — | 能力声明 + 事件 | 快照 |

`HsCapability` 的 `UNSUPPORTED_CAPABILITY` 分支必须在 `HsSessionImpl.write/perform` 入口按目录判定，不静默降级。

**机型门控**：`HsSession.connect(HsTarget)` 按 `HsDeviceCatalog.model(modelId)` 取该机型能力集合；目录未登记机型仍允许连接（链路可用），但对不支持的 `HsCapability` 的 `write/perform` 返回 `UNSUPPORTED_CAPABILITY`。

**控件类型必须与可实现行为一致（目录不变量，构造期校验，违反即抛错）**：
- `ACTION` ⇒ `HsCapabilityRouter` 必须有该能力的动作/写入映射（`perform` 可达），否则不许声明 ACTION。
- `LEVEL` ⇒ 必须带 `range`，且必须是可写能力。
- `GATE` = 仅用于功能门控、不产生通用控件行，**不得进入 `HsSnapshot.values()`**。凡"事实已在快照字段或事件里、不需要独立控件"的能力一律 GATE（`CONNECTION`、`BATTERY`、`DEVICE_INFO`、`SERVICE_STATUS`、`AI_ASSISTANT`、`OTA`、`OFFLINE_LOG`、`CLASSIC_BT`）；它们的展示由既有快照面承担（首页摘要卡、About 信息行、事件驱动的专门组件），**不允许把同一事实再复制进 `ControlValue`**（单一状态来源）。
- 禁止为"只用于门控"的能力声明 `ACTION`/`TOGGLE`/`INFO`：那会渲染出永远失败的按钮，或渲染出永远"未上报"的只读行。
- **一个能力 = 一个控件形态**：同一能力不得在 app 侧被特判成两种呈现。需要"开关 + 列表"两个面时，拆成两个能力（例：`DUAL_CONNECTION`（TOGGLE）与 `DUAL_CONNECTION_DEVICES`（LIST））。

**值未知不影响控件存在**（写入是绝对设定语义）：`TOGGLE`/`LEVEL`/`CHOICE`/`CURVE` 在值未上报时仍渲染可操作控件，值位置显示"未上报"，写入失败回退到未确认呈现。`CURVE` 的编辑器不依赖当前值即可打开（自定义 EQ 曲线未上报时从编辑器默认曲线开始）。

**快照自愈**：进入 READY 后会话层自动补查一次首页聚合状态命令（固件/颜色/在仓），并入 `HsSnapshot`；**只补一次**，失败不重试、不改连接状态，未回报的字段保持"未上报"语义；不为此新增 `HsAction`。

## 6. app 目标结构

```
com.ggec.earbuds
  provider/biz_ggec_hs/
    HsDevicePortAdapter.java        # 实现 app DevicePort（扫描/连接/观察/写/执行/释放）
    HsCapabilityCatalogAdapter.java # 实现 app DeviceCapabilityCatalog（机型/能力描述投影）
    HsValueMapper.java              # HsControlValue ↔ app ControlValue / HsFailure ↔ app DeviceFailure
  provider/lib_common/log/          # YoLogProvider + AppLog 门面（唯一 lib_common.log 入口）
  provider/lib_common/permission/   # BluetoothScanPermission + PermissionProvider
  provider/lib_common/refresh_rate/ # WindowProvider
  provider/lib_ai/                  # 现状保留
  domain/device/
    model/ DeviceSnapshot, DeviceState, DeviceCapabilityView, ControlValue, DeviceCapabilityKind
    port/ DevicePort, DeviceCapabilityCatalog, Subscription
    usecase/ ObserveDevice, ScanDevices, ConnectDevice, WriteDeviceControl, RunDeviceAction
  ui/device/
    home/    按能力描述生成入口与快捷开关
    scan/    按目录机型项做筛选
    settings/ 通用 SectionFactory：按 HsCapabilityGroup 顺序 + HsControlKind 生成行；
              自定义编辑器（自定义 EQ 曲线、录音列表、双连接设备列表）按 kind/能力定位到专门组件
```

**删除（app 侧"无端定义"）**：
`data/device/DeviceProfileCatalogImpl.java`、`data/device/EmptyDeviceControlsPort.java`、`data/device/IdleDevicePort.java`（保留一个真实的空态实现，见下）、
`domain/device/model/{Capability, CapabilitySet, DeviceModel, DeviceVariant, DeviceProfile, ProfileParams, DeviceControls, DeviceSwitch, DeviceControlPolicy 相关取值}`、
`domain/device/port/{DeviceProfileCatalog, DeviceControlsPort}`、`domain/device/usecase/{LoadDeviceProfiles, EditDeviceControls}`、
对应测试 `DeviceProfileCatalogImplTest`、`LoadDeviceProfilesTest`、`DeviceVariantTest`、`DeviceControlPolicyTest`（改写为新契约测试）。
`Provider/biz_ggec_hs` 目录由 `provider/lib_bluetooth` 改名而来（R4）。

**注意**：`DeviceSnapshot` 保留（连接态 + 电量 + 固件 + 名称 + 地址 + 颜色），它来自 biz 快照投影，不含机型知识。

### 6.1 app 侧契约（权威，`app-core` 与 `app-ui` 共同遵守）

```java
package com.ggec.earbuds.domain.device.model;

public enum DeviceCapabilityGroup { MUSIC, CONTROL, FUNCTION, DUAL_CONNECTION, RECORD, ABOUT }
public enum DeviceCapabilityKind { TOGGLE, LEVEL, CHOICE, CURVE, ACTION, LIST, GATE }

public record CapabilityOption(String id, String title) { }

/** LEVEL 类能力的取值域。由 biz 描述符提供，UI 不做推导。 */
public record ControlRange(int min, int max, int step) { }

/** APP 的呈现契约：只描述"怎么呈现"，不含任何机型知识。数据全部来自 biz 目录。 */
public record DeviceCapabilityView(String id, String title,
                                   DeviceCapabilityGroup group,
                                   DeviceCapabilityKind kind,
                                   List<CapabilityOption> options,
                                   java.util.Optional<ControlRange> range) { }

public record DeviceVariantView(String id, String displayName) { }

public record DeviceProfileView(String modelId, String displayName,
                                List<DeviceVariantView> variants,
                                List<DeviceCapabilityView> capabilities) {
    public boolean supports(String capabilityId);
    public DeviceCapabilityView capability(String capabilityId);
}

public sealed interface ControlValue {
    record Toggle(boolean enabled) implements ControlValue { }
    record Level(int value) implements ControlValue { }
    record Choice(String optionId) implements ControlValue { }
    record Curve(List<Integer> points) implements ControlValue { }
}

public record DeviceState(Map<String, ControlValue> values) {
    /** 唯一读取口：无值返回 empty，UI 用 sealed switch 分支渲染"未上报"，不设哨兵值、不抛异常。 */
    public java.util.Optional<ControlValue> value(String capabilityId);
}

public record DeviceSnapshot(ConnectionState connection, String modelId, String variantId,
                             BatterySnapshot battery, WearState wearState, String firmware,
                             String displayName, String bleAddress, int earColor,
                             DeviceState state,
                             java.util.Optional<DualConnectionView> dualConnection,
                             List<RecordFileView> recordFiles) { }

/** 扫描页观察结果。字段名与 biz `HsNearbyDevice` 对齐。 */
public record ScanResult(String modelId, String variantId, String displayName,
                         String bleAddress, int rssi) { }

public enum DeviceFailure {
    BLUETOOTH_DISABLED, PERMISSION_DENIED, LOCATION_DISABLED, BLE_UNSUPPORTED,
    SCAN_THROTTLED, SCAN_FAILED, NOT_READY, TIMEOUT, REJECTED,
    UNSUPPORTED_CAPABILITY, DEVICE_ERROR, INTERNAL_ERROR
}

public sealed interface DeviceWriteResult {
    record Ok() implements DeviceWriteResult { }
    record Failed(DeviceFailure failure) implements DeviceWriteResult { }
}

/** APP 能发起的动作：类型化，不含协议字节。 */
public sealed interface DeviceAction {
    enum RecordMode { LOCAL, STREAM }
    record FindEar(Side side) implements DeviceAction { public enum Side { LEFT, RIGHT, BOTH } }
    record StopFindAlert() implements DeviceAction { }
    record Reboot() implements DeviceAction { }
    record Rename(String name) implements DeviceAction { }
    record ClearPairing() implements DeviceAction { }
    record ResetControl() implements DeviceAction { }
    record StartRecord(RecordMode mode) implements DeviceAction { }
    record StopRecord(RecordMode mode) implements DeviceAction { }
    record ReadRecordList() implements DeviceAction { }
    record TransferRecord(String fileId, boolean start) implements DeviceAction { }
    record ReadPairedDevices() implements DeviceAction { }
    record ForgetPairedDevice(String address) implements DeviceAction { }
    record SetPriorityPairedDevice(String address) implements DeviceAction { }
    record ConnectPairedDevice(String address) implements DeviceAction { }
    record DisconnectPairedDevice(String address) implements DeviceAction { }
}

public record PairedDeviceView(String name, String address, PairedDeviceType type,
                               boolean connected, PairedBusiness business,
                               boolean currentDevice, boolean priority) {
    public enum PairedDeviceType { OTHER, PHONE, COMPUTER, PAD, TV, WEARABLE }
    public enum PairedBusiness { IDLE, MUSIC, CALL }
}

public record DualConnectionView(boolean dualEnabled, String priorityAddress,
                                 List<PairedDeviceView> devices) { }

public record RecordFileView(String fileId, String name, int durationSeconds, long sizeBytes) { }
```

```java
package com.ggec.earbuds.domain.device.port;

public interface DeviceCapabilityCatalog {
    List<DeviceProfileView> models();
    Optional<DeviceProfileView> model(String modelId);
}

public interface DevicePort {
    DeviceSnapshot snapshot();
    Subscription observe(Consumer<DeviceSnapshot> listener);
    void connect(String modelId, String variantId, String bleAddress, String displayName);
    void disconnect();
    void startScanBatch(Consumer<List<ScanResult>> onResults,
                        Consumer<DeviceFailure> onFailed,
                        Runnable onFinished,
                        Consumer<ScanResult> onRemoved);
    void stopScan();
    boolean isScanning();
    void write(String capabilityId, ControlValue value, Consumer<DeviceWriteResult> callback);
    void perform(DeviceAction action, Consumer<DeviceWriteResult> callback);
    void close();
}
```

要求：
- `ControlValue`/`DeviceCapabilityKind`/`DeviceCapabilityGroup` 是 **APP 呈现词汇**，与 biz 的 `HsControlValue`/`HsControlKind`/`HsCapabilityGroup` 由 `provider/biz_ggec_hs/HsValueMapper` 一一映射；映射必须穷尽，禁止 `default` 兜底。
- `capabilityId` 使用 biz `HsCapability` 的 `name()` 字符串（provider 侧 `HsCapability.valueOf(id)` 解析，非法 id 抛 `IllegalArgumentException`，不静默忽略）。
- APP 中不得出现任何"机型 → 能力"的静态表；`DeviceProfileView` 只能由 `provider/biz_ggec_hs/HsCapabilityCatalogAdapter` 从 `HsDeviceCatalog` 生成。
- 没有能力名称 → 控件类型的推导逻辑；`kind`/`group`/`title`/`options`/`range` 一律来自 biz 描述符。
- **目录不变量**：`kind == LEVEL` 的能力必须带 `range`，否则 `HsCatalogImpl` 在构造期抛错（快速失败），UI 不得猜值域、不得造哨兵值。
- `DevicePort` 之外保留 `domain/device/port/ScanPermissionPort`（扫描权限查询）与 `Subscription`；权限申请/结果回收仍由 Activity 经 `provider/lib_common/permission` 完成。
- LIST 类数据（双连接已配对设备、录音文件）由 `DevicePort` 事件经 `DeviceSnapshot.dualConnection`（未上报为空）与 `recordFiles`（未上报为空列表）承载，不新增端口方法；`null` 一律禁止。

## 7. 数据链路

### 7.1 设计前（现状，证据为代码位置）

1. 扫描：`ScanScreen → ScanViewModel → ScanDevices → DevicePort(HsDevicePort) → YoHsSdk.open → HsAssembly → YoHsNearby(HsNearbyScanner) → YoBleCommandLink.bleClient().subscribeScan`。
2. 机型/能力：`DeviceHomeViewModel → LoadDeviceProfiles → DeviceProfileCatalog(app 硬编码 HS01/HS02 表)`。
3. 配置写入：`DeviceSettingsScreen → DeviceSettingsViewModel → DeviceControlsPort(EmptyDeviceControlsPort)`（**未接通**，当前写入路径是空实现）。
4. 权限：`YoBluetoothSDK.ble() → BluetoothSdkCore → BluetoothProviderInstaller.installAll() → 全局静态 port`；权限查询由 app `provider/lib_common/PermissionProvider` 与库内 `PermissionProvider` 两套并存。

### 7.2 设计后

1. 扫描：`ScanScreen → ScanViewModel → ScanDevices → DevicePort(HsDevicePortAdapter) → YoHsSdk.open().nearby() → HsNearbyScanner（provider/lib_bluetooth）→ lib_bluetooth`。
2. 能力：`DeviceHomeViewModel → DeviceCapabilityCatalog(HsCapabilityCatalogAdapter) → YoHsSdk.catalog()`；app 不含机型表。
3. 配置：`DeviceSettingsScreen → WriteDeviceControl → DevicePort.write(capability, value) → HsSessionImpl.write → HsCapabilityRouter → internal/protocol 命令 → provider/lib_bluetooth 下发`。
4. 权限：`lib_bluetooth 内部 provider/lib_common/permission` 构造注入；app 侧权限只经 `provider/lib_common/permission` 查询/请求，无全局安装步骤。

## 8. 门禁（gradle 结构性检查，D8）

每个模块一个 `check<Module>Boundary`，规则来自 §1.2，**不允许任何人名/类名白名单**：

- `app`：`provider/**` 之外禁止 `import com.yovo.{lib_bluetooth,lib_ai,lib_common,biz_ggec_hs,biz_appupdate}.`，唯一例外是 §1.2 R6 的平台基线类别 `com.yovo.lib_common.event`（按包名分类，不是文件白名单）；`provider/<dep>` 内部只允许 import 对应 `<dep>`（不得跨依赖混用）；`ui/**` 禁止 import `provider.**` 与 `data.**`；`domain/**` 禁止 import `provider.**`/`data.**`/`android.*`。
- `biz_ggec_hs`：`api/**` 只允许 import `api/**`、`java.*`、`androidx.annotation`；`api/**`、`internal/**` 禁止 import `provider.*`/`lib_bluetooth.*`/`android.bluetooth.*`；非 `provider/**` 禁止 import `lib_bluetooth.*`；`provider/lib_bluetooth/**` 禁止 import `biz_ggec_hs.api` 之外的内部包（不得反向点名上层状态机）。
- `lib_bluetooth`：`provider/**` 之外禁止 import `lib_common.*`；`api/**`、`model/**` 禁止 import `provider.*`、`internal.*`；`provider/**` 禁止被 `api/**`、`model/**` import。
- 跨模块：任何模块（除消费方 provider 目录外）不得 import 其它模块的 `provider.*`、`internal.*`。
- 现有 `checkAppMvvmLayers`、`checkExplicitImports`、`checkFrameworkToastForbidden`、`checkSingleYoLogInit` 保留（无白名单），并入统一入口。

## 9. 验收（缺一不算完成）

1. `./gradlew :app:assembleDebug :biz_ggec_hs:check :lib_bluetooth:check :lib_common:check --offline` 通过（含新增/改写的边界门禁）。
2. `./gradlew test --offline` 全绿；被删能力的测试**改写**为新契约测试，不得 `@Ignore`、不得删测了事。
3. `grep` 全仓：`BluetoothProviderInstaller`、`YoHsBroadcast`、`HsAssembly`、`DeviceProfileCatalogImpl`、`DeviceControlsPort`、`EmptyDeviceControlsPort`、`LoadDeviceProfiles` 零残留。
4. 无 `TODO`/`FIXME`/`HACK`/`@Deprecated` 兼容层/双轨/注释冻结。
5. 本文件 §7.2 每条链路在代码中有对应实现点（评审时逐条指认文件）。
6. 架构审查六段报告（`review.md`）对三个模块分别给出 CLEAN，且无"非阻塞观察"。

## 10. 分工与写作用域（互不重叠）

| 成员 | 写作用域 | 任务 |
|------|----------|------|
| lead | `docs/architecture/**`、根 `build.gradle`、跨模块集成 | 契约、集成、最终验收 |
| teammate `libbt` | `lib_bluetooth/**`（含 build.gradle） | §3 全部 |
| teammate `biz` | `biz_ggec_hs/**`（含 build.gradle） | §4、§5、§5.1 |
| teammate `app` | `app/**`（含 build.gradle，不含 app 的 docs） | §6、§7.2、app 门禁 |
| teammate `reviewer` | 只读 | §9 独立复审、六段报告 |
