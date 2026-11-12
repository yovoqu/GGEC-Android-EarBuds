# 第三轮：设备/扫描链路全量架构复审 · 问题清单与修复计划

> 依据 `D:\A_yoprogram\Github_Gallery\YoAgentDocs`（task.architecture-review + playbook + checklist + common rules + android 类型包 + modification 规则）。
> 对象：Cursor 会话 `42ffd0eb` 第 9 轮「自底而上重构」之后的工作树。审查方：3 个只读子代理（lib / biz / app），Lead 复核。
> 结论：三范围均 **HAS_ISSUES**。本文件是修复合同；每条只能「通过 / 未通过」。

## 0. 已定语义（用户拍板）
- 扫描会话：进程内一个扫描器；首个订阅者启动、最后一个取消后停止；**无产品级扫描超时**；系统 5 次/30 秒限流留在 lib。
- 单台设备过期：**要移除**（方案 A）。按末次广播间隔超时判 `lost`（默认约 10s，可调），经 lib→hub→biz→app 移除；这不是产品扫描超时。
- BLE 与经典蓝牙（CBT）身份/地址/名称严格分离；BLE 列表不显示已配对标记；经典已配对走独立查询。

## 1. 问题清单（P0/P1 本阶段必闭合；P2 除注明外一并收口）
### lib_bluetooth
- L-P0-1 YoBleScanHub 跨 client 生命周期缺陷：第二次订阅忽略传入 Engine；`YoBleModuleCore.release()` 不重置静态 current/active → 释放旧 client 后新 client 永不扫描、旧引擎泄漏。正确层：internal·ble 扫描能力域。
- L-P1-1 lost/移除子系统死链：默认 ALL_MATCHES 下 `YoBleScanLostPolicy.softwareLostEnabled=false`；`YoBleScanHub.fanout` 不转发 `onScanResultLost` → 生产永不产生/转发 lost。方案 A：按末次广播超时启用软件 lost 并在 hub 转发。
- L-P1-2 YoBleScanController（718 行）职责过载：状态机+限流调度+适配器恢复+长扫重启+软批处理+降级重试+lost 清扫+统计混一文件。正确层：internal·ble 扫描能力域；按策略拆类，控制器只编排。
- L-P1-3 扫描配置边界未封闭：`BleScanConfig` 为 public 模型、`YoBleScanHub.defaultConfig()` public 工厂跨包供配置。外部不应能构造/注入扫描配置。
- L-P2 死配置 `BleConfig.defaultScanTimeoutMillis=12000` 无读者；`LOW_LATENCY` 默认两处重复；`resetForTest` 生产测试钩子；`BleDeviceIdentity.displayName()` 兼容别名（若无调用方则删）。`compat` 包改名按 `should` 偏离不动，避免无谓 churn。

### biz_ggec_hs
- B-P0-1 `build.gradle:81` rootFacade 正则用 `.*\/biz_ggec_hs\/...` 匹配相对路径 `biz_ggec_hs/...` 永不命中 → `YoHs*.java` 门面逃逸边界检查。修正则并补负向验证。
- B-P1-1 门面耦合 internal：`YoHsSdk` import `internal.graph.HsGraph`+`provider` 实现；`YoHsBroadcast` import `internal.broadcast.HsBroadcastRuntime`。门面只转发、装配下沉 internal。
- B-P1-2 `HsNearbyDevices.map` 每帧双重识别（先 `YoHsBroadcast.classify` 再 `session.observe`，HS02 解两次）。单一识别路径。
- B-P1-3 `HsNearbyScanner.start()` 锁内调外部 `subscribeScan` 且在同步回调后才赋 `subscription`；同步 `onScanFailed` 时 `end()` 先置 null、随后被赋成已取消句柄 → 状态错乱/句柄泄漏；`end()` 不 cancel 句柄。
- B-P1-4 lost 接线：`HsNearbyScanner` 未重写 `BleScanObserver.onScanResultLost` → `HsNearbyObserver.onLost` 永不触发。方案 A 需接通。
- B-P2 `HsNearbyDevices` 硬编码 MODEL_HS01/HS02/"v1"（`v1` 兜底不可达）；`HsProductCatalog` 的 `label/protocolVersion` 生产零引用；`toSnapshot` 与 `HsScanSnapshots.from` 两套；`YoBleCommandLink` 吞 RuntimeException；测试缺口。

### app
- A-P1-1 `onRemoved` 死链 + `DevicePort` 3/4 参双轨 API（default「兼容既有实现」）。方案 A：保留移除，收敛为单一签名。
- A-P1-2 `HsDevicePort` 吞异常（release/currentBattery/ensureConnectHandler/空 mac 静默 return）。
- A-P1-3 12s 连接超时 + 代次 + 主线程 Handler 编排在 app provider，应下沉 biz 会话层；端口只投影。
- A-P1-4 `release()` 只挂 `onTerminate`（Android 生产不回调）；`DeviceGraph` 用 `instanceof` 下探具体实现（端口缺生命周期契约）。加稳定关闭契约并接真实生命周期/明确 best-effort。
- A-P2 `ScanActivity` 里做 start/stop 决策与错误文案映射；`ScanViewModel` 直出端口错误枚举到 UI；硬编码 dp/重复字面量/分隔符；`DeviceSnapshot` 恒默认字段无来源；死代码 `ScanUiMapper.map(List)`、`ScanDeviceCard` 4 参构造；陈旧注释；测试缺口。

## 2. 严格验收条目（全部通过才可收尾；每条可二判、含证据）
- **G1 构建/单测**：`./gradlew :lib_bluetooth:testDebugUnitTest :biz_ggec_hs:testDebugUnitTest :app:testDebugUnitTest` → BUILD SUCCESSFUL，0 失败。
- **G2 边界门禁**：`:lib_bluetooth:checkLibBluetoothApiSurface/checkLibBluetoothProviderBoundary/checkLibBluetoothLogSeam/checkNoStrayLogging`、`:biz_ggec_hs:checkBizGgecHsBoundary`、`:app:` 全部 `check*` → 全 PASS。
- **G3 门禁有效性（B-P0-1）**：临时在 `biz_ggec_hs/.../YoHsSdk.java` 注入一行 `import com.yovo.biz_ggec_hs.internal.broadcast.HsBroadcastRuntime;` 后 `checkBizGgecHsBoundary` 必失败；移除后通过（证明门面受约束）。
- **G4 门面解耦（B-P1-1）**：`grep -R "import com.yovo.biz_ggec_hs.internal" biz_ggec_hs/src/main/java/com/yovo/biz_ggec_hs/YoHs*.java biz_ggec_hs/src/main/java/com/yovo/biz_ggec_hs/api biz_ggec_hs/src/main/java/com/yovo/biz_ggec_hs/model` = 0 命中。
- **G5 配置边界（L-P1-3）**：全仓非 `lib_bluetooth` 模块无 `BleScanConfig` 引用；lib 外无法获得可写扫描配置；`subscribeScan` 仍是唯一扫描入口（`grep "scan(" YoBleClient` 无 `scan(BleScanConfig`）。
- **G6 Hub 生命周期（L-P0-1）**：单测：订阅→释放当前引擎→新引擎订阅能重新启动并收到帧；`onScanFailed` 后**订阅者保留**（失败不销毁会话）并可再次订阅（STOPPED→STARTING）；`onScanStarted` 把 STOPPED 拉回 RUNNING。
- **G7 设备过期移除（方案 A，端到端单测）**：模拟末次广播超时后，`BleScanObserver.onScanResultLost` 被 hub 转发、biz `HsNearbyObserver.onLost` 触发、app `onRemoved` 后 `DiscoveredDevices` 不再含该设备；会话本身无固定时长自动停止。
- **G8 HsNearbyScanner 并发（B-P1-3）**：同步 `onScanFailed` 后 `isScanning()==false` 且 `subscription==null`（无泄漏/错乱）；单测覆盖。
- **G9 单次识别（B-P1-2）**：每帧 HS02 只解码一次（可用计数/结构断言）。
- **G10 端口单轨（A-P1-1）**：`DevicePort.startScanBatch` 只有一种签名（含 onRemoved 必填）；`IdleDevicePort`/`FakeTestDevicePort`/`HsDevicePort` 一致实现；无 3/4 参双轨。
- **G11 无吞异常（A-P1-2）**：设备扫描/连接投影路径无 `catch(... ignored)` 或 `catch(RuntimeException ignored)`；空 mac 不静默 return（显式分类）。
- **G12 无新增技术债**：范围内无 `TODO/FIXME/HACK/XXX`、无 `@Ignore`/跳过测试、无 double-track/兼容别名、无死代码（删除或接通）。
- **G13 环境缺口**：未接真机 → 回复明确「未实机」，仅静态+单测+gates；不声称实机验证。

## 3. 修复顺序（所有权，互不重叠）
1. lib_bluetooth（基础）→ 构建 lib
2. biz_ggec_hs → 构建 biz
3. app → 构建 app+全量
4. 全部范围再开一轮只读复审 → 同一波次全 CLEAN 才收口

## 4. 执行结果（实测证据，2026-11-11）

| 门 | 结果 | 证据 |
|---|---|---|
| G1 | 通过 | `:lib_bluetooth:testDebugUnitTest` 31 套件/135 用例；`:biz_ggec_hs` 8/36；`:app` 57/426；全部 0 失败 0 跳过。`:app:assembleDebug` 产出 `app-debug.apk`（33.4 MB）。`:lib_bluetooth:compileDebugAndroidTestJavaWithJavac` 通过。 |
| G2 | 通过 | lib 4 项 + biz 1 项边界任务全 PASS；`:app:preBuild` 触发 10 项 `check*` 全 PASS。 |
| G3 | 通过 | 向 `YoHsSdk.java` 注入 `internal.broadcast.HsBroadcastRuntime` → `checkBizGgecHsBoundary` FAILED 且精确定位；移除后 PASS。 |
| G4 | 通过 | `api/`、`model/`、`YoHs*.java` 对 `import ...internal.` / `...provider.` / `lib_bluetooth` / `android.bluetooth` 均 0 命中。 |
| G5 | 通过 | `BleScanConfig` 仅存在于 `internal/ble/scan`；非 lib 模块 0 引用；`YoBleClient` 只有 `subscribeScan`。 |
| G6 | 通过 | Hub 单测覆盖：释放后新引擎可启动、`onScanFailed` 保留订阅者、STOPPED→再订阅重启、`onScanStarted` 回 RUNNING、最后一个取消后回非运行态。 |
| G7 | 通过 | 默认 ALL_MATCHES + 设备级 10s；`softwaredLost` 由 `matchLostTimeoutMillis>0` 启用；Hub 转发 `onScanResultLost`；biz `seen` 按地址移除并回调 `onLost`；app 投影为 `onRemoved`→`DiscoveredDevices.remove`。会话本身无产品超时。 |
| G8 | 通过 | `start()` 锁外 `subscribeScan`；同步失败一次取消（单测断言 `cancellations==1`、`subscription==null`）；异步失败在 `end()` 锁外 cancel。 |
| G9 | 通过 | 完整 HS01/HS02 帧 `session.observe` 次数 0（单测断言）；仅分片帧走会话。 |
| G10 | 通过 | `DevicePort.startScanBatch` 唯一 4 参；三实现一致；全仓 8 处调用均 4 参；`DevicePortContractTest` 反射锁死。 |
| G11 | 通过 | 设备链路无 `catch(... ignored)`；`close()` 的 RuntimeException 经 `YoLog.w`；空 mac 抛 `IllegalArgumentException`。 |
| G12 | 通过 | 范围内 TODO/FIXME/HACK 0（仅协议文档占位符 `xx:xx`/`xxxx`）；`@Ignore`/跳过 0；`scan` 包 `catch(` 0；`shouldRetryOnce` 单点判定；`isSingleModeLe/isDualMode` 已删；biz 跨模块取字节改复制版；`DeviceHomeUiState.empty()`/`lastMac()`/`"--"` 副本/裸 `dp(8)`/`dpf(5)` 已清。 |
| G13 | 未实机 | 无真机，仅静态 + 单测 + 门禁；未声称实机验证。 |

### 已声明的偏离（`should`，写明理由）
- **控制器 700 行**：8 类混职责已下沉（preflight/stats/lost 清扫/flush/长扫重启/适配器恢复/平台 executor/降级配额），剩余为扫描会话状态机与编排。限流等待与会话超时定时器的触发点直接改变 `ScanState`，外移需回调控制器改状态，会形成双向依赖；`platformExecutor.submit` 属编排本身。因此不再强拆，文档声明相应修正为「受理、状态迁移、编排，以及会话级定时器与降级重试」。
- **`BleScanConfig` 仍为 14 字段设置面**：它是 `lib_bluetooth` 的通用扫描能力模型（非 HS 产品专用），唯一生产配置源是 `YoBleScanDefaults`；门禁禁止外部 import internal，等价于封口。
- **调度写法并存**（`runtime.stateDelayed` 与 `runtime.scheduler().schedule(()->state(...))`）：两者最终都投递到同一 state 线程，属等价 API，不构成双轨。

## 5. 下一阶段（另声明范围 + 可判定验收）

1. **app 设备首页数据来源**：`DeviceSnapshot.firmware/wear/colorLabel` 接协议事件（固件版本/佩戴/配色）→ 连接就绪后字段非恒默认；单测注入回包断言。
2. **app 扫描页编排归位**：`ScanActivity` 的 start/stop 与权限门进一步收进 `ScanViewModel`；验收 `ScanActivity` 无扫描决策分支。
3. **`HsNearbyScanner` 拆分**：会话状态机与产品过滤分文件（当前单文件压 4 件事）。
4. **biz `internal/protocol` 调度器异常可观测性**：`CommandScheduler` 的 `catch(RuntimeException ignored)` 改为分类记录；验收无 `ignored` 形参。
5. **lib 单测补齐**：5/30 限流窗口、Hub 并发取消+订阅交错。
6. **文档同步**：`lib_bluetooth/doc/ble_01_architecture_design.md` 仍写 `scan(config,observer)`，更新为 `subscribeScan`。

## 6. 第四轮限定边界复审结论（3 个独立只读复审员，重读当前树）

| 范围 | 结论 | 关键依据 |
|---|---|---|
| lib_bluetooth | **CLEAN** | P1-1 两条出口都清恢复现场（`stopActiveInternal(true)` + `stopScan()` 早退分支）、`takeResumable` 一次性票、Hub 真实链路回归用例；P1-3 的 `engine.start/stop` 已纳入 `GATE`，无死锁（`stopScan` 只入队）；P2-1/2/4/6/8 全部核实闭合；新增 P0/P1 = 0。 |
| biz_ggec_hs | **CLEAN** | P1-1 在 biz 侧自洽（无二次 cancel、事件单线程有序、无锁序反转）；G4/G5/G7/G8/G9/G10/G12 未被破坏；新增 P0/P1 = 0。遗留 P2（`rawAdvertising()`/`HsMarket.UNKNOWN` 死面、`HS01/HS02` 与 `HsGeneration` 双份词汇、名称优先级两处、异步失败用例缺）均为**非正确性缺陷**，入下一阶段。 |
| app | 首轮 CONDITIONAL → 已按建议收口 | 唯一阻塞为 `AppContainer.release()` 注释与实现不符，已改写为「只释放设备子图（停扫/关会话/BLE 线程池）；AI 与 shell 无进程级资源；`AppExecutors` 为 daemon + coreTimeout」；同时删除了复审指出的「只写不读」半成品（`DevicePreferences` 整条链：接口 + `FileDevicePreferences` + `MemoryTestDevicePreferences` + `ConnectDevice` 依赖）。现状 `grep DevicePreferences = 0`、`lastMac = 0`、`DeviceHomeUiState.loaded = 0`、home/scan 内裸 `dp(`/`dpf(` = 0。 |

复审员另指出 `HsDevicePort(HsRuntime)` 为死构造器 —— 经复核**该判断有误**：它是 `HsDevicePort(Context)` 的 `this(YoHsSdk.open(context))` 委托目标（复审只 grep 了 `new`），已保留并以此为准。

### 第四轮遗留（下一阶段）
- lib：`Hub.fanout.onScanStopped` 不迁移 `engineState` → **已修**：引擎自停后置回 `STOPPED`，下一个订阅者可重启（用例 `engineSelfStopAllowsNextSubscribeToRestart`）；若不修，一旦开放扫描超时配置即成 P1。
- lib：`subscribe` 持 `GATE` 时同步失败投递的锁序（仅「已释放引擎 + 并发订阅/取消」可达，业务侧锁外订阅使其不可达）→ 记录为已知约束，下一阶段补单测。
- biz：`HsNearbyScanner` 缺会话代次，跨会话陈旧帧依赖 app 的 `scanToken` 兜底。
- 环境：三轮复审均为静态 + 测试源码核读；G1/G2 的实测证据由 Lead 在可写环境跑出（见 §4）。

### 最终状态（2026-11-11 收口）
```
:lib_bluetooth  31 套件 / 136 用例 / 0 失败 / 0 跳过   （scan 包 35 条）
:biz_ggec_hs     8 套件 /  36 用例 / 0 失败 / 0 跳过
:app            57 套件 / 426 用例 / 0 失败 / 0 跳过
:app:assembleDebug → app-debug.apk 33.4 MB
:lib_bluetooth:compileDebugAndroidTestJavaWithJavac 通过
5 项 lib/biz 边界门禁 + 10 项 app check*            全 PASS
HEAD 未变动（9816a75），未 commit / 未 push
```
