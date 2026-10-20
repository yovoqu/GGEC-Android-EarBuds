# YoUI AAR 校验报告（哈希 / API / 编译）

> **2026-10-19 16:00 之后本报告的 AAR 哈希结论已过期。** 为修复列表 Ripple 几何与多行输入，
> 并跟上库在 16:53 前的最新改动，已按 `Android-YoUI` 当前源码重新执行 `:youi_kit:clean :youi_kit:assembleRelease`
> 并替换 `app/libs/youi_kit-1.0.0.aar`
> （最新 SHA-256 `3CA4EB12D484065871A8A02BCC39AA9FD2FC7D93E5FB20774B27665BE9CA0CBC`，2,186,788 B；
> 16:06 的上一版为 `09D4D807…A1315`）。
> 详见 [youi-kit-patch-2026-10-19.md](youi-kit-patch-2026-10-19.md)。下文保留为旧 AAR（与 releases 一致）的历史校验记录。

- 校验时间：2026-10-19 14:42–14:52（Asia/Shanghai）
- 校验对象：`app/libs/youi_kit-1.0.0.aar`（App 实际依赖的 AAR）
- 对照源：`D:\A_yoprogram\Github_Gallery\Android-YoUI`
- 校验人：aar-verifier（只读验证，未修改任何源码 / AAR；解包均在 `%TEMP%` 临时目录）
- 结论速览：
  1. **三份 AAR 的 SHA-256 完全一致**（`A5112DCE…1D41AD`，2,172,144 字节）；
  2. **AAR 晚于源码最后一次修改（+3 分 25 秒），且构建产物链证明它包含当前未提交改动**；
  3. App 用到的 **19 个 `com.yohu.youi.api.*` 类型全部存在**，公共 API 面与当前源码编译结果**双向 0 差异**；
  4. **`:app:compileDebugJavaWithJavac` 强制重跑通过**（offline，BUILD SUCCESSFUL，0 错误）；
  5. 该 AAR 就是本仓库 `assembleRelease`/`exportReleaseAar` 的默认 release 产物，App 可按该 AAR 编译。

---

## 1. 哈希对照表

| # | 文件 | SHA-256 | 大小(B) | LastWriteTime |
|---|------|---------|--------:|---------------|
| 1 | `GGEC-Android-EarBuds\app\libs\youi_kit-1.0.0.aar` | `A5112DCE4B9CC7A23AAB26FECDBD6DA541CF932B31221C55F073F1D1501D41AD` | 2,172,144 | 2026-10-19 13:09:31.1269375 |
| 2 | `Android-YoUI\releases\youi_kit-1.0.0.aar` | `A5112DCE4B9CC7A23AAB26FECDBD6DA541CF932B31221C55F073F1D1501D41AD` | 2,172,144 | 2026-10-19 13:09:31.1269375 |
| 3 | `Android-YoUI\youi_kit\build\outputs\aar\youi_kit-release.aar` | `A5112DCE4B9CC7A23AAB26FECDBD6DA541CF932B31221C55F073F1D1501D41AD` | 2,172,144 | 2026-10-19 13:09:31.0418543 |

- 三份字节级一致；文件 2 与文件 1 的 mtime 完全相同（`Copy-Item` 保留时间戳），说明 **App 里的 AAR 直接复制自 releases 目录**；文件 3 由 `assembleRelease` 生成，比 releases 副本早约 85 ms。
- 佐证：AAR 内 `classes.jar` 的 SHA-256 = `F4BC9BDE3C75B8035113C198C92B6021517C648DDAEE6023177B83CE4B03A044`，与本次构建中间产物 `youi_kit\build\intermediates\aar_main_jar\release\syncReleaseLibJars\classes.jar` **逐字节相同**。

验证命令（PowerShell）：

```powershell
Get-FileHash -LiteralPath <aar> -Algorithm SHA256
```

---

## 2. AAR 与源码的时间关系（是否包含未提交改动）

### 2.1 时间线（同一天，2026-10-19）

| 时间 | 事件 |
|------|------|
| 13:06:06.588 | `youi_kit` **源码最后一次修改**：`src\main\java\com\yohu\youi\widget\feedback\dialog\internal\DialogPresentation.java`（全库 1,134 个代码文件中最新） |
| 13:08:35.58 | `youi_kit\build\intermediates\javac\release\compileReleaseJavaWithJavac\classes` 最新 class 输出（1,849 个 class） |
| 13:09:20.80 | `build\outputs\mapping\release\usage.txt`（R8 `-printusage`，412,192 B） |
| 13:09:26.77 | `build\outputs\mapping\release\mapping.txt`（R8 mapping，8,528,341 B） |
| 13:09:30.18 | `build\intermediates\aar_main_jar\release\syncReleaseLibJars\classes.jar` |
| **13:09:31.04** | **`build\outputs\aar\youi_kit-release.aar` 生成** |
| 13:09:31.13 | `releases\youi_kit-1.0.0.aar`（`exportReleaseAar` 重命名导出） |

**源码最新 mtime + 3 分 25 秒 = AAR mtime**。AAR 之后没有任何 `youi_kit` 源文件被修改（当前复核时最新文件仍是 13:06:06）。

### 2.2 未提交改动确实被包含（交叉证据）

`git -C Android-YoUI log -1` = `1a958665bba4b359283eeb51cf9d32170d0ef2e9`（2026-10-15 16:58），而构建发生在 10-19；`git status` 显示大量未提交修改与未跟踪新文件（含 `api/containers/YoPage.java` +654 行等）。

证明 AAR 由"包含这些未提交改动"的构建生成：

1. **R8 mapping 的 `pg_map_id` 与 AAR 一致**：`mapping.txt` 头 `pg_map_id: c90426c0a9caf89161c19a50307ea6688843c325bcecccec6c573dd342317918`；`javap` 读取 AAR 内类时显示 `Compiled from "r8-map-id-c90426c0a9caf89161c19a50307ea6688843c325bcecccec6c573dd342317918"` —— AAR 就是该次 R8 的产物。
2. **mapping 反映未提交的类重命名**：`git status` 有 `RM .../animation/material/immersive/DisplayFrame.java -> .../animation/core/physics/DisplayFrame.java`；mapping 中对应行为 `com.yohu.youi.animation.core.physics.DisplayFrame -> o.lIO0:`（旧包路径不存在）。
3. **`usage.txt` 列出未跟踪的新类**（新文件被编译进 R8 输入后被裁剪）：`MenuMotion` 13 处、`MenuMotionModel` 10 处、`MenuDistortionFlow` 3 处、`ListRowFace` 3 处、`ListChrome` 3 处、`DistortionState` 2 处、`MenuAnchoring` 2 处、`ListGroupCard`/`MenuPlacements` 各 1 处 —— 这些在 git HEAD 中都不存在。
4. **pre-R8 javac 输出包含未跟踪新包的 class**：`page/internal/{spec=4, rules=30, engine=10, view=27, controller=2, platform=11, presenter=4}`、`animation/overlay`、`list/internal/ListRowFace.class` 等，均存在且由该次 release 编译产生。
5. **API 面与当前源码编译结果完全一致**（见 §5），即 AAR 的公共面 = 13:08 由当前工作区源码编译出的公共面。

> 局限：以上基于文件系统 mtime + Gradle/R8 中间产物 + mapping id 的交叉证据；mtime 理论上可被保留/伪造，但 mapping id 与 classes.jar 哈希把 AAR 唯一绑定到 13:09 这次构建。

---

## 3. AAR 解包内容

AAR 顶部条目（`System.IO.Compression.ZipFile`，共 7 条目）：

| 条目 | 大小(B) | 内容摘要 |
|------|--------:|----------|
| `AndroidManifest.xml` | 200 | `package="com.yohu.youi"`，`minSdkVersion=24` |
| `classes.jar` | 2,306,178 | 1,614 个 entry（class），其中 `com/yohu/youi/api/**` 402 个 class |
| `proguard.txt` | 734 | consumer rules，与 `youi_kit\consumer-rules.pro` SHA-256 **完全相同**（`0A46D793…CADF`） |
| `R.txt` | 114 | `youi_immersive_skip_color_invert` / `youi_motion_lab_root` / `youi_motion_runtime_status` 三个 id |
| `res/values/values.xml` | 231 | 上述三个 id 的声明 |
| `META-INF/com/android/build/gradle/aar-metadata.properties` | 210 | `aarFormatVersion=1.0`、`minCompileSdk=1`、`coreLibraryDesugaringEnabled=true`、`desugarJdkLib=com.android.tools:desugar_jdk_libs:2.1.5` |

### consumer proguard / R8 keep

`proguard.txt`（= `consumer-rules.pro`）内容要点，宿主 App 开启 R8 时生效：

```proguard
-keep public class com.yohu.youi.api.** { public *; }
-keep public interface com.yohu.youi.api.** { *; }
-keepclassmembers enum com.yohu.youi.api.** { **[] $VALUES; public *; }
```

同时库自身 release 构建使用了 `youi_kit\proguard-rules.pro`（`-keep public class com.yohu.youi.api.** { public *; }`、`-repackageclasses 'o'`、字典混淆），因此 **AAR 是"库自身已 R8 混淆"的 release 产物**：`api` 公共类名/方法名保留，内部类被重命名并扁平化到 `o.*`。这也是 `classes.jar` 中不存在内部类原始类名的原因。

### BuildConfig / versionName

- `classes.jar` 中 **没有** `com/yohu/youi/BuildConfig.class`，`usage.txt` 明确记录 `com.yohu.youi.BuildConfig` 被 R8 移除；因此无法从 AAR 字节码直接读 `VERSION_NAME`。
- 在本次构建的 pre-R8 输出上 `javap -p -constants` 得到：

```text
public final class com.yohu.youi.BuildConfig {
  public static final boolean DEBUG = false;
  public static final java.lang.String LIBRARY_PACKAGE_NAME = "com.yohu.youi";
  public static final java.lang.String BUILD_TYPE = "release";
  public static final boolean EMBED_HARMONY_SANS = false;
}
```

  （library 模块的 BuildConfig 本身不生成 `VERSION_NAME`/`VERSION_CODE` 字段，所以这是正常现象，不是 AAR 缺版本信息。）
- versionName 的事实来源是 Gradle 配置：`youi_kit/build.gradle` → `defaultConfig { versionCode 100; versionName "1.0.0" }`；`exportReleaseAar` 用 `${versionName}` 重命名导出为 `releases/youi_kit-1.0.0.aar`。**即 AAR 版本 = 1.0.0，与文件名一致。**

---

## 4. App 使用的类型清单与存在性

对 `app/src` 全量扫描 `import com.yohu.youi.*`，去重后 **19 个顶层类型**（任务描述写的 18 个，实际为 19 个）：

| # | 类型（App import） | AAR `classes.jar` | 嵌套 class 数 |
|---|-------------------|-------------------|-------------:|
| 1 | `com.yohu.youi.api.containers.list.ListPrefixSpec` | ✅ | 2 |
| 2 | `…containers.list.ListSuffixSpec` | ✅ | 2 |
| 3 | `…containers.list.ListTextSpec` | ✅ | 1 |
| 4 | `…containers.YoCard` | ✅ | 1 |
| 5 | `…containers.YoList` | ✅ | 26 |
| 6 | `…containers.YoPage` | ✅ | 22 |
| 7 | `…containers.YoSheet` | ✅ | 33 |
| 8 | `…controls.YoButton` | ✅ | 5 |
| 9 | `…controls.YoSegmentButton` | ✅ | 7 |
| 10 | `…controls.YoSlider` | ✅ | 2 |
| 11 | `…controls.YoTextInput` | ✅ | 6 |
| 12 | `…feedback.YoDialog` | ✅ | 11 |
| 13 | `…feedback.YoToast` | ✅ | 4 |
| 14 | `…navigation.YoMenu` | ✅ | 9 |
| 15 | `…navigation.YoTabs` | ✅ | 9 |
| 16 | `…platform.YoSystemBar` | ✅ | 0 |
| 17 | `…theme.YoColors` | ✅ | 2 |
| 18 | `…theme.YoDimens` | ✅ | 0 |
| 19 | `…theme.YoFonts` | ✅ | 0 |

**缺失的 App 使用类型：0 个。**

补充：源码 `api` 包 122 个 `.java` 顶层类型中，AAR 缺 1 个 class —— `api/animation/curve/ExtrapolatedCurve`，其源码声明为 `final class ExtrapolatedCurve implements Curve`（**包私有，非公开 API**），被 R8 裁掉属正常；App 未使用。

---

## 5. 重点方法签名 `javap` 验证

`javap -p -classpath <解包的 classes.jar>`（JDK 21）逐项确认：

| App 调用 | AAR 中的签名 | 结果 |
|----------|--------------|------|
| `YoPage.Builder#setMenuItems` | `public abstract YoPage$Builder setMenuItems(java.util.List<YoPage$MenuItem>)` | ✅ |
| `YoPage.Builder#setMenuMaxCount` | `public abstract YoPage$Builder setMenuMaxCount(int)` | ✅ |
| `YoMenu#addItem(int, CharSequence)` | `public abstract YoMenu addItem(int, java.lang.CharSequence)` | ✅ |
| `YoMenu#addItem(int, CharSequence, int)` | `public abstract YoMenu addItem(int, java.lang.CharSequence, int)` | ✅ |
| `YoMenu#addItem(YoMenu.Item)` | `public abstract YoMenu addItem(YoMenu$Item)` | ✅ |
| `YoMenu#build` | `YoMenu$Builder.build(): YoMenu` | ✅ |
| `YoMenu#show` | `public abstract void show()`（另有 `dismiss/toggle/isShowing/destroy`） | ✅ |
| `YoTabs.Builder#setOnContentInsetListener` | `YoTabs$Builder setOnContentInsetListener(YoTabs$OnContentInsetListener)` | ✅ |
| `YoTabs#getContentInsetPx` | `public abstract int getContentInsetPx()`（在 `YoTabs` 接口上；App `MainActivity:72-73` 正是 `YoTabs created = builder.build()` 后调用） | ✅ |
| `YoButton.Shape.CIRCULAR` | `public static final YoButton$Shape CIRCULAR`（枚举值：CAPSULE、CIRCULAR） | ✅ |
| `YoTextInput.InputType.TEXT` | `public static final YoTextInput$InputType TEXT`（TEXT/PASSWORD/EMAIL/NUMBER/PHONE） | ✅ |
| `YoPage.MenuItem` 构造器 | 7 个构造器全部存在，含 App 使用的 `MenuItem(int, CharSequence, int, Runnable)` 及带 `MenuStyle/IconSize/Badge/Drawable/Bitmap` 的重载 | ✅ |

App 侧实际调用点抽样（编译前人工核对，供审计参考）：

```text
AiChatPage.java:191  .setMenuMaxCount(3)
AiChatPage.java:192  .setMenuItems(List.of(
AiChatPage.java:193  new YoPage.MenuItem(MENU_NEW_CHAT, ...)
AiChatPage.java:489  .addItem(ACTION_COPY, ..., R.drawable.ic_ai_copy)
AiChatPage.java:508  menu.show();
MainActivity.java:72 created.setOnContentInsetListener(chat::setBottomInset);
MainActivity.java:73 chat.setBottomInset(created.getContentInsetPx());
```

### 公共 API 面完整性（全量、双向比较）

对本次构建的 **pre-R8 javac 输出（13:08，403 个 api class，来自当前工作区源码）** 与 **AAR 内 402 个 api class** 用 `javap -p -s` 提取每个类的字段/方法签名并做集合比较：

| 检查项 | 结果 |
|--------|------|
| pre-R8 公共 API 在 AAR 中缺失的成员 | **0** |
| pre-R8 公共类在 AAR 中整体缺失 | **0** |
| AAR 中存在、但当前源码构建中不存在的公共成员 | **0** |

即 AAR 的公开 API 面与"当前源码（含未提交改动）"的编译结果完全一致，既没缺新 API，也没有多余旧 API。

---

## 6. 编译验证

### 环境

| 项 | 值 |
|----|-----|
| 工程 | `D:\A_yoprogram\Github_Gallery\GGEC-Android-EarBuds` |
| Gradle | wrapper 9.6.0；发行版已缓存在 `GRADLE_USER_HOME`（`E:\Android_Studio_Data\ .gradle`），**--offline 首次即成功，无需下载** |
| AGP | 9.4.1（`com.android.application`/`library`） |
| JDK | `JAVA_HOME=D:\Dev\Env\Java\jdk-21`（21.0.12）；PATH 上的 `java` 是 17.0.2，但 `gradlew.bat` 走 `JAVA_HOME`，编译 Java 21 源码正常 |
| SDK | `E:\Android\Sdk`（`local.properties: sdk.dir=E\:\\Android\\Sdk`）；`platforms/android-37.2` ✅、`build-tools/37.0.0` ✅ 均已存在，**未安装任何组件** |
| 依赖解析 | 全部依赖已在 Gradle 缓存中，offline 成功 |

### 命令与结果

```powershell
cd D:\A_yoprogram\Github_Gallery\GGEC-Android-EarBuds
.\gradlew.bat :app:compileDebugJavaWithJavac --offline --console=plain
```

- 首次执行：`BUILD SUCCESSFUL in 46s`，exit 0，但 `:app:compileDebugJavaWithJavac UP-TO-DATE`（只是复用之前产物，不作为编译通过的证据）。

```powershell
.\gradlew.bat :app:compileDebugJavaWithJavac --offline --console=plain --rerun
```

- **强制重跑：`BUILD SUCCESSFUL in 27s`，exit code 0，`3 executed, 50 up-to-date`，`:app:compileDebugJavaWithJavac` 实际执行。**
- 产物：`app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes` 共 **236 个 class**，最新写入 14:44:35；无 `error:`、无缺失符号、无 youi API 相关 warning（仅 Gradle 10 弃用提示）。
- App 编译期确实解析了该 AAR（`app/build/intermediates/external_file_lib_dex_archives/debug/.../0_youi_kit-1.0.0-runtime.jar` 等中间产物）。

**结论：App 可按 `app/libs/youi_kit-1.0.0.aar` 正常编译（离线，0 错误）。**

---

## 7. 结论

1. **AAR 一致性：一致。** `app/libs/youi_kit-1.0.0.aar` = `Android-YoUI/releases/youi_kit-1.0.0.aar` = `youi_kit/build/outputs/aar/youi_kit-release.aar`，SHA-256 均为 `A5112DCE…1D41AD`；AAR 内 `classes.jar` 与本次构建的 `syncReleaseLibJars/classes.jar` 逐字节相同。
2. **AAR 是否为当前库的默认产物：是。** 它是 `youi_kit` release 变体（库自身 R8 混淆、`-repackageclasses 'o'`）经 `assembleRelease` 生成、再由 `exportReleaseAar` 以 `${versionName}` 重命名导出的标准产物；versionName=1.0.0（`youi_kit/build.gradle`），与文件名和 releases 目录一致。
3. **AAR 是否跟上源码（含未提交改动）：是。** AAR 时间（13:09:31）晚于源码最新 mtime（13:06:06）3 分 25 秒；R8 mapping id 与 AAR 唯一对应；mapping 反映未提交的 `DisplayFrame` 包重命名；usage.txt 含多个未跟踪新类；pre-R8 编译输出含未提交新包；且 AAR 公共 API 面与"当前源码编译结果"双向零差异。
4. **App 能否按该 AAR 编译：能。** App 使用的 19 个 `api` 类型、重点方法签名全部存在；`--offline --rerun` 强制重跑 `:app:compileDebugJavaWithJavac` **BUILD SUCCESSFUL**（exit 0，27 s，236 个 class），无缺失符号。

## 8. 局限与未覆盖项

- 本报告验证的是**编译期兼容性**，未执行 `:app:assembleRelease` / 宿主 R8 缩包（release 变体）、未安装运行、未做运行时/视觉验证；宿主 R8 场景下仅静态核对 consumer rules（`com.yohu.youi.api.**` keep）与 AAR 内已保留的公开签名。
- AAR 已被库自身 R8 混淆：非 `api` 包内部实现类被重命名/扁平化到 `o.*`，无法按内部类名逐项核对实现；本报告对 AAR 的类名级核对仅针对 `com.yohu.youi.api.**`（keep 规则保证其稳定）。
- `BuildConfig` 被 R8 从 AAR 移除，且 library 模块 BuildConfig 本就不含 `VERSION_NAME`，故 versionName 结论来自 Gradle 配置 + 导出文件名，而非 AAR 字节码。
- 时间/包含性证据基于 mtime、R8 mapping id、中间产物哈希与 API 面一致性交叉得出；mtime 可被保留或改写，但 mapping `pg_map_id` 与 `classes.jar` 哈希已将 AAR 绑定到 2026-10-19 13:09 的构建。
- 未修改任何源码或 AAR；临时解包目录：`E:\System\Temp\aar-verify-cf6d0f32625b424fa2cfddf7916c3ace`（可清理）。
