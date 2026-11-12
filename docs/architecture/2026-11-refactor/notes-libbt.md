# lib_bluetooth 重构记录（teammate: libbt）

> 权威契约：`docs/architecture/2026-11-refactor/design.md`。本文件只记录 `lib_bluetooth` 这一刀的落地证据与验证结果。

## 1. 覆盖文件

### 新增
| 文件 | 职责 |
|------|------|
| `provider/lib_common/log/YoBtLogger.java` | 模块唯一日志缝，直接委托 `lib_common.log.YoLog`/`YoLogBootstrap`；`provider/**` 是本模块唯一允许出现 `com.yovo.lib_common.log` 的目录 |
| `provider/lib_common/permission/BluetoothPermissionProvider.java` | `BluetoothPermissionPort` 的 lib_common 实现（`YoPermission` → 蓝牙权限）；无静态实例、无安装入口，由装配根构造 |

### 删除
| 文件 | 原因 |
|------|------|
| `provider/lib_common/BluetoothProviderInstaller.java` | R3：禁止静态安装器 / `installAll()` / `installed` 标志（design §3.1） |
| `provider/lib_common/PermissionProvider.java` | 改名并移入 `provider/lib_common/permission/`（design §3.6） |
| `internal/common/diagnostics/YoBtLogger.java` | 迁至 `provider/lib_common/log/`（design §3.5） |

### 修改（权限端口去静态化 + 构造注入）
- `internal/common/permission/YoBluetoothPermissionChecker.java`：删除 `static volatile BluetoothPermissionPort port` 与 `static setPort(...)`；改为 `private final` 端口 + 构造注入。
- `internal/common/permission/BluetoothPermissionPort.java`：删除 `UNINSTALLED` 全局兜底实现（端口不再有"未安装"默认态，缺失即编译期不可达）。
- 装配根 `internal/BluetoothSdkCore.java`：`new BluetoothPermissionProvider()` → `new YoBluetoothPermissionChecker(port)` → 向下传；`YoBtLogger.ensureInitialized(context)` 保留在入口。
- 传递链（全部为构造参数注入，无人自行 `new` checker）：
  - `internal/classic/ClassicClientImpl` → `internal/classic/internal/android/YoCbtAndroidClient` → `internal/classic/internal/android/YoCbtAndroidModuleGraph.create(..., permissionChecker)`
  - `internal/ble/BleClientImpl` → `internal/ble/internal/android/YoBleAndroidModuleGraph.create(..., permissionChecker)` → `internal/ble/device/YoBleAndroidCapabilityInspector(context, permissionChecker)`
  - `YoCbtAndroidModuleGraph` 内已有使用点全部改为使用注入实例：`YoCbtAndroidDiscoveryPlatform`、`YoCbtDiscoveryController`、`YoCbtAndroidBondedDeviceLoader`、`YoCbtBondedDeviceProvider`、`YoCbtBondOperationController`；`YoCbtAndroidClient` 经 `modules.permissionChecker` 使用同一实例。
- `internal/common/diagnostics/BluetoothDiagnosticsBus.java`：改用新日志缝。
- 约 30 个内部类仅 Import 迁移：`internal.common.diagnostics.YoBtLogger` → `provider.lib_common.log.YoBtLogger`。
- `YoBluetoothSDK.java`：类注释同步为"装配根构造并注入"，对外方法签名未变。
- `src/test/.../YoCbtSppConnectionLifecycleTest.java`：删除 `YoBtLogger.setEnabled(false)`（模块级静态日志开关已随静态可写状态一并删除；测试断言对象是生命周期状态机，与该开关无关）。
- `lib_bluetooth/build.gradle`：门禁改写（见第 4 节）。

## 2. 数据链路

### 2.1 设计前（现状，证据等级：已读到 — 代码位置）
1. `YoBluetoothSDK.ble()/classic()` → `BluetoothSdkCore`：`YoBtLogger.ensureInitialized` + `BluetoothProviderInstaller.installAll()`。
2. `installAll()` 写 `YoBluetoothPermissionChecker.setPort(PermissionProvider.INSTANCE)`，`installed` 静态标志去重。
3. `YoCbtAndroidModuleGraph.create(...)` 与 `YoBleAndroidCapabilityInspector(...)` 各自 `new YoBluetoothPermissionChecker()`，读全局静态 `port`。
4. 缺陷跳点：权限端口是**进程级可变全局状态**；同一进程内任何代码都能替换端口，且 `UNINSTALLED` 兜底把"未装配"推迟到运行期 `IllegalStateException`。测试/多 SDK 实例之间无法隔离。
5. 日志缝位于 `internal/common/diagnostics/`，但直接 import `lib_common.log`，与"非 provider 不得依赖 lib_common"冲突，只能靠 build.gradle 文件名白名单豁免。

### 2.2 设计后（实现点）
1. `YoBluetoothSDK.ble()/classic()`（`YoBluetoothSDK.java:22-39`）→ `BluetoothSdkCore`（`internal/BluetoothSdkCore.java:24-42`）。
2. 装配根内唯一构造点：`BluetoothSdkCore.permissionChecker()` → `new YoBluetoothPermissionChecker(new BluetoothPermissionProvider())`（`BluetoothSdkCore.java:39-42`）。
3. 向下传：`ClassicClientImpl(context, config, checker)` → `YoCbtAndroidClient(context, oldConfig, checker)` → `YoCbtAndroidModuleGraph.create(..., checker)`；`BleClientImpl(context, config, checker)` → `YoBleAndroidModuleGraph.create(..., checker)` → `YoBleAndroidCapabilityInspector(appContext, checker)`。
4. 使用点：`checker.has(context, YoBluetoothPermission.SCAN/CONNECT/ADVERTISE/LOCATION)` → `BluetoothPermissionPort.has` → `BluetoothPermissionProvider` → `YoPermission.isGranted`（副作用终点：系统权限查询）。
5. 日志：任意内部类 `YoBtLogger.d/i/w/e` → `provider/lib_common/log/YoBtLogger` → `YoLog`（Logcat + 文件落盘）。
6. 运行观察：`:lib_bluetooth:compileDebugJavaWithJavac`、`:lib_bluetooth:testDebugUnitTest`（176 tests / 0 failures）、`:lib_bluetooth:check` 全绿（见第 5 节）。真机权限查询路径未实跑（见第 6 节未闭合项）。

## 3. 与 design.md §3 的逐条对应

| §3 | 要求 | 落地 |
|-----|------|------|
| 1 | 删除 `BluetoothProviderInstaller`（含 `installAll()`、`installed`） | 文件已删除，全仓代码零残留 |
| 2 | `YoBluetoothPermissionChecker` 去静态化：删 `static volatile port` / `setPort`，实例持有端口 | 构造注入 `final` 端口；无静态成员 |
| 3 | 所有内部使用点改构造参数注入，不再自行 `new` | 见 1 节传递链；`YoCbtAndroidModuleGraph.create` 内 `new YoBluetoothPermissionChecker()` 已删除 |
| 4 | 装配集中在 `BluetoothSdkCore`，不允许任何静态可写状态 | 装配根唯一构造点；模块内 `/static\s+(?!final)/` 在 `provider/**` 由门禁拦截，`internal/**` 无新增静态可写状态（`YoBtLogger.enabled` 已随迁移删除） |
| 5 | `YoBtLogger` 迁至 `provider/lib_common/log/` | 已迁移，旧文件删除，全部引用/测试已更新 |
| 6 | `PermissionProvider` 改名 `BluetoothPermissionProvider` 移入 `permission/` | 已改名迁移，`INSTANCE` 静态单例一并去除 |
| 7 | `api/`、`model/` 不得 import `provider/**`、`internal/**` | 现状成立（扫描 0 命中），并写入结构性门禁 |
| 8 | `build.gradle` 门禁结构性化，删除 `YoBtLogger`、`provider/lib_common` 白名单字符串 | 见第 4 节；旧三个门禁任务合并为 `checkLibBluetoothBoundary` + `checkLibBluetoothLogSeam` + `checkNoStrayLogging` |

## 4. 门禁（`lib_bluetooth/build.gradle`）

判定只用目录事实（`provider/**`、`api/**`、`model/**`、`internal/**`、根包），**不含任何类名 / 文件名 / 路径白名单**：

| 规则 | 内容 | 对应 |
|------|------|------|
| R1 依赖单点接入 | 除 `provider/**` 外禁止 `import com.yovo.lib_common.` | §1.2 R1 / §8 |
| R2 实现不外泄 | `api/**`、`model/**` 禁止 `import ...lib_bluetooth.provider.` 与 `...lib_bluetooth.internal.` | §1.2 R2 / §8 |
| R3 无静态可写状态 | `provider/**` 内禁止 `static` 非 `final` 字段 | §1.2 R3 / §3.4 |
| R3 无安装入口 | `provider/**` 内禁止声明 `install` / `installAll` / `setPort` 方法 | §1.2 R3 / D2 |
| 根包唯一门面 | 根包只允许 `YoBluetoothSDK.java` | §3.8 |
| 日志唯一缝 | `provider/**` 之外禁止直接调用 `YoLog`（无文件名豁免） | §3.8 |
| 散点日志 | 禁止 `android.util.Log` / `System.out` / `System.err` / `printStackTrace` | 原有规则保留 |

反证验证（探针文件，验证后已删除）：注入 5 个违规探针后门禁逐条报错并 `FAILED`，证明规则真的生效而不是空跑：
- `internal/__BoundaryProbe.java`（非 provider import `lib_common.log`）→ "非 provider 代码直接依赖 lib_common"
- `provider/lib_common/permission/__Probe.java`（`private static volatile Object`、`static void installAll()`）→ "provider 不得持有静态可写状态" / "provider 不得声明安装入口"
- `__RootProbe.java`（根包第二个文件）→ "根包只允许 YoBluetoothSDK.java"
- `api/__ApiProbe.java`（import `internal.BluetoothSdkCore`）、`model/ble/__ModelProbe.java`（import `provider...YoBtLogger`）→ "api/model 引用实现层"

## 5. 验证结果（实跑）

命令与结果（`--offline --console=plain`）：

1. `.\gradlew.bat :lib_bluetooth:compileDebugJavaWithJavac --offline --console=plain` → **BUILD SUCCESSFUL**（19s）
2. `.\gradlew.bat :lib_bluetooth:testDebugUnitTest --offline --console=plain` → **BUILD SUCCESSFUL**（21s），结果聚合：36 suites / **176 tests / 0 failures / 0 errors / 0 skipped**
3. `.\gradlew.bat :lib_bluetooth:check --offline --console=plain` → **BUILD SUCCESSFUL**（25s），其中 `checkLibBluetoothBoundary`、`checkLibBluetoothLogSeam`、`checkNoStrayLogging` 均 executed 且通过

残留检查（`git grep`，排除 `**/build/**`）：
- `BluetoothProviderInstaller`、`setPort(`：**代码内 0 命中**（`.java`/`.gradle`）
- `lib_bluetooth.provider`、`internal.common.diagnostics.YoBtLogger` 在其他模块的引用：0 命中

## 6. 未闭合 / 待确认

1. ~~历史文档残留类名（待 lead 裁决）~~ → **已裁决（lead：方案 C，不删历史文档，加"历史快照"标记）**。
   - 本模块 `lib_bluetooth/doc/` 下三份重构前文档已在文件首行加标记：
     `> 历史快照（2026-11 重构前）：本文所述 Provider 安装机制（BluetoothProviderInstaller / setPort）已删除，当前架构见 docs/architecture/2026-11-refactor/design.md。`
     涉及文件：`00_requirements_reflection_and_polished_prompt.md`、`ble_01_architecture_design.md`、`lib_bluetooth_full_rebuild_prompt_new_architecture.md`。正文未改写（保留决策轨迹）。
   - 残留判据按 lead 口径收敛为**可执行代码与构建脚本**：`.java` / `.gradle` / `.pro` 内 `BluetoothProviderInstaller`、`setPort` 零残留（已 `git grep` 确认）；`docs/**` 与 `lib_bluetooth/doc/**` 属历史档案，非现状残留。
   - `docs/**` 下历史审计报告（`docs/00_project_audit.md`、`docs/audit/round1_ble.md`、`docs/device/round1_device_module_audit.md`、`docs/app_architecture_review_and_design.md`、`docs/app_refactor_verification_report.md`）不在本模块写作用域，由 lead 在最终报告统一声明。
2. **真机权限查询未实跑**：`:lib_bluetooth:check` 覆盖编译、单测与门禁，未连真机跑 instrumentation（`src/androidTest` 未改动）；`BluetoothPermissionProvider → YoPermission.isGranted` 的运行期行为与重构前一致（同一调用，仅装配方式变化），无新增分支。
3. **跨模块集成验证不在本刀范围**：`:app:assembleDebug`、`:biz_ggec_hs:check` 由 lead 统一跑；本模块对外契约（`YoBluetoothSDK`、`api/`、`model/`）未变，其他模块无需改动。

## 7. `lib_bluetooth/doc/` 文档边界

- `lib_bluetooth/doc/` 是**重构前工程档案**（需求反思、BLE 架构设计、全量重构提示词），只描述当时的设计意图与审计过程，不参与编译、不作为契约；当前唯一权威契约是 `docs/architecture/2026-11-refactor/design.md`。
- 该目录内的文档不得作为实现依据；其中出现的类名/结构（含已删除的 `BluetoothProviderInstaller`、`setPort`、`Provider.INSTANCE`）一律视为历史记录，不代表现状。
- 三份文档首行已加"历史快照"标记，正文保留原样以留存决策轨迹；后续若需要更新架构描述，写在新契约文档，不回写历史档案。

---

# 第二轮：扫描机制层（lib 只认帧与平台事实）

> 依据：`scan-architecture.md` §0/§2、`product-taxonomy.md` §2.2。

## 8. 新公开契约（`api/`，纯接口/record/抽象契约）

| 文件 | 形态 | 内容 |
|------|------|------|
| `api/BleScanPlan.java` | `record` | `(BleScanMode mode, long reportDelayMs, boolean legacy, List<BleScanFilter> hardwareFilters)`；构造期校验非空/非负，列表不可变；`defaults()` = 低时延/不批处理/非 legacy/无硬过滤 |
| `api/BleScanFrameFilter.java` | `interface` | `boolean matches(BleScanResult frame)`；纯谓词、可热替换 |
| `api/BleScanSubscription.java` | `abstract class extends BleOperationHandle` | `void setFrameFilter(@Nullable BleScanFrameFilter)`；既是可取消句柄，又能热替换谓词 |
| `api/BleScanCapabilities.java` | `record` | `(boolean offloadedFiltering, boolean batching, boolean extendedAdvertising)` |
| `api/BleScanHealth.java` | `record` | `(boolean scanning, long startedAtMillis, int startsInLastWindow, long lastFrameAtMillis, BleAdapterState adapterState)` |
| `api/YoBleClient.java` | 增量 | `subscribeScan` 返回 `BleScanSubscription`；新增 `reconfigureScan(BleScanPlan)`、`scanCapabilities()`、`scanHealth()` |

**与任务书签名的一处偏差（可编译性所迫；lead 已裁决接受，并同步到 `scan-architecture.md`）**：
任务书写的是 `interface BleScanSubscription extends BleOperationHandle`，但 `BleOperationHandle` 是**类**，
Java 的接口不能继承类。为保证"订阅就是可取消句柄"这一语义不变，`BleScanSubscription` 落成
`abstract class extends BleOperationHandle`（`BleOperationHandle` 去掉 `final`），实现类
`internal/ble/scan/YoBleScanSubscription` 持有 `AtomicReference<BleScanFrameFilter>`。
调用方按 `BleOperationHandle` 收口即可，取消语义与其它句柄完全一致。

**裁决记录（lead）**：
1. 抽象类方案接受。约定：`BleScanSubscription` **只**新增 `setFrameFilter`，其余能力全部继承自
   `BleOperationHandle`；`BleOperationHandle` 去 `final` 是这条约定的必要代价（把句柄整体改接口会波及
   37 处构造点与 biz 测试，风险远大于收益）。
2. 门禁只封产品分类学标识，**不封裸 `deviceType`**（理由见 §10；`build.gradle` 里已写死这段注释，
   防止后续有人"顺手补齐"）。
3. 内部恢复路径（长扫到期、适配器重新开启）不受限流守卫拦截，但必须：① 计入 `startsInLastWindow`（已满足）；
   ② 只在明确事件上触发，禁止紧循环重试。平台限流命中时上层需要能提示"搜索受限"——本层已提供判定所需的
   全部事实（`scanning` + `lastFrameAtMillis` + `startsInLastWindow`），`SILENT`/`THROTTLED` 这类**状态名属
   上层语义**，由 biz/app 从这些字段派生，lib 不产出产品告警文案。

**收尾（lead 指派，已落地）**：`YoBleScanHub` 类注释里原有一处指向 biz 具体回调类名（该类已并入 biz 的
`HsNearbyImpl` 后删除）的表述，已改为不依赖上层类名的「业务侧扫描会话」。同时复核：`lib_bluetooth/src/**`
内已无任何 `Hs*` 上层类型引用（`git grep` 0 命中），机制层不再点名业务实现。

## 9. 机制实现落点

| 机制 | 落点 | 说明 |
|------|------|------|
| 帧级谓词逐帧求值 | `YoBleScanHub` 的 fanout（`onDeviceFound` / `onBatchScanResults` / `onScanResultLost`） | 每个订阅各有谓词，逐帧读当前值再求值；批量帧按订阅过滤后再交付（过滤后为空的批次不投递） |
| 谓词热替换 | `YoBleScanSubscription.setFrameFilter` | 只 `AtomicReference.set`，不触碰 `Engine.start/stop`，不清空订阅 |
| 唯一显式重扫 | `YoBleScanController.reconfigure(BleScanPlan)` → `applyReconfigure` → `restartPlatformScan` | 与当前计划相等则幂等成功不重扫；无会话返回 `ILLEGAL_STATE`；窗口耗尽返回 `SCAN_THROTTLED` 且不调平台 |
| 计划 → 平台配置 | `YoBleScanDefaults.config(BleScanPlan)` | 单点映射：超时/lost/去重等机制策略固定，平台参数来自计划 |
| 硬限流守卫 | `YoBleScanController.startScanInternal` + `YoBleScanThrottleGuard.wouldExceed()` | 窗口内启动次数达上限时直接失败（`SCAN_THROTTLED`），**不调用平台 `startScan`**；拒绝不打断已在跑的会话 |
| 启动计数 | `onPlatformStartResult` 每次平台启动成功后 `recordScanStarted()` | 重配与内部重启同样消耗平台限流额度，因此不只记首次启动 |
| 健康度 | `YoBleScanController.health()` | `startedAtMillis` 进入 SCANNING 时写入、停止/失败清零；`lastFrameAtMillis` 每次收到平台帧更新；`startsInLastWindow` 来自守卫 |
| 能力查询 | `YoBleScanController.capabilities()` → `YoBleScanPlatform.supportsOffloadedFiltering/supportsHardwareBatching/supportsExtendedAdvertising` → `BluetoothAdapter` | 扩展广播查询按 API 26 版本分支（低版本直接 false，不用 `@SuppressLint` 藏判空） |

**删除的旧机制（D9：不留双轨）**：`model/ble/BleScanThrottlePolicy`（UNRESTRICTED / DELAY / FAIL_FAST 三态）
及其延迟启动分支（`ScanState.THROTTLED_WAIT`、`pendingStart`、`delayedStartHandle`）。限流只能是硬拒绝，
"等窗口空出来再扫"会把用户意图变成长时间静默等待，且与平台的静默黑洞叠加。`git grep` 确认零残留。

## 10. 门禁新增（`checkLibBluetoothScanMechanismOnly`）

- 规则：`src/main/java/**` 出现产品分类学标识 `modelId` / `projectId` / `hsCapability` / `hsDeviceType` /
  `productModel` / `productId` / `variantId` / `HS0x` 即失败；注释行（`//`、`*`、`/*`）排除，只允许在注释里
  解释"本层不得引入业务概念"。
- **口径说明（需 lead 知悉）**：任务书列的关键字里还有裸 `deviceType`，但 lib 既有 `deviceType` 是**平台蓝牙
  设备类型**（`BleDeviceType ← BluetoothDevice.getType()`、`YoCbtDeviceType ← BluetoothClass.Device`），属于
  `product-taxonomy.md` §2.2 明确允许 lib 处理的通用 BLE 概念，不是产品形态分类。按任务书原样封杀会逼着改
  `BleDeviceIdentity.deviceType()` 这类公开模型 getter（跨模块契约变更），收益为零。因此门禁判定的是产品分类学
  标识（`HsDeviceType` 等），不把平台蓝牙类型当业务字样；若要连裸 `deviceType` 一起禁，需要 lead 决定是否
  单独开一刀做 lib 模型改名。
- 反证验证：探针文件里 `int modelId;`、`String HS01;` 两行被判定失败，同一文件的注释行（含 `modelId`、
  `HsCapability` 字样）未被判定；探针已删除。

## 11. 验证结果（第二轮实跑）

1. `:lib_bluetooth:compileDebugJavaWithJavac` → **BUILD SUCCESSFUL**
2. `:lib_bluetooth:testDebugUnitTest` → **BUILD SUCCESSFUL**，38 suites / **190 tests / 0 failures / 0 skipped**
   - 新增 `YoBleScanSubscriptionTest`（7 个用例）：5 次谓词热替换后 `engine.starts` / `engine.stops` 均不变；
     谓词只影响后续帧；批量帧按订阅过滤；lost 事件同谓词；带谓词取消仍正常退订
   - 新增 `YoBleScanReconfigureTest`（7 个用例）：同计划幂等不重扫；计划变化只重启一次；无会话失败；
     窗口内第 6 次启动被拒（`SCAN_THROTTLED`、平台 `startCount` 仍为 5、会话未被打断）；窗口耗尽后重配被拒；
     健康度字段语义；能力来自平台查询
3. `:lib_bluetooth:check` → **BUILD SUCCESSFUL**（`checkLibBluetoothBoundary`、`checkLibBluetoothLogSeam`、
   `checkLibBluetoothScanMechanismOnly`、`checkNoStrayLogging` 全通过，含 lint）

**跨模块影响核对**：`git grep` 显示 biz 的 `HsNearbyScanner` / `HsNearbyScannerTest` 已按新契约（`BleScanSubscription`、
`setFrameFilter`、`reconfigureScan`）实现；`:biz_ggec_hs:compileDebugJavaWithJavac` 与
`:biz_ggec_hs:compileDebugUnitTestJavaWithJavac` 均 **BUILD SUCCESSFUL**，无需额外同步动作。

## 12. 第二轮未闭合

1. **真机未验收**：`scan-architecture.md` §7 的真机判据（切规则 5 次 logcat 无 STARTING/STOPPING、30s 内第 6 次
   无平台扫描、`health` 三态）本刀只做了单测级验证，未连真机跑。
2. **内部恢复路径仍会调用平台 startScan 且不受守卫拦截**（lead 已裁决保留）：长扫重启、平台降级重试、适配器恢复
   三条路径修复的是已死会话，不是用户改参数，因此"只有 start/reconfigure 会触发平台 startScan"按**外部 API 入口**
   理解。约束（lead）：① 三条路径都会 `recordScanStarted()` 计入窗口（已满足）；② 只允许在明确事件上触发
   （长扫到期、适配器重新开启），禁止紧循环重试；③ 平台限流命中时由上层提示"搜索受限"——lib 提供
   `scanning` + `lastFrameAtMillis` + `startsInLastWindow` 三个事实，`SILENT`/`THROTTLED` 属上层状态名，lib 不产文案。
   设计条款由 lead 写入 `scan-architecture.md`。
3. **若后续要 `reconfigure` 与在跑会话并存的真机用例**：单测已覆盖"计划变化只重启一次且会话不中断"，真机需验证
   `adb logcat` 中重配只有一组 `scan-state STOPPING/STARTING`（且不出现「扫描归 IDLE 后无人重启」），本条待 lead 指派。

