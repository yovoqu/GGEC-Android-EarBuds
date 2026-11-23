# 设备首页 · 组件架构（推倒重来）

> 架构契约：[docs/architecture/2026-11-device-home/design.md](../../docs/architecture/2026-11-device-home/design.md)
> 渲染证据：`render-hero.png`（概览）、`render-top.png`（首屏，含参考线）、`render-bottom.png`（末屏，含参考线）

## 1. 分层：公共组件 / 定制组件

```
ui/device/component       公共组件（与业务无关，可复用）
  DeviceInsets            内边距唯一控制点（page / cardInner / headerText / gap）
  DeviceLayouts           布局参数工厂
  DeviceSectionHeader     版块标题（与卡片标题同线）
  DeviceCardView          公共卡片：入口卡 / 开关卡
  DeviceCardRow           卡片排布：一行一列 / 一行两列
  BatteryGlyphView        电量电池图形（自绘原语）

ui/device/home            定制组件（设备首页专用）
  DeviceHeroSectionView   概览：状态胶囊 + 产品主图 + 三段电量
  DeviceBatteryStrip      三段电量（复用 BatteryGlyphView）
  DeviceHomeSectionView   一个版块：标题 + 卡片排布
  DeviceHomeSectionsView  五个版块槽位
  DeviceHomePage          装配：标题栏 + 概览 + 版块 / 空态
  DeviceHomeUiState / Mapper / ViewModel / DeviceTitleView
```

不再用 YoList 承载首页行：首页是「版块 + 卡片」，卡片宽度（整列/半列）、尾槽、按压面都由
`DeviceCardView` 统一表达，YoList 的行模型表达不了半列卡片。

## 2. 交互与视觉取自 YoUI

- **开关**：`YoSwitch`（`setChecked/setEnabled/setOnCheckedChangeListener`），由版块级
  `YoComponents` 登记 `destroy`，每次重建前释放上一批。
- **按压**：`YoRipple`（默认 `Effect.MASK` 灰遮罩）+ 圆角 + 卡面底色 + 1dp 描边，一次构建成卡面背景。
- **入口卡不再叠右箭头**：有分区图标即足够，避免图标与箭头双尾槽。
- **开关卡**：图标 + `YoSwitch`（游戏/佩戴/音量等开关也带图标）。

## 2.1 图标：Phosphor Duotone（每项专属色相）

- 从 10 家第三方库对比后选定 **Phosphor Duotone**（Phosphor 社区，MIT），见 `icon-library-compare.png`。
- 每个图标用 `assets/duotone/<name>-duotone.svg`，把主色烘焙成**本项专属色相**，副层保留 20% 明度 → 双色质感。
- 9 枚：蓝牙蓝 / 音符橙 / 手指紫 / 推子青绿 / 麦克风玫红 / 信息靛蓝 / 手柄琥珀 / 耳朵绿 / 喇叭青。
- 旧图标整套覆盖删除，无并存。多库对比与做法见
  [docs/architecture/2026-11-device-home/icons.md](../../docs/architecture/2026-11-device-home/icons.md)。

## 2.2 音量自适应归「本机设置」

- 参考华为音频管家：**音量自适应属于设备本机设置，不是「声音」版块快捷开关**。
- 已从首页「声音」区移除，`声音` 仅保留「音乐与音效」入口。
- 目标落位 `更多 → 本机设置` 页；功能清单见
  [docs/architecture/2026-11-device-home/feature-inventory.md](../../docs/architecture/2026-11-device-home/feature-inventory.md)。

## 3. 版块（对齐 HarmonyOS 智慧生活）

| 版块 | 内容 |
|------|------|
| 设备连接中心 | 双连接（整列入口卡） |
| 声音 | 音量自适应（开关卡）+ 音乐与音效（整列入口卡） |
| 交互 | 游戏模式（开关卡）+ 控制设置（整列入口卡） |
| 佩戴 | 佩戴检测（开关卡） |
| 更多 | 功能设置（整列）+ 录音 / 关于设备（一行两列） |

卡片不再有「X 项设置」副标题。

## 4. 内边距口径（唯一控制点）

- 卡片外沿离屏幕 = `DeviceInsets.page` = `LIST_EDGE`(16dp)。
- 卡片标题到屏幕 = `DeviceInsets.headerText` = `page + cardInner`(16dp) = 32dp。
- 版块标题用同一 `headerText`，因此标题与所有卡片标题落在同一条竖线。
- 参考线验证：`render-top.png` 红线=45px(卡片外沿)、绿线=90px(标题)；所有卡片左沿与所有标题
  均贴线，末屏 `render-bottom.png` 同样成立（含「录音 | 关于设备」两列）。

## 5. 验证

- `:app:assembleDebug` 通过（含 `checkApp*` 九个架构门禁）。
- `:app:testDebugUnitTest`：618 用例，0 失败（含版块分流、整列/半列、pending 写入、空态）。
- moto edge 60 pro 实机运行无崩溃；上述三张渲染图为实机截图。
