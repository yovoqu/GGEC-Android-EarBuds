# 架构复审报告（2026-11 重构 · 四波只读复审，终审 CLEAN）

> 方法：YoAgentDocs `task.architecture-review` + `playbook/architecture-review` 六段式 + `checklists/architecture-review`；权威契约 `docs/architecture/2026-11-refactor/design.md`。
> 角色：`reviewer`，**只审不改**（本文件是唯一被写文件；未改任何 `.java` / `.gradle` / `design.md` / `audit.md` / `notes-*`，无 git commit）。
> 证据等级沿用 `rules/common/architecture.md`：`已验证`（代码位置 + 可复现运行观察）/ `已读到`（只有代码位置）/ `推断` / `假设`。本仓**无真机**，设备侧 BLE/协议节点最高为 `已读到`；升级到 `已验证` 的只有构建、单测与边界门禁（实际执行并有结果）。
> **重建说明**：本文件在第三波复审中被整份重建（原文件在做行号校正时被一次非 UTF-8 回写破坏）。第一、二波章节按当时结论与证据条目如实重述，未新增、未撤回任何结论。
> 每一波都重读当前树并重跑命令，不沿用上一波结论。

## 波次索引与最终结论

| 范围 | 第一波 | 第二波 | 第三波 | **第四波（终审）** |
|------|--------|--------|--------|--------------------|
| R1 `lib_bluetooth` | CLEAN | CLEAN | CLEAN | **CLEAN** |
| R2 `biz_ggec_hs` | HAS_ISSUES（P2×3） | HAS_ISSUES（P1×1 + P2×1） | HAS_ISSUES（P1-B2） | **CLEAN** |
| R3 `app` | HAS_ISSUES（P1×2 + P2×2） | HAS_ISSUES（P1×1） | HAS_ISSUES（P1-B1、P1-B3） | **CLEAN** |

第三波止于 3 条 P1；第四波（终审）三范围**全部 CLEAN**，35 条能力的 kind 与控件真实性逐条通过 ⇒ **本轮范围可收口**（末行）。范围外事项见 §R4。

---

# 第一波只读复审（重构落地第一轮 · 历史）

范围：`lib_bluetooth`、`biz_ggec_hs`、`app`。方法同上，实跑见 §R1.4。

## R1.1 `lib_bluetooth` — CLEAN

覆盖：根包 `YoBluetoothSDK`；`api/`、`model/**`（契约）；`internal/**`（实现，含装配根 `internal/BluetoothSdkCore`）；`provider/lib_common/{log/YoBtLogger,permission/BluetoothPermissionProvider}`；`build.gradle` 三条门禁。

设计后链路（`已读到`）：`YoBluetoothSDK.ble()/classic()` → `internal/BluetoothSdkCore.java:28,34` → `permissionChecker()`（`:39-42`，全模块唯一 `new YoBluetoothPermissionChecker(`）→ 客户端实现构造注入 → `has()` → `provider/lib_common/permission/BluetoothPermissionProvider.java:25-39` → `YoPermission.isGranted`（副作用终点＝系统权限查询，未实机）。日志：`internal/**` 28 处 → `YoBtLogger` → `YoLog`。

逐项结论：`import com.yovo.lib_common` 全模块**仅 5 行**且全在 `provider/lib_common/**`（`log/YoBtLogger.java:8-9`、`permission/BluetoothPermissionProvider.java:9-11`）；`api/`+`model/` 对 `internal|provider` 0 命中；`BluetoothProviderInstaller`/`setPort`/`installAll`/`UNINSTALLED` 全仓 0 命中；`provider/**` 无静态可写字段；根包仅一个文件；`YoBleScanHub.current`（`internal/ble/scan/YoBleScanHub.java:105-106`）满足 §1.2 R3a 三条（`private`＋internal 包、类文档写明锁/代次/终态/分发/迁移归属、不承担依赖装配）；三条门禁按目录判定、无类名文件名白名单（唯一以名字出现的是 `ROOT_PACKAGE_FACADE = 'YoBluetoothSDK.java'`，语义是「根包文件集合恰好等于这一个门面」的唯一性约束，不是豁免）。

结论：**CLEAN**（P0/P1/P2 皆无）。

## R1.2 `biz_ggec_hs` — HAS_ISSUES（第一波）

问题（均已由 lead 在第二轮闭合）：

- **P2** `biz_ggec_hs/consumer-rules.pro:2` — `-keep class com.yovo.biz_ggec_hs.YoHsBroadcast`（类已删，属 §9.3 零残留标的名，`.pro` 在残留判据内）；`:4`/`:6` — `-keep …model.**`（`model/` 包已按 design §4 消失）。正确层：该文件删三行。
- **P2** `biz_ggec_hs/.../internal/protocol/batterycmd/CaseChargingStatusCommand.java:131-136` — 注释掉的旧 `getTimeoutMs()`（§9.4 注释冻结）。正确层：删除注释块。
- **P2** design §4 与 §2.1/§8 冲突：§4 把 `HsNearbyDevices/HsNearbyScanSession/HsNearbyScanCallbacks/HsNearbyScanLedger` 列在 `provider/lib_bluetooth/`，而实现放 `internal/device/`（`HsNearbyDevices` 必须 import `internal.broadcast.*`，按 §4 落位会被本模块门禁 `build.gradle:100-111` 判失败）。正确层：修订 design §4（docs 属 lead 写作用域）。

已通过项：`api/**` 28 文件零实现零 `internal`/`android.bluetooth` import；三层单向（`provider/lib_bluetooth/**` 的 `com.yovo.*` import 只有 `api.*`+`internal.contract.*`+`lib_bluetooth.*`；非 provider 无 `lib_bluetooth`；`internal/device` 无 `provider`/`lib_bluetooth`；import `provider.*` 的 main 文件只有 `internal/graph/HsGraph.java:12-13`）；`model/`、`assembly/`、`internal/session/`、根包 `YoHsBroadcast` 已消失；`HsCapabilityRouter` 单点映射；机型门控 + 入口 `UNSUPPORTED_CAPABILITY`（`HsSessionImpl.java:99,175-176,193`）；门禁结构性化。

## R1.3 `app` — HAS_ISSUES（第一波）

问题：

- **P1** `app/.../provider/lib_common/log/YoLogProvider.java:21,27`（当时）— `private static volatile boolean installed` + `public static void install(Context)`：§1.2 R3 明文禁止形态，且 `installed` 是重复幂等状态（`lib_common/log/YoLogBootstrap.java:18,39-40` 自带「已初始化直接返回」）。
- **P1** `app/.../provider/lib_ai/LibAiProvider.java:17,22`（当时）— 同型；`YoAiDiagnosticsInstaller.java:15,20-28` 自身已是幂等双检。
- **P2** `app/.../ui/device/DeviceCapabilityIds.java:17` + `ui/device/settings/SettingsSectionFactory.java:117` + `DeviceSettingRow.java:88` + `section/ActionCardView.java:79,107-110` — `REBOOT` 在 biz `HsCapability` 里不存在，UI 分支不可达。
- **P2** `app/.../provider/lib_common/refresh_rate/WindowProvider.java:18` — `public static void installHighRefresh(Application)` 字面违反 R3 的 `install*` 条款（无状态转发）。

已通过项：非 `provider/**` 的 `import com.yovo.*` 仅 5 行且全为 `com.yovo.lib_common.event.OneShotLiveData`（§1.2 R6 基线）；`provider/**` 只被 `wiring/**` 引用；机型/变种/能力硬编码 0（`HS01|HS02|family` grep 0）；旧 `Capability/CapabilitySet/DeviceModel/DeviceVariant/DeviceProfile/ProfileParams/DeviceControls/DeviceSwitch/DeviceControlPolicy/DeviceProfileCatalog(Impl)/DeviceControlsPort/EmptyDeviceControlsPort/IdleDevicePort/LoadDeviceProfiles/EditDeviceControls/provider/lib_bluetooth/**` 全部删除；UI 完全描述符驱动（`DeviceHomeUiMapper.row` 按 kind 穷尽 switch、`SettingsSectionFactory` 按 kind+id 路由）；`ScanPermissionPort` 在用；9 条门禁全绿。

## R1.4 第一波实跑 / 范围外

```
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :biz_ggec_hs:testDebugUnitTest ^
  :lib_bluetooth:testDebugUnitTest :lib_ai:testDebugUnitTest :biz_appupdate:testDebugUnitTest ^
  :yotheme:testDebugUnitTest :biz_ggec_hs:check :lib_bluetooth:check --offline --console=plain
→ BUILD SUCCESSFUL in 1m 6s；再以 --rerun-tasks 强跑 → BUILD SUCCESSFUL in 2m 6s
```
app 502 / biz 118 / lib_bluetooth 176 / lib_ai 411 / biz_appupdate 58 / yotheme 1，failures=errors=skipped=0。
范围外：`lib_common/src/test/java/com/yovo/lib_common/system/file/internal/core/FileCoreAccessGateTest.java:29`（`无法访问FingerprintManager`，基线可复现，见 §R4）。

---

# 第二波只读复审（第二轮落地后 · 历史）

## R2.1 lead 处置核对（8 项）

1. `YoLogProvider` 去 `installed`、`bootstrap()` 直调 `YoLogBootstrap.initOnce` — **已闭合**。
2. `LibAiProvider` 去 `installed`、`bootstrap()` 直调 `YoAiRuntime.install`（`YoAiRuntime.java:11-25` 声明幂等）— **已闭合**。
3. `consumer-rules.pro` 收敛为 3 行 — **已闭合**。
4. `CaseChargingStatusCommand` 注释冻结删除 — **已闭合**。
5. REBOOT 走补契约（`HsCapability` 34 项含 LDAC/VOLUME_ADAPTIVE/RESET_CONTROL/REBOOT、`HsAction.ResetControl`、HS01=28/HS02=34、`sessionBootstrapQueries` 含 `LDACQueryCommand`、`read` 未上报返回 `NOT_READY`）— **已闭合**，但「五处 switch 穷尽无 default」当时不准确（`value()` 仍留 `default -> null`）。
6. 新增 §1.2 **R3b 启动步骤允许形态**（只被装配根调用、不得自建第二份幂等状态、不解析依赖）：`WindowProvider` 合规、两个 provider 已改 — **已闭合**。
7. design §4 修订，四个 `HsNearby*` 归 `internal/device/`，provider 只留 `YoBleCommandLink/HsNearbyScanner/HsRawAdverts/HsScanFailures` — **已闭合**（文档与实现一致）。
8. `lib_ai/ResponsesTest` 吞 IOException 划为范围外下一阶段任务 — **一致**（该改动只影响清理、未削弱断言：断言行与 HEAD 一致）。

## R2.2 第二波问题（均由 lead 在第三轮闭合）

- **P1-1（biz）** `internal/device/HsCapabilityRouter.java`（当时 `value():212-230`）— 缺 `BEGINNER_MODE` 分支，而域层已有 `DeviceDomainStore.getBeginnerMode():188-190`、事件已发（`DeviceEventPipeline:186` → `HsSessionImpl:398-399`）⇒ 状态永不入快照、写入入口不可达。
- **P1-2（app）** `ui/device/home/DeviceCapabilityListView.java:114-122` 与 `ui/device/settings/section/SettingsRowsView.java:118-126`（当时）— `addToggle` 在 `checked.isEmpty()` 时只渲染「未上报」文本、不构建开关，而 Toggle 的唯一写入口挂在该开关上 ⇒ `VOLUME_ADAPTIVE/WEAR_DETECT/VOICE_WAKEUP/FALL_ALERT/VOLUME_REMIND/INTERPRET` 六项永远发不出写入（旧 `git show HEAD:…/settings/page/FunctionPageRows.java:26,28,30` 至少渲染了开关）。
- **P2-1（biz）** 同一 `value()` 的 `default -> null` 与「五处穷尽」自述不符。

## R2.3 第二波功能对等审计

12 个旧入口（`DeviceSwitch` 10 + `SettingId.RESET_CONTROL` + `YoHsClient.reboot()`）中 **可达 5 / 不可达 7**：可达 LDAC、GAME_MODE、DUAL_CONNECTION、RESET_CONTROL、REBOOT；不可达 VOLUME_ADAPTIVE、WEAR_DETECT、VOICE_WAKEUP、FALL_ALERT、VOLUME_REMIND、BEGINNER_MODE、INTERPRET。

## R2.4 第二波实跑

指定命令 BUILD SUCCESSFUL in 31s；`--rerun-tasks` 强跑 BUILD SUCCESSFUL in 2m 13s；app 507 / biz 121 / lib_bluetooth 176 / lib_ai 411 / biz_appupdate 58 / yotheme 1，全 0 失败、0 跳过。

---

# 第三波只读复审（收口波 · 2026-11-12 第三轮落地后）

> 变更面（mtime 11:41–11:52）：`biz_ggec_hs/{api/HsControlKind,internal/device/{HsCatalogImpl,HsCapabilityRouter,HsSessionImpl},internal/protocol/{HsProtocolEngine,inbound/{DeviceDomainStore,DeviceEventPipeline}}}`、`app/{ui/device/{TogglePresentation,LevelPresentation,DeviceControlOutcome,ReadOnlyControlText},ui/device/{home,settings}/**,provider/biz_ggec_hs/HsValueMapper,domain/device/model/DeviceCapabilityKind}`、`docs/.../design.md`。`lib_bluetooth/**` 与三份 `build.gradle` 本轮未改（mtime 仍为 10:31–10:46）。

## 第三波结论

| 范围 | 结论 | 阻塞项 |
|------|------|--------|
| R1 `lib_bluetooth` | **CLEAN** | 无 |
| R2 `biz_ggec_hs` | **HAS_ISSUES** | P1-B2（INFO 四能力的「值」通路未成立） |
| R3 `app` | **HAS_ISSUES** | P1-B1（CURVE 编辑器打不开）、P1-B3（双连接已配对设备列表无 UI 承载） |

第二波 3 项（P1-1/P1-2/P2-1）已全部闭合。

## 3.1 lead 修复清单逐条核对（1–4）

| # | lead 声称 | 我的核对（当前树） | 结论 |
|---|-----------|--------------------|------|
| 1 | `value()` 补 `BEGINNER_MODE`；新增独立域字段 `beginnerModeEnabled`，与 `99ECD2` 配置路径互不替代 | `HsCapabilityRouter.value():246-248` = `case BEGINNER_MODE -> domain.getBeginnerModeEnabled()`；域侧 `DeviceDomainStore.java:38,136,200-202,366-371`（字段 + getter + `setBeginnerModeEnabled`，`:371` 单发 `onBeginnerModeEnabledChanged`），与配置路径 `setBeginnerMode(int,int)`（`:356`）**并存、互不覆盖**；链路 `HsProtocolEngine.java:215`（`99ECCC/99ECCD` 应答 → `applyBeginnerModeEnabled(command.isEnter())`）→ `DeviceEventPipeline.java:143` → 域 → `value()` → 快照 → app；未应答前 `value()==null` ⇒ `read()` 返回 `NOT_READY`（`HsSessionImpl:158-161`）。测试 `HsSessionImplTest.java:518 beginnerModeToggleReflectsCommandAck` 存在并通过 | **已闭合** |
| 2 | `value()` 穷尽 34 项，五处 switch 全无 `default` | `HsCapabilityRouter` 全文 `default ->` **0 命中**；`value():232-257` 明列 5 个有状态源（EQ_MODE/GAME_MODE/DUAL_CONNECTION/LDAC/BEGINNER_MODE）+ 29 个显式 `-> null` = 34；`supportsWrite:98`、`supportsAction:111`、`write:129`、`capabilityOf:157`、`commands:183` 五处亦无 `default`。测试 `HsCapabilityRouterTest.java:263 valueIsDefinedForEveryCapability` 存在并通过 | **已闭合** |
| 3 | `TogglePresentation` 纯规则；两处 `addToggle` 与 `DualConnectionCard` 一律建开关；失败经 `DeviceControlOutcome` 回退 | `TogglePresentation.java:20-30`：`unknown = reported.isEmpty()`、`position = reported.or(pending).orElse(false)`、`enabled = interactive`（**与值是否已知无关**）；`DeviceCapabilityListView.addToggle:143-167` 与 `SettingsRowsView.addToggle:148-166` 无条件 `ListSuffixSpec.switch_(...)`（只分可写/只读两种），值未知时标题下挂「未上报」文案；`pending` 只活在视图层（`SettingsRowsView:48,157`、`DeviceCapabilityListView:154`）；`DeviceControlOutcome`（`:10-18`）+ `SettingsRowsView.onControlOutcome:89-95` + `DeviceSettingsScreen.onControlOutcome:112-114`：成功保留用户选择、失败丢弃回「未上报」。测试 `TogglePresentationTest`（6 例）、`LevelPresentationTest` 存在并通过 | **已闭合** |
| 4 | 新增 `INFO`/`GATE`，共八名；四种 kind 不变量；判定源统一 `supportsWrite/supportsAction` | `api/HsControlKind.java:15` 八名；`HsCatalogImpl.describe():244-317` 归类：INFO=CONNECTION/BATTERY/DEVICE_INFO/SERVICE_STATUS、GATE=AI_ASSISTANT/OTA/OFFLINE_LOG/CLASSIC_BT、TOGGLE=10、CHOICE=6、CURVE=1、ACTION=8、LIST=1、LEVEL=0（我按源码逐条计数 = 34，与你的口径一致）；`validate():188-242` 四条不变量（`validateLevel/validateDeclarationOnly/validateAction/validateNoRange`），判定源 `HsCapabilityRouter.supportsWrite:98`/`supportsAction:111`；测试 `HsCatalogImplTest.java:148 everyCapabilitySatisfiesItsKindInvariant` 与不变量一致。**归类本身成立**；但「INFO 的**值**能不能拿到」不成立 → P1-B2 | **规则与归类已闭合；INFO 值通路未闭合（P1-B2）** |
| — | app 侧八名同步、INFO 只读行、GATE 不出行、LEVEL 走 `LevelPresentation` | `DeviceCapabilityKind` 8 名；`HsValueMapper.toKind:81-92` 8 分支穷尽；`SettingsSectionFactory.row:72-88` 8 分支（INFO→`CapabilityInfo`、GATE→`Optional.empty()`）；`DeviceHomeUiMapper.row:85-97` 8 分支（INFO→`Row.Info`、GATE→empty）；`ReadOnlyControlText:27-40` | **已闭合**（呈现层） |

## 3.2 A. 功能对等审计表（12 个旧入口）

口径：`旧入口 → 新能力 id → 描述符 → 状态来源 → 写入/动作命令 → UI 控件 → 可达`。可达＝用户能从**可见可操作**控件走到对应写入/动作。

| # | 旧入口 | 新能力 id | 描述符 | 状态来源 | 命令 | UI 控件 | 可达 |
|---|--------|-----------|--------|----------|------|---------|------|
| 1 | `DeviceSwitch.LDAC` | `LDAC` | MUSIC/TOGGLE | `getLdacEnabled()` ← `LDACQueryCommand`（自愈查询 `sessionBootstrapQueries:298`） | `LDACCommand`（`write:135`） | 通用开关（home+settings，恒建） | **是** |
| 2 | `DeviceSwitch.VOLUME_ADAPTIVE` | `VOLUME_ADAPTIVE` | MUSIC/TOGGLE | 无（协议只有 Set） | `VolumeAdaptiveCommand`（`:136`） | 通用开关（未知仍可写） | **是** |
| 3 | `DeviceSwitch.WEAR_DETECT` | `WEAR_DETECT` | CONTROL/TOGGLE | 无 | `WearDetectionCommand`（`:138`） | 同上 | **是** |
| 4 | `DeviceSwitch.VOICE_WAKEUP` | `VOICE_WAKEUP` | CONTROL/TOGGLE | 无 | `VoiceWakeupCommand`（`:139`） | 同上 | **是** |
| 5 | `DeviceSwitch.GAME_MODE` | `GAME_MODE` | CONTROL/TOGGLE | `getGameMode()` | `GameModeCommand`（`:137`） | 同上 | **是** |
| 6 | `DeviceSwitch.FALL_ALERT` | `FALL_ALERT` | FUNCTION/TOGGLE | 无 | `FallAlertCommand`（`:140`） | 同上 | **是** |
| 7 | `DeviceSwitch.VOLUME_REMIND` | `VOLUME_REMIND` | CONTROL/TOGGLE | 无 | `VolumeRemindCommand`（`:141`） | 同上 | **是** |
| 8 | `DeviceSwitch.BEGINNER_MODE` | `BEGINNER_MODE` | FUNCTION/TOGGLE | `getBeginnerModeEnabled()` ← `99ECCC/99ECCD` 应答 | `BeginnerModeCommand`（`:142`） | 同上 | **是** |
| 9 | `DeviceSwitch.INTERPRET` | `INTERPRET` | FUNCTION/TOGGLE | 无 | `SimultaneousInterpretationCommand`（`:143`） | 同上 | **是** |
| 10 | `DeviceSwitch.DUAL_CONNECTION` | `DUAL_CONNECTION` | DUAL_CONNECTION/TOGGLE | `dual.dualEnabled()` ← `GetDualConnectionPageCommand` | `SetDualConnectionCommand`（`:144`） | 通用开关（未知仍可写） | **是**（仅「开关」面；**同一能力的已配对设备列表侧不可达 → P1-B3**） |
| 11 | `SettingId.RESET_CONTROL` | `RESET_CONTROL` | CONTROL/ACTION | 不适用 | `ResetControlCommand`（`capabilityOf:162`、`commands:192`） | `ActionCardView.bindResetControl` | **是** |
| 12 | `YoHsClient.reboot()` | `REBOOT` | ABOUT/ACTION | 不适用 | `DeviceRebootCommand`（`:161,191`） | `ActionCardView.bindReboot` | **是** |

**A 结论：12/12 可达**（第二波 5/12 → 本轮 12/12）。#2/#3/#4/#6/#7/#9 由 `TogglePresentation` 修复，#8 由 biz `value()` 修复。
A 只覆盖「单一控件入口」；#10 的**列表侧**与编辑器家族属于 B 的范围。

## 3.3 B. 控件真实性核查（34 条能力逐条）

判定：`声明的 kind → app 渲染出的控件 → 该控件是否有真实写入/动作（只读类则是否有真实**值**）通路`。

| kind | 能力（数量） | app 渲染 | 通路核查 | 结论 |
|------|--------------|----------|----------|------|
| TOGGLE | DUAL_CONNECTION、LDAC、VOLUME_ADAPTIVE、WEAR_DETECT、VOICE_WAKEUP、GAME_MODE、FALL_ALERT、VOLUME_REMIND、BEGINNER_MODE、INTERPRET（10） | 恒建开关（`TogglePresentation`） | 全部 `supportsWrite=true`（`HsCapabilityRouter:100-103`），写入命令齐备；失败回退「未上报」 | **10/10 真实** |
| CHOICE | SOUND_QUALITY、EQ_MODE、TOUCH_LONG、TOUCH_SHORT、TOUCH_DOUBLE、TOUCH_TAP（6） | 文本+箭头，点击出选项对话框（`SettingsRowsView.addChoice:172`、`DeviceCapabilityListView.addChoice`） | `write()` 六分支齐备（`:131,133,145-148`）；无值也可点 | **6/6 真实** |
| ACTION | RESET_CONTROL、REBOOT、FIND_EAR、RENAME、CLEAR_PAIRING、RECORD_LOCAL、RECORD_STREAM、RECORD_TRANSFER（8） | `ActionCardView` 八个 `bind*`（`:77-87`） | `actionKindOf` 八分支与目录一一对应（`SettingsSectionFactory:122-134`）；`commands()` 均可达 | **8/8 真实** |
| LIST | RECORD_LIST（1） | `RecordFilesCard`（`SettingsRowsView:297-302`） | 数据 `RecordFilesChanged` → `DeviceSnapshot.recordFiles`；拉取 `ReadRecordList`（`DeviceSettingsViewModel:167-169`） | **1/1 真实** |
| CURVE | CUSTOM_EQ（1） | `DeviceSettingRow.Curve` → `addCurve` | **控件存在但永远打不开**：`SettingsRowsView.addCurve:185-197` 仍以 `points().isPresent()` 作为可点前提；而 `router.value(CUSTOM_EQ)` 恒 `null`（`:250-256`）⇒ `curveOf` 恒空 ⇒ 行退化成灰字「未上报」、无箭头 ⇒ `CustomEqSheet`（唯一挂载点 `openCustomEq:253`）不可达 ⇒ `CustomEQRealtimeCommand` 发不出 | **0/1 真实（P1-B1）** |
| LEVEL | 无（0） | `SliderCardView` + `LevelPresentation` 已就绪 | 无能力声明 LEVEL；`validateLevel` 要求带 range。属「为后续能力预留」，不是「声明了控件却失败」 | **n/a（无声明）** |
| INFO | CONNECTION、BATTERY、DEVICE_INFO、SERVICE_STATUS（4） | `CapabilityInfo` 只读行 + `ReadOnlyControlText:27-40` | **值通路不成立**：`router.value()` 对这四项恒 `null`（`:250-256`）；真实值在快照别的字段（`battery`/`connection`/`deviceInfo`），`HsDevicePortAdapter.toDeviceState:256-266` 只从 `snapshot.values()` 投影 ⇒ 行恒显示「未上报」，与同页 `AboutInfoRows` 与首页摘要卡显示的真实电量/连接态/固件**矛盾**；`SERVICE_STATUS` 更严重：域里有数据（`DeviceDomainStore:212 getServiceStatus`）、事件已发（`HsSessionImpl:413-414`），但 app 明确丢弃（`HsDevicePortAdapter:362`）且无其他展示 ⇒ **服务状态在 UI 上完全不可见** | **0/4 真实（P1-B2）** |
| GATE | AI_ASSISTANT、OTA、OFFLINE_LOG、CLASSIC_BT（4） | 不出行（`SettingsSectionFactory:86`、`DeviceHomeUiMapper:94`） | 门控语义（`DeviceProfileView.supports(id)`）；旧 UI 也没有这四项入口（`git show HEAD:…/settings/page/AboutPageRows.java` 只有机型/固件/MAC/颜色/名称/CLEAR_PAIRING/能力数），非功能回退 | **4/4 合规** |
| 额外 | `ListKind.DUAL_CONNECTION` + `DualConnectionCard` | 声明了列表组件，真实目录不产出 | `DUAL_CONNECTION` 的 kind 已是 TOGGLE ⇒ `listRow()` 永不为其调用 ⇒ `SettingsSectionFactory.listKindOf:137-143` 的 DUAL 分支与 `SettingsRowsView:288-296` 的 `DualConnectionCard` **在真实目录下不可达**；`DeviceSettingsViewModel:162-164` 仍在为 DUAL 组发 `ReadPairedDevices`（拉数据不展示）；`DualConnectionView.devices` 与 Forget/SetPriority/Connect/Disconnect 四个动作没有 UI 入口（旧 `DualPageRows:31` 有该列表） | **不可达（P1-B3）** |

**B 结论**：34 条能力中 **29 条**（10+6+8+1+4）控件与通路一致，**5 条不一致**（CURVE 1、INFO 4），另有 1 处列表组件挂空（DUAL_CONNECTION 列表侧）。

测试盲区（两处不一致都未被覆盖）：`DeviceSettingsUiMapperTest:80-94`、`SettingsSectionFactoryTest:183,202`、`DeviceSettingsViewModelTest:176-177` 用 **fake 的 `DeviceCapabilityKind.LIST`** 描述符覆盖双连接列表，真实目录形状（TOGGLE）从未被试过；CURVE/INFO 没有任何「控件可打开／值的来源接通」的断言。

## 3.4 第三波问题清单

- **P1-B1（app）** `app/src/main/java/com/ggec/earbuds/ui/device/settings/section/SettingsRowsView.java:185-197` — `addCurve` 把「值存在」当作「控件可用」前提；`CUSTOM_EQ` 无状态来源（`HsCapabilityRouter.value():250-256` 返回 `null`）⇒ 行恒灰字、`CustomEqSheet` 与 `CustomEQRealtimeCommand` 不可达。**正确层**：`ui/device` —— 与 Toggle/Level 同一裁定，引入 `CurvePresentation`（控件存在与否与值是否已知无关；无上报值时以「未确认」初始曲线打开，如 10 段全 0，并在标题旁标「未上报」），或由 biz 补 EQ 曲线查询并接进 `value()`。
- **P1-B2（biz 归类 / app 呈现）** `biz_ggec_hs/.../internal/device/HsCatalogImpl.java:246-251,289-290,305-306` 把 CONNECTION/BATTERY/DEVICE_INFO/SERVICE_STATUS 归为 `INFO`，但 `HsCapabilityRouter.value():250-256` 对这四项恒 `null`、`ControlValue` 也没有承载「电量三元组／连接态／设备信息／服务状态字典」的形态 ⇒ `DeviceState` 永远没有这四个键 ⇒ 只读行恒「未上报」，与快照真实值矛盾；`SERVICE_STATUS` 且无替代展示。**正确层**：二选一——(a) 最小修法：把四项归 `GATE`，事实由现有承载者展示（电量/连接态在首页摘要卡、固件/颜色在 `AboutInfoRows`；服务状态需补一个专门组件消费 `ServiceStatusChanged`）；(b) 扩契约：给 `HsControlValue` 增加信息型载荷并让 `value()`/`HsValueMapper` 接通。当前「INFO 行 + 无值源」是既无信息也不诚实的第三种状态。
- **P1-B3（app 工厂 / 契约粒度）** `app/.../ui/device/settings/SettingsSectionFactory.java:137-143`（`listKindOf` 仍映射 `DUAL_CONNECTION`）与 `app/.../ui/device/settings/section/SettingsRowsView.java:288-296`（`DualConnectionCard` 唯一挂载点）— `DUAL_CONNECTION` 已是 `TOGGLE`、`LIST` 只有 `RECORD_LIST`，故 `ListKind.DUAL_CONNECTION` 分支与 `DualConnectionCard` 在真实目录下不可达；`DeviceSettingsViewModel.java:162-164` 仍在发 `ReadPairedDevices`，已配对设备列表与四个管理动作没有 UI 入口（旧 `DualPageRows:31` 有）。**正确层**：契约 + 工厂——① 允许一个能力两种呈现（`SettingsSectionFactory.rows()` 对 `DUAL_CONNECTION` 追加一行 `CapabilityList(ListKind.DUAL_CONNECTION)`），或 ② `HsCapability` 新增独立 `DUAL_CONNECTION_DEVICES`（`LIST`）能力（需同步 `HS01/HS02` 计数与测试）。需 lead 定契约。

## 3.5 常规门禁复跑结果（第三波）

- 分层/依赖：非 `provider/**` 的 `import com.yovo.*` 仍仅 5 行且全为 `com.yovo.lib_common.event.OneShotLiveData`（R6 基线）；`provider/**` 静态可写字段 0；`provider.**` 只被 `wiring/**` 引用；`ui/domain/data` 不 import provider；biz 三层单向与 lib_bluetooth 的 provider 单点接入未变（本轮未改这两处结构）。
- 技术债扫描：三模块 `TODO|FIXME|HACK|@Deprecated|@Ignore|assumeTrue` **0**；注释掉的 Java 代码 **0**；根包/`model/`/`assembly/`/`internal/session/` 仍不存在；§9.3 标识符无真残留。
- 门禁规则：三份 `build.gradle` 本轮未改，规则仍为「目录 + 包名前缀」判定，无类名/文件名白名单。

## 3.6 第三波实跑（`已验证`）

```
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :biz_ggec_hs:testDebugUnitTest ^
  :lib_bluetooth:testDebugUnitTest :lib_ai:testDebugUnitTest :biz_appupdate:testDebugUnitTest ^
  :yotheme:testDebugUnitTest :biz_ggec_hs:check :lib_bluetooth:check --offline --console=plain
→ BUILD SUCCESSFUL in 43s

.\gradlew.bat :app:testDebugUnitTest :biz_ggec_hs:testDebugUnitTest :lib_bluetooth:testDebugUnitTest ^
  :lib_ai:testDebugUnitTest :biz_appupdate:testDebugUnitTest :yotheme:testDebugUnitTest ^
  --rerun-tasks --offline --console=plain
→ BUILD SUCCESSFUL in 1m 43s（强制重跑）
```

| 模块 | suites | tests | failures | errors | skipped |
|------|--------|-------|----------|--------|---------|
| app | 66 | **528** | 0 | 0 | 0 |
| biz_ggec_hs | 19 | **125** | 0 | 0 | 0 |
| lib_bluetooth | 36 | 176 | 0 | 0 | 0 |
| lib_ai | 55 | 411 | 0 | 0 | 0 |
| biz_appupdate | 13 | 58 | 0 | 0 | 0 |
| yotheme | 1 | 1 | 0 | 0 | 0 |
| **合计** | 190 | **1299** | **0** | **0** | **0** |

与 lead 给的聚合一致（1299，全绿）。门禁任务全部实际执行并通过（app 9 条 + biz 1 条 + lib_bluetooth 3 条；`:biz_ggec_hs:check`、`:lib_bluetooth:check` 含 lint）。
**未覆盖**：`src/androidTest` 无真机；设备侧仍为 `已读到`。

## 3.7 第三波最终结论

| 范围 | 结论 |
|------|------|
| R1 `lib_bluetooth` | **CLEAN** |
| R2 `biz_ggec_hs` | **HAS_ISSUES**（P1-B2） |
| R3 `app` | **HAS_ISSUES**（P1-B1、P1-B3） |

**未达收口条件，故不写「本轮范围可收口」。** 收口条件：P1-B1（CURVE 与 Toggle/Level 同裁定）、P1-B2（INFO 四项降为 GATE + 补服务状态展示，或扩契约给值）、P1-B3（DUAL_CONNECTION 列表侧给出入口，二选一定契约）落地后，再开只读复审复核这三处**行为**，并补三类测试：① 真实目录形状下的双连接页行类型；② `CUSTOM_EQ` 在无上报值时仍能打开编辑器并写入；③ INFO 行显示快照真实值而非「未上报」。

---

# 第四波 · 终审（2026-11-12 终轮落地后）

> 依据：`playbook/architecture-review`「每一波重读当前树」。第三波结论不作为依据，逐条重核 + 重新实跑（含 `--rerun-tasks`）。
> 变更面（mtime 12:04–12:14）：`biz_ggec_hs/{api/{HsControlKind,HsCapability},internal/device/{HsCatalogImpl,HsCapabilityRouter},build.gradle,4 个测试}`、`app/{ui/device/{DeviceCapabilityIds,CurveEditorSeed,home/{DeviceHomeUiState,DeviceHomeUiMapper,DeviceCapabilityListView},settings/{DeviceSettingRow,SettingsSectionFactory,DeviceSettingsViewModel,section/{SettingsRowsView,DualConnectionCard}},domain/device/model/DeviceCapabilityKind,provider/biz_ggec_hs/HsValueMapper,9 个测试}`、`docs/.../design.md`。`lib_bluetooth/**`、`app/lib_bluetooth` 相关与 app/biz 之外的模块本轮未改。

## 4.0 终审结论

| 范围 | 结论 |
|------|------|
| R1 `lib_bluetooth` | **CLEAN** |
| R2 `biz_ggec_hs` | **CLEAN** |
| R3 `app` | **CLEAN** |

第三波 3 条阻塞项（P1-B1/P1-B2/P1-B3）全部闭合；35 条能力的 kind 与控件真实性逐条通过；无 TODO/注释冻结/双轨/白名单/门面夹带实现/provider 导出安装器。**本轮范围可收口**（见文末）。

## 4.1 变更集逐条核对（1–5）

| # | lead 声称 | 我的核对（当前树） | 结论 |
|---|-----------|--------------------|------|
| 1 | P1-B2 取倾向 ① 且更进一步：`HsControlKind` 回到 7 名（删 `INFO`），四项降 `GATE` 且不进 `values()`；展示交快照面；app 删 `DeviceCapabilityKind.INFO`/`CapabilityInfo`/`Row.Info`/`ReadOnlyControlText` | `api/HsControlKind.java` = `{TOGGLE, LEVEL, CHOICE, CURVE, ACTION, LIST, GATE}`（7）；`HsCatalogImpl.describe()`：GATE = CONNECTION(`:252-253`)、BATTERY(`:258-259`)、DEVICE_INFO(`:297-298`)、SERVICE_STATUS(`:313-314`)、AI_ASSISTANT(`:311-312`)、OTA(`:319-320`)、OFFLINE_LOG(`:321-322`)、CLASSIC_BT(`:323-324`) 共 8；`validate():201-206` 七 kind 穷尽（`validateGate` 断言「不得可写、不得带值域」，`:226-232`）；`HsCapabilityRouter.value()` 对上述全部走 `-> null` 组（`:252`），`supportsWrite/supportsAction` 亦不含它们；app 侧 `DeviceCapabilityKind` 7 名、`ReadOnlyControlText.java` **已删**、`CapabilityInfo` 0 命中、`DeviceHomeUiState.Row.Info` 0 命中（`DeviceSettingRow.Info` 保留的是「设备事实行」，由 `AboutInfoRows` 使用，与能力 INFO 行无关）；`HsValueMapper.toKind:81-92` 7 分支穷尽、无 default；`SettingsSectionFactory.row:74-83` 与 `DeviceHomeUiMapper.row:82-96` 均 7 分支、`GATE -> Optional.empty()` | **已闭合**（双源风险随 `ControlValue` 不承载而消失；`everyCapabilitySatisfiesItsKindInvariant` + `gateCapabilitiesStayOutOfSnapshotValues``:270` + `gateCapabilitiesProjectAsGateWithoutRange` 三处断言锁住） |
| 2 | P1-B3 取倾向 ②：新增 `DUAL_CONNECTION_DEVICES`（LIST，HS02 门控与 `DUAL_CONNECTION` 一致）；app `listKindOf` 按 id 分派；`DUAL_CONNECTION` 只剩开关 | `api/HsCapability.java` 35 值含 `DUAL_CONNECTION_DEVICES`；`HsCatalogImpl:64-65`（`HS02_EXTRA` 含 DUAL_CONNECTION + DUAL_CONNECTION_DEVICES）、`:256-257` = `LIST`/`DUAL_CONNECTION` 组；计数 **HS01=28 / HS02_EXTRA=7 / HS02=35**；`HsCapabilityRouter` 中该能力：不可写（`:104`）、可动作（`:116` 真）、`write()` 拒绝（`:150`）、`capabilityOf` 把 `ReadPairedDevices/Forget/SetPriority/Connect/Disconnect` 五项全部归它（`:171-175`）、`value()` 恒 null（`:252`）；app `SettingsSectionFactory.listKindOf:134-139` 按 id 分派（`DUAL_CONNECTION_DEVICES → ListKind.DUAL_CONNECTION`、`RECORD_LIST → RECORD_FILES`），`DualConnectionCard` 只挂 `CapabilityList`；`DeviceSettingsViewModel:162-164` 改为 `supports(DeviceCapabilityIds.DUAL_CONNECTION_DEVICES)` 才发 `ReadPairedDevices`；`DualConnectionCard` 头部文档（`:29-35`）明示「只负责列表与设备操作，不含开关」，实现也确实只在设备项上挂 `DeviceAction`（`:133-139` 起） | **已闭合**（同一能力不再两种呈现；`DeviceCapabilityIds.DUAL_CONNECTION` 只作为开关能力的 id 常量与测试真形状锚点，main 无分支） |
| 3 | P1-B1：`CURVE` 行恒定可点；新增 `CurveEditorSeed`（设备值 → 未确认编辑 → 默认曲线，默认不写回 `DeviceState`） | `SettingsRowsView.addCurve:187-200`：`saved = points.isPresent() \|\| curveDrafts.containsKey(id)`，`interactive` 时**无条件** `textAndArrow`（不再以 `points` 为前置）；`openCustomEq:256-268` 用 `CurveEditorSeed.of(row.points(), draft)`；`CurveEditorSeed:23-27` 取 reported → draft → 默认（10 段 × 0 dB），`onSaved` 才 `curveDrafts.put(...)` + `viewModel.write(ControlValue.Curve)`；默认曲线不写回 `DeviceState`（草稿只在视图层） | **已闭合** |
| 4 | 测试补盲区：appcore 真实目录投影测试 5 例 + appui 5 项断言 + 双连接旧用例改真形状 | `HsCapabilityCatalogAdapterRealCatalogTest`（5 例）以 **`com.yovo.biz_ggec_hs.internal.device.HsCatalogImpl`** 驱动 app 投影：机型数量/能力数量/能力 id 集合/displayName/variants 一致（`:38-52`）、逐能力 kind/group/title/options/range 相等（`:57-73`）、GATE 无 range 无 options 且「至少存在一个 GATE 否则测试形同虚设」（`:81-99`）、kind 词汇双向集合相等（`:103-113`）、能力 id 全可解（`:118-121`）；appui：`DeviceHomeUiMapperTest:72 gateCapabilitiesRenderNoRows`、`SettingsSectionFactoryTest:53 gateCapabilitiesRenderNoRows`、`:73 dualConnectionSplitsIntoASwitchRowAndADeviceListRow`、`:110 curveRowExistsWithoutReportedPoints`、`DeviceSettingsViewModelTest:133 curveSaveWritesCurveControlValue`、`CurveEditorSeedTest` 均存在且用例为**真实 id + 真实 kind** | **已闭合**（第四波漏检的根因——fake 形状——已被真实目录投影测试堵住） |
| 5 | `checkBizGgecHsBoundary` 跨模块扫描限定其它模块 `src/main/java`；测试源集允许触达内部实现 | `biz_ggec_hs/build.gradle:53-54` 写明理由（生产边界只约束生产源集）、`:136` `include '**/src/main/java/**/*.java'`；模块内扫描仍含 `src/test`（`:65-66`）；验证：外部模块 **src/main** 对 `com.yovo.biz_ggec_hs.{internal,provider}` 的 import **0 命中**（app / lib_bluetooth / lib_common / lib_ai / biz_appupdate / yotheme 全量 grep），即生产边界未被放宽 | **已闭合**（范围声明式收窄，不是文件白名单；副作用是 app 测试可对真实目录做投影校验，属净收益） |

## 4.2 B. 控件真实性复核（35 条 × 7 种 kind）

判定：`声明的 kind → app 能否渲染出对应控件 → 该控件是否具真实通路（只读类已全部退场，GATE 不出行）`。

| kind | 数量 | 能力 | app 渲染 | 通路 | 结论 |
|------|------|------|----------|------|------|
| TOGGLE | 10 | DUAL_CONNECTION、LDAC、VOLUME_ADAPTIVE、WEAR_DETECT、VOICE_WAKEUP、GAME_MODE、FALL_ALERT、VOLUME_REMIND、BEGINNER_MODE、INTERPRET | 恒建开关（`TogglePresentation`：`enabled=interactive`，值未知只影响文案） | 全部 `supportsWrite=true`（`HsCapabilityRouter:100-108`）、`write()` 十分支齐备、失败经 `DeviceControlOutcome` 回退 | **10/10 通过** |
| CHOICE | 6 | SOUND_QUALITY、EQ_MODE、TOUCH_LONG、TOUCH_SHORT、TOUCH_DOUBLE、TOUCH_TAP | 文本+箭头，点击出选项对话框 | `write()` 六分支（`:131,133,145-148`）；无值也可点 | **6/6 通过** |
| CURVE | 1 | CUSTOM_EQ | 恒定可点的曲线行 + `CustomEqSheet` | `write(CUSTOM_EQ)` → `CustomEQRealtimeCommand`（`:132`）；种子来自设备值/草稿/默认（不写回状态） | **1/1 通过**（第三波 P1-B1 已修） |
| ACTION | 8 | RESET_CONTROL、REBOOT、FIND_EAR、RENAME、CLEAR_PAIRING、RECORD_LOCAL、RECORD_STREAM、RECORD_TRANSFER | `ActionCardView` 八个 `bind*` | `actionKindOf` 八分支（`SettingsSectionFactory:122-134`）+ `commands()` 全可达（`capabilityOf:157` 起） | **8/8 通过** |
| LIST | 2 | DUAL_CONNECTION_DEVICES、RECORD_LIST | `DualConnectionCard` / `RecordFilesCard`（`SettingsRowsView` 的 `CapabilityList` 分支） | `listKindOf` 双分支按 id 分派；数据分别来自 `DualConnectionChanged`/`PairedDevicesChanged` 与 `RecordFilesChanged` → `DeviceSnapshot.{dualConnection,recordFiles}`；读取由进组 `ReadPairedDevices`/`ReadRecordList` 触发（`DeviceSettingsViewModel:162-169`） | **2/2 通过**（第三波 P1-B3 已修） |
| GATE | 8 | CONNECTION、BATTERY、DEVICE_INFO、SERVICE_STATUS、AI_ASSISTANT、OTA、OFFLINE_LOG、CLASSIC_BT | **不出行**：`SettingsSectionFactory.row:83`、`DeviceHomeUiMapper.row:94` 均 `Optional.empty()` | 仍进 `DeviceProfileView.capabilities()`（`HsCapabilityCatalogAdapter:79-84` 不筛 kind）⇒ `supports(id)` 门控语义可用；不进 `values()`（`value()` 恒 null）；不可写（`validateGate`） | **8/8 通过（确实不出行）** |
| LEVEL | 0 | 无 | `SliderCardView` + `LevelPresentation`（未知仍可写、失败回退）就绪 | 无能力声明 LEVEL；`validateLevel` 强制带 range ⇒ 将来新增必须给值域 | **n/a（无声明，非「声明了控件却失败」）** |

**B 结论：35/35 一致**（10+6+1+8+2+8 = 35；LEVEL 0 项无声明）。CURVE 与 LEVEL 的「未知值是否仍可写」：CURVE —— `addCurve` 在 `interactive` 时无条件可点、编辑器以默认曲线为种子，可写；LEVEL —— `LevelPresentation.of(...)` 的 `enabled` 恒为 `interactive`（与值是否已知无关），`LevelPresentationTest` 锁该裁定，但当前无 LEVEL 能力，属未启用路径。
`DeviceControlOutcome` 仍把写入失败回退到「未上报」（`SettingsRowsView.onControlOutcome:89-95`、`DeviceSettingsScreen.onControlOutcome:112-114`），因此不存在「控件假装成功」。

## 4.3 常规门禁与债务复核（终审）

- 依赖/分层：非 `provider/**` 的 `import com.yovo.*` 仍**仅 5 行**且全为 `com.yovo.lib_common.event.OneShotLiveData`（§1.2 R6 基线）；app `provider/**` 静态可写字段 **0**；`provider.**` 只被 `wiring/**` 引用；外部模块 `src/main` 对 biz internal/provider **0**；biz 三层单向与 lib_bluetooth provider 单点接入未变（本轮未改这两处结构）。
- 债务扫描：三模块 `TODO|FIXME|HACK|@Deprecated|@Ignore|assumeTrue` **0**；注释掉的 Java 代码 **0**；`model/`、`assembly/`、`internal/session/`、根包 `YoHsBroadcast` 仍不存在；`ReadOnlyControlText`/`CapabilityInfo`/`Row.Info` 已随 INFO 删除，无死代码。
- 门禁：`checkBizGgecHsBoundary` 仅收窄跨模块扫描范围（见 4.1 #5），规则本身仍为「目录 + 包名前缀」，无类名/文件名白名单；app 9 条与 lib_bluetooth 3 条未改。

## 4.4 终审实跑（`已验证`）

```
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :biz_ggec_hs:testDebugUnitTest ^
  :lib_bluetooth:testDebugUnitTest :lib_ai:testDebugUnitTest :biz_appupdate:testDebugUnitTest ^
  :yotheme:testDebugUnitTest :biz_ggec_hs:check :lib_bluetooth:check --offline --console=plain
→ BUILD SUCCESSFUL in 22s

.\gradlew.bat :app:testDebugUnitTest :biz_ggec_hs:testDebugUnitTest :lib_bluetooth:testDebugUnitTest ^
  :lib_ai:testDebugUnitTest :biz_appupdate:testDebugUnitTest :yotheme:testDebugUnitTest ^
  --rerun-tasks --offline --console=plain
→ BUILD SUCCESSFUL in 2m 8s（强制重跑）
```

| 模块 | suites | tests | failures | errors | skipped |
|------|--------|-------|----------|--------|---------|
| app | 68 | **540** | 0 | 0 | 0 |
| biz_ggec_hs | 19 | **129** | 0 | 0 | 0 |
| lib_bluetooth | 36 | 176 | 0 | 0 | 0 |
| lib_ai | 55 | 411 | 0 | 0 | 0 |
| biz_appupdate | 13 | 58 | 0 | 0 | 0 |
| yotheme | 1 | 1 | 0 | 0 | 0 |
| **合计** | 192 | **1315** | **0** | **0** | **0** |

与 lead 的聚合一致（540/129/176/411/58/1 = 1315，全绿）。产物：`app/build/outputs/apk/debug/app-debug.apk`，25,133,876 bytes（24.0 MiB / 25.1 MB 十进制），mtime 12:11。门禁任务全部实际执行并通过（app 9 + biz 1 + lib_bluetooth 3，`check` 含 lint）。
**未覆盖**：`src/androidTest` 无真机；设备侧行为仍为 `已读到`（见 R4 第 3 项）。

---

# R4. 范围外事项（不计入三范围结论，供最终报告原文引用）

1. **`lib_common` 测试编译基线缺陷**：`lib_common/src/test/java/com/yovo/lib_common/system/file/internal/core/FileCoreAccessGateTest.java:29` — `错误: 无法访问FingerprintManager`，`:lib_common:compileDebugUnitTestJavaWithJavac` FAILED。根因：`compileSdk release(37) minorApiLevel 2` 的 `android.jar` 已不含 `android.hardware.fingerprint.FingerprintManager`，而 Robolectric 的 `ShadowApplication.denyPermissions` 签名仍引用它；`lib_common` 本轮零改动、基线可复现。下一阶段验收：升级 Robolectric 至支持 SDK 37 的版本，或把该用例改为不依赖 `ShadowApplication.denyPermissions`（`ApplicationProvider` + 自定义权限桩/反射隔离），验收标准为该模块 `compileDebugUnitTestJavaWithJavac` + `testDebugUnitTest` 绿且断言等价。因此三波复审一律逐模块跑测试任务，不使用裸 `test`。
2. **`lib_ai` 测试收尾吞异常**：`lib_ai/src/test/java/com/yovo/lib_ai/responses/ResponsesTest.java:250-256`（`catch (java.io.IOException ignored)` 包住 `server.shutdown()`）——只影响清理、未削弱断言（断言行 `:239-245` 与 HEAD 一致），但吞掉的异常根因是 MockWebServer 的 WebSocket 队列未及时收尾。下一阶段验收：改为显式 `webSocket.close()` + `awaitClose(...)` 后再 `shutdown()`，或对关闭耗时做断言，使队列泄漏可观测。
3. **未实机验证**：四波复审的全部证据止于构建、单测（终审波 1315 用例）与结构性门禁；`src/androidTest` 未执行，BLE 扫描/连接/写入/权限查询的真实行为仍为 `已读到`。报告中任何「可达」「真实」结论都是代码级判定，不是运行级已验证。

---

**本轮范围可收口**（R1 `lib_bluetooth` / R2 `biz_ggec_hs` / R3 `app` 三范围 CLEAN；35 条能力 kind 与控件真实性逐条通过；1315 用例全绿、13 条门禁实际执行通过）。范围外仍有三项待下一阶段处理：`lib_common` 的 `FileCoreAccessGateTest.java:29` FingerprintManager 基线与 Robolectric/SDK37 不匹配、`lib_ai/ResponsesTest.java:250-256` teardown 吞 IOException、以及全部设备侧行为未实机验证。
