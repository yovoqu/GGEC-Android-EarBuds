# 多 Android 版本适配架构（能力模型 · 单一事实源）

> 目标：**完全适配多 Android 版本**，但以**架构方式**统一处理，禁止在各处散点 `Build.VERSION.SDK_INT` 打补丁。

## 1. 问题

此前版本差异（API 26/29/30/31/33/34/35）以 `if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.X)` 散落在 17+ 文件、40+ 处。表现为“到处打补丁”：
- 版本知识分散，新增/调整适配点易遗漏；
- lint 无法跨文件理解“已守卫”，易产生 NewApi 误报或漏报；
- 兼容策略无法集中审查。

## 2. 架构方案：能力模型（Capability Façade）

新增唯一事实源：`lib_bluetooth/internal/platform/AndroidPlatform.java`
- **唯一**知晓 API level 的地方（`level()` + 语义能力方法）。
- 每个能力方法标注 `@ChecksSdkIntAtLeast(api = ...)`：等价于显式“版本守卫”，使调用点可在能力为真时安全访问新 API，且 lint 认可。
- 调用方只问**能力**，不问版本号：如 `if (AndroidPlatform.supportsExtendedAdvertising()) { ...startAdvertisingSet... }`。

```java
@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.O)
public static boolean supportsExtendedAdvertising() { return level() >= Build.VERSION_CODES.O; }
```

### 能力 → API 级别对照表

| 能力方法 | 含义 | API |
|---|---|---|
| `supportsLePhy()` | LE PHY / ScanResult phy·legacy·sid·txPower | 26 (O) |
| `supportsGattPhyControl()` | GATT set/read PHY | 26 (O) |
| `supportsExtendedAdvertising()` | AdvertisingSet 扩展广播 | 26 (O) |
| `supportsScanSolicitationUuid()` | 扫描 Service Solicitation UUID | 29 (Q) |
| `supportsL2capCoc()` | L2CAP CoC Socket | 29 (Q) |
| `supportsDeviceAlias()` | BluetoothDevice#getAlias | 30 (R) |
| `supportsA2dpOptionalCodecApi()` | A2DP 可选编码 | 30 (R) |
| `supportsAdvertisingSolicitationUuid()` | 广播 Service Solicitation UUID | 31 (S) |
| `supportsModernDiscovery()` | BluetoothAdapter#startScan 现代发现 | 31 (S) |
| `usesTypedParcelableExtras()` | getParcelableExtra(key, Class) | 33 (T) |
| `usesTypedGattValueCallbacks()` | GATT 带值写/回调新签名 | 33 (T) |
| `usesTypedPeripheralNotify()` | notifyCharacteristicChanged 带值 | 33 (T) |
| `supportsLeAudioProfile()` | LE Audio | 33 (T) |
| `supportsDckConnectionPriority()` | GATT DCK 连接优先级 | 34 |
| `supportsDeviceAddressType()` | getAddressType | 35 |
| `supportsLocationEnabledApi()` | LocationManager#isLocationEnabled | 28 (P) |
| `supportsHearingAidProfile()` | Hearing Aid | 28 (P) |

## 3. 落地结果

已把 `lib_bluetooth` 内**所有运行时 `Build.VERSION.SDK_INT` 分支**收敛到 `AndroidPlatform`（迁移 17 个文件，含 BLE 扫描/GATT/广播/Peripheral/L2CAP/Bond/Receiver 与 Classic Intent/A2DP/HearingAid/LeAudio/Discovery）。

校验准则：`grep Build.VERSION.SDK_INT` 于 `lib_bluetooth/src/main/java`，除 `AndroidPlatform.java` 外**为 0**。

同时清理的兼容/补丁代码（前序）：
- 删除 `YoBleScanModeWindows`（软件扫描窗口仿真，会静默丢包）及控制器引用；
- 删除通用 `HsDeviceFilter`，产品准入由 `HsProductCatalog`（每款产品一行）驱动。

## 4. 使用契约（强制）

1. **业务/适配代码禁止**直接写 `Build.VERSION.SDK_INT`；只调用 `AndroidPlatform.xxx()` 能力。
2. 能力为真的分支内可调用对应 API（`@ChecksSdkIntAtLeast` 保证 lint 允许）。
3. 新增 Android 版本适配 = 只在 `AndroidPlatform` 增加/调整能力方法；不改散点。
4. `@RequiresApi` 仅用于“必须该版本才存在的方法”的入口声明，不替代能力判断。

## 5. 验证

- `:lib_bluetooth:check`（含 lint）→ **BUILD SUCCESSFUL，lint 0 errors**（42 个 NewApi 误报经 `@ChecksSdkIntAtLeast` 消除）。
- `:biz_ggec_hs:check`、`:app:testDebugUnitTest`、`:app:lintDebug`（0 errors）、`:app:assembleDebug` → BUILD SUCCESSFUL。
- 真机 NEPI4E0106：安装启动、扫描、退出 **无 FATAL**。

## 6. 说明

- `*Compat` 类（Scan/Gatt/Advertise/L2cap/CbtIntent）保留，但已是“薄适配器”：内部只按 `AndroidPlatform` 能力选择新旧 API，不再各自持有散点版本号。
- 本轮编辑期间检测到外部写入通道对部分文件产生字符级损坏，已用 `git checkout` 恢复原始内容并用编辑工具重放所有改动；最终以上述构建/测试为准（全部通过）。
