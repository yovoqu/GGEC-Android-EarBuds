# 扫描（Discovery）架构设计 — nRF Connect 式实时筛选

> 目标：筛选器是**视图谓词**，调整时只重投影，绝不触发底层停扫/重扫；扫描会话在页面之间保留，结果实时增删。
> 本文先给设计，代码在确认后按此改造。范围只含扫描，不含连接。

## 0. 实测事实（先纠正一条误判）

| 事实 | 证据 |
|------|------|
| 当前筛选**不会**触发停扫/重扫 | `ScanViewModel.selectFilter` 只改字段并 `buildStateLocked()`；`ScanUiMapper.map(results, filterModelId)` 是纯投影。真机日志里切换 `HS01→HS02→全部设备` 全程只有一条 `[ble-scan] scan started gen=1`。 |
| 真机上"切完筛选列表空/不回来"不是重扫导致 | 停 30 秒后同样操作，8 台设备全部正常出现；根因是 **Android 对频繁启停的 BLE 扫描静默限流**（扫描状态仍 RUNNING、零帧回调，无错误事件）。 |
| 列表会"看起来被清空" | `startScan()` 里 `devices.clear()`；任何一次重新进页/重启都会清表，用户感知为"筛选后没了"。 |

结论：**要改的不是"去掉重扫"（本来就没有），而是把扫描会话与筛选彻底解耦、把限流从静默变成可见、把筛选从单值升级为可组合谓词。**

## 1. 分层（自底向上）

```
L0 平台扫描        lib_bluetooth（YoBleScanHub + YoBleAndroidScanPlatform）
                   职责：进程内唯一扫描器、帧整流、平台限流保护、扫描健康信息
                   不做：产品识别、用户筛选、UI 状态

L1 产品识别        biz_ggec_hs（HsNearbyImpl + internal/broadcast）
                   职责：广播 TLV 识别 → 机型/变体，丢失记账（ledger），失败归一
                   不做：用户筛选、保留策略的界面语义

L2 扫描会话        app domain（新增 DeviceScanSession）
                   职责：持有**全量**设备表（不按筛选裁剪）、过滤谓词、派生视图、
                         扫描健康度（最近帧时间、启动频率）、生命周期（跨页面保留）
                   不做：平台参数、UI 细节

L3 页面状态        app ui（ScanViewModel）
                   职责：attach/detach 会话、把派生视图转 UI 状态、DiffUtil
                   不做：起停扫描（除用户显式动作）、筛选求值

L4 交互            app ui（ScanScreen）
                   职责：筛选器只调 session.setFilter；按钮只调 session.start/stop
```

## 2. 两条筛选线，语义必须分开

| 类别 | 例子 | 变更后果 |
|------|------|----------|
| **平台扫描参数**（`BleScanFilter`/`ScanSettings`） | 只收某 Service UUID、低占空比模式 | **唯一合法重扫触发源**。必须走显式 API `session.restartWithPlatformFilters(...)`，UI 禁止隐式调用 |
| **视图谓词**（`ScanFilter`） | 机型多选、名称包含、最小 RSSI、只看已知产品 | **永不触碰平台扫描**，只对 L2 的全量表重投影 |

当前实现只支持"单选机型"，且谓词写死在 `ScanUiMapper` 里——这是要升级的部分。

## 3. 数据流

```
平台帧 ──L0──► BleScanResult（原始）
        ──L1──► HsNearbyDevice(modelId, variantId, displayName, address, rssi) + onLost
        ──L2──► DeviceScanSession.devices（全量、按地址去重、RSSI 覆盖）
                     │
        setFilter(p) │（纯重算，零副作用）
                     ▼
             projected = devices.where(p)  ──L3──► ScanUiState ──L4──► DiffUtil 局部刷新
```

要点：
- **L2 的表是唯一真源**：筛选、排序、空态判定都从它派生；UI 不缓存第二份列表。
- **淘汰只由两件事驱动**：L1 的 `onLost` 与 TTL（默认 10s 无帧→视为消失）。**筛选不参与淘汰**。
- **排序**：默认 RSSI 降序（nRF 习惯），排序是投影的一部分，不写回表。

## 4. 生命周期

| 事件 | 行为 |
|------|------|
| 用户点「开始搜索」 | `session.start()`：清表（唯一清表点）→ 订阅 L0 |
| 用户点「停止搜索」 | `session.stop()`：取消订阅，**保留表**（便于回看与筛选） |
| 进入/离开扫描页 | 只 `attach/detach` 观察者；**不停扫、不清表** |
| 进程级 | `AppContainer` 持有唯一 `DeviceScanSession`；重复 attach 幂等 |
| 会话过期 | 切后台超过 N 分钟或显式「重新搜索」时清表 |

这解决两个现实问题：反复进页不再反复启停（限流主因），筛选切换后列表不会"消失"。

## 5. 限流与健康度（把静默变可见）

- L0 增加三个只读观测：`startedAt`、`startCountIn(lastWindow)`、`lastFrameAt`。
- L2 暴露 `session.health()`：`RUNNING / SILENT(>5s 无帧) / BLOCKED(应用侧节流)`。
- 应用侧节流：30s 内 `start()` 超过 5 次 → 直接返回 `SCAN_THROTTLED`，**不发平台请求**（避免进入平台静默黑洞）。
- UI 三态空态：
  1. 全量表为空 + 扫描中 + 未静默 → "正在搜索附近的耳机…（请确认耳机已开启并靠近）"
  2. 全量表为空 + 静默/被节流 → "搜索受限，请稍等片刻再试"（对应我们已改的提示）
  3. 全量表非空 + 过滤后为空 → "当前筛选下暂无设备（共 N 台，切换筛选查看）"

## 6. 与现状的差异清单（改造点）

| # | 现状 | 目标 |
|---|------|------|
| 1 | 扫描归 `ScanViewModel` 所有，页面销毁即停 | 新增 `DeviceScanSession`（domain），`AppContainer` 持有；VM 只 attach/detach |
| 2 | `startScan()` 无条件 `devices.clear()` | 清表只在 `session.start()`；attach/筛选/重进页都不清 |
| 3 | 筛选是 `selectedFilterModelId: String` 单值，求值在 `ScanUiMapper` | `ScanFilter`（机型集合 + 名称包含 + 最小 RSSI + 仅已知产品）在 L2 求值，`ScanUiMapper` 只做 UI 映射 |
| 4 | 筛选器单选 | 多选（nRF 式），选择即生效，无扫描副作用 |
| 5 | 空态只有两态 | 三态（见 §5） |
| 6 | 平台限流静默 | `health()` + 应用侧节流前置拦截 |
| 7 | 排序按发现顺序 | RSSI 降序（投影期排序） |

## 7. 验收（真机可判定）

1. 连续切换 `全部↔HS01↔HS02` 5 次，`adb logcat` 中 `[ble-scan] scan-state` **零** `STARTING/STOPPING`（无重扫）。
2. 切换筛选后列表在 100ms 内按新谓词更新，不依赖下一帧广播。
3. 退出扫描页再进入：不发新扫描请求，列表立即显示上次结果（会话未过期）。
4. 1 分钟内快速启停 6 次：第 6 次直接显示"搜索受限"提示，日志无平台扫描启动。
5. 设备被 `onLost` 后该行消失；筛选条件不变时其他行不动（DiffUtil 局部刷新）。
6. 单测：`ScanFilter` 谓词组合、`DeviceScanSession` 投影/淘汰/健康度、`ScanViewModel` attach 幂等。

## 8. 需要你确认的三个决策

1. **会话作用域**：`DeviceScanSession` 放 `AppContainer`（进程级，跨页面保留，推荐）还是 `ScanViewModel` 级别（离页即停）？
2. **筛选能力**：先做「机型多选 + 仅已知产品」两项（够用、可控），还是同时上「名称包含 + 最小 RSSI」（更像 nRF，但 UI 要加输入/滑杆）？
3. **淘汰 TTL**：默认 10s 无帧视为消失（nRF 约 3–5s，蓝牙耳机广播间隔较大，10s 更稳），是否接受？
