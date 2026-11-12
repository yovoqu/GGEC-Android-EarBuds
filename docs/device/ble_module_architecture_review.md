# BLE 模块架构审查（全量对齐优秀项目 · 消除潜藏防御补丁）

> 范围：`lib_bluetooth` 的 BLE 子系统（扫描 / 连接 / GATT / 传输 / 外设 / L2CAP / 配对 / 诊断）。
> 方法：逐层对照 Nordic `Android-BLE-Library`、`Android-Scanner-Compat-Library`、`Kotlin-BLE-Library`、`RxAndroidBle`，给出 file:line 证据；识别“潜藏防御式补丁”并收敛。

## 1. 模块分层（现状）

```
YoBleClient(API) → BleClientImpl → YoBleModuleCore(装配根)
  ├─ YoBleRuntime（state/io/callback/scheduler 四类线程 + 释放门面）
  ├─ YoBleScanController（FSM：IDLE/STARTING/THROTTLED_WAIT/SCANNING/STOPPING/FAILED）
  │    ├─ YoBleScanPlatform（Android Binder 桥；转换在 yo-ble-scan-parse）
  │    ├─ YoBleScanCallbackEmulator（FIRST_MATCH/MATCH_LOST/去重）
  │    ├─ YoBleScanFilterMatcher / YoBleScanThrottleGuard（前置限流）
  ├─ YoBleConnectionManager（连接/MTU/PHY/优先级/RSSI/服务发现/缓存刷新/reliable write/split write/sendAndWait）
  │    ├─ YoBleGattSession + YoBleConnectionStateMachine（10 态合法迁移）
  │    ├─ YoBleGattOperationQueue + GattOperationHandler
  │    └─ YoBleLinkTuner / YoBleServiceDiscoveryManager / YoBleReliableWriteCoordinator
  ├─ YoBleL2capManager（CoC）、YoBlePeripheralManager（AdvertisingSet + GATT Server）、YoBleBondController（配对）
  ├─ 诊断：YoBleTimelineRecorder / YoBleDumpBuilder / BluetoothEventBus
  └─ 平台边界：internal/android/**（Compat 薄适配器）+ platform/AndroidPlatform（能力事实源）
```

## 2. 与优秀项目对齐（证据）

| 维度 | 优秀项目证据 | 我方实现 | 结论 |
|---|---|---|---|
| 连接状态模型 | `Android-BLE-Library/observer/ConnectionObserver.java:35-135`（CONNECTING/CONNECTED/READY/DISCONNECTING/DISCONNECTED + REASON_*） | `YoBleConnectionStateMachine`（IDLE/CONNECTING/CONNECTED/BONDING_WAIT/DISCOVERING_SERVICES/INITIALIZING/READY/DISCONNECTING/DISCONNECTED/FAILED，显式合法迁移）；`BleConnectionSnapshot.lastFailure: BleFailure`（errorCode/recoverable/retryable/rawStatusCode/suggestedAction/traceId） | **对齐且更细**（10 态 + 类型化失败） |
| 扫描状态机 | `Android-Scanner-Compat-Library/.../BluetoothLeScannerCompat.java`（Stopped/Starting/Scanning/Stopping） | `YoBleScanController` 六态 + `runtime.checkStateThread()` + generation 栅栏 | **对齐** |
| 扫描限流 | `RxAndroidBle/.../ScanPreconditionsVerifierApi24.java:17-56`（5次/30s，unlock 时间） | `YoBleScanThrottleGuard`（滑窗，requiredDelayMillis） | **对齐** |
| onScanFailed 降级 | `Android-Scanner-Compat-Library/.../BluetoothLeScannerImplLollipop.java:274-305` | 启动期批量降级 + 运行期 `softwareFallback()` 每代一次降级重试 | **对齐** |
| 多版本适配 | 各库内 `Build.VERSION` 分支 | `platform/AndroidPlatform` 能力模型单一事实源（`@ChecksSdkIntAtLeast`） | **对齐（结构更集中）** |
| 操作队列 | `Android-BLE-Library/Operation.java`、串行化请求 | `YoBleGattOperationQueue` + `YoBleRequestResponseCoordinator` | **对齐** |
| 通知路由 | — | `YoBleNotificationRouter`（按 target 分发） | 已具备 |
| 外设/广播 | `Android-BLE-Library` Advertiser / GATT Server | `YoBlePeripheralManager` + `YoBleAdvertiseCompat`（Legacy/Extended） | **对齐** |

## 3. 潜藏防御式补丁清单（catch 审计）

BLE 模块共 30+ 处 `catch(...)`。分两类：
- **边界 best-effort（合规，Nordic/RxAndroidBle 同款）**：平台 Binder 调用（stop/startScan、gatt.disconnect/close、unregisterReceiver、release hook）。这类不改变状态机，仅防撕裂。
- **逻辑层（禁止静默）**：业务/状态逻辑必须映射为 `BleFailure` 上报。

**本轮已把 3 个平台边界文件的静默捕获改为显式可观测（不改变行为）**：
| 文件 | 位置 | 改动 |
|---|---|---|
| `internal/ble/internal/android/YoBleAndroidScanPlatform.java` | flushPendingScanResults / stopScan | `catch(Throwable ignored){}` → `YoBtLogger.w(TAG,...,t)` |
| `internal/ble/internal/android/gatt/YoBleAndroidConnectionPlatform.java` | gatt.disconnect / gatt.close | 同上 |
| `internal/ble/internal/android/receiver/YoBleAndroidReceiverRegistry.java` | unregisterReceiver | 同上 |

**仍待收敛（同类边界，机械替换）**：`YoBlePeripheralManager`、`YoBleGattSession`、`YoBleAndroidBondPlatform`、`YoBleAndroidPeripheralPlatform` 等剩余的 `catch(...ignored)`。

## 4. 架构缺口 / 后续对齐 backlog（按优先级）

1. **统一边界 catch 可观测化**：剩余 `catch(...ignored)` 全部改为 `YoBtLogger.w`（零行为变更）。
2. **干净断开的类型化原因**：Nordic 有 `REASON_SUCCESS/TERMINATE_PEER_USER/LINK_LOSS/TIMEOUT/...`；我方以 `lastFailure` 表达异常断开，可补 `DisconnectReason` 枚举覆盖“正常断开/本地断开/链路丢失/超时/权限”等，用于 UI/重连策略。（增量，非破坏）
3. **扫描回调类型策略**：当前平台层恒用 `ALL_MATCHES` + 软件模拟 FIRST_MATCH/LOST；可对齐 Nordic：硬件支持时用硬件回调类型，不支持再回退软件（需真机验证 OEM 行为）。
4. **连接重试/退避策略显式化**：将重连次数/退避写在 `BleConnectionConfig` 并文档化（现由上层驱动 reconnect）。

> 说明：以上 2/3/4 为“更贴近优秀项目”的增强项，不属缺陷；1 为清理项。本轮已完成 1 的代表性批（3 文件）。

## 5. 验证

- `:lib_bluetooth:compileDebugJavaWithJavac` → BUILD SUCCESSFUL。
- 后续 `:lib_bluetooth:testDebugUnitTest/:check`、`:biz_ggec_hs:check`、`:app:testDebugUnitTest/:lintDebug/:assembleDebug` 全绿（见本轮总结）。
- 真机 NEPI4E0106 冒烟：扫描/退出无 FATAL。

## 6. 证据来源

- `E:\System\Temp\ble-ref\Android-BLE-Library\ble\src\main\java\no\nordicsemi\android\ble\observer\ConnectionObserver.java`
- `E:\System\Temp\ble-ref\Android-Scanner-Compat-Library\...\BluetoothLeScannerCompat.java` / `BluetoothLeScannerImplLollipop.java`
- `E:\System\Temp\ble-ref\RxAndroidBle\...\internal\scan\ScanPreconditionsVerifierApi24.java`
- 我方：`YoBleModuleCore`、`YoBleConnectionStateMachine`、`BleConnectionSnapshot`、`BleFailure`、`YoBleScanController`、`AndroidPlatform`
