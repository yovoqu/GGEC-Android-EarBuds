# 耳机 App 侧 YoUI 调用点审计

> **本文记录的是重构前的状态（2026-10-19 15:00 前），其中文件行号、类名（AiStyle/AiViews/AiRowTest）和部分风险已随全量重构消失。**
> 重构后的结构、生命周期规则与验证结果见 [ui-refactor-youi.md](ui-refactor-youi.md)。

> 审计对象：`D:\A_yoprogram\Github_Gallery\GGEC-Android-EarBuds`（只读；排除 `**/build/**`）
> 基准库：`D:\A_yoprogram\Github_Gallery\Android-YoUI`（`youi_kit` 源码 + `app` 演示模块）
> 审计方式：静态阅读（read/grep/glob）+ 与库默认值逐条比对；未在真机运行，凡"推断"均单独标注
> 路径约定：App 文件相对耳机仓根（`app/src/main/java/com/ggec/earbuds/...`）；库/演示文件相对 Android-YoUI 根（`youi_kit/src/main/java/com/yohu/youi/...`、`app/src/main/java/com/yohu/youidemo/...`）
> 配套材料：库侧契约见 `app/doc/youi-contract-audit.md`；AAR 与源码一致性见 `app/doc/youi-aar-verification.md`（三份 AAR SHA-256 同为 `A5112DCE…1D41AD`，App 的 `app/libs/youi_kit-1.0.0.aar` 与 `releases/` 字节一致，且包含构建时的未提交改动）→ 本文以库源码默认值作为判定基准是成立的。
> 判定分级：**[默认]** 与库默认值/演示写法一致；**[非默认]** 显式改了默认但 API 合法、可解释；**[显式默认值]** 把库默认值又写了一遍（无行为差异）；**[偏差]** 与库显式契约（javadoc / 构建校验 / inset 归属）冲突，或存在确定缺陷；**[绕过]** 用裸 Android 控件替代库组件。

---

## 0. 结论速览

- 直接 `import com.yohu.youi.*` 的文件 **15 个**；另有 **2 个**文件（`AiAssistantMessageView.java`、`AiUserMessageView.java`）不直接 import，只经 `AiStyle`/`AiViews` 间接消费 `YoColors`/`YoFonts` 令牌。
- **import 边界 0 违规**：全部 59 条 `com.yohu.youi` import 均落在 `com.yohu.youi.api.*`（详 §2.1），与库 `verifyApiBoundary` 及演示的"宿主只允许 api 包"规则一致。
- 逐调用点判定（91 个逻辑行，展开 setter 后约 180+ 次调用）：**[默认]** 55、**[非默认]** 14、**[显式默认值]** 2、**[偏差]** 9、**[绕过]** 11。
- **最高风险是宿主骨架，而不是单个组件参数**：
  1. `YoTabs` 里嵌了 `YoPage`（库两处 javadoc 明令禁止）；
  2. `MainActivity` 没人为状态栏 top inset 负责（`YoTabs` 源码确认不处理 top）；
  3. 三处 `YoMenu` 弹出后从不 `destroy()`；
  4. `AiHistorySheet` 每次刷新都新建行内 `YoButton` 且不销毁；
  5. `AiDialogs.slider` 的 `YoSlider` 未销毁。
- 组件级"默认调用"整体是合格的：`YoList`/`YoSheet`/`YoPage`/`YoButton`/`YoTextInput`/`YoSlider`/`YoSegmentButton`/`YoCard` 的 Builder→build 流程、`YoSheet`/`YoList`/`YoPage`/`YoTabs` 的 `destroy()` 配对、`YoToast` 传 Activity、`YoDialog` 的 hosted 托管，基本都按库写法。主要问题集中在 **窗口级容器组合、inset 归属、长生命周期组件的回收、以及聊天/Markdown 区域的裸控件自绘**。

### Top 5 风险（详见 §5）

| 排名 | 风险 | 位置 | 级别 |
|---|---|---|---|
| R1 | `YoTabs` 内容里嵌套 `YoPage`（两个窗口级壳互为父子，库显式禁止） | `app/src/main/java/com/ggec/earbuds/ui/ai/chat/AiChatPage.java:186-200` + `app/src/main/java/com/ggec/earbuds/ui/shell/MainActivity.java:60-61,65` | 高 |
| R2 | `MainActivity` 无顶部状态栏 inset 处理；`YoTabs` 源码只消费 left/right 与底栏，top 无人消费 | `MainActivity.java:27-31,63-64`（缺失项） | 高 |
| R3 | 三处 `YoMenu` 未 `destroy()`，每次弹出泄漏一个菜单与其主题/配置监听 | `AiChatPage.java:488-508`、`ui/ai/settings/AiSettingsActivity.java:251-263`、`ui/ai/settings/AiPlatformActivity.java:313-332` | 中高 |
| R4 | `AiHistorySheet` 每次 `update()` 新建行内 `YoButton`，`list.clear()` 前不销毁，`destroy()` 也不覆盖 | `AiHistorySheet.java:105-123,139-149`（对照 `:96-103`） | 中高 |
| R5 | `AiDialogs.slider` 用 `setContentView` 放入 `YoSlider`（非 hosted），弹窗销毁不回收该组件 | `ui/ai/widget/AiDialogs.java:281-294,302-308` | 中 |

---

## 1. 审计范围与方法

### 1.1 纳入范围

| 类别 | 文件 |
|---|---|
| 直接 YoUI 调用（15） | `ui/shell/MainActivity.java`、`ui/shell/ShellPageView.java`、`ui/ai/chat/AiChatPage.java`、`AiComposerView.java`、`AiHistorySheet.java`、`AiModelSheet.java`、`AiWelcomeView.java`、`ui/ai/settings/AiSettingsActivity.java`、`AiPlatformActivity.java`、`AiListSection.java`、`AiAddPlatformDialog.java`、`ui/ai/widget/AiDialogs.java`、`AiStyle.java`、`AiViews.java`、`MarkdownView.java` |
| 间接消费令牌（2） | `ui/ai/chat/AiAssistantMessageView.java`、`AiUserMessageView.java`（经 `AiStyle`/`AiViews`） |
| 裸控件辅助（旁证） | `ui/ai/chat/AiMessageAdapter.java`、`AiTypingDotsView.java`、`ui/ai/widget/LinkTextView.java` |

`grep 'com.yohu.youi'` 命中 59 行、全部为 import（15 文件），XML/资源/Manifest 无 YoUI 自定义 View 引用；`lib_ai`/`lib_common`/`lib_bluetooth`/`biz_appupdate`/`yotheme` 无 `com.yohu.youi` import（`yotheme` 是另一套旧主题库 `com.yovo.yotheme`，不在本次范围）。

### 1.2 判定基线

- 组件 API 默认值以库源码为准（如 `ButtonConfig.java:205-209`、`TabsConfig.java:85-90`、`PageConfig.java:111-142`、`SheetBuilder.java:59-77`）。
- 宿主写法以演示模块为准（`TabsTestActivity`、`PageTestActivity`、`DialogsAndContainersTestActivity`）。
- 两个显式契约直接作为"偏差"判据：
  - `youi_kit/.../api/navigation/YoTabs.java:16-20`："窗口根容器：Activity 的 content 就是 getView()。不要再包一层 YoPage（两者都是窗口级铬层，嵌套会把底栏顶出可视区）。"
  - `youi_kit/.../api/containers/YoPage.java:47-49`："窗口根容器。内容槽放页面内容，不要再塞一个窗口级壳（YoTabs）……应用级底栏用 YoTabs 做 Activity content，而不是 YoPage 的 child。"
- inset 归属以 `PageInsets` / `TabsWindowInsetBinder` 源码为准（§2.3）。

### 1.3 明确不在本文下结论的部分

- 未上真机，所有"视觉/间距双倍"均为源码推断（§6 单列）。
- R8/minify 下的宿主行为、字体回退已由 `youi-aar-verification.md` 覆盖，本文不重复。
- `yotheme` 旧库与 `com.yovo.lib_common` 的 UI（非 YoUI）不在任务范围。

---

## 2. 全局检查

### 2.1 import 边界：✅ 全部合规（0 违规）

- 59 条 import 去重后仅 19 个类型：`api.containers.{YoCard,YoList,YoPage,YoSheet}`、`api.containers.list.{ListPrefixSpec,ListSuffixSpec,ListTextSpec}`、`api.controls.{YoButton,YoSegmentButton,YoSlider,YoTextInput}`、`api.feedback.{YoDialog,YoToast}`、`api.navigation.{YoMenu,YoTabs}`、`api.platform.YoSystemBar`、`api.theme.{YoColors,YoDimens,YoFonts}`。
- 未出现 `com.yohu.youi.widget.*` / `internal.*` / `core.*` / `theme.*` / `animation.*`（grep 复核：非 api import 命中数为 0）。
- 与 App 构建期的分层校验一致：`app/build.gradle:86-102,136-147` 只禁止 `ui/ai`、`domain`、`data`、`platform`、ViewModel 越层，未禁止 ui 用 YoUI；宿主读 api 包属于预期用法。
- 结论：**不存在"突破 api 边界"的问题**，内部实现（`PageInsets`/`TabsWindowInsetBinder`/`ThemeBind`）只作为判定依据被本文引用，App 并未直接依赖它们。

### 2.2 宿主默认步骤矩阵（3 个 Activity）

| 默认步骤 | MainActivity | AiSettingsActivity | AiPlatformActivity | 依据 |
|---|---|---|---|---|
| `YoSystemBar.prepareTransparentSystemBars` | ✅ `:27` | ✅ `:49` | ✅ `:60` | 演示 `MainActivity:23`、`TabsTestActivity:72`、`PageTestActivity` |
| 状态栏 top inset 归属 | ❌ **缺失**（无人 padding） | ✅ `YoPage` 标题栏内部吃 | ✅ 同左 | `PageInsets.java:35,90-103`；`TabsWindowInsetBinder.java:88-99`（YoTabs 不取 top） |
| 底部导航 inset 归属 | ✅ `YoTabs` 底栏自用 + `getContentInsetPx()` 给输入区 | ✅ `YoPage` 的 `PageNavPad` | ✅ 同左 | `TabsHostBindings.java:117-119`；`PageNavPad.java:19-31` |
| `YoColors.of(Context)` | ✅ `:48`（局部变量） | 经 `AiListSection`/`AiStyle` 间接 | 经 `AiListSection`/`AiStyle` 间接 | 令牌快照，未跨色模式缓存 |
| `YoTheme.setColorMode` / 监听 | —（不调用 = 默认 `FOLLOW_SYSTEM`） | — | — | `ThemeController.java:18`；库侧契约文档 §2.3 判定"不调用即默认方式" |
| 根/组件 `destroy()` | ✅ `tabs.destroy()` `:41`、`aiPage.destroy()` `:37` | ✅ `page.destroy()` `:97` + 4 个 section `:91-94` | ✅ `page.destroy()` `:120` + `search.destroy()` `:112` + 3 个 section `:115-117` | 演示 `PageTestActivity:264-271`、`TabsTestActivity:126-133` |
| `setContentView` 对象 | `tabs.getView()` `:30` | `page.getView()` `:73` | `page.getView()` `:96` | 演示模板一致 |

结论：宿主默认步骤在"每个 Activity 都调 prepareTransparentSystemBars / 每个窗口壳都 destroy / YoTheme 走默认"三项上合格；**唯一结构性缺口是 MainActivity 的 top inset**（R2），且它被"AI tab 内又套了一个 YoPage"部分掩盖——AI tab 顶部由 YoPage 自己处理，其它 tab（`ShellPageView`）完全裸露，形成同窗口内两种顶部行为。

### 2.3 inset 契约判定（任务点 7：MainActivity 与 YoTabs 是否冲突）

**库侧事实（直接源码，非推断）**：

1. `YoTabs` 的窗口 inset 订阅只取 `systemBarsAndCutout` 的 left/right 和 `navigationProtectionBottom`，**不读取 top**：`youi_kit/.../widget/navigation/tabs/internal/TabsWindowInsetBinder.java:88-99`。
2. 宿主 host 的 padding 只写 `setPadding(left, 0, right, 0)`，top 恒为 0：`.../TabsHostBindings.java:125-132`。
3. 底栏自己消费底部 nav（TILED 时作为项行 paddingBottom）：`.../TabsHostBindings.java:116-123`；内容避让量通过 `getContentInsetPx()`/`OnContentInsetListener` 暴露：`.../TabsHostView.java:251-258`、`api/navigation/YoTabs.java:163-164`。
4. `TabsHostView.applyPageEndInset` 只会给"页面自身或单层子 View"里的纵向 scroller 垫 contentInset，**不会下钻**：`.../TabsHostView.java:232-249` + `.../TabsScrollBinder.java:99-118`。

**演示写法（事实来源）**：`app/src/main/java/com/yohu/youidemo/uitest/TabsTestActivity.java:116-119` 明确给根容器设置 `padding(bars.left, bars.top, bars.right, 0)`——即 YoTabs 场景下 top 由宿主负责。

**App 判定**：`MainActivity.java:27-31,63-64` 只有 `prepareTransparentSystemBars`，没有任何 top inset owner（全仓 grep `padViewForSystemBars|systemBarsAndCutout|setOnApplyWindowInsetsListener`，MainActivity 0 命中；只有 `AiChatPage.java:328` 注册了一个纯 IME 监听）。因此 **MainActivity 与 YoTabs 的 inset 契约存在缺口**：状态栏区域没有消费方，`ShellPageView` 的全屏 `FrameLayout` 直接铺到状态栏下。

补充两点，避免误判：

- 当前 `ShellPageView` 只是"整页底色 + 居中占位文字"（`ShellPageView.java:24-35`），所以缺口暂时没有明显视觉事故；一旦这些 tab 放真实内容（顶部标题/列表），就会压到状态栏。这不是"能跑就行"的合规写法。
- **不能**用 `YoSystemBar.padViewForSystemBars(tabs.getView(), false)` 补这个洞：该方法会**覆盖**目标 View 已有的 `OnApplyWindowInsetsListener`（`SystemBarUtils.java:204`，库侧契约 §2.4 已记录），会顶掉 YoTabs 自己的 inset 绑定。正确做法见 §5 修复建议 F2。

### 2.4 生命周期配对表

| 组件实例 | 创建位置 | 销毁位置 | 配对 | 说明 |
|---|---|---|---|---|
| `YoTabs` | `MainActivity.java:51` | `MainActivity.java:41` | ✅ | 演示同款 |
| `YoPage`（AI tab） | `AiChatPage.java:186` | `AiChatPage.java:228`（经 `MainActivity:37`） | ✅ | 但嵌套 YoTabs（R1） |
| `YoPage`（设置） | `AiSettingsActivity.java:66` | `:97` | ✅ | |
| `YoPage`（平台） | `AiPlatformActivity.java:89` | `:120` | ✅ | |
| `YoSheet` ×2 | `AiHistorySheet.java:70`、`AiModelSheet.java:53` | `AiHistorySheet.java:98`、`AiModelSheet.java:80` | ✅ | |
| `YoList` ×6 | `AiHistorySheet:50`、`AiModelSheet:43`、`AiListSection:39`×4 | `AiHistorySheet:101`、`AiModelSheet:83`、`AiListSection:102` | ✅ | |
| `YoButton`（页级） | `AiChatPage:101`、`AiHistorySheet:51`、`AiWelcomeView:174,185` | `AiChatPage:227`、`AiHistorySheet:102`、`AiWelcomeView:119-122,171-173,205-213` | ✅ | |
| `YoButton`（历史行内） | `AiHistorySheet.java:140` 每次 `update()` | **无** | ❌ **R4** | 每次刷新泄漏 N 个 |
| `YoCard` | `AiWelcomeView.java:93` | `:212` | ✅ | |
| `YoTextInput` | `AiDialogs:121,138`（hosted）、`AiAddPlatformDialog:112`×3、`AiPlatformActivity:63` | dialog 托管自动销毁 / `AiAddPlatformDialog:97-100` / `AiPlatformActivity:112` | ✅（有边界缺口，R7） | hosted 由 `DialogPresets.ownHosted` 托管 |
| `YoSlider` | `AiDialogs.java:281` | **无** | ❌ **R5** | 非 hosted，dialog 不管 |
| `YoSegmentButton` | `AiAddPlatformDialog.java:40-48` | `:97` | ✅（边界缺口 R7） | |
| `YoMenu` ×3 | `AiChatPage:488`、`AiSettingsActivity:251`、`AiPlatformActivity:313` | **无** | ❌ **R3** | 库要求显式 `destroy()` |
| `YoToast` | 静态调用 6 处 | 库内部自管（延时/替换） | ✅ | 传的都是 Activity |
| `YoDialog` | 8 处 Builder 链 | `Builder.show()` 内部 `showThenDestroy` | ✅ | `DialogImpl.java:834-848` |

### 2.5 builder 用法检查

- **build 之后再改**：3 处。
  - `AiChatPage.java:501-508`：`build()` 之后 `addItem`/`addDestructiveItem` 再 `show()`。库实现每次都重建 model 并 `syncModel()`（`MenuImpl.java:84-121`、`442-444`），所以**能正常工作**，但演示模块全部是"先 add 再 build"；这不是崩溃点，是非默认写法。
  - `AiSettingsActivity.java:259-262`、`AiPlatformActivity.java:330-332`：`build()` 之后 `setSelectedItemId()` 再 `show()`，同上。
- **Builder 链完整性**：15 个文件的 build 调用全部来自库 Builder（`build()` 后取 `getView()`/`show()`），无自造 Builder、无 `YoList.Builder` 与实例方法混用（`addItem` 均在实例态或 builder 态各自闭合）。
- **显式写默认值**（无行为差异）：`MainActivity.java:52` 的 `setBarStyle(FLOATING)`（默认即 FLOATING，`TabsConfig.java:85`）、`AiChatPage.java:189-191` 的 `TitleMode.MINI`/`Level.PRIMARY`/`setMenuMaxCount(3)`（默认分别是 MINI/PRIMARY/3，`PageConfig.java:111-113`、`PageDimens.java:53`）、`AiAddPlatformDialog.java:41` 的 `SegmentType.SINGLE_CHOICE`（默认即 SINGLE_CHOICE，`SegmentConfig.java:103`）。

### 2.6 参数覆盖（显式颜色/尺寸/样式绕过库令牌默认值）

| 覆盖点 | App 取值 | 库默认/令牌 | 判定 |
|---|---|---|---|
| `AiStyle` 自建尺寸阶梯 | `TEXT_BODY=15`、`TEXT_TITLE=17`、`TEXT_HEADLINE=20`、`RADIUS_FIELD=22`、`SPACE_XXL=32`（`AiStyle.java:22-47`） | `YoDimens`：`TEXT_SIZE_BODY=14`、`TEXT_SIZE_MEDIUM=16`、`TEXT_SIZE_LARGE=18`、`TEXT_SIZE_TITLE=20`、`TEXT_SIZE_HEADLINE=24`；`RADIUS_CONTROL=20`/`RADIUS_HUGE=24`；间距最大 24（`YoDimens.java:99-113`、`56-69`、`15-37`） | 有意自建（`ui-design.md:40-58` 定义了另一套阶梯），但与库令牌并存，同一 App 出现两套字号/圆角口径 |
| 页面底色 | `palette().snowGray(1)`（`MainActivity.java:49`、`AiStyle.java:80`） | 演示页底色用 `backgroundSecondary()`（`TabsTestActivity.java:77`、`HomeFragment.java:44`） | 非默认；有 `ui-design.md:32` 依据 |
| `YoButton` 样式 | `CIRCULAR+SMALL`（`AiChatPage:103-104`）、`TEXT+CIRCULAR+WARNING+SMALL`（`AiHistorySheet:53-56`、`:142-144`）、`OUTLINED+SMALL`（`AiWelcomeView:188-189`） | 默认 `PRIMARY+CAPSULE+MEDIUM`（`ButtonConfig.java:205-208`） | 非默认（显式按钮样式，属设计选择） |
| `YoMenu.Placement` | `AUTO`（3 处） | 默认 `BOTTOM_LEFT`（`MenuBuilder.java:97`+`MenuImpl.java:38`） | 非默认（AUTO 避免越界，合理） |
| `YoMenu.SelectedMark` | `CHECK`（2 处） | 默认 `NONE`（库侧契约 §3.5） | 非默认（选择型菜单需要） |
| `YoSheet` backdrop | `SOLID`（`AiHistorySheet:74`、`AiModelSheet:57`） | 默认 `CACHED`（`SheetBuilder.java:77`） | 非默认；`ai-architecture.md:189` 已记录原因（面板内容透出） |
| `YoSheet` 高度 | `Height.large()` 单档（`AiHistorySheet:73`、`AiModelSheet:56`） | 默认 `[medium, large]` 双档（`SheetBuilder.java:59-60`） | 非默认（未在文档记录） |
| `YoTextInput` 尺寸 | 未设置（默认 `NORMAL`） | — | ✅ 默认 |

### 2.7 裸控件绕过 YoUI 清单

| 位置 | 被绕过的库组件 | 替代实现 | 可接受性 |
|---|---|---|---|
| `AiComposerView.java:75-135` | `YoTextInput`（输入框）、`YoButton`（发送/停止/模型 chip） | 裸 `EditText`、`ImageView`、`LinearLayout` + `GradientDrawable`/`RippleDrawable` | 中：多行输入+随状态换图，YoUI 无等价物，但按钮/输入语义（按压、禁用、无障碍 role）全部自绘 |
| `AiChatPage.java:79-91` + `AiMessageAdapter.java` | `YoList` | 裸 `RecyclerView` + `ListAdapter` + 自绘气泡 | 可接受：流式聊天列表，`YoList` 无此能力；`ui-design.md:5.1` 明确用 RecyclerView |
| `AiDialogs.java:186-213` | `YoTextInput`（多行） | 裸 `EditText`+`TextView` 计数/错误行 | 可接受：`YoTextInput` 仅单行；但错误码/计数逻辑是自绘 |
| `AiListSection.java:116-127` | 无对应库文本组件 | 裸 `TextView` + `YoDimens`/`YoColors`/`YoFonts` | 低：令牌正确，仅缺"文本组件"这一层 |
| `AiViews.java:30-68` | 无对应组件 | 裸 `TextView`/`ImageView` 工厂 | 低：全局字族已走 `YoFonts` |
| `MarkdownView.java`（全文 517 行） | 无对应组件 | 裸 `TextView`/`TableLayout`/`LinkTextView` + `YoFonts` | 可接受：Markdown 渲染无库组件；字体走令牌 |
| `ShellPageView.java:24-35` | 无 | 裸 `FrameLayout`+`TextView` | 低（占位页） |
| `AiAssistantMessageView.java`、`AiUserMessageView.java` | 无 | 全裸控件 | 可接受（同聊天特化） |

结论：绕过集中在 AI 聊天/Markdown/输入区这些"库没有对应组件"的特化区域，属**结构性绕过**而非随手乱写；但代价是这些区域完全不享受库统一的按压态、禁用态、字体缩放与主题重绘机制，且叠加了 `AiStyle` 自建尺寸阶梯（§2.6）。

---

## 3. 逐文件表格

> 列说明：**行号** 为 App 文件行；**实参/覆盖** 只列与默认有关的参数；**证据** 中库/演示路径相对 Android-YoUI 根。每个文件末尾给"小结"计数（默认/非默认/显式默认值/偏差/绕过）。

### 3.1 `ui/shell/MainActivity.java`（13 行）

| 行号 | 调用 | 实参/覆盖 | 与默认/演示的差异 | 判定 | 风险 | 证据 |
|---|---|---|---|---|---|---|
| 27 | `YoSystemBar.prepareTransparentSystemBars(this)` | Activity | 与演示一致；图标明暗取 `YoColors` 快照 | 默认 | — | `youi_kit/.../api/platform/YoSystemBar.java:27-29`；演示 `MainActivity.java:23` |
| 48 | `YoColors.of(this)` | Activity | 局部变量，不跨色模式复用（快照语义正确） | 默认 | — | `youi_kit/.../api/theme/YoColors.java:21-30` |
| 49-50 | `colors.palette().snowGray(1)`、`colors.textPrimary()` | 页色/标题色 | 演示页底色用 `backgroundSecondary()` | 非默认（有 `ui-design.md:32` 依据） | 低 | `TabsTestActivity.java:77` |
| 51-53 | `YoTabs.create(this).setBarStyle(FLOATING).setSelectedId(...)` | FLOATING 显式写 | FLOATING 即库默认（`TabsConfig.java:85`）→ 显式默认值 | 显式默认值 | 低 | `TabsConfig.java:85` |
| 54-66 | `builder.addTab(id,label,icon,selectedIcon,page)` ×N | 5 参重载 | 演示同款 | 默认 | — | `youi_kit/.../api/navigation/YoTabs.java:70-75` |
| 57-61 | AI tab 内容 = `new AiChatPage(...).view()`（内部是 `YoPage`） | 窗口级壳嵌套 | 库两处 javadoc 禁止 `YoTabs`⊃`YoPage` | **偏差** | 高 | `YoTabs.java:16-20`；`YoPage.java:47-49`；见 R1 |
| 67 | `builder.build()` | — | 默认 | 默认 | — | `TabsImpl.java` |
| 68-69 | `setOnChangeListener(→ shellViewModel.select)` | — | 默认 | 默认 | — | `YoTabs.java:123` |
| 72-73 | `setOnContentInsetListener(chat::setBottomInset)`、`getContentInsetPx()` | 悬浮栏内容避让 | 演示同款用法 | 默认 | — | `TabsTestActivity.java:100-104` |
| 30 | `setContentView(tabs.getView())` | — | 窗口根交给 Tabs，正确 | 默认 | — | `TabsTestActivity.java:110-115` |
| 30-44 | 缺失：`padViewForSystemBars`/`systemBarsAndCutout`/自注册 insets listener | 全仓无命中 | YoTabs 不消费 top（`TabsWindowInsetBinder.java:88-99`），演示给 root 垫 top | **偏差** | 高 | 见 R2；`TabsHostBindings.java:125-132`；`TabsTestActivity.java:116-119` |
| 34-44 | `aiPage.destroy()` 先于 `tabs.destroy()` | 顺序 | 建议让窗口壳先摘内容（`tabs.destroy()` 内部会 `contentLayer.removeAllViews()`），当前顺序非默认但未见错误 | 非默认（顺序建议） | 低 | `TabsImpl.java:120-128,404-413` |
| — | 未调用 `YoTheme.setColorMode`/`addListener` | — | 缺省 `FOLLOW_SYSTEM`，即库的默认方式 | 默认 | — | `ThemeController.java:18` |

**小结**：默认 8 / 非默认 2 / 显式默认值 1 / 偏差 2 / 绕过 0（合计 13 行；其中 2 行"偏差"是窗口壳嵌套与 top inset 缺失）。

### 3.2 `ui/shell/ShellPageView.java`（3 行）

| 行号 | 调用 | 实参/覆盖 | 差异 | 判定 | 风险 | 证据 |
|---|---|---|---|---|---|---|
| 28 | `label.setTextSize(YoDimens.TEXT_SIZE_HEADLINE)` | 24sp | 令牌用法正确，但"占位页用大标题"非演示写法 | 非默认 | 低 | `YoDimens.java:111-113` |
| 31 | `YoFonts.applyBold(label)` | — | 默认字族 | 默认 | — | `YoFonts.java:57-59` |
| 24-35 | 裸 `FrameLayout`+`TextView` 铺满（无 inset padding） | — | 无库组件可绕；但作为 tab 内容不做 top 避让，是 R2 的放大项 | 绕过 | 低 | `ShellPageView.java:24-35` |

**小结**：默认 1 / 非默认 1 / 显式默认值 0 / 偏差 0 / 绕过 1。

### 3.3 `ui/ai/chat/AiChatPage.java`（11 行）

| 行号 | 调用 | 实参/覆盖 | 差异 | 判定 | 风险 | 证据 |
|---|---|---|---|---|---|---|
| 79-91 | 裸 `RecyclerView` + `DefaultItemAnimator` + 自定 `LinearLayoutManager` | 替代 `YoList` | 聊天流式列表无库等价物；`ui-design.md:5.1` 明确用 RecyclerView | 绕过 | 中 | `AiMessageAdapter.java`；`ui-design.md:77-89` |
| 86 | `messages.setPadding(0, dp4, 0, dp8)` | 自定内边距 | `YoPage` 的 `PageNavPad` 会把该 scroller 的 `paddingBottom` **覆盖**为 nav 高度（写 padding 而非累加） | 非默认 | 低 | `PageNavPad.java:19-31,41-50`（R12） |
| 101-105 | `YoButton.create().setIcon().setShape(CIRCULAR).setSize(SMALL).build()` | CIRCULAR/SMALL | 默认 `PRIMARY+CAPSULE+MEDIUM` | 非默认 | 低 | `ButtonConfig.java:205-208` |
| 186-200 | `YoPage.create(...).setTitle().setSubtitle().setTitleMode(MINI).setLevel(PRIMARY).setMenuMaxCount(3).setMenuItems(3).setContent(column).build()` | MINI/PRIMARY/3 显式写默认值；作为 YoTabs tab 内容 | **窗口级壳嵌套**（核心偏差）；MINI/PRIMARY/3 本身是默认值 | **偏差**（嵌套）+ 显式默认值 | 高 | `TabsTestActivity`/`PageConfig.java:111-113`；R1 |
| 204-207 | `installInsets(column)`：`ViewCompat.setOnApplyWindowInsetsListener` 只取 IME | 宿主自绘 IME 避让 | 合规（宿主允许自管 IME），但嵌套 YoPage 后同一子树出现第二个 inset 消费者（推断） | 非默认 | 中 | `AiChatPage.java:327-345`；`PageInsets.java:33-38` |
| 216-221 | `setBottomInset(max(ime, tabsInset))` | 悬浮栏+IME 取大 | 与 `ai-architecture.md:121` 记录一致 | 默认 | — | `AiChatPage.java:347-350` |
| 302 | `YoToast.show(activity, text)` | Activity | 默认（库要求 Activity） | 默认 | — | `YoToast.java:57`；`ToastImpl.java:442-452` |
| 428 | `YoTextInput.InputType.TEXT` 传给 `AiDialogs.input` | — | 默认枚举 | 默认 | — | `YoTextInput.java:35-41` |
| 443 | `YoToast.show(activity, ...)` | — | 默认 | 默认 | — | 同上 |
| 488-508 | `YoMenu.create().addItem(COPY).setAnchorView(anchor).setPlacement(AUTO).setOnItemClickListener().build()` → `addItem(REGENERATE)` → `addDestructiveItem(DELETE)` → `show()` | AUTO；build 后追加；**无 destroy** | 默认 `BOTTOM_LEFT`；build 后改（库支持但非演示写法）；库要求显式 `destroy()` | **偏差** | 中高 | `MenuImpl.java:84-121,384-389`；演示 `DialogsAndContainersTestActivity.java:1593-1594`；R3/R9 |
| 223-229 | `historySheet.destroy()`、`modelSheet.destroy()`、`welcome.destroy()`、`toBottom.destroy()`、`page.destroy()` | — | 页级组件配对正确；不含 Menu/composer（见上） | 默认 | — | `AiHistorySheet.java:96-103` 等 |

**小结**：默认 5 / 非默认 3 / 显式默认值 0（并入 186-200 行）/ 偏差 2（嵌套、Menu）/ 绕过 1。

### 3.4 `ui/ai/chat/AiComposerView.java`（2 行）

| 行号 | 调用 | 实参/覆盖 | 差异 | 判定 | 风险 | 证据 |
|---|---|---|---|---|---|---|
| 90 | `YoFonts.applyRegular(input)` | — | 字体令牌唯一一处库调用，正确 | 默认 | — | `YoFonts.java:49-51` |
| 75-135 | 裸 `EditText`（16sp、自定 padding/描边底）+ 裸 `ImageView` 发送/停止 + `LinearLayout` 模型 chip | 替代 `YoTextInput`/`YoButton` | 无 `YoTextInput` 多行能力可复用；发送按钮用 `setBackground(style.pressable(...))` 自绘按压态，失去 `YoButton` 的 role/loading/无障碍语义 | 绕过 | 中 | `AiComposerView.java:75-135,220-248` |

**小结**：默认 1 / 非默认 0 / 显式默认值 0 / 偏差 0 / 绕过 1。

### 3.5 `ui/ai/chat/AiHistorySheet.java`（8 行）

| 行号 | 调用 | 实参/覆盖 | 差异 | 判定 | 风险 | 证据 |
|---|---|---|---|---|---|---|
| 50 | `YoList.create(ctx).setOnItemClickListener(...).build()` | — | 默认 | 默认 | — | `YoList.java:44-47` |
| 51-58 | `YoButton.create().setIcon().setStyle(TEXT).setShape(CIRCULAR).setRole(WARNING).setSize(SMALL).setOnClickListener().build()` | TEXT/CIRCULAR/WARNING/SMALL | 默认 PRIMARY/CAPSULE/MEDIUM | 非默认 | 低 | `ButtonConfig.java:205-208` |
| 70-82 | `YoSheet.create().setTitle().setContent(list).setHeight(large()).setBackdropMode(SOLID).setTrailingAction(clear).setListener(...).build()` | 单档 large；SOLID | 默认双档 medium+large、backdrop `CACHED` | 非默认（SOLID 有文档依据） | 低 | `SheetBuilder.java:59-60,77`；`ai-architecture.md:189` |
| 83/86-90 | `sheet.show()` / `sheet.dismiss()` | — | 默认 | 默认 | — | `YoSheet.java:507-517` |
| 96-103 | `sheet.destroy()`、`list.destroy()`、`clear.destroy()` | — | 页级配对正确 | 默认 | — | `YoSheet.java:543-545`、`YoList.java:605-610` |
| 105-123 | `update()`：`list.clear()` 后全量重建 | — | API 默认；配合行内按钮时出现 R4 | 默认 | — | `YoList.java:182-188` |
| 139-149 | 每行 `YoButton more = create()...build()` 放进 `ListSuffixSpec.custom()` | 每次 update 新建 | 旧按钮不 `destroy()`（`clear()` 前未回收，`destroy()` 也不覆盖）→ 主题/配置监听泄漏 | **偏差** | 中高 | `ListSuffixSpec.java:126-130`；`ThemeComponent.java:20-33`；R4 |
| 109-121、151-159 | `addItem(prefix,text,suffix)` / `addGroupFooter` / 位置→动作映射 | — | 三段式默认；header/footer 也是 item，`actions` 逐条对齐，无错位 | 默认 | — | `ListModel.java:39-46`；`ListAdapter.java:291-317` |

**小结**：默认 5 / 非默认 2 / 显式默认值 0 / 偏差 1 / 绕过 0。

### 3.6 `ui/ai/chat/AiModelSheet.java`（5 行）

| 行号 | 调用 | 实参/覆盖 | 差异 | 判定 | 风险 | 证据 |
|---|---|---|---|---|---|---|
| 43 | `YoList.create(ctx).setOnItemClickListener(...).build()` | — | 默认 | 默认 | — | `YoList.java:44-47` |
| 53-64 | `YoSheet.create()...setHeight(large()).setBackdropMode(SOLID)...build()` | 同 3.5 | 默认双档 + CACHED | 非默认 | 低 | `SheetBuilder.java:59-60,77` |
| 78-84 | `sheet.destroy()`、`list.destroy()` | — | 配对正确 | 默认 | — | — |
| 86-109 | `update()`：clear + `addGroupHeader`/`addItem`/`addGroupFooter` | — | 默认三段式；无行内库组件，无泄漏 | 默认 | — | `ListModel.java:39-46` |
| 121-129 | 位置→动作映射 | — | 与 addX 顺序一一对齐 | 默认 | — | `ListAdapter.java:291-317` |

**小结**：默认 4 / 非默认 1 / 显式默认值 0 / 偏差 0 / 绕过 0。

### 3.7 `ui/ai/chat/AiWelcomeView.java`（5 行）

| 行号 | 调用 | 实参/覆盖 | 差异 | 判定 | 风险 | 证据 |
|---|---|---|---|---|---|---|
| 93 | `YoCard.create(ctx).setContentView(setupContent).build()` | — | 默认 | 默认 | — | `YoCard.java:31,71` |
| 174-177 | `YoButton.create().setText().setOnClickListener().build()` | 未改样式 | 默认 PRIMARY/CAPSULE/MEDIUM | 默认 | — | `ButtonConfig.java:205-208` |
| 185-195 | `YoButton.create().setText().setIcon().setStyle(OUTLINED).setSize(SMALL)...build()` | OUTLINED/SMALL | 默认 PRIMARY/MEDIUM | 非默认 | 低 | 同上 |
| 119-122、171-173、204-213 | 切换模式/销毁时 `destroy()` 旧建议按钮、`setupHost.destroy()` | — | 生命周期配对正确（同文件示范了 R4 应有的做法） | 默认 | — | `AiWelcomeView.java:119-122,204-213` |
| 64-107 | 裸 `ScrollView`/`LinearLayout`/`GridLayout`/`TextView` 排版与文案 | 替代库文本组件 | 库无文本/网格组件；排版需要自绘 | 绕过 | 低 | `AiWelcomeView.java:64-107`；`AiViews.java:30-68` |

**小结**：默认 3 / 非默认 1 / 显式默认值 0 / 偏差 0 / 绕过 1。

### 3.8 `ui/ai/chat/AiAssistantMessageView.java`（1 行）与 `AiUserMessageView.java`（1 行）

| 文件:行 | 调用 | 差异 | 判定 | 风险 | 证据 |
|---|---|---|---|---|---|
| `AiAssistantMessageView.java:28-226` | 无直接 YoUI import；全裸 `LinearLayout`/`TextView`/`ImageView`/`MarkdownView`；字色来自 `AiStyle`，字族来自 `AiViews`→`YoFonts` | 聊天消息特化区域，库无组件；但操作行按钮（复制/重试/删除）是裸 `ImageView`+`pressableCircle`，非 `YoButton` | 绕过 | 中 | `AiAssistantMessageView.java:196-226`；`AiViews.java:57-68` |
| `AiUserMessageView.java:17-44` | 裸 `FrameLayout`+`TextView` 气泡 | 同上；`ui-design.md:5.1` 明确气泡自绘 | 绕过 | 低 | `AiUserMessageView.java:29-36`；`ui-design.md:87` |

**小结**：默认 0 / 非默认 0 / 显式默认值 0 / 偏差 0 / 绕过 2。

### 3.9 `ui/ai/settings/AiSettingsActivity.java`（8 行）

| 行号 | 调用 | 实参/覆盖 | 差异 | 判定 | 风险 | 证据 |
|---|---|---|---|---|---|---|
| 49 | `YoSystemBar.prepareTransparentSystemBars(this)` | — | 与演示一致 | 默认 | — | `YoSystemBar.java:27-29` |
| 66-73 | `YoPage.create().setTitle().setTitleMode(MINI).setLevel(SECONDARY).setOnBackListener(finish).setContent(scroll).build()` + `setContentView(page.getView())` | SECONDARY | 模板与 `PageTestActivity` 一致；MINI 是默认值；SECONDARY 让返回键出现（二级页正确） | 默认 | — | `PageTestActivity.java:51-78,264-271`；`PageConfig.java:111-113` |
| 75-85 | 两个 Activity 域 observer（`observe(this, ...)`） | — | 生命周期安全 | 默认 | — | AndroidX |
| 78 | `YoToast.show(this, ...)` | Activity | 默认 | 默认 | — | `YoToast.java:57` |
| 89-100 | `modelSection/platformSection/chatSection/dataSection.destroy()`、`page.destroy()` | — | 配对正确 | 默认 | — | `AiListSection.java:101-103` |
| 104-175 | `render()`：每次 `uiState` 变化 `clear()` 4 个 section 再全量 `add()` | — | 演示是一次性 build；全量重建会丢滚动位置/焦点、频繁创建视图 | 非默认 | 低中 | `AiSettingsActivity.java:106-109,126-175` |
| 251-259 | `YoMenu.create().setAnchorView().setPlacement(AUTO).setSelectedMark(CHECK).setOnItemClickListener().build()` | AUTO/CHECK | 默认 BOTTOM_LEFT/NONE；**无 destroy** | **偏差** | 中高 | `MenuBuilder.java:97`；`MenuImpl.java:384-389`；R3 |
| 260-263 | `menu.setSelectedItemId(selected)`、`menu.show()` | build 后改 | 库支持，非演示写法 | 非默认 | 低 | `MenuImpl.java`；R9 |

**小结**：默认 5 / 非默认 2 / 显式默认值 0 / 偏差 1 / 绕过 0。

### 3.10 `ui/ai/settings/AiPlatformActivity.java`（8 行）

| 行号 | 调用 | 实参/覆盖 | 差异 | 判定 | 风险 | 证据 |
|---|---|---|---|---|---|---|
| 60 | `YoSystemBar.prepareTransparentSystemBars(this)` | — | 与演示一致 | 默认 | — | 同上 |
| 63-68 | `YoTextInput.create().setPlaceholder().setLeadingIcon().setShowCancel(true).setOnValueChangeListener().build()` | 显式交互项 | 默认样式/尺寸，交互项合法 | 默认 | — | `YoTextInput.java:44-131` |
| 89-96 | `YoPage.create().setTitle("").setTitleMode(MINI).setLevel(SECONDARY).setOnBackListener(finish).setContent(content).build()` + `setContentView` | — | 模板一致 | 默认 | — | `PageTestActivity.java:51-78` |
| 98-106 | observers；101-103 `YoToast.show` | — | 默认 | 默认 | — | `YoToast.java:57` |
| 110-123 | `search.destroy()`、3 个 section `destroy()`、`page.destroy()` | — | 配对正确（搜索框是页内长生命周期组件，必须显式销毁） | 默认 | — | `YoTextInput.java:131` |
| 127-144 | `render()`：`clear()` 3 个 section 再 `fill()` | — | 同 3.9 全量重建 | 非默认 | 低中 | `AiPlatformActivity.java:140-147` |
| 313-332 | `YoMenu.create().setAnchorView().setPlacement(AUTO).setSelectedMark(CHECK).setOnItemClickListener().build()` → `setSelectedItemId` → `show()` | AUTO/CHECK；build 后改；**无 destroy** | 默认值偏离 + 生命周期缺失 | **偏差** | 中高 | R3/R9 |
| 137-138、130、320 | `page.setTitle/setSubtitle`、`YoToast.show` | — | 默认 API | 默认 | — | `YoPage.java:561-564` |

**小结**：默认 6 / 非默认 1 / 显式默认值 0 / 偏差 1 / 绕过 0。

### 3.11 `ui/ai/settings/AiListSection.java`（5 行）

| 行号 | 调用 | 实参/覆盖 | 差异 | 判定 | 风险 | 证据 |
|---|---|---|---|---|---|---|
| 36-43 | `YoDimens.TEXT_SIZE_BODY`、`SPACING_*`、`YoColors.of(...).textTertiary()`、`YoList.create(...).build()` | 令牌 + 默认列表 | 令牌使用正确（dp 值已乘 density）；列表默认 | 默认 | — | `YoDimens.java:99-113,56-69` |
| 39 | `YoList.create(context).setOnItemClickListener(...).build()` | — | 默认 | 默认 | — | `YoList.java:44-47` |
| 42、120 | `YoColors.of(context)`（每次现取快照） | — | 默认语义 | 默认 | — | `YoColors.java:21-30` |
| 72、81、101-103 | `list.clear()`、`addItem(prefix,text,suffix)`、`list.destroy()` | — | 默认 | 默认 | — | `YoList.java:62,188,610` |
| 116-127 | `label()`：裸 `TextView` + `YoFonts` + `YoColors` | 替代文本组件 | 库无文本组件；令牌正确 | 绕过 | 低 | `AiViews.java` 同类 |

（非调用点说明：`AiListSection.java:24-28` 注释"标题在列表外面，不使用分段列表"是有意设计，对应 `ui-design.md:94` 的分组标题在卡片外，不计入调用行。）

**小结**：默认 4 / 非默认 0 / 显式默认值 0 / 偏差 0 / 绕过 1（合计 5 行）。

### 3.12 `ui/ai/settings/AiAddPlatformDialog.java`（6 行）

| 行号 | 调用 | 实参/覆盖 | 差异 | 判定 | 风险 | 证据 |
|---|---|---|---|---|---|---|
| 40-48 | `YoSegmentButton.create().setType(SINGLE_CHOICE).addSegment(...).setSelectedIndex(0).setOnSelectionChangeListener().build()` | 显式写默认类型 | 默认即 SINGLE_CHOICE（`SegmentConfig.java:103`） | 显式默认值 | 低 | `SegmentConfig.java:103-109` |
| 54-59、111-118 | `YoTextInput.create().setPlaceholder().setInputType().setMaxLength().setShowCancel(true).setShowErrorEnabled(true).build()` ×3 | 显式交互项 | 默认样式 | 默认 | — | `YoTextInput.java:44-131` |
| 60 | `YoDimens.SPACING_LARGE * density` | dp→px | 正确换算（`YoDimens` 是 dp 数值） | 默认 | — | `YoDimens.java:5-8` |
| 72-103 | `YoDialog.create().setTitle().setContentView(scroll).setNegativeButton().setPositiveButton().setOnWillDismissListener().setOnDismissListener().show()` | Kind.STANDARD | 默认 kind；`Builder.show()` 内部 `showThenDestroy` 自动 destroy | 默认 | — | `DialogImpl.java:834-848` |
| 96-103 | `onDismiss` 中 `type/name/url/key.destroy()` + `hideKeyboard` | — | **必要**：这些组件在 `setContentView` 里，不属 hosted，库不托管 | 默认 | — | `DialogPresets.java:100-113` 只托管 hosted |
| 96-103 | 同上：销毁依赖 `OnDismissListener` | — | 若对话框未 dismiss 而 Activity 先销毁，`DialogImpl.destroy()` 置空 listener 后不回调（见 `DialogImpl.java:442-455`），4 个组件漏 destroy（`DialogOwner` 只 destroy dialog） | **偏差**（边界） | 中低 | `DialogOwner.java:125-131`；R7 |

**小结**：默认 4 / 非默认 0 / 显式默认值 1 / 偏差 1 / 绕过 0。

### 3.13 `ui/ai/widget/AiDialogs.java`（9 行）

| 行号 | 调用 | 实参/覆盖 | 差异 | 判定 | 风险 | 证据 |
|---|---|---|---|---|---|---|
| 60-66 | `YoDialog.Action.of(...)`、`.alert()`、`Action.negative(...)` | — | 默认 API | 默认 | — | `YoDialog.java:431-466` |
| 67-72 | `YoDialog.create().kind(ACTION_SHEET).setTitle().setActions().build().show()` | 显式 kind | `ACTION_SHEET` 是合法 Kind（底部动作列表） | 默认 | — | `YoDialog.java:29-35` |
| 102-110 | `YoDialog.create().setTitle().setMessage().setActions(...).build().show()` | 默认 STANDARD | 默认 | 默认 | — | `DialogConfig.java` 默认 |
| 121-128、138-145 | `YoTextInput.create()...build()` 交给 `input()` | — | 默认 | 默认 | — | `YoTextInput.java:44` |
| 149-177 | `YoDialog.create().kind(INPUT).hosted(field).setTitle().setNegativeButton().setPositiveButton().setOnWillDismissListener().setOnDismissListener()` → `show()` | hosted | hosted 组件由 `DialogPresets.ownHosted` 托管，`Builder.show()` 自动销毁 → 正确 | 默认 | — | `DialogPresets.java:100-113`；`DialogImpl.java:834-848` |
| 185-260 | `editText()`：裸 `EditText`+`TextView` 计数/错误行，`setContentView(content)` | 替代多行输入组件 | `YoTextInput` 仅单行，属于结构性绕过；颜色/字号/字族走令牌 | 绕过 | 中 | `YoTextInput.java:12-16` |
| 281-294 | `YoSlider.create().setMin().setMax().setStep().setValue().setOnProgressChangeListener().build()` | — | 默认 API；但**未 destroy** | **偏差** | 中 | `YoSlider.java:33,258`；R5 |
| 296-308 | `YoDialog.create().setTitle().setContentView(content).setNegativeButton().setNeutralButton().setPositiveButton().show()` | `setContentView`（非 hosted） | dialog 只销毁 hosted 组件，`YoSlider` 不在其中 → 每次打开泄漏（缺陷归入 281-294 一行） | 默认 | — | `DialogImpl.java:442-455` |
| 311-318 | `caption()`：裸 `TextView` + `YoDimens`/`YoFonts` | — | 令牌正确 | 绕过 | 低 | — |

**小结**：默认 6 / 非默认 0 / 显式默认值 0 / 偏差 1（281-294 与 296-308 合并计 1）/ 绕过 2（合计 9 行）。

### 3.14 `ui/ai/widget/AiStyle.java`（2 行）

| 行号 | 调用 | 实参/覆盖 | 差异 | 判定 | 风险 | 证据 |
|---|---|---|---|---|---|---|
| 78-100 | `YoColors.of(context)` + 20+ 令牌读取（palette/snowGray、system().cardBg、text*、brand、textOnPrimary、divider、alert/warning/confirm、ripple、scrim），并用 `setAlphaComponent` 派生 soft/fill | 只读令牌 | 令牌读取方式正确（快照当次使用；`AiStyle` 随页面重建） | 默认 | — | `YoColors.java:46-97,150-167`；`AiStyle.java:75-101` |
| 22-47 | 自建 `SPACE_*`、`RADIUS_*`、`TEXT_*` 常量（15/17/20/22/32 等） | 与 `YoDimens` 并行的第二套阶梯 | `TEXT_BODY=15` ↔ token 14/16；`TEXT_TITLE=17` ↔ 16/18；`RADIUS_FIELD=22` ↔ token 20/24；`SPACE_XXL=32` 无 token | 非默认 | 中低 | `YoDimens.java:99-113,15-37,56-69`；`ui-design.md:40-58` |

**小结**：默认 1 / 非默认 1 / 显式默认值 0 / 偏差 0 / 绕过 0。

### 3.15 `ui/ai/widget/AiViews.java`（2 行）

| 行号 | 调用 | 差异 | 判定 | 风险 | 证据 |
|---|---|---|---|---|---|
| 30-68 | 裸 `TextView`/`ImageView` 工厂（text/icon/iconButton） | 无对应库组件；字族经 `YoFonts`，颜色由调用方传令牌 | 绕过 | 低 | `AiViews.java:30-68` |
| 41-43 | `YoFonts.applyMedium/applyBold/applyRegular` | 默认字族 API | 默认 | — | `YoFonts.java:49-59` |

**小结**：默认 1 / 非默认 0 / 显式默认值 0 / 偏差 0 / 绕过 1。

### 3.16 `ui/ai/widget/MarkdownView.java`（2 行）

| 行号 | 调用 | 差异 | 判定 | 风险 | 证据 |
|---|---|---|---|---|---|
| 191、202、219、493 | `YoFonts.applyBold/applyRegular`、`YoFonts.bold(context)`（用于 `TypefaceSpan`） | 字体令牌用法正确（含行内粗体） | 默认 | — | `YoFonts.java:34-59` |
| 全文 517 行 | 裸 `TextView`/`TableLayout`/`HorizontalScrollView`/`LinkTextView` 渲染 Markdown；代码块用 `monospace`、`RelativeSizeSpan` 等自定排版 | 库无 Markdown/富文本组件，属必要自绘；但富文本样式全部绕开库（库也没有对应令牌） | 绕过 | 中 | `MarkdownView.java:116-517` |

**小结**：默认 1 / 非默认 0 / 显式默认值 0 / 偏差 0 / 绕过 1。

### 3.17 逐文件计数汇总

| 文件 | 默认 | 非默认 | 显式默认值 | 偏差 | 绕过 | 行数合计 |
|---|---:|---:|---:|---:|---:|---:|
| `ui/shell/MainActivity.java` | 8 | 2 | 1 | 2 | 0 | 13 |
| `ui/shell/ShellPageView.java` | 1 | 1 | 0 | 0 | 1 | 3 |
| `ui/ai/chat/AiChatPage.java` | 5 | 3 | 0 | 2 | 1 | 11 |
| `ui/ai/chat/AiComposerView.java` | 1 | 0 | 0 | 0 | 1 | 2 |
| `ui/ai/chat/AiHistorySheet.java` | 5 | 2 | 0 | 1 | 0 | 8 |
| `ui/ai/chat/AiModelSheet.java` | 4 | 1 | 0 | 0 | 0 | 5 |
| `ui/ai/chat/AiWelcomeView.java` | 3 | 1 | 0 | 0 | 1 | 5 |
| `ui/ai/chat/AiAssistantMessageView.java` | 0 | 0 | 0 | 0 | 1 | 1 |
| `ui/ai/chat/AiUserMessageView.java` | 0 | 0 | 0 | 0 | 1 | 1 |
| `ui/ai/settings/AiSettingsActivity.java` | 5 | 2 | 0 | 1 | 0 | 8 |
| `ui/ai/settings/AiPlatformActivity.java` | 6 | 1 | 0 | 1 | 0 | 8 |
| `ui/ai/settings/AiListSection.java` | 4 | 0 | 0 | 0 | 1 | 5 |
| `ui/ai/settings/AiAddPlatformDialog.java` | 4 | 0 | 1 | 1 | 0 | 6 |
| `ui/ai/widget/AiDialogs.java` | 6 | 0 | 0 | 1 | 2 | 9 |
| `ui/ai/widget/AiStyle.java` | 1 | 1 | 0 | 0 | 0 | 2 |
| `ui/ai/widget/AiViews.java` | 1 | 0 | 0 | 0 | 1 | 2 |
| `ui/ai/widget/MarkdownView.java` | 1 | 0 | 0 | 0 | 1 | 2 |
| **合计** | **55** | **14** | **2** | **9** | **11** | **91** |

---

## 4. 判定汇总

### 4.1 合格项（无需修改）

1. **import 边界**：59 条 import 全部 `api.*`，0 违规（§2.1）。
2. **宿主窗口步骤**：3/3 Activity 调 `prepareTransparentSystemBars`；3/3 窗口壳在 `onDestroy` 里 `destroy()`；`YoTheme` 不调用（= 默认 `FOLLOW_SYSTEM`）。
3. **组件默认调用**：`YoList`/`YoSheet`/`YoPage`/`YoTabs`/`YoCard`/`YoTextInput`/`YoSegmentButton`/`YoSlider` 的 `create→configure→build→getView/show` 全部走库 Builder；`YoPage`/`YoSheet`/`YoList`/`YoTabs` 的 `destroy()` 配对；`YoToast` 全部传 Activity；`YoDialog.Builder.show()` 依赖库的 `showThenDestroy` 自动清理（无需宿主 destroy）。
4. **`YoDialog.hosted(...)` 用法正确**：`AiDialogs.input/inputWithReset` 用 hosted，组件由 `DialogPresets.ownHosted` 托管（`DialogPresets.java:100-113`），宿主不用手动 destroy —— 这正是与 R5 的对照面。
5. **`YoColors` 快照语义正确**：`MainActivity` 局部变量、`AiListSection`/`AiDialogs` 每次现取、`AiStyle` 随页面重建；没有把 `YoColors` 跨色模式缓存成静态/长生命周期字段。
6. **`YoSheet`/`YoList` 的三段式与 click 位置映射正确**：`actions` 与 `addItem/addGroupHeader/addGroupFooter` 严格一一对应，失配风险已排除（`ListModel.java:39-46` + `ListAdapter.java:291-317`）。

### 4.2 偏差 / 低风险项（建议修，按严重度）

| 编号 | 偏差 | 位置 | 级别 | 说明 |
|---|---|---|---|---|
| R1 | 窗口级壳嵌套（`YoTabs`⊃`YoPage`） | `AiChatPage.java:186-200` + `MainActivity.java:60-61,65` | 高 | 见 §5 |
| R2 | `MainActivity` 顶部 inset 无 owner | `MainActivity.java:27-31,63-64`（缺失） | 高 | 见 §5 |
| R3 | 3 处 `YoMenu` 未 destroy | `AiChatPage.java:488-508`；`AiSettingsActivity.java:251-263`；`AiPlatformActivity.java:313-332` | 中高 | 见 §5 |
| R4 | 历史行内 `YoButton` 泄漏 | `AiHistorySheet.java:105-123,139-149` | 中高 | 见 §5 |
| R5 | `YoSlider` 未 destroy（非 hosted） | `AiDialogs.java:281-294,302-308` | 中 | 见 §5 |
| R7 | `AiAddPlatformDialog` Activity 先销毁时组件不释放 | `AiAddPlatformDialog.java:96-103` | 中低 | 见 §5 |
| R9 | build 后再改（3 处） | `AiChatPage.java:501-507`；`AiSettingsActivity.java:259-262`；`AiPlatformActivity.java:330-332` | 低中 | 非默认，库支持，非演示写法 |
| R12 | `PageNavPad` 覆写滚动容器 padding | `AiChatPage.java:86` × `PageNavPad.java:41-50` | 低 | 非默认，自定 8dp 被 nav 高度替换 |

> 计数说明：逐文件表里"偏差"共 9 行，唯一问题 6 个——R1（`YoTabs`⊃`YoPage`）在 `MainActivity`（挂载点）与 `AiChatPage`（构造点）各记 1 行；R3（`YoMenu` 未 destroy）在 3 个文件各记 1 行，属同一类问题。表中 R9/R12 在逐文件表里归为"非默认"，此处一并列出仅为修复排序，不计入偏差数。

### 4.3 非默认项（设计选择 / 有文档依据，保留但需登记）

- 视觉令牌：`AiStyle` 第二套尺寸阶梯、`snowGray(1)` 页底色（§2.6）。
- 组件样式：`YoButton` 的 CIRCULAR/TEXT/OUTLINED/SMALL、`YoSheet` 的 SOLID 单档、`YoMenu` 的 AUTO/CHECK。
- 结构性绕过：聊天 RecyclerView、输入区、Markdown、多行编辑（§2.7）。
- 全量重建：两个设置页每次 `uiState` 变化清空重建列表。
- 显式写默认值 2 处（`setBarStyle(FLOATING)`、`setType(SINGLE_CHOICE)`）及 `YoPage` 的 MINI/PRIMARY/3。

---

## 5. Top 风险详解与修复建议

### R1（高）`YoTabs` 内嵌 `YoPage`

- **事实**：`MainActivity.createTabs()` 里把 `new AiChatPage(...).view()` 作为 tab content（`MainActivity.java:57-65`），而 `AiChatPage.view()` 返回的是 `YoPage.getView()`（`AiChatPage.java:186-213`）。
- **库契约**（两处独立）：
  - `YoTabs.java:16-20`：Activity content 就是 `tabs.getView()`，**不要再包一层 YoPage**（两者都是窗口级铬层，嵌套会把底栏顶出可视区）。
  - `YoPage.java:47-49`：**不要**把窗口级壳（YoTabs）塞进 page content；应用级底栏用 YoTabs 做 Activity content。
- **源码后果（推断，需真机确认）**：
  1. 两个宿主各自注册 `OnApplyWindowInsetsListener`（`PageInsets.java:33-38` 与 `TabsWindowInsetBinder.java:35-37`），inset 在嵌套子树被消费两次；两者都返回 `insets.inset(left,0,right,0)`，top/bottom 透传，但**左右 padding 会同时落在 Tabs host 和 Page host** → 横屏/挖孔下水平内边距翻倍（推断）。
  2. YoTabs 的"自动给页面 scroller 垫 contentInset"失效：`findVerticalScroller` 只接受页面自身或单层子 View（`TabsScrollBinder.java:99-118`），而 `YoPage.getView()` 是多子 View 的自定义宿主 → 不会被识别；本 App 靠 `OnContentInsetListener` 手工补偿（`MainActivity.java:72-73`），所以**当前功能可用**，但这是"绕开库自动机制"的补丁。
  3. `YoPage` 的 `PageNavPad` 会写聊天 `RecyclerView` 的 `paddingBottom`（`PagePresenter.java:406-410`），与 App 自设的 8dp 和输入区的 `max(ime,tabsInset)` 叠加成三层底部避让，语义混乱（推断）。
- **修复**：二选一，推荐 A。
  - **A（符合库契约）**：`MainActivity` 只保留 `YoTabs` 做窗口根；AI tab 内容改为普通 View（推荐 `LinearLayout`：轻量自绘标题行（AI/当前模型/菜单入口）+ `RecyclerView` + 输入区）。历史/设置入口移到页面内 header 或 `YoTabs` 运营位（`setBleedItemId`/菜单）。需要新写一个 title row，但去掉 `YoPage` 后左右/顶部 inset 归属重新变清晰。
  - **B（保留 YoPage，去掉 Tabs）**：改 `YoPage` 做每个 Activity 的根，底部导航改成页内固定栏或 `YoSegmentButton.TABS`；但库的"应用级底栏"语义就是 YoTabs，B 会牺牲底栏默认观感。
- **验收**：横屏+刘海机型截图对比左右边距；`dumpsys window` 看 status bar 区域无内容压入；`tabs.getContentInsetPx()` 与输入区实际 padding 一致。

### R2（高）`MainActivity` 顶部状态栏 inset 无 owner

- **事实**：`MainActivity.java:27-31` 只做 edge-to-edge；`:63-64` 只创建 `ShellPageView`；全文件无 `padViewForSystemBars`/`systemBarsAndCutout`/自注册 listener。
- **库事实**：`YoTabs` 的 inset binder 只取 left/right/bottom，host padding 的 top 恒 0（`TabsWindowInsetBinder.java:88-99`、`TabsHostBindings.java:125-132`）。
- **演示事实**：`TabsTestActivity.java:116-119` 给根 `setPadding(bars.left, bars.top, bars.right, 0)`。
- **风险**：当前 `ShellPageView` 只有居中占位文字，事故不明显；但任何真实 tab 内容（顶部标题/滚动列表）都会压到状态栏。且 AI tab 因内嵌 YoPage 而"看起来正常"，掩盖了缺口。
- **修复（F2）**：在 `MainActivity` 里包一层容器（不要直接对 `tabs.getView()` 调 `padViewForSystemBars`，它会覆盖 YoTabs 的 inset listener，见 `SystemBarUtils.java:204`）：

  ```java
  FrameLayout root = new FrameLayout(this);
  root.addView(tabs.getView(), MATCH, MATCH);
  setContentView(root);
  ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
      Insets bars = YoSystemBar.systemBarsAndCutout(insets);
      v.setPadding(bars.left, bars.top, bars.right, 0); // bottom 交给 YoTabs
      return insets;
  });
  ```

  注意：YoTabs 已经处理 left/right/bottom，这里只需 top；若同时设置 left/right 会与其叠加（推断），建议只写 top（`v.setPadding(0, bars.top, 0, 0)`）并真机验证。

### R3（中高）三处 `YoMenu` 未 destroy

- **事实**：`AiChatPage.onLongPress` 每次长按新建菜单（`:488-508`）；`AiSettingsActivity.showChoices`（`:249-263`）、`AiPlatformActivity.chooseProtocol`（`:311-332`）每次选择新建菜单。三者都没有持有引用，也没有 `destroy()`。
- **库契约**：`MenuImpl.destroy()` 是解除 `ThemeComponent.bind` + `popup` + `controller` 监听的唯一入口（`MenuImpl.java:384-389`）；不调则 `ThemeController` 静态 listener 列表持有 `Binding→View→Activity`（`ThemeComponent.java:20-33`、`ThemeBind.java:24,55-68`）。菜单不像 `YoDialog` 有 Activity 兜底（`DialogOwner` 只兜 Dialog）。
- **演示做法**：`DialogsAndContainersTestActivity` 用 `sectionMenus` 列表统一收集，`onDestroy` 里逐个 `destroy()`（`:1593-1594`）。
- **修复**：三种方案任选：
  1. 页面持有 `@Nullable YoMenu activeMenu`，show 前 `destroy()` 旧的，`onDestroy`/`onPause` 里 destroy；
  2. 改用库的绑定式 API `YoMenu.bindMenu(host, builder -> ...)` / `bindContextMenu(...)`（库自己管理 popup 生命周期）；
  3. 至少给每次创建加 `setOnDismissListener(() -> menu.destroy())`（注意：`destroy()` 要能拿到实例，需字段或数组持有）。
- **验收**：反复长按/选择 50 次后，`dumpsys meminfo` 无持续增长；`ThemeController` 监听数量无增长（可加调试计数）。

### R4（中高）`AiHistorySheet` 行内 `YoButton` 泄漏

- **事实**：`moreSuffix()` 每次调用都 `YoButton.create(...).build()`（`:139-149`），`update()` 每次先 `list.clear()` 再为新行重新创建（`:105-123`）；`destroy()` 只销毁 `list` 和页级 `clear` 按钮（`:96-103`）。
- **后果**：每刷新一次历史列表，旧 N 个 `YoButton` 的 `ButtonView` 永久留在 `ThemeBind.BINDINGS`/`ThemeController`（`ThemeComponent.java:20-33`），且 `ButtonConfig` 生命周期不会自动释放。历史面板每次打开/更新都会触发 `update()`（`AiChatPage.java:304-308`）。
- **对照正确写法**：同 App 的 `AiWelcomeView` 在 `setMode`/`destroy` 里显式销毁旧按钮（`:119-122,171-173,205-213`）。
- **修复**：在 `AiHistorySheet` 内维护 `List<YoButton> rowButtons`；`update()` 开头先 `for (b : rowButtons) b.destroy(); rowButtons.clear();` 再 `list.clear()`，`moreSuffix()` 里把新按钮加入列表；`destroy()` 里同样清空。或干脆去掉行内 more 按钮，改为 `ListSuffixSpec.arrow()` + 行长按菜单（更少组件、更符合列表默认语义）。

### R5（中）`AiDialogs.slider` 的 `YoSlider` 未销毁

- **事实**：`YoSlider.create(...).build()`（`:281-290`）放进 `LinearLayout content`，通过 `YoDialog.setContentView(content)` 呈现（`:302-308`）。
- **库契约**：只有 `hosted(...)` 的组件会被 `DialogPresets.ownHosted` 托管，`dialog.destroy()` 时统一销毁（`DialogPresets.java:100-113`、`DialogImpl.java:442-455`）；`setContentView` 不托管。
- **后果**：每次打开温度滑块对话框泄漏一个 `YoSlider`（含其主题监听）。对比 `AiDialogs.input` 用 `hosted(field)` 是正确写法（`:153-155`）。
- **修复**：给 dialog 加 `setOnDismissListener(() -> slider.destroy())`（需要把 slider 提为局部 final 并在 listener 中引用），或改为 `builder.setBody(YoDialog.Body.hosted(slider.getView()))`/`hosted(slider)`（若 API 允许 hosted(Slider)，当前 `YoDialog` 只重载了 `View/YoList/YoTextInput/YoLoadingProgress`，因此用 `hosted(slider.getView())` 不会托管组件本身，仍需手动 destroy）。

### R6（中）裸控件绕过 YoUI 的系统性代价

- **事实**：聊天三件套（消息列表、输入区、Markdown）全部自绘（§2.7）；`AiStyle` 又自建了一套尺寸/字号阶梯（§2.6）。
- **代价**：按压态/禁用态/水波纹/加载态/无障碍 role 全部自实现（`AiComposerView.java:220-248`、`AiAssistantMessageView.java:196-226`、`AiViews.java:57-68`），与库组件观感容易漂移；字号不随系统字体缩放（大量 `setTextSize(SP)` 固定值）；`AiStyle` 与 `YoDimens` 两套口径会让后续维护"以哪套为准"失焦。
- **建议（不要求重写）**：
  1. 在 `ui-design.md`/`ai-architecture.md` 里明确"聊天/Markdown 为有意绕过区"，避免后续审计重复判定；
  2. 把 `AiStyle` 的尺寸常量收敛为对 `YoDimens` 的映射（能对齐的对齐，确实要新档位的集中在一个 `AiTokens` 并注释理由），去掉 15/17 这类夹在 token 中间的档位；
  3. 消息操作行、发送/停止按钮如果后续需要加载态/禁用态/无障碍一致性，优先换回 `YoButton`（图标按钮可用 `setIcon + CIRCULAR + SMALL`，即已存在的库能力）。

### R7（中低）`AiAddPlatformDialog` 的"Activity 先销毁"边界

- **事实**：4 个库组件（Segment + 3×TextInput）在 `setOnDismissListener` 里销毁（`:96-103`），但 `YoDialog` 的 Activity 兜底路径是 `DialogOwner.onDestroy → dismissAndDestroyAll → dialog.destroy()`（`DialogOwner.java:125-131`），而 `DialogImpl.destroy()` 先把 `dismissListener = null` 再拆树（`DialogImpl.java:442-455`）——**不会回调宿主监听器**。
- **后果**：对话框打开时 Activity 被销毁（返回/进程回收），4 个组件不会 destroy（主题监听残留到进程结束）。频率低，但与"destroy 必须"的契约不符。
- **修复**：在 `show()` 返回的 `YoDialog` 上持有引用，`Activity.onDestroy` 里先 `dialog.destroy()`；或者把 4 个组件装进一个 `YoList`/容器并用 `hosted(...)` 托管（`YoDialog` 会统一销毁）；至少把 `destroy()` 同时挂到 `setOnDismissListener` 和 `Activity.onDestroy` 两条路径（用幂等标记防重复）。

### R8（低中）`YoSheet` 的默认值覆盖

- **事实**：两个面板都 `setBackdropMode(SOLID)` + `setHeight(Height.large())`（`AiHistorySheet.java:73-74`、`AiModelSheet.java:56-57`）。
- **库默认**：backdrop `CACHED`（`SheetBuilder.java:77`），detents `[medium, large]`（`SheetBuilder.java:59-60`）。
- **判定**：SOLID 已有文档依据（`ai-architecture.md:189`，为避免面板内容透出）；**单档 large 没有记录**——它去掉了默认的 medium 档，用户不能半屏看历史/模型。如果是有意的（内容较长），建议在 `ai-architecture.md` 补一行；否则改回不设置 `setHeight`（默认双档）或 `setDetents(medium, large)`。

### R9/R10/R11/R12（低）

- **R9 build 后修改**：三处（§2.5）。建议统一改成"先 add/set 再 build"，与演示一致，避免后续有人误以为 build 之后不可变。
- **R10 全量重建**：两个设置页每次状态变化清空 4 个/3 个 section 再重建（`AiSettingsActivity.java:106-109`、`AiPlatformActivity.java:140-147`）。当前列表规模小，功能可接受；但滚动位置与开关焦点会丢，且每次刷新创建大量 View。建议按 `uiState` 做差分（列表项 id 稳定）或至少保存/恢复滚动位置。
- **R11 显式默认值**：`setBarStyle(FLOATING)`（`MainActivity.java:52`）、`TitleMode.MINI/Level.PRIMARY/setMenuMaxCount(3)`（`AiChatPage.java:189-191`）、`SegmentType.SINGLE_CHOICE`（`AiAddPlatformDialog.java:41`）。无行为差异，建议删除以免误导"已定制"。
- **R12 `PageNavPad` 覆写 padding**：`messages.setPadding(0, dp4, 0, dp8)`（`AiChatPage.java:86`）的 bottom 会被 `PageNavPad` 覆盖（`PageNavPad.java:41-50` 是 `setPadding` 不是累加）。若希望保留 8dp，需要用外层容器 padding 或把 8dp 放进 item 间距。

---

## 6. 未证实 / 需真机验证

1. **R1 的视觉后果**（双倍左右 inset、三层底部避让）目前是源码推断：需要横屏 + 刘海/挖孔机型，对比 `AiChatPage` 左右边距、输入区到栏距离，并 `dumpsys window` 查 status bar 区内容。库 javadoc 所说"嵌套会把底栏顶出可视区"本 App 未复现（因为 AI tab 的 YoPage 高度受 Tabs content slot 约束）。
2. **R2 的当前视觉影响**：`ShellPageView` 是占位页，暂无可感知事故；需要放入真实内容后验证。
3. **R12 的实际覆盖值**：`navigationProtectionBottom` 在 3 键/手势导航下可能为 0 或 >0（`TabsWindowInsetBinder.java:88-99`），决定 8dp 是否真的被覆盖为 0。
4. **R7 的触发条件**：仅在对话框打开时 Activity 被销毁才发生；静态路径已核实（`DialogImpl.java:442-455` 置空 listener），未做运行时复现。
5. **`AiStyle` 颜色在系统夜间切换时的刷新**：依赖 Activity 重建（manifest 未声明 `configChanges`）；若后续为 MainActivity 加 `configChanges="uiMode"`，`AiStyle`/`ShellPageView` 的已缓存颜色会 stale（`YoColors` 是快照，`YoColors.java:21-30`）。
6. 本文的"默认值"以库源码为准；若后续库升级，`ButtonConfig`/`SheetBuilder`/`PageConfig` 的默认值可能变化，需重跑本审计。

---

## 7. 证据文件索引

- App（相对耳机仓根）：`app/src/main/java/com/ggec/earbuds/ui/shell/{MainActivity,ShellPageView}.java`、`app/src/main/java/com/ggec/earbuds/ui/ai/{chat,settings,widget}/*.java`、`app/build.gradle`、`app/src/main/AndroidManifest.xml`、`app/src/main/res/values/themes.xml`、`app/doc/{ui-design,ai-architecture,youi-contract-audit,youi-aar-verification}.md`
- 库（相对 Android-YoUI 根）：`youi_kit/src/main/java/com/yohu/youi/api/{containers,navigation,platform,theme,controls,feedback}/*.java`、`youi_kit/src/main/java/com/yohu/youi/widget/{containers/page,widget/containers/list,widget/containers/sheet,widget/navigation/tabs,widget/controls/button,widget/controls/textinput,widget/feedback/dialog,widget/navigation/menu}/*.java`、`youi_kit/src/main/java/com/yohu/youi/{theme,core/lifecycle}/*.java`
- 演示（相对 Android-YoUI 根）：`app/src/main/java/com/yohu/youidemo/{MainActivity.java,ui/home/HomeFragment.java,uitest/{TabsTestActivity,PageTestActivity,DialogsAndContainersTestActivity}.java}`、`app/src/main/res/{layout/activity_main.xml,values/themes.xml}`
