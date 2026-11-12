# 扫描限流 / 错误降级架构（对标 Nordic · RxAndroidBle 证据链）

> 目的：把“剩余项”（扫描 30s/5 次限流防护、`catch(Throwable)` 防御式捕获、onScanFailed 处理）按优秀项目架构规范化，给出 file:line 证据与去留决策，用架构解决而非随意删改。

## 1. 30s / 5 次扫描限流：**保留**（RxAndroidBle 同构）

**证据（RxAndroidBle）** `rxandroidble/src/main/java/com/polidea/rxandroidble2/internal/scan/ScanPreconditionsVerifierApi24.java`
- L17-22：注释引用 AOSP `AppScanStats`，常量 `SCANS_LENGTH = 5`、`EXCESSIVE_SCANNING_PERIOD = 30s`。
- L46-56：维护最近 5 次时间戳的滑窗，若 `now - oldest < 30s` 抛 `BleScanException(UNDOCUMENTED_SCAN_THROTTLE, unlockDate)`（**fail-fast + 返回解锁时间**）。

**我实现** `lib_bluetooth/internal/ble/scan/YoBleScanThrottleGuard.java`
- L12-64：同样维护 `windowMillis` 内 `maxStarts` 次滑窗，`requiredDelayMillis()` 返回需等待毫秒（等价于 RxAndroidBle 的 unlock 时间）。

**决策：保留该前置校验器**（它不是“补丁”，而是业界标准做法）。当前额外提供 3 种策略 `BleScanThrottlePolicy`（`DELAY_UNTIL_WINDOW_FREE` / `FAIL_FAST` / `UNRESTRICTED`）：
- 与 RxAndroidBle 完全一致的是 `FAIL_FAST`（返回建议等待时间，见 `YoBleScanController` 组装 `SCAN_THROTTLED` failure）。
- `DELAY_UNTIL_WINDOW_FREE` 是本产品在 SDK 内部自动等待的产品化选择；如需严格对齐 RxAndroidBle，可把默认改为 `FAIL_FAST`，由上层决定重试。

## 2. onScanFailed 降级：**已对齐 Nordic ScannerCompat**

**证据（Nordic）** `Android-Scanner-Compat-Library/.../BluetoothLeScannerImplLollipop.java`
- L274-305：`onScanFailed(errorCode)` 时，若使用了硬件回调类型（FIRST_MATCH/MATCH_LOST）且底层报 `FEATURE_UNSUPPORTED(4)` / `OUT_OF_HARDWARE_RESOURCES(5)`，则 `disableUseHardwareCallbackTypes()` → `stopScan` → 以软件方式 `startScanInternal` 重启；否则才把错误上抛。

**我实现** `lib_bluetooth/internal/ble/scan/YoBleScanController.java`
- 启动期批量降级（对齐 Nordic 思路）：`launchPlatformScan` L390-400，若 `reportDelayMillis>0` 且不支持硬件批量、或 start 返回 `FEATURE_UNSUPPORTED`，则以 `reportDelay=0` 的软件批量重试。
- **本轮新增运行期降级重试**：`handlePlatformFailure`（L640 起）当底层报 `SCAN_FAILED_FEATURE_UNSUPPORTED / SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES` 时，使用 `BleScanConfig.softwareFallback()`（清空原生过滤 + `reportDelay=0`）在**同一代**重试一次（`degradedRetryGeneration` 保证每代仅一次），不再直接把失败上抛。
- 另外：本仓库平台层恒用 `CALLBACK_TYPE_ALL_MATCHES`，FIRST_MATCH/MATCH_LOST 由 `YoBleScanCallbackEmulator` 软件模拟，从设计上规避了 Nordic 所处理的“硬件回调类型不可用”问题。

**决策：该能力已具备并补强**（启动期 + 运行期双重降级），与 Nordic 行为等价且更适配本产品“软件模拟回调类型”的实现。

## 3. `catch(Throwable/Exception ignored)`：**边界捕获策略**

**证据（Nordic / RxAndroidBle）** 均只在平台边界吞异常：
- Nordic `BluetoothLeScannerImplLollipop.java:290-299`：restart 时 `scanner.stopScan(...) catch(Exception)`、`scanner.startScanInternal(...) catch(Exception)` —— 边界 best-effort。
- RxAndroidBle 在 operation/stop 边界同样有 catch-ignore。

**我方策略（架构约束，强制）**：
1. **允许**：平台/Binder 调用边界（如 `scanPlatform.stopPlatformScan/startPlatformScan`、`unregisterReceiver`、诊断导出）做 best-effort 捕获，避免单个平台异常撕裂状态机。
2. **禁止**：业务/状态逻辑里静默吞异常；必须通过 `YoBleErrorMapper` 映射为带 `BleErrorCode` 的 `BleFailure` 并经 `onScanFailed` 上报。
3. 现有 catch 均落在这两类内；后续新增代码按此约束评审。

## 4. 架构原则（固化）

- **限流是前置校验器（precondition verifier）**，不是散落 if：位于 `YoBleScanController.startScanInternal`，与 RxAndroidBle 同构。
- **错误降级在边界 + 有界重试**：仅对 `FEATURE_UNSUPPORTED`/`OUT_OF_HARDWARE_RESOURCES` 降级一次，避免失败风暴；其余错误映射后上抛。
- **异常不静默**：边界 catch 只用于防撕裂，业务错误必须 typed 化。

## 5. 验证

- `:lib_bluetooth:testDebugUnitTest`（含新增 `degradesAndRetriesOnceWhenPlatformReportsFeatureUnsupported`）→ BUILD SUCCESSFUL。
- `:lib_bluetooth:check`（含 lint）→ BUILD SUCCESSFUL，0 errors。
- 下游 `:biz_ggec_hs`、`:app` 编译/测试/打包通过（见总体验证）。

## 6. 证据来源清单

- RxAndroidBle：`E:\System\Temp\ble-ref\RxAndroidBle\...\internal\scan\ScanPreconditionsVerifierApi24.java`
- Nordic ScannerCompat：`E:\System\Temp\ble-ref\Android-Scanner-Compat-Library\...\BluetoothLeScannerImplLollipop.java`
- 我方：`YoBleScanThrottleGuard.java`、`YoBleScanController.java`、`BleScanConfig.java`
