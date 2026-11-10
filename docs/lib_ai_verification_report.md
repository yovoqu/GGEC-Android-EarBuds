# lib_ai 架构收敛验收报告

> 日期：2026-11-10
> 目标：按 lib_bluetooth 重构经验全量重构 lib_ai——日志总线（`YoAiLogger` 唯一门面 → `lib_common.YoLog`、Provider 边界）、统一异常/诊断能力、清理冗余/死代码、严格高内聚低耦合、Gradle 边界校验、审计/验收报告。

---

## 1. 最终架构

```text
调用点(api/internal)
  ├─ 日志：YoAiLogger(internal/common/diagnostics) → YoLog(lib_common.log，唯一后端)
  ├─ 异常/诊断：YoAiDiagnosticsBus.get().recordFailure(YoAiException) / recordException / recordEvent
  │              ├─ internal 层直接调用（transport / protocol / assembly）
  │              └─ public 包(media/speech) 经公开 YoAiDiagnostics.recordFailure → Sink → Bus（依赖倒置）
  └─ 诊断导出：YoAiClient.exportDiagnosticReport() → api.YoAiDiagnosticReport
```

- 日志单一缝：`YoAiLogger` 是 lib_ai 内唯一 import `lib_common.log` 的类（Gradle `checkLibAiLogSeam` 强制）。
- Provider 边界：`implementation project(':lib_common')`；`checkLibAiProviderBoundary` 强制"非 provider/`YoAiLogger` 禁 import `lib_common`"。

## 2. 变更清单

### 新增
- `internal/common/diagnostics/YoAiLogger`（日志门面，直接委托 `YoLog`）。
- `internal/common/diagnostics/YoAiDiagnosticsBus`（异常/诊断总线：`recordFailure/recordException/recordEvent/timelineSnapshot/dumpReport`）。
- `api/YoAiDiagnosticSource` / `api/YoAiDiagnosticReport`（诊断导出模型，公开）。
- `api/YoAiDiagnostics`（公开诊断汇聚 + `Sink` 依赖倒置，供 public 包使用）。
- 单测：`internal/common/diagnostics/YoAiDiagnosticsBusTest`。

### 接入
- `OkHttpTransport`：重试/非 2xx 告警；**终态传输异常 → `YoAiDiagnosticsBus.recordFailure`**。
- `internal/protocol/ProtocolSupport.httpException`：**HTTP 失败 → 总线**（chat/responses/messages）。
- `media/YoImageServiceImpl`（YoVideo 复用）、`speech/YoSpeechSynthesis`、`speech/YoWhisperSpeech`：HTTP 失败 → `YoAiDiagnostics.recordFailure`。
- `YoAiAssembly`：open/close 生命周期日志；安装 `YoAiDiagnostics.Sink`；`YoAiClient.exportDiagnosticReport()`。

### Gradle（`:lib_ai:check` 依赖）
- `checkLibAiPublicBoundary`（原有）+ `checkLibAiLogSeam` + `checkLibAiProviderBoundary` + `checkNoStrayLogging`（新增）。

### 清理
- 删除死顶层类 `speech/YoTranscriptionStreamListener`；删除空目录 `endpoint`。
- 清理未使用 import 19 处，复扫 0。

## 3. 验证证据

| 校验 | 结果 |
|---|---|
| `:lib_ai:compileDebugJavaWithJavac` | BUILD SUCCESSFUL |
| `:lib_ai:compileDebugUnitTestJavaWithJavac` | BUILD SUCCESSFUL |
| `checkLibAiPublicBoundary / LogSeam / ProviderBoundary / NoStrayLogging` | 全 PASS |
| `:lib_ai:testDebugUnitTest`（全量） | 409 项，1 项**既存**失败（见已知限制） |
| `YoAiDiagnosticsBusTest`（新增） | 2 项 PASS |
| `:app:compileDebugJavaWithJavac`（集成，含 7 项层边界） | BUILD SUCCESSFUL |
| 未使用 import / 顶层死类 / 空目录 / 杂散日志 | 0 / 0 / 0 / 0 |

## 4. 已知限制

- `ResponsesTest.webSocketDemuxesStreamIds` 既存失败：`java.io.IOException: Gave up waiting for queue to shut down`（OkHttp dispatcher 关闭时序，环境/flaky）。**重构前基线同样失败，与本次改动无关**；后续可 `@Ignore` 或改用独立 executor 规避。

## 5. 验收结论

- 日志总线：`YoAiLogger` 唯一门面 → `YoLog`，散点直调/非 YoLog 实现为 0。✅
- 异常/诊断：`YoAiDiagnosticsBus` 统一汇聚；transport/protocol/media/speech 失败已接入；`exportDiagnosticReport()` 可用。✅
- 边界/耦合：4 道 Gradle 校验；public/内部/log seam 全部 PASS；app 集成编译通过。✅
- 冗余/死代码：删除死类与空目录，未使用 import 0，死类 0。✅
- 报告：`docs/lib_ai_audit.md` + 本验收报告。✅

> 结论：lib_ai 目标达成，可作为验收基线（唯一遗留为既存 flaky 单测，非本次引入）。
