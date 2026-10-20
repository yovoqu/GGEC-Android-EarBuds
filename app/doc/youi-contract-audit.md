# YoUI 库侧默认/标准调用契约审计

> **2026-10-19 16:00 之后库源码已有补丁，本审计的“列表 Ripple / 输入框”部分不再代表当前行为。**
> 补丁内容与原因见 [youi-kit-patch-2026-10-19.md](youi-kit-patch-2026-10-19.md)：独立列表 Ripple 改为由自身板材半径推导；
> `YoTextInput` 增加多行模式。其余审计结论（页面、对话框、主题令牌等）仍有效。

> 审计对象：`D:\A_yoprogram\Github_Gallery\Android-YoUI`（只读，未做任何修改）
> 审计范围：`youi_kit` 源码（`com.yohu.youi.api` 全量 122 个文件 + 关键内部实现）、`app` 演示模块、`docs/`、`app/build.gradle` 的 `verifyAppSdkBoundary`、AAR 产物
> 用途：作为判定「耳机 App 是否按库的默认方式调用」的基准
> 证据格式：`文件:行`，路径相对 Android-YoUI 仓库根；`youi_kit/...` 均指 `youi_kit/src/main/java/com/yohu/youi/...`（除非写明 res/manifest）
> 方法：read + grep + glob 静态阅读；AAR 用只读 zip 解析。**源码中文注释可能乱码，结论一律以代码为准。**

---

## 0. 结论速览（12 条硬契约）

| # | 契约 | 关键证据 |
|---|---|---|
| 1 | **没有任何自动初始化**：库 manifest 为空，无 ContentProvider / androidx.startup Initializer，无 `Application` 子类要求 | `youi_kit/src/main/AndroidManifest.xml:1-4`；全库 grep 无 `ContentProvider|Initializer|androidx.startup` |
| 2 | **不要求特定主题父样式**：库不带 `styles.xml`/主题，代码不读取主题属性（仅 ripple 读 `android.R.attr.state_*`） | `youi_kit/src/main/res/values/` 仅 `ids.xml`；`MotionRippleDrawable.java:134-136` |
| 3 | `YoTheme.setColorMode` **可选**，缺省 `FOLLOW_SYSTEM`；不调用 = 跟随系统 `UI_MODE_NIGHT` | `theme/ThemeController.java:18`、`theme/ThemeResolver.java:25-29` |
| 4 | `YoColors.of(context)` **返回当时的不可变快照**，不是实时视图；换模式后必须重新 `of()` | `api/theme/YoColors.java:21-30`、`theme/ThemeSnapshot.java:9` |
| 5 | `YoSystemBar.prepareTransparentSystemBars`(窗口级) 与 `padViewForSystemBars`(视图级 padding) **分工不同，可且通常都要调**；前者图标明暗取自主题快照 | `api/platform/YoSystemBar.java:27-29,39-41`；`core/utils/SystemBarUtils.java:133-151,203-211` |
| 6 | **YoPage 内部自己吃状态栏顶部与内容底部 nav 区** → 宿主不要再对 YoPage 根 `padViewForSystemBars`，否则双重 padding | `page/internal/platform/PageInsets.java:35,90-103`；`PagePresenter.java:396-410`；`PageNavPad.java:19-31` |
| 7 | YoTabs 默认 **`BarStyle.FLOATING` + `barOverlap=true`**（FLOATING 强制 overlap）；内容避开量用 `getContentInsetPx()`/`OnContentInsetListener` | `tabs/internal/config/TabsConfig.java:28,85-91`；`TabsHostView.java:251-258` |
| 8 | 组件 `destroy()` **语义上必须**（幂等；不调则主题静态监听持有 View→Activity，且销毁后调用其它 API 抛 `IllegalStateException`） | `core/lifecycle/ComponentLifecycle.java:8-9,20-24,35-55`；`theme/ThemeBind.java:24,55-68` |
| 9 | 宿主**只能 import `com.yohu.youi.api.**`（+ kit `R`/`BuildConfig`）**，内部包禁止引用 | `app/build.gradle:59-95`；`youi_kit/build.gradle:241-290` |
| 10 | `YoDialog`/`YoToast` **必须传 Activity context**（否则抛 `IllegalArgumentException`/不显示），Dialog 在 finishing Activity 上抛异常 | `dialog/internal/DialogOwner.java:133-140,72-81`；`ToastImpl.java:442-452` |
| 11 | release AAR **已 R8 minify**（`repackageclasses 'o'`），只 keep `com.yohu.youi.api.**`；实测公开类名全部保留，内部类被改名 | `youi_kit/build.gradle:63-65`；`proguard-rules.pro`；AAR 实测（见 §1.3） |
| 12 | release AAR **不嵌 HarmonyOS Sans**（回落系统 sans-serif），`YoFonts.isBundled()==false` | `youi_kit/build.gradle:5-7,21-22,66`；AAR 的 `res/` 只有 `values/values.xml`（无字体） |

---

## 1. 版本与产物事实

### 1.1 模块身份

| 项 | 值 | 证据 |
|---|---|---|
| 模块 | `:youi_kit`（`com.android.library`） | `youi_kit/build.gradle:1-3`；`settings.gradle:23-24` |
| namespace | `com.yohu.youi` | `youi_kit/build.gradle:34` |
| versionCode / versionName | `100` / `"1.0.0"` | `youi_kit/build.gradle:44-45` |
| minSdk / targetSdk / compileSdk | `24` / `35` / `36` | `youi_kit/build.gradle:35,42-43` |
| Java 级别 | source/target 21 + 全模块 core-library desugaring | `youi_kit/build.gradle:73-76`；根 `build.gradle:11-26` |
| 依赖（implementation，会传递到宿主） | appcompat 1.8.0、core 1.18.0、recyclerview 1.4.0 | `youi_kit/build.gradle:84-93`；`gradle/libs.versions.toml` |
| **不依赖** | Material Components、androidx.startup、activity、constraintlayout | 同上（这些只在演示 app 里，`app/build.gradle:49-52`） |

> 结论：宿主不需要引入 Material；库不带任何 Manifest 组件。

### 1.2 AAR 构建方式

- `youi_kit` 的 release 走 `minifyEnabled true` + `shrinkResources false`（`youi_kit/build.gradle:61-67`）。
- 导出任务 `exportReleaseAar`（`youi_kit/build.gradle:96-133`）：`assembleRelease` → 取 `build/outputs/aar/youi_kit-release.aar` → 重命名为 `youi_kit-${versionName}.aar` 拷到 `releases/`（`:98-111`）。
- **mapping.txt 单独拷出，明确"勿随 AAR 分发"**（`youi_kit/build.gradle:118-127`，`:126`）。
- 字体变体：debug 默认嵌 HarmonyOS Sans（`src/harmonySans`），release 默认用 `src/systemSans`（`youi_kit/build.gradle:21-31,52-59`），可由 `-Pyoui.embedHarmonySans=true/false` 覆盖（`:8-22`）。
- 库自带 4 个校验任务（`verifySelfDevelopedMotion`/`verifyApiBoundary`/`verifyCalleeOwnership`/`verifyPageLayering`），绑定到 `check` 与 `compile*JavaWithJavac`（`youi_kit/build.gradle:404-407`）。

### 1.3 实测产物 `releases/youi_kit-1.0.0.aar`（2,172,144 字节）

zip 条目：`classes.jar`(2,306,178) / `proguard.txt`(734) / `R.txt`(114) / `AndroidManifest.xml`(200) / `res/values/values.xml`(231) / `aar-metadata.properties`。

- `classes.jar` 共 1614 条目，顶层只有 `com/` 与 `o/` 两个包：
  - `com/yohu/youi/api/**` **402 个条目，类名未被混淆**（如实测存在 `com/yohu/youi/api/containers/YoPage.class`、`api/navigation/YoTabs.class`、`api/theme/YoColors.class`、`api/platform/YoSystemBar.class`）；
  - 内部实现被 `-repackageclasses 'o'` 压平成 `o/I00.class` 之类；
  - 未见 `com/yohu/youi/theme/ThemeController.class`、`core/utils/SystemBarUtils.class`（已改名），说明"内部包不可依赖"在产物层面也成立；
  - 例外：`com/yohu/youi/devtools/motionlab/MotionLabHost.class`、`com/yohu/youi/widget/feedback/dialog/internal/DialogOwner.class` 两个内部类未改名（未证实原因，可能是 keep 规则副作用）——**不要把它们当公开 API**。
  - `classes.jar` 内**没有** `com/yohu/youi/BuildConfig.class`、`com/yohu/youi/R.class`（R 由消费方构建生成）。
- `proguard.txt` 内容 == `youi_kit/consumer-rules.pro`：`-keep public class/interface com.yohu.youi.api.**` + 注解/Signature/InnerClasses/Exceptions 属性 + `api.**` 枚举 `$VALUES`。
- `res/values/values.xml` 只有 3 个 id：`youi_immersive_skip_color_invert`、`youi_motion_lab_root`、`youi_motion_runtime_status`（对应 `youi_kit/src/main/res/values/ids.xml:1-6`）；**无字体资源** → release 走 systemSans。

---

## 2. 宿主接入契约

### 2.1 是否需要 Application 初始化

**不需要。** 依据：

- 库 Manifest 为空壳：`youi_kit/src/main/AndroidManifest.xml:1-4`（无 `<application>`、无 `<provider>`、无 meta-data）。
- 全库 grep `ContentProvider|Initializer|androidx.startup` 无命中；依赖里也没有 `androidx.startup`（§1.1）。
- 所有全局状态都是懒初始化静态字段，例如 `ThemeController` 的 `private static volatile ColorMode colorMode = ColorMode.FOLLOW_SYSTEM`（`theme/ThemeController.java:18`）、`ThemeBind.BINDINGS`（`theme/ThemeBind.java:24`）。
- 演示 app 也没有自定义 `Application`（`app/src/main/AndroidManifest.xml:5-60` 无 `android:name`）。

> 唯一"接近初始化"的动作是窗口级 `YoSystemBar.prepareTransparentSystemBars(activity)`，它按 Activity 调，不是 Application 级。

### 2.2 主题父样式

- 库**不提供**任何主题/样式资源：`youi_kit/src/main/res/values/` 只有 `ids.xml`（§1.3）。
- 库内**不读取主题属性**：全库只出现 `android.R.attr.state_enabled/state_pressed`（`widget/common/ripple/internal/MotionRippleDrawable.java:134-136`），无 `obtainStyledAttributes`、无自定义 styleable。
- 组件全部用代码创建：proguard 注释明确"kit views are created in code, not XML"（`proguard-rules.pro` "Runtime keep" 段）。
- 因此**父样式无硬性要求**；实际约束来自宿主自身：若用 `AppCompatActivity` 需要 AppCompat 主题；若要跟随系统夜间，建议 `DayNight`。演示 app 的取值是 `Theme.Material3.DayNight.NoActionBar`（`app/src/main/res/values/themes.xml:3-8`；night 变体 `app/src/main/res/values-night/themes.xml:3-6`），app 在 manifest 里挂 `@style/Theme.BTTools`（`app/src/main/AndroidManifest.xml:13`）。
- 演示 app 的 release 配置 `minifyEnabled false`（`app/build.gradle:21-26`）——即演示工程不验证宿主 R8 场景，宿主开 R8 时靠库的 consumer rules（§1.3）。

### 2.3 色彩模式与 `YoColors` 快照语义

- 门面：`YoTheme.setColorMode / colorMode / addListener / removeListener`（`api/theme/YoTheme.java:26-41`），内部委托 `ThemeController`。
- 缺省值 `ColorMode.FOLLOW_SYSTEM`（`theme/ThemeController.java:18`）；解析逻辑：
  - `DARK → dark`、`LIGHT → light`、`FOLLOW_SYSTEM → 读 Resources.configuration.uiMode 的 NIGHT 位`（`theme/ThemeResolver.java:25-29,33-37`）。
- **`setColorMode` 不是必需调用**：不调用时行为等同跟随系统；只有"App 内强制的浅/深色开关"才需要它。
  演示模块全仓 grep `YoTheme.|setColorMode|ColorMode.` **零命中** → 演示从未设置色模式，即"默认方式"就是完全依赖系统夜间模式。
- `YoColors.of(context)` 每次都新建 `YoColors` 并**捕获**当时的 `ThemeSnapshot`：
  - `YoColors.java:21-30`（字段 `private final ThemeSnapshot snapshot`，`of()` 为 `new YoColors(Theme.of(context))`）；
  - `ThemeSnapshot` 是"Immutable resolved color tokens for one color mode"（`theme/ThemeSnapshot.java:9-14`），`light()/dark()` 返回调色板单例（`:245-253`）。
  - **推论（重要）**：`YoColors` 不是实时视图。切换色模式后必须重新 `YoColors.of()` 才能拿到新值；把 `YoColors` 存成 Activity 字段（演示的做法，如 `TestUIActivity.java:25,30`、`TabsTestActivity.java:48,68`）在"进程内切模式 / 系统夜间变化"时不会自动刷新。
- 组件自身不受此影响：它们通过 `ThemeComponent.bind(...)` 订阅 `ThemeController` + `ComponentCallbacks`（`theme/ThemeComponent.java:20-33`、`theme/ThemeBind.java:55-68`），会自绘刷新。
- 演示中 `YoColors` 只用于：页面背景、TextView 颜色、`YoCorner.fill(...)` 背景等**宿主自绘**部分（`TestUIActivity.java:41,49,71,79`）。

### 2.4 `YoSystemBar`：两个方法的分工、顺序与 inset 归属

公开 API（`api/platform/YoSystemBar.java`）：

| 方法 | 作用 | 证据 |
|---|---|---|
| `prepareTransparentSystemBars(Activity)` | 窗口级 edge-to-edge：`setDecorFitsSystemWindows(false)`、状态栏/导航栏色透明、关闭对比度 scrim、**图标明暗 = `!YoColors.of(activity).isDark()`** | `YoSystemBar.java:27-29` → `SystemBarUtils.java:133-151` |
| `setStatusBarColor` / `setStatusBarDarkIcons` | 单点覆盖栏色/图标 | `YoSystemBar.java:31-37` |
| `padViewForSystemBars(View, boolean includeBottom)` | 视图级：给传入 View 设置 padding（left/top/right = systemBars+cutout；bottom = `max(bars.bottom, navigationProtectionBottom)`，`includeBottom=false` 时 bottom=0），并在每次 insets 回调时重写 padding | `YoSystemBar.java:39-41` → `SystemBarUtils.java:203-211` |
| `systemBarsAndCutout(WindowInsetsCompat)` | 只读投影，供宿主自己算 padding | `YoSystemBar.java:43-46` → `SystemBarUtils.java:166-170` |

关键细节：

1. **图标明暗来自主题快照，不是系统配置**（`YoSystemBar.java:23-26`、`SystemBarUtils.java:129-132`）。因此若宿主强制 `YoTheme.setColorMode(LIGHT)`，`prepareTransparentSystemBars` 会写成"深色图标"；请务必在设置颜色模式之后调用它，或改模式后重新调用。
   - ⚠️ 同名陷阱：内部 `SystemBarUtils.prepareTransparentSystemBars(Activity)`（`SystemBarUtils.java:157-161`，按系统配置取明暗）**不是**公开方法，公开方法走的是 `YoColors` 快照路径。
2. `padViewForSystemBars` 用 `ViewCompat.setOnApplyWindowInsetsListener` **覆盖**该 View 已有的 listener（`SystemBarUtils.java:204`），且 **不消费 insets**（返回原 `insets`，`:209`）。所以：同一 View 只能有一个 padding owner；且子 View 仍会收到 full insets。
3. `padViewForSystemBars` 是"重写 padding"而非"追加"（`:208`）——若该 View 还有别的 padding 需求，会被抹掉。
4. 底部保护量是 `navigationBars / tappableElement / mandatorySystemGestures` 三者 max（`SystemBarUtils.java:191-197`），比单纯 `navigationBars.bottom` 更保守（手势导航下不为 0）。
5. **两个方法通常都要调，且是不同层级**：`prepareTransparentSystemBars` 决定"窗口是否 edge-to-edge + 栏色/图标"，`padViewForSystemBars` 决定"哪个 View 付 padding"。演示的标准写法见 §2.6。

### 2.5 模糊场景源（用 Menu/Dialog 沉浸材质时）

- `YoBlur.captureScene(View)`：把已挂父节点的场景注册为霜面采样源；注释明确"PAGE_IDLE 由 Page / Tabs / 实验室宿主在构造时调用；Dialog / Menu 只 nearest，禁止在 show 时 wrap `android.R.id.content` child 0"（`api/background/YoBlur.java:81-88`）。
- `YoPage` / `YoTabs` 内部已自动调用：`PageHostView.java:90`、`TabsHostView.java:61`（对各自的 contentLayer）。
- 因此：宿主若用 YoPage / YoTabs 作根，**无需**手动 `captureScene`；若用普通自定义根（如演示的 `UiTestScaffold`），需要自己调一次（`app/src/main/java/com/yohu/youidemo/uitest/UiTestScaffold.java:100-101`）。

### 2.6 演示模块给出的"标准启动序列"（三套，按页型选择）

**A. 普通页（无 Page/Tabs 壳）** — `app/src/main/java/com/yohu/youidemo/uitest/TestUIActivity.java:27-35,127-129`

```
colors = YoColors.of(this);                       // 2.3 快照
YoSystemBar.prepareTransparentSystemBars(this);   // 窗口级
setContentView(buildDashboard());                 // 根 View
YoSystemBar.padViewForSystemBars(root, true);     // 视图级（root=ScrollView）
```

**B. YoPage 页** — `PageTestActivity.java:51-78,264-271`

```
YoSystemBar.prepareTransparentSystemBars(this);   // 只调窗口级
scroller = buildScroller();
page = YoPage.create(this)...setContent(scroller).build();
setContentView(page.getView());                   // ← 不再 padViewForSystemBars
// onDestroy: page.destroy();
```

**C. YoTabs 页（应用级底栏）** — `TabsTestActivity.java:65-133,302-312`

```
colors = YoColors.of(this);
YoSystemBar.prepareTransparentSystemBars(this);
tabs = YoTabs.create(this)...addTab(id,label,icon,selectedIcon,pageView)...build();
root.addView(tabs.getView(), MATCH_PARENT/MATCH_PARENT);
setContentView(root);
// 顶 inset 由宿主自己付，底交给 tabs：
root 监听 insets → root.setPadding(bars.left, bars.top, bars.right, 0)
tabs.setOnContentInsetListener(inset -> 给每个页面滚动内容的 paddingBottom 设 max(inset,24dp))
// onDestroy: tabs.destroy();
```

**inset 归属矩阵（谁付哪个方向的 inset）**

| 场景 | left/right | top(状态栏) | bottom(导航/手势) |
|---|---|---|---|
| 普通页 + `padViewForSystemBars(v,true)` | 该 View padding | 该 View padding | 该 View padding（含手势强制区） |
| 普通页 + `padViewForSystemBars(v,false)` | 该 View padding | 该 View padding | **宿主自行处理**（留给叠底组件） |
| **YoPage** | host 左右 padding（`PagePresenter.java:396-402`） | **标题栏内部**按 `avoidLayoutSafeArea` 缺省 true 在标题栏顶垫（`api/containers/YoPage.java:710-717`、`PageConfig.java:119`、`PageMetricsRules.java:70-76`） | **内容滚动器** `paddingBottom = navigationProtectionBottom`（`PagePresenter.java:406-410`、`PageNavPad.java:19-31`） |
| **YoTabs** | host 左右（`TabsWindowInsetBinder.java:35-37,88-101`） | 不处理（宿主付，见 C） | **底栏自己**用（`TabsHostBindings.java:117-119`）；内容侧通过 `getContentInsetPx()` 暴露给宿主（`TabsHostView.java:251-258`、`YoTabs.java:163-164`） |
| **YoSheet** | 库自持 overlay（本轮未逐行核对 Sheet 的 inset 分支，标注未证实） | 同左 | 同左 |

> 两个组件都是"只在 left/right 上消费 insets"（`PageInsets.java:35` 与 `TabsWindowInsetBinder.java:37` 都是 `insets.inset(leftPx,0,rightPx,0)`），top/bottom **不消费**，所以子 View 仍会看到完整 inset——这正是"不要把 Page/Tabs 再套一层 pad 根"的另一条理由。

---

## 3. 组件默认契约

### 3.0 一览（入口 / 默认 / destroy）

| 组件 | 入口 | 关键默认 | getView | destroy |
|---|---|---|---|---|
| `YoPage` | `YoPage.create(context)` → Builder → `build()` | `TitleMode.MINI`、`Level.PRIMARY`、隐藏返回键、`BlurStyle.NONE`、`barOverlap=false`、`avoidLayoutSafeArea=true`、`DividerMode.AUTO`、`HideMode.NEVER`、`Intensity.STRONG`、`BlurStrategy.ADAPTIVE` | `getView():846` | `:849` |
| `YoTabs` | `YoTabs.create(context)` → Builder → `build()` | `BarStyle.FLOATING`、`barOverlap=true`、`ItemLayout.AUTO`、`HidePolicy.NEVER`、`DividerMode.ALWAYS` | `:167` | `:169` |
| `YoSheet` | `YoSheet.create(context)` → Builder → `build()` | 见 §3.3 | 无 `getView`（自持 overlay） | `:545`（`@MainThread`） |
| `YoList` | `YoList.create(context)` → Builder → `build()` | `showDividers=true`、`selectable=false`、`SelectionIndicator.NONE`、`scrollBar/refresh/swipe/dragSort=false`、`lanes=1` | `:599` | `:610` |
| `YoMenu` | `YoMenu.create(context)`；或 `bindMenu(host,cfg)` / `bindContextMenu(host,[ResponseType,]cfg)` | `Placement.BOTTOM_LEFT`、`SelectedMark.NONE`、`SubMenuExpandingMode.SIDE_EXPAND`、分隔线默认有 | 无 | `:392` |
| `YoDialog` | `YoDialog.create(context)`；`dismissAll(context)` | `Kind.STANDARD`、`cancelable=true`、`cancelableOnTouchOutside=true`、`blurBackground=true`、`EffectMode.DEFAULT`、`Placement.CENTER`、`ButtonDirection.AUTO` | 无 | `:189` |
| `YoToast` | `YoToast.create(context)` / 静态 `show/showLong/showConfirm/showWarning/showAlert` | `Duration.SHORT(2s)`、`Position.BOTTOM`、`Style.BAR` | 无 | `:197` |
| `YoButton` | `YoButton.create(context)` → `build()` | `Style.PRIMARY`、`Shape.CAPSULE`、`Size.MEDIUM`、`Role.DEFAULT`、圆角 `RADIUS_FULL` | `:284` | `:291` |
| `YoTextInput` | `YoTextInput.create(context)` | 见 API | `:129` | `:131` |
| `YoSegmentButton` | `YoSegmentButton.create(context)` | `SegmentType.SINGLE_CHOICE`、`Size.MEDIUM`、`ClickEffect.LIGHT` | `:225` | `:232` |
| `YoSlider` | `YoSlider.create(context)` / `createInSet` / `createOutSet` | — | `:251` | `:258` |
| `YoCard` | `YoCard.create(context)` | — | `:166` | `:173` |
| `YoBlur` | `YoBlur.create(context)` + `captureScene` | — | `:103` | `:105` |
| `YoScrollBar` | — | — | `:110` | `:118` |

---

### 3.1 `YoPage`（最严格的一个）

接口与语义（`api/containers/YoPage.java`）：

- 工厂：`static Builder create(Context)` → `PageImpl.PageBuilder`（`:552-555`）；`setContent(View)`（`:558`）；`getView()`（`:846-847`）；`destroy()`（`:849`）。
- **它是"窗口根容器"**：javadoc 明确"内容槽放页面内容，不要再塞一个窗口级壳（YoTabs）：两者各自消费系统栏，嵌套会把底栏顶出可视区；应用级底栏用 YoTabs 做 Activity content，而不是 YoPage 的 child"（`:47-49`）。
- **标题栏 inset 内部处理**：`setAvoidLayoutSafeArea` 缺省 `true`（`PageConfig.java:119`，文档 `docs/youi-kit-page-architecture.md:185` 也写缺省 true），标题栏自己在顶部垫状态栏；`barOverlap=false`（`PageConfig.java:118`）表示内容让出标题栏高度。
- **内容底部 inset 内部处理**：`PagePresenter.applyNavPad()` 把 `navigationProtectionBottom` 垫到"已绑定滚动器，否则自动探测到的滚动容器"的 `paddingBottom`，并 `setClipToPadding(false)`（`PagePresenter.java:406-410`、`PageNavPad.java:19-31,41-49`）。
- 滚动绑定：
  - `bindScroller(View)`（`:763-768`）可重复调用，`null` 停止自动探测；**不调用**时组件自动探测内容里的 `NestedScrollView/RecyclerView`；
  - `bindScrollers(List<View>)`（`:770-776`）、`bindNestedScrollers(List<NestedScroller>)`（`:778-784`）；
  - system `ScrollView` 不向父级派发嵌套滚动，故不受支持（`:763-765` 注释）。
- `TitleMode.FREE` 未 `bindScroller` 时不会收起（`:27`、`:69-70`）。
- `destroy()`：幂等（`PageImpl.java:588-600`：清 listener → `presenter.destroy()` → `lifecycle.destroy()`）；文档 `docs/youi-kit-page-architecture.md:160` 说明内部"引擎取消、时钟关闭、刷新订阅拆除、监听清空"。
- `getContentInsetPx()`（`:844`）暴露给宿主（用于自绘内容避让）。

**调用模板**：`YoSystemBar.prepareTransparentSystemBars(activity)` → `YoPage.create(activity)` → 配置 → `setContent(滚动器)` → `build()` → `setContentView(page.getView())` → `onDestroy(){ page.destroy(); }`（`PageTestActivity.java:51-78,264-271`）。

### 3.2 `YoTabs`

- 工厂：`YoTabs.create(Context)`（`api/navigation/YoTabs.java:64-67`）；`addTab(id,label,icon,content)` / 带 `selectedIconRes` 重载（`:70-75`）；`build()`。
- **默认 `BarStyle.FLOATING`、`barOverlap=true`**（`TabsConfig.java:85-86`）；且 **FLOATING 强制 overlap**（`TabsConfig.java:28`：`barStyle == FLOATING || barOverlap`）。
- `BarStyle` 语义（`YoTabs.java:33-38`）：`TILED` = 全宽项行、不挂沉浸材质，`overlap` 只改内容是否叠在栏下；`FLOATING` = 居中胶囊 64dp、THIN 光感。
- `setBarOverlap(boolean)` 语义（`YoTabs.java:80-86`）：TILED overlap=true 时内容伸到栏下，`inset = 项行 + nav`；false 时内容层让出栏高，`inset=0`；不重绑铬层。
- **`contentInset` 语义**（`YoTabs.java:163-164`、`TabsHostView.java:251-258`）：
  - `FLOATING` → `floatingBarHeightPx + floatingBottomMarginPx(navInset)`（栏高 + 底边距）；
  - `TILED` → `tiledContentInsetPx(...)`：overlap 时 = 项行 + nav，贴底时 = 0。
  - 组件自己也会把该值垫到"当前页里找到的纵向 scroller"的 `paddingBottom`（`TabsHostView.java:206,232-249`）；`setOnContentInsetListener` 是给宿主做**额外**避让的钩子（演示用它把页面 column 的 paddingBottom 设为 `max(inset, 24dp)`，`TabsTestActivity.java:100-104,302-312`）。
- `HidePolicy.FOLLOW_SCROLL` 只对 FLOATING 生效，TILED 忽略（`tabs/internal/TabsHideRules.java:18-19`；文档 `docs/youi-kit-bottom-bar-architecture.md:128-130`）。
- 滚动订阅：`bindScroller(View)` 可重复调用、`null` 解除；RecyclerView 用 `addOnScrollListener`，其余用 `ViewTreeObserver`，**不覆盖**调用方 listener（`YoTabs.java:111-117`）。未调用时切页自动绑定当前页自身的纵向 scroller（`:114-115`）。
- `destroy()`：`@` `TabsImpl.destroy()` 幂等，先清 4 个 listener 再 `lifecycle.destroy()`；注册的释放动作包括 `bindings.unbind()`（含 inset binder unbind）、`barView` 解绑、`host.getContentLayer().removeAllViews()`、`controller.destroy()`、清 contents（`TabsImpl.java:120-128,404-413`）。
- 演示把 `setContentView` 的**根**留给 Tabs，并在 `onDestroy` 里 `destroy()`（`TabsTestActivity.java:110-115,126-133`）。

### 3.3 `YoSheet`

- 工厂 `YoSheet.create(Context)`（`api/containers/YoSheet.java:493-497`），Builder 有 50+ setter（`:547-597`）。
- 呈现/收起/销毁全部 `@MainThread`：`prepareResult()`、`showResult()`、`show()`（default，`:507-511`）、`dismiss()`（`:513-517`）、`dismissResult(reason)`、`destroy()`（`:543-545`，注释"Idempotent instance teardown. Public operations fail afterwards."）。
- 默认：`PreferredPresentation.ADAPTIVE`（→ 由 resolver 决定 BOTTOM/CENTER/POPUP/SIDE 等）、面板自持完整呈现树，**明确不委托** Android Dialog/transition/animator/Material（`:20-27`）。
- 外部交互默认：bottom/center/side sheet 遮挡外部，popup sheet 允许外部交互，content-cover 恒遮挡（`:561-569`）。
- 销毁动词统一为 `destroy()`（不是 `close()`）。

### 3.4 `YoList`

- `YoList.create(context)` → Builder（`api/containers/YoList.java:44-47`）；`build()`（`:601-603`）；`getView()`（`:593-599`，可直接加入任意 ViewGroup）。
- `destroy()` 幂等，"解除全部监听、释放滚动/刷新/横滑等持有资源"（`:605-610`）。
- 默认：`showDividers=true`、`selectable=false`、`SelectionIndicator.NONE`、`scrollBar/refresh/swipe/dragSort/snap/maintainVisiblePosition=false`、`lanes=1`（`ListConfig.java:375-437`）。
- 行 inset：`setItemVerticalInset`（`:405-408`，选择弹窗内容区为 8vp）、`setItemHorizontalInset`（`:434-438`）。
- 有 `getScroller()`（`:585-589`），可交给 `YoPage.bindScroller`。

### 3.5 `YoMenu`

- 三种用法：`YoMenu.create(context)`；`bindMenu(host, Consumer<Builder>)`（长按/点击绑定）；`bindContextMenu(host, [ResponseType,] Consumer<Builder>)`（`api/navigation/YoMenu.java:263-280`）。
- 显示控制：`show()/dismiss()/toggle()/isShowing()`（`:365-371`）；`destroy()`（`:392`）。
- 默认：`Placement.BOTTOM_LEFT`（`MenuBuilder.java:97`、`MenuImpl.java:38`）、`SelectedMark.NONE`、`SIDE_EXPAND`、默认项/组分隔线（`MenuConfig.java:204-208`）。
- 锚点：`setAnchorView`，或走 `YoPage.getMenuAnchor(itemId)` 当弹出锚点（`YoPage.java:650-655`）。

### 3.6 `YoDialog`

- `YoDialog.create(context)`（`api/feedback/YoDialog.java:77-80`）、`dismissAll(context)`（`:82-84`）；`show()/dismiss()/destroy()`（`:183-189`）。
- **必须 Activity context**：`DialogOwner.requireActivity` 找不到 Activity 时抛 `IllegalArgumentException("YoDialog requires an Activity context")`（`dialog/internal/DialogOwner.java:133-140`）；在 finishing/destroyed Activity 上 `bind` 抛 `IllegalStateException("cannot present a dialog on a finishing Activity")`（`:72-81`）。
- 生命周期：`DialogOwner` 在 Activity(`LifecycleOwner`) 的 `onDestroy` 里 `dismissAndDestroyAll()` 并从 `OWNERS` 移除（`:96-105`）——即 Dialog 由库兜底清理，宿主不复用同一实例时可以不显式 destroy。
- 默认：`Kind.STANDARD`、`cancelable=true`、`cancelableOnTouchOutside=true`、`blurBackground=true`、`Placement.CENTER`、`ButtonDirection.AUTO`（`DialogConfig.java:177-195`）。
- 变体 = `Kind` + `Body.hosted(View/YoList/YoTextInput/YoLoadingProgress)`，"没有第二套预设类型树"（`YoDialog.java:18-26`）。

### 3.7 `YoToast`

- 静态快捷：`show(ctx,text)` / `show(ctx,text,iconRes)` / `showLong` / `showConfirm`(绿) / `showWarning`(红) / `showAlert`(橙)（`api/feedback/YoToast.java:57-101`）；`create(ctx)` → Builder（`:111-114`）。
- 控制：`show()`、`dismiss()`、`isShowing()`、`destroy()`（`:177-197`；`:194-197` 明确"幂等；销毁后不可再 show()"）。
- **必须有 Activity** 才能找到 `android.R.id.content`；`isHostFinishing()` 会跳过已 finishing 的宿主（`ToastImpl.java:442-445,447-452`）→ 非 Activity context 时 `findRootView()` 返回 null（不显示）。
- 默认：`Duration.SHORT=2s`、`LONG=3.5s`；`Position.BOTTOM`；`Style.BAR`（`YoToast.java:23-47`；`ToastImpl.getGravity` 默认 BOTTOM，`ToastImpl.java:454-463`）。
- 单例替换：新 `show()` 会 `dismissForReplacement()` 掉当前的（`ToastImpl.java:184-189,315-320`）。

### 3.8 `YoButton`

- `YoButton.create(context)` → `build()`（`api/controls/YoButton.java:77-80`）；`getView()`（`:284`）、`destroy()`（`:291`）。
- 默认：`Style.PRIMARY` + `Shape.CAPSULE` + `Size.MEDIUM` + `Role.DEFAULT`，圆角 `RADIUS_FULL`，内边距/字号取 MEDIUM 档（`ButtonConfig.java:205-220`）。
- `Shape` 与 `Style` 正交：`CIRCULAR + OUTLINED` = 带边框圆钮（`:102-106`）。

### 3.9 `YoTextInput` / `YoSegmentButton` / `YoSlider` / `YoCard`

- `YoTextInput.create(context)`（`api/controls/YoTextInput.java:44`），`getView()`/`destroy()`（`:128-131`）；风格/尺寸/输入类型/最大长度/计数器均为 Builder 项（`:141-169`）。
- `YoSegmentButton.create(context)`（`:78-81`），`addSegment(id,text[,iconRes])`（`:88-100`），`getView()`/`destroy()`（`:225,232`）；默认 `SINGLE_CHOICE` + `MEDIUM` + `ClickEffect.LIGHT`（`SegmentConfig.java:103-109`）；`SegmentType` 会切换整套 `SegmentStyle`（`SegmentConfig.java:117-123`）。
- `YoSlider`：三个入口 `create` / `createInSet` / `createOutSet`（`:33,44,55`），`getView()`/`destroy()`（`:251,258`）。
- `YoCard`：`create(context)`（`:30-33`），标题/副标题/图标/内容视图，`getView()`/`destroy()`（`:166,173`）。

### 3.10 `YoColors` / `YoDimens` / `YoFonts`

- `YoColors`：只读色票门面（`api/theme/YoColors.java`）。语义分组：品牌/语义色（`:46-57`）、文字（`:59-71`）、图标（`:73-77`）、背景（`:79-83`）、组件/交互（`:85-97`）、`system()`（HarmonyOS `ohos_id_color_*` 层，`:99-148`）、`palette()`（雪域灰/辅助色，`:150-167`）。
- `YoDimens`：**纯静态 float 常量（单位 dp/sp，不是 px）**，全部转发 `core.constants.Dimens`（`api/theme/YoDimens.java:1-114`）。注释给出档位（圆角 2/4/8/12/16/18/20/24/32、间距 2/4/8/12/16/24、图标 16/24/32、字号 10/12/14/16/18/20/24）。宿主若自己 `setPadding` 需乘 density。
- `YoFonts`：`isBundled()` 反映当前产物是否嵌字体；`regular/medium/bold(Context)` 与 `applyRegular/Medium/Bold(TextView)`（`api/theme/YoFonts.java:29-59`）。release AAR 走 systemSans → `isBundled()==false`、`regular()` 回落 `sans-serif`（`:16-18` 注释 + §1.3 实测）。

---

## 4. 库对消费者的约束

### 4.1 `app/build.gradle` 的 `verifyAppSdkBoundary`（演示工程的"SDK 边界"）

规则原文（`app/build.gradle:59-89`）：

- 扫描 `src/main/java`、`src/test/java`、`src/androidTest/java`（`:62-66`）。
- 只看以 `import com.yohu.youi.` 开头的 import 行（`:74`）。
- 允许：`import com.yohu.youi.api.`、`import com.yohu.youi.R`、`import com.yohu.youi.BuildConfig`（`:75-77`）。
- 其余一律 `GradleException`（`:78-87`），并绑定到 `check` 与 `compile*JavaWithJavac`（`:91-94`）。

含义与**已知空白**（做耳机 App 对比时必须知道）：

1. 该检查**只约束 import 行**：同包内使用、`import static`、全限定名内联（`com.yohu.youi.widget.X x`）、字符串反射均不拦。
2. `youi_kit` 的 `api` 包本身**从不 import `R`/`BuildConfig`**（全库 grep 零命中）→ 这两条白名单是为宿主保留的（kit `R` 有 3 个 id，见 §1.3；演示测试确实用了 `com.yohu.youi.R.id.youi_motion_runtime_status`，`app/src/test/java/com/yohu/youidemo/uitest/UiTestActivitiesSmokeTest.java:87`）。
3. 实测 release AAR 的 `classes.jar` 里**没有** `com.yohu.youi.BuildConfig`（被 R8 改名/内联，§1.3）→ 依赖 release AAR 时 `com.yohu.youi.BuildConfig` 是否可编译**未证实**；`com.yohu.youi.R` 由 AGP 在消费方生成（未证实，需以实际编译验证）。
4. 演示工程 `release minifyEnabled false`（`app/build.gradle:22-25`）→ 演示没验证宿主 R8 下的 API 稳定性。

### 4.2 库自身的 API 边界（会间接约束宿主）

- `verifyApiBoundary`（`youi_kit/build.gradle:241-290`）：`api/` 包不许 import `.internal.`（`:260-262`），且 public/protected 签名不许出现内部类型（`:273-284`）。→ **api 包是可信的"纯净层"**，宿主用它的签名不会牵出内部类。
- 内部前缀集合（`:248-253`）：`com.yohu.youi.{animation,widget,core,internal}.` → 这些都属于"内部包"。
- `verifyCalleeOwnership`（`:292-340`）：产品内部也不许越借他人 Dimens/内部件（说明库里存在"host 不能借内部"的同款纪律）。
- `verifyPageLayering`（`:342-403`）：`page.internal` 分层（spec<rules<engine<view/platform<presenter；spec/rules/engine 禁 `android.*`），且 **只有 `widget/containers/page` 可以 import page internal**（`:386-398`）。
- `verifySelfDevelopedMotion`（`:135-239`）：库禁 `android.animation./android.transition./androidx.transition./androidx.dynamicanimation/.animate()` 等平台动画，且禁内联全限定类型（`:149-188`）；XML 禁 `@anim/@animator/animated-/windowAnimationStyle/stateListAnimator`（`:217-234`）。

> 对宿主的实际含义：不要依赖库的动画实现细节、不要引用内部包、不要指望用平台 `Transition` 去接管库组件。

### 4.3 明确写进 javadoc 的宿主约束

- **不要嵌套窗口级壳**：YoPage 与 YoTabs 都是窗口根，不能互为 child（`YoPage.java:47-49`、`YoTabs.java:18-20`）。
- **api 的 `YoColors` 是"宿主侧"**，组件内部继续用 `Theme.of(context)`（`YoColors.java:15-18` 注释）。
- **`YoSystemBar` 是唯一允许宿主用的窗口栏入口**："Kit components keep using the internal inset math; applications must not import `core.utils`"（`YoSystemBar.java:14-17`）。
- `YoDimens`："applications must use this type instead of core.constants"（`YoDimens.java:5-8`）。

---

## 5. 生命周期 / 销毁契约

### 5.1 `destroy()` 的语义（全库统一）

`ComponentLifecycle`（`core/lifecycle/ComponentLifecycle.java`）：

- "组件资源所有权边界。资源按注册逆序释放，destroy 幂等，**销毁后所有公开操作必须失败**"（`:8-9`）。
- `requireAlive()` → 已销毁抛 `IllegalStateException("<Owner> has been destroyed")`（`:20-24`）。
- `destroy()` 幂等；逐个执行释放，异常聚合后抛出（`:35-55`）。

因此：**对同一实例 `destroy()` 两次安全；`destroy()` 之后再调任何 setter/getter 会抛异常**（如 `TabsImpl.java` 每个公开方法首行 `lifecycle.requireAlive()`，`:142-399`）。宿主若把组件实例缓存到 Activity 之外并跨页面复用，会在第二次使用时崩。

### 5.2 为什么 `destroy()` 实际上是"必须"

组件（如 YoPage/YoTabs/YoList/YoButton…）在构造时调用 `ThemeComponent.bind(...)`，它会：

1. `apply.accept(Theme.of(view.getContext()))` 立即上色；
2. `ThemeBind.attach(view, ...)` — 内部 `ThemeController.addListener(this)` + `view.getContext().registerComponentCallbacks(this)`（`ThemeBind.java:55-59`）；
3. `lifecycle.own(() -> ThemeBind.detach(view))`（`ThemeComponent.java:32`）。

`ThemeBind.BINDINGS` 是 `WeakHashMap<View, Binding>`（`ThemeBind.java:24`），但 **value(`Binding`) 强持 `view`**（`:46-47`）→ key 永远可达，WeakHashMap 不会自动清理。**若 `destroy()` 没调**，`ThemeController` 的静态 listener 列表（`ThemeController.java:19`）会一直持有 `Binding → View → Activity Context`。

> 结论：`destroy()` 是解除主题订阅/系统 config 回调的唯一入口（`ThemeBind.detach`），不调就是 Activity 级泄漏 + 残留 `ComponentCallbacks`。这是**推断**（基于上述代码路径），但路径完整、无其它解除点。

### 5.3 各组件释放内容（抽样）

| 组件 | destroy 释放 | 证据 |
|---|---|---|
| YoPage | 清 back/menu listener、title-mode/hide/inset listener → `presenter.destroy()`（引擎取消/时钟关闭/刷新解绑/监听清空）→ 视图释放 | `PageImpl.java:588-600`；`docs/youi-kit-page-architecture.md:160` |
| YoTabs | `bindings.unbind()`（含窗口 inset 解绑）、bar 解绑、内容层移除、`controller.destroy()`、清 contents；`ThemeComponent` 解绑 | `TabsImpl.java:120-128,404-413` |
| YoSheet | 幂等 teardown，之后公开操作失败 | `YoSheet.java:543-545` |
| YoList | 解除全部监听、释放滚动/刷新/横滑资源 | `YoList.java:605-610` |
| YoDialog | 拆呈现树；另有 Activity onDestroy 兜底 `dismissAndDestroyAll()` | `YoDialog.java:189`；`DialogOwner.java:96-105,125-131` |
| YoToast | 幂等拆树；`ToastController.destroy()` 取消 `postDelayed` | `YoToast.java:194-197`；`ToastImpl.java:271`；`ToastController.java:35-51` |

### 5.4 演示的销毁纪律

- `PageTestActivity.onDestroy()` → `page.destroy()`（`PageTestActivity.java:264-271`）。
- `TabsTestActivity.onDestroy()` → `tabs.destroy()`（`TabsTestActivity.java:126-133`）。
- 简单控件（Button/Card/TextInput 等）演示中多数**不在 onDestroy 里显式 destroy**（如 `TestUIActivity`、`ControlsTestActivity` 均无）；即"无长生命周期资源的小控件可随 View 树回收"，但一旦用了 `YoToast` 的延时、`YoBlur` 的捕获、`YoMenu` 的 popup 等，就必须 destroy。

---

## 6. 线程契约

- **所有 View 型组件必须在主线程创建/操作**（Android View 契约），库内大量 `@MainThread` 标注：
  - `YoSheet` 的 `prepareResult/showResult/show/dismiss/setDetentResult/dismissResult/pushResult/popResult/destroy` 全部 `@MainThread`（`api/containers/YoSheet.java:499-545`）。
  - `YoScrollBar.show/hide`（`api/containers/YoScrollBar.java:86,91`）、`DialogOwner.of/dismissAll/top/bind/unbind`（`DialogOwner.java:41-85`）。
  - 动画 runtime/binder/View 写入全部 `@MainThread`（`animation/coordinator/MotionImpl.java`、`MotionCoordinator.java` 等 250+ 处），文档亦写明"runtime、binder 和 Android View 写入默认限定主线程；纯数学模块线程无关"（`docs/animation-system-v3/00-overall-architecture.md:125`）。
- 副作用：`YoTheme.setColorMode` 内部同步遍历 listener（`CopyOnWriteArrayList`），从任意线程调都安全，但 listener 回调（重绘）必须在主线程 → 实际应主线程调用。
- `YoToast.show()` 用主线程 `Handler.postDelayed`（`ToastController.java:35-39`）。

---

## 7. 「文档默认值 / 文档事实」vs「当前代码」冲突清单

> 说明：`docs/` 里**没有**面向宿主的接入指南（grep `YoSystemBar|YoPage.create|YoTabs.create|prepareTransparentSystemBars|padViewForSystemBars|YoTheme.setColorMode` 在 `docs/**/*.md` 只有 1 处命中，且在架构文档的 API 清单里，`docs/youi-kit-bottom-bar-architecture.md:210`）。所以"标准调用方式"的事实来源只能是 **代码 + 演示模块**，docs 只描述内部实现。

| # | 文档说法 | 代码现状 | 判定 |
|---|---|---|---|
| C1 | `docs/youi-kit-full-audit-2026-08-17.md:390-393`：**"F1. YoToast 无 `destroy()` / 无 ComponentLifecycle；静态 Handler + currentToast，Activity 销毁后 postDelayed 仍可能操作已死窗口"** | `YoToast.java:194-197` 有 `destroy()`（幂等、"销毁后不可再 show()"）；`ToastImpl.java:60` 有 `ComponentLifecycle("YoToast")`；`:271` 实现 destroy；`ToastController.java:50-51` 会取消回包；`:442-445` 检测宿主 finishing | **文档已过期**（该文档是 2026-08-17 审计快照） |
| C2 | 同文档 `:412`：**"生命周期动词分裂：多数 `destroy()`，Sheet `close()`，Toast 什么都没有"** | `YoSheet.java:509-517` 是 `show()/dismiss()`，`:545` 是 `destroy()`，**没有 `close()`** | **文档已过期** |
| C3 | 同文档 `:260`：**"Button/Checkbox/Switch 有 `requireAlive`；其余四组件多数公开方法销毁后仍可改状态"** | 主流组件（Page/Tabs/List 等）已普遍 `requireAlive`；但**未逐组件全量核对**（如 YoCard/YoSlider 的每个 setter） | **未证实**，不据此下结论 |
| C4 | `docs/youi-kit-page-architecture.md:185`：`setAvoidLayoutSafeArea` 缺省 **true** | `PageConfig.java:119` `private boolean avoidLayoutSafeArea = true;` | 一致 ✅ |
| C5 | `docs/youi-kit-bottom-bar-architecture.md:213`：`// FLOATING 强制 true` | `TabsConfig.java:28` 一致；但**两份架构文档都没写默认 BarStyle** | 一致；**文档缺"默认 FLOATING"这一关键事实**（代码：`TabsConfig.java:85`），只看文档会误以为默认 TILED |
| C6 | `docs/youi-kit-bottom-bar-architecture.md:207-222` 公开 API 清单 | 与 `YoTabs.java` 接口一致（含 `getView()/destroy()`） | 一致 ✅ |
| C7 | `docs/youi-kit-list-rewrite-contract.md:186-187`：`destroy()` 幂等、保留签名兼容 | `YoList.java:605-610` 一致 | 一致 ✅ |
| C8 | `gradle.properties:27-30` 与 `youi_kit/build.gradle:5-7`：release 不嵌字体 | AAR 实测无字体资源；`YoFonts.java:16-18` 一致 | 一致 ✅ |

> 另注意一处**同名不同义**陷阱（非文档冲突，但极易踩）：`YoSystemBar.prepareTransparentSystemBars`（公开，图标明暗=主题快照，`YoSystemBar.java:27-29`）与 `SystemBarUtils.prepareTransparentSystemBars`（内部，图标明暗=系统夜间配置，`SystemBarUtils.java:157-161`）行为不同；宿主必须用前者。

---

## 8. 供耳机 App 对比用的检查清单（Checklist）

> 每项都可用"文件:行"给证据；`❓` 表示需人工确认。

**接入层**
1. 是否**没有**自定义 `Application` 初始化 YoUI，或虽初始化但调用的都是静态内部门面（无 `init()` 之类）？（库侧无 init 要求，§2.1）
2. 主题是否 `NoActionBar`（避免与 `YoPage` 标题栏重叠）？是否 `DayNight`（跟随系统夜间）？（§2.2）
3. 是否调用了 `YoSystemBar.prepareTransparentSystemBars(activity)`，且它在 `setContentView` 前后顺序与演示一致（演示两种顺序都有，`TestUIActivity.java:31-32` vs `UiTestScaffold.java:96-98`）？（§2.6）
4. 是否在用了 `YoPage` 之后**仍**对 page 根调 `padViewForSystemBars`（=双重 padding，错）？（§2.4 矩阵）
5. 是否用 `YoColors.of()` 的返回值**跨色模式复用**（快照失效，错）？（§2.3）
6. 是否调用了 `YoTheme.setColorMode`？若调，是否在 `prepareTransparentSystemBars` 之前/之后保持一致（否则状态栏图标与环境不匹配）？（§2.3/§2.4）
7. 若用 Menu/Dialog 沉浸材质但自绘根：是否 `YoBlur.captureScene(root)`？（§2.5）

**组件层**
8. `YoPage`：是否 `create→setContent→build→setContentView(getView())`；`FREE`/滚动隐藏是否 `bindScroller` 或依赖自动探测？（§3.1）
9. `YoPage`：是否**没有**把 `YoTabs` 塞进 page 的 content（库明确禁止）？（§3.1）
10. `YoTabs`：是否理解默认是 FLOATING（而不是 TILED）；宿主是否正确使用 `getContentInsetPx()/setOnContentInsetListener`（尤其 TILED 贴底时 inset=0）？（§3.2）
11. `YoTabs`：`FOLLOW_SCROLL` 在 TILED 下无效，是否被误用？（§3.2）
12. `YoSheet/YoTabs/YoPage/YoList` 是否在 `onDestroy` 里 `destroy()`（避免主题静态监听泄漏）？（§5.2）
13. `YoDialog/YoToast` 是否传入 Activity context（而非 `applicationContext`）？是否在 finishing Activity 上 show？（§3.6/§3.7）
14. `YoToast` 是否用了 `destroy()`/`dismiss()`（而不是只等超时）？（§3.7）
15. 简单控件（Button/Card/TextInput/Segment/Slider）是否在销毁后复用实例（错，会抛 `IllegalStateException`）？（§5.1）
16. `YoDimens` 的 dp 常量是否被当成 px 用（漏乘 density，错）？（§3.10）

**工程层**
17. 宿主是否只 import `com.yohu.youi.api.**`（+ `R`/`BuildConfig`）？是否有 `com.yohu.youi.widget.*`、`core.*`、`animation.*`、`theme.*` 的 import？（§4.1/§4.2）
18. 是否依赖了 AAR 里被改名的内部类（会在 R8 下崩）？（§1.3）
19. 宿主是否开启了 R8？依赖 release AAR 时 `BuildConfig`/`R` 的可编译性需实测（未证实）。（§4.1 空白 3）
20. `minSdk` 是否 ≥ 24、是否开启了 core-library desugaring（库用 Java 21 集合/流 API）？（§1.1）

---

## 9. 未证实 / 需下一步验证的项

1. **`com.yohu.youi.BuildConfig` 在 release AAR 下是否可 import**（AAR classes.jar 无该类；可能被 R8 内联/改名）——需在耳机 App 或本仓 `-Pyoui.useAar=true` 下实编译验证。
2. **`com.yohu.youi.R` 在 release AAR 下由 AGP 生成的具体形态**（非传递 R 类，`android.nonTransitiveRClass=true`，`gradle.properties:26`）——同上。
3. 为何 `MotionLabHost`/`DialogOwner` 在 AAR 中未被改名（推测与 keep 规则或 `-keepattributes` 有关，未找到直接规则）——需看 mapping.txt 或反编译确认。
4. 各组件 `destroy()` 后再调 API 的**逐方法**行为（只核对了 Page/Tabs/Sheet/List/Toast 的显式声明与通用 `requireAlive` 机制，未逐一跑）。
5. `YoDialog`/`YoSheet` 在**非主线程**调用的具体失败形式（只有注解约束，未测运行时断言）。
6. `YoPage` 在 `barOverlap=true` 时内容 inset 的完整公式（本文只核对了 `barOverlap=false` 缺省路径与 `PageMetricsRules` 的 status 参与方式）。
7. `docs/` 中其它历史审计文档（`docs/youi-kit-full-audit-2026-08-17.md` 之外）是否还有同类过期陈述——本次只抽查了与"默认值/销毁/inset"直接相关的 7 篇。

---

## 10. 证据索引（本文引用的一等文件）

| 主题 | 文件 |
|---|---|
| 构建/版本/导出 | `youi_kit/build.gradle`、`app/build.gradle`、`settings.gradle`、`gradle.properties`、`gradle/libs.versions.toml`、`releases/youi_kit-1.0.0.aar` |
| 接入与主题 | `api/theme/{YoTheme,YoColors,ColorMode,YoDimens,YoFonts}.java`、`theme/{ThemeController,ThemeResolver,ThemeSnapshot,ThemeComponent,ThemeBind}.java` |
| 系统栏 | `api/platform/YoSystemBar.java`、`core/utils/SystemBarUtils.java`、`core/utils/ContextUtils.java` |
| 容器 | `api/containers/{YoPage,YoSheet,YoList,YoCard}.java`、`api/navigation/YoTabs.java`、`widget/containers/page/{PageImpl.java,internal/**}`、`widget/navigation/tabs/**` |
| 反馈/导航/控件 | `api/{feedback,navigation,controls}/**` |
| 生命周期 | `core/lifecycle/ComponentLifecycle.java` |
| 演示（标准用法） | `app/src/main/java/com/yohu/youidemo/{MainActivity,uitest/TestUIActivity,uitest/UiTestScaffold,uitest/PageTestActivity,uitest/TabsTestActivity}.java`、`app/src/main/res/values{,-night}/themes.xml`、`app/src/main/AndroidManifest.xml` |
| 库内自检 | `youi_kit/build.gradle` 的 4 个 verify 任务、`app/build.gradle:59-95` |
| 历史文档（含过期项） | `docs/youi-kit-full-audit-2026-08-17.md`、`docs/youi-kit-page-architecture.md`、`docs/youi-kit-bottom-bar-architecture.md`、`docs/youi-kit-list-rewrite-contract.md`、`docs/animation-system-v3/00-overall-architecture.md` |
