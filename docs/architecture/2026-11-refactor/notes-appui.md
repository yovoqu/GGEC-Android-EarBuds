# app-ui 工作记录（app 设备 UI 全量重写）

范围：`app/src/main/java/com/ggec/earbuds/ui/device/**`、对应单测、设备相关 `res`、`ui/shell/ShellScreen.java` 设备页接线（lead 授权）。
契约来源：`docs/architecture/2026-11-refactor/design.md` §6、§6.1（含 lead 后续裁定：`DeviceSnapshot` 增 `Optional<DualConnectionView> dualConnection` + `List<RecordFileView> recordFiles`；`DeviceCapabilityView` 增 `Optional<ControlRange> range`；`DeviceAction.RecordMode` 提到顶层）。

## 1. 渲染链路（描述符 → 行 → 控件）

### 1.1 首页 `ui/device/home`

```
DeviceHomeViewModel.publish()
  → DeviceCapabilityCatalog.model(snapshot.modelId())      // 唯一能力来源（biz 目录投影）
  → DeviceHomeUiMapper.map(profile, snapshot)
      → sections(): EnumMap<DeviceCapabilityGroup, List<Row>>
          分区顺序 = DeviceCapabilityGroup.values()（枚举声明顺序）
          分区内顺序 = profile.capabilities() 声明顺序
      → row(capability): 按 kind 穷尽 switch
          TOGGLE → Row.Toggle(capabilityId, title, Optional<Boolean>)
          CHOICE → Row.Choice(capabilityId, title, Optional<valueLabel>, options)
          ACTION/LEVEL/CURVE/LIST → Row.Link(capabilityId, title)（点行跳该分组设置页）
  → DeviceHomePage.render(state)
      → DeviceCapabilityListView.render(sections, interactive)
          Toggle → ListSuffixSpec.switch_（不可写/未上报 → 禁用开关 / “未上报”文本）
          Choice → ListSuffixSpec.textAndArrow + 选项对话框 → listener.onSelectOption
          Link   → ListSuffixSpec.arrow + listener.onOpenGroup
```

写入链路：`DeviceCapabilityListView → DeviceHomePage.Listener → ShellScreen → DeviceHomeViewModel.setToggle/selectOption → WriteDeviceControl.execute → DevicePort.write`。
失败链路：`DeviceWriteResult.Failed → DeviceHomeViewModel.failures() → ShellScreen 观察 → DeviceFailureText.message → YoToast`。

### 1.2 扫描 `ui/device/scan`

```
ScanViewModel.filterOptions = DeviceCapabilityCatalog.models() 的 (modelId, displayName)
ScanViewModel 结果表 = DiscoveredDevices（域层）
  → ScanUiMapper.map(ScanResult) → ScanItem(modelId, variantId, displayName, bleAddress, rssi)
  → ScanScreen（RecyclerView + DiffUtil）→ ScanDeviceCard.bind → 显示 title(displayName|modelId) / bleAddress / rssi
ScanDevices.startBatch(onResults, Consumer<DeviceFailure>, onFinished, onRemoved)
失败 → DeviceFailure → ScanEvent（穷尽 switch）→ OneShotLiveData → ScanActivity 提示/申请权限
权限 → ScanActivity 经 DeviceGraph.scanPermissionRequest(this) 的 ScanPermissionRequest（宿主能力接口）
```

### 1.3 设置 `ui/device/settings`

```
DeviceSettingsViewModel.publish()
  → DeviceCapabilityCatalog.model(modelId)
  → DeviceSettingsUiMapper.map(group, profile, snapshot)
      ABOUT 组：AboutInfoRows.build(snapshot)（设备事实）
      其余组：SettingsSectionFactory.rows(profile, state, group)
  → SettingsSectionFactory.row(capability)
      TOGGLE  → DeviceSettingRow.Toggle
      CHOICE  → DeviceSettingRow.Choice
      LEVEL   → DeviceSettingRow.Level（range 缺失 = 契约违约 → 不渲染）
      CURVE   → CUSTOM_EQ → Curve；其它 id → Unsupported
      ACTION  → id 路由（FIND_EAR/RENAME/REBOOT/CLEAR_PAIRING/RECORD_LOCAL/RECORD_STREAM/RECORD_TRANSFER）→ Action(kind)；未知 → Unsupported
      LIST    → id 路由（DUAL_CONNECTION → DUAL_CONNECTION，RECORD_LIST → RECORD_FILES）；未知 → Unsupported
  → SettingsRowsView.render(rows)
      Entry（Toggle/Choice/Curve/Info/Unsupported）→ 聚成一个 YoList 卡片
      Component：
        Level          → SliderCardView（描述符 range 驱动；抬手才写 ControlValue.Level）
        Action         → ActionCardView（按 ActionKind 出按钮；确认/输入对话框）
        CapabilityList → DualConnectionCard（DUAL_CONNECTION）/ RecordFilesCard（RECORD_FILES）
```

写入/动作链路同上：`ControlValue` / `DeviceAction` → `WriteDeviceControl` / `RunDeviceAction` → `DevicePort.write/perform`。
列表数据链路：进页连接就绪后 `RunDeviceAction(ReadPairedDevices / ReadRecordList)` 一次 → biz 事件 → 适配层组装 `DeviceSnapshot.dualConnection/recordFiles` → `ObserveDevice` 推送。

## 2. 文件职责（一文件一件事）

| 文件 | 职责 |
|------|------|
| `DeviceFormats` | 数值/大小/未知值格式 |
| `DeviceCapabilityIds` | 能力 id 字面量唯一处（路由键） |
| `DeviceGroupLabels` | 分组 → 分区标题/图标（界面词汇） |
| `DeviceFailureText` | `DeviceFailure` → 文案唯一处 |
| `home/DeviceHomeUiState` | 首页状态模型（分区/行） |
| `home/DeviceHomeUiMapper` | 快照+描述 → 首页状态（纯函数） |
| `home/DeviceHomeViewModel` | 订阅/写入/失败上浮 |
| `home/DeviceHomePage` | 页面骨架（页头/滚动/空态/组合） |
| `home/DeviceSummaryCard` | 摘要卡渲染 |
| `home/DeviceCapabilityListView` | 分区行渲染 + 交互分派 |
| `home/DeviceEmptyState` | 空态 |
| `scan/ScanPermissionRequest`、`ScanPermissionResult` | 宿主权限能力接口（实现由装配根注入） |
| `scan/ScanViewModel` | 扫描状态机（过滤/权限门/代际） |
| `scan/ScanUiState`、`ScanFilterOption`、`ScanUiMapper`、`ScanItem`、`ScanEvent` | 扫描状态与映射 |
| `scan/ScanScreen`、`ScanDeviceCard`、`ScanActivity` | 扫描视图与宿主 |
| `settings/DeviceSettingRow` | 行模型（Entry / Component 两族，sealed） |
| `settings/SettingsSectionFactory` | 描述符 → 行，唯一能力 id 路由表 |
| `settings/AboutInfoRows` | 设备事实行 |
| `settings/DeviceSettingsUiMapper`、`DeviceSettingsUiState` | 设置页状态投影 |
| `settings/DeviceSettingsViewModel` | 订阅/写入/动作/列表数据请求 |
| `settings/DeviceSettingsScreen`、`DeviceSettingsActivity` | 页面骨架与宿主 |
| `settings/PairedDeviceLabels` | 已配对设备枚举 → 文案 |
| `settings/section/SettingsRowsView` | 行 → 视图分派 |
| `settings/section/SliderCardView` | LEVEL 滑杆组件 |
| `settings/section/ActionCardView` | ACTION 按钮组 + 确认/输入 |
| `settings/section/DualConnectionCard` | 双连接设备列表组件 |
| `settings/section/RecordFilesCard` | 录音列表组件 |
| `settings/section/CustomEqSheet`、`EqTickScaleView` | CURVE 编辑器 |
| `settings/section/SettingsChrome`、`SettingsSectionView` | 布局原语与区块接口 |

## 3. 删除的旧结构

`DevicePage`、`DeviceLabels`、`HomeToggle`、`DeviceEntryMapper`、`DeviceEntryListView`、`QuickToggleCard`、
`SettingId`、`settings/page/{About,Control,Dual,Function,Record}PageRows`、
`settings/section/{Music,Control,Function,List}SectionView`，以及 77 条随之失效的 `device_*` 字符串资源。

## 4. 契约边界（不做的事）

- 不出现机型/能力静态表；能力清单、标题、分组、kind、options、range 全部来自 biz 描述符。
- 不做「能力名称 → 控件类型」推导；不做 LEVEL 值域推导（range 缺失即不渲染）。
- 不做协议字节；`ControlValue` / `DeviceAction` 是唯一写入词汇。
- 不吞错：`DeviceWriteResult.Failed(DeviceFailure)` 一律上浮为文案提示。
- 值为空一律渲染「未上报」，不用哨兵值、不强行取值。

## 5. 验证（实跑）

| 命令 | 结果 |
|------|------|
| `.\gradlew.bat :app:compileDebugJavaWithJavac --offline --console=plain` | **BUILD SUCCESSFUL**（含 appcore 的新门禁 `checkAppDependencyBoundary`、`checkAppMvvmLayers`、`checkDeviceLayerImports`、`checkComponentOwnership`） |
| `.\gradlew.bat :app:testDebugUnitTest --offline --console=plain` | **BUILD SUCCESSFUL**（全 app 单测） |
| `ui/device` 单测统计 | 7 个测试类 / 55 个用例 / 0 失败 |

`ui/device` 单测清单：

- `home/DeviceHomeUiMapperTest`：分区顺序与声明顺序、kind → 控件、缺失能力不渲染、未上报不造哨兵、未知选项 id、连接中/未就绪/断开、佩戴未知与未佩戴两句文案。
- `home/DeviceHomeViewModelTest`：空闲不写、写入走 `WriteDeviceControl`、失败上浮 `DeviceFailure`、未就绪拒写、断开。
- `settings/SettingsSectionFactoryTest`：分组顺序、kind → 行类型、专门组件路由（ActionKind / ListKind）、未知能力 id 生成可见 `Unsupported`、LEVEL 缺 range 不渲染、值解析与未上报、`RESET_CONTROL`/`REBOOT` 各自路由、`LDAC`/`VOLUME_ADAPTIVE` 走通用开关行。
- `settings/DeviceSettingsUiMapperTest`：未连接/缺描述、ABOUT 事实行在前、其它分组仅能力行、列表数据随快照、未就绪只读。
- `settings/DeviceSettingsViewModelTest`：空闲拒写、写入与动作走用例、失败上浮、未就绪拒写、DUAL/RECORD 进页读一次列表、无关分组不读。
- `scan/ScanViewModelTest`：筛选项来自目录（modelId + displayName）、空目录只有「全部」、结果只用广播名/地址/信号、去重升级与最新 RSSI、过滤不移除已发现计数、过期移除、失败事件、权限门、进页幂等、连接参数。
- `DeviceFailureTextTest`：每个 `DeviceFailure` 都有文案。

**未实机**：本次没有连接真机或模拟器，扫描、连接、写入、录音、双连接的运行期行为未做实机验证；以上仅为编译与 JVM 单测证据。

## 7. 第二轮（U1：能力 id 覆盖，功能回退修复）

重构前由旧 UI 承担、必须在能力集里保留的用户可见能力，现在全部回到描述符驱动的行上（biz 目录 → 描述符 → 行，界面仍不按 id 分支，唯一例外是 ACTION 路由表）：

| 旧结构 | 新落点 | 渲染 |
|--------|--------|------|
| `device_row_ldac` 开关（`DeviceSwitch.LDAC`） | 能力 `LDAC`（biz `HsControlKind.TOGGLE`，MUSIC） | 通用开关行 → `ControlValue.Toggle` |
| `device_row_volume_adaptive` 开关 | 能力 `VOLUME_ADAPTIVE`（TOGGLE，MUSIC） | 通用开关行 → `ControlValue.Toggle` |
| `SettingId.RESET_CONTROL`（重置手势） | 能力 `RESET_CONTROL`（ACTION，CONTROL） + `DeviceAction.ResetControl()` | `DeviceSettingRow.ActionKind.RESET_CONTROL` → `ActionCardView` 单按钮 + 二次确认 |
| 旧 API `reboot()` | 能力 `REBOOT`（ACTION，ABOUT） + `DeviceAction.Reboot()` | `ActionKind.REBOOT` → 单按钮 + 二次确认（biz 目录已产出该 id，行真正可达） |

`DeviceCapabilityIds` 因此补齐 `LDAC`、`VOLUME_ADAPTIVE`、`RESET_CONTROL`（`REBOOT` 原已有）；`LDAC`/`VOLUME_ADAPTIVE` 只作为「必须按通用开关行渲染」的断言锚点出现在单测里，界面代码对二者没有任何 id 分支。


## 6. 跨边界改动与依赖

- `ui/shell/ShellScreen.java`（lead 授权，仅设备页相关行）：`onOpenEntry(DevicePage)` → `onOpenGroup(DeviceCapabilityGroup)`、`onToggle(HomeToggle)` → `onToggle(String capabilityId)`、新增 `onSelectOption`、`DeviceSettingsActivity.intent(activity, group)`、观察 `DeviceHomeViewModel.failures()` 弹提示、内部类改名 `DeviceTabListener`。
- `ui/device/scan/ScanPermissionRequest` + `ScanPermissionResult`：宿主权限能力接口（Activity 才能发起系统权限请求），实现由 `DeviceGraph.scanPermissionRequest(Activity)` 注入，界面层不 import `provider.**`。
- 依赖 appcore 落地并已就绪：`DeviceProfileView.range`、`DeviceSnapshot.dualConnection/recordFiles`、`DeviceAction.RecordMode`（顶层）、`WriteDeviceControl` / `RunDeviceAction` 签名、`DeviceGraph.settingsFactory(group)` / `scanPermissionRequest(Activity)`。



## 8. 第三轮（P1：值未知不得取消控件）

**缺陷**：`addToggle` 在 `checked.isEmpty()` 时只渲染「未上报」文本、不构建开关，而 Toggle 的唯一写入口就挂在该开关上。协议只有 Set、没有 Query 的 6 项功能开关（VOLUME_ADAPTIVE / WEAR_DETECT / VOICE_WAKEUP / FALL_ALERT / VOLUME_REMIND / INTERPRET）永远拿不到上报值，因此永远发不出写入 —— 功能不可用。

**裁定落地**：值未知只影响「值怎么显示」，不影响「控件是否存在」。写入是绝对设定语义（set on / set off），不需要已知当前值。

| 关注点 | 实现 |
|--------|------|
| 控件始终存在 | `TogglePresentation`（`ui/device`）统一给出 `unknown / position / enabled`；两个 `addToggle` 与 `DualConnectionCard` 一律先建开关，不再有「未知 → 只出文本」的分支 |
| 未知的呈现 | 行副标题显示「未上报」，开关位置取关；这是**未确认**呈现，只存在于视图层（`pending` map），不写回 `DeviceState` / 快照 |
| 写入口 | 开关回调即 `WriteDeviceControl.write(capabilityId, ControlValue.Toggle(target))` |
| 失败不假装成功 | 新增 `DeviceControlOutcome(capabilityId, succeeded)`：VM 在写入落地后按能力 id 回传；视图收到失败即丢弃 `pending` 并重建，开关回到「未上报」呈现；用户文案仍由 `DeviceFailureText` 上浮 |
| 双连接开关 | 原先 `dualConnection.isEmpty()` 时整卡返回、开关不出现；现在始终渲染开关（`Optional<Boolean> reported = state.dualConnection().map(::dualEnabled)`），仅值显示为「未上报」 |

新增/改动的 UI 侧类型：`TogglePresentation`、`DeviceControlOutcome`、`section/ControlOutcomeSink`（设置页内部渲染宿主 → 子卡片的失败回传）。

**新增用例**（6 + 4）：
- `TogglePresentationTest`（新）：未知仍给出可写开关、未知保留用户未确认选择、上报值优先、上报 false 不等于未知、不可写时禁用但仍存在、null 视为空。
- `SettingsSectionFactoryTest#setOnlyTogglesAlwaysProduceRowsEvenWithoutReportedValues`：6 个 Set-only 能力在无任何上报值时仍各生成一行 Toggle。
- `DeviceHomeViewModelTest#unknownValueToggleStillWritesThroughUseCase`、`#failedWriteEmitsFailedOutcomeForThatCapability`。
- `DeviceSettingsViewModelTest#unknownValueToggleStillWritesAndSucceeds`、`#writeFailureIsSurfacedInsteadOfSwallowed`（补 outcome 断言）。

**未实机**：开关的点击与失败回退未在真机上验证（无真机/模拟器；单测为纯 JVM，视图层通过 `TogglePresentation` 规则锁定，Android 视图本身未实例化）。

## 9. 第四轮（契约补充：INFO / GATE 两个 kind + LEVEL 可写化）

HsControlKind / DeviceCapabilityKind 增加 `INFO`、`GATE` 后，界面按 kind 穷尽渲染，不再把「只读信息 / 纯门控 / 可设档位」当成可写控件或伪按钮：

| kind | 首页 | 设置页 | 规则 |
|------|------|--------|------|
| `INFO` | `Row.Info`（只读行，右侧显示值） | `DeviceSettingRow.CapabilityInfo`（只读行） | 值来自 `DeviceState.value(id)`，未知显示「未上报」；**不产生任何写入入口** |
| `GATE` | 不生成行 | 不生成行 | 只用于 `DeviceProfileView.supports(id)` 的功能门控（OTA 归 biz_appupdate，不在设备页出按钮） |
| `LEVEL` | `Row.Link`（跳该分组设置页） | `SliderCardView`（滑杆） | 可写；值未知仍渲染滑杆（`LevelPresentation`：未上报提示 + 未确认位置 + 失败回退），不再禁用 |
| `ACTION` | `Row.Link` | `ActionCardView`（仅路由表内 id） | 路由表外一律 `Unsupported` 可见行，不静默 |

新增：`LevelPresentation`（档位呈现规则）、`ReadOnlyControlText`（只读值文案：Choice→选项标题、Level→数字、Toggle→开/关、Curve→未上报）、`DeviceSettingRow.CapabilityInfo`、`DeviceHomeUiState.Row.Info`、字符串 `device_value_on` / `device_value_off`；`SliderCardView` 实现 `ControlOutcomeSink`，写入失败丢弃未确认值并回到「未上报」呈现。

新增用例（8）：`LevelPresentationTest`（5 例：未知仍可写、未知保留未确认值、上报值优先、不可写禁用但仍存在、null 视为空）、`SettingsSectionFactoryTest`（INFO 只读行带值 / INFO 无值仍出行 / GATE 不出行）、`DeviceHomeUiMapperTest#infoKindRendersReadOnlyRowAndGateKindRendersNothing`。

**验证**：`:app:compileDebugJavaWithJavac --offline` 与 `:app:testDebugUnitTest --offline` 均 BUILD SUCCESSFUL；`ui/device` 9 个测试类 / 73 个用例 / 0 失败。**未实机**。

## 10. 第五轮（CURVE 可达 / 删 INFO / 双连接拆成两条能力）

### U1 CURVE 恒可点
CURVE 行不再以“是否已上报曲线”为可点前提（删除 `points().isPresent()` 开关条件）：

- 行恒定可点（可写连接下）；
- 编辑器初值由 `CurveEditorSeed` 决定：设备上报值 → 本次未确认编辑结果 → **编辑器自带默认曲线**（10 段 0dB，仅编辑起点，不写回 `DeviceState`）；
- 保存即 `WriteDeviceControl.write(id, ControlValue.Curve(points))`；
- 失败经 `ControlOutcomeSink` 链路丢弃 `curveDrafts` 并重建 → 行回到「未上报」呈现。

### U2 删除 INFO
`DeviceCapabilityKind` 回到 7 值（`TOGGLE/LEVEL/CHOICE/CURVE/ACTION/LIST/GATE`）。删除 `DeviceSettingRow.CapabilityInfo`、`DeviceHomeUiState.Row.Info`、`ReadOnlyControlText` 及对应用例；`CONNECTION/BATTERY/DEVICE_INFO/SERVICE_STATUS` 全部降为 `GATE` → 首页与设置页都不再为它们生成行，电量/固件/连接态仍由摘要卡与 `AboutInfoRows`（设备事实行）承担。

### U3 双连接拆成两条能力
- `DUAL_CONNECTION` = TOGGLE → 只有通用开关行；
- `DUAL_CONNECTION_DEVICES` = LIST → `DualConnectionCard`（已配对设备列表 + 连接/断开/设为优先/忘记 四动作）；
- `SettingsSectionFactory.listKindOf` 按 capabilityId 分派：`DUAL_CONNECTION_DEVICES` → `ListKind.DUAL_CONNECTION`，`RECORD_LIST` → `ListKind.RECORD_FILES`；`DualConnectionCard` 不再持有开关，也不再实现 `ControlOutcomeSink`；
- 进页读数据改判 `profile.supports(DUAL_CONNECTION_DEVICES)` 才发 `ReadPairedDevices`。

### 用例（73 → 80）
新增：`CurveEditorSeedTest`（4 例：未上报回默认曲线、未上报保留本次编辑、上报值优先、null 视为空）、`SettingsSectionFactoryTest#gateCapabilitiesRenderNoRows`、`#dualConnectionSplitsIntoASwitchRowAndADeviceListRow`、`#recordListRoutesToTheRecordFilesComponent`、`#curveRowExistsWithoutReportedPoints`、`DeviceHomeUiMapperTest#gateCapabilitiesRenderNoRows`、`DeviceSettingsViewModelTest#curveSaveWritesCurveControlValue`；删除两个 INFO 用例；双连接相关用例全部改用真实 id + 真实 kind。

**验证**：`:app:compileDebugJavaWithJavac --offline` 与 `:app:testDebugUnitTest --offline` 均 BUILD SUCCESSFUL；`ui/device` 10 个测试类 / 80 个用例 / 0 失败。**未实机**。