# lib_bluetooth 架构收敛最终验收报告

> 日期：2026-11-10
> 目标（Goal）：以 `YoBtLogger` 为模块唯一日志门面并直接使用 `lib_common.YoLog`（Provider 边界对接 biz↔lib），彻底删除冗余的日志/异常/诊断中间类与防御性补丁，严格高内聚低耦合；完成代码改造、Gradle 边界校验与验收报告。
> 关联：`docs/00_project_audit.md`、`lib_bluetooth/doc/00_requirements_reflection_and_polished_prompt.md`

---

## 1. 最终架构（日志 / 异常 / 诊断 三总线）

```text
调用点(api/internal)
  │
  ├─ 日志：YoBtLogger(internal/common/diagnostics)
  │        → YoLog(lib_common.log，唯一后端：Logcat+文件落盘)
  │
  ├─ 异常：BluetoothDiagnosticsBus.get().recordFailure/recordException
  │        （写日志 + 时间线 + traceId 幂等去重 + 观察者）
  │
  ├─ 事件：BluetoothEventBus（YoCbtRuntime / YoBleRuntime 各持实例，BLE/Classic 对称）
  │
  └─ 诊断：BluetoothDiagnosticsBus.timelineSnapshot()/dumpReport()
           ← YoBleClient.exportDiagnosticReport() / YoCbtClient.exportDiagnosticReport()
```

- 日志**唯一门面**：`YoBtLogger`；模块内其余类不直调 `YoLog`、不用 `android.util.Log`。
- 日志唯一对接缝：仅 `YoBtLogger` 可 `import com.yovo.lib_common.log.*`（Gradle `checkLibBluetoothLogSeam` 强制）。
- Provider 边界：`provider/lib_common`（权限对接）；`biz_ggec_hs/provider/lib_bluetooth`（biz→lib 对接，`checkBizGgecHsBoundary` 强制）。

## 2. 代码变更清单

### 2.1 新增
| 文件 | 说明 |
|---|---|
| `internal/common/diagnostics/YoBtLogger.java` | 模块唯一日志门面，直接委托 `YoLog` |
| `internal/common/diagnostics/BluetoothEventBus.java` | BLE/Classic 共用运行时事件总线 |
| `internal/common/diagnostics/BluetoothDiagnosticsBus.java` | 统一异常/失败/事件/时间线/dump 总线 |
| `model/classic/CbtDiagnosticReport.java` | Classic 诊断报告模型（与 BLE 对齐） |

### 2.2 删除（冗余/死类，共 64）
- 日志/诊断中间类 8：`YoBluetoothLogger`、`BluetoothLogPort`、`LogProvider`、`YoCbtLogger`、`YoCbtLogContext`、`YoCbtConnectionTrace`、`YoCbtDumpReport`、`YoLogLevel`。
- 异常/结果死类型 6：`YoException`、`YoRecoverability`、`YoCbtErrorContext`、`YoCbtException`、`YoResultCallback`、`YoSuccess`。
- 孤立顶层死类 45（`docs/audit/dead_types_round1.txt`）+ 级联 4（`docs/audit/dead_types_round2.txt`）：含 `YoCbtPacketEncoder/Decoder/PacketFrame/…`、`YoCbtA2dpStateProvider`、`YoCbtHeadsetStateProvider`、`YoCbtSppConnectionStore`、`YoCbtHiddenApiInvoker`、`internal/*/testing/Fake*` 等。
- `YoCbtEventHub`（被 `BluetoothEventBus` 取代）。

> 死类收敛：`lib_bluetooth/src/main/java` 顶层孤立类型 **0**（Round-3 复扫为 0）；现存 Java 文件 394。

### 2.3 迁移
- 39 处 `YoBluetoothLogger` → `YoBtLogger`。
- 22 处“带异常的 error 日志” → `BluetoothDiagnosticsBus.get().recordException(...)`（异常单出口）。
- Classic 全部 `eventHub()` 调用点 → `BluetoothEventBus`；`YoBleRuntime` 增加 `eventBus()`/`diagnostics()`。

## 3. 防御性补丁审查

- **已删除的冗余防御垫片**：`BluetoothLogPort.UNINSTALLED`（强制必须经 SDK 安装的抛错垫片）、重复日志适配器 `LogProvider`、重复 Logger `YoCbtLogger`、重复 ErrorContext/Exception 等。
- **保留的 55 处 `catch(...ignored)` 分类**（经审查，属必要行为，非冗余补丁）：
  1. 资源关闭（`close()/shutdownNow()` 失败可忽略）——如 L2CAP/Socket/Executor 释放；
  2. hidden-API / 反射兼容（不同 ROM 方法缺失时静默回退）——如 `getAudioState`、`createRfcommSocket(channel)`；
  3. 观察者/监听回调隔离（不让用户回调异常炸穿框架）；
  4. 设备信息映射缺字段时返回 UNKNOWN。
- 结论：上述为**功能性兼容/资源安全**代码，删除或改写会破坏 Android 版本兼容或资源释放语义，故保留；真正的“冗余防御垫片”已清除，异常错误路径已统一收敛到 `BluetoothDiagnosticsBus`。

## 4. 验收证据（最终全量回归）

命令：
```
gradlew :lib_bluetooth:compileDebugJavaWithJavac :lib_bluetooth:compileDebugUnitTestJavaWithJavac \
  :lib_bluetooth:checkLibBluetoothProviderBoundary :lib_bluetooth:checkLibBluetoothApiSurface \
  :lib_bluetooth:checkLibBluetoothLogSeam :lib_bluetooth:checkNoStrayLogging \
  :biz_ggec_hs:compileDebugJavaWithJavac :biz_ggec_hs:checkBizGgecHsBoundary \
  :biz_appupdate:compileDebugJavaWithJavac :app:compileDebugJavaWithJavac
```

| 项 | 结果 |
|---|---|
| lib_bluetooth 主代码编译 | BUILD SUCCESSFUL |
| lib_bluetooth 单测编译 | BUILD SUCCESSFUL |
| ProviderBoundary / ApiSurface / LogSeam / NoStrayLogging | 全 PASS |
| biz_ggec_hs 编译 + checkBizGgecHsBoundary | BUILD SUCCESSFUL / PASS |
| biz_appupdate 编译 | BUILD SUCCESSFUL |
| app 编译（含 7 项层边界，全模块） | BUILD SUCCESSFUL |
| 总计 | **BUILD SUCCESSFUL（74 tasks）** |
| 全仓库 `android.util.Log` / `System.out` / `System.err` / `printStackTrace` | 0 |
| 非 `YoBtLogger`/provider 的 `YoLog.` 直调 | 0 |
| `lib_bluetooth/src/main/java` 顶层孤立死类 | 0 |

## 5. 验收结论

- 日志：单一门面 `YoBtLogger` → `YoLog`，散点直调与非 `YoLog` 实现清零。✅
- 异常：统一 `BluetoothDiagnosticsBus`（失败/异常单出口 + 幂等 + 时间线）。✅
- 诊断：`BluetoothEventBus` 双端对称；Classic/BLE 均有 `exportDiagnosticReport()`。✅
- 冗余/死代码：64 个中间/死类删除，死类收敛为 0。✅
- 边界/耦合：Provider 边界 + 4 道 Gradle 校验，主/单测/biz/app 全编译通过。✅
- 防御性补丁：冗余垫片清除；功能性兼容 catch 经审查保留并分类说明。✅

> 结论：Goal 目标达成，可作为最终验收基线。

---

## 6. 全量复审记录（整理干净，2026-11-10）

- **未使用 import**：清理 36 处（跨 28 文件，主源码+测试）；复扫 = 0。
- **空目录**：删除 5 个（`internal/classic/diagnostics`、`internal/classic/testing`、`internal/common/testing`、遗留空目录 `internal/classic/yocbt`、`internal/common/common`）；复扫 = 0。
- **命名统一**：`YoCbtRuntime.eventHub()` → `eventBus()`（6 处调用迁移 + 字段/方法一并统一），Classic/BLE 事件总线命名对称。
- **对称补齐**：新增 `YoCbtRuntime.diagnostics()`（BLE `YoBleRuntime.diagnostics()` 已有）。
- **悬空引用**：无对已删类的 `{@link}`（仅 1 处刻意的 `{@code YoCbtEventHub}` 说明）。
- **复扫指标**：未使用 import = 0；`src/main/java` 顶层死类 = 0；空目录 = 0；非 `BluetoothDiagnosticsBus` 的 `YoBtLogger.e(throwable)` = 0；现存 Java 文件 394。
- **最终权威校验**：`gradlew :lib_bluetooth:check` → **BUILD SUCCESSFUL**（含 `compileDebugJavaWithJavac` + `testDebugUnitTest` + `lint` + 4 项边界任务）。
