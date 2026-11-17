# lib_bluetooth 只读复审修复（2026-11-17，P1/P2 落地记录）

> 本文记录 `lib_bluetooth` 第二波只读复审提出的 P1-1…P1-5 与 P2 快修项的落地结果、
> 结构规则与需要上游配合的接线点。权威规则仍是 `design.md` §1.2 / §8，本文只描述本轮变化。

## 1. 依赖方向（P1-5）

目标态依赖图（单向）：

```
根门面 YoBluetoothSDK  ──(构造)──►  provider/lib_common/{log,permission}
        │                                   │
        │ 注入 BluetoothPermissionPort       │ 只依赖
        │      + spi.log.LogSink             ▼
        ▼                                   spi/  ◄── internal/** 也只依赖这里
   internal/BluetoothSdkCore ──► internal/{ble,classic}
```

- **SPI 移出实现层**：`spi/permission/BluetoothPermissionPort`、`spi/permission/YoBluetoothPermission`、
  `spi/log/LogSink`。原先位于 `internal/common/permission` 的端口与枚举已删除。
- **provider 单向**：`provider/**` 只允许 import `spi/**`、`model/**`/`api/**`、被依赖模块（`lib_common`）
  与平台标准库；**不得**再 import `com.yovo.lib_bluetooth.internal.**`。门禁 R2c 强制。
- **装配根上移**：`internal/BluetoothSdkCore` 不再 `new BluetoothPermissionProvider()`。
  根门面 `YoBluetoothSDK` 构造 `BluetoothPermissionProvider` 与 `YoBtLogger.SINK` 后注入
  `BluetoothSdkCore.classic/ble(context, config, permissionPort, logSink)`。
- **日志断言**：`YoBtLogger.requireInitialized()` 只断言宿主已初始化（`YoLogBootstrap.requireInitialized()`），
  库不代宿主初始化、不缓存状态；宿主未初始化即在 SDK 入口报错。

## 2. 静态装配态（P1-2）

- 删除 `internal/common/bluetooth/radio/YoBtRadioSharing.java`（进程级静态持有者 + 懒建平台）。
- 删除全部“便利构造器默认参数”（共 8 处）：`YoBleAndroidModuleGraph.create(3 参)`、
  `YoCbtAndroidModuleGraph.create(4 参)`、`YoBleAndroidScanPlatform(1 参)`、
  `YoBleAndroidConnectionPlatform(3 参)`、`YoCbtAndroidDiscoveryPlatform(2 参)`、
  `YoCbtDiscoveryController(6 参)`、`YoCbtSppConnectionManager(4 参)`、`YoCbtSppConnection(9/10 参)`。
- 唯一 `YoBtRadioCoordinator` 由客户端装配点构造并持有：`BleClientImpl` / `ClassicClientImpl`
  （`new YoBtRadioCoordinator(new YoBtAndroidRadioPlatform(new YoBluetoothAdapterProvider(appContext)))`），
  沿构造参数逐层注入两个装配图及其平台适配器；释放时由装配图 `releaseAll()` 调
  `radioCoordinator.releaseAllLeases()`。

## 3. 诊断总线（P1-3）

- `BluetoothDiagnosticsBus` 删除静态 `INSTANCE`/`get()`，改为图内实例：
  由 `BleClientImpl` / `ClassicClientImpl` 构造（注入 `LogSink`），经装配图交给
  `YoBleRuntime.diagnostics()` / `YoCbtRuntime.diagnostics()`，各组件只读这一个实例。
- `recordedTraceIds` 改为有界窗口（`DEFAULT_RECORDED_TRACE_LIMIT = 512`，插入序淘汰最旧），
  不再是无限增长集合。
- `clear()` 接到释放路径：BLE 图 `releaseAll()` 清空总线；Classic 图注册 `"diagnostics"` 释放钩子。

## 4. 错误分类（P1-1）

- `YoErrorCode` 改为 `YoErrorCode<C>`：`code()` 只作诊断标识，新增 `publicClass()` 携带对外契约枚举。
- `YoCbtErrorCode` 每个值在声明处绑定 `CbtErrorCode`（78 个值，无 default 分支）。
- 删除 `ClassicClientImpl` 的 63 分支字符串 `switch` 与 `mapErrorCode`；`YoCbtFailure` 携带
  `publicClass()`，投影层仅做一次类型收窄。

## 5. 门禁（P1-4）

`lib_bluetooth/build.gradle` 的 `checkLibBluetoothBoundary` 用目录事实重写：

| 规则 | 判定 |
| --- | --- |
| R1 依赖单点 | `lib_common` 只允许在 `provider/**` |
| R2 实现不外泄 | `api/**`、`model/**` 不得 import `provider.**` / `internal.**` |
| R2b 契约不碰平台类型 | `api/**`、`model/**` 不得 import `android.bluetooth.**` |
| R2c SPI 单向 | `provider/**` 只允许 spi / model·api / 被依赖模块 / 平台标准库 |
| R3 版本适配单点 | `Build.VERSION.SDK_INT` 只允许 `internal/platform/AndroidPlatform`；`Build.VERSION_CODES` 只允许出现在 `@RequiresApi` / `@ChecksSdkIntAtLeast` 注解 |
| R3 无静态装配态 | 禁 `static volatile`、禁静态集合字段 |
| R3 无线程阻塞/静默 | 禁 `Thread.sleep`、禁 `catch (… ignored)` |
| R4 无兼容层命名 | 类型名不得含 `compat/legacy/shim/bridge` |
| R5 根包唯一 | 根包 `.java` 数量必须为 1（不再按文件名白名单） |

门禁挂在 `preBuild`（`tasks.named('preBuild') { dependsOn 'checkLibBluetoothBoundary' }`），
另在 `check` 上汇总四项检查。

## 6. P2 快修

- `YoCbtAndroidLeAudioExtension.isSupported()` 删除 `NoSuchMethodError` 兜底，改由
  `AndroidPlatform.supportsLeAudioProfile()` 单点表达版本能力。
- 已配对设备类型白名单单点化：`YoCbtDeviceType.isBondedInventoryType()`；
  `YoCbtAndroidBondedDeviceLoader` 与 `YoCbtBondedDeviceCoordinator` 不再各自持有 `EnumSet`。
- `api/BleScanPlan.defaults()` 删除，默认计划回到 `internal/ble/scan/YoBleScanDefaults.plan()`。
- `YoBleScanHub` 静态实例改为图内实例：`YoBleModuleCore.scanHub = YoBleScanHub.forEngine(scanController)`，
  `subscribe(observer)`/`release()` 为实例方法，迁移线程池随 `release()` 关闭（`transitions.shutdownNow()`）。

## 7. 需要上游/其他模块配合的接线点

1. **同名客户端只能有一个射频协调器**：本轮的协调器归各客户端实例所有（BLE 与 Classic 各自一份）。
   若要求“经典发现在跑时挂起 BLE 扫描”的跨客户端互斥继续成立，需要在上层只创建一个客户端，
   或由 lead 决定引入进程级装配持有者（那会重新引入静态装配态，需在门禁里显式例外）。
2. **诊断总线同理**：`exportDiagnosticReport()` 只反映本客户端实例的总线；跨客户端汇总需要同一个持有者。
3. `lib_common.log` 改为 `YoLogBootstrap.requireInitialized()` 后，`lib_bluetooth` 已在
   `provider/lib_common/log/YoBtLogger.requireInitialized()` 与 `BluetoothSdkCore` 两个入口切换完成。
   仍需注意两处（不在本模块写入范围）：
   - `app/.../provider/lib_common/log/YoLogProvider.java:18` 的 javadoc 仍写「其它模块走
     `YoLogBootstrap#ensureInitialized` 兜底」，方法已删除，注释需同步；
   - `lib_bluetooth/src/androidTest/**` 的 `YoCbtClientInstrumentedTest`、
     `YoCbtSppMultiConnectionInstrumentedTest` 直接调用 `YoBluetoothSDK.classic(...)`：
     宿主（测试）必须先 `YoLogBootstrap.initOnce(context, config)`，否则 SDK 入口按新语义抛
     `IllegalStateException`（这是刻意的「未初始化即报错」）。
