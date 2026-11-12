# 扫描架构设计（lib 机制 / biz 规则 / app 显示）

> 参照：[nRF Connect](https://raw.githubusercontent.com/nordicsemi/Android-nRF-Connect/refs/heads/main/documentation/README.md)、[Nordic ScannerCompat](https://raw.githubusercontent.com/NordicSemiconductor/Android-Scanner-Compat-Library/master/README.md)、Nordic Android-BLE-Library / Blessed / Gadgetbridge 调研（`YoAgentDocs/research/by-stack/client-runtime/`）。
> 分层原则（本轮已确认）：**lib 提供扫描机制与实时过滤能力；biz 定义并实现设备过滤规则；app 只表达"显示哪些"。** 三者互不越界。

## 0. 平台事实与参照系统行为

**平台（决定机制，不是偏好）**
1. `BluetoothLeScanner.startScan(filters, settings, cb)` 没有"更新过滤器"API —— 改**平台参数**必须 stop+start。
2. 硬件卸载过滤不一定支持；不支持时标准做法是"无过滤扫描 + 客户端按帧过滤"（ScannerCompat 官方做法）。
3. 30 秒内启动扫描超过 5 次会被系统限流，**表现为扫描正常但收不到回调**（本仓真机已复现）。
4. 无过滤的后台扫描在熄屏后会被系统停掉；后台扫会被强制降为低功耗。

**nRF Connect**
- Scanner 列出范围内全部设备；Filter（名称/MAC/广播数据/RSSI）只收窄列表，**扫描不停**。
- 行内可连接、可看包历史与平均 RSSI；Bonded 页来自系统缓存而非扫描。

**因此机制与规则必须分开**：实时过滤是**每帧求值**的能力（机制，属 lib），过滤条件的内容（哪些产品、哪类设备）是**产品知识**（规则，属 biz）。

## 1. 分层总览（依赖单向：app → biz → lib）

```
app（显示层）
  └─ ScanDisplaySelection：选择要显示的"平台产品 / 设备类型"；渲染快照
        │ setDisplaySelection(...)                 ▲ HsNearbyView 快照
        ▼                                          │
biz_ggec_hs（产品与规则层）
  ├─ HsDeviceFilterRule   过滤规则（产品目录匹配、设备类型归类、显示条件）
  ├─ HsNearbyRegistry     全量设备表（按地址去重、RSSI 平均、TTL、lost）
  └─ HsNearby             扫描编排 + 识别 + 规则求值 + 视图投影 + 健康度
        │ start(profile)/stop()                    ▲ 原始帧 / 健康
        ▼                                          │
lib_bluetooth（扫描机制层）
  ├─ ScanSession       进程唯一扫描、平台参数（模式/批量/PHY）、显式重扫
  ├─ FramePipeline     帧整流 → 帧级谓词（ScanFramePredicate）→ 多订阅者分发
  ├─ ThrottleGuard     启动频率守卫（超限直接 SCAN_THROTTLED，不发平台请求）
  └─ ScanHealth         startedAt / startCount / lastFrameAt / adapterState
```

## 2. lib_bluetooth：机制层（分层设计）

### 2.1 内部分层

| 子层 | 组件 | 职责 | 明确不做 |
|------|------|------|----------|
| L0-a 平台适配 | `PlatformLeScanner`（API21+）/ `PlatformScannerCompat` | 把 `startScan/stopScan` 与会话周期绑定；能力探测；平台错误码 | 不缓存帧、不认识产品 |
| L0-b 会话 | `ScanSession`（进程唯一，现 `YoBleScanHub` 演进） | 首个订阅者启动、最后取消停止；`ScanPlan`（模式/批量/PHY/legacy/平台硬过滤）变更=**显式重启** | 不解释帧内容 |
| L0-c 帧管道 | `FrameNormalizer` → `ScanFramePredicate` → `FrameFanout` | 原始回调归一为 `BleScanResult`；**每帧**按谓词求值；只把命中的帧分发给订阅者 | 不做去重/表/TTL/排序（那是 biz） |
| L0-d 限流守卫 | `ThrottleGuard` | 30s 窗口计数；超阈值直接失败，不发起平台扫描 | 不猜平台状态 |
| L0-e 观测 | `ScanCapabilities` / `ScanHealth` | 卸载过滤/批量/扩展广播支持；`startedAt/startCount/lastFrameAt/adapterState` | 不产出业务告警文案 |

### 2.2 两条"变更"路径，语义写死

| 变更 | API | 后果 | 允许调用方 |
|------|-----|------|-----------|
| **平台扫描参数**（模式/批量/PHY/硬过滤） | `session.reconfigure(ScanPlan)` | 停扫 → 新参数启动（唯一合法重扫） | 仅 biz（且只由用户显式"扫描档位"动作触发） |
| **帧级谓词**（要不要把这帧交给上层） | `subscription.setFramePredicate(p)` | 只影响后续帧的过滤，**不重启扫描、不清空** | biz |

`setFramePredicate` 是 lib 对 nRF"无硬件过滤时客户端过滤"的正式化：谓词是可替换对象，求值在帧管道内完成，与扫描生命周期正交。

### 2.3 契约（设计签名）

```java
interface YoBleScanSession {                       // 进程唯一
    Result<Void> start(ScanPlan plan, BleScanObserver observer);
    Result<Void> reconfigure(ScanPlan plan);        // 显式重扫，唯一合法路径
    void stop();
    boolean isScanning();
    ScanCapabilities capabilities();
    ScanHealth health();
}

interface BleScanSubscription extends AutoCloseable { // 每个订阅者一份
    void setFramePredicate(@Nullable ScanFramePredicate predicate); // 实时过滤，不重启
}
interface ScanFramePredicate {                       // 纯函数：机型未知时只看原始字段
    boolean matches(BleScanResult frame);
}

record ScanPlan(ScanMode mode, long reportDelayMs, boolean legacy,
                List<PlatformScanFilter> hardwareFilters) { }

record BleScanResult(int rssi, @Nullable String name, String address,
                     List<UUID> serviceUuids, byte[] rawAdvertisement, boolean connectable) { }
```

### 2.4 不变量（供门禁校验）
- 只有 `start/reconfigure` 会触发平台 `startScan`；`setFramePredicate`、`stop`、订阅取消都不得启动平台扫描。
- 帧管道内不持有设备表、不做 TTL、不做排序。
- 限流守卫拒绝时不得调用平台 API。

## 3. biz_ggec_hs：规则层（识别 + 规则 + 视图）

### 3.1 职责
| 组件 | 职责 | 不做 |
|------|------|------|
| `HsDeviceCatalog` | 产品目录：产品 id、显示名、能力集、识别特征（service UUID / 厂商数据 / feature code） | 不做扫描、不做 UI |
| `HsDeviceFilterRule` | **过滤规则**（本轮核心）：按产品识别结果归类；"显示条件"（产品集合、设备类型集合、是否仅显示可连接、最小 RSSI 等）以**数据**表达，不写死分支 | 不直接实现扫描 |
| `HsNearbyRegistry` | 全量设备表：按地址去重、RSSI 平均、`lastSeenAt`、TTL、`onLost` 合并 | 不做平台参数 |
| `HsNearby` | 编排：`start/stop`、把规则翻译成 lib 的帧级谓词（可选）、识别→入库→按规则投影→发快照；透传健康度 | 不做 UI 文案 |

### 3.2 规则求值在 biz，且是"实时"的
- 规则变化（例如"只看 HS01"）→ `HsNearby.setDisplayRule(rule)`：**只重投影**视图快照，既不停扫也不清表；必要时同步更新 lib 的 `framePredicate`（纯优化，谓词永远不能成为唯一真源——未命中谓词的帧不进入识别，所以**默认谓词必须放行全部**，仅当规则可用原始字段表达时才下推）。
- 真源永远是 `HsNearbyRegistry` 的全量表 + 规则；谓词只是省流量的前置过滤。

### 3.3 契约（增量，分类学定义见 `product-taxonomy.md`）
```java
interface HsNearby {
    void start(HsNearbyObserver observer);
    void start(HsNearbyObserver observer, HsScanProfile profile);   // 显式档位（低功耗/批量）
    void stop();
    boolean isScanning();

    /** 三维过滤规则（设备类型 / 项目 / 型号）；空集=不限，维内 OR、维间 AND。只重投影。 */
    void setFilterRule(HsDeviceFilterRule rule);
    HsNearbyView view();                           // 规则投影后的快照（含分面计数与总量）
    HsNearbyHealth health();
}

record HsDeviceFilterRule(Set<HsDeviceType> deviceTypes,
                          Set<String> projectIds,
                          Set<String> modelIds,
                          boolean onlyConnectable,
                          int minRssi) { }

record HsNearbyView(List<HsNearbyDevice> devices,
                    Map<HsDeviceType, Integer> countsByType,
                    Map<String, Integer> countsByProject,
                    Map<String, Integer> countsByModel,
                    int totalInRange, boolean scanning) { }
```

## 4. app：显示层

- **只做两件事**：把用户选择翻译成 `HsDeviceFilterRule`（设备类型/项目/型号三级），把 `HsNearbyView` 渲染成列表（含分面计数）。
- 不持有设备表、不做去重/TTL/排序、不实现任何过滤分支（没有 `if (modelId.equals(...))`）。
- 会话生命周期（已确认 A）：`ScanSessionController` 由 `AppContainer` 持有；`start()` 是唯一清表点；`stop()` 与退页**不停表**；退页停扫但保表 N 分钟，重进页立即有数据、不重启扫描。
- 空态三态：搜索中 / 搜索受限（`health` 为 SILENT 或 THROTTLED）/ 规则下暂无设备（`totalInRange > 0`）。

## 5. 状态机

```
lib 扫描：IDLE ─start()─► STARTING ─► SCANNING ─stop()/失败─► IDLE
                       └─ ThrottleGuard 拒绝 ─► THROTTLED（不调用平台）
规则变更：任意状态 ─setDisplayRule()─► 仅重投影（扫描状态不变）
健康：RUNNING（5s 内有帧）/ SILENT（扫描中 >5s 无帧）/ THROTTLED
```

## 6. 改造清单（按层）

| 层 | 现状 | 目标 |
|----|------|------|
| lib | 进程唯一扫描 + 基本策略；无订阅级谓词、无能力/健康、无限流守卫 | 增 `ScanSession/ScanFramePredicate/ScanCapabilities/ScanHealth/ThrottleGuard`；`reconfigure` 为唯一重扫入口 |
| biz | 已做识别 + lost 记账；无"过滤规则"概念；快照未带全量规模 | 增 `HsDeviceFilterRule`/`HsDisplayRule`/`HsNearbyRegistry`/`view()/health()`；规则变化零重扫 |
| app | 扫描归 VM、`startScan()` 清表、筛选在 UI mapper 求值 | 删表与筛选逻辑；`ScanSessionController` + 显示选择；空态三态 |

## 7. 验收（真机 + 单测）

1. 连续切换显示规则 5 次：`adb logcat` 无 `scan-state STARTING/STOPPING`；视图 <100ms 更新。
2. 退页再进页：无新扫描请求，列表立即有数据；退页 N 分钟后表过期。
3. 30s 内第 6 次 `start`：无平台扫描启动，`health=THROTTLED`，UI 显示"搜索受限"。
4. 单测：`ScanFramePredicate` 在管道内生效且不触发重启；`HsDeviceFilterRule` 组合；`HsNearbyRegistry` 去重/TTL/RSSI 平均；`HsNearby.view()` 是纯投影。

## 8. 待确认（一处措辞）

你说的"显示哪个**平台设备**和**设备类型**"里，"设备类型"我按两种可能理解，需要你确认口径：

- **口径 A**：`设备类型` = 产品型号（HS01 / HS02），`平台设备` = 具体某台耳机（地址）—— 那显示规则就是"产品集合 + 具体设备"。
- **口径 B**：`设备类型` = 形态分类（TWS 耳机 / 头戴 / 音箱…），由 biz 目录给出；`平台设备` = 产品型号。

这决定 `HsDisplayRule` 的字段与 biz 目录要不要加"形态分类"维度，确认后我再按最终口径细化并开工。
