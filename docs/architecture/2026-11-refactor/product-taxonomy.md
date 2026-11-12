# 产品分类学与三层职责（设备类型 / 项目 / 产品型号）

> 本文是本仓**唯一权威定义**：术语、标识规则、代码类型、每层职责、迁移映射。
> 与本文冲突的现有命名一律按本文改正，不留兼容别名。

## 1. 术语与关系

| 术语 | 英文/代码名 | 定义 | 示例 | 标识规则 |
|------|-------------|------|------|----------|
| **设备类型** | `DeviceType` | 产品的**形态分类**，决定"这是什么硬件" | `EARBUDS`（耳机）、`SPEAKER`（音箱）、`HEADBAND`（头戴）… | 稳定枚举 id，全大写英文；显示名可本地化 |
| **项目** | `Project` | 产品线/项目，同一项目共享硬件与协议族 | `HS`、`WY` | 稳定 id（大写字母数字）；**不再从型号字符串里切分推导** |
| **产品型号** | `ProductModel` | 具体可售型号，是**识别与连接的最小单位** | `HS01`、`HS02`、`WY01` | 稳定 id，全局唯一，直接是 `HS01` 这种串 |
| **变体** | `Variant` | 同一型号下的市场/硬件差异（区域版等） | `v1`、`v2`、`CN` | 稳定 id，隶属于型号 |
| **能力** | `Capability` | 型号支持的功能原子 | `EQ_MODE`、`LDAC` | 稳定枚举 |
| **能力描述** | `CapabilityDescriptor` | 能力的呈现契约（分组/控件类型/选项/值域） | — | 由 biz 提供 |

**基数关系（已确认）**
```
DeviceType 1 ──── N Project        （同一设备类型下可以有多个项目；但本仓按 1:N 落在型号上）
Project    1 ──── N ProductModel   （HS → HS01, HS02）
ProductModel 1 ── N Variant        （HS01 → v1, v2, v3）
ProductModel 1 ── 1 DeviceType     （设备类型逐型号声明，**不从项目推导**——项目下可能有多形态）
ProductModel 1 ── N Capability
```

**红线**
1. **禁止用字符串拼接表达身份**：`"HS" + "01"` 是展示格式，不是标识来源。
2. **禁止把显示名当标识**：显示名可本地化/可改；`modelId` 不可改。
3. **禁止跳过型号直接按项目判能力**：能力逐型号声明。
4. 型号 id 全局唯一；变体 id 在型号内唯一。

## 2. 三层职责（lib / biz / app）

### 2.1 总表

| 概念 | lib_bluetooth | biz_ggec_hs | app |
|------|---------------|-------------|-----|
| 设备类型 / 项目 / 型号 | **完全不知道**（只有 BLE 地址、广播名、AD 字节、Service UUID） | **定义与唯一真源**：`HsDeviceType` / `HsProject` / `HsProductModel` + 目录 | **只读镜像**用于展示与筛选 UI，不定义取值 |
| 广播识别 | 不做 | 按目录特征识别出 `modelId`（可能为空=未登记） | 不做 |
| 设备表 / 去重 / TTL / RSSI 平均 | 不做 | `HsNearbyRegistry` | 不做 |
| 过滤规则 | 只提供**帧级谓词机制**（可热替换，不重启扫描） | `HsDeviceFilterRule`：按 deviceType/project/model 求值 | 只把用户选择翻译成规则，不实现求值 |
| 展示 | — | 提供 `HsNearbyView`（含分面计数） | 渲染列表、分面筛选器 |
| 连接目标 | — | `HsTarget(modelId, variantId, bleAddress)` | 只传行数据，不构造协议概念 |

### 2.2 lib_bluetooth：机制，无产品概念

```java
// 只有"帧"这一种输入；没有 model/project/deviceType 任何字样
record BleScanResult(int rssi, @Nullable String name, String address,
                     List<UUID> serviceUuids, byte[] rawAdvertisement, boolean connectable) { }

interface ScanFramePredicate { boolean matches(BleScanResult frame); }   // 纯函数、热替换、不重启
```

- 允许 lib 处理"通用 BLE 概念"：地址、地址类型、广播名、AD 结构、Service UUID、可连接标志、PHY、MTU。
- **禁止**出现 `modelId`、`project`、`deviceType`、`HS01` 等业务字样（门禁可校验关键字）。
- 实时过滤在这层只做"要不要把这帧交给上层"，**判断内容由 biz 注入**；默认谓词放行全部。

### 2.3 biz_ggec_hs：分类学真源 + 规则 + 注册表

```java
public enum HsDeviceType { EARBUDS, SPEAKER, HEADBAND }          // 稳定 id；显示名由描述符给

public record HsProject(String projectId, String displayName) { } // "HS" / "HS 系列"

public record HsProductModel(
        String modelId,          // "HS01"（稳定，全局唯一）
        String displayName,      // 可本地化
        String projectId,        // "HS"
        HsDeviceType deviceType, // 逐型号声明，不从项目推导
        List<HsDeviceVariant> variants,
        Set<HsCapability> capabilities) { }

public interface HsDeviceCatalog {
    List<HsProductModel> models();
    Optional<HsProductModel> model(String modelId);
    List<HsDeviceType> deviceTypes();     // 目录里出现过的形态（含显示名）
    List<HsProject> projects();
    HsCapabilityDescriptor descriptor(HsCapability capability);
}

/** 过滤规则：三维独立，空集=不限；维度内 OR，维度间 AND */
public record HsDeviceFilterRule(
        Set<HsDeviceType> deviceTypes,
        Set<String> projectIds,
        Set<String> modelIds,
        boolean onlyConnectable,
        int minRssi) { }

public record HsNearbyView(
        List<HsNearbyDevice> devices,                 // 已按 RSSI 降序
        Map<HsDeviceType, Integer> countsByType,       // 分面计数（对应全量表）
        Map<String, Integer> countsByProject,
        Map<String, Integer> countsByModel,
        int totalInRange,
        boolean scanning) { }
```

规则求值在 biz：`HsNearby.setFilterRule(rule)` → 只重投影 `HsNearbyView`，**不重启扫描、不清表**。

### 2.4 app：显示层，只有"选择"和"渲染"

```java
public enum DeviceTypeView { EARBUDS, SPEAKER, HEADBAND }    // 由 provider 从 biz 映射，app 不新增取值
public record ProjectView(String projectId, String displayName) { }
public record ProductModelView(String modelId, String displayName,
                               String projectId, DeviceTypeView deviceType) { }
public record ScanDisplaySelection(Set<DeviceTypeView> types,
                                   Set<String> projectIds,
                                   Set<String> modelIds) { }
```

- app 把 `ScanDisplaySelection` 经 provider 映射为 `HsDeviceFilterRule`（**唯一映射点** `provider/biz_ggec_hs/`）。
- 列表渲染只读 `HsNearbyView`（或 provider 投影的 `DeviceScanView`），**不含**任何 `if (modelId…)` 的过滤分支。
- 筛选 UI：设备类型 / 项目 / 型号三级（多选），选项来自目录（`deviceTypes()/projects()/models()`），不硬编码。

## 3. 扫描链路（按新定义重述）

```
lib 帧流 ──► biz 识别（目录特征 → modelId，未登记则 modelId="")
        ──► biz 注册表（按地址去重 / RSSI 平均 / TTL / lost）
        ──► biz 规则求值（deviceType/project/model/connectable/rssi）
        ──► HsNearbyView（列表 + 分面计数 + 总量）
        ──► app 渲染（DiffUtil）
```

**筛选器变化路径**：`app 选择 → provider 映射 → HsNearby.setFilterRule → 重投影 → 新 view`。全程零扫描调用。

## 4. 迁移映射（现状 → 目标）

| 现状 | 目标 | 说明 |
|------|------|------|
| `api/HsDeviceModel(modelId, displayName, variants, capabilities)` | `HsProductModel(modelId, displayName, projectId, deviceType, variants, capabilities)` | 重命名并补两字段，删旧名不留别名 |
| `HsCapability` 不变 | 不变 | 能力仍逐型号声明 |
| `HsNearbyDevice(modelId, variantId, displayName, bleAddress, rssi)` | 不变（`modelId` 语义明确为产品型号 id） | 上层用目录解析 project/type |
| app `DeviceProfileView(modelId, displayName, variants, capabilities)` | `ProductModelView` + `DeviceTypeView`/`ProjectView` | 增加分类维度 |
| app 筛选：单值 `selectedFilterModelId` | `ScanDisplaySelection` 三维 | — |
| 旧 app `DeviceModel(family,type)`（已删） | **不再恢复** | 身份不再靠拼接 |
| biz 内部 `HsGeneration`（HS01/HS02） | 保留，但只作为**协议代际**，与产品型号映射 | 代际 ≠ 型号；映射写在目录 |

## 5. 命名规范（全仓）

| 层 | 前缀 | 例 |
|----|------|----|
| lib | `Ble*` / `YoBle*` | `BleScanResult`、`ScanFramePredicate` |
| biz api | `Hs*` | `HsProductModel`、`HsDeviceFilterRule` |
| biz internal | `Hs*` + 子包职责名 | `internal/device/HsNearbyRegistry` |
| app domain | `Device*` | `DeviceTypeView`、`ScanDisplaySelection` |
| app provider | `Hs*Adapter` / `Hs*Mapper` | `HsCapabilityCatalogAdapter` |

禁止：`Model` 单独出现（必须 `ProductModel`）、`family/type` 表达身份、`kind` 与 `type` 混用（`DeviceType` 专指形态）。

## 6. 验收

1. 代码：`grep -i "family"` 零命中；`HsDeviceModel` 零命中；`modelId` 全部来自目录或扫描结果，无拼接。
2. 目录：`models()` 每项同时具备 `projectId` 与 `deviceType`；`deviceTypes()/projects()` 由目录聚合，不额外维护第二份表。
3. 门禁：lib 源码出现 `modelId|projectId|deviceType|HS0` 关键字即失败；app 源码出现过滤分支（`equals(modelId)`）即失败。
4. 真机：三级筛选切换零重扫（`scan-state` 无 STARTING/STOPPING），分面计数与列表同步更新。
5. 单测：规则组合（三维 AND、维内 OR、空集=不限）、目录聚合、映射穷尽。
