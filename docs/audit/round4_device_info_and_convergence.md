# 第四轮：设备信息链路接通 + 扫描适配层收敛 + lib 收尾

> 依据 `D:\A_yoprogram\Github_Gallery\YoAgentDocs`（task.architecture-review / implement / playbook / checklist / common rules / android 类型包 / modification 规则）。
> 本轮范围由上一轮 §5「下一阶段」移交而来。每一刀必须整理到干净，严禁简化、严禁在旧结构上打补丁。

## 0. 已定语义（沿用）
- 扫描：进程内一个扫描器；首订阅启动、末订阅取消后停止；无产品扫描超时；单台设备末次广播约 10s 判 lost 并移除。
- BLE 与经典蓝牙身份/地址/名称严格分离；BLE 列表不显示已配对标记；经典已配对走独立查询。
- 分层：`lib_bluetooth`（蓝牙能力）→ `biz_ggec_hs`（HS 协议与设备能力）→ `app`（展示与调用）。调用只能自上而下。

## 1. 范围切片（本阶段要整理到干净的清单）

### A. 设备信息链路（biz → app）
| 字段 | 改前链路（证据） | 缺口位置 |
|---|---|---|
| `firmware` | `HomePageStatusV12FirstCommand` TLV `0x94` → `result.put("94", decodeUtf8)`（`HomePageStatusV12FirstCommand.java:103-108`）→ `HsProtocolEngine:132` `events.applyAggregatedHomeStatus(status)` → `DeviceEventPipeline.applyAggregatedHomeStatus` 只处理 `80_*`/`95`/`83`/`B5_*`（`:62-108`） | **`"94"` 在 store 层被丢弃**；`DeviceDomainStore` 无 firmware 字段；`HsDeviceObserver` 无设备信息回调 |
| `colorLabel` | `EarColorCommand` 已注册（`CommandPolicyRegistry.java:153`），解析 `earColor`（`EarColorCommand.java:65-69`） | **无任何 submit 入口**：`YoHsClient` 无 `readEarColor`，命令不可达 |
| `wear` | — | **协议未提供「当前是否佩戴」事件**（`B5_5` 是佩戴检测*服务状态*码，不是佩戴态）。不臆造数据：字段保留、来源缺口显式声明 |

### B. biz 扫描适配层收敛
- `HsNearbyScanner`（单文件压 4 件事：会话状态机 / 产品过滤 / lost 记账 / 失败映射）按职责拆分。
- 会话代次：跨会话陈旧帧/陈旧 lost 目前只由 app 的 `scanToken` 兜底，biz 契约不自洽。
- P2 收敛：`HsScanSnapshot.rawAdvertising()` 死面、`HsNearbyDevices` 的 `"HS01"/"HS02"` 与 `HsGeneration` 双份词汇、名称优先级两处实现、异步失败用例缺失。

### C. biz 协议调度器异常可观测性
- `CommandScheduler` 4 处 `catch (RuntimeException ignored)`（`:797/:855/:871/:883`）→ 分类记录，不留静默丢弃。

### D. lib 收尾
- `subscribe` 持 `GATE` 时同步失败投递的锁序约束（补用例或消除）。
- 5/30 限流窗口单测、Hub 并发取消+订阅交错单测。
- `lib_bluetooth/doc/ble_01_architecture_design.md` 仍写 `scan(config,observer)` → 同步为 `subscribeScan`。

### E. app 编排归位
- `ScanActivity` 的 start/stop 与权限门进一步收进 `ScanViewModel`（View 只做权限申请与转发）。
- app 侧 `Bridge` 消费新的设备信息回调，投影到 `DeviceSnapshot.firmware/colorLabel`。

## 2. 验收条目（每条可二判，含证据；全部通过才收口）
- **V1**：`grep -R "readEarColor\|onDeviceInfo" biz_ggec_hs/src/main` 有生产调用链（定义 + submit + 回调），且 `HsProtocolEngine` 在 READY 后触发一次设备信息查询。
- **V2**：单测：注入一条含 `0x94` 的首页状态 → `HsDeviceObserver.onDeviceInfo` 收到 firmware 非空；`EarColorCommand` 成功回包 → `onDeviceInfo` 收到 earColor。
- **V3**：`DeviceEventPipeline` 不再丢弃 `"94"`；`DeviceDomainStore` 具备 firmware/earColor 读取并在 `setObserver` 回放。
- **V4**：app `HsDevicePort` 实现设备信息回调，`DeviceSnapshot.firmware/colorLabel` 在连接就绪后非默认；单测断言。
- **V5**：`HsNearbyScanner` 拆分后单文件只承担一类职责；既有 `HsNearbyScannerTest` 全绿；新增会话代次用例（旧代会话的帧/ lost 被丢弃）。
- **V6**：`grep '"HS0' biz_ggec_hs/src/main` 仅 `HsGeneration` 命中；`rawAdvertising()` 删除或成为唯一取值入口；名称优先级单点（`snapshot.deviceName()`）。
- **V7**：`CommandScheduler` 无 `catch (RuntimeException ignored)`；每条丢弃路径有分类日志或终态回调。
- **V8**：`grep "catch (" lib_bluetooth/.../scan = 0` 保持；`YoBleScanHub` 新增并发取消+订阅交错用例；5/30 限流窗口用例；`ble_01_architecture_design.md` 不再出现 `scan(config`。
- **V9**：`ScanActivity` 无 start/stop 决策分支（只申请权限并转发 VM）。
- **V10**：`./gradlew :lib_bluetooth:testDebugUnitTest :biz_ggec_hs:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug` + 全部 `check*` 门禁 → BUILD SUCCESSFUL，0 失败 0 跳过。
- **V11**：范围内无 `TODO/FIXME/HACK/XXX`、无 `@Ignore`/跳过测试、无双轨/兼容别名/死代码。
- **V12**：未实机 → 明示「未实机」，仅静态+单测+门禁。

## 3. 波次（所有权互不重叠）
1. **波 1（并行）**：biz-A（A：设备信息链路，文件：`internal/protocol/inbound/**`、`model/**`、`api/**`、`internal/session/**`、`internal/protocol/HsProtocolEngine.java`）；biz-B（B+C：文件：`provider/lib_bluetooth/**`、`model/broadcast/**`、`internal/protocol/CommandScheduler.java`）；lib（D：`internal/ble/scan/YoBleScanHub.java`、测试、doc）。
2. **波 2**：app（E：`provider/lib_bluetooth/HsDevicePort.java`、`ui/device/scan/**`）→ 依赖波 1 的 `onDeviceInfo`。
3. **波 3**：同一波次全范围只读复审，全 CLEAN 才收口。

## 4. 执行结果（实测，clean 后重建）

```
:lib_bluetooth:testDebugUnitTest   32 套件 / 147 用例 / 0 失败 / 0 跳过
:biz_ggec_hs:testDebugUnitTest     12 套件 /  61 用例 / 0 失败 / 0 跳过
:app:testDebugUnitTest             58 套件 / 444 用例 / 0 失败 / 0 跳过
:app:assembleDebug                 BUILD SUCCESSFUL → app-debug.apk 23,647,862 B
:lib_bluetooth:compileDebugAndroidTestJavaWithJavac   通过
5 项 lib/biz 边界门禁 + app 10 项 check*                全 PASS
HEAD 9816a75 未变（未 commit / 未 push）；124 个已跟踪改动 + 57 个新增文件
```

### V 项逐条
| 项 | 结果 | 证据 |
|---|---|---|
| V1 | 通过 | `YoHsClient.readEarColor` → `YoHsClientImpl` → `HsProtocolEngine.submit(EarColorCommand)`；READY 后 `queryEarColorOnce` 每会话一次 |
| V2 | 通过 | `HsDeviceInfoChainTest` 8 例：`0x94`→`firmware=="V1.2.3"`；耳色回包→`earColor==0x2A`；观察者后挂回放；READY 只查一次（断连再就绪才第 2 次） |
| V3 | 通过 | `DeviceEventPipeline` 处理 `"94"`→`domain.setFirmware`；`reset()` 已接上（会话起点/终点/release 三处）；删除零调用的 `resetKeepingBattery/clearBattery` |
| V4 | 通过 | `HsDevicePort` 消费 `onDeviceInfo`→`firmware/earColor/earInCaseStatus`；`switchingDeviceWithoutDisconnectDoesNotCarryPreviousDeviceInfo` 断言换设备后 `firmware==""`、`earColor==-1`；`twoEarsInCaseMapsToNotWorn`/`earOutOfCaseOrUnreportedStaysUnknown`/`switchingDeviceDoesNotKeepPreviousNotWorn` 覆盖佩戴态负向接通 |
| V5 | 通过 | `HsNearbyScanner` 46 行门面 + `HsNearbyScanSession`/`HsNearbyScanCallbacks`/`HsNearbyScanLedger`/`HsScanFailures`；`callbacksFromPreviousSessionAreDroppedAfterRestart` 断言旧代次四类回调全丢 |
| V6 | 通过 | `"HS0` 字面量 0 命中（走 `HsGeneration.name()`）；`rawAdvertising(` 0 命中，唯一入口 `rawAdvertisingView()`；名称优先级单点 |
| V7 | 通过 | `CommandScheduler` `ignored` 0 命中，4 处改具名 + `HsLog.w` + 终态语义 |
| V8 | 通过 | `scan` 包 `src/main` `catch` 代码级 0；`YoBleScanThrottleGuardTest` 2 例（第 6 次 30000ms / +5s 25000ms / 期满前 1ms / 窗口滑过放行）；并发交错 5 例；`ble_01` 已同步 `subscribeScan` |
| V9 | 通过 | `ScanActivity` `startScan()` 0 命中；`enterScan()` 幂等；未授权只发 `PERMISSION_REQUIRED` |
| V10 | 通过 | 见上方命令块 |
| V11 | 通过 | 范围内 TODO/FIXME/HACK 0、`@Ignore` 0、双轨 0；`ScanError` 残留 0；`colorLabel` 0 |
| V12 | 未实机 | 仅静态 + 单测 + 门禁，未声称实机验证 |

## 5. 修复轮次与发现（含本轮引入并已修的问题）

| 轮 | 发现 | 处置 |
|---|---|---|
| 1 | lib 旧代异步 `onScanStopped` 误杀新会话并误发给新订阅者 | 每次 `start` 绑定带代次 fanout，三终态按代次判定，作废代次吸收；引擎调用移出句柄栈。反向验证撤改恰好 2 例红 |
| 1 | biz READY 中重连不清会话（幂等早退） | `sessionEstablished` 取代 `commandReady`；先 `endSessionAndNotify` 再清起点再开链路；`abortAll` 中止在途 |
| 1 | biz 生命周期事件双发、原因丢失 | `markNotReady` 改「先原因后状态」；`onLinkError` 按会话/尝试分流单发；超时只经引擎单点 |
| 1 | app `setDeviceObserver` 早于 `connect` 导致回放上一台设备 | 改为 `connect` 之后挂 observer |
| 2 | **跨模块 happens-before 不成立**：biz 清域在引擎线程，app 依赖它在 `connect` 返回前完成 | 清域搬到 `connect` 调用线程同步段；`GatedExecutor` 用例证明「任务未执行时回放仍空域」 |
| 2 | **本轮引入**：换目标时旧会话 `onDisconnected` 取消新尝试的看门狗 → 上层永久 CONNECTING | `arm` 移到 `Connecting`；连接事件改由恒挂 `ConnectionEvents` 承接（不能用「谁在听事件」承载计时）；4 例覆盖 |
| 2 | lib `onScanStarted/onScanStopped` 锁外读 live 集合（与已修 P1 同形态） | 判定与分发集合同临界区；补窗口探针 |

## 6. 下一阶段（另声明范围 + 可判定验收）

1. **旧会话在途回包窗口（两审结论冲突，需用例裁定）**：lib 终审判「生产靠 `YoBleCommandLink` 的 generation 兜住」，biz 终审判「窗口内旧回包仍会被 `sessionId > 0` 门接受并写回域」。验收：构造「调用线程清域 → 引擎线程收尾」窗口内投一条旧会话 `0x94` 回包，断言域未被写、端口未收到 `onDeviceInfo`；据结果决定是否需要引擎侧 epoch 门。
2. **零调用 API 清理**：`EarColorCommand.getEarColor`、`StorageCapacityResult.getTotalCapacity`、`DualConnectionPageData.getDualConnection/getPriorityMac/getDevices` 等只有声明无调用点。验收：删除或接通，grep 为 0。
3. **`earColor` 展示**：关于页当前显示协议色号原值（如「42」）或 `--`；仓库无可信色号→色名表，需产品提供映射后落 `R.string`/`DeviceFormats` 单点。验收：色号→文案映射表单一来源 + 未知回退 `--`。
4. **`WearState` 正向待协议**：已**负向接通** —— 设备主动上报的 `99EC96` 在仓状态（两耳在仓）经 `HsDeviceInfo.earInCaseStatus` 传到 app，`WearState.NOT_WORN` 只由「两耳都在仓」推出；有耳出仓或未上报一律 `UNKNOWN`（出仓不等于戴在耳朵上，不做正向断言）。`WearState` 已删除不可达的 `LEFT/RIGHT/BOTH` 三个取值与其文案资源（原为生产不可达、仅测试填充）。验收：协议提供佩戴态事件后引入对应取值并投真实值。
5. **app 端口测试的异步投递覆盖**：现有用例用同步替身模拟 `setDeviceObserver` 回放，未覆盖 `MainDeviceObserver` 经 `mainHandler.post` 的时序。验收：异步替身或用例断言时序等价。
6. **3 个静态测试钩子**：`resetForTest`/`isEngineRunningForTest`/`submitTransitionBarrierForTest`。验收：改为可注入调度器抽象，生产类不再持有 `@VisibleForTesting` 静态成员。
7. **真机回归**（V12 缺口）：扫描启停、失败恢复、A→B 换设备（首页 footer 与关于页固件/颜色）、适配器开关循环。验收：截图 + Logcat 无 FATAL、无 `CONNECTING` 卡死。
8. **历史审查文档保留原样**：`round1_*`/`*_review`/`*_verification_report` 中的 `HsBroadcastScanMapper` 等已删符号是当时事实记录，不改写；现行设计以 `ble_scan_state_machine_and_architecture.md` 与本文件为准。
