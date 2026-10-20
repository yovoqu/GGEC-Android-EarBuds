# UI 层 YoUI 接入重构（全量）

> 范围：`app/src/main/java/com/ggec/earbuds/ui/**`（壳层 + AI 对话/设置）与相关文档、测试。
> 目标：把 YoUI 的窗口接入、组件生命周期、主题令牌统一到一套基础设施；删除重复实现与死代码；不改产品交互与视觉。

## 1. 结果一览

| 项 | 重构前 | 重构后 |
| --- | --- | --- |
| 宿主接入 | 每个 Activity 各自调 `YoSystemBar`、各自建 `YoPage`、各自 `destroy` | `YoUi` + `YoPageActivity` 统一 |
| 主题令牌 | `AiStyle`（188 行，颜色 + 第二套字号/间距 + Drawable 工厂）与 `AiViews`（文本/图标工厂）两套 | `AiTheme` 一处；颜色仍是 `YoColors` 快照 |
| 面板骨架 | `AiHistorySheet`、`AiModelSheet` 各自复制 sheet+list+action 映射 | `AiListSheet` 共用，行内组件随刷新回收 |
| 对话框 | `build().show()` 与 `Builder.show()` 混用；键盘/滑块生命周期分散 | 全部 `Builder.show()`；`AiKeyboard`；滑块登记进 `YoComponents` |
| 菜单 | 3 处 `YoMenu` 弹出后不销毁 | 关闭即 `destroy`，并登记到页面释放集合 |
| 壳层 | `MainActivity` 直接取色、显式写默认值、无组件owner | `YoComponents` 持有 `YoTabs`/`AiChatPage`；去掉显式 `FLOATING` |
| 占位页 | 铺满全屏、标题可能压状态栏 | `YoUi.padTopForSystemBars` 只补顶部，背景仍全屏 |
| 死代码 | `AiStyle`、`AiViews`、`AiRowTest`（引用已删类，单测编译失败）、`AiPlatformUiState` 5 个未读字段、MarkdownView 两个零引用方法 | 全部删除 |

## 2. 新结构

```
ui/common/
  YoUi.java           edge-to-edge + 顶部留白（唯一入口）
  YoComponents.java   页面级组件释放集合（逆序、幂等）
  YoPageActivity.java 以 YoPage 为窗口根的 Activity 基类
ui/ai/
  AiTheme.java        颜色/字号/间距/圆角 + text/icon/iconButton/背景工厂
  AiKeyboard.java     键盘收起
  AiDialogs.java      确认/动作列表/输入/多行编辑/滑块
  common/AiTexts.java 文案拼接（从 widget 移入）
ui/ai/chat/
  AiChatBottomInset.java  标签栏/输入法二选一的底部留白
  AiListSheet.java        历史/模型面板共用骨架
ui/ai/widget/             MarkdownView、LinkTextView、AiTypingDotsView（必要自绘）
```

## 3. 生命周期与 inset 归属

| 组件 | 谁创建 | 谁释放 |
| --- | --- | --- |
| `YoPage` | `YoPageActivity.installPage` / `AiChatPage` | 基类 `onDestroy` / `YoComponents` |
| `YoTabs` | `MainActivity` | `YoComponents` |
| `YoSheet` + `YoList` | `AiListSheet` | `AiListSheet.destroy()` |
| `YoMenu` | 每次弹出 | `setOnDismissListener(menu::destroy)` + `YoComponents` 兜底 |
| `YoDialog` | `AiDialogs` | `Builder.show()` 内置 `showThenDestroy` |
| `YoSlider`/`YoTextInput`/`YoButton` | 各视图 | `YoComponents` 或 dialog 的 `hosted` 托管 |
| 页面行内组件（历史“更多”按钮等） | `AiListSheet` | 每次 `resetRows()` 统一销毁 |

inset 规则：

- 顶部：`YoPage` 页面由标题栏自己吃；普通页用 `YoUi.padTopForSystemBars`。
- 左右/底部：`YoTabs`、`YoPage` 负责，页面不再补。
- 输入区底部：`AiChatBottomInset` 取“标签栏高度 / 输入法高度”较大者。

## 4. 删除的冗余

- `ui/ai/widget/AiStyle.java`（188 行）、`AiViews.java`（58 行）：合并为 `AiTheme`。
- `ui/ai/settings/AiAddPlatformDialog` 自带的键盘收起、重复的 `YoDimens` 依赖、显式默认 `setType`。
- 两个设置页重复的 edge-to-edge、`YoPage` 创建、`setContentView`、页面 `destroy`。
- `AiPlatformUiState` 的 `templateId`、`keyHint`、`Models.query`、`Models.sortBySpeed`、`Speed.totalMillis`（全仓库无读取）。
- `AiStyle` 中 9 个未使用常量、`pressableCorners`；`AiTheme` 中未使用的 `confirm/confirmSoft/scrim`。
- `MarkdownView.shownBlockCount()/blockViewAt()`（零引用）、`LinkTextView.performClick()`（等价 `super` 的覆写）、`AiWelcomeView.setListener`（已由构造参数替代）。
- 过时单测 `AiRowTest.java`（`AiRow` 类已不存在，重构前单测编译失败）。

## 5. 验证

| 项 | 结果 |
| --- | --- |
| `:app:compileDebugJavaWithJavac --offline --rerun` | BUILD SUCCESSFUL |
| `:app:checkShellLayerImports :app:checkAiLayerImports` | BUILD SUCCESSFUL |
| `:app:testDebugUnitTest --offline` | BUILD SUCCESSFUL（26 个测试类、220 个用例、0 失败） |
| `:app:assembleDebug --offline` | BUILD SUCCESSFUL（clean 后全量重建亦通过） |
| import 边界 | 仅 `com.yohu.youi.api.*`；无 `widget/internal/core/theme/animation` 引用 |
| 编码检查 | 所有 `app/src/**/*.java`：无 BOM、无 U+FFFD、无 mojibake |
| AAR | 2026-10-19 17:49 按库最新源码重建版（SHA-256 `3CA4EB12…A0CBC`，2,186,788 B），见 [youi-kit-patch-2026-10-19.md](youi-kit-patch-2026-10-19.md)；与库 `releases/` 不再字节一致 |

## 6. 以后改 UI 的约定

1. 新页面：设置类继承 `YoPageActivity`；需要窗口级底栏用 `YoTabs` 作 Activity content。
2. 新建 YoUI 组件后立刻 `components.own(x::destroy)`；不要依赖 GC。
3. 对话框用 `AiDialogs` 或 `YoDialog.Builder.show()`；不要写 `build().show()` 做一次性弹窗。
4. 菜单用“每次新建 + 关闭销毁”，或 `YoMenu.bindMenu` 绑定到会随页面销毁的宿主。
5. 颜色/字号/间距从 `AiTheme`（颜色来自 `YoColors`）取；不要在页面里再建第二套令牌。
6. inset：先用 `YoPage`/`YoTabs` 的默认能力；普通页才用 `YoUi.padTopForSystemBars`。

## 7. 2026-10-19 第二轮：列表 Ripple 与多行输入

按真机反馈补了四处：

1. **独立列表 Ripple 与圆角对不上**：库 `ListRowFace` 对无分组行返回直角，而列表板材是 16dp 圆角。
   已在库侧改为“半径 = 板材圆角 − 容器内边距”，独立列表与分组列表两条路径彻底分开。
2. **独立/分组 Ripple 混用**：最终统一为独立列表——`AiSectionList` 每块一张独立 `YoList`（标题/说明在列表外），
   不再用 `groupHeader` 触发分组卡片几何；行 Ripple 由独立板材的圆角/内边距推导。
3. **列表默认边界线与阴影**：`ListConfig` 增加 `borderWidth/borderColor`（默认 1dp + 主题 `componentBorder`），
   `ListView` 板材与 `ListGroupCard` 分组卡都改用 `YoCorner.fillStroke`；独立列表阴影沿用 `ListChrome` 默认 elevation。
4. **系统提示词输入框**：库 `YoTextInput` 增加 `setMultiline(boolean, minLines, maxLines)`；
   `AiDialogs.editText()` 改用 `YoTextInput`（5–10 行、内置计数与错误提示），不再创建原生 `EditText`。

同时删除了不再使用的 `AiGroupedList`、旧 `AiListSection` 以及库里的兼容常量 `ListDimens.PLAIN_PRESS_RADIUS`。

库补丁、AAR 重建与验证细节见 [youi-kit-patch-2026-10-19.md](youi-kit-patch-2026-10-19.md)。

