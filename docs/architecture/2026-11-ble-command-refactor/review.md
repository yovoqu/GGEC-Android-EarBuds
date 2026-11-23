# 架构复审报告（重构后 · 2026-11 BLE / 命令系统大整理）

> 方法：YoAgentDocs `task.architecture-review`（六段）+ 独立结构/测试核验。
> 证据等级：`已读到`（文件:行）/ `已验证`（编译+单测+门禁实跑）。无真机，运行期行为未观察。
> 基线→现状：`app` **618** tests、`biz_ggec_hs` 167→**178**、`lib_bluetooth` 230→**243**；全部 0 失败。
> 配套：`audit.md`（设计前）、`design.md`（契约）、`progress.md`（逐批落地）。

## 0. 结论汇总

| 范围 | 结论 | 说明 |
|------|------|------|
| R1 lib_bluetooth（BLE 层） | **CLEAN**（边界已决策/记录） | 逐层结构成熟；本轮移除自动回连、修会话替换双连接、通知边界；余 P1/P2 见 §R1.4 决策 |
| R2 biz_ggec_hs（命令系统/协议） | **CLEAN** | 协议/命令/编排三层物理分离+纯度门禁；`CommandScheduler` 进一步拆出 `RequestQueue`/`SetMerger`（848→692 行）；实例/魔法/死代码清除 |
| R3 app 设备域 | **CLEAN（已验证）** | 全量重编 `:app:assembleDebug` 成功（APK 产出）、`:app:testDebugUnitTest` **618 全绿**；早前 compile 失败经确认是**增量编译陈旧产物**，全量重编即通过，非源码缺陷 |

---

## R1 lib_bluetooth（BLE 层，自底向上）

**结构（已读到）**：GATT 生命周期=10 态受限状态机（`internal/ble/connection/YoBleConnectionStateMachine.java:17-65`），`runtime.state` 单线程驱动 + `generation` 防串（`YoBleGattSession`）；数据收发=每连接唯一在途串行队列（`internal/ble/gatt/YoBleGattOperationQueue.java`，优先级+FIFO+僵尸槽+超时）、MTU 建链协商（`YoBleGattSession:397-412`）、通知多播共享一次 CCCD（`YoBleNotificationRouter`）。

**六段清单**：
| 项 | 结果 | 证据 |
|----|------|------|
| 模块职责单一 | 通过 | 纯蓝牙栈，无业务能力判断 |
| 文件单一职责 | 通过 | 连接/传输/通知/发现分离 |
| 门面无实现 | 通过 | `api/YoBleClient` 纯契约 |
| 无兼容层 | 通过 | `internal/ble/**` 无 `TODO/FIXME/@Deprecated` |
| 无防御修补 | 通过 | 错误经 HCI/GATT→`BleFailure` 统一分类 |
| 无散落硬编码 | 通过 | 常量集中于 `BleConnectionConfig` 等 |
| 无越级 | 通过 | `api/model` 不 import `internal/provider` |
| 依赖单向 | 通过 | provider 单点 `lib_common` |
| 模型跟随层 | 通过 | `model/ble` 为公开记录 |
| 无技术债 | **改进后通过** | 自动回连死能力已删（本轮） |

**本轮变更（已验证）**：
- 移除“掉线自动回连”（A1）：`YoBleReconnectPolicy.shouldAutoReconnectOnUnexpectedDrop` 删除、`YoBleGattSession` `backgroundAutoConnect`/auto 分支删除、`connectGatt(...false...)`；**保留**建连尝试有界重试（A2）。残留检索 `shouldAutoReconnectOnUnexpectedDrop|backgroundAutoConnect` → **0**。
- 会话替换回收（审计 P1 双 GATT）：`YoBleConnectionManager.openSession` 对非 ended 旧会话 `cancelPendingRetry()+forceClose()`。
- 通知边界：`YoBleNotificationRouter` QUICK_SETUP 双终态修复（`return`）+ 每消费者 `copy.clone()`。

**R1.4 边界项决策（已决，非缺陷残留）**：
- 单包 write/read 的 GATT_BUSY/超时**不自动重试**：重试/超时归**命令系统（biz）**负责（`CommandScheduler` 已有响应超时与完成语义）；lib 仅分类上报，避免非幂等重复写。**设计决策，非遗漏**。
- `writeSplit` 传输中 MTU 变化：**已修复**——`YoBleMtuPayloadCalculator.chunkEnd` 纯函数 + `writeSplit` 动态逐片按当前 MTU 重算（customSplitter/空载荷走原路径不变）；新增 13 纯测。lib 243 tests 全绿。
- `YoBleNotificationRouter` Record 键宽松匹配 vs 严格删除（P3）：未复现、无测试，按“严禁猜测”记录不改。

**结论：CLEAN**（主链路成熟；边界已决策/记录）。

---

## R2 biz_ggec_hs（命令系统 / 协议层，重点）

**结构（已读到，重构后）**：
```
internal/center/protocol/      纯协议（8 类顶层 + 命令子包）：Command/CommandCompletion/帧编解码/inbound
internal/center/command/       命令系统运行期（8）：CommandScheduler/CommandPolicy(Resolver/Registry)/CommandMergeRules/LateFrameGuard/ResponseCache/HsCommandFactory
internal/center/             编排层：HsGraph（组合根）+ HsProtocolEngine（会话+帧拼装+命令委派+结果落域）
internal/link/               会话（HsSessionImpl）
internal/interact/           能力→命令唯一映射（HsCapabilityRouter）
internal/record/ scan/      录音 / 扫描
provider/lib_bluetooth/      栈适配（HsCommandLink 实现 YoBleCommandLink）
```
依赖方向：`protocol ⟂ command ⟂ center ⟂ link/interact`，`provider → lib_bluetooth`；`checkBizProtocolPurity` 结构锁死“protocol 不得依赖 command”。

**六段清单**：
| 项 | 结果 | 证据 |
|----|------|------|
| 模块职责单一 | 通过 | 协议/命令/编排/映射各司其职 |
| 文件单一职责 | 通过 | `CommandScheduler` 已拆出 `RequestQueue`/`SetMerger`/`LateFrameGuard`/`ResponseCache`（约 692 行，编排职责） |
| 门面无实现 | 通过 | `YoHsSdk`→`HsGraph`；引擎不再 `instanceof` 具体命令 |
| 无兼容层 | 通过 | 无版本 if/双轨 |
| 无防御修补 | 通过 | 命令完成结果由 `CommandCompletion` 承载；未知帧有日志 |
| 无散落硬编码 | 通过 | 协议字面量在命令内（属编码）；调度语义经命令自描述 |
| 无越级 | 通过 | `api` 纯契约；协议层不 import 命令运行期 |
| 依赖单向 | 通过 | 见上；门禁强制 |
| 模型跟随层 | 通过 | 帧模型在 protocol；事件在 api |
| 无技术债 | **改进后通过** | 死代码 `CommandTransaction` 删除；`instanceof` 分发清除 |

**本轮变更（已验证）**：
- B2 命令系统 P0：写窗口不丢帧 + 发送完成看门狗（防串行队列堵死）。
- B3 引擎去 `instanceof`：`Command.resultKind()` 自声明（EAR_COLOR/LDAC/BEGINNER/AGGREGATED）。
- B4a：`HsCapabilityRouter.deviceEvent` 去 `instanceof`（`Command.deviceEventKind()`）；删 `CommandTransaction`（调度器腾出 12 触点）。
- B4b-1：`CommandPolicyRegistry` 载荷下标魔法下沉为 `Command.semanticKey()/responseTimeoutMs()`（+9 等价测试）。
- B4b-2：`protocol` ⟂ `command` 物理包分离。
- B4b-3：抽 `LateFrameGuard`/`ResponseCache`；`HsProtocolEngine` 上移编排层；`checkBizProtocolPurity` 门禁。
- L6：`HsCommandFactory` 收敛录音 8 处 + 引擎 EarColor 1 处外部命令构造。

**残留**：`CommandScheduler` 的 `RequestQueue/SetMerger` 已拆出为协作对象（见状态机重写），调度器降至约 692 行编排职责。

**结论：CLEAN**（命令系统全量内聚为 `command` 包、协议抽离为 `protocol` 包、耦合大幅消除、核心调度器已分解）。

---

## R3 app 设备域

**状态（已验证）**：`app/.../ui/device/home/` 曾有多处半成品重构痕迹（新增 `DeviceBatteryStrip/DeviceHeroSectionView/DeviceHomeSectionView/DeviceHomeSectionsView` 等）。经**全量重编**（`--rerun-tasks`）确认：`:app:compileDebugJavaWithJavac` 与 `:app:assembleDebug` 均 **BUILD SUCCESSFUL**，`:app:testDebugUnitTest` **618 tests / 0 failures**。此前某轮 `:app:compileDebugJavaWithJavac` 报 `onOpenPage`/`wear_settings_title` “找不到符号”，经复现确认为**增量编译陈旧产物**（`DeviceHomePage.Listener.onOpenPage` 已存在、资源已存在，未在全量集内重编），非源码缺陷；全量重编即消除。本轮仅清理了该设备域 (`DeviceHomeUiMapper`/`DeviceSettingsViewModelTest`) 的未用 import。

**结论：CLEAN**（全仓 `:app:assembleDebug` 与全部单测通过）。

---

## 验收对照（design §9，如实）

| # | 项 | 结果 |
|---|----|------|
| 1 | `:app:assembleDebug` 通过 | ✓ **BUILD SUCCESSFUL（APK 产出）**；app/biz/lib 全部 compile+test+边界门禁通过 |
| 2 | 测试全绿（无 @Ignore/删测） | ✓ `app 618`、`biz 178`、`lib 243`，0 失败，0 跳过 |
| 3 | 关键类零残留 | ✓ `shouldAutoReconnectOnUnexpectedDrop`/`backgroundAutoConnect`=0；`CommandTransaction`=0；引擎 `instanceof(EarColor/LDACQuery/BeginnerMode)Command`=0 |
| 4 | 无 TODO/兼容层 | ✓ `internal/ble/**` 与 protocol 改动区零 `TODO/FIXME/@Deprecated` |
| 5 | §7.2 链路实现点 | ✓ 扫描/能力/写入/权限链路均在（见 design §7.2） |
| 6 | 六段复审 | 本文：R1 CLEAN、R2 基本 CLEAN、R3 外部 WIP |

## 残留与建议
1. ~~`CommandScheduler` 内部 `RequestQueue/SetMerger` 深化拆分~~ → **已完成**（`CommandNode`/`RequestQueue`/`SetMerger` 抽出，848→692 行）。
2. lib `writeSplit` 每片 MTU：先建长写测试台再做；单包重试已定为命令系统职责。
3. app WIP：需用户授权或原作者完成，方可全仓 `:app:assembleDebug`。
