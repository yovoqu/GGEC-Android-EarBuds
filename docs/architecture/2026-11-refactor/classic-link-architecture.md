# 经典蓝牙链路架构（权威）

> 本文件是 `lib_bluetooth` 经典链路与上层消费边界的**唯一权威契约**。
> 与本文冲突的实现视为未完成。
> 历史快照文档（`lib_bluetooth/doc/**`）已删除，其内容要么被本文取代，要么属于已废弃设计。

## 0. 参照与结论来源

| 参照 | 取到的设计结论 |
|------|----------------|
| [Nordic Android-BLE-Library](https://github.com/NordicSemiconductor/Android-BLE-Library)（`BleManager` / `BleManagerHandler`） | 连接、重试、服务发现、初始化、队列、超时、错误分类**全部封装在库内**；上层只发请求、只收状态。连接状态机是库的职责，不是应用层的。 |
| [Nordic Scanner-Compat](https://github.com/NordicSemiconductor/Android-Scanner-Compat-Library) | 平台能力缺失时由库内模拟；上层不感知版本差异。 |
| 本仓 `docs/architecture/2026-11-refactor/design.md` | 依赖单向 `app → biz → lib_bluetooth → lib_common`；provider 只做适配不承载策略。 |

**核心结论**：经典链路的「就绪判定」是一个**有状态、依赖多条平台事实、需要超时收敛**的能力。
把它留给上层，等于让每个产品各写一遍同一个状态机——本仓已出现两份实现，且两份都有同一类缺陷。

## 1. 缺陷根因（已修复，记录以防回归）

### 1.1 链路就绪：把「未知」当「未连接」

现象：对已配对且 A2DP 已连接的耳机发起连接，必然 15 秒超时。

```
WyClassicRadio: 经典蓝牙未连接，先调用 lib_bluetooth 建立经典连接
WyClassicRadio: 设备已配对，发起 connectAudio 建立经典蓝牙音频/ACL
A2dpStateMachine: Connected: CONNECT ignored        ← 系统忽略重复 CONNECT
（此后无任何状态广播）
WyClassicRadio: 经典蓝牙连接超时
WySession: 控制流 SPP 状态变更: FAILED, failure=TIMEOUT
```

根因链：

1. `isClassicConnected()` 把 `getAclState()` 的 **`UNKNOWN`（合法值：未跟踪）** 当成「未连接」；
2. 于是对已连接设备重复调用 `connectAudio()`；
3. 系统因 A2DP 已连接而**忽略**该请求（`CONNECT ignored`），不产生任何广播；
4. 实现只 `observe*` 等待事件，事件永不到来 → 超时。

第二种形态：链路在订阅**之前**就已建立，事件早已错过。

### 1.2 链路就绪：只读缓存，不做系统查询

同步 getter（`getAclState` / `getConnectedProfiles` / `getDeviceProfileSnapshot`）只读
**快照仓库**，而仓库只由广播与查询填充。冷启动时仓库为空 → 永远 `UNKNOWN`。
必须用 `queryConnectedDevices` 真正调 `BluetoothProfile.getConnectedDevices()`
并把结果写回仓库（见 `YoCbtAndroidConnectedDeviceQuery#persistConnectedProfile`）。

### 1.3 音频帧：字节序按小端解析大端协议

现场字节级证据（`00 00 00 00` / `00 00 00 0A` / `00 00 00 14`…，即大端 0/10/20）：

| 现象 | 原因 |
|------|------|
| 每包只解出 1 帧，其余 `corrupted stream` | 按小端读帧头，除首帧（0 在两序下相同）外全是上亿伪值 |
| 日志刷「缺口=39 / 缺口=23」 | 伪值触发逐字节滑移，包边界全错 |
| 80 秒只落盘 11 帧 | 解码几乎全失败 |

**字节序是产品适配参数**，不是全局常量：HS 既有固件小端、已定稿；WY01 现场固件大端。

## 2. 分层职责（不可越界）

```
app
 └─ provider/biz_*            ← 只做 biz API ↔ app 模型适配
      │
      ▼
biz_ggec_*  （产品层）
 ├─ api/                     对外契约：纯类型 + 接口
 ├─ internal/                协议解析、会话状态机、产品命令表
 └─ provider/lib_bluetooth/  ← 只做「lib 能力 ↔ 本模块端口」适配
      │                       不判定链路就绪、不组合 ACL/Profile 查询
      ▼
lib_bluetooth （蓝牙栈）
 ├─ api/                     YoCbtClient / YoBleClient（唯一公开门面）
 ├─ model/                   不可变值对象（零 android.bluetooth.*）
 └─ internal/                平台适配、状态机、队列、超时
```

### 2.1 边界硬规则

| 规则 | 内容 |
|------|------|
| **B1** | 链路就绪判定**只能**发生在 `lib_bluetooth` 内（`YoCbtLinkSession`）。biz 不得组合 `getAclState / getConnectedProfiles / queryConnectedDevices / observeAclLinkState / observeProfileState` 自行判定。 |
| **B2** | biz 的 `provider/lib_bluetooth/**` 只允许：`openLinkSession`、`connectSpp`、`send`、`disconnectSpp`、发现、已配对列表、失败映射。 |
| **B3** | 就绪事实必须**三态**（`READY / DOWN / UNKNOWN`）。`UNKNOWN` 只能等待，**禁止**退化成 `DOWN` 去触发配对/重连。 |
| **B4** | 任何「等待」都必须有**显式超时**，不得留悬挂等待。 |
| **B5** | 失败映射单点：biz 内只允许一处把 `lib_bluetooth` 失败翻成产品失败枚举。 |

## 3. 链路会话契约

```java
// lib_bluetooth/api/YoCbtClient.java
CbtLinkSessionHandle openLinkSession(CbtDeviceId deviceId,
                                     CbtLinkSessionConfig config,
                                     CbtLinkSessionObserver observer);
```

### 3.1 就绪三态

```java
enum CbtLinkReadiness { READY, DOWN, UNKNOWN }
```

归并规则（`CbtLinkReadiness.of(...)`，单一实现）：

| 输入 | 结果 |
|------|------|
| ACL == CONNECTED，或 A2DP/HEADSET == CONNECTED，或已连接 Profile 列表非空 | `READY` |
| ACL == DISCONNECTED **且** Profile 列表为空 **且** 音频 Profile 明确断开 | `DOWN` |
| 其余（任一事实缺失/未知） | `UNKNOWN` |

### 3.2 会话行为（对齐 Nordic「查询 + 事件 + 超时」模型）

```
start()
  ├─ 订阅 ACL / Profile 事件            （覆盖后续变化）
  ├─ 启动显式超时定时器                 （B4）
  └─ 主动 queryConnectedDevices()       （覆盖「事件已错过」+ 冷启动空仓库）
        │
        ├─ READY  → 上报，清定时器，结束
        ├─ 系统查询确认设备不在已连接集合
        │         → 判定 DOWN，按配置 createBond / connectAudio
        └─ UNKNOWN→ 只周期复核，绝不配对/重连（B3）
```

关键不变量：

- **先查后等**：进入会话先做系统查询，命中即 READY，**不发起任何请求**；
- **查询失败 ≠ 未连接**：查询失败保持 `UNKNOWN`，交给复核与超时；
- **不重复请求**：`audioRequested` / `bondRequested` 保证同一次会话内每类请求最多发一次；
- **周期复核**：`queryRetryMillis` 到期主动重查，不依赖事件必达；
- **只在变化时回调**：`lastReported` 去重；
- **失败即终态**：超时/配对被拒时一次性关闭会话——`active=false` + 清定时器 + 取消全部订阅，
  只回调一次 `onLinkFailed`；之后任何 ACL/Profile 事件都不再推进（否则会二次失败或误报 READY）；
- **取消幂等**：`cancel()` 后不再有任何回调。

### 3.3 线程

| 环境 | 用途 |
|------|------|
| state 单线程 | 会话全部状态字段只在此改写 |
| scheduler | 超时与周期复核 |
| callback | 对观察者的回调 |

## 4. 音频帧装配与观测（lib_ggec_audio）

### 4.1 字节序是产品适配参数

```java
enum AudioWireByteOrder { LITTLE_ENDIAN, BIG_ENDIAN }
```

| 产品 | 字节序 | 来源 |
|------|--------|------|
| HS | `LITTLE_ENDIAN`（默认） | 既有固件，已定稿，行为不得改变 |
| WY01 | `BIG_ENDIAN` | 现场字节证据 |

由产品在构造装配层时声明：

```java
new StreamRecordPipeline<>(..., new LoggingAudioFrameTrace("WyAudioFrame",
        WyRecordLog.INSTANCE, AudioWireByteOrder.BIG_ENDIAN), executor,
        AudioWireByteOrder.BIG_ENDIAN);
```

**禁止**在共享层写死字节序：改成任一侧都会破坏另一侧。

### 4.2 观测点分级：lib 只报诊断，不打印数据

**职责分治**：`AudioFrameTrace` 是 lib 的唯一观测出口，`LoggingAudioFrameTrace` 是唯一实现，
但它**只报低频诊断事实、不逐包 dump 数据**；真正的原始数据流（SPP 控制/音频字节、PCM 解码字节）
由 **Biz 层在处理数据时打印**——lib 默认不打印数据。

lib 侧（诊断，低频）：

| 场景 | 级别 | 内容 |
|------|------|------|
| 格式锁定（一次性） | INFO | 完整几何 + 字节序 |
| 零帧（切帧疑似失败） | WARN | 长度 + 首序号 + 头部字节（故障定位） |
| 序号缺口 | WARN | 缺口折算 |
| 无法识别格式 | WARN | 长度 + 暂存 |

Biz 侧（真实原始数据，处理数据时打印，head 上限 48B + 总长）：

| 打印点 | 位置 | 行样式 |
|--------|------|--------|
| SPP 接收控制命令数据 | `WySession.onControlDataReceived` | `SPP接收控制命令数据: 99 EC D5 …` |
| SPP 接收音频数据 | `WySession.onAudioDataReceived` | `SPP接收音频数据: 00 00 00 00 … …(共440B)` |
| PCM 解码数据 | `WyStreamRecordSession` 包裹 listener | `PCM解码数据: B7 FF BD FF … …(共640B)` |

设计要点：

- **lib 不打印业务数据**：440B/包、每秒数十帧，lib 逐包 dump 会淹没日志；
- **数据打印在 Biz**：Biz 处理数据时用 `HexPreview.of(bytes, 48)` 打印真实原始字节（head 上限 + 总长）；
- **零帧必报**：这是切帧失败的唯一信号，lib 侧保留 WARN（含头部字节）；
- **散点诊断禁止回流 biz**：lib 的诊断只能经 `AudioFrameTrace`。

### 4.3 线格式判定

168 字节是唯一**有歧义**的包长：

| 布局 | 几何 | 相邻包首帧序号差 |
|------|------|------------------|
| `QUAD_40_168` | 整包 1 个 8B 头 + 4×40B Opus（16 kbps） | **4** |
| `DUAL_HALF_168` | 每帧 4B 头 + 80B Opus（32 kbps），2 帧 | **2** |

判定顺序：**包长几何唯一 → 首包锁定**；包长歧义 → 用「相邻两个 Notify 首帧序号之差」
定夺；包内嵌帧头（offset 84 / 164）作为次级证据。

两条不变式：

1. **序号按帧递增**。一包 10 帧时相邻包序号差 10，这不是丢包。缺口 = 推进量 − 每包帧数，只有 > 0 才上报。
2. **整包头布局下，包内帧序号是隐式的**。不能拿包内偏移去匹配序号头，否则会把一包 N 帧误读成一帧。

## 5. 产品层适配形态

> **迁移状态**：经典链路就绪已迁移到 lib 的 `openLinkSession`，`biz_ggec_wy` 与 `biz_ggec_hs`
> **均已迁移**——两者 provider 适配类都只消费 `openLinkSession`，biz 内不再有任何链路就绪状态机
> （HS 的 `HsClassicConnect/HsClassicPlan/HsClassicLink/HsMainDelay` 已删除）。

| | `biz_ggec_wy`（已迁移） | `biz_ggec_hs`（已迁移） |
|---|---|---|
| 端口 | `WyClassicRadioPort` | `HsClassicRadio` |
| 适配类 | `WyClassicRadio` | `YoClassicRadio` |
| 链路就绪 | 委托 `openLinkSession` | 委托 `openLinkSession` |
| 音频字节序 | `BIG_ENDIAN` | `LITTLE_ENDIAN`（默认） |
| 自身职责 | 两条 SPP 通道（控制 `7777…` / 音频 `8888…`）编排、字节收发、失败映射 | 三态→onReady/onFailed/onLost 单点投影 |
| B1 门禁 | 启用 | 启用 |

## 6. 门禁

`lib_bluetooth/build.gradle` 的 `checkLibBluetoothBoundary` 覆盖 R1–R5（依赖单点、实现不外泄、
契约不碰平台类型、SPI 单向、版本适配单点、无静态装配态、无线程阻塞、无兼容层命名、根包唯一）。

`biz_ggec_wy` 与 `biz_ggec_hs` 的 `checkBizGgec*Boundary` 均含 **B1 规则**：

```groovy
def linkJudgementApis = ['getAclState', 'getConnectedProfiles',
                         'queryConnectedDevices', 'observeAclLinkState', 'observeProfileState']
// 任何 biz 源码（含 provider/lib_bluetooth）出现 client.<api>( 即失败；注释行豁免。
```

该规则已用**注入探针**验证真实生效（干净状态通过、注入 `client.getAclState(id)` 立即 FAILED），
不是空跑。

## 7. 验证（真机 + 单测）

| 项 | 结果 |
|----|------|
| 全模块 `compileDebugJavaWithJavac` | 通过 |
| 全模块 `testDebugUnitTest` | **1708 用例，0 失败 / 0 错误 / 0 跳过** |
| `checkLibBluetoothBoundary` / `checkBizGgecWyBoundary` / `checkBizGgecHsBoundary` / `checkLibGgecAudioBoundary` | 通过（WY 含 B1 探针验证） |
| 真机链路就绪 | 修复前必超时 → 修复后 **43ms** 判定 `READY` |
| 真机三态正确性 | 耳机闲置关机时判 `UNKNOWN→DOWN`（不误判 READY），尝试连接后超时收敛；耳机上电后判 `READY → SPP CONNECTED` |
| 真机音频解码 | 修复前 80s/11 帧且全程 `corrupted stream` → 修复后 7.6s **360 帧**，`corrupted stream` **0 次** |
| 落盘完整性 | 360 × 640 = **230,400 字节**，与预期逐字节相等 |
| 观测日志 | lib 仅报诊断（格式锁定含字节序、缺口、零帧）；**真实数据由 Biz 打印**：`SPP接收控制命令数据` / `SPP接收音频数据` / `PCM解码数据` 三类，真机验证 PCM 为真实波形（如 `B7 FF BD FF…`） |
| 会话收敛（加固） | 首次就绪前按 `queryRetryMillis` 复核；**到达过 READY 后不再无限复核**（避免 READY→掉线时留下永不停止的定时任务），保持/重连交由上层策略 |
| 失败即终态（加固） | 超时/配对拒绝时 `active=false`+清定时器+取消订阅，只回调一次；失败后残余事件不再推进会话 |
| 本轮真机复验 | `READY` 后 `SPP CONNECTED`；录音 280 帧 = **179,200 字节**（280×640），`corrupted stream` 0 次 |
| 冗余清理 | 删除无引用的 `YoCbtAclLinkRepository#isAclConnected` 死代码；删除 3 份过时 `lib_bluetooth/doc/**` 历史快照；把散落在 5 个适配器里重复的「平台整型→内部枚举」`switch` 收敛为唯一映射器 `YoCbtAndroidStateMapper`（附纯单测） |

### 回归保护

- `YoCbtLinkSessionTest` 用「同步假运行时 + 手动定时器」确定性覆盖：先查后等（已连接不重连）、
  DOWN 才连接、UNKNOWN 不连接、查询失败保持 UNKNOWN、超时失败一次、**失败后残余事件不推进**、
  取消幂等——即把本会话的每个生命周期合同钉死，且不依赖线程/Looper；
- `CbtLinkReadinessTest` 逐一钉死三态归并的每个分支（尤其「查询失败不得退化成 DOWN」）；
- `AudioFrameAssemblerTest#littleEndianRemainsDefaultForHs` 守住 HS 小端基线；
- `AudioFrameAssemblerTest#locksQuad40_168FromFieldSequenceAndExtractsFourFramesPerNotify`
  与 `#reportsGapOnlyWhenWholeNotifyIsMissing` 固化序号/缺口不变式；
- `WyClassicRadioTest` 断言适配层调用 `openLinkSession`，且对
  `getAclState / createBond / observeAclLinkState / connectAudio / queryConnectedDevices`
  **抛 `UnsupportedOperationException`**——只要有人把链路判定写回 biz，测试立即失败。

## 8. 复审结论（objective 达成度）

| 目标 | 状态 | 证据 |
|------|------|------|
| (1) 深读文档 + 参考设计 | ✅ | §0 记录 Nordic Android-BLE-Library / Scanner-Compat 结论；删 3 份过时快照 |
| (2) lib 自底向上重构、稳定、不受上层影响 | ✅ | `YoCbtLinkSession` 单一实现 + `YoCbtLinkRuntime` 端口；**依赖仅 lib_common，零上层依赖（已核验）**；三处生命周期缺陷已修（超时终态 / READY 后不再复核 / 失败即终态） |
| (3) biz 严禁关注蓝牙细节 | ✅ 全量 | WY 与 HS 的 provider 适配类都只消费 `openLinkSession`（各含 B1 门禁 + 探针强制）；两者非 provider 层**零 `lib_bluetooth`/`android.bluetooth` 依赖** |
| (4) 清冗余、高内聚低耦合 | ✅ | 删死代码 `isAclConnected`、3 份过时文档；映射重复收敛为 `YoCbtAndroidStateMapper` 单一真源；删除 HS biz 链路状态机 4 文件；全库零孤立类 |
| (5) 全量测试 + 复审 | ✅ | **1708 用例 0 失败**；四道门禁全绿 |

**验证说明**：WY 已真机验证（`READY` + 录音落盘 360 帧/230,400B、`corrupted` 0 次、三类真实数据打印）；
HS 因现场无 HS 耳机（连接的是 WY01），经典链路无法真机验证，改由**编译 + 单元测试
（`YoClassicRadioTest`：用代理断言只消费 `openLinkSession`、不碰 `getAclState/queryConnectedDevices` 等，
并验证 READY→onReady / 失联→onLost / 失败映射）+ 与 WY 同构**保障；`HsSessionImpl` 端口契约未变。
两者 biz 非 provider 层均零蓝牙栈依赖，目标(3) 对全部产品达成。

