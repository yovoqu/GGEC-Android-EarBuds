# App + lib_ai 架构评审与目标设计（MVVM / Provider / YoLog 统一初始化）

> 日期：2026-11-10
> 范围：`app`（324 Java 文件）、`lib_ai`、`lib_common`(YoLog)、以及 App 对 `lib_bluetooth`/`biz_ggec_hs`/Youi 的使用方式
> 原则：**先完整审查、再设计、后分批实施；严禁简单打补丁**；MVVM 严格分层；SDK 统一 Provider；YoLog 单一初始化、**严禁重复初始化**、SDK 调用可自动初始化。

---

## 1. 现状审查（证据）

### 1.1 分层与 DI（已有较好骨架）
- 目录分层齐全：`ui/**`（9 个 ViewModel + View/Activity/Page）、`domain/**`（port / usecase / model / policy）、`data/**`（repository / gateway / store）、`platform/**`（Android 实现）、`wiring/**`（组合根）。
- 组合根：`EarbudsApplication → AppContainer → {AiGraph, DeviceGraph, ShellGraph}`（手动 DI，页面不 new 依赖）。
- 已有 Gradle 层校验（app/build.gradle）：`checkShellLayerImports`、`checkAiLayerImports`、`checkDeviceLayerImports`、`checkMainThreadIoImports`、`checkComponentOwnership`、`checkExplicitImports`、`checkFrameworkToastForbidden`。
- 已有 ViewModel：`ScanViewModel / DeviceHomeViewModel / DeviceSettingsViewModel / AiChatViewModel / AiCallViewModel / AiHistoryViewModel / AiSettingsViewModel / AiPlatformViewModel / ShellViewModel`。

### 1.2 SDK 直连矩阵（需要 Provider 化的点）
| SDK | 当前直连文件 | 所在层 | 问题 |
|---|---|---|---|
| `lib_bluetooth` | `platform/device/HsDevicePort`(15 import)、`platform/device/HsBroadcastScanMapper`(2) | platform | 业务/平台层直接依赖蓝牙 SDK |
| `biz_ggec_hs` | `platform/device/HsBroadcastScanMapper`(9) | platform | 同上 |
| `lib_ai` | `data/ai/gateway/LibAiChatGateway`、`LibAiClientPool`、`LibAiFailures`、`LibAiModels`、`LibAiPlatformCatalog`、`LibAiSpeedTestGateway`、`LibAiVoiceModelGateway`；`data/ai/voice/RealtimeLinkAdapter` | data | 数据层直接依赖 AI SDK |
| `lib_common` | `EarbudsApplication`(YoRefreshRate)、`ui/ai/chat/AiChatViewModel` 等 4 个 VM（OneShotLiveData）、`ui/device/scan/ScanActivity`(4 处 permission)、`ScanViewModel` | app/ui | UI/VM 直接依赖公共库（含日志/事件/权限） |
| `android.bluetooth` | 0 | — | 已经隔离，良好 |
| Youi(`com.yohu.youi`) | 46 个 UI 文件 | ui | UI 框架，属 View 层合法依赖（见 §3.3 决策） |

### 1.3 YoLog 初始化现状（关键问题）
- **全仓库生产代码 0 处 `YoLog.init`**（仅 `InitResult` 注释提及）。App 从未初始化 YoLog。
- 后果：所有 `YoBtLogger/YoAiLogger` 日志走 `LogCore` 的“未初始化 → LogcatSink 兜底”通道；**无文件落盘**、无统一级别/滚动/保留/降级策略。实机 Logcat 实测已印证（见 `docs/device_logcat_review.md`）。
- **重复初始化风险**：`lib_common` 的 `LogCore.init(...)` 在已初始化时会先 `shutdownLocked()` 再重建 pipeline——即**再次 init = 拆掉现有日志管线重建**。因此必须“只初始化一次”，绝不能各处随意 init。

### 1.4 MVVM 严格性差距（诊断）
1. SDK 未统一到 Provider：蓝牙在 platform、AI 在 data 直连（见 1.2），违反“下游替换性/边界单一”。
2. UI/VM 直接依赖 `lib_common`（OneShotLiveData、Permission）与 `Youi`：`Youi` 可接受（UI 框架），但 `lib_common` 的日志/权限应经 Provider 归一。
3. 未见统一的“日志门面 / 初始化门面”，日志与初始化散落。

---

## 2. 目标分层与依赖方向

```text
View (ui: Activity/Page/View)          —— 只依赖 ViewModel + UI 框架(Youi) + 只读 UI 模型
   ↓（单向）
ViewModel (ui/*ViewModel)              —— 只依赖 domain usecase/port/model + 生命周期/可观察封装
   ↓
Domain (domain: usecase / port / model / policy)  —— 纯逻辑，不依赖 android/data/ui/SDK
   ↓
Data (data: repository / gateway impl / store)    —— 只依赖 domain port + provider 抽象
   ↓
Provider (provider: lib_bluetooth / lib_ai / lib_common)  —— 唯一允许 import 对应 SDK 的层
   ↓
SDK (lib_bluetooth / lib_ai / lib_common / biz_*)
```

新增包：
```text
com.ggec.earbuds.provider.lib_bluetooth   # 实现 domain.device.port.DevicePort 等
com.ggec.earbuds.provider.lib_ai          # 实现 domain.ai.port.*（Chat/Model/Voice/SpeedTest/Catalog…）
com.ggec.earbuds.provider.lib_common      # YoLog 初始化/日志门面、YoPermission、OneShotLiveData 适配
```
迁移映射：
- `platform/device/HsDevicePort` + `HsBroadcastScanMapper`（蓝牙/biz 部分）→ `provider/lib_bluetooth/*`（`platform/device` 只保留纯 Android 能力，如剪贴板/Toast 等）。
- `data/ai/gateway/LibAi*` + `data/ai/voice/RealtimeLinkAdapter` → `provider/lib_ai/*`；`data/ai/**` 只保留 Repository 实现并依赖 `domain.ai.port` 抽象，由 Provider 提供 SDK 能力。
- `EarbudsApplication(YoRefreshRate)`、VM 的 `OneShotLiveData`、`ScanActivity` 的 `YoPermission` → 经 `provider/lib_common/*` 归一（日志见 §3）。

### 2.1 依赖规则（Gradle 强制）
| 层 | 允许 import | 禁止 |
|---|---|---|
| ui | 自身、domain、androidx、Youi、`lib_common.event`（共享 UI 原语）、`provider/lib_common`(日志/权限薄封装) | data、SDK 原始包（除 `lib_common.event`） |
| viewmodel | domain、androidx/lifecycle | android.view/widget、data、SDK |
| domain | 纯 Java、自身 | android.*、data、ui、SDK |
| data | domain、provider 抽象 | ui、android.*（除必要）、SDK 原始包 |
| provider | 对应 SDK、domain port | ui、data（实现可在 provider 内） |

新增/加固 Gradle 任务：
- `checkAppSdkProviderBoundary`：仅 `provider/**` 可 `import com.yovo.lib_bluetooth|lib_ai|lib_common|biz_ggec_hs`；其它层一律禁止。
- `checkAppMvvmLayers`：ui→data / vm→android.view|data / domain→android|data|ui 全禁（在现有 check 上补 provider）。
- 保留并扩展 `checkShellLayerImports/checkAiLayerImports/checkDeviceLayerImports`。

---

## 3. YoLog 统一初始化设计（严禁重复初始化 + SDK 自动初始化）

### 3.1 新增单一初始化门面（lib_common）
新增 `com.yovo.lib_common.log.YoLogBootstrap`：
- `public static InitResult initOnce(Context appContext, LogConfig config)`：`synchronized` + `if (YoLog.isInitialized()) return existing;` 否则 `YoLog.init(...)` 一次。**唯一允许真正 init 的入口**。
- `public static void ensureInitialized(Context appContext)`：懒初始化，用安全默认 `LogConfig`（仅当未初始化时生效）。供 SDK 自动初始化调用。
- `YoLog.init(...)` 文档标注为“库内部/仅 Bootstrap 调用”，并在 Gradle 禁止其它处直接调用。

### 3.2 初始化流程（唯一 owner + 自动兜底）
```text
EarbudsApplication.onCreate()
  └─ LoggingBoot.install(app)          // App 侧单点 owner（provider/lib_common）
        └─ YoLogBootstrap.initOnce(app, appLogConfig)   // 只真初始化一次；开文件落盘
AppContainer 装配 graphs（不初始化日志）
任意 SDK 调用点（如 provider/lib_bluetooth 创建 YoBluetoothSDK、provider/lib_ai 创建 client）
  └─ YoLogBootstrap.ensureInitialized(context)          // 若 App 已初始化 → no-op；否则按默认配置懒初始化
```
- **SDK 自动初始化**：`YoBluetoothSDK.classic/ble(context)`、lib_ai `YoAiClient.builder().build()`/`YoAiAssembly.open(context?)` 之前调用 `ensureInitialized`。保证“单用 SDK 也有日志”。
- **严禁重复**：`initOnce` 幂等 + 全局唯一 owner(Application)；`checkSingleYoLogInit` 校验：除 `YoLogBootstrap` 与 `wiring/LoggingBoot` 外，禁止 `YoLog.init(`；禁止未判 `isInitialized()` 的初始化调用。

### 3.3 关于 Youi 与 lib_common 的边界决策
- `Youi` 是 UI 框架/组件库，属于 View 层合法依赖，**不纳入业务 Provider**（否则 UI 层无法工作）。
- `lib_common` 是基础设施：日志(YoLog)、权限、OneShotLiveData 等应经 `provider/lib_common` 薄封装后供上层使用；禁止 ui/data/domain 直接 import `com.yovo.lib_common`（Gradle 强制），唯一例外 provider 层。

---

## 4. lib_ai 架构优化 + 日志补全设计

1. **公开日志桥**：扩展 `api/YoAiDiagnostics`，新增 `Sink.onLog(level, tag, message, throwable)` + `log(...)`，由 `YoAiAssembly` 安装为 internal `YoAiLogger`；使 `realtime/speech/media` 等**公开包**也能记日志且不越界。
2. **realtime 日志**：在 `RealtimeWebSocketLink`（连接建立/关闭/错误/重连）与 `YoRealtime` 关键阶段经公开桥记录；补齐当前 0 日志的实时链路。
3. **统一失败**：HTTP/传输/协议/能力失败继续统一 `YoAiDiagnosticsBus.recordFailure`；`exportDiagnosticReport()` 已具备。
4. **架构完善**：明确 `api(公开) / internal(实现) / wiring(装配)` 边界；`provider` 概念在 App 侧体现（`provider/lib_ai`），lib_ai 自身保持 SDK 内聚。

---

## 5. 分批实施计划（严格按序，禁止越层打补丁）

- **Phase A（本设计）**：评审 + 设计（本文档）。
- **Phase B：YoLog 初始化治理（跨模块）**
  - lib_common 增 `YoLogBootstrap`（initOnce/ensure）。
  - lib_bluetooth/lib_ai 创建入口接入 `ensureInitialized`。
  - app 增 `wiring/LoggingBoot` + `provider/lib_common/YoLogProvider`，Application 单点 init。
  - Gradle `checkSingleYoLogInit`。
- **Phase C：App Provider 化（蓝牙/AI/lib_common）**
  - 新增 `provider/lib_bluetooth`、`provider/lib_ai`、`provider/lib_common`，迁移 `HsDevicePort`/`LibAi*`/`RealtimeLinkAdapter` 与 lib_common 用点。
  - 调整 `DeviceGraph/AiGraph` 装配到 provider。
  - Gradle `checkAppSdkProviderBoundary`、加固层校验。
- **Phase D：MVVM 严格化收尾**
  - 复核 ui→data、vm→android、domain 纯净；修正违规；补 `checkAppMvvmLayers`。
- **Phase E：lib_ai 日志补全**（公开桥 + realtime）。
- **Phase F：验收**：编译/单测/层校验 + 实机 Logcat（YoLog 落盘 + 模块标签可见，含 lib_ai）。

## 6. 验收标准
- App 内除 `provider/**` 外，`0` 处 import `com.yovo.lib_bluetooth|lib_ai|lib_common|biz_ggec_hs`（Gradle 强校验）。
- `YoLog.init` 仅存在于 `YoLogBootstrap`/`LoggingBoot`；运行期只初始化一次（可加运行期断言/日志计数）。
- SDK 单用场景（不经 App）也能自动初始化日志（`ensureInitialized`）。
- lib_ai 公开包可记日志，realtime 有连接/关闭/错误日志；`exportDiagnosticReport()` 可用。
- 全部 Gradle 层校验 PASS；`:app` / `:lib_ai` / `:lib_bluetooth` 编译通过；实机 Logcat 能看到 lib_bluetooth 与 lib_ai 的 YoLog 标签且无崩溃。

## 7. 实施进度

### Phase B（YoLog 单一初始化治理）——✅ 已完成（本轮）
- `lib_common` 新增 `com.yovo.lib_common.log.YoLogBootstrap`：`initOnce()`（`isInitialized()` 幂等 + synchronized，唯一真 init 入口）、`ensureInitialized()`（懒初始化，安全默认配置）、`defaultConfig()`。
- SDK 自动初始化：`lib_bluetooth` 的 `YoBtLogger.ensureInitialized(Context)`（由 `BluetoothSdkCore.classic/ble` 调用）；`lib_ai` 的 `YoAiLogger.ensureInitialized(Context)`。
- App 唯一 owner：新增 `provider/lib_common/YoLogProvider.install(app)`（`YoLogBootstrap.initOnce` + 文件落盘配置），`EarbudsApplication.onCreate` 最先调用。
- Gradle 新增 `checkSingleYoLogInit`（仅 `YoLogBootstrap` 可调用 `YoLog.init`，禁重复/旁路初始化），挂在 `app:preBuild`。
- 验证：`:lib_common`、`:lib_bluetooth`、`:lib_ai`、`:app` 编译 + 既有边界校验 + `checkSingleYoLogInit` 全部 BUILD SUCCESSFUL。

### Phase C–F —— ⏳ 待续（下一轮）
- **Phase C（进行中）**：
  - ✅ **lib_bluetooth Provider 化**：`platform/device/HsDevicePort` + `HsBroadcastScanMapper`（及对应单测）迁移至 `provider/lib_bluetooth`；`DeviceGraph` 装配更新；新增 Gradle `checkAppSdkProviderBoundary`（当前强制：非 provider 禁 import `com.yovo.lib_bluetooth|biz_ggec_hs`）。`:app` 主/单测编译 + 校验通过。
  - ✅ **lib_ai Provider 化**：`data/ai/gateway/LibAi*`(8) + `data/ai/voice/RealtimeLinkAdapter` → `provider/lib_ai`（含 `LibAiGatewayTest` 同迁）；`AiGraph` 引用更新；`provider/lib_ai` 无 `data/ui` 反向依赖（50 处仅依赖 domain）；`checkAppSdkProviderBoundary` 已扩展到 `lib_ai`。`:app` 主/单测编译 + 校验通过。
  - ✅ **lib_common 按需 Provider 化**：仅适配平台能力——`PermissionProvider`（蓝牙权限请求 + 回调归一）、`WindowProvider`（刷新率安装）、`YoLogProvider`（日志初始化）。**`OneShotLiveData` 不做 Provider/不复制**：它是 `lib_common.event` 的共享 UI 原语（biz_appupdate 等也在用），包装/复制属重复实现或兼容层，故按“共享 UI 原语”直接复用；边界校验仅允许非 provider 代码 import `com.yovo.lib_common.event.`，其余 `lib_common.*` 一律禁。
  - **Phase C 验收证据链**：`app` 非 provider 代码对外部 SDK 的 import 仅剩 `lib_common.event.OneShotLiveData`（5 个 ViewModel）；`lib_bluetooth|lib_ai|biz_ggec_hs|lib_common.system/log` 的 import 全部收敛在 `provider/**`（14 个 provider 文件）；旧目录 `platform/device`、`data/ai/gateway` 已删除；`:app` 主/单测编译 + 全部边界校验 PASS。
- ✅ **Phase D｜MVVM 严格化**：新增 Gradle `checkAppMvvmLayers`（ViewModel 禁 android.view/widget/content/app；domain 禁 android/data/ui；ui 禁 data；data 禁 ui）。取证扫描当前 **0 违规**，校验 PASS。
- ✅ **Phase E｜lib_ai 日志补全**：`api/YoAiDiagnostics` 扩展 `Sink.onLog(level,tag,msg,throwable)` + `d/i/w/e`；`YoAiAssembly` 安装 Sink 路由到 `YoAiLogger`；`RealtimeWebSocketLink` 增加 open/open-failed/onOpen/onFailure/onClosing/onClosed/close 日志。`:lib_ai` 编译 + 4 边界 + 诊断单测全绿。
- ✅ **Phase F｜实机验收**：`:app:assembleDebug` 通过；实机 YoLog 单次初始化且文件落盘；落盘文件同时含 lib_bluetooth（8 条）与 lib_ai（2 条 `YoRealtime`）日志；BLE 扫描/语音通话无崩溃。最终验收报告：`docs/app_refactor_verification_report.md`。
