# APP 全量重构契约（2026-11 · app-rebuild）

> 本文件是本轮重构的**唯一权威契约**；与 `docs/architecture/2026-11-device-mgmt/design.md`（其中 home→roster 迁移未落地，撤销）冲突时以本文件为准。
>
> 依据：YoAgentDocs `task.architecture-review` + `playbook/architecture-review` + `rules/common/architecture.md`
> （旧架构不打补丁 / 严禁兼容层 / 禁止防御性修补 / 严禁残留技术债务 / 严禁简化实现）。
>
> 适用范围：`app/**` 全层 + `app` 直接消费的 provider 适配层 + `app/build.gradle` 门禁 +
> 被 app 直接消费的库层卫生项。库内部实现（lib_bluetooth 协议栈等）不在本轮写作用域。

## 1. 目标分层（唯一一套）

```
ui/         View（Activity/Screen/View/Row）+ ViewModel + UiState/UiMapper + 资源文案
domain/     纯 Java：model（不可变 record/sealed）、port（接口）、usecase（编排）、
            session/machine（显式状态机）、policy（纯函数）
data/       DocumentStore（唯一 IO 原语）+ codec（严格版本化）+ repository（每文档单写者）
platform/   Android 能力实现（音频/密钥/时钟/权限宿主）
provider/   每依赖一目录：依赖 API ↔ 本模块端口适配；只被 wiring 引用
wiring/     装配根：唯一创建实现、唯一持有生命周期
```

规则（结构性，不靠注释）：
1. `ui` 不 import `data`/`provider`；`domain` 不 import `android.*`/`org.json`/`data`/`ui`/`platform`/`provider`/`wiring`；`data` 不 import `ui`/`platform`；`provider` 只 import 自身依赖。
2. ViewModel 不 import `android.view|widget|content|app|os.Handler|Looper`、不持 `Context`、不拼用户文案。
3. `provider.**` 只被 `wiring/**` 引用。目录名 == 依赖模块名。
4. 用户可见文案只进 `res/values/strings*.xml`；颜色/尺寸/时长进 theme/token。
5. 产品型号（HS01/HS02/WY01…）只出现在 provider 目录投影与 biz 内部；`ui`/`domain`/`data`/`wiring` 零字面量。

## 2. 统一数据源

### 2.1 唯一 IO 原语
```java
public interface DocumentStore {                 // data/store
    Optional<String> read(String name);          // 缺失 = empty（不是 null/不是空串）
    void write(String name, String content);     // 同目录 tmp + fsync + ATOMIC_MOVE
    void delete(String name);
    List<String> list(String directory);
}
```
- 唯一实现 `FileDocumentStore`（根目录由装配根给出）。台账、AI 设置、AI 会话、界面偏好**全部**经它读写。
- 禁止：模块自建 `FileOutputStream`/`Files.write`/`SharedPreferences`/第二套原子写。
- 每次业务提交只经**一个** repository 方法；跨文件提交（文档 + 密钥）必须在同一 repository 内成对完成，失败回滚。

### 2.2 文档与写者
| 文档 | 持有者 | 写者 |
|------|--------|------|
| `ai/settings.json` | `AiSettingsRepositoryImpl` | `AppExecutors.settingsPersistence`（串行、合并写到最新 revision） |
| `ai/conversations/*.json` + `ai/conversations/index.json` | `AiConversationRepositoryImpl` | `AppExecutors.historyIo` |
| `devices/roster.json` | `DeviceRosterRepositoryImpl` | `AppExecutors.deviceStore`（串行、合并写） |
| 密钥（`noBackup/ai/secrets.json`） | `EncryptedSecretStore` | 调用方执行器（同一提交点） |
| 界面偏好（`lastSettingsMode`） | 并入 `ai/settings.json` 的 `preferences` | 同 settings |

- 删除 `platform/ai/prefs/SharedPreferencesAiUiPreferences` 与 `domain/ai/port/AiUiPreferences`；
  `AiChatPreferences` 增加 `lastSettingsMode`，设置仓库读写它。
- 严格版本：`decode` 第一步读 `v`；`v != CURRENT` → 抛 `DocumentFormatException`（→ `AiFailure(STORAGE)`），
  **不做旧字段/旧键迁移**。旧文档按「读不出来」上报，不静默清空、不猜形。
- `JsonValues` 只保留两种语义：`required*`（缺失/类型错抛 `DocumentFormatException`）与 `optional*`（返回 `Optional`）。
  删除所有带 fallback 参数的方法。
- 未知枚举持久化值不得映射成合法业务事实：解析失败 = 文档损坏（抛），或 `Optional.empty()` 后由用例丢弃并上报。

### 2.3 持久化模型与领域模型分家
- `data/device/json` 定义 `RosterDto/DeviceDto/IdentityDto/FactsDto/StateDto/ValueDto`，领域 record 不参与序列化。
- 台账键名与契约单点一致（`id.modelId/address/classicAddress/classicName`），读侧只认该键，无双读。

## 3. 设备组合层（删除补丁式多产品接入）

### 3.1 删除
`wiring/device/DeviceDriver.java`、`HsDeviceDriver.java`、`WyDeviceDriver.java`、
`CompositeDeviceLinkAdapter.java`、`CompositeCapabilityCatalog.java`、`CompositeRecordPort.java`。

### 3.2 新契约
```java
// domain/device/model
public enum DeviceTransport { BLE, CLASSIC }

// wiring/device（装配层内部类型，不出现在 ui/domain 公共签名里）
public interface DeviceEndpoint {          // 一个 SDK 实例 = 一个传输
    DeviceTransport transport();
    DeviceCapabilityCatalog catalog();
    DeviceLinkPort link();
    RecordPort record();
    void close();
}
```
- `DeviceEndpoints`（wiring）：构造期用各 endpoint 的 `catalog().models()` 建立 `Map<modelId, DeviceEndpoint>`；
  同一 modelId 出现在两个 endpoint → 构造期抛 `IllegalStateException`（单一真源，不静默取第一个）。
- `DeviceCatalog`（wiring）：合并各 endpoint 目录，`model(null)` → 抛；`model(unknown)` → `Optional.empty()`。
- `DeviceGateway implements DeviceLinkPort`（wiring）：
  - `connect(target)`：`catalog.model(target.id().modelId())` 查 endpoint；未登记 → `ConnectSettlement.Failed(DEVICE_NOT_FOUND)`；
    记录 `connectedTransport`（唯一连接路由状态，`connect` 设置、`disconnect`/失败清除），不做 `get(0)` 兜底。
  - `write/perform`：按 `connectedTransport` 取 endpoint；未连接 → `Failed(NOT_READY)`。
  - `snapshot()`：`connectedTransport == null` → `DeviceSnapshot.idle()`；否则该 endpoint 的快照。
  - 订阅：构造期订阅全部 endpoint，仅转发 `connectedTransport` 对应的快照。
  - `startScanBatch`：启动**全部** endpoint 的扫描，合并结果（`ScanResult` 已含 modelId，地址去重）；
    `filterModels(Set)` 语义按契约：空集 = 不限；非空集逐元素下发到所有 endpoint（provider 热替换规则，**不重启扫描**）。
  - `stopScan`：停全部。`close`：按 endpoint 顺序关闭并解订阅。
- `RecordPort` 由 `DeviceGateway` 自身实现：按 `connectedTransport` 路由；未连接时
  `supportedModes()` 返回空集、其余操作 `Consumer<Boolean>` 以 `false` 结束——这是「无当前设备」的显式结果，
  不是兜底成功。`RecordPort` 的失败通道改为分类枚举（见 §5）。
- `DeviceGraph`：只 create endpoints → gateway/catalog/session/usecases；`release()` 显式 `session.close()` → `gateway.close()`。
- `SyncActiveDevice` 不再在构造期自动启动：由 `AppContainer` 显式 `start()`，`DeviceGraph.release()` 调 `stop()`。

### 3.3 扫描筛选
- `ScanViewModel` 默认筛选 = 目录首项（`models().get(0).modelId()`），目录空 → 空集（不限）。
- 删除 `"HS02"` 全部字面量与 `ScanUiState` 内置机型表；空目录 UI 渲染"无可用机型"空态。
- 切筛选只调 `filterModels`，不启动/停止扫描（`CompositeDeviceLinkAdapterTest` 的「切驱动重扫」断言删除并改写）。

## 4. 状态机（每个功能一个显式状态 + 一个迁移函数）

### 4.1 DeviceSession
```java
sealed interface SessionState {
    record Idle() implements SessionState {}
    record Connecting(DeviceTarget target) implements SessionState {}
    record Ready(DeviceTarget target) implements SessionState {}
    record Failed(DeviceTarget target, DeviceFailure failure) implements SessionState {}
}
```
- `open(target)`：`generation++`；旧 pending 以 `REJECTED` 结算；`state = Connecting(target)`；
  `port.disconnect()`；发布 CONNECTING 快照；`port.connect(...)`。
- 快照 READY → `Ready`；DISCONNECTED（设备主动）→ `Idle` + 清 target。
- `close()`：`generation++`；pending → `REJECTED`；`port.disconnect()`；**清 target**；
  `state = Idle`；发布一次 DISCONNECTED 快照（唯一生产者）。
- 契约：端口只在设备真实状态变化时发布快照；`disconnect()` 不发布业务快照（HS/WY adapter 同步改）。
- 所有迁移在锁内完成，回调在锁外调用。

### 4.2 RecordMachine（耳机录音，domain，纯函数）
```java
record RecordState(RecordMode mode, Set<RecordMode> supported, boolean connected,
                   Stream stream, DeviceRecord deviceRecord, Sync sync,
                   List<SavedRecordItem> localFiles, List<RecordFileView> deviceFiles,
                   Optional<String> playingFile)
sealed interface RecordEvent { Connected(...), Disconnected(), ModeSelected(...),
    StreamState(...), StreamSaved(...), StreamError(RecordFailure),
    DeviceRecordStarted/Failed, DeviceRecordTicked(nowMs), SyncProgress(...), SyncSucceeded(...), SyncFailed(...),
    LocalFilesReloaded(...), PlaybackStarted/Stopped, DeleteResult(...) }
sealed interface RecordEffect { StartStream, StopStream, StartDeviceRecord, StopDeviceRecord,
    RefreshDeviceFiles, SyncFile(String), CancelSync, DeleteDeviceFile(String), Play(...), StopPlayback, ReloadLocal }
```
- `RecordMachine.reduce(state, event) -> Transition(state, effects)`：穷尽 switch，无 `default`，无 `null`。
- 计时由 `Clock` 端口 + `Ticker` 驱动 `DeviceRecordTicked`，不在状态机里读 `SystemClock`。
- `RecordUiState` 由 `RecordUiMapper.map(state)` 纯派生；VM 只做意图转发与 effect 执行。
- 文案：`RecordEvent` 只带分类与结构化载荷；中文文案进 `strings.xml`（`earbuds_record_*`）。
- 修复 `supportedModes` 空集时 `iterator().next()` 崩溃：空集 = 不支持录音，渲染禁用态。

### 4.3 其他状态机
- `ScanViewModel`：`ScanPhase {IDLE, SCANNING, STOPPED, CONNECTING(address), FAILED(failure)}` 单一字段，取代 `scanning/hasScanned/connectPhase` 三源；保留 `scanToken/connectEpoch` 代际丢弃。
- `DeviceSettingsViewModel`：写入意图状态入 `DeviceSettingsUiState`（每行 `WriteState {IDLE, IN_FLIGHT, CONFIRMED, FAILED}`），删除 View 层 `pendingToggles/curveDrafts` 手工回退；`onAction` 穷尽、无 `default -> {}`。
- `AiCallViewModel`：删除字段群输入源，`uiState` 只作输出；`Phase` 单源派生 `live/ended`。

## 5. 错误与失败通道
- 每个 feature 一份 `sealed`/enum 失败分类：`DeviceFailure`、`AiFailureKind`、`RecordFailure`、`RecordPenFailure`。
- 载荷只放结构化字段（id/路径/计数/已收字节）；禁止用户句子、英文技术串、`Throwable.getMessage()` 进邻层错误。
- 端口回调不再用 `Consumer<Boolean>` / `onError(String)`；用结果对象（`sealed interface XxxResult`）。

## 6. 录音笔入口（原 `com.ggec.earbuds.record`）——本轮完全不处理

产品决定（2026-11-17）：第二个 Record 入口本轮不动，不重构、不冻结、不隔离改造、不纳入验收；
重点是自底而上的 EarBuds 链路（lib_bluetooth → lib_common → biz_ggec_hs → app）。

- `record/**` 保持现状（与 HEAD 一致），本文件的其它条款不适用于它。
- 本轮不新建 `provider/biz_ggec_record`、`domain/recordpen`、`ui/recordpen`、`RecordPenGraph`。
- 门禁扫描跳过该目录（`untouchedFeatureRoots`），不新增逐文件/逐类名白名单；后续另立范围再处理。

## 7. 文件职责与拆分
- 一个文件一类职责：ViewModel 不建 View；View 不判业务；Mapper 纯函数；Repository 只做持久化与提交顺序。
- `SettingsRowsView` 的字符串 diff 重建改为按 `DeviceSettingsUiState.rows` 的结构等价判断；`pending` 状态移入 UiState。
- `ScanUiState` 去掉 `Context` 与文案解析；`ScanScreen` 拆 `ScanFilterController`（下拉状态）+ 列表适配器。
- 时长格式化单点 `DeviceFormats.duration(ms)`；删除面板内两处复制实现。
- `CustomEqSheet` 的值域/频段来自 `DeviceCapabilityView` 描述符（biz 目录），不在 UI 写死。

## 8. 门禁（`app/build.gradle`，结构性、无白名单）
1. `providerDependencyModules` 从 `configurations.implementation` 的 `ProjectDependency` 派生，不手写。
2. 删除 `recordLegacyRoot` / `recordDirectModules` 与两处判断。
3. `platformBaselinePackages` 仅对 `ui/**` 生效（`com.yovo.lib_common.event`）。
4. 逐类名/文件名判定改为包前缀判定；删除 `OneShotLiveData.java`、`FileAiDocumentStore`、`YoLogBootstrap.java` 等字面量。
5. 新增结构规则：
   - `com.ggec.earbuds.record` 包不存在（历史双轨）。
   - `ui/**` 不得 import `java.io.*`/`java.nio.file.*`/`org.json.*`。
   - `data/**` 不得 import `android.content.SharedPreferences`。
   - `domain/**` 不得 import `com.ggec.earbuds.data.store`（IO 原语属数据层）。
6. 所有门禁跑在 `preBuild`。

## 10. 决策定稿（2026-11-17，无待确认项）

### 10.1 本轮 Goal 的模块范围
纳入：`app`、`lib_bluetooth`、`lib_common`、`lib_ggec_opus`、`biz_ggec_hs`、`biz_ggec_wy`、`biz_appupdate`。
不纳入：`lib_ai`（内部不重排；`app/provider/lib_ai` 只做目录归位与签名适配）、`yotheme`、`biz_ggec_record`、`sherpa_onnx`、`record/**`。

### 10.2 生命周期归属
`SyncActiveDevice` 由 `AppContainer` 显式 `device.start()` / `device.release()` 成对管理；
`DeviceGraph` 构造期不启动任何写盘或订阅，只装配。

### 10.3 行为语义（默认可用，禁止锁定）
- 默认值只允许是**产品默认**（目录首项、出厂模式），且必须是显式、可展示、可覆盖的；不得用默认值掩盖「未上报」。
- 「未上报/未知」一律用显式未知语义（`Optional`/`UNKNOWN`），不映射成某个具体事实。
- 不锁定能力：`setPreferredPhy(ALL/UNKNOWN)` = 不锁定 PHY（三掩码并集）；WY 控制值必须真实投影，不得恒空。
- 失败只分类；文案只在 ui 资源层。

### 10.4 剩余项的收口设计（逐条，全部在本 Goal 内完成）
| # | 现状 | 目标 |
|---|------|------|
| R-1 | `WyDeviceLinkAdapter.toDeviceState` 恒空 | 把 `WyDevice.Snapshot.values()` 全量投影为 `DeviceState`（与 HS 同构映射，穷尽 switch）；缺值=不写入该 key |
| R-2 | `SyncProgressState.errorMessage`、`StreamAudioSessionState.currentFileName` 恒空 | 删除两字段及全部构造/消费点；失败走 `RecordFailure`，文件名由 `SavedRecordItem` 承载 |
| R-3 | `ScanScreen` 377 行、筛选/列表/渲染混装 | 拆 `ScanFilterController`（筛选下拉状态）+ `ScanDeviceAdapter`（行绑定）+ `ScanScreen` 只做骨架与渲染；删除 `device_scan_filter_empty_*` 死文案 |
| R-4 | provider 平铺 | 按 `architecture-rules.md` §2.1 归位：`provider/biz_ggec_hs/{device,record,value}`、`provider/biz_ggec_wy/{device,record,value}`、`provider/lib_ai/{chat,platform,realtime}`；`provider/lib_common` 保持 `log|permission|refresh_rate` |
| R-5 | `CustomEqSheet` 写死频段/增益域 | biz 的 `CUSTOM_EQ` 描述符必须带 `ControlRange` 与频段 options；UI 只渲染描述符，缺描述符=不可用（不猜） |
| R-6 | `RealtimeVoiceSession` 三处状态机缺陷（effect 内重入 apply、`stopRequested` 静默丢事件、THINKING 无超时） | reducer 增加 `Timeout` 事件与 THINKING 超时迁移；effect 失败改为「事件重入队列」；停止态由显式终态判定，不用标志位前置拦截 |
| R-7 | `AiCallViewModel` 字段群/uiState/领域快照三源 | 删除字段群；`uiState` 只作输出；`live/ended` 由 `Phase` 派生 |
| R-8 | `lib_common` 静态装配态（已扩展为日志域 `LogCore/YoLog/YoLogBootstrap/CrashFlushInstaller/LogContext`、下载域 `DownloadCore/DownloadInstallation/DownloadNotificationController/SqliteBreakpointStore`、保活 `KeepAliveCore`；`lib_bluetooth` 射频共享 `YoBtRadioSharing`）；另清 `Thread.sleep` 轮询与 `legacy` 命名 | 全部改为门面/持有者实例，构造注入；`src/main` 的 `static volatile`/`static Map`/`catch (... ignored)`/`Thread.sleep` 归零 |
| R-9 | `lib_common` 测试基线失败（`FileCoreAccessGateTest` 依赖 `ShadowApplication.denyPermissions` 与 SDK37 android.jar 不兼容） | 改为不依赖该 Shadow 的等价断言，模块 `testDebugUnitTest` 必须绿 |
| R-10 | `biz_appupdate` 内嵌 9 个 Android UI 文件、静态单例 `YoAppUpdateCore`、R6 口径与 `internal/ui` 不匹配 | UI 以 `UpdateUiPort` 契约暴露状态与回调，Android 视图实现移入 app（`ui/update`）；核心去单例由门面持有；R6 基线按包名放行 |
| R-11 | `biz_ggec_hs` 与 `biz_ggec_wy` 22/25 文件同构（音频帧装配重复） | 抽 `lib_ggec_audio`（L1，仅依赖 `lib_ggec_opus`）：帧装配/格式探测/包跟踪/契约；两个 biz 只保留各自命令表与产品差异，删除复制实现 |
| R-12 | `biz_ggec_hs` 目录与旧 design §4/§5 不符 | 以现实现为准重写契约：`api/` 保留 `YoHsSdk` + `HsDevice/HsControl/HsScan/HsRecordApi`；`internal/center/**` 作为声明结构写入本文，不再引用失效的旧 §4 目录表 |
| R-13 | app 门禁仍有按文件名/类名的判定（`OneShotLiveData.java`、`YoLogBootstrap` 路径） | 改为包结构判定；基线类型重复检测用「非 lib_common 包内出现同名类型」的结构规则 |

### 10.5 验收（本轮 Goal，全部可二判）
1. `:app:assembleDebug`、`:app:testDebugUnitTest`、`:lib_bluetooth`、`:lib_common`、`:lib_ggec_opus`、`:biz_ggec_hs`、`:biz_ggec_wy`、`:biz_appupdate` 的 `testDebugUnitTest` 全绿。**已通过**：1258 个用例，0 失败/0 错误/0 跳过（app 613 / lib_common 182 / lib_bluetooth 205 / lib_ggec_audio 12 / lib_ggec_opus 2 / biz_ggec_hs 172 / biz_ggec_wy 13 / biz_appupdate 59）。
2. `:app:preBuild` 与各模块 `check` 门禁全绿，且无类名/文件名白名单。**已通过**：8 个模块 `check`（含 lint）与 `app:preBuild` 全绿；门禁全部按目录/包前缀判定，`lib_bluetooth` 根包改为「.java 数量==1」、`lib_common` 公开面按包结构、`lib_ggec_opus` 新增门禁、`biz_appupdate` 新增 api 纯净与内部 import 门禁。
3. `grep`：`"HS02"|drivers.get(0)|Composite|DeviceDriver|SharedPreferences|realtimeModels|PENDING_DECODER|catch (Throwable ignored` 在纳入模块的 `src/main` 全为 0。**已通过**（`"HS02"` 仅存于 biz 产品目录本身，属唯一真源）。
4. R-1…R-13 每条有代码位置与单测/门禁证据；无「待确认」「后续」条目。**已闭合**（见 §10.4 各行）。

### 10.6 第一波（已落地，保留作回归基线）
1. `:app:assembleDebug` 成功；`:app:testDebugUnitTest` 0 failure/0 error/0 skipped。
2. `:app:preBuild` 全部门禁通过（含新增结构规则，无白名单/类名判定）。
3. `grep -rn '"HS02"\|"HS01"\|"WY01"' app/src/main/java/com/ggec/earbuds/{ui,domain,data,wiring}` = 0。
4. `grep -rn 'com.ggec.earbuds.record\|recordLegacyRoot\|CompositeDeviceLinkAdapter\|DeviceDriver' app/src/main` = 0。
5. `grep -rn 'SharedPreferences' app/src/main/java` = 0。
6. `grep -rn 'lastSettingsMode(.*fallback\|fromId(.*fallback\|JsonValues\.[a-z]*(.*,.*fallback' app/src/main/java` = 0。
7. `DeviceSessionTest`：`close()` 后 state=Idle、target 清空、观察者收到一次 DISCONNECTED；同一 target 重开不重复 disconnect。
8. `DeviceGatewayTest`：未知 model → `DEVICE_NOT_FOUND`；未连接 write → `NOT_READY`；`filterModels(empty)` 不重启扫描；切筛选 `stopScan` 调用次数 = 0。
9. `RecordMachineTest`：空 supportedModes 不崩；tick 由事件驱动；sync 进度/成功/失败迁移穷尽。
10. `AiSettingsJsonTest`：`v` 不匹配、缺字段、未知枚举 → `DocumentFormatException`；legacy 键不再被接受。
11. `RecordPenViewModelTest`：扫描→连接→会话状态由事件驱动；Activity 不含 SDK import。
12. 未实机：设备侧 BLE/SPP 运行观察仍为「已读到」，报告如实标注。
