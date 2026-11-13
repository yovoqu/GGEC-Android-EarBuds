# APP 设备管理域架构设计（2026-11）

> 权威契约：本文件。实现与评审以本文件为准；与本文件冲突的实现视为未完成。
> 依据：YoAgentDocs `task.architecture-design` / `task.architecture-review` + `rule.common.architecture` + `rule.common.stack-layering` + `rules/by-type/android`（层名 View / ViewModel / Model）。
> 本轮范围：`app` 模块的设备管理域（`domain/device`、`data/device`、`provider/biz_ggec_hs`、`ui/device`、`wiring`）、`app` 架构门禁、旧文档/旧代码清理、公共事件原语单源化。
> 不在本轮范围：`biz_appupdate`（用户明确暂不处理）、AI 域的整改（下一刀另声明，见 §13）、`lib_*` 与 `yotheme`。

## 0. 决议（本轮定案）

| 编号 | 决议 |
|------|------|
| D10 | 设备管理 = **设备台账（roster）+ 单活动会话（one active session）+ 每设备档案（archive）**。biz 只有一条 `HsSessionImpl` 与一条 BLE 客户端，APP **不做 N 路并行会话**；多设备指"可管理多台设备、记住每台的最后已知状态与信息"，不是"同时连 N 台"。 |
| D11 | 设备身份唯一键 `DeviceId(modelId, bleAddress)`；`classicAddress` 是属性不是身份。扫描、连接、档案、台账共用同一身份，地址规范化单点。 |
| D12a | 断开即清空实时值：`ARCHIVED` 只保留名称/MAC/型号与 `lastSeenMillis`；电量/固件/佩戴属会话内事实，不得以档案形式冒充现状。 |\n| D12 | 状态来源必须显式：`LIVE`（活动会话实时）/ `ARCHIVED`（落盘档案）/ `DISCOVERED`（扫描到未入台账）。非活动设备**不得**用 `0`/`--`/假值冒充实时值；缺失即 `UNKNOWN` + `lastSeenMillis`。 |
| D13 | 活动会话的所有权在 `domain`：`DeviceSession` 持有代数（generation）、先断后连、终态回调只对当前代生效。`provider` 只做投影，不再做"换目标时把上一个回调判 REJECTED"这类仲裁。 |
| D14 | 台账持久化走**单写者 + 合并写入**：内存即时发布，落盘在 `device-store` 串行执行器上合并最后一次状态，主线程不碰 JSON/文件/fsync。 |
| D15 | 删除 `DeviceIntake`：它是"永远为真"的假开关（适配器只产出 `CONNECTION`，首页据此永远走空分支）。是否已有设备事实由 `DeviceFacts.known()` 显式回答。 |
| D16 | `ui` 与 `domain` 的公共事件原语单源：统一用 `com.yovo.lib_common.event.OneShotLiveData`（design.md §1.2 R6 基线），删除 `app/ui/OneShotLiveData`，门禁按包名放行基线。 |
| D17 | 持久化 / 领域 / 展示模型分家：`data/device/json` 的 DTO 只服务编解码，`domain` 模型不 import `org.json`。通用 JSON 取值工具上移为 `data/json/JsonValues` 单源。 |

## 1. 设计前：现状链路（证据等级 = 已读到）

未接真机跑本轮路径，以下节点均为「已读到」（有代码位置，无运行观察），只证明边存在。

**链路 A：连接一台设备 → 首页渲染**

| # | 节点 | 位置 | 等级 |
|---|------|------|------|
| A1 | 入口：点"+"进扫描页 | `ui/device/home/DeviceHomePage.java:181-185` → `MainActivity`（ShellScreen 转交） | 已读到 |
| A2 | 扫描状态持有 | `ui/device/scan/ScanViewModel.java` → `ScanDevices` → `DevicePort.startScanBatch` | 已读到 |
| A3 | 连接用例 | `domain/device/usecase/ConnectDevice.java:17-21` | 已读到 |
| A4 | 端口实现 | `provider/biz_ggec_hs/HsDevicePortAdapter.java:89-123` → `YoHsSdk.connect`（`YoHsSdk.java:80-82`） | 已读到 |
| A5 | 会话 | `biz_ggec_hs/internal/link/HsSessionImpl.java`（单会话） | 已读到 |
| A6 | 状态持有 | `DeviceHomeViewModel.java:112-127`（`DeviceSnapshot` → mapper → 3 条 LiveData） | 已读到 |
| A7 | 渲染 | `DeviceHomeUiMapper.java:38`（`intake == CONNECTION` 分支）→ `DeviceHomePage.render` | 已读到 |
| A8 | 副作用终点 | 屏幕像素 + `DeviceSettingsViewModel` 的能力写入（`DevicePort.write`） | 未观察 |

**链路 B：能力写入**

`DeviceSettingsActivity` → `DeviceSettingsViewModel.write`（`:94-99`）→ `WriteDeviceControl` → `DevicePort.write` → `HsValueMapper.capabilityOf` → `YoHsSdk.write` → `HsSessionImpl` → 协议帧。节点均有代码位置（已读到）。

**多设备事实**：当前**没有**台账、没有历史设备、没有每设备档案。`app/src/main/java` 里设备身份只出现在 `ScanResult`/`DeviceSnapshot` 的散字段上，进程退出即丢失；UI 只显示"当前这一台"。

## 2. 目标分层

依赖方向（单向）：

```
ui/device  ──►  domain/device  ◄──  data/device
                     ▲                 
                     └── provider/biz_ggec_hs ◄── biz_ggec_hs
wiring  ──►  { ui, domain, data, provider }   （唯一装配点，唯一 import provider 的地方）
```

| 层 | 目录 | 职责 | 不做什么 |
|----|------|------|----------|
| View | `ui/device/{roster,scan,settings}` | 布局、绑 UiState、转发意图 | 不碰仓库/端口/JSON/文件；不推导业务状态 |
| ViewModel | 同上（`*ViewModel`） | 单一 UiState + 一次性事件；把意图交给用例 | 不持 Context/View/YoUI；不自建线程池；不 import `wiring`/`data`/`provider` |
| Model | `domain/device/{model,port,session,roster,usecase}` | 台账、身份、状态、会话仲裁、持久化端口 | 不 import Android/JSON/`data`/`ui`/`provider` |
| 持久化 | `data/device/{json,store,roster}` | JSON DTO、原子写盘、内存仓库 | 不 import `ui`/`provider`；不做业务规则 |
| 适配 | `provider/biz_ggec_hs` | biz 事件 → 领域快照/扫描结果；SDK 生命周期 | 不解释协议字节；不做重试/仲裁/机型判断 |

## 3. 领域模型

```text
DeviceId(modelId, bleAddress)                       身份唯一键，地址规范化单点
DeviceIdentity(bleName, classicAddress, classicName)  其余双模身份（bleAddress 在 Id 里）
RecordOrigin{LIVE, ARCHIVED, DISCOVERED}            状态来源
DeviceStatus(origin, connection, lastSeenMillis)     一条设备"现在怎样"
DeviceFacts(battery, firmware, wearState, earColor)  只读设备事实，未知显式 unknown()
DeviceRecord(id, alias, identity, status, facts, state)  台账一条（不可变）
DeviceRoster(devices: List<DeviceRecord>, activeId: Optional<DeviceId>)  台账（不可变）
DeviceTarget(id, identity)                          一次连接的目标
```

规则（全部落在 `domain`，纯函数、可单测）：

1. `DeviceId` 构造时规范化 `bleAddress`（去空白、转大写），`key()` = `modelId + "@" + bleAddress` 用于持久化与相等。
2. `DeviceRecord.displayName()` = `alias` 非空取别名，否则 `identity.bleName()` 非空取名，否则 `modelId`。别名是用户事实，单独字段持久化，不覆盖身份。
3. `DeviceRoster.sorted()` = 活动设备置顶，其余按 `lastSeenMillis` 降序，同时间按 `key()` 字典序（稳定）。
4. `DeviceRoster.upsert(record)` 以 `DeviceId` 去重；`without(id)` 删除并从 `activeId` 摘除（不自动改选下一台——不替用户做决定）。
5. `DeviceRoster.withActive(Optional<DeviceId>)` 只改活动指针，不动记录。
6. `DeviceFacts.known()` = 任一路电量已知 或 firmware 非空；这是"有没有设备事实"的**唯一**回答，取代 `DeviceIntake`。
7. 非活动设备 `DeviceStatus.origin = ARCHIVED`、`connection = DISCONNECTED`、`lastSeenMillis > 0`；活动设备 `origin = LIVE`，`connection` 取会话快照。

## 4. 端口与用例

```java
// domain/device/port
interface DeviceLinkPort {                 // 原 DevicePort，语义收窄为"一条链路"
    DeviceSnapshot snapshot();
    Subscription observe(Consumer<DeviceSnapshot>);
    void connect(DeviceTarget target, Consumer<ConnectSettlement> onSettled);
    void disconnect();
    void startScanBatch(FourCallbacks...);  // 四回调签名不变
    void stopScan();
    boolean isScanning();
    void filterModels(Set<String> modelIds);
    void write(String capabilityId, ControlValue value, Consumer<DeviceWriteResult>);
    void perform(DeviceAction action, Consumer<DeviceWriteResult>);
    void close();
}
interface DeviceRosterRepository {           // 台账：内存快照 + 观察 + 变更
    DeviceRoster current();
    Subscription observe(Consumer<DeviceRoster>);
    void update(UnaryOperator<DeviceRoster> change);
}
interface DeviceCapabilityCatalog { ... }   // 不变
interface ScanPermissionPort { ... }        // 不变
```

用例（`domain/device/usecase`）：

| 用例 | 职责 | 不做什么 |
|------|------|----------|
| `ObserveDeviceRoster` | 合并「仓库台账 + 会话 live 快照」为对外 `DeviceRoster` 流；live 只在 `activeId` 匹配时覆盖 | 不写盘、不持 UI |
| `OpenDevice` | 目标 = 当前活动设备且 READY → 直接 `Ready`；否则 `DeviceSession.open`（先断后连）→ 成功即 `withActive` + upsert(LIVE) | 不重试、不弹窗 |
| `CloseDevice` | `DeviceSession.close` + 该设备转 ARCHIVED（保留档案） | 不清台账 |
| `ObserveDevice` | 读活动会话快照（供设置页） | 不碰台账 |
| `WriteDeviceControl` / `RunDeviceAction` | 类型化写入/动作 | 不判机型（biz 判） |
| `RenameDevice` / `ForgetDevice` | 改别名 / 删记录与档案 | 不动 biz 绑定 |
| `ScanDevices` | 扫描批次四回调 | 不做识别规则 |

`DeviceSession`（`domain/device/session`，包 `DeviceLinkPort`）：

- `long generation`；`open()` 先 `generation++`，若当前非 IDLE 先 `disconnect()`，再 `connect()`。
- `onSettled` 只在 `generation == 当前代` 时回调；跨代回调直接丢弃（按代际归属丢弃，不用延时/标志位编时序）。
- `close()`：`generation++` + `disconnect()` + 快照转 `DISCONNECTED`。
- `observe()` 透传端口回调，并记录 `activeTarget`，供 `ObserveDeviceRoster` 判断"live 属于哪台"。

## 5. 数据链路（设计后）

**链路 A：打开设备 → 台账 + 首页**

```
DeviceRosterPage 点击某条 → DeviceRosterViewModel.open(id)
  → OpenDevice.execute(target)                      domain/usecase
  → DeviceSession.open(target)                       gen++；必要时先断
  → DeviceLinkPort.connect(target, settle)           provider/biz_ggec_hs
  → YoHsSdk.connect → HsSessionImpl → BLE/协议
  ← ConnectSettlement.Ready
  → DeviceRosterRepository.update(r -> r.withActive(id).upsert(liveRecord))
  → ObserveDeviceRoster 重新投影（live 覆盖该条）
  → DeviceRosterViewModel.uiState（单一 LiveData）
  → DeviceRosterPage.render
```

失败分支：`Failed(failure)` → 台账不变（不写 active），`events` 发一次失败事件，页面提示并留在原地。

**链路 B：设备上报 → 档案落盘**

```
BLE 帧 → HsSessionImpl → HsControl.Listener.onSnapshotChanged
  → HsDevicePortAdapter 投影为 DeviceSnapshot（含 facts/state）
  → DeviceSession 转发（当前代）
  → ObserveDeviceRoster 组 LIVE record → repository.update
  → repository 内存发布 + 合并写盘（device-store 串行）
  → files/devices/roster.json（原子替换）
```

切换设备：`DeviceSession.open(新目标)` 是唯一入口——先 `port.disconnect()`，再对**旧设备身份**发一次 `DISCONNECTED` 快照（台账据此把旧设备归档并清空实时值），最后 `connect(新目标)`；被取代的进行中尝试以 `REJECTED` 结算，不会静默丢弃。页面下拉里选中另一台走的就是这条路径。

掉线：`onConnectionChanged(DISCONNECTED)` → 快照 `DISCONNECTED` → 台账该条转 `ARCHIVED`（`lastSeenMillis = now`）并**清空实时值**（`DeviceFacts.unknown()`、空 `DeviceState`），只留名称/地址/型号 → 落盘。

**链路 C：冷启动**

```
AppContainer → DeviceGraph（repository 先 load 盘）
  → DeviceRosterViewModel.start() → ObserveDeviceRoster.current()
  → 首页直接显示台账（全部 ARCHIVED，无活动会话）
```

## 6. 持久化

- 路径：`filesDir/devices/roster.json`（不进 `noBackup`：设备台账属于可重建的用户配置，随备份迁移合理）。
- 格式（`data/device/json/DeviceRosterJson`）：

```json
{"version":1,"active":"HS01@AA:BB:CC:DD:EE:FF",
 "devices":[{"id":{"modelId":"HS01","bleAddress":"AA:BB:CC:DD:EE:FF"},
   "alias":"我的耳机",
   "identity":{"bleName":"HS01","classicAddress":"11:22:33:44:55:66","classicName":"HS01"},
   "lastSeen":1763000000000,
   "facts":{"battery":{"left":80,"right":75,"case":60,"charging":false},
            "firmware":"1.0.3","wear":"NOT_WORN","earColor":2},
   "state":{"EQ_MODE":{"kind":"CHOICE","optionId":"POP"}}}]}
```

- 损坏处理：解析失败 → 返回 `StorageGuard` 式显式失败，仓库以空台账启动并**保留**坏文件副本（`roster.json.bad`），由 UI 发一次 `STORE_FAILED` 事件；不静默吞。
- 合并写入：`update()` 用 `AtomicReference` 记最后状态，单写者循环取走写盘（一次 fsync），避免快照高频写盘。
- 单源：`JsonValues` 上移到 `data/json`，AI 的编解码改 import，删旧文件。

## 7. UI 契约

`DeviceRosterUiState`（单一、不可变）：

```java
record DeviceRosterUiState(List<Row> devices, boolean scanning, int emptyTextRes, String footer)
record Row(String key, String title, String subtitle, int statusTextRes,
           String left, String right, String caseText, String lastSeenText,
           boolean active, boolean live, boolean actionable)
```

- 渲染规则：`LIVE` 行显示实时电量/固件与连接态；`ARCHIVED` 行只显示"最后同步 x 分钟前"，电量/固件不显示（断开即清空），**不显示连接态**（那是推断）。
- 事件：`OneShotLiveData<DeviceRosterEvent>`（`OpenFailed(DeviceFailure)` / `OpenSucceeded(DeviceId)` / `StoreFailed()` / `DeviceMissing()`），与 UiState 分离。
- 交互：点行 = 打开该设备，成功后宿主进该设备的设置页（`DeviceSettingsActivity(MUSIC)`）；长按行 = 菜单（重命名 / 忘记）；页头 + = 扫描页。
- 行为分支必须实现：空台账、扫描中、连接中、连接失败、掉线、切换设备、重命名、忘记设备（含删活动设备）、权限拒绝、蓝牙关闭、档案损坏。

页面：`DeviceRosterPage`（页头 + 列表 + 空态）替换现有"单设备摘要页"；`DeviceSettingsActivity` 保持（按活动设备 + 分组）；扫描页选中即 `OpenDevice`。

## 8. 错误分类

`DeviceFailure` 增补三枚并保持"只分类"：

- `STORE_FAILED`：台账/档案读写失败（含 JSON 损坏）。
- `DEVICE_NOT_FOUND`：打开/删除一个台账里不存在的设备。
- `INCOMPLETE_IDENTITY`：扫描结果没有经典地址，双模链路的第一步就缺参数。

其余沿用既有 12 枚；映射单点仍在 `HsValueMapper`（biz → app），上面三枚只由 `domain` 产生，不下发给 biz。

## 9. 删除清单

| 对象 | 处理 | 理由 |
|------|------|------|
| `domain/device/model/DeviceIntake.java` | 删 | 假开关（D15） |
| `ui/OneShotLiveData.java` | 删，统一 lib_common | R6 第二份基线实现（D16） |
| `domain/device/port/DevicePort.java` | 改名 `DeviceLinkPort`，`connect` 收为 `DeviceTarget` | 语义收窄，去掉 5 参数散装身份 |
| `ui/device/home/{DeviceHomeUiMapper,DeviceHomeUiState,DeviceHomePage,DeviceSummaryCard,DeviceCapabilityListView,DeviceEmptyState,DeviceHomeViewModel}` | 由 `ui/device/roster/**` 替换，旧文件删 | 单设备摘要 → 多设备台账 |
| `data/ai/json/JsonValues.java` | 移到 `data/json/JsonValues.java` | 单源（D17） |
| 空目录 `model/`、`view/`、`viewmodel/`、`domain/device/scan/`、`ui/device/settings/page/` | 删 | 残留 |
| `app/doc/**` 旧架构/旧设计/草稿/`.bak` | 删 | 与当前契约冲突（v5 已 10-31 过期，引用已删门禁） |
| `docs/` 下被本轮契约取代的旧审计/旧设计（`00_project_audit.md`、`app_architecture_review_and_design.md`、`ai-architecture-refactor.md`、`audit/**`、`device/**`、`34_verification_report.md`、`app_refactor_verification_report.md`、`lib_ai_audit.md`、`lib_ai_verification_report.md`、`device_logcat_review.md`） | 删 | 描述的是已删除的旧结构 |
| `checkAiLayerImports` 中 `data/ai/gateway` 扫描项；`providerDependencyModules` 中 `lib_bluetooth` | 改门禁 | 不存在的目录/不存在的依赖名 = 假门禁 |

## 10. 门禁增补（`app/build.gradle`）

1. `checkAppDependencyBoundary`：加 `platformBaselinePackages = ['com.yovo.lib_common.event']` 按包名放行（不是文件名白名单），落实 R6；删 `lib_bluetooth`。
2. 新增 `checkNoBaselineCopies`：禁止 app 内出现与 `lib_common` 同名的基线类（按"类名 + 方法签名"表，单源：`OneShotLiveData`），防止再抄第二份。
3. `checkAppLayering` 增补：`domain/**` 禁 `org.json`；`ui/**` 禁 `java.io.File`/`FileOutputStream`/`Files`（设备台账落盘只能在 data）。
4. `checkAiLayerImports` 删 `data/ai/gateway` 项；新增 `data/device/json` 只允许被 `data/device/**` import。

## 11. 不做什么

- 不做 N 路并行 BLE 会话、不建第二条蓝牙栈（biz 单链路；做了就是假实现）。
- 不用 `0`/`--`/默认文案冒充非活动设备的实时值。
- 不合并持久化/领域/展示模型为一个结构。
- 不为 `biz_appupdate` 补接入（用户明确暂不处理；写入下一刀）。
- 不动 `lib_*`、`yotheme`、`lib_ai` 内部实现。
- 不在 `ui` 里做台账合并、排序、去重（属 `domain`）。

## 12. 影响面

- `wiring/DeviceGraph`：重构为「repository + session + 6 用例 + 3 个 ViewModel 工厂」。
- `wiring/AppExecutors`：新增 `device-store` 串行队列。
- `ui/shell/ShellScreen`：设备页签换成 `DeviceRosterPage`。
- `biz_ggec_hs`：**零改动**（多设备是 APP 侧台账，不需要 SDK 支持并行会话）。
- `biz_appupdate`：零改动。

## 13. 分阶段

| 阶段 | 范围（必须整理到干净） | 验收 |
|------|------------------------|------|
| **本刀** | 设备域全链路（model/port/session/roster/usecase/repository/ui/scan/settings/wiring）+ 门禁 + 旧文档/旧代码/空目录/重复原语清理 | 编译、9+4 门禁、单测、真机安装与截图/Logcat |
| 下一刀（另声明） | AI 域审查问题闭合（`ui/ai` 98 文件 + `domain/ai` 93 文件的越级/职责混杂/多路 LiveData） | 另出范围声明与验收 |
| 再下一刀 | `biz_appupdate` 接入壳（当前 0 引用） | 另出范围声明与验收 |

本刀结束时旧路径必须已删（`DeviceIntake`、单设备首页、`app/ui/OneShotLiveData`、`app/doc` 旧文档），不得双轨并存。
