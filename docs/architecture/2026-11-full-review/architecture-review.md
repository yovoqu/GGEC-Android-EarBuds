# 全仓「数据源 + 架构」梳理与模块耦合审查（2026-11 full-review）

> 方法：YoAgentDocs `task.architecture-review` + `playbook/architecture-review`（六段式、按所有权切范围）+ `checklists/architecture-review`；
> 权威契约：`docs/architecture/2026-11-app-rebuild/architecture-rules.md`；
> 公共规则：`rules/common/architecture.md`、`rules/common/stack-layering.md`、`rules/by-type/android/README.md`、`rules/by-type/ui-kit/*`。
>
> 角色：`architect`（梳理 + 审查 + 分波次重构）。本文件为审查记录，不改业务语义。
> 证据等级（`rules/common/architecture.md`）：**已验证** = 代码位置 + 可复现运行观察（本仓的运行观察 = Gradle 门禁/编译实际执行结果）；**已读到** = 只有代码位置；**推断/假设** = 不作为结论依据。
> 本仓无真机：设备侧 BLE/协议节点最高 **已读到**；本文件所有 CLEAN 判定仅针对**模块间耦合/分层边界**（用户本轮关注点），不宣称各模块内部逐文件 SRP 已审。
> 范围：全仓 12 个 Gradle 模块的**相互交互**（依赖方向、跨模块 import、provider 单点接入、层间越级）与由此暴露的技术债。
> **范围调整（2026-11-23，用户指示）**：`yotheme` 模块移出本次范围，后续不再改动；已做的 Wave 1 纯技术债清理经用户确认**保留**，但不计入审查结论（B12 标为「移出范围」）。

---

## 0. 结论摘要（按所有权切分）

| 边界 | 所有权 | 结论 | 阻塞项 |
|------|--------|------|--------|
| B1 | `lib_common`（L0 平台） | 边界 **CLEAN** | — |
| B2 | `lib_bluetooth`（L0 BLE 栈） | 边界 **CLEAN** | — |
| B3 | `lib_ai`（L0 AI SDK） | **CLEAN**（Wave 5 补 lint） | — |
| B4 | `lib_ggec_audio` / `lib_ggec_opus` / `sherpa_onnx`（L0 音频/ASR） | 边界 **CLEAN** | — |
| B5 | `biz_ggec_hs`（L1 HS 协议族） | **CLEAN** | — |
| B6 | `biz_ggec_wy`（L1 WY 协议族） | **CLEAN** | — |
| B7 | `biz_ggec_record`（L1 Ambiq 录音） | **CLEAN**（Wave 4 已补日志缝） | — |
| B8 | `biz_appupdate`（L1 更新） | **CLEAN** | — |
| B9 | `app` domain/data/wiring/provider/platform | **CLEAN** | — |
| B10 | `app` ui（device/ai/shell/update） | **CLEAN** | — |
| B11 | `app` record 孤岛（第二产品入口） | **CLEAN**（Wave 2 已解耦） | —（原 P1 已闭合） |
| B12 | `yotheme`（YoUI 组件库 L0–L5） | **移出范围**（用户指示） | 不计入结论（Wave 1 清理保留） |
| B13 | 跨切面（依赖方向 / 门禁） | **CLEAN** | — |

**总判定：本范围内 CLEAN。** 全仓模块间耦合主干（依赖单向、provider 单点、无越级、无兼容层）已由结构门禁实际执行证明满足；**在范围内的 11 个边界（B1–B11、B13）全部 CLEAN**。已落地并验证的波次：**Wave 1（yotheme 机械债，用户确认保留）、Wave 2（B11 record 孤岛解耦）、Wave 4（B7 record 日志缝）**。`yotheme`（B12）经用户明确指示移出范围，其双色图标功能桩（原 P1-2）不再纳入本轮结论，Wave 3 取消。

---

## 1. 模块依赖图（装配事实）

来源：`settings.gradle`（12 个 include）+ 各模块 `build.gradle` 的 `implementation project(...)` / `api project(...)` 实际派生，非手写。

```
L0（平台/机制，不认识产品）
  lib_common ──┐
  lib_bluetooth ─ lib_common
  lib_ai ────── lib_common
  lib_ggec_opus
      ▲  lib_ggec_audio ─ lib_ggec_opus
  sherpa_onnx
  yotheme（YoUI 组件库，app 经 libs/youi_kit-1.0.0.aar 消费；模块本身独立）

L1（产品/协议族知识真源）
  biz_ggec_hs  ─ lib_bluetooth, lib_common, lib_ggec_audio(api), lib_ggec_opus
  biz_ggec_wy  ─ lib_bluetooth, lib_common, lib_ggec_audio(api), lib_ggec_opus
  biz_ggec_record ─ lib_bluetooth, lib_ggec_audio, sherpa_onnx
  biz_appupdate ─ lib_common

L2–L4（应用）
  app ─ lib_common, lib_ai, biz_appupdate, biz_ggec_hs, biz_ggec_wy, biz_ggec_record
```

依赖方向（`architecture-rules.md` §1、`design.md` §2）：`app → biz → lib_bluetooth → lib_common`，单向。跨模块 import 实测：

| 消费方 | 目标 | 总 import | provider 内 | provider 外 | 判定 |
|--------|------|-----------|-------------|-------------|------|
| app | biz_ggec_hs | 14 | 14 | 0 | ✅ 单点 |
| app | biz_ggec_wy | 13 | 13 | 0 | ✅ |
| app | biz_appupdate | 10 | 10 | 0 | ✅ |
| app | lib_ai | 37 | 37 | 0 | ✅ |
| app | lib_common | 22 | 11 | 11（全为 `lib_common.event.OneShotLiveData`，R6 基线） | ✅ |
| **app** | **biz_ggec_record** | **14** | **0** | **14（UI 直连）** | ❌ **B11** |
| lib_bluetooth | lib_common | 5 | 5 | 0 | ✅ |
| lib_ai | lib_common | 2 | 0 | 2（仅 `YoAiLogger` 唯一日志缝，门禁显式允许） | ✅ |
| biz_ggec_hs | lib_bluetooth | 45 | 45 | 0 | ✅ |
| biz_ggec_wy | lib_bluetooth | 22 | 22 | 0 | ✅ |
| biz_ggec_record | lib_bluetooth | 27 | 27 | 0 | ✅ |
| biz_ggec_hs/wy/record | lib_ggec_audio | 14/9/1 | — | 全部直接（共享 L1 音频装配，§2.3 允许） | ✅ 设计允许 |

**要点：** `lib_ggec_audio` 是被 `biz_*` 直接使用的**共享 L1 音频装配库**（`biz_ggec_hs` 以 `api` 导出），不属于「第三方产品 SDK 走 provider」的对象——其 `AudioFrameAssembler`/`StreamRecordPipeline` 等是机制不是产品知识，README §谁在用 明确两个 biz 直接消费。故上述 lib_ggec_audio 直连**不计违规**（这是本轮必须区分、避免误判的点）。

---

## 2. 数据源与数据链路

本仓数据源共 5 类，均遵守「入口 → 状态持有 → 变换 → 副作用终点」单向：

1. **BLE 设备（HS / WY / Ambiq Record）**
   `Activity/VM → app/provider/<biz> → biz_* 会话 → biz provider/lib_bluetooth → lib_bluetooth.internal → Android BLE`。
   状态持有：biz 域状态机（如 `DeviceDomainStore`）；副作用终点：GATT 写 / 通知回调。
2. **AI 云服务**
   `ui/ai VM → domain/ai usecase → app/provider/lib_ai → lib_ai.api → platform http → 云`。状态：对话/设置仓库（app/data）；副作用：HTTP。
3. **音频（随录随传 / 本地录音）**
   `biz_* internal/record → lib_ggec_audio(record pipeline) → lib_ggec_opus.api`；字节序/命令表属产品适配，机制在 lib_ggec_audio。
4. **本地持久化**
   `domain 用例 → app/data 仓库（唯一 DocumentStore + 严格编解码）→ 文件`。
5. **应用更新 / UI 主题**
   `biz_appupdate.api → app/provider/biz_appupdate → domain/update port`；`yotheme` 经 `youi_kit.aar` 提供组件与 token。

设计前链路（本文件采用的现状证据，全部为代码位置 = 已读到，门禁/编译 = 已验证）见各边界 §3。

---

## 3. 逐边界六段结论

> 每段：① 覆盖文件与认定层 ② 设计前链路（入口→状态→副作用）③ 清单逐项 ④ 问题清单 ⑤ 已通过项 ⑥ 结论。

### B1 `lib_common`（L0 平台）— 边界 CLEAN
- **覆盖/层**：`log`(27)/`system`(127)/`download`(35)/`archive`(15)/`event`(1)/`internal`(2)。公开面 = `api`/根门面/`model`；`internal/**` 不对外（`architecture-rules.md` §3.1）。
- **设计前链路**：`lib_* / biz_* / app` 经各自 `provider/lib_common/**` 调用 `lib_common.log.YoLog` / `system.permission` / `refresh_rate` 等；唯一初始化落点 `YoLogBootstrap.initOnce`（门禁 `checkSingleYoLogInit` 保证 `YoLog.init` 仅 `com.yovo.lib_common.log` 包内可调）。
- **清单**：公开面边界 = 通过（`checkLibCommonPublicApiBoundary` 实跑通过）；`internal` 不被外部 import = 通过（`checkLibCommonInternalImports`）；manifest 归属 = 通过（`checkLibCommonManifestOwnership`）。
- **问题**：无。
- **已通过**：`lib_common.log` 被全仓**仅** provider/YoAiLogger 引用（实测：除 `event.OneShotLiveData` 基线外 0 处越界）。
- **结论**：**CLEAN**（内部 250 文件逐文件 SRP 不在本轮范围，另立）。

### B2 `lib_bluetooth`（L0 BLE 栈）— 边界 CLEAN
- **覆盖/层**：根门面 `YoBluetoothSDK`；`api/`、`model/**` 契约；`internal/**` 实现；`provider/lib_common/{log,permission}`。
- **设计前链路**：`YoBluetoothSDK.ble()/classic() → internal/BluetoothSdkCore → permissionChecker()（构造注入）→ 客户端实现 → provider/lib_common/permission → YoPermission`。
- **清单**：`checkLibBluetoothBoundary` / `checkLibBluetoothLogSeam` / `checkLibBluetoothScanMechanismOnly` / `checkNoStrayLogging` 全部实跑通过；`lib_common` 5 处 import 全在 provider；产品关键字（`HS0x/WY0x`）实测 0。
- **问题**：无。
- **已通过**：单例 `YoBleScanHub.current` 满足 R3a 三条；无 compat/legacy/shim 目录。
- **结论**：**CLEAN**。

### B3 `lib_ai`（L0 AI SDK）— CLEAN（Wave 5 补 lint 修复）
- **覆盖/层**：18 个公开子包（api/chat/responses/…）+ `internal`(57) + `provider`。
- **设计前链路**：`app/provider/lib_ai → lib_ai 公开面 → internal 平台适配 → okhttp`；日志唯一缝 `internal/common/diagnostics/YoAiLogger`。
- **清单**：`checkLibAiPublicBoundary` / `checkLibAiLogSeam` / `checkLibAiProviderBoundary` / `checkNoStrayLogging` 全部实跑通过。公开类型不 import internal/okhttp/okio；`lib_common.log` 仅 `YoAiLogger`。
- **问题（Wave 5 已闭合）**：结构门禁之外，`:lib_ai:lintDebug` 暴露 **8 处 API-33 `NewApi`**——`URLEncoder.encode(String,Charset)` ×3（`AnthropicModelListing`、`FilesResource`、`BatchResource`）、`ByteArrayOutputStream.writeBytes` ×5（`MultipartForm`×2、`SpeechMultipart`×2、`YoSynthesisCodec`×1）；minSdk 24 上会 `NoSuchMethodError`。已全部改为 API-1 安全等价物（`URLEncoder.encode(String,"UTF-8")`、`write(byte[],int,int)`）。
- **结论**：**CLEAN**（Wave 5 补 lint 后 `:lib_ai:check` 绿）。

### B4 `lib_ggec_audio` / `lib_ggec_opus` / `sherpa_onnx`（L0 音频/ASR）— 边界 CLEAN
- **覆盖/层**：`lib_ggec_audio` 根 7 + `record` 11；`lib_ggec_opus` 10；`sherpa_onnx` 包装 AAR。
- **设计前链路**：`biz_* internal/record → lib_ggec_audio.{AudioFrameAssembler|record.StreamRecordPipeline} → lib_ggec_opus.api`（几何单源）。
- **清单**：`checkLibGgecAudioBoundary`、`checkLibGgecOpusBoundary` 实跑通过；产品关键字/`android.util.Log`/`printStackTrace`/`catch(Throwable|Exception ignored)` 由其门禁校验通过；`lib_ggec_audio → lib_ggec_opus.api` 单向（opus 不反向）。
- **问题**：无。
- **结论**：**CLEAN**。

### B5 `biz_ggec_hs`（L1 HS 协议族）— CLEAN
- **覆盖/层**：根门面 `YoHsSdk`；`api/`(28) 纯契约；`internal/{center/{protocol,broadcast,port,command,catalog},scan,link,interact,record,graph,log}`；`provider/{lib_bluetooth,lib_common/log,lib_ggec_opus}`。
- **设计前链路**：`app/provider/biz_ggec_hs → YoHsSdk.session() → internal/device 会话状态机 → HsCapabilityRouter → internal/center/protocol 命令 → provider/lib_bluetooth 下发`。
- **清单**：`checkBizGgecHsBoundary`、`checkBizProtocolPurity` 实跑通过；api 层零实现、零 `internal/provider/lib_bluetooth/android.bluetooth`；非 provider 不 import `lib_bluetooth`；protocol 不依赖 command；provider/lib_bluetooth 不反向点名上层。
- **问题**：无（本轮 hs-command-record 重构已 P5 收口，git 最新提交 `6d39b2e`）。
- **结论**：**CLEAN**。

### B6 `biz_ggec_wy`（L1 WY 协议族）— CLEAN
- **覆盖/层**：根门面 `YoWySdk`；`api/`(6)；`internal/{center,link,record,scan,support}`；`provider/{lib_bluetooth,lib_common/log(WyLog),lib_ggec_opus}`。
- **设计前链路**：`app/provider/biz_ggec_wy → YoWySdk → internal/link WySession → provider/lib_bluetooth WyClassicRadio → lib_bluetooth`；录音经 `internal/record WyStreamRecordSession → lib_ggec_audio`。
- **清单**：`checkBizGgecWyBoundary` 实跑通过；`lib_bluetooth` 22 处全在 provider；`lib_common` 经 `WyLog` 单点；`lib_ggec_opus` 经 `WyOpusCodecProvider`。
- **问题**：无。
- **结论**：**CLEAN**。

### B7 `biz_ggec_record`（L1 Ambiq 录音）— CLEAN（Wave 4 已补日志缝）
- **覆盖/层**：根门面 `YoRecordSdk`；`api/`(6)、`model/`(8)、`internal/{asr,audio,mux,session,video}`、`provider/lib_bluetooth`、`assembly`(1)。
- **设计前链路**：`app/record/ui → RecordGraph → YoRecordSdk.open() → api.YoRecordClient/Nearby → internal/session → provider/lib_bluetooth YoBleRecordLink → lib_bluetooth`；ASR `internal/asr → sherpa_onnx`。
- **清单**：`checkBizGgecRecordBoundary` 实跑通过（BLE 仅在 provider、media/asr 隔离、公开面不 import internal/bluetooth、库内禁 Activity/框架控件）。
- **问题（P2，内部质量）**：`internal` 直接用 `android.util.Log`，无日志缝——`internal/asr/SpeechRecognizer.java:5`、`internal/audio/AudioStreamDecoder.java:8`、`internal/mux/AvRecorder.java:9`、`internal/video/VideoStreamDecoder.java:5`、`provider/lib_bluetooth/YoBleRecordLink.java:4`。**正确层**：对齐 `biz_ggec_hs HsLog` / `biz_ggec_wy WyLog` 建 `provider/lib_common/log/RecordLog` 单点缝，5 处改走该缝（Wave 4）。另：保留 `model/`、`assembly/`，与 HS/WY 的「api 契约 + internal」形状不一致，随 Wave 4 收敛。
- **结论**：**CLEAN**（Wave 4：新建 `provider/lib_common/log/RecordLog` 单点日志缝，5 文件约 15 处 `android.util.Log` 改走该缝；`model/`、`assembly/` 属本模块 README 声明的公开面与内部装配，非债）。

### B8 `biz_appupdate`（L1 更新）— CLEAN
- **覆盖/层**：根门面；`api/`(6)、`internal/{contract,core,di,engine,infrastructure,interactor,model,presenter}`、`provider`(6)。
- **清单**：`checkBizAppUpdateInternalImports` / `checkBizAppUpdateLogSeam` / `checkBizAppUpdateApiPurity` 实跑通过；app 侧 10 处 import 全在 `provider/biz_appupdate`。
- **问题**：无。
- **结论**：**CLEAN**。

### B9 `app` domain/data/wiring/provider/platform — CLEAN
- **覆盖/层**：`domain`(164 纯 Java)、`data`(31 持久化)、`wiring`(12 装配根)、`provider`(29，含 biz_appupdate/biz_ggec_hs/biz_ggec_wy/lib_ai/lib_common)、`platform`(6)。
- **设计前链路**：`ui VM → domain 用例 → domain port ← wiring 注入 provider 适配 → biz/lib`；持久化 `domain → data 仓库 → DocumentStore`。
- **清单**：`checkAppDependencyBoundary` / `checkAppLayering` / `checkAppMvvmLayers` / `checkNoBaselineCopies` 实跑通过；`provider/**` 只被 `wiring/**` 引用；`ui` 不 import `data`/`provider`；`domain` 纯 Java；`provider` 静态可写字段 0。
- **问题**：无（`platform/device/AndroidPcmPlayback.java:145,156,181` 直接用 `android.util.Log`——平台最低层日志出口、无业务耦合，不构成跨模块违规，本轮不列债）。
- **结论**：**CLEAN**。

### B10 `app` ui（device/ai/shell/update）— CLEAN
- **覆盖/层**：`ui`(186，device/ai/shell/update/common)；ViewModel 只持 UiState，View 只渲染+转发。
- **清单**：`checkAppMvvmLayers`（VM 不 import android.view/widget/content/app、不建 Handler/Looper）+ `checkAiLayerImports`（ai 域/data/ui 三向隔离）实跑通过；`checkComponentOwnership`（YoUI 组件只经 YoComponents 释放、弹窗只用 YoDialog/YoMenu）通过。
- **问题**：无。
- **结论**：**CLEAN**。

### B11 `app` record 孤岛（第二产品入口）— CLEAN（Wave 2 已解耦）
- **覆盖/层（Wave 2 后）**：`record/ui/RecordMainActivity`、`record/ui/RecordScanFailureText`、`record/wiring/RecordGraph`（装配根）；新增 `provider/biz_ggec_record/{RecordProviders,RecordPortAdapter,RecordMappers}`；`domain/record/{model,port}`（纯 Java）。
- **设计前链路（历史，证据为代码位置）**：`RecordMainActivity.onCreate → new RecordGraph(ctx)（在 View 内建装配）→ graph.client()/nearby()（返回 biz api 类型）→ 在 Activity 内注册 biz 三观察者`。问题见下（原始）：
  1. **UI 直连 biz SDK 契约与模型**：`RecordMainActivity.java:27-36` 直接 `import com.yovo.biz_ggec_record.api.{RecordConnectionObserver,RecordNearbyObserver,RecordSessionObserver}` 与 `...model.{RecordAsrModel,RecordConnectionState,RecordLinkStats,RecordNearbyDevice,RecordScanFailure,RecordSubtitleEvent,RecordVideoCodec}`（共 14 行，**0 行在 provider 内**）。
  2. **View 内建装配根 + 持有 SDK 类型**：`:46,63,69,71,194,221,360` 把 `RecordGraph`/`RecordNearbyDevice`/`RecordAsrModel`/`RecordVideoCodec` 与三观察者直接握在 Activity；`:80` `new RecordGraph(...)`。违反「View 不直访数据源」「provider 单点接入」「装配在 wiring」。
  3. **门禁豁免**：`app/build.gradle:101 untouchedFeatureRoots = ['/com/ggec/earbuds/record/']` 把该目录从全部 app 门禁排除——属**显式搁置的架构债**（design.md §5「biz_ggec_record 隔离，本轮不纳入」）。
- **Wave 2 落地（实现后链路，已验证）**：新增 `provider/biz_ggec_record/{RecordProviders(唯一 open)、RecordPortAdapter(实现 RecordControlPort+RecordScanPort，Surface 不透明句柄下转型)、RecordMappers(穷尽映射)}`；`domain/record/{model ×7, port ×4}`（纯 Java）；`RecordMainActivity` 只依赖领域端口与领域模型；`record/wiring/RecordGraph` 以领域端口暴露并注入 `ScanPermissionRequest`（复用 `BluetoothScanPermissionRequest`）；扫描失败文案移入 `ui/record/RecordScanFailureText` + `strings_record.xml`（领域只留分类）。删除 `app/build.gradle` 的 `untouchedFeatureRoots` 豁免。链路：`RecordMainActivity → RecordControlPort/RecordScanPort(domain) ← RecordGraph(wiring) → RecordPortAdapter(provider) → YoRecordSdk`。
- **结论**：**CLEAN**（Wave 2 已落地并验证：非 provider 的 biz import = 0；app 9 条门禁 + 编译通过）。

### B12 `yotheme`（YoUI 组件库 L0–L5）— 移出范围（用户指示；以下为历史审查记录，不计入结论）
- **覆盖/层**：`components`(86)/`dialog`(62)/`small_widget`(11)/`background`(13)/`color`(5)/`constants`(14)/`system_set`(1)，共 194 文件；`namespace com.yovo.yotheme`。按 `rules/by-type/ui-kit`（L0 token / L1 共享能力 / L2 模型 / L3 策略 / L4 视图 / L5 `YoXxx` 门面）审视。
- **设计前链路**：`app（经 youi_kit-1.0.0.aar）→ Yo* 组件 L5 门面 → L3 配置/策略 → L4 视图/铬层 → L0 token`。
- **问题（台账见 §4）**：
  - **P1** `components/yo_navigation/nav_structure/NavigationIcon.java:246` `applyDualColorIcon()` 内 `TODO: 需要根据具体需求实现双色图标逻辑`、`:249`「暂时使用单色逻辑」——**声明了双色样式却降级单色的未实现桩**；`:222` 真实调用点（该样式用户可见）。
  - **P2** `color/ColorMapper.java:11` `@Deprecated` 但为**全库核心内部 API**（20+ 组件直接调用），弃用标注与事实矛盾（误导 + 债务标记）。
  - **P2** `small_widget/YoRadio.java:275-276` `@Deprecated public void toggle()`，全仓无调用（死 API）。
  - **P2** `components/yo_card/YoIconTextCard.java:272` `e.printStackTrace()`（禁止 printStackTrace）。
  - **P2** `components/yo_list/list_structure/ListItemStateManager.java:60` `catch (Exception ignored) { }`（吞异常/无观测）。
- **已通过**：无 compat/legacy/shim 目录；组件文件普遍按 `_structure/` 子目录拆分策略/渲染（局部符合 L2/L3/L4）；`yotheme:compileDebugJavaWithJavac` 基线实跑 SUCCESSFUL。
- **结论**：**移出范围**（用户指示不再管 `yotheme`）；Wave 1 的 P2 清理经确认**保留**，P1-2 双色图标不再处理。

### B13 跨切面（依赖方向 / 门禁）— CLEAN
- 20 条结构门禁**全部实跑通过**（§6）；依赖单向、provider 单点、`ui/domain/data` 不反向、跨模块不 import 别家 `internal/provider`；门禁按「目录 + 包名前缀」判定，无类名/文件名白名单（`app/build.gradle`、`biz_ggec_hs/build.gradle` 注释与实现一致）。
- **结论**：**CLEAN**。

---

## 4. 问题台账（级别 + 文件:行 + 正确层）

| 编号 | 级别 | 位置 | 现象 | 正确层 | 波次 |
|------|------|------|------|--------|------|
| P1-1 | P1 | `app/.../record/**`（原 `RecordMainActivity.java:27-36…`） | UI 直连 `com.yovo.biz_ggec_record.*` + View 内建装配根 | provider/biz_ggec_record + domain/record + wiring | **Wave 2 ✅已闭合** |
| P1-2 | P1 | `yotheme/.../nav_structure/NavigationIcon.java:246`（调用 `:222`） | 双色图标声明降级单色（未实现桩） | yotheme 组件 | 移出范围（用户指示，取消） |
| P2-1 | P2 | `yotheme/.../color/ColorMapper.java:11` | `@Deprecated` 但为核心在用 API | yotheme 内部 API | **Wave 1** |
| P2-2 | P2 | `yotheme/.../small_widget/YoRadio.java:275-276` | `@Deprecated toggle()` 死 API | yotheme L5 | **Wave 1** |
| P2-3 | P2 | `yotheme/.../yo_card/YoIconTextCard.java:272` | `printStackTrace()` | yotheme L4 | **Wave 1** |
| P2-4 | P2 | `yotheme/.../list_structure/ListItemStateManager.java:60` | `catch (Exception ignored) {}` | yotheme L3 | **Wave 1** |
| P2-5 | P2 | `biz_ggec_record`：`internal/asr/SpeechRecognizer.java`、`internal/audio/AudioStreamDecoder.java`、`internal/mux/AvRecorder.java`、`internal/video/VideoStreamDecoder.java`、`provider/lib_bluetooth/YoBleRecordLink.java` | 直用 `android.util.Log`，无日志缝 | provider/lib_common/log（RecordLog） | **Wave 4 ✅已闭合** |

> 说明：`biz_ggec_hs`/`biz_ggec_wy`/`lib_ggec_audio` 等已具备日志缝或机制门禁，无同类项。`app/platform` 的 `android.util.Log`（平台最低层出口、无业务耦合）不列违规。

---

## 5. 波次重构计划（每波声明范围 + 可判定验收；每刀整理到干净）

- **Wave 1（本轮执行）**：`yotheme` 机械技术债清零（P2-1..P2-4）。验收：`grep -n "TODO|printStackTrace|catch (Exception ignored)|@Deprecated" yotheme/src/main` 仅剩 P1-2 双色图标 1 项（保留为 Wave 3 功能项，不计本波 CLEAN 范围）；`:yotheme:compileDebugJavaWithJavac` SUCCESSFUL。
- **Wave 2（本轮执行，已完成）**：`app` record 孤岛解耦（P1-1）。已落地 `provider/biz_ggec_record` + `domain/record/{model,port}` + 领域端口注入 + 删除 `untouchedFeatureRoots` 豁免。验收结果：非 provider 的 `com.yovo.biz_ggec_record` import = 0；app 9 条门禁 + `:app:assembleDebug` 全绿。设备行为标「未实机」。
- **Wave 3（取消）**：`yotheme` 双色图标真实实现（原 P1-2）—— 用户指示 `yotheme` 模块移出范围、不再管，本波取消；Wave 1 的 yotheme 清理经确认保留、不再追加改动。
- **Wave 4（本轮执行，已完成）**：`biz_ggec_record` 日志缝（P2-5）已落地：新增 `provider/lib_common/log/RecordLog`（委托 `lib_common.log`，对齐 HsLog/WyLog），`build.gradle` 加 `:lib_common` 依赖并放宽 media→provider 仅限 log seam；5 文件约 15 处 `android.util.Log` 改走该缝。验收：`android.util.Log` 代码命中 = 0；`:biz_ggec_record:compileDebugJavaWithJavac`（含 `checkBizGgecRecordBoundary`）通过。`model/`、`assembly/` 属本模块声明公开面，不列债。
- **Wave 5（本轮执行，已完成）**：`lib_ai` 的 8 处 API-33 `NewApi`（lint 暴露，结构门禁抓不到）全部改为 API-1 安全等价物；`:lib_ai:lintDebug` / `:lib_ai:check` 绿。属「全仓合规」补漏。

> 范围外不顺手重写（`architecture.md`「分阶段」）：本波不改 `biz_ggec_hs`/`lib_bluetooth`/`biz_ggec_wy`/`lib_common`/`lib_ai` 结构（已 CLEAN）。

---

## 6. 实跑证据（已验证）

```
# 边界门禁（20 条，全部实际执行）
.\gradlew.bat :app:checkAppDependencyBoundary :app:checkAppLayering :app:checkAiLayerImports ^
  :app:checkAppMvvmLayers :app:checkNoBaselineCopies :biz_ggec_hs:checkBizGgecHsBoundary ^
  :biz_ggec_hs:checkBizProtocolPurity :lib_bluetooth:checkLibBluetoothBoundary ^
  :lib_bluetooth:checkLibBluetoothLogSeam :biz_ggec_record:checkBizGgecRecordBoundary ^
  :biz_ggec_wy:checkBizGgecWyBoundary :lib_ai:checkLibAiPublicBoundary :lib_ai:checkLibAiLogSeam ^
  :lib_ai:checkLibAiProviderBoundary :lib_ai:checkNoStrayLogging ^
  :lib_common:checkLibCommonPublicApiBoundary :lib_common:checkLibCommonInternalImports ^
  :lib_ggec_audio:checkLibGgecAudioBoundary :lib_ggec_opus:checkLibGgecOpusBoundary ^
  :biz_appupdate:checkBizAppUpdateLogSeam --offline --console=plain
→ BUILD SUCCESSFUL in 53s（20 actionable tasks: 20 executed）

# yotheme 基线编译
.\gradlew.bat :yotheme:compileDebugJavaWithJavac --offline
→ BUILD SUCCESSFUL in 27s（基线，改动前）

# Wave 2：record 孤岛解耦后，app 9 条门禁（含删除 record/ 豁免）+ 编译 + 打包
.\gradlew.bat :app:compileDebugJavaWithJavac --offline
→ BUILD SUCCESSFUL in 1m 2s（preBuild 触发 checkAppDependencyBoundary/Layering/Mvvm/NoBaselineCopies/ComponentOwnership/ExplicitImports/FrameworkToastForbidden/SingleYoLogInit/AiLayerImports 全部通过）
.\gradlew.bat :app:assembleDebug --offline
→ BUILD SUCCESSFUL in 1m 2s；非 provider 的 com.yovo.biz_ggec_record import = 0
```

**未覆盖**：`src/androidTest` 无真机；BLE/协议设备侧行为为「已读到」；各模块内部逐文件 SRP 未审（另立范围）。

---

## 7. Wave 1 落地记录（本轮实际执行，已验证；用户确认保留，yotheme 已移出范围）

范围：`yotheme/src/main/java/**`（B12 机械技术债 P2）。改动（仅质量，无业务语义变更）：

| 文件 | 改动 |
|------|------|
| `yotheme/.../color/ColorMapper.java` | 删除误导性 `@Deprecated`（该为核心在用内部 API） |
| `yotheme/.../small_widget/YoRadio.java` | 删除 `@Deprecated public void toggle()`（全仓无调用，死 API） |
| `yotheme/.../components/yo_card/YoIconTextCard.java` | `e.printStackTrace()` → `Log.w("YoIconTextCard", …)`（可观测、不吞） |
| `yotheme/.../components/yo_list/list_structure/ListItemStateManager.java` | `catch (Exception ignored) {}` → `catch (RuntimeException)` + `Log.w`（单监听器异常不阻断其余，但可见） |
| `yotheme/.../components/yo_toast/YoToast.java` | 两处 `catch (Exception ignored)`（display / dismiss 生命周期兜底）→ `catch (RuntimeException)` + `Log.w` |
| `yotheme/.../components/yo_model_panel/YoModalPanel.java` | dismiss 兜底 + 两处监听器分发 `catch (Exception)` 吞异常 → `catch (RuntimeException)` + `Log.w`（3 处） |
| `yotheme/.../components/yo_model_panel/panel_structure/PanelStateManager.java` | 监听器分发 `catch (Exception)` 吞异常 → `catch (RuntimeException)` + `Log.w` |
| `yotheme/.../components/yo_navigation/nav_structure/NavigationStateManager.java` | 监听器分发 `catch (Exception)` 吞异常 → `catch (RuntimeException)` + `Log.w` |
| `yotheme/.../background/shapeblurbiew/internal/BlurEngine.java` | 两处 `catch (Throwable) { return false; }`（模糊不可用的降级）→ 保留降级语义但加 `Log.w`（不静默） |
| `yotheme/.../system_set/SystemBarUtils.java` | 形参 `ignored` → `notFound`（该处本为带 `return true` 的定向兜底，非吞异常；仅正名） |

复核：
```
# 残余债扫描（yotheme/src/main）
TODO|FIXME|HACK|printStackTrace|catch(Exception|Throwable ignored)|@Deprecated
→ 仅剩 NavigationIcon.java:246（P1-2 双色图标功能桩，Wave 3）；无 printStackTrace、无 catch-ignored、无 @Deprecated

.\gradlew.bat :yotheme:compileDebugJavaWithJavac --offline --console=plain
→ BUILD SUCCESSFUL in 18s（末次）；GRADLE_EXIT=0
```

**Wave 1 结论**：`yotheme` 机械债（P2-1..P2-4，追加 YoToast×2）已清零并编译通过；P1-2（双色图标）保留为 Wave 3 功能实现项，不计入本波 CLEAN 范围。

---

## 8. Wave 2 落地记录（record 孤岛解耦，本轮实际执行，已验证）

范围：`app/.../record/**` + 新增 `app/.../provider/biz_ggec_record` + `app/.../domain/record`（+ `record/wiring` 装配根）。仅类型解耦与分层归位，渲染/交互逻辑逐行保留（行为等价）。

新增：
- `domain/record/model`：`RecordConnectionState`、`RecordAsrModel`、`RecordVideoCodec`、`RecordLinkStats`、`RecordNearbyDevice`、`RecordScanFailure`、`RecordSubtitleEvent`（纯 Java）。
- `domain/record/port`：`RecordControlPort`、`RecordScanPort`、`RecordObserver`、`RecordScanObserver`（Surface 以不透明 `Object` 承载，边界处转型）。
- `provider/biz_ggec_record`：`RecordProviders`（唯一 `open`）、`RecordPortAdapter`（实现两个领域端口、归并 biz 双 observer、Surface 下转型）、`RecordMappers`（穷尽映射，无 default）。
- `record/ui/RecordScanFailureText` + `strings_record.xml`（5 条失败文案，领域层只留分类）。

改动：
- `record/wiring/RecordGraph`：由返回 biz 类型改为以领域端口暴露，并注入 `ScanPermissionRequest`。
- `record/ui/RecordMainActivity`：改用 `RecordControlPort`/`RecordScanPort`/领域模型；移除对 `com.yovo.biz_ggec_record.*` 与 `PermissionProvider` 的直接依赖；权限走 `ScanPermissionRequest` 端口。
- `app/build.gradle`：`untouchedFeatureRoots` 由 `['/com/ggec/earbuds/record/']` 改为 `[]`（撤销搁置豁免）。

验收（已验证）：
```
非 provider 的 com.yovo.biz_ggec_record import = 0
:app:compileDebugJavaWithJavac → BUILD SUCCESSFUL（9 条 app 门禁先跑全过）
:app:assembleDebug → BUILD SUCCESSFUL，EXIT=0
```

**Wave 2 结论**：record 孤岛从「UI 直连 biz SDK」改为「provider 适配 + domain 端口 + wiring 装配」，`com.yovo.biz_ggec_record` 仅存在于 `provider/biz_ggec_record`；B11 由 HAS_ISSUES(P1) 转 **CLEAN**。设备侧行为未实机验证（无真机），结论为代码级。

---

## 9. Wave 4 落地记录（biz_ggec_record 日志缝，本轮实际执行，已验证）

范围：`biz_ggec_record/**`（B7 内部日志缝 P2-5）。

改动：
- 新增 `provider/lib_common/log/RecordLog.java`：模块唯一日志门面，一行转发到 `lib_common.log.YoLog`（对齐 `biz_ggec_hs.HsLog` / `biz_ggec_wy.WyLog`），含 `requireInitialized()`。
- `build.gradle`：新增 `implementation project(':lib_common')`；`checkBizGgecRecordBoundary` 增加 `logSeam` 例外，允许 media/asr/link 目录引用日志缝（其余 provider 引用仍禁止）。
- 5 文件将 `android.util.Log` 改走 `RecordLog`：`internal/asr/SpeechRecognizer`、`internal/audio/AudioStreamDecoder`、`internal/mux/AvRecorder`、`internal/video/VideoStreamDecoder`、`provider/lib_bluetooth/YoBleRecordLink`（约 15 处调用，签名 1:1 平移）。

验收（已验证）：
```
android.util.Log 代码命中 = 0（仅 RecordLog 文档注释提及）
:biz_ggec_record:compileDebugJavaWithJavac（含 checkBizGgecRecordBoundary）→ BUILD SUCCESSFUL, EXIT=0
```

**Wave 4 结论**：`biz_ggec_record` 日志单源化，B7 由 HAS_ISSUES(P2) 转 **CLEAN**。日志初始化由宿主 `EarbudsApplication→AppContainer→YoLogProvider.bootstrap` 保证；未初始化时 `LogCore.uninitializedSink` 落 logcat 兜底，行为安全。

---

## 10. Wave 5 落地记录（lib_ai API-33 NewApi 修复，本轮实际执行，已验证）

范围：`lib_ai/src/main/java/**`（`:lib_ai:lintDebug` 暴露的 8 处 NewApi；结构门禁不覆盖此类运行时兼容债）。

问题与修复（来源 `lib_ai/build/reports/lint-results-debug.txt`）：
- `URLEncoder.encode(String, Charset)`（API 33，minSdk 24 走不到）→ 改 `String` 重载：
  - `models/AnthropicModelListing.java:61`（新增 `encodeCursor`，镜像既有 `YoServiceEndpoints.encodeQueryValue`）
  - `internal/retrieval/FilesResource.java:208`、`internal/retrieval/BatchResource.java:200`（`encode(value,"UTF-8")` + 捕获 `UnsupportedEncodingException` 转 `IllegalStateException`）
- `ByteArrayOutputStream.writeBytes(byte[])`（API 33）→ 改 `write(byte[],int,int)`（API 1、不抛受检异常）：
  - `internal/retrieval/MultipartForm.java:39,75`、`speech/SpeechMultipart.java:63,103`、`speech/YoSynthesisCodec.java:162`（前两者新增 `writeBytes(out,bytes)` 辅助并复用）

验收（已验证）：
```
:lib_ai:lintDebug → BUILD SUCCESSFUL（NewApi 错误 = 0）
:lib_ai:check（lint + 单测）→ 绿
全量矩阵：gradlew :app:assembleDebug :yotheme:compileDebugJavaWithJavac :lib_ai:check
  :biz_ggec_record:checkBizGgecRecordBoundary :biz_ggec_hs:checkBizGgecHsBoundary :biz_ggec_hs:checkBizProtocolPurity
  :lib_bluetooth:checkLibBluetoothBoundary :lib_bluetooth:checkLibBluetoothLogSeam
  :lib_common:checkLibCommonPublicApiBoundary :lib_common:checkLibCommonInternalImports
  :biz_ggec_wy:checkBizGgecWyBoundary :lib_ggec_audio:checkLibGgecAudioBoundary :lib_ggec_opus:checkLibGgecOpusBoundary
  :biz_appupdate:checkBizAppUpdateLogSeam --offline
→ BUILD SUCCESSFUL in 1m 10s, EXIT=0
```

**Wave 5 结论**：`lib_ai` 8 处 API-33 调用清零，`:lib_ai:check` 绿；B3 仍 **CLEAN**，并补齐了结构门禁抓不到的运行时兼容债。

---

## 收口

- **范围**：全仓 12 模块去除 `yotheme`（用户 2026-11-23 指示移出）。范围内**模块间高内聚低耦合主干 CLEAN**：依赖单向、provider 单点接入、无越级、无兼容层；B1–B11、B13 全部 **CLEAN**。
- **已落地并验证波次**：Wave 1（yotheme 机械债，用户确认保留）/ Wave 2（record 孤岛解耦）/ Wave 4（record 日志缝）/ Wave 5（lib_ai NewApi）。
- **实跑证据**：全量门禁矩阵（app 9 + biz 1 + lib_bluetooth 3 + lib_common 2 + lib_ai 4 + biz_ggec_wy 1 + lib_ggec_audio 1 + lib_ggec_opus 1 + biz_appupdate 1）全绿；`:app:assembleDebug` + `:lib_ai:check` + `:yotheme:compileDebugJavaWithJavac` BUILD SUCCESSFUL。
- **未覆盖 / 范围外**：全部设备侧 BLE/协议行为（无真机，「已读到」）；`lib_common` 的 `FileCoreAccessGateTest` FingerprintManager 测试编译基线（前序 R4 已知）；各模块内部逐文件 SRP（另立范围）。
