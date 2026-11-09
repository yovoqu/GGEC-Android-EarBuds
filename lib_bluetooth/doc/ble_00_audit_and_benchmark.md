# BLE 模块自底向上现状审查与行业标杆调研报告 (`ble_00_audit_and_benchmark`)

> 日期：2026-11-09  
> 范围：`lib_bluetooth`（`YoBluetoothSDK`、`api/YoBleClient`、`model/ble/*`、`internal/ble/*`、`internal/common/*`、`AndroidManifest.xml`）及标杆开源库深度对标。  
> 原则：**文档先行、代码后置**；所有结论绑定真实文件路径、类名、方法名与 Android API / Bluetooth SIG 事实。

---

## 1. 现状代码只读审查（Bottom-Up Audit）

### 1.1 调用方与模块边界验证
1. **入口委派链路**：
   - `lib_bluetooth/src/main/java/com/yovo/lib_bluetooth/YoBluetoothSDK.java` 仅作为统一静态入口，调用 `BluetoothSdkCore.ble(context, config)` 自动执行 `BluetoothProviderInstaller.installAll()` 后返回 `BleClientImpl`。因此 `YoBluetoothSDK` **不冗余**，而是 Gradle `checkLibBluetoothApiSurface` 明确校验的根包唯一允许类（见 `lib_bluetooth/build.gradle:90-96`）。
2. **宿主 `app/` 引用情况**：
   - 全仓搜索确认 `app/` 下 **0 处引用** `YoBleClient`、`BleClientImpl`、`BleRuntime` 或 `com.yovo.lib_bluetooth.model.ble.*`（`app/` 当前通过 `IdleDevicePort` / `DeviceProfileCatalogImpl` 描述设备能力，覆盖 `CONNECTION`、`BATTERY`、`DUAL_CONNECTION`、`RECORD_STREAM`、`RECORD_TRANSFER`、`OTA`、`OFFLINE_LOG`、`CLEAR_PAIRING`、`RENAME` 等需求）。
   - 因此可参照 `yocbt` 全量重构先例（`lib_bluetooth/doc/lib_bluetooth_full_rebuild_prompt_new_architecture.md:25,98-120`）：**无历史包袱，彻底重建 `YoBleClient`、`model/ble/*` 与 `internal/ble/*`**。
3. **Gradle 编译与边界约束（`lib_bluetooth/build.gradle`）**：
   - `compileSdk = 37 (minorApiLevel = 2)`、`minSdk = 24`、`JavaVersion.VERSION_21`。
   - `checkLibBluetoothProviderBoundary`：`src/main/java` 下除 `provider/lib_common/**` 外禁止 `import com.yovo.lib_common.*`。
   - `checkLibBluetoothApiSurface`：
     - 根包 `com.yovo.lib_bluetooth` 仅允许 `YoBluetoothSDK.java`；
     - `com.yovo.lib_bluetooth.api` 仅允许 `YoBleClient.java` 和 `YoCbtClient.java`；
     - `api/*.java` 禁止 `import com.yovo.lib_bluetooth.internal.*`。
4. **Manifest 权限与特性现状（`lib_bluetooth/src/main/AndroidManifest.xml`）**：
   - 已声明 `BLUETOOTH (maxSdk=31)`、`BLUETOOTH_ADMIN (maxSdk=30)`、`ACCESS_COARSE_LOCATION / ACCESS_FINE_LOCATION (maxSdk=30)`、`BLUETOOTH_SCAN (usesPermissionFlags="neverForLocation")`，以及 `BLUETOOTH_CONNECT`。
   - **缺失**：未声明 `BLUETOOTH_ADVERTISE`（但 `PermissionProvider.java:30-31` 与 `YoBluetoothPermission.ADVERTISE` 已预留支持！若补齐 BLE Peripheral/Advertising 能力，必须在 `AndroidManifest.xml` 补全 `<uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" />`）。

### 1.2 现有 BLE 文件清单与严重缺陷评估（共 19 个源文件，0 个测试）

| 文件路径 | 类/接口 | 关键方法/字段 | 关键缺陷 / 架构问题 | 处理决策 |
| :--- | :--- | :--- | :--- | :--- |
| `api/YoBleClient.java` | `YoBleClient` | 仅 5 组业务方法 (`scan`/`stopScan`/`connect`/`disconnect`/`getConnectionSnapshot`/`read`/`write`/`setNotify`/`release`) | 缺多设备快照列表、服务发现树查询、Descriptor/LongWrite/ReliableWrite、Notification 订阅路由、Request-Response、MTU/PHY/ConnPriority/RSSI、Bond、L2CAP CoC、Advertiser/GATT Server、Diagnostics Dump | **全量扩展重构** |
| `internal/ble/BleClientImpl.java` | `BleClientImpl` (847 行 God 类) | `Session`, `callback`, `startScan`, `read`, `write`, `setNotify` | 1. **根本未重写 `onCharacteristicChanged`**，Notify/Indicate 完全无法收数；<br>2. `setNotify` 硬编码 `ENABLE_NOTIFICATION_VALUE`，不识别 `PROPERTY_INDICATE`；<br>3. `read/write` 忽略 `target.descriptorUuid()`；<br>4. 仅用单个 `pendingGattOp` 拒绝并发而非排队（直接 `GATT_BUSY`）；<br>5. `read/write/setNotify` 返回空实现 `() -> {}`，无法取消；<br>6. `startScan` 未保存 `timeout` 句柄无法 `stopScan` 取消，无 Filter/Settings/AD 解析/5-in-30s 防限流/Location 开关校验；<br>7. 未监听 `ACTION_STATE_CHANGED`（BT 关闭不触发断连回调），无退避重连，无 HCI/GATT 错误码映射，未适配 API 33+ 新签名，`onServicesDiscovered` 直接强调 `requestMtu` 无防重入与失败回退；<br>8. 直接依赖 `android.bluetooth.*` 无端口抽象，**0 单元测试/0 仪器测试** | **彻底删除并按能力域重建** |
| `internal/ble/BleRuntime.java` | `BleRuntime` | `ioExecutor`, `scheduler`, `callbackDispatcher` | 仅简单包装 cached pool + main dispatcher，缺少 `yocbt/YoCbtRuntime` 标准的单线程串行 `stateExecutor`、`checkStateThread/checkIoThread`、优雅关闭时序、`EventHub`、`Scope` 生命周期隔离；且在 JVM 单测中无条件调用 `Looper.getMainLooper()` 会直接崩 | **删除并重建 `YoBleRuntime` + `YoBleRuntimeConfig` + `YoBleRuntimeScope` + `YoBleReleaseCoordinator`** |
| `internal/model/ble/BleGattSessionState.java` | `BleGattSessionState` | volatile state + 手动 `transition()` | 绕过 `common/state/YoStateMachine`，无状态迁移合法性校验/守卫/Entry-Exit 动作/时间线，且孤立于单文件包 `internal/model/ble`（违反反对“为了划分而划分”约束） | **删除，合并迁移至 `internal/ble/connection/`** |
| `model/ble/*.java` (16 个类) | `BleConfig`, `BleErrorCode`, `BleScanConfig`, `BleScanResult`, `BleGattObserver`, `BleOperationHandle` 等 | 只含最基础字段 | `BleOperationHandle` 方法叫 `release()` 而 `CbtOperationHandle` 叫 `cancel()`（应同时兼容 `cancel()` 与 `release()`）；`BleFailure` 缺 `rawStatusCode`/`traceId`/`suggestedAction`；`BleScanResult` 缺完整 AD `ScanRecord` 解析、`txPower`、`advertisingSid`、`phy`、`connectable`、`scanRecordRaw`；`BleGattObserver` 缺Notify数据与Descriptor等回调；缺 GATT Service/Char/Desc 树模型 | **全量扩展重构** |

### 1.3 `internal/common/` 现有问题审查
- `YoBluetoothCapabilityChecker.java:33-38`：`supportsBle()` 实现为 `adapter.isMultipleAdvertisementSupported() || adapter.getBluetoothLeScanner() != null`。**缺陷**：在蓝牙适配器关闭状态（`STATE_OFF`）下 `getBluetoothLeScanner()` 返回 `null`，且大量支持 BLE Central 的低端机不支持外设广播（`isMultipleAdvertisementSupported() == false`），导致关闭蓝牙时误判设备“不支持 BLE 硬件”！应优先结合 `PackageManager.FEATURE_BLUETOOTH_LE`（并通过独立可测逻辑保证 JVM 兼容）。

---

## 2. 开源标杆项目深度调研综合对比

已本地克隆/下载并逐一研读以下四个业界权威 Android BLE 库源码：
1. **Nordic `Android-BLE-Library`**（Nordic Semiconductor 官方企业级 BLE 库）
2. **Weliem `blessed-android`**（极简清晰但高可靠的生产级 BLE 库）
3. **Polidea `RxAndroidBle`**（最完整的状态并发控制、AD 解析与协议坑位处理）
4. **Nordic `Android-Scanner-Compat-Library`**（跨 API 扫描过滤/批处理/回调仿真标准实现）

### 2.1 标杆库核心架构精华提炼

| 维度 | Nordic `Android-BLE-Library` | Weliem `blessed-android` | Polidea `RxAndroidBle` | `Android-Scanner-Compat-Library` | 本项目 `yoble` 吸收决策 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **扫描与限流** | （无内置扫描，专注连接态） | Central 分离 Scan；处理 BT off | `ScanPreconditionsVerifierApi24` 检测 5 次/30s 滚动窗口限流；`LocationServicesStatus` | 软件兜底匹配 `ScanFilter`、仿真 `FIRST_MATCH`/`MATCH_LOST`、批处理 `flushPendingScanResults` | 引入 `YoBleScanController` + **5-in-30s 滑窗节流保护 + Location 服务检查（API<31）+ 完整 AD LTV Parser + 软硬混合 Filter + Batching & MatchMode 仿真** |
| **广告数据解析** | — | — | `ScanRecordParser` 完整解析 `0x01` Flags ~ `0xFF` Manufacturer Data（含 16/32/128-bit UUID 与 Solicitation UUID、Service Data、TxPower、Complete/Short Local Name） | `ScanRecord.parseFromBytes()` 完整 TLV 解析 | 实现零 Android 依赖的纯 Java **`YoBleScanRecordParser`** -> `BleScanRecord`（可 100% JVM 单测） |
| **状态机与线程** | `BleManagerHandler` 单主/Handler 串行任务队列 (`RequestQueue`) + `await()` 条件请求 | Handler 串行每外设 Queue + Central 全局调度 | `ConnectionOperationQueueImpl` 基于优先级队列 + 断连广播中断所有在途请求 | 主 Handler 处理定时 Lost 任务与批处理 | 沿用 `yocbt` 架构：**`YoBleRuntime`（单线程 `state` 状态机 + `io` + `scheduler` + `callback`）+ `YoStateMachine<BleConnectionState>`** |
| **GATT 队列与长包** | 串行 `Request` 队列、`WaitForValueChangedRequest`、`WriteRequest` + `DataSplitter` / `DataMerger`、ReliableWrite、`atomicRequestQueue` | FIFO `commandQueue` + 20ms-500ms retry；`splitWrite` | `CharacteristicLongWriteOperation` 分片 + 策略重试 + `MtuBasedPayloadSizeLimit(min(mtu-3, 512))`；超时与取消解绑 | — | 每会话独立 **`YoBleGattOperationQueue`**：支持 FIFO+优先级、可取消 Pending/Running、超时、ReliableWrite、事务批处理，以及 **`YoBleDataSplitter` / `YoBleDataMerger` / `YoBleLongWriteCoordinator`** |
| **通知与指示** | `NotificationCallback` + 自动识别 Notify(0x10) vs Indicate(0x20) 写 CCCD；支持 `ServiceChanged` (`0x1801`/`0x2A05`) | 自动识别 `PROPERTY_INDICATE` vs `PROPERTY_NOTIFY` | `NotificationAndIndicationManager` 多播路由（引用计数 Setup/Teardown + `SetupMode`: `DEFAULT`/`COMPAT`/`QUICK_SETUP`），按 `(serviceUuid, charUuid, instanceId)` 精确路由 | — | **`YoBleNotificationRouter`**：按 `(serviceUuid, charUuid, instanceId)` 多播订阅、引用计数管理 CCCD、自动区分 Notify/Indicate、`SetupMode` 可选 |
| **连接容错与厂商坑位** | close 前间隔 200ms (`BleConnectionCompat`)、连接重试 backoff、Bonded discovery delay、`refreshDeviceCache()` | Android 13+ 新 API 分支；Android 13 `setPreferredPhy` 缺回调 1000ms 后 `readPhy()` 兜底；`requestConnectionPriority` 无回调 200ms 兜底 | HCI `0x08`/`0x13`/`133` 映射、Pixel `ACTION_STATE_CHANGED` 关闭时不回调 `onConnectionStateChange` 兜底、pre-O 认证错误 `0x05/0x08/0x0F/0x89` 自动重试 | — | 全部纳入 **`YoBleGattSession`** 与 **`YoBleAndroidGattPlatform` / compat** 层 |
| **L2CAP & 外设端** | `BleServerManager` GATT Server + 多客户端隔离与 Shared 特征值 | — | — | — | 提供 **`YoBleL2capManager`**（API 29+ Secure/Insecure CoC，低版本优雅报 `UNSUPPORTED_OPERATION`）与 **`YoBlePeripheralManager`**（Advertiser API 24+/26+ + GATT Server 多客户端隔离） |

---

## 3. Android 官方 BLE API 与 Bluetooth SIG 关键约束（API 24–37）

1. **权限矩阵（API 24–37）**：
   - API 24–30：扫描依赖 `BLUETOOTH` + `BLUETOOTH_ADMIN` + `ACCESS_FINE_LOCATION`（或 `COARSE`）**且系统位置开关需开启**（`LocationManager.isLocationEnabled()`）；连接依赖 `BLUETOOTH`。
   - API 31+（Android 12+）：扫描依赖 `BLUETOOTH_SCAN`（Manifest 已声明 `neverForLocation`，因此通常豁免位置开关，除非业务显式关闭豁免）；GATT/Bond/L2CAP/Server 连接依赖 `BLUETOOTH_CONNECT`；广播依赖 `BLUETOOTH_ADVERTISE`。
2. **Android 13 / API 33 新 GATT API 与坑点**：
   - 废弃 `characteristic.setValue()` + `gatt.writeCharacteristic(c)` 与 `descriptor.setValue()` + `gatt.writeDescriptor(d)`，改为 **`gatt.writeCharacteristic(characteristic, byte[], writeType)` 返回 `int`（`BluetoothStatusCodes.SUCCESS == 0`）** 与 **`gatt.writeDescriptor(descriptor, byte[])`**。
   - 废弃无参 `characteristic.getValue()`，改为重写带 **`byte[] value`** 参数的 `onCharacteristicRead(..., value, status)`、`onCharacteristicChanged(..., value)`、`onDescriptorRead(..., status, value)`（注意在 API 33+ 调用旧 `getValue()` 可能读到空或旧值！）。
   - `setPreferredPhy` 在部分 Android 13 机型上不触发 `onPhyUpdate`，需 1000ms 超时兜底主动 `readPhy()`。
   - 广播接收器在 API 33+ 需显式传 `RECEIVER_EXPORTED` / `RECEIVER_NOT_EXPORTED`。
3. **ATT MTU 与 512 字节单包上限**：
   - 依据 Bluetooth Core Spec (ATT 517 bytes max attribute value limit)：即便通过 `requestMtu(517)` 协商成功，`Write Without Response` / `Write Characteristic` 单次最大负载仍为 **`Math.min(mtu - 3, 512)`**；Long Write（超过 payload limit）必须使用 `YoBleDataSplitter` 分片或 GATT Queued Write。
4. **Service Changed (`0x1801` / `0x2A05`) 与设备缓存**：
   - API 31+ 原生提供 `BluetoothGattCallback.onServiceChanged(gatt)`；
   - API 24–30 需在服务发现后自动检测 `00001801-0000-1000-8000-00805f9b34fb` 下的 `2A05` Indicate CCCD 并启用，收到 Indication 后清除缓存重新 `discoverServices()`。
   - `BluetoothGatt.refresh()` 属隐藏 API，必须严格隔离在 `internal/ble/internal/android/hiddenapi/` 并受 `YoBleHiddenApiPolicy` 控制。
5. **HCI & GATT 错误码完整语义区分**：
   - `onConnectionStateChange(gatt, status, newState)` 中 `status != GATT_SUCCESS`（如 `8 = CONNECTION_TIMEOUT / Link Loss`、`19 = REMOTE_USER_TERMINATED`、`22 = LOCAL_HOST_TERMINATED`、`133 = GATT_ERROR`、`0x3E = CONN_FAIL_TO_ESTABLISH`）与 GATT 操作状态（`0x05 INSUFFICIENT_AUTHENTICATION`、`0x08 INSUFFICIENT_AUTHORIZATION`、`0x0F INSUFFICIENT_ENCRYPTION`、`0x85/133 GATT_ERROR`、`0x101 GATT_FAILURE`）必须独立映射为结构体化的 `YoBleErrorCode` / `BleErrorCode`，附带 `rawStatusCode`、`recoverable`、`retryable`、`suggestedAction` 与 `traceId`。

---

## 4. 现状与目标差距结论

当前 BLE 模块处于**不可用于生产的状态**（关键收数通道 Notify/Indicate 缺失、Descriptor 失效、无操作队列、无状态机、无扫描过滤与防限流、无长包分片、无重连/退避/BT关闭清理、无测试、无 API 33+ 适配）。

接下来依据 [`ble_01_architecture_design.md`](file:///D:/A_yoprogram/Github_Gallery/GGEC-Android-EarBuds/lib_bluetooth/doc/ble_01_architecture_design.md) 与 [`ble_02_goal_and_acceptance_matrix.csv`](file:///D:/A_yoprogram/Github_Gallery/GGEC-Android-EarBuds/lib_bluetooth/doc/ble_02_goal_and_acceptance_matrix.csv)，以 **全量能力域架构** 彻底重建 `yoble` 模块，并达到 100% P0/P1 严格验收门槛。
