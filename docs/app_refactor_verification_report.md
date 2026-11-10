# App + lib_ai 架构重构最终验收报告

> 日期：2026-11-10
> 目标：App 严格 MVVM 分层；对 lib_bluetooth/lib_ai/其它 SDK 统一 Provider；App 统一接入 YoLog（单一初始化 + provider，严禁重复）；lib_ai 优化架构并补齐日志（公开日志桥 + realtime）；Gradle 边界校验；审计/验收报告。

## 1. 目标达成总览

| 目标 | 结果 | 证据 |
|---|---|---|
| App 严格 MVVM 分层 | ✅ | Gradle `checkAppMvvmLayers`：ViewModel 禁 android UI；domain 禁 android/data/ui；ui 禁 data；data 禁 ui（0 违规，PASS） |
| SDK 统一 Provider | ✅ | `provider/lib_bluetooth`(2)、`provider/lib_ai`(10)、`provider/lib_common`(3)；`checkAppSdkProviderBoundary` PASS（非 provider 禁 import 蓝牙/AI/biz/lib_common.*，例外 `lib_common.event` 共享原语） |
| App 统一接入 YoLog（单次初始化） | ✅ | `lib_common.YoLogBootstrap` 唯一 init 入口；App `provider/lib_common/YoLogProvider.install` 单点 owner；SDK `ensureInitialized` 兜底；Gradle `checkSingleYoLogInit` PASS；实机 `YoLog 已初始化：…文件日志=true，目录=YoSdkLog` |
| lib_ai 日志补全 | ✅ | `api/YoAiDiagnostics` 分级日志桥 + `YoAiRuntime.install` + `RealtimeWebSocketLink` 连接/关闭/失败日志；实机 `YoRealtime: realtime open / websocket opened` 且写入落盘文件 |
| 审计/验收报告 | ✅ | `docs/app_architecture_review_and_design.md`、`docs/lib_ai_audit.md`、`docs/lib_ai_verification_report.md`、本报告 |

## 2. 代码改造清单

### 2.1 YoLog 单一初始化（lib_common + SDK + App）
- `lib_common.log.YoLogBootstrap`：`initOnce()` 幂等 + `ensureInitialized()` 懒初始化（唯一允许调用 `YoLog.init` 的类）。
- `lib_bluetooth`：`YoBtLogger.ensureInitialized(context)`（`BluetoothSdkCore` 创建客户端时）。
- `lib_ai`：`YoAiLogger.ensureInitialized(context)`（`YoAiRuntime.install` 调用）。
- App：`provider/lib_common/YoLogProvider.install(app)`（文件落盘 `YoSdkLog/earbuds.log` + 崩溃前刷新），`EarbudsApplication.onCreate` 最先执行。
- Gradle `checkSingleYoLogInit`：除 `YoLogBootstrap` 外禁止直接 `YoLog.init`。

### 2.2 App Provider 化（唯一 import SDK 的层）
- `provider/lib_bluetooth`：`HsDevicePort`、`HsBroadcastScanMapper`（原 `platform/device`）。
- `provider/lib_ai`：`LibAi*`(8) + `RealtimeLinkAdapter` + `LibAiProvider`(安装 `YoAiRuntime`)。
- `provider/lib_common`：`YoLogProvider`、`PermissionProvider`、`WindowProvider`。
- 旧目录 `platform/device`、`data/ai/gateway` 已删除；`DeviceGraph/AiGraph` 装配更新。
- `OneShotLiveData` 决策：属 `lib_common.event` 共享 UI 原语（biz_appupdate 亦用），**不 Provider/不复制**；边界校验放行 `lib_common.event.`。

### 2.3 lib_ai 公开日志桥 + realtime
- `api/YoAiDiagnostics`：`Sink.onFailure` + `Sink.onLog(level,tag,msg,throwable)`；`d/i/w/e(...)`。
- `internal/common/diagnostics/YoAiDiagnosticsInstaller`（幂等安装 Sink→Bus/Logger）。
- `wiring/YoAiRuntime.install(context)`：公开运行时入口（YoLog 兜底 + 安装诊断桥）。
- `RealtimeWebSocketLink`：open/open-failed/onOpen/onFailure/onClosing/onClosed/close 日志。

### 2.4 Gradle 边界校验（App）
- `checkAppSdkProviderBoundary`、`checkAppMvvmLayers`、`checkSingleYoLogInit`，连同既有 `checkShellLayerImports/checkAiLayerImports/checkDeviceLayerImports/...` 全部挂 `preBuild`。

## 3. 验证证据

### 3.1 构建 + 静态校验
- `gradlew :app:assembleDebug` → **BUILD SUCCESSFUL**（含全部 preBuild 层校验：`checkAppMvvmLayers`、`checkAppSdkProviderBoundary`、`checkSingleYoLogInit` 等 PASS）。
- `:lib_ai` 编译 + `checkLibAiPublicBoundary/LogSeam/ProviderBoundary/NoStrayLogging` + `YoAiDiagnosticsBusTest` 全 PASS。
- App 证据链：非 provider 代码对外部 SDK 的 import 仅剩 `lib_common.event.OneShotLiveData`（5 ViewModel，放行原语）；其余全部收敛 `provider/**`。

### 3.2 实机 Logcat + YoLog 落盘（设备 motorola edge 60 pro / Android 15 / API35）
- **YoLog 单一初始化**：`I YoLog: YoLog 已初始化：logcat=true，文件日志=true，目录=PathTarget{APP_EXTERNAL/EXT_FILES:YoSdkLog}`。
- **落盘文件**：`/storage/emulated/0/Android/data/com.ggec.earbuds/files/YoSdkLog/earbuds-06735.log` 同时包含：
  - **8 条 lib_bluetooth**：`YoCbtAndroidClient 已创建 SDK=35`、`YoCbtAndroidReceiverRegistry …`、`YoBleTimeline [ble-scan] scan started/stopped`；
  - **2 条 lib_ai**：`YoRealtime: realtime open: wss://dashscope.aliyuncs.com/api-ws/v1/realtime?model=qwen3.8-omni-flash-realtime`、`YoRealtime: websocket opened`。
- **BLE 扫描**：`[ble-scan] scan started gen=1 / scan stopped gen=1`，发现附近耳机，无崩溃。
- **AI 实时语音**：通话建立并“正在聆听”，lib_ai realtime 日志正常；无 FATAL/ANR。

## 4. 已知限制 / 说明
- `lib_ai` 单元测试 409 项中 1 项**既存失败** `ResponsesTest.webSocketDemuxesStreamIds`（`Gave up waiting for queue to shut down`，OkHttp dispatcher 关闭时序/环境 flaky；重构前基线同样失败，非本次引入）。
- 实机 AI 文本对话需要配置文本模型（当前仅语音平台已配置）；realtime 路径已用语音链路验证日志。文本路径的 `YoAiAssembly`/`OkHttpTransport`/`YoAiDiagnosticsBus` 已实现并在装配时安装。
- 设备副作用：覆盖安装 debug 包、授予 RECORD_AUDIO、发起/挂断语音通话（均在测试范围）。

## 5. 结论
App 已形成 **View → ViewModel → Domain → Data → Provider → SDK** 的严格单向分层；所有业务 SDK 直连收敛到 `provider/**` 并经 Gradle 强校验；YoLog 单一初始化（禁止重复，SDK 可自动兜底）并在实机开启文件落盘；lib_ai 具备公开日志桥与 realtime 日志。可作为验收基线。
