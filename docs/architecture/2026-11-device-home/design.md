# 设备首页 UI 组件 · 架构设计（推倒重来）

> 触发：设备首页组件架构重构（公共卡片 + 定制组件），按 YoAgentDocs「架构审查 → 架构设计 → 实现」流程。
> 范围：`app` 模块 `ui/device/home` 与 `ui/device/component` 切片。不动领域/数据/provider 层。

## 1. 分层（Android 应用模块：View / ViewModel / Model）

- **View**：`ui/device/home`（页面 + 定制组件）、`ui/device/component`（公共组件）。只做渲染与事件上送。
- **ViewModel**：`DeviceHomeViewModel`。持台账观察、内联开关写入槽、派发意图。
- **Model / 映射**：`domain/device/*`（经用例）+ `DeviceHomeUiMapper`（纯 Model→UiState，无 View/Context）。

## 2. 组件分层（公共 / 定制）

```
ui/device/component        // 公共组件：与业务无关、可复用
  DeviceLayouts            // 布局参数工厂（matchWrap / 页边距 / 竖距）
  DeviceSectionHeader      // 版块标题（与行文字对齐）
  DeviceCardView           // 公共卡片：一张卡的渲染（标题 + 尾槽），支持整列/半列
  DeviceCardRow            // 卡片排布：一行一列 / 一行两列
  BatteryGlyphView         // 电量电池图形（自绘原语）

ui/device/home             // 定制组件：设备首页专用
  DeviceHeroSectionView    // 概览：状态胶囊 + 产品主图 + 三段电量
  DeviceBatteryStrip       // 三段电量（复用 BatteryGlyphView）
  DeviceHomeSectionView    // 一个版块：标题 + 卡片排布（复用公共组件）
  DeviceHomeSectionsView   // 五个版块槽位
  DeviceHomePage           // 装配：标题栏 + 概览 + 版块 / 空态
  DeviceHomeUiState/Mapper/ViewModel, DeviceTitleView
```

**关键决策：不再用 YoList 承载首页行。** 首页是「版块 + 卡片」结构，卡片的宽度（整列/半列）、
尾槽（图标+箭头 / 开关）、按压面都由卡片自身统一；YoList 的行模型不表达半列卡片，套用会逼出补丁。
公共卡片 `DeviceCardView` 承载所有行：入口卡（尾槽=图标+箭头）、开关卡（尾槽=开关）。

## 3. 数据链路

### 设计前（旧）
`ShellScreen → DeviceHomeViewModel(ObserveDeviceRoster) → DeviceHomeUiMapper(roster,entries,writes) → DeviceHomeUiState(sections) → DeviceHomePage.rebuild → DeviceHomeSectionsView(5×YoList) → 屏幕`。
问题：版块行全部用 YoList，无法表达「一行两列」卡片；标题与行文字靠硬编码 dp 猜对齐（表面对齐）；组件混职责。

### 设计后（新）
`ShellScreen → DeviceHomeViewModel(ObserveDeviceRoster + WriteDeviceControl) → DeviceHomeUiMapper(roster,entries,writes) → DeviceHomeUiState(sections{cards}) → DeviceHomePage.rebuild → DeviceHeroSectionView + DeviceHomeSectionsView(5×DeviceHomeSectionView→DeviceCardRow→DeviceCardView) → 屏幕`。
开关写入：`DeviceCardView(switch) → onToggle → ViewModel.toggle → WriteDeviceControl → 端口`，结果回写 UiState。

## 4. 卡片尺寸

`DeviceCardView` 由 `DeviceCardRow` 按 `CardSize` 排布：
- `FULL`：整行一张卡（一行一列）。
- `HALF`：两两并排（一行两列），单张可占半列；奇数尾张占半列、右侧留白。

版块内容示例：`更多` = 「功能设置」整列 + 「录音 | 关于设备」两列。

## 5. 统一内边距（对齐口径，非表面猜测）

- 卡片外沿离屏幕 = `LIST_EDGE`(16dp)。所有版块卡片、概览卡左沿同一竖线。
- 版块标题文字 = 卡片外沿 + 行文字内缩（由 `DeviceCardView` 的卡片内边距决定），标题内边距取同一值 → 标题与所有卡片标题落在同一竖线。
- 全屏只用这两条竖线；长截图 + 水平/垂直参考线核对。

## 6. 不做什么

- 不再给首页行套 YoList；不保留旧 `DeviceCardList`/`DeviceHomeSectionsView(YoList版)`/`DeviceHeroView`。
- 不留兼容层/双轨/TODO/半成品；旧文件删除而非注释冻结。
- 尺寸/颜色/文案只进 `DeviceTheme` 与 `strings.xml`。

## 7. 验收（本轮必须全部通过）

1. 存在公共卡片 `DeviceCardView` + `DeviceCardRow`，支持 `FULL`/`HALF`；`更多` 版块出现「录音 | 关于设备」两列。
2. 首页行全部为卡片（入口卡带右箭头、开关卡带原生开关），无 YoList。
3. 无「X 项设置」副标题。
4. 五版块：设备连接中心 / 声音 / 交互 / 佩戴 / 更多。
5. 长截图叠加两条竖直参考线：所有卡片左沿一条线、所有版块标题与卡片标题一条线。
6. 每个组件文件单一职责；尺寸常量进 `DeviceTheme`，无散落魔法 dp；无兼容层/TODO/旧路径残留。
7. `:app:assembleDebug` 与 `:app:testDebugUnitTest` 全绿；moto 实机运行、无崩溃。
```
