# 设备模块架构 v2（严格 MVVM · 强类型）

> 日期：2026-11-04
> 范围：`com.ggec.earbuds.{domain,data,ui}.device`
> 原则：单向数据流、强类型界面状态、View 不接触领域模型/枚举、无兼容层

## 1. 分层

```
domain/device
  model/       DeviceProfile · DeviceSnapshot · DeviceControls · EqPreset ·
               SoundQuality · GestureSlot · GestureAction · DeviceSwitch · Capability
  policy/      DeviceControlPolicy（当前机型允许的选项，只返回枚举）
  port/        DevicePort · DeviceProfileCatalog · DevicePreferences · DeviceControlsPort
  usecase/     ObserveDevice · ConnectDevice · ScanDevices · LoadDeviceProfiles · EditDeviceControls

data/device
  FakeDevicePort · DeviceProfileCatalogImpl · FileDevicePreferences · MemoryDeviceControls

ui/device
  DevicePage · DeviceTheme · DeviceLabels · DeviceFormats
  home/        设备页签：Page + 组件 + ViewModel + UiState + Mapper + HomeToggle
  scan/        扫描页：Activity + Screen + 卡片 + ViewModel + UiState + ScanItem + ScanUiMapper
  settings/    设置页：Activity + Screen + SettingsSectionView 实现 + ViewModel + Mapper + 强类型状态
    page/      每个二级页的强类型行构建器（只输出 DeviceSettingRow）
    section/   每个二级页的视图（只消费 DeviceSettingRow / MusicPanel）

wiring/DeviceGraph  唯一装配根
```

## 2. 数据链路（单向）

```
DevicePort / DeviceControlsPort
        │ 订阅
        ▼
ViewModel（领域状态 → 纯 Mapper → 不可变 UiState）
        │ LiveData
        ▼
View（只渲染 UiState，只上抛语义意图）
        │ SettingId / HomeToggle / ScanItem / 索引
        ▼
ViewModel（语义意图 → 领域操作）
        ▼
EditDeviceControls → DeviceControls（唯一真源）
```

- `DeviceControls` 是首页快捷开关、设置页开关/手势/音效/音质的唯一状态源，不再有第二份本地开关表。
- View **不引用** `DeviceSwitch / GestureSlot / EqPreset / ScanResult` 等领域类型。

## 3. 界面意图契约

| 层 | 类型 | 说明 |
| --- | --- | --- |
| 设置页 | `SettingId` | 语义 ID，ViewModel 翻译成 GestureSlot / DeviceSwitch / Command |
| 首页 | `HomeToggle` | 快捷开关语义 |
| 扫描页 | `ScanItem` | 扫描结果界面项；领域 `ScanResult` 只存在于 ViewModel 内部 |
| 音乐页 | `MusicPanel` | 强类型面板（预置、自定义、音质、开关） |

## 4. 强类型界面状态

`DeviceSettingRow` 是封闭集合：

- `Section`
- `Info`
- `Choice(SettingId, titleRes, valueRes, optionRes)`
- `Toggle(SettingId, titleRes, checked)`
- `Action(SettingId, titleRes, destructive)`

`DeviceSettingsUiState = (titleRes, List<DeviceSettingRow>, MusicPanel)`。

音乐页不再走通用行：`MusicPanel` 描述预置项、自定义音效、音质与开关；自定义音效与预置项属于同一个单选值 `EqPreset`。

## 5. 单选状态机

- 列表：`YoList.setSelectable(true)` + `SelectionIndicator.RADIO`。
- 回调语义：`onSelectionChanged(oldStableId, newStableId)`，取 **newStableId** 计算索引。
- 选中变化只更新 `setSelectedStableId`（带重入保护），不重建列表；结构变化才重建（签名 = 预置项/是否自定义/名称/音质项）。
- View 释放顺序：先 `removeAllViews()` 再 `destroy()`，避免 YoUI 动效状态机在脱离窗口后销毁崩溃。

## 6. 本次清理（无兼容层）

- 删除旧通用模型 `DeviceSettingsUiState.Row` 与 `settings/rows/*`。
- 删除旧字符串 ID 通道（`viewModel.choose(String)` / `toggle(String)` / `action(String)`）。
- 删除不再使用的 `DeviceCommand`。
- 扫描页移除 `ScanResult` 直接进入 View 的耦合，新增 `ScanItem + ScanUiMapper`。
- 首页快捷开关移除 `DeviceSwitch` 直连，改为 `HomeToggle`。

## 8. YoSheet 模态面板对齐（HarmonyOS7）

依据 `youi_kit` 源码与 AAR（REA 校验 `sha256=88d7af…`，`classes.jar` 为唯一代码容器）：

- `SheetPageHost.onMeasure` 用 **EXACTLY / AT_MOST 的“内容视口高度”** 测量页面，内容根视图必须按该高度排版。
- `SheetContentLayout` 结构是「固定 chrome（标题/关闭）+ contentHost」；`SheetChromeView` 自身水平内边距 20dp，内容区全宽。
- `SheetContentInsets` 为 BOTTOM 弹出补充底部安全区；`ScrollSizeMode.FOLLOW_DETENT` 由面板接管滚动手势。

因此自定义音效面板按三层组织，禁止用固定 spacer 猜高度：

```
内容根 LinearLayout（由宿主给 EXACT 高度）
├─ 音效名称：YoList 默认样式（全宽、带箭头）
├─ EQ 画布卡：CANVAS_HEIGHT = 270dp
│   ├─ 重置行
│   ├─ 刻度列 20dp + 10 个滑杆列（TRACK_HEIGHT = 250dp，垂直居中于画布）
│   └─ 频点行（与滑杆同列宽）
├─ 提示语
├─ 弹性 spacer（height=0, weight=1）  ← 唯一正确的底部压位方式
└─ 保存按钮（水平 16dp 边距 + 底部安全内边距）
```

尺寸来自对 HarmonyOS 实机截图的像素测量：滑轨有效行程约占屏高 9.9%，10 列中心跨度约占屏宽 74.5%，据此反推 250dp 滑轨 + 270dp 画布 + 12dp 卡片内边距 + 24dp 刻度列。

刻度列由 `EqTickScaleView` 自绘 12 段短横刻度（0/6/12 为主刻度），与滑轨上下端对齐。

滑杆裁剪：YoUI `OutSetSliderView` 的轨道/滑块绘制几何只使用 `trackPaddingPx()`（不读取 View padding）。垂直 OutSet 沿轴端边距 10vp，而滑块半径 9vp + 阴影半径 4vp + dy 2vp > 10vp，滑到极值时阴影被组件自身边界裁剪。修复落在库层：`trackPaddingPx()` 现在把 `shadowRadius + |dy|` 并入沿轴边距，重新构建 Release AAR（`sha256=3AA4BC…`）并回导；APP 侧 thumb 保持官方 18vp。



## 9. 验收

- `:app:assembleDebug`、五项 Gradle 守卫、`:app:testDebugUnitTest` 全部通过。
- 真机：预置音效单选可切换；自定义音效保存后与预置项同一单选组；自定义 EQ 面板名称行默认 YoList、画布列对齐、保存按钮与卡片同边界。
