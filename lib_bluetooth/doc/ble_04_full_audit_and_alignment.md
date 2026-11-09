# BLE 模块全量复审与标杆架构对齐报告 (`ble_04_full_audit_and_alignment`)

> 日期：2026-11-09  
> 模块：`lib_bluetooth`（`yoble` 全量复审 + 公共 Lint 清零）  
> 参考标杆源码：`ble-refs/` 下已克隆 4 大开源标杆库（`Android-BLE-Library`、`Android-Scanner-Compat-Library`、`blessed-android`、`RxAndroidBle`）及 `lib_bluetooth/doc/lib_bluetooth_full_rebuild_prompt_new_architecture.md`、`lib_bluetooth/doc/ble_01_architecture_design.md`。

---

## 1. 四大标杆开源库架构与工程细节横评对标

| 维度 | `Android-BLE-Library` (Nordic) | `Android-Scanner-Compat-Library` (Nordic) | `blessed-android` (Weliem) | `RxAndroidBle` (Polidea) | `yoble` 复审后对齐实现 |
| --- | --- | --- | --- | --- | --- |
| **GATT 串行化与死锁防护** | 请求队列单并发 + Timeout + `invalidate()` | N/A | `Queue` 单线程调度 + 命令超时 + 失败继续出队 | `ClientOperationQueue` FIFO + Priority + Disposable 取消释放槽位 | `YoBleGattOperationQueue`：全生命周期统一在 `state` 线程回调 `CompletionHandler`，取消/超时/断连 100% 释放 `inFlight` |
| **建连竞态与重连策略** | AutoConnect + 断连清空请求 | N/A | Bonded 设备发现服务前保留延时 (`600ms–1600ms`)，避免加密协商冲突 | `close()` 后延时保护（防 133）；区分本地 `0x16/0x100` 与远端 `0x08/0x13` | `YoBleGattSession` + `YoBleReconnectPolicy`：修复 `status==0 && isLocalTerminated` 死代码分支；`0x16/0x100` 绝不触发 `autoConnect` 重连；保留 `lastConnectionConfigs` 支持 `disconnect()` 后再次 `reconnect()` |
| **Android 7–15+ 平台差异与坑位** | 区分 API 33+ `writeCharacteristic`/`onCharacteristicChanged` 与旧版 `setValue`/`getValue` | 软过滤补齐 ROM Name 子串/Mask 缺陷；`5-in-30s` 扫描限流防护；`FIRST_MATCH/MATCH_LOST` 仿真 | Android 13 `setPreferredPhy` 无回调兜底 `readPhy()`；ConnectionPriority 缺失回调延时兜底；Service Changed `0x2A05` | `RxBleGattCallback` 严格按 SDK 分支防双倍回调；API 33+ 写回调不再读取陈旧 `getValue()`；L2CAP Socket 断开/取消关闭解锁阻塞 | 补全 API 33+ `onCharacteristicWrite` 传 `null` 防 ReliableWrite 误判；Scan 下推统一 `CALLBACK_TYPE_ALL_MATCHES` 交上层仿真并增加硬件 Filter 安全防错与降级；`connectGatt`/`openClientChannel` 异常与取消全防护 |
| **Notify/Indicate & 多播** | 区分 Notify (`0x01,0x00`) / Indicate (`0x02,0x00`)；Wait-for-indication | N/A | 自动写 CCCD 与兼容模式 | 共享 `CharacteristicNotification` 引用计数 (`SetupMode.DEFAULT/COMPAT/QUICK_SETUP`) | `YoBleNotificationRouter`：引用计数 `0->1`/`1->0`、Describe/Indicate 智能选值、`setNotify` 返回真实可取消队列句柄 |
| **长包分片 & 请求-响应 & GATT Server** | `DataSplitter` / `DataMerger` + ReliableWrite + `BleServerManager` | N/A | 字节分片写队列 | 流式长写重试 | `YoBleLongWriteCoordinator` 消除递归栈深（改为调度至 `state` 线程重试）；`YoBleRequestResponseCoordinator` 闭合 `writeHandleRef` 与超时竞态；`YoBlePeripheralManager` 修正 CCCD (`0x2902`) 在 `SHARED` 特征下也严格按 Client 隔离，句柄取消按 `generation` 精确隔离，补全 `ADVERTISE_FAILED_FEATURE_UNSUPPORTED (5)` 映射与 `instanceId` 精确/规范双索引查找 |
| **运行时与生命周期关闭** | Executor 关闭 | Handler 清理 | Handler 线程释放 | 线程池/订阅清理 | `YoBleRuntime` 对 `state/io/callback` 增加 `RejectedExecutionException` 关闭窗口竞态防护 |

---

## 2. 全量代码复审发现清单（共 22 项：结构 1 + 缺陷与健壮性 16 + 静态 Lint 风险 5 类 / 13 条）

### 2.1 结构规范对齐（反对单一文件子包）

| 编号 | 审查发现 | 对齐修复方案 |
| --- | --- | --- |
| STR-01 | `internal/ble/internal/android/` 下存在 `scanner/`、`pairing/`、`l2cap/`、`peripheral/` 4 个仅含单个文件的子包，违背 `lib_bluetooth_full_rebuild_prompt_new_architecture.md` 第 169–219 行“禁止为划分而划分的单一文件子包”规范，而 `compat/`、`gatt/`、`hiddenapi/`、`receiver/` 均为多文件强协作包。 | 将 `YoBleAndroidScanPlatform`、`YoBleAndroidBondPlatform`、`YoBleAndroidL2capPlatform`、`YoBleAndroidPeripheralPlatform` 扁平化归并至 `internal/ble/internal/android/`，删除 4 个空子目录并同步更新 `YoBleAndroidModuleGraph`、`ble_01_architecture_design.md` 及 `BleArchitectureBoundaryTest` 架构守卫断言。 |

### 2.2 功能、并发与平台兼容性缺陷清单

| 编号 | 模块/文件 | 问题根因与风险 | 修复措施 |
| --- | --- | --- | --- |
| AUD-01 | `YoBleGattSession` & `YoBleReconnectPolicy` | `onPlatformDisconnected` 第 753 行存在永假条件 `(status == 0 && YoBleHciStatus.isLocalTerminated(status))`，在逻辑上是死代码且未能明确区分本地终止状态码。 | 移除死代码条件，明确仅 `userDisconnected` 走主动断开静默收尾；在 `YoBleReconnectPolicy.shouldAutoReconnectOnUnexpectedDrop` 增加 `rawStatus` 参数显式拒绝 `YoBleHciStatus.isLocalTerminated(rawStatus)`（`0x16/0x100`），即使 `autoConnect=true` 也绝不自动重连而是洁净上报 `LOCAL_TERMINATED` 失败，保留兼容重载。 |
| AUD-02 | `YoBleConnectionManager` | `disconnect(id)` 从 `sessions` 移除会话后调用 `reconnect(id)` 返回“No existing session config”。 | 维护仅保存不可变配置对象的 `lastConnectionConfigs`（不持有 `Observer` 引用防内存泄漏），`reconnect(id)` 在 `sessions` 找不到时回退读取历史配置重建会话，`release()` 时统一清空。 |
| AUD-03 | `YoBleConnectionManager` & `YoBleServiceDiscoveryManager` & `YoBleNotificationRouter` | `discoverServices()` 和 `setNotify()` 返回的 `BleOperationHandle` 若已入队，无法取消队内或阻塞等待操作。 | `discoverServices` 返回可取消 `BleOperationHandle`（取消对应等待者并在全部等待者取消时取消延迟定时器与在途队列任务）；`setNotifyCompat` 返回队列操作句柄并通过 `dispatchSessionHandle` 统一接入。 |
| AUD-04 | `YoBleGattOperationQueue` | `enqueue` 满队、`cancel`、`clearAll`、`failInFlight` 与 `timeout` 将 `CompletionHandler` 派发到 `callback` 线程（如 Main Thread），导致 `YoBleServiceDiscoveryManager`、`YoBleReliableWriteCoordinator` 等协调器在非 `state` 线程读写会话字段。 | 所有 `CompletionHandler`（`onSuccess`/`onCancelled`/`onFailed`）统一经 `runtime.state(...)` 调度（在 `state` 线程中执行状态变更后再由协调器决定经 `runtime.callback(...)` 通知业务观察者）。 |
| AUD-05 | `YoBleServiceDiscoveryManager` | 1) `bondedDiscoveryDelayMillis` 定时器未保存句柄，断连/重置时可能延迟残留；2) `onPlatformServicesDiscovered` 未校验 `op == null`，超时/取消后的迟到系统回调会覆盖 `currentDatabase`；3) `onServiceChangedTriggered` 重发现时将 `serviceChangedSubscribed` 置 `false` 而未取消上次 `0x2A05` 订阅，多次触发后叠加重复订阅且旧快照未从 `currentDatabase` 清空。 | 保存 `bondedDelayHandle` 与自增 `activeDiscoveryEpoch` 在 `reset()` 中原子取消；`op == null` 直接丢弃陈旧发现回调；保存 `serviceChangedUnsubscribe` 句柄避免重复叠加订阅并在触发时同步清理 `YoBleSessionContext#onDatabaseCleared()`。 |
| AUD-06 | `YoBleAndroidGattCallbackBridge` | API 33+ 调用 `writeCharacteristic(char, value, type)` 时系统不会写入 `characteristic.getValue()`，`onCharacteristicWrite` 仍调用 `getValue()` 可能读出陈旧缓存值，导致 Reliable Write 错误触发 `RELIABLE_WRITE_MISMATCH`。 | `Build.VERSION.SDK_INT >= TIRAMISU` 分支传 `echoed = null`（API `< 33` 维持 `getValue().clone()`）；并补充 null 保护与既有源码契约不变性。 |
| AUD-07 | `YoBleRequestResponseCoordinator` | `ensureEnabled` 回调内的 `writeHandleRef.set(wh)` 与超时/主动 `cancel()` 存在竞态，若先 `cancel()` 再 `set(wh)`，已入队写入不会被连带取消；`overallTimeout` 未切回 `state` 线程。 | `writeHandleRef.set(wh)` 后二次检查 `if (finished.get()) wh.cancel()`；超时任务通过 `context.runtime().state(...)` 收口。 |
| AUD-08 | `YoBleLongWriteCoordinator` | `GATT_BUSY` 重试直接递归调用 `sendChunk(...)`，高频失败时增加调用栈深度。 | 改为通过 `context.runtime().state(() -> sendChunk(...))` 异步调度下一片与重试发送。 |
| AUD-09 | `YoBleRuntime` | `!isReleased()` 与底层 Executor `dispatch/execute` 之间存在关闭瞬间 TOCTOU 竞态，可能抛出 `RejectedExecutionException`。 | 在 `state()`、`io()`、`callback()` 增加 `try/catch (RejectedExecutionException)` 防护。 |
| AUD-10 | `YoBleAndroidConnectionPlatform` | `YoBleGattCompat.connectLe` 在部分 OEM 上可能抛出 `SecurityException` 或 `DeadObjectException` 直接中断上层状态机，跳过重连。 | 捕获 `Throwable` 记录日志并返回 `null`，交由 `YoBleGattSession` 标准重试分支接管。 |
| AUD-11 | `YoBleScanCompat` & `YoBleAndroidScanPlatform` & `YoBleScanController` | 1) Android 底层设置直接透传 `FIRST_MATCH`/`MATCH_LOST` 会与批处理或旧芯片冲突且与 `YoBleScanCallbackEmulator` 重复；2) `ServiceDataMask`/`ManufacturerDataMask` 长度与 Data 不一致时原生 `ScanFilter.Builder` 直接抛 `IllegalArgumentException`；3) 部分芯片不支持硬件 Batching/Filter 时缺少安全降级；4) `scanning`/`released` 缺 `volatile`。 | 原生扫描 `ScanSettings` 统一使用 `CALLBACK_TYPE_ALL_MATCHES`，交由 `YoBleScanCallbackEmulator` 跨版本一致性仿真；Mask 长度异常时跳过危险硬件参数由 `YoBleScanFilterMatcher` 软件兜底；`YoBleAndroidScanPlatform` 增加参数异常自动降级（关闭批处理/硬件过滤并用软件过滤兜底）与扫描回调生成期隔离；`scanning`/`released` 改 `volatile`。 |
| AUD-12 | `YoBleBondController` | 1) 若设备未先经扫描缓存进入 `snapshotStore`，`createBond` / `BONDED` 时 `snapshotStore.get(deviceId)` 为 `null` 会导致 `onSuccess` 被静默跳过；2) 部分 OEM 跳过 `BOND_BONDING` 直接发出 `BOND_NONE` 导致等待器挂起超时。 | `fallbackSnapshot(...)` 兜底构造完整不可变 `BleDeviceSnapshot`；`onPlatformBondStateChanged` 在 `current == NONE && (previous == BONDING || hasWaiters(deviceId))` 时排空并回调失败。 |
| AUD-13 | `YoBleL2capPlatform` & `YoBleL2capManager` & `YoBleAndroidL2capPlatform` | 调用 `openL2capChannel().cancel()` 时若 `BluetoothSocket.connect()` 正在 IO 线程阻塞，`BleOperationHandle` 无法关闭尚未返回的 `Socket`，导致线程长时间卡死且可能错过 `OPERATION_CANCELLED`。 | 在 `YoBleL2capPlatform` 新增带 `Consumer<Closeable>` 默认重载方法；`YoBleAndroidL2capPlatform` 在 `connect()` 前即注册 `BluetoothSocket`；`YoBleL2capManager` 取消时立刻关闭 Socket 并按单次终态守卫上报 `OPERATION_CANCELLED`。 |
| AUD-14 | `YoBlePeripheralManager` | 1) `startAdvertising`/`startGattServer` 句柄取消直接调用全局 `stopAdvertising()`/`stopGattServer()`，持有旧句柄的延迟 `cancel()` 会误关后续新开启的服务/广播；2) `ADVERTISE_FAILED_FEATURE_UNSUPPORTED (5)` 被映射为通用 `ADVERTISE_FAILED`；3) **`SHARED` 特征下的 `CCCD (0x2902)` 值错误存放在 `sharedDescValues`**，违背 Bluetooth Core Spec Vol 3 Part G 3.3.3.3，导致客户端 A 开启 Notify 后客户端 B 读到已开启状态。 | 句柄取消限定 `advGen == gen` / `serverGen == gen`；错误码 `5` 精确映射 `UNSUPPORTED_OPERATION`；`CCCD_UUID` 无论所属特征 `SHARED` 或 `PER_CLIENT` 一律强制使用 `perClientDescValues` 按连接客户端独立维护；`advertising`/`serverRunning` 补充 `volatile`。 |
| AUD-15 | `YoBleAndroidPeripheralPlatform` | 1) 客户端回调透传的 `BleGattTarget` 包含系统分配的 `instanceId >= 0`，而 `characteristicMap` 仅存入无 `instanceId` 的规范 key，直接将回调 `target` 传回 `notifyCharacteristic` 时查找返回 `null` 失败；2) `ADVERTISING_SET_SUCCESS` 静态字段引用 API 26 常量触发 `InlinedApi`。 | 同时按 `onServiceAdded` 后真实 `(svcInstanceId, chInstanceId)` 与规范 key 建索引，并在 `notifyCharacteristic` 中支持规范 key 回退；替换为 `@RequiresApi(Build.VERSION_CODES.O)` 静态辅助方法。 |
| AUD-16 | `YoBleAdvertiseCompat` | Legacy `timeoutMillis` 未钳制上限（Android 限制 0..180,000ms）；`toExtendedParameters` 在 `legacyMode=true` 时错误设置 `primaryPhy`/`secondaryPhy` 且未保证可连接广播的 `scannable` 对称性。 | Legacy Timeout 钳制 `[0, 180_000]`；Extended Parameters 仅在 `!legacyMode()` 时配置 primary/secondary PHY，Legacy 模式确保 `connectable || scannable`。 |

### 2.3 `lintDebug` 全量清零清单（原 13 条 Warning -> 目标 0 Errors / 0 Warnings）

| Lint ID | 原位置 | 修复方式 |
| --- | --- | --- |
| `UnspecifiedRegisterReceiverFlag` (×2) | `YoBleAndroidReceiverRegistry`, `YoCbtAndroidReceiverRegistry` | 统一迁移至 `ContextCompat.registerReceiver(..., ContextCompat.RECEIVER_EXPORTED)` |
| `DefaultLocale` (×6) | `YoBleGattStatus`, `YoBleHciStatus`, `YoBleTimelineRecorder`, `YoCbtSppConnection`, `YoCbtSppReader` | 全部显式传入 `Locale.ROOT` |
| `InlinedApi` (×3) | `YoBleAndroidPeripheralPlatform`, `YoCbtAndroidProfileProxyAdapter` | 使用 `@RequiresApi(26)` 辅助方法 / `@SuppressLint("InlinedApi")` 常量辅助方法 |
| `AnnotateVersionCheck` (×1) | `YoCbtAndroidHearingAidExtension` | 添加 `@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.P)` |
| `DiscouragedPrivateApi` (×1) | `YoCbtAndroidSppSocket` (`mPort` 反射) | 该方法已具有完整 Javadoc、异常回退 `-1` 与可控降级策略，补充局部 `@SuppressLint("DiscouragedPrivateApi")` 并保留说明 |

---

## 3. 复审后验证状态（基线 vs 全量对齐后）

- **复审前基线**（2026-11-09）：
  - `:lib_bluetooth:testDebugUnitTest`：182/182 PASS（`yoble` 81 + `yocbt` 88 + `common` 13）
  - `:lib_bluetooth:lintDebug`：0 Errors, 13 Warnings
  - `:app:checkAiLayerImports :app:compileDebugJavaWithJavac`：PASS
  - `connectedDebugAndroidTest`（`YoBleClientInstrumentedTest` + `YoCbtClientInstrumentedTest` on `NEPI4E0106`）：25/25 PASS
- **全量对齐修改与回归验证后**（最终实测，详见 `ble_03_verification_report.md`）：
  - `:lib_bluetooth:checkLibBluetoothApiSurface` + `:lib_bluetooth:checkLibBluetoothProviderBoundary`：**PASS**
  - `:lib_bluetooth:testDebugUnitTest`：**51 测试类 / 195 条用例 100% PASS**（`yoble` 26 类 **94** 条 + `yocbt` 24 类 **88** 条 + `common` 1 类 **13** 条；较基线扩充 +13 条专项回归测试及多处深度断言）
  - `:lib_bluetooth:check :lib_bluetooth:lintDebug`：**PASS — 0 Errors, 0 Warnings**（原 13 条 Warning 全量清零，`lint-results-debug.xml` 为空）
  - `:app:checkAiLayerImports :app:compileDebugJavaWithJavac`：**PASS**（含全部宿主分层门禁与 Debug 编译）
  - `connectedDebugAndroidTest`（`NEPI4E0106` / `motorola edge 60 pro` / Android 15 SDK 35）：`YoBleClientInstrumentedTest` **8/8 PASS**（`4.723s`） + `YoCbtClientInstrumentedTest` **17/17 PASS**（`22.227s`），合计 **25/25 PASS**。
