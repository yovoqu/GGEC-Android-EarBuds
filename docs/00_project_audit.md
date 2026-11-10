# lib_bluetooth 自底向上架构审查与清理报告

> 日期：2026-11-10
> 范围：`lib_bluetooth`（`internal/{common,ble,classic}` + `api` + `model` + `provider/lib_common`）
> 关联：`doc/00_requirements_reflection_and_polished_prompt.md`、`doc/lib_bluetooth_full_rebuild_prompt_new_architecture.md`、`doc/ble_01_architecture_design.md`

---

## 1. 模块与依赖图（自底向上）

```text
GGEC-Android-EarBuds
├── app                 (application; 依赖 lib_common/lib_bluetooth/lib_ai/biz_appupdate/biz_ggec_hs)
├── lib_common          (YoLog / permission / system 等基础能力)
├── lib_bluetooth       (蓝牙 SDK：api / model / internal{common,ble,classic} / provider/lib_common)
├── lib_ai
├── biz_appupdate       (provider/lib_common/LogProvider? -> 本模块自有 LogPort 适配)
├── biz_ggec_hs         (provider/lib_bluetooth/  -> biz 引用 lib_bluetooth 的唯一适配层)
└── yotheme
```

依赖方向（蓝牙模块内，单向）：
```text
api → model
internal/{ble,classic} → internal/common → (YoBtLogger) → lib_common.log.YoLog
provider/lib_common → lib_common (权限)
```
Gradle 强校验边界：
- `lib_bluetooth: checkLibBluetoothProviderBoundary`（非 provider 禁 import lib_common）
- `lib_bluetooth: checkLibBluetoothApiSurface`（api 仅 YoBleClient/YoCbtClient，根包仅 YoBluetoothSDK）
- `lib_bluetooth: checkLibBluetoothLogSeam`（仅 `YoBtLogger` 可 import `lib_common.log`）— **本轮新增**
- `lib_bluetooth: checkNoStrayLogging`（禁 `android.util.Log`/`System.out`/`System.err`/`printStackTrace`/散点 `YoLog`）— **本轮新增**
- `biz_ggec_hs: checkBizGgecHsBoundary`（biz 仅经 `provider/lib_bluetooth` 引用 lib）
- `lib_common: checkLibCommonInternalImports` 等

---

## 2. 日志总线：收敛结果（核心变更）

### 2.1 目标设计
```text
调用点(业务/能力域) → YoBtLogger(模块唯一日志门面, internal/common/diagnostics)
                    → YoLog(lib_common 唯一日志后端: Logcat + 文件落盘)
```
- 模块内所有日志点只调 `YoBtLogger`；**不直调 `YoLog`**、**不用 `android.util.Log`**。
- `YoBtLogger` 是模块内唯一允许 `import com.yovo.lib_common.log.*` 的类（日志对接缝 / Provider seam）。
- **不设中间端口/适配类**（按要求“不用创建中间的冗余类”）。

### 2.2 删除的冗余实现（8 个日志/诊断类）
| 类 | 处置原因 |
|---|---|
| `internal/common/diagnostics/YoBluetoothLogger.java` | 旧日志门面，被 `YoBtLogger` 取代 |
| `internal/common/diagnostics/BluetoothLogPort.java` | 冗余端口层（含 `UNINSTALLED` 防御性抛错） |
| `provider/lib_common/LogProvider.java` | 冗余适配层（现由 `YoBtLogger` 直接对接 `YoLog`） |
| `internal/common/diagnostics/YoLogLevel.java` | 未使用枚举 |
| `internal/classic/diagnostics/YoCbtLogger.java` | 0 import 死代码 |
| `internal/classic/diagnostics/YoCbtLogContext.java` | 0 引用的自研上下文（应走 `YoLog.LogContext`） |
| `internal/classic/diagnostics/YoCbtConnectionTrace.java` | 0 引用死代码 |
| `internal/classic/diagnostics/YoCbtDumpReport.java` | 0 引用死代码 |

### 2.3 迁移
- 全模块 39 处 `YoBluetoothLogger` 引用 → `YoBtLogger`（同名签名，零行为差异）。
- `BluetoothProviderInstaller` 不再安装日志端口，仅保留权限端口安装。
- `YoBluetoothLogger.setEnabled(false)`（单测）→ `YoBtLogger.setEnabled(false)`。

---

## 3. 异常/错误类型收敛（本轮删除的死类型，6 个）

| 类 | 处置原因 |
|---|---|
| `internal/common/error/YoException.java` | 0 引用（无统一异常总线时的死封装） |
| `internal/common/error/YoRecoverability.java` | 0 引用 |
| `internal/classic/error/YoCbtErrorContext.java` | 0 引用（与 `YoErrorContext` 双份） |
| `internal/classic/error/YoCbtException.java` | 0 引用 |
| `internal/common/result/YoResultCallback.java` | 0 引用 |
| `internal/common/result/YoSuccess.java` | 0 引用 |

保留的对外/对内模型（边界一处映射，合法）：
```text
对外: model/common/Result<T> + Failure(抽象) + CbtFailure/BleFailure
对内: YoResult<T> + YoFailure(基类) + YoCbtFailure
错误码: YoErrorCode ← YoCbtErrorCode / YoBleErrorCode
```

> 说明：异常“总线”（`BluetoothDiagnosticsBus`）与强类型事件总线（`BluetoothEventBus`）尚属下一轮实现；本轮先清除死类型，避免半成品继续扩散。

---

## 4. 死代码清除（第二轮：全量扫描）

方法：扫描 `lib_bluetooth/src/main/java` 全部顶层 `public` 类型，统计全仓库（main/test/androidTest）引用；仅保留“无任何其他文件引用”的孤立顶层类作为死代码候选；先排除 proguard/manifest/resource 中的反射引用（结果：无）。

- **Round 1**：删除 45 个孤立顶层类（清单：`docs/audit/dead_types_round1.txt`）。
  典型：`YoCbtPacketEncoder/Decoder/PacketFrame/PacketBoundaryDetector`、`YoCbtA2dpStateProvider`、`YoCbtHeadsetStateProvider`、`YoCbtSppConnectionStore`、`YoCbtHiddenApiInvoker`、`YoCbtAddress`、以及 `internal/*/testing/Fake*` 等。
- **Round 2**（级联）：删除 4 个（`YoCbtSppConnectStrategy`、`YoBluetoothPowerState`、`YoCommonErrorCode`、`YoReleaseState`，清单：`docs/audit/dead_types_round2.txt`）。
- **Round 3**：孤立顶层类 = **0（收敛）**。
- 合计删除冗余/死类：**63**（8 日志/诊断 + 6 错误 + 45 + 4）。

> 说明：本轮只删“全仓库零引用”的顶层类；核心 kernel 抽象（仍被引用的）一律保留，避免破坏架构完整性。

---

## 5. 验证证据

| 校验 | 命令 | 结果 |
|---|---|---|
| 主代码编译 | `:lib_bluetooth:compileDebugJavaWithJavac --rerun-tasks` | BUILD SUCCESSFUL |
| 单测编译 | `:lib_bluetooth:compileDebugUnitTestJavaWithJavac` | BUILD SUCCESSFUL |
| Provider 边界 | `:lib_bluetooth:checkLibBluetoothProviderBoundary` | PASS |
| API 面 | `:lib_bluetooth:checkLibBluetoothApiSurface` | PASS |
| 日志单一缝 | `:lib_bluetooth:checkLibBluetoothLogSeam` | PASS |
| 无杂散日志 | `:lib_bluetooth:checkNoStrayLogging` | PASS |
| biz 编译+边界 | `:biz_ggec_hs:compileDebugJavaWithJavac`、`:biz_ggec_hs:checkBizGgecHsBoundary`、`:biz_appupdate:compileDebugJavaWithJavac` | BUILD SUCCESSFUL / PASS |
| app 集成编译 | `:app:compileDebugJavaWithJavac`（含 7 项层边界检查 + 全模块编译） | BUILD SUCCESSFUL |
| 全仓库杂散检查 | `android.util.Log / System.out / System.err / printStackTrace` | 0 处 |

`lib_bluetooth/src/main/java` 现存 Java 文件：394（清理前约 440+）。

---

## 6. 剩余工作（下一轮 Goal 继续）

1. **异常总线**：引入 `BluetoothDiagnosticsBus`（`recordFailure/recordException/recordEvent`），把 161 处 `catch` 收敛到统一出口。
2. **诊断总线对称化**：Classic 侧补 `YoEventTimeline`/`YoDumpable`，与 BLE 的 `YoBleTimelineRecorder`/`YoBleDumpBuilder` 对齐；弱类型 `YoCbtEventHub` 升级为强类型 `BluetoothEventBus` 并上移 common 供 BLE/Classic 共用。
3. **业务级“防御性补丁”**：审查平台适配层多余的 fallback / 静默 catch / 双保险分支，按“架构设计优先”收敛（需逐处单测保障，避免行为回退）。
4. **CI**：把上述 Gradle 校验挂入 `check`（已挂 lib_bluetooth:check），并把全仓库杂散日志校验推广到 lib_common/lib_ai。

---

## 7. 第二轮（Goal Round 1）：事件总线与异常/诊断总线

### 7.1 新增公共总线（`internal/common/diagnostics`）
- `BluetoothEventBus`：BLE 与 Classic 共用的运行时事件总线；替代 Classic 私有的 `YoCbtEventHub`（已删除），并接入 `YoBleRuntime`，实现事件通道**对称**。
- `BluetoothDiagnosticsBus`：模块**统一异常/失败总线**（进程级单例 `get()`）：
  - `recordFailure(YoFailure)`：写错误日志 + 追加时间线 + 按 traceId 幂等去重 + 通知观察者；
  - `recordException(tag, throwable[, message])`：未预期异常兜底；
  - `recordEvent(type, payload)` / `timelineSnapshot()` / `dumpReport()`。
- 已把运行时/释放协调器的边界异常收敛到总线：`YoCbtRuntime`（执行器关闭中断）、`YoBleRuntime`（state 任务异常）、`YoCbtReleaseCoordinator` / `YoBleReleaseCoordinator`（组件释放异常）。

### 7.2 状态
- `:lib_bluetooth:compileDebugJavaWithJavac`、`compileDebugUnitTestJavaWithJavac` + 4 项边界任务：`BUILD SUCCESSFUL`。
- `YoCbtEventHub` 已删除；Classic 调用点全部迁移到 `BluetoothEventBus`；BLE `YoBleRuntime.eventBus()`/`diagnostics()` 已就位。

### 7.3 状态（含诊断对称）
- `YoCbtClient` 已新增 `exportDiagnosticReport()`，配套 `model.classic.CbtDiagnosticReport`（由 `BluetoothDiagnosticsBus` 时间线 + dump 生成），与 `YoBleClient.exportDiagnosticReport()` 对齐。
- `:lib_bluetooth:compileDebugJavaWithJavac`、`compileDebugUnitTestJavaWithJavac` + 4 项边界任务：`BUILD SUCCESSFUL`。

### 7.4 异常单出口与验收（Round 2 完成）
- 22 处“带异常的 error 日志”已统一为 `BluetoothDiagnosticsBus.recordException(...)`；可恢复告警仍保留 `YoBtLogger.w` 直接日志。
- 55 处 `catch(...ignored)` 经审查归类为：资源关闭 / hidden-API 反射兼容 / 回调隔离 / 映射缺省，属必要行为，非冗余补丁（详见 `docs/34_verification_report.md` 第 3 节）。
- 最终验收报告：`docs/34_verification_report.md`（主/单测/biz/app 全量回归 BUILD SUCCESSFUL）。

## 8. 全量复审（整理，2026-11-10）

- 清理未使用 import 36 处（28 文件，主+测试），复扫 0。
- 删除空目录 5 个；复扫 0。
- 事件总线命名统一 `eventHub()→eventBus()`；`YoCbtRuntime.diagnostics()` 对称补齐。
- 最终 `:lib_bluetooth:check`（compile+test+lint+4 边界）BUILD SUCCESSFUL。
