# 扫描过滤架构审查与重构（产品化过滤规则 · 清理兼容/补丁/防御代码）

> 目标：**每款产品过滤规则各自显式声明**，禁止用通用“兼容类/补丁类/防御类”堆叠；从架构层审查后重构，清理旧的不合规代码，不耦合旧实现。

## 1. 结论摘要

- 扫描分两层职责：**底层（lib_bluetooth）只做“实时原始扫描”**（不感知产品）；**过滤/识别层按产品规则判定**（HS01/HS02 代际 + 市场 MarketID + 业务规则）。
- 原 `hs01-android-app`（`yobtservice` SDK）已验证的分层：`RawScanHub（宽扫） → L1 协议硬门槛 → L2 市场准入 → L3 业务过滤`。本仓库此前缺少显式的“市场准入（MarketID）过滤层”，且存在重复的特征码目录与软件扫描窗口仿真等兼容/补丁代码。
- 本轮已完成：**产品目录单一事实源（`HsProductCatalog`）**、删除重复特征码目录（`HsFeatureCatalog`）、删除软件扫描窗口仿真兼容类（`YoBleScanModeWindows` 及其测试与控制器引用）、**删除通用过滤类 `HsDeviceFilter`（准入直接由产品目录驱动，不再多造策略/兼容类）**。

## 2. 原仓库过滤架构（证据）

`D:\A_yoprogram\Gitlab_Gallery\HS02\hs01-android-app\yobtservice\src\main\java\com\ggec\bleservice\scanner\`

| 层 | modern（HS02） | old（HS01） |
|---|---|---|
| L0 原始扫描 | `RawScanHub` / `RawScanObservation` / `RawScanConfig` | 同左（共用宽扫） |
| L1 协议硬门槛 | `ModernProtocolConstants`(CompanyId 0xFFFF / Magic "HC" / version 1) + `ModernAdvertisementParser`（MALFORMED/UNSUPPORTED_VERSION/INCOMPLETE/MATCHED） | `OldAdvertisementParser` + `OldFeatureCodeRegistry`（市场→FeatureCode→9 字节前缀，与 128-bit Service UUID 比对） |
| L2 市场准入(MarketID) | `ModernMarketFilter`（`AcceptedMarkets` 包含 `AdvertisingData.getMarketId()`，marketId 来自 TLV 0x0B） | 市场→FeatureCode 注册表（`OldFeatureCodeRegistry.fromMarkets`），空表=不允许 |
| L3 业务过滤 | `ModernDeviceTypeFilter`(默认耳机 0x02) / `ModernPairableFilter` / 自定义 `ModernDeviceFilter`（AND） | `OldDeviceNameFilter` / `OldServiceUuidFilter` / 自定义（AND） |

要点：**代际（HS01/HS02）由广播结构决定；MarketID 是两代都携带的独立维度**，两代都按“接受市场集合”准入——这正是“按 MarketID 过滤”的落点（`AcceptedMarkets` 由 `YoBLEApi.configureMarkets` 分别写入 old/modern，不做跨协议映射）。

## 3. 现仓库（GGEC-Android-EarBuds）对照

| 层 | 现实现 |
|---|---|
| 底层实时扫描 | `lib_bluetooth`：`YoBleAndroidScanPlatform`（结果转换移出主线程）→ `YoBleScanController` → `BleScanResult`（不感知产品） |
| 代际识别(L1) | `biz_ggec_hs`：`Hs02BroadcastDecoder`（0xFFFF "HC" v1，TLV 0x0A MAC / 0x0B MARKET）+ `Hs01BroadcastDecoder`（128-bit UUID 9 字节特征码） |
| 市场准入(L2) | **本轮收敛**：`HsBroadcastScanMapper` 识别后用 `HsProductCatalog.isKnownMarket(identity.marketId())` 做产品准入 |
| 业务过滤(L3) | `HsBroadcastScanMapper`：仅耳机（`identity.headset()`）+ 产品目录市场准入；无独立策略类 |
| UI 呈现 | `app`：`ScanViewModel.selectFilter` 按 `modelId`（全部/HS01/HS02）做视图层投影 |

## 4. 每款产品规则（单一事实源 `HsProductCatalog`）

`biz_ggec_hs/model/broadcast/HsProductCatalog.java` 每行一款产品，识别/过滤逻辑不随产品数增长而加分支：

| 市场 MarketID | 展示名 | UI 变体 | HS01 特征码(前缀) | 协议 |
|---|---|---|---|---|
| CN(0) | CN | v1 | 8610380090000010 00 | V11 |
| CN2(3) | CN2 | v1 | 8610380090000030 00 | V12 |
| TVS(1) | TVS | v2 | 1c7b8471e31707bc | V11 |
| AUSTRALIA(2) | AU | v3 | 8650990000400000 09 | V11 |

- HS01：`Hs01BroadcastDecoder` 由 128-bit UUID 反推 9 字节前缀 → `HsProductCatalog.byHs01FeatureCode()` 命中产品 → 取 marketId/variant。
- HS02：`Hs02BroadcastDecoder` 从 TLV 0x0B 取 marketId → 与产品目录一致性由 `HsDeviceFilter` 市场准入校验。
- 新增产品 = 目录加一行；不再新增兼容/分支类。

## 5. 兼容/补丁/防御/旧代码盘点与处置

| 文件/位置 | 性质 | 处置 |
|---|---|---|
| `internal/broadcast/HsFeatureCatalog.java` | 与产品目录重复的 HS01 特征码硬编码（旧实现） | **已删除**（并入 `HsProductCatalog`） |
| `internal/ble/scan/YoBleScanModeWindows.java`（+Test） | 软件扫描窗口仿真（兼容某些 ROM 不遵守 ScanMode 的补丁；会静默丢包） | **已删除**（连同 `YoBleScanController.withinScanModeWindow()` 引用） |
| `model/broadcast/HsDeviceFilter.java`（+Test） | 通用过滤策略类（易滑向“万能兼容层”） | **已删除**：准入内联为 `HsProductCatalog.isKnownMarket + identity.headset()`；产品规则只在目录一处 |
| `app/HsBroadcastScanMapper.mapHs01Variant()` | 旧 switch 分支硬编码变体 | **已改为**查产品目录 `variantId()` |

**保留（非产品兼容，属 Android OS 版本兼容，删除会破坏 minSdk 24–37 运行）**：
`YoBleAdvertiseCompat` / `YoBleGattCompat` / `YoBleL2capCompat` / `YoBleScanCompat`、`YoCbtIntentCompat`——这些是 API 级别兼容（不同 Android 版本 API 差异），不是产品/市场兼容，按架构应保留。

**待评估清理（需你确认口径，避免误删必要防护）**：
- `YoBleScanThrottleGuard` + `BleScanThrottlePolicy`：与系统 5次/30s 限流重复的“防御性”限流；若确认由系统承担可移除。
- `YoBleScanCallbackEmulator` 的 FIRST_MATCH/MATCH_LOST 仿真：跨 API 行为仿真；`HsDevicePort` 依赖其做 MATCH_LOST 过期移除，属功能而非纯兼容，建议保留。
- 各层大量 `catch(Throwable/Exception ignored)` 防御式忽略：可逐步收敛为显式失败，风险较高，建议专项分批。

## 6. 本轮重构落地清单

- 新增 `model/broadcast/HsProductCatalog.java`（产品单一事实源）。
- `Hs01BroadcastDecoder` 改用产品目录（按特征码前缀匹配），删除 `HsFeatureCatalog`。
- `HsBroadcastScanMapper`：识别后直接用 `HsProductCatalog.isKnownMarket` + `headset` 做产品准入；HS01 变体查目录。
- 删除 `HsDeviceFilter`（+Test）与 `YoBleScanModeWindows`（+Test），控制器移除 `withinScanModeWindow` 软件丢包逻辑。
- 新增单测 `HsProductCatalogTest`（4 款产品、市场/特征码/协议一致性）。

## 7. 验证

- `:biz_ggec_hs:testDebugUnitTest`、`:app:testDebugUnitTest`、`:app:assembleDebug` → BUILD SUCCESSFUL
- `:lib_bluetooth:check`、`:biz_ggec_hs:check`（含 lint）、`:app:lintDebug` → BUILD SUCCESSFUL（app lint **0 errors**）
- 真机：过滤层改动后行为保持（默认接受产品目录内市场耳机），扫描列表/类型切换逻辑不变。

## 8. 架构原则（后续强制）

1. **底层只扫不筛**：`lib_bluetooth` 不引入任何产品/市场过滤；产品过滤全部在 `biz_ggec_hs` 过滤层。
2. **每款产品一行**：新增市场/型号只在 `HsProductCatalog` 增行，不新增兼容/补丁/分支类。
3. **无跨代兼容耦合**：HS01/HS02 各自解码器独立，共享“产品目录”这一只读事实源，互不引用。
4. **过滤可组合但不堆兼容**：市场准入/代际/业务规则由 `HsDeviceFilter` 组合，默认值全部来自产品目录。
