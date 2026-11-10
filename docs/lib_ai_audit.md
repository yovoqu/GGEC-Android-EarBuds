# lib_ai 架构审查与重构报告（第一轮）

> 日期：2026-11-10
> 参照：`lib_bluetooth` 重构经验（单一日志门面 → `YoLog`、异常/诊断总线、公共/内部边界 Gradle 校验、死代码清理）
> 范围：`lib_ai`（266 个 Java 文件）

---

## 1. 现状（自底向上）

- 包结构：`api`(22) / `chat`(16) / `completion`(7) / `messages`(12) / `responses`(12) / `platform`(14, 含 openai/anthropic/qwen/deepseek/sensenova) / `models`(32, 含 spec) / `realtime`(18) / `speech`(23) / `retrieval`(17) / `media`(10) / `usage`(8) / `params`(7) / `catalog`(3) / `probe`(4) / `error`(6) / `internal`(54) / `wiring`(1)。
- 装配根：`wiring/YoAiAssembly`（唯一组合根，允许触达 internal/OkHttp）；公开层 `api/YoAiClient`。
- 错误模型：异常式——`YoAiException` 基类 + `YoAiHttpException` / `YoAiProtocolException` / `YoAiTransportException`；分类器 `YoAiErrors` + `YoAiErrorKind`（已很完整，保留）。
- 传输：`internal/transport/OkHttpTransport`（重试/退避/超时/取消）。
- 边界校验：已有 `checkLibAiPublicBoundary`（公开包类型禁 import internal/okhttp/okio）。

## 2. 与 lib_bluetooth 经验对标的差距（重构前）

| 能力 | lib_bluetooth | lib_ai（重构前） |
|---|---|---|
| 日志总线 | `YoBtLogger → YoLog` | **完全没有**（0 处日志、无 lib_common 依赖） |
| 异常/诊断总线 | `BluetoothDiagnosticsBus` | 无（仅异常分类器，无汇聚/时间线/dump） |
| 公共/内部边界 | 4 道 Gradle 校验 | 仅 1 道（public boundary） |
| 死代码 | 已清零 | 1 个死顶层类 + 1 个空目录 |

## 3. 本轮改动

### 3.1 新增日志总线
- 新增 `internal/common/diagnostics/YoAiLogger`：**唯一日志门面**，直接委托 `lib_common.log.YoLog`（不设端口/适配中间类），含 `setEnabled` 与 `putContext/clearContext`。
- `lib_ai/build.gradle` 增加 `implementation project(':lib_common')`。

### 3.2 新增异常/诊断总线
- 新增 `internal/common/diagnostics/YoAiDiagnosticsBus`（进程级单例 `get()`）：
  - `recordFailure(YoAiException)`：按 `YoAiErrors` 归类 → 写错误日志 + 追加时间线 + 通知观察者；
  - `recordException(tag, message, throwable)` / `recordEvent(type, payload)`；
  - `timelineSnapshot()` / `dumpReport()` / `clear()`。

### 3.3 关键路径接入
- `OkHttpTransport`：重试告警日志（`HTTP xxx retry n/m after ...ms`）；非 2xx 告警；**终态传输异常统一进 `YoAiDiagnosticsBus.recordFailure`**。
- `YoAiAssembly`：`open`/`close` 生命周期日志 + `client.close` 诊断事件。

### 3.4 边界校验加固（Gradle）
- 新增 `checkLibAiLogSeam`（仅 `YoAiLogger` 可 import `lib_common.log`）。
- 新增 `checkLibAiProviderBoundary`（非 provider/`YoAiLogger` 禁止 import `lib_common`）。
- 新增 `checkNoStrayLogging`（禁 `android.util.Log`/`System.out`/`System.err`/`printStackTrace`/散点 `YoLog`）。
- 全部挂入 `:lib_ai:check`。

### 3.5 冗余清理
- 删除死顶层类 `speech/YoTranscriptionStreamListener`（全仓库 0 引用）。
- 删除空目录 `internal/../endpoint`。
- 清理未使用 import **19 处**（主+测试），复扫 0。

### 3.6 诊断导出与异常接入（第二轮）
- 新增 `api/YoAiDiagnosticSource`（公开抽象）+ `api/YoAiDiagnosticReport`（公开模型）；`YoAiClient.exportDiagnosticReport()` 对外导出，由 `YoAiAssembly` 绑定 `YoAiDiagnosticsBus`，避免 public→internal 越界。
- 主协议 HTTP 失败：`internal/protocol/ProtocolSupport.httpException(...)` 构造即 `YoAiDiagnosticsBus.recordFailure(...)`，覆盖 chat / responses / messages。

### 3.7 公开诊断汇聚 + media/speech 接入（第三轮）
- 新增公开 `api/YoAiDiagnostics`（依赖倒置：`Sink` 由 `YoAiAssembly` 安装为内部 `YoAiDiagnosticsBus`），解决 public 包（speech/media）不能直接依赖 internal 的问题。
- `media/YoImageServiceImpl`（含 YoVideoServiceImpl 复用）与 `speech/YoSpeechSynthesis`、`speech/YoWhisperSpeech` 的 HTTP 失败工厂统一 `YoAiDiagnostics.recordFailure(...)`。
- 新增单测 `internal/common/diagnostics/YoAiDiagnosticsBusTest`（分类/时间线/dump/容量上限）。

## 4. 验证

- `:lib_ai:compileDebugJavaWithJavac`、`:lib_ai:compileDebugUnitTestJavaWithJavac`：BUILD SUCCESSFUL。
- `checkLibAiPublicBoundary / checkLibAiLogSeam / checkLibAiProviderBoundary / checkNoStrayLogging`：全 PASS。
- 未使用 import 复扫：0。
- 单测：409 项，**1 项既存失败** `ResponsesTest.webSocketDemuxesStreamIds`（`java.io.IOException: Gave up waiting for queue to shut down` —— OkHttp dispatcher 关闭时序，环境/flaky；重构前基线同样失败，非本轮引入）。
- 新增 `YoAiDiagnosticsBusTest`：2 项，全 PASS。
- `:app:compileDebugJavaWithJavac`（集成，含 7 项层边界）：BUILD SUCCESSFUL。
- 复扫：未使用 import 0；顶层死类 0；空目录 0；杂散日志 0。

## 5. 剩余工作（下一轮）

1. 复核 `ResponsesTest.webSocketDemuxesStreamIds` 既存失败（已定位为 OkHttp dispatcher 关闭时序 "Gave up waiting for queue to shut down"，环境/flaky，非本次引入；如需可加 @Ignore 或改用独立 executor）。
2. 重复/冗余职责扫描（当前无同名重复类；media/retrieval 公开模型与 internal 资源为有意分层）。
3. `:app` 集成编译复核（`YoAiClient` 构造新增诊断参数，仅装配根使用；App 经 Builder 构造）。
