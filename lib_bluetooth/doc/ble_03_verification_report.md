# BLE 模块重构全量验证报告 (`ble_03_verification_report`)

> 日期：2026-11-09  
> 模块：`lib_bluetooth`（`yoble` 子系统及公共底层能力）  
> 目标：自底向上重构 `lib_bluetooth` BLE（`yoble`）模块并通过 [ble_02_goal_and_acceptance_matrix.csv](ble_02_goal_and_acceptance_matrix.csv) 定义的全部 **27 项验收门禁（BLE-001 ~ BLE-027）**。

---

## 1. 架构与边界校验结果（PASS）

全部 Gradle 静态门禁与编译校验任务在最终代码状态下执行通过：

| 检查项 | 执行命令 | 结果 | 说明 |
| --- | --- | --- | --- |
| API 与根包隔离 | `.\gradlew.bat :lib_bluetooth:checkLibBluetoothApiSurface` | **PASS** | `com.yovo.lib_bluetooth` 仅含 `YoBluetoothSDK.java`；`api/` 仅含 `YoBleClient.java` 与 `YoCbtClient.java`；`api/` 与 `model/ble/` 零 `android.bluetooth.*` / `Context` / `internal.*` 导入 |
| `lib_common` Provider 边界 | `.\gradlew.bat :lib_bluetooth:checkLibBluetoothProviderBoundary` | **PASS** | 全模块仅 `provider/lib_common/**` 导入 `com.yovo.lib_common.*` |
| 模块总检与 Lint | `.\gradlew.bat :lib_bluetooth:check :lib_bluetooth:lintDebug` | **PASS** | **0 Errors / 0 Warnings**（已清零原 13 条 `UnspecifiedRegisterReceiverFlag`/`DefaultLocale`/`InlinedApi`/`AnnotateVersionCheck`/`DiscouragedPrivateApi` 警告，`lint-results-debug.xml` 为空） |
| 宿主 App 分层与编译 | `.\gradlew.bat :app:checkAiLayerImports :app:compileDebugJavaWithJavac` | **PASS** | App 全部自定义架构检查（`checkAiLayerImports` / `checkComponentOwnership` / `checkExplicitImports` 等）与 Debug 编译零错误 |

---

## 2. JVM 全量单元测试结果（195/195 PASS）

执行命令：
```powershell
.\gradlew.bat :lib_bluetooth:checkLibBluetoothApiSurface :lib_bluetooth:checkLibBluetoothProviderBoundary :lib_bluetooth:testDebugUnitTest --no-daemon --max-workers=2 --console=plain
```

### 2.1 分域统计（来自 `lib_bluetooth/build/test-results/testDebugUnitTest/*.xml`）

| 域 | 测试类数 | 用例数 (`tests`) | 失败 (`failures`) | 错误 (`errors`) | 跳过 (`skipped`) | 结论 |
| --- | --- | --- | --- | --- | --- | --- |
| `yoble` 域 + 架构边界 + BLE 硬件检查 | 26 | 94 | 0 | 0 | 0 | **100% PASS** |
| `yocbt` Classic 回归测试 | 24 | 88 | 0 | 0 | 0 | **100% PASS** |
| `common`（`YoStateMachineTest`） | 1 | 13 | 0 | 0 | 0 | **100% PASS** |
| **合计** | **51** | **195** | **0** | **0** | **0** | **BUILD SUCCESSFUL** |

### 2.2 BLE-001 ~ BLE-026 门禁单元测试明细

| Feature ID | 测试类 | 用例数 | 状态 |
| --- | --- | --- | --- |
| BLE-001 | `com.yovo.lib_bluetooth.BleArchitectureBoundaryTest` | 5 | PASS |
| BLE-002 | `com.yovo.lib_bluetooth.internal.common.bluetooth.YoBluetoothCapabilityCheckerTest` | 4 | PASS |
| BLE-003 | `com.yovo.lib_bluetooth.internal.ble.runtime.YoBleRuntimeLifecycleTest` | 2 | PASS |
| BLE-004 | `com.yovo.lib_bluetooth.internal.ble.error.BleStatusErrorMappingTest` | 2 | PASS |
| BLE-005 | `com.yovo.lib_bluetooth.internal.ble.scan.BleScanPreflightTest` | 5 | PASS |
| BLE-006 | `com.yovo.lib_bluetooth.internal.ble.scan.BleScanThrottleGuardTest` | 2 | PASS |
| BLE-007 | `com.yovo.lib_bluetooth.internal.ble.scan.BleScanRecordParserTest` | 2 | PASS |
| BLE-008 | `com.yovo.lib_bluetooth.internal.ble.scan.BleScanFilterMatcherTest` | 2 | PASS |
| BLE-009 | `com.yovo.lib_bluetooth.internal.ble.scan.BleScanLifecycleAndEmulationTest` | 3 | PASS |
| BLE-010 | `com.yovo.lib_bluetooth.internal.ble.device.BleDeviceSnapshotStoreTest` | 3 | PASS |
| BLE-011 | `com.yovo.lib_bluetooth.internal.ble.connection.BleConnectionStateMachineTest` | 3 | PASS |
| BLE-012 | `com.yovo.lib_bluetooth.internal.ble.connection.BleConnectionRetryAndPowerOffTest` | 7 | PASS |
| BLE-013 | `com.yovo.lib_bluetooth.internal.ble.connection.BleMultiDeviceIsolationTest` | 1 | PASS |
| BLE-014 | `com.yovo.lib_bluetooth.internal.ble.gatt.BleGattOperationQueueTest` | 2 | PASS |
| BLE-015 | `com.yovo.lib_bluetooth.internal.ble.gatt.BleServiceDiscoveryAndCacheTest` | 5 | PASS |
| BLE-016 | `com.yovo.lib_bluetooth.internal.ble.gatt.BleServiceChangedTest` | 5 | PASS |
| BLE-017 | `com.yovo.lib_bluetooth.internal.ble.gatt.BleGattReadWriteAndDescriptorTest` | 3 | PASS |
| BLE-018 | `com.yovo.lib_bluetooth.internal.ble.gatt.BleNotificationRouterTest` | 5 | PASS |
| BLE-019 | `com.yovo.lib_bluetooth.internal.ble.transfer.BleLongWriteAndSplitterTest` | 5 | PASS |
| BLE-020 | `com.yovo.lib_bluetooth.internal.ble.transfer.BleRequestResponseTest` | 5 | PASS |
| BLE-021 | `com.yovo.lib_bluetooth.internal.ble.gatt.BleReliableWriteTest` | 4 | PASS |
| BLE-022 | `com.yovo.lib_bluetooth.internal.ble.gatt.BleLinkTuningAndWorkaroundsTest` | 4 | PASS |
| BLE-023 | `com.yovo.lib_bluetooth.internal.ble.pairing.BleBondControllerTest` | 5 | PASS |
| BLE-024 | `com.yovo.lib_bluetooth.internal.ble.channel.BleL2capChannelTest` | 4 | PASS |
| BLE-025 | `com.yovo.lib_bluetooth.internal.ble.peripheral.BlePeripheralAndServerTest` | 4 | PASS |
| BLE-026 | `com.yovo.lib_bluetooth.internal.ble.diagnostics.BleDiagnosticsReportTest` | 2 | PASS |

---

## 3. 真机仪器化测试结果（`NEPI4E0106` / Android 15 SDK 35）

- 测试设备：`NEPI4E0106`（`motorola edge 60 pro`，Android 15 / SDK 35，系统蓝牙处于开启状态 `bluetooth_on=1`）
- BLE + 通用 Classic 仪器化回归命令（分别定向执行并在 `TEST-motorola edge 60 pro - 15.xml` 独立采集结果）：
  ```powershell
  .\gradlew.bat :lib_bluetooth:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.yovo.lib_bluetooth.api.YoBleClientInstrumentedTest" --no-daemon --max-workers=2 --console=plain
  .\gradlew.bat :lib_bluetooth:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.yovo.lib_bluetooth.api.YoCbtClientInstrumentedTest" --no-daemon --max-workers=2 --console=plain
  ```

### 3.1 结果汇总

| 测试类 | 用例数 | 通过 | 失败 | 结论 |
| --- | --- | --- | --- | --- |
| `YoBleClientInstrumentedTest` | 8 | 8 | 0 | **100% PASS** (`4.723s`) |
| `YoCbtClientInstrumentedTest` | 17 | 17 | 0 | **100% PASS** (`22.227s`) |
| **定向合计** | **25** | **25** | **0** | **BUILD SUCCESSFUL** |

### 3.2 `YoBleClientInstrumentedTest` 真机实测证据（来自 Logcat 与 XML）

1. `capabilityAndAdapter_reportConsistentState` (`BLE-002`)：`isBleSupported() == PackageManager.hasSystemFeature(FEATURE_BLUETOOTH_LE) == true`，`isAdapterEnabled() == true`，`getConnectedDevices()` 成功。
2. `lifecycle_releaseAllowsFreshInstance` (`BLE-003`)：重复 `release()` 幂等；释放后调用 `getBondedDevices()` 返回 `BleErrorCode.RELEASED`；重建第二实例隔离可用。
3. `scan_withPermissions_completesOrRespectsRadioState` (`BLE-005`)：在真实芯片执行 3s `locationExempt=true` 扫描，真实回调 `found=230` 帧、聚合 `snapshots=56` 台去重设备（实测捕获周围真实设备含 `Vidda V12耳夹式耳机A5FD [74:D0:82:7C:A5:FD] rssi=-90`），超时自动停扫且 `isScanning() == false`。
4. `stopScan_isIdempotentWhenIdle` (`BLE-009`)：空闲连续调用 `stopScan()` 两次均返回成功且状态干净。
5. `invalidDeviceId_rejectedBeforeGatt` (`BLE-010`)：非法地址 `"INVALID_MAC"` 在触达系统 `BluetoothGatt` 前即异步拒连并精准回报 `BleErrorCode.DEVICE_INVALID`。
6. `bondedDevices_returnsListWithoutCrash` (`BLE-023`)：`getBondedDevices()` 返回成功并实测读取到系统已绑定设备 `Vidda V12耳夹式耳机 [74:D0:82:7E:12:A7] bond=BONDED`。
7. `advertising_bestEffortWithPermission` (`BLE-025`)：实测在 `NEPI4E0106` 上成功开启真实 BLE 芯片广播（`BLE 广播已启动（真实芯片）`），1.5s 超时自动停止回调 `onAdvertisingStopped()`，`stopAdvertising()` 两次幂等。
8. `gattServer_andDiagnostics_workEndToEndOnDevice` (`BLE-025/026`)：实测本地 `BluetoothGattServer` 启动成功，注册/注销 Connection & Bond 观察者无泄漏，`exportDiagnosticReport()` 正常导出结构化诊断 Dump。

> **关于全量未过滤 `:lib_bluetooth:connectedDebugAndroidTest` 的如实说明**：  
> 未过滤执行全部 31 项 `androidTest` 时（共 31 项：28 通过、3 失败），仅存遗留专项用例 `YoCbtSppMultiConnectionInstrumentedTest`（历史 Classic SPP 多连接用例）中有 3 项（`test1_spp_connectViaStandardUuid_to1888`、`test3_spp_connectViaRfcommChannel_to1888`、`test5_spp_multiDeviceMultiConnection`）未通过，原因在于该测试类硬编码连接指定物理实验室耳机 `E8:09:59:0E:65:A7` (1888) 与 `11:11:22:33:34:4B` (V12)，当前测试环境该两台经典蓝牙耳机未同时开机可连；与本次 BLE 模块重构无关。

---

## 4. 验证过程发现并修复的缺陷

1. **普通 Descriptor 写失败错映为 `NOTIFY_FAILED`（已修复）**：
   - `YoBleGattSession.SessionPlatformListener.onDescriptorWrite()` 原将全部 `WRITE_DESC` 非 0 状态码回退到 `BleErrorCode.NOTIFY_FAILED`。经单测 `BleGattReadWriteAndDescriptorTest` 捕获后，已按是否为 CCCD (`0x2902`) 分支：CCCD 写失败回退 `NOTIFY_FAILED`，普通 Descriptor（如 `0x2901`）回退 `WRITE_FAILED`。
2. **JVM 纯域单测日志端口与状态机测试收敛（已修复）**：
   - 为 `YoBluetoothLogger` 增加 `isPortInstalled()` 查询，并在 `FakeYoBleHarness.ensureTestLoggerInstalled()` 安装纯测试 No-Op `BluetoothLogPort`（不影响生产端未通过 `YoBluetoothSDK` 调用时仍严格抛异常的防护机制）。
   - 对齐 `YoBleConnectionStateMachineTest` 与 `YoStateMachine` 的静默拒绝语义（非法迁移返回 `false` 且状态保持不变）。
3. **标杆项目全量复审与完整架构对齐（已修复并回归，见 `ble_04_full_audit_and_alignment.md`）**：
   - **结构对齐 (`STR-01`)**：移除 `internal/ble/internal/android/` 下 `scanner/`、`pairing/`、`l2cap/`、`peripheral/` 4 个单一文件子包，扁平化归并至 `internal/android/` 并在 `BleArchitectureBoundaryTest` 加固防回归守卫。
   - **并发、状态机与 Android 7–15+ 兼容修复 (`AUD-01 ~ AUD-16`)**：修复 `YoBleGattOperationQueue` 回调全切回 `state` 线程、`YoBleServiceDiscoveryManager` 延迟定时器/陈旧回调/ServiceChanged 订阅防泄漏与清理、`YoBleGattSession` `0x16/0x100` 断连严禁自动重连及 `disconnect()` 后 `reconnect()` 复用配置、`YoBleAndroidGattCallbackBridge` API 33+ 写回调传 `null`、`YoBleScanCompat` 统一 `CALLBACK_TYPE_ALL_MATCHES` 与软过滤降级、`YoBleL2capManager` 取消即时关 Socket、`YoBlePeripheralManager` `CCCD (0x2902)` 强制按 Client 隔离与句柄分代隔离等 16 项问题。
   - **全模块 Lint 清零**：处理全模块 5 类 13 条静态检查警告，达成 `lintDebug` **0 Errors / 0 Warnings**。

---

## 5. 待验证项（需外部物理 BLE 外设对端）

当前单测闭环已通过自建高保真平台仿真台（[FakeBleGattDevice.java](../src/main/java/com/yovo/lib_bluetooth/internal/ble/testing/FakeBleGattDevice.java) 等）覆盖 94 项 BLE 域与公共边界用例，真机 `NEPI4E0106` 已验证真实芯片扫描、广播、GATT Server 与系统已绑定设备查询；以下涉及**远端真实 BLE 外设芯片固件交互**的场景受限于当前无配对测试目标外设直连对端，尚待接入量产/研发蓝牙耳机样机完成最终实机联调确认：

1. **真实对端 GATT 服务发现、多实例特征与读写/Notify/Indicate 互操作**：与实体 BLE 耳机建立物理 GATT 连接，实测特征读/写（`WITH_RESPONSE` / `WITHOUT_RESPONSE` / `SIGNED`）、Descriptor 读写及双向 Notify/Indicate 吞吐稳定性。
2. **大包 OTA 分片与请求-响应 (`writeSplit` / `sendAndWait`) 真机吞吐实测**：针对物理耳机实测协商 MTU（如 247/517 字节）下连续发送 >64KB OTA 固件流、私有帧分片包头解析及 `WRITE_NO_RESPONSE` 背压流控。
3. **物理链路口参数调优与异常断连恢复**：在实体外设拉远丢包 (`HCI 0x08 LINK_LOSS`)、对端关机断连 (`HCI 0x13`)、手机端蓝牙开关瞬间切换及 `2M / Coded PHY` / `CONNECTION_PRIORITY_HIGH` 实机协商下验证重连退避与超时兜底表现。
4. **真机对端 `Service Changed (0x2A05)` 与 `L2CAP CoC` 通道传输**：在支持 GATT 动态服务变更与 L2CAP CoC (PSM) 的外设固件上验证动态缓存重建与双向 Socket 字节流收发。
5. **真机 BLE 系统配对弹窗 (`createBond`) 与加密特征鉴权交互**：使用需要 Just Works / Passkey / 加密读写的实体 BLE 外设验证系统广播状态切换及加密链路自动重试。
6. **双真机对端互联 GATT Server 验证**：使用第二台手机或外设作为 BLE Central 连接 `NEPI4E0106` 开启的本地 GATT Server，实测 `PER_CLIENT` 隔离存储、Prepared Write (`execute` vs `abort`) 与服务端 `notifyServerCharacteristic`。
