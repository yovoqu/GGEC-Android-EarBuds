# Biz / APP 功能梳理与 HS02 设置失败根因审计

> 版本：2026-11（复审后修订）· 范围：`biz_ggec_hs`、`biz_ggec_wy`、`biz_ggec_record`、`app`（设备域 + 设置页）
> 方法：逐层读码（文件:行）+ 运行期日志核对（HS02/WY01）+ motorola edge 60 pro 真机实测（§8）。
> 证据等级：`已读码` / `日志实测` / `真机实测`。**凡本文前部与实现或 §8/§9 冲突处，一律以 §8/§9（复审后最终口径）为准。**
> ⚠️ **重要勘误**：早期草稿曾把「A = 改 `WITH_RESPONSE`」写作已修；真机证明其错误并已回退。**最终代码为 `WRITE_TYPE = WITHOUT_RESPONSE`，真正的修复在 `CommandScheduler`（匹配响应帧即视为“写已送达”，不再死等不触发的 GATT 写完成）。** 本文 §3.4 / §4 / §5 / §6.6 中「WITH_RESPONSE 已修」表述作废，见 §8、§9。
> 本轮落地：BLE 收发日志、命令终态原因日志、随录随传多源结算、命令三态（发送/处理中/成功/失败）、开关成功后保持目标值。详见 §6/§9。

---

## 0. 结论摘要（TL;DR）

1. **通道分层是对的**：命令走命令通道（GATT `820100/820104/820105`），音频流走音频通道（GATT `820000/820003`）；WY 走控制 SPP（`7777…`）与音频 SPP（`8888…`）。命令/数据分道设计成立。
2. **HS02「随录随传」启动/停止必失败**（日志实证）：`StartStreamRecordCommand` 下发后**收不到 99ECD5 回执**，约 **10.03s** 后 `FAILED`，业务层打印「录音启动失败」。但**音频实际在流**（428 帧 PCM 已落盘，格式锁定 `DUAL_HALF_168 / LITTLE_ENDIAN`）。即：**设备侧动作成功、APP 侧命令判定失败**——是「命令回执/写完成」链路问题，不是录音管线问题。
3. **关键盲区**：现有日志**没有命令通道的 BLE 接收埋点**，也**不打印命令终态原因**（只有「状态变更为 FAILED」）。因此无法仅凭旧日志区分是「GATT 写超时」还是「设备不在命令通道回 99ECD5」。**本轮已补齐这两处日志**（§6），下一轮同类失败可直接定论。
4. **权威对照（WY01）**：同样命令 `99ECD5/99ECD6`，WY01 在**控制通道**收到 `99 EC D5/D6 00 01 00 12 34` 回执，状态字节 `0x00` → 成功。HS02 未在命令通道看到对应回执，却在**音频通道**收到 `0x8012` 控制上报包。两种产品回执路径不同，HS 命令模型未覆盖。
5. **APP 开关状态**：`DeviceSettingsUiState.WriteState{IDLE,IN_FLIGHT,CONFIRMED,FAILED}` 已有清晰抽象（发送/处理中合并为 IN_FLIGHT，成功 CONFIRMED，失败 FAILED），且 IN_FLIGHT 时 `enabled=false` 已实现「禁用点击」。**但存在一处与要求相反的实现**：IN_FLIGHT 期间 `position` 取「设备上报值优先」，导致**点击后开关不先翻转**，违背「点击时先切换状态」。**本轮已修正为 IN_FLIGHT 期间优先呈现用户目标值**（§6.3）。（注：本段的 `IN_FLIGHT` 是改造前基线的旧状态名；最终枚举为 `IDLE/SENDING/PROCESSING/CONFIRMED/FAILED`，成功/处理中均保持目标值，见 §6.3/§9。）

---

## 1. Biz 侧功能梳理

### 1.1 biz_ggec_hs（HS 命令系统 / 协议 / 录音）

结构（重构后，见 `2026-11-ble-command-refactor/review.md`）：

```
internal/center/protocol/   纯协议：Command/CommandSpec(CommandCompletion)/帧编解码/inbound + 各命令子包
internal/center/command/    命令运行期：CommandScheduler(RequestQueue/SetMerger/ResponseCache/LateFrameGuard)/CommandPolicy*
internal/center/            编排：HsProtocolEngine（会话+拼帧+委派+落域）、HsGraph（组合根）
internal/link/              会话：HsSessionImpl（能力→命令、快照、事件）
internal/interact/          HsCapabilityRouter（能力↔命令唯一映射/构造点）
internal/record/            HsStreamRecordSession（随录随传）、HsDeviceRecordSyncSession（文件同步）、HsAudioPacketClassifier
provider/lib_bluetooth/     YoBleCommandLink / YoBleAudioDataLink（lib 栈适配）、HsBleSettings
```

要点：
- **命令是一发一收（REQUEST_REPLY）**：`CommandScheduler` 只调度 `HsTraffic.REQUEST_REPLY`；主动上报走 `InboundFrameDemux` 旁路。命令超时默认 `Command.getTimeoutMs()=2000ms`（`Command.java:196`）。
- **写完成看门狗 15s**：`WRITE_COMPLETION_WATCHDOG_MS=15_000`（`CommandScheduler.java:28`），注释明确「须大于链路自身单次 GATT 操作超时（lib 默认 10s）」——即 **lib 单次 GATT 操作超时约 10s**。
- **两条 BLE 通道**（`HsBleSettings.java`）：
  - 命令：SERVICE `…820100` / WRITE `…820104` / NOTIFY `…820105`，`WRITE_TYPE = WITHOUT_RESPONSE`。
  - 音频：SERVICE `…820000` / NOTIFY `…820003` / WRITE `…820004`。
- **接收点**：命令通道 `YoBleCommandLink.NotifyWatcher.onCharacteristicChanged → HsProtocolEngine.LinkListener.onInbound`；音频通道 `YoBleAudioDataLink.onCharacteristicChanged → HsStreamRecordSession.onAudioChunk`。
- **音频控制包分类**：`HsAudioPacketClassifier`（820003 上 ≤20B 且 `value[1]==0x80` 视为控制上报；`0x8004=START / 0x8005=STOP / 0x8012=DELAY`）。

### 1.2 biz_ggec_wy（WY，参考实现）

- 双 SPP：控制 `7777…`、音频 `8888…`。命令只在控制通道，流只在音频通道（`stream-record-refactor.md` §4 时序）。
- **日志范式（本轮的参考基准）**：`WySession` 的 `onControlDataReceived` 打印 `SPP接收控制命令数据: <hex>`；`onAudioDataReceived` 打印 `SPP接收音频数据: <hex>`（`WySession.java:138-152`）。原始字节在 **Biz 处理点** 打印，lib 不打印。
- `WyStreamControlFrames` 收敛 8B 控制帧（START/STOP/状态位）；`WyStreamRecordSession.onAudioChunk` 精确 8B 分流，其余字节原样进管线。

### 1.3 biz_ggec_record

- 负责 PCM/ASR/视频解码与封装（`AudioStreamDecoder`/`PcmNormalizer`/`AvRecorder`），与设备协议无直接耦合，本轮不涉及。

---

## 2. APP 侧功能梳理

### 2.1 设备域

```
domain/device/port/DeviceLinkPort   链路端口：connect/write/perform/observe...
domain/device/usecase/WriteDeviceControl  → port.write(capabilityId, value, callback: DeviceWriteResult)
domain/device/usecase/RunDeviceAction     → port.perform(action, callback)
domain/device/model/DeviceWriteResult      { Ok | Failed(DeviceFailure) }   // 单次终态回调
```

- **写入端口是一次终态回调**：`HsDeviceLinkAdapter.write → sdk.write(cap, value, result → callback)`（`HsDeviceLinkAdapter.java:200-205`），`HsControl.Callback.onResult(Outcome<Void>)` 只在终态触发（`HsControl.java:96-98`）。因此 APP 层**观测不到**「写完成/SENT」与「收到回执」两个中间时刻，只能看到「发起」和「终态」。

### 2.2 设置页开关状态机（重点）

- `DeviceSettingsViewModel.write()`：先落 `WriteSlot(IN_FLIGHT, pendingValue)` → `publish()`（视图立即刷新）→ `writeControl.execute(...)`（`DeviceSettingsViewModel.java:112-121`）。
- 回调 `onWriteResult`：`Ok → CONFIRMED(保留 pending)`；`Failed → FAILED(丢弃 pending) + 事件`（`:299-324`）。
- `TogglePresentation.of(...)`（`TogglePresentation.java`）：
  - `enabled = interactive && writeState != IN_FLIGHT`（IN_FLIGHT 禁用，已实现）；
  - `position` 旧实现 `= reported.or(pending).orElse(false)`（**设备上报值优先**）。
- `SettingsRowsView.addToggle`：`enabled()` 为 false 时渲染只读 `YoSwitch(disabled, position)`（`SettingsRowsView.java:139-161`）。

**发现（P1，已修）**：旧 `position` 在 IN_FLIGHT 期间仍「上报值优先」。当某能力**有上报值**（如关闭态 `false`）时，用户点开 → pending=true 但 `position` 仍显示 `false` → **开关不先翻转**，与「点击时先切换状态」相反；用户只有在设备回读后才看到变化，体验上像是「点了没反应」。已在 §6.3 修正。

---

## 3. HS02 功能项设置失败根因（基于日志）

### 3.1 现场（`earbuds-07f0e.log`，HS02，2026-11-18 17:04）

| 时间 | 事件 | 证据 |
|---|---|---|
| 17:04:18.041 | 音频 Notify 就绪，发 StartStreamRecordCommand | `HsStreamRecordSession` |
| 17:04:18.046/052 | `StartStreamRecordCommand_1` QUEUED → SENDING | `Command` |
| 17:04:18.048/051 | 生成命令 `99ECD50001001234` | `StartStreamRecordCommand` |
| **17:04:18.136** | **音频通道收到控制上报包 `len=8 cmdType=0x8012`** | `HsStreamRecordSession` |
| 17:04:18.242 | 锁定音频格式 `DUAL_HALF_168 / PER_FRAME_HEADER / 80B×2 / 168B / LITTLE_ENDIAN` | `HsRecord` |
| 17:04:28.080 | **`StartStreamRecordCommand_1` → FAILED**（距 SENDING **约 10.03s**） | `Command` |
| 17:04:28.085 | `录音启动失败` | `HsRecord` |
| 17:04:28.087 | 停止随录随传，**总 PCM 帧数=428（≈8560ms）已落盘** | `HsRecord` |

后续 `_3/_4` 启动、`Stop_2` 停止同样 `FAILED`。整段无任何命令通道接收日志（因无埋点）。

### 3.2 与 WY01 对照（`earbuds-006eb.log`）

| 环节 | WY01（成功） | HS02（失败） |
|---|---|---|
| 发开始命令 | 控制通道发 `99ECD5` | 命令通道(`820104`)发 `99ECD5` |
| 收到回执 | **控制通道** `SPP接收控制命令数据: 99 EC D5 00 01 00 12 34`（status=0x00） | **命令通道无记录**；音频通道收到 `0x8012` 控制包 |
| 结果 | 开始成功 | ~10s 后 FAILED，但音频已在流 |

### 3.3 根因判定（分层，含不确定度）

**能确定的事实**：
- 失败点不在音频管线：`DUAL_HALF_168` 已锁定、428 帧 PCM 已解码落盘。
- 失败点在**命令往返**：`StartStreamRecordCommand` 等不到匹配的 99ECD5 完成帧 → 终态 `FAILED`。
- 失败延迟 **≈10.03s**，与 `CommandScheduler` 注释所述「lib 单次 GATT 操作超时约 10s」量级一致（`CommandScheduler.java:22-28`）。

**候选根因（旧日志不足以二选一，需新日志确认）**：
- **A（链路层）**：命令特征 `820104` 使用 `WRITE_TYPE = WITHOUT_RESPONSE`（`HsBleSettings.java:33`）。无响应写缺少应用层确认，若某固件/栈上 `onCharacteristicWrite` 未回调，lib 的 GATT 操作在 ~10s 超时 → `onTimeout → failCurrent(TIMEOUT,"GATT 写入超时")`。设备侧可能已收到（音频 0x8012 在 84ms 后出现即为旁证），但 APP 侧写操作未「完成」。
- **B（协议层）**：HS02 固件对「随录随传 开始/停止」**不在命令通道回 `99ECD5/D6` 文本帧**，而以**音频通道的 8B 控制上报**（`0x8004 开始 / 0x8005 停止 / 0x8012 延时`）表达。当前 `StartStreamRecordCommand.isResponseMatch` 只认 `99ECD5` 前缀，`HsStreamRecordSession.onAudioChunk` 只对 `0x8005 → stopByDevice` 处理，**既不把 `0x8004/0x8012` 当作「开始受理」完成，也没有把命令系统与音频通道控制包关联** → 命令必然超时。

**为什么必须补日志**：旧日志既没有命令通道 RX 字节，也没有终态 `code/message`。本轮已在 `Command.complete` 打印 `code+message`（可区分 `TIMEOUT/"GATT 写入超时"` vs `TIMEOUT/"等待设备响应超时"` vs `FAILED/...`），并在 `HsProtocolEngine.onInbound` 打印命令通道原始字节。**下一次复现即可判定 A/B。**

### 3.4 修复落地（本轮已实施：A、B 双修，无残留）

1. **A（链路层）——【已作废，见 §8/§9】**：曾尝试把命令特征写类型改为 `WITH_RESPONSE`，真机证明固件不回 ATT 应答、反而等满 10s 超时，**已回退为 `WITHOUT_RESPONSE`**。真正的修复不在写类型，而在 `CommandScheduler`：把匹配响应帧视为“写已送达”并立即处理。详见 §8、§9。
2. **B（协议层）已修**：HS 随录随传开始/停止改为「设备侧信号优先」的**多源结算**（`HsStreamStartGate`）：命令回执 SUCCESS / 音频控制上报 `0x8004`(开始) / 首段非控制音频切片 / 停止的 `0x8005` 或命令终态，谁先到算谁，且**只成功一次**；`0x8012` 仅记为延时信息（不算成败）；成功后迟到的命令失败一律忽略，并主动 `replayCompletion(SUCCESS)` 释放串行队列（避免 Stop 被占满 10s）。→ **同时兼容 HS02（无命令 ack）与既有固件（有 99ECD5 ack）**。文件同步会话 `HsDeviceRecordSyncSession` 同样以「已出流」判定设备已接受，避免误中止。
3. **通用**：命令终态原因已日志（§6.2），FAILED 可区分 TIMEOUT/业务失败；UI 侧 `DeviceFailure` 已含 `TIMEOUT/REJECTED/DEVICE_ERROR` 分类。

> 本轮不再是「只建议、待定论」——A、B 均已按上述实现落地，并通过编译 + 全量单测（§7）。

---

## 4. 发现清单

| 级别 | 模块 | 事实 | 状态 |
|---|---|---|---|
| P0 | biz_ggec_hs | HS02 随录随传 开始/停止命令 ~10s 后 FAILED（设备实际已在流） | ✅ 已修（多源结算 §3.4-B + 带响应写 §3.4-A） |
| P1 | biz_ggec_hs | 命令通道 BLE 接收无埋点，失败无法定位 | ✅ 已加 `BLE接收命令数据`（§6.1） |
| P1 | biz_ggec_hs | 命令终态不打印 `code/message` | ✅ 已加 `完成: code=.. message=..`（§6.2） |
| P1 | app | 开关写入中 `position` 上报值优先 → 点击不先翻转 | ✅ 已修 乐观呈现（§6.3） |
| P1 | biz_ggec_hs | `820104` 命令特征写类型 | ✅ 最终 `WITHOUT_RESPONSE`；真修复在 `CommandScheduler`（§8/§9） |
| P2 | biz_ggec_hs | 音频通道控制包只处理 `0x8005`，忽略 `0x8004/0x8012` | ✅ 已关联（`0x8004` 开始成功、`0x8012` 延时、`0x8005` 停止，§6.5） |
| P2 | app | 写入端口单回调，APP 无法区分「发送」与「处理中」 | ✅ 已实现 `SENDING/PROCESSING` 三态贯通（§6.7） |
| P3 | app | 滑块 `LevelPresentation` 同样上报值优先 | ✅ 已同步（§6.8） |

---

## 5. 通道/协议对照矩阵

| 项 | WY01 | HS02 | 结论 |
|---|---|---|---|
| 命令通道 | 控制 SPP `7777…` | GATT `820100/820104/820105` | 分道成立 |
| 音频通道 | 音频 SPP `8888…` | GATT `820000/820003` | 分道成立 |
| 开始/停止命令 | `99ECD5/99ECD6 …1234` | 同（`99ECD50001001234`） | 命令字节一致 |
| 命令回执路径 | 控制通道回 `99EC D5/D6 …`（status=0x00） | **命令通道未见**；音频通道 `0x8012` | **差异=本次问题** |
| 音频格式 | `DECA_40_440 / BIG_ENDIAN / 40B×10 / 440B` | `DUAL_HALF_168 / LITTLE_ENDIAN / 80B×2 / 168B` | 各自锁定正确 |
| 写类型 | SPP 字节流 | `WITHOUT_RESPONSE`（最终） | 响应帧驱动完成（§9） |

---

## 6. 本轮落地改动（已读码）

### 6.1 HS 包 BLE 接收数据日志（参考 WY01，区分命令/音频）

- `biz_ggec_hs/.../protocol/HsHex.java`：新增 `preview(byte[], int)`，与 WY `HexPreview` 对齐（空格分隔大写十六进制，超限截断标注总字节）。
- `biz_ggec_hs/.../center/HsProtocolEngine.java`：`LinkListener.onInbound` 首行打印
  `HsLog.d("HsProtocolEngine", "BLE接收命令数据: " + HsHex.preview(value, 48))`。
- `biz_ggec_hs/.../record/HsStreamRecordSession.java`：`onAudioChunk` 入口按分类打印
  - 音频流：`BLE接收音频数据: <hex>`；
  - 控制包：`BLE接收音频通道控制包: len=.. cmdType=0x.. data=<hex>`。
- 效果：命令通道与音频通道分开可查；音频通道内再区分「控制上报包」与「音频流字节」，与 WY 的 `SPP接收控制命令数据 / SPP接收音频数据` 一一对应。

### 6.2 命令终态原因日志

- `biz_ggec_hs/.../protocol/Command.java`：`complete()` 增加
  `HsLog.d(TAG, commandId + " 完成: code=" + result.getCode() + " message=" + result.getMessage())`。
- 效果：FAILED 时可区分 `TIMEOUT/"GATT 写入超时"`、`TIMEOUT/"等待设备响应超时"`、`FAILED/业务原因`。

### 6.3 APP 开关「点击即先切换」（SENDING/PROCESSING 乐观呈现）

- `app/.../ui/device/TogglePresentation.java`：写入中（`writeState.inFlight()` = SENDING/PROCESSING）时
  `position = pending`（先呈现用户目标值）+ `enabled=false`（禁用重复点击）；CONFIRMED 回到上报值优先；
  FAILED 丢弃 pending 回退到上报值。
- `app/.../device/settings/DeviceSettingsUiState.java`：`WriteState` 明确为
  `IDLE/SENDING/PROCESSING/CONFIRMED/FAILED` 并新增 `inFlight()`。

### 6.5 Biz HS 随录随传「多源结算」健壮化

- `HsStreamRecordSession`：开始成功 = 命令回执 SUCCESS ∪ 控制上报 `0x8004` ∪ 首段非控制音频切片；
  停止 best-effort（`0x8005` 或命令终态）；`0x8012` 仅记延时；成功后主动 `replayCompletion(SUCCESS)` 释放队列。
- `HsAudioPacketClassifier`：补 `isStartSignal/isStopSignal/isDelayNotice/describeControlCmdType` 纯函数。
- `HsDeviceRecordSyncSession`：已出流即视为设备接受，避免误中止。
- 新增纯逻辑 `HsStreamStartGate`（事件→NONE/SUCCESS，单次结算、忽略迟到失败）与单测。

### 6.6 Biz HS 命令通道写类型

- `HsBleSettings.WRITE_TYPE`：**保持 `WITHOUT_RESPONSE`**（§6.6 原写「改 WITH_RESPONSE」已作废，真机否定）；真修复在 `CommandScheduler`（§8/§9）。

### 6.7 APP 命令状态三态贯通（发送 / 处理中 / 成功 / 失败）

- Biz 侧产生「处理中」信号：`Command.StateListener`（状态迁移回调）→ `HsSessionImpl` 在 `SENT` 时回调
  `HsControl.Callback.onStarted()`；`WyControl.Callback.onStarted()` 由 `WySession.write` 即时触发。
- 领域端口：`DeviceLinkPort.writeWithProgress(cap, value, onStarted, callback)`（默认退化；HS/WY 适配器与 `DeviceGateway` 均覆写转发）；`WriteDeviceControl.execute(cap, value, onStarted, callback)`。
- APP：`DeviceSettingsViewModel.write` 落 `SENDING` → `onStarted` 转 `PROCESSING` → 结果 `CONFIRMED/FAILED`；
  迟到/旧写入的 onStarted 由「状态仍为 SENDING 且值相等」守卫，不会覆盖终态或新写入。

### 6.8 滑块一致性

- `LevelPresentation`：同样在 SENDING/PROCESSING 时禁用并乐观呈现目标值，与开关一致。

### 6.4 验证

- 见 §7 结果（命令已实跑通过）。


---

## 7. 验证结果（全绿）

命令（离线，已通过）：

```
.\gradlew.bat :biz_ggec_hs:testDebugUnitTest :biz_ggec_wy:testDebugUnitTest :app:testDebugUnitTest \
  :biz_ggec_hs:checkBizGgecHsBoundary :biz_ggec_hs:checkBizProtocolPurity --offline --console=plain
.\gradlew.bat :app:assembleDebug --offline --console=plain
```

- `biz_ggec_hs:testDebugUnitTest`：**190 tests / 0 failures / 0 errors / 0 skipped**（真机修正后新增 `matchingResponseCompletesEvenWhenWriteNeverReportsCompletion`）。
- `biz_ggec_wy:testDebugUnitTest`：**34 tests / 0 failures**。
- `app:testDebugUnitTest`：**627 tests / 0 failures**（Toggle/Level 三态 + onStarted + 网关 `writeWithProgress` 转发 + 在途去重用例）。
- `:app:assembleDebug`：**BUILD SUCCESSFUL**（全量打包通过）。
- 结构门禁 `checkBizGgecHsBoundary`、`checkBizProtocolPurity`、`checkLibBluetoothBoundary`、app 侧 `check*` 全部通过（期间修正两处：命令 TX 日志从 `provider/lib_bluetooth`（禁止依赖协议）上移到 `HsProtocolEngine`；`DeviceGateway` 补 `writeWithProgress` 转发，否则生产链路 `PROCESSING` 态不可达——已补 2 条网关单测）。
- 变更点 `grep TODO/FIXME/XXX/暂不` → 0。

> 说明：真机行为无法在本环境复现；A（带响应写）需真机确认 `820104` 具备 WRITE 属性，B（多源结算）已按「设备侧信号优先」实现且对既有 ack 固件保持兼容。下一轮带上 `BLE接收命令数据` / `BLE发送命令数据` / `完成: code=.. message=..` 在 HS02 复跑即可闭环验证。

---

## 8. 真机实测（motorola edge 60 pro + HS02 Vidda V12）与最终根因修正

> 设备：`NEPI4E0106`（motorola edge 60 pro，Android 15 / SDK35 / arm64）；HS02 = `74:D0:82:7E:12:A7`（Vidda V12 耳夹式，Product ID 1）。
> 方式：ADB 安装 debug APK → 连接 HS02 → 操作「随录随传 / 佩戴检测」→ `adb logcat` 取证。

### 8.1 真机结论（决定性）

1. **随录随传已修复并可正常录音落盘**：启动 0.2s 内 `COMPLETED`，停止后文件保存（例：`HS02_stream_20261123_111213.pcm`，00:01:51 / 3.4 MB / 5558 帧）。
2. **但发现更深层真因**：所有 REQUEST_REPLY 命令（含普通功能设置，如佩戴检测）此前均 `SENDING → (≈10s) → FAILED code=1 "GATT 写入超时"`。真机日志证明：**设备收到命令后约 84~93ms 就在 Notify 通道回了业务响应帧**（如 `99 EC 87 00 01 01 12 34`），但 APP 侧 **`CommandScheduler` 在 `node.writeCompleted==false` 时把匹配响应帧缓冲起来等待 `onWriteCompleted`，而命令特征的 GATT 写完成回调在该固件上始终不触发**（lib 操作等满 10s 报 `GATT_OPERATION_TIMEOUT`）→ 缓冲帧永远不处理 → 命令 10s 超时。**这才是「功能项设置都会失败 / 随录随传失败」的真正根因。**
3. **写类型并非真因**：`WITH_RESPONSE` 与 `WITHOUT_RESPONSE` 实测表现一致（都 10s 超时），因固件不回 ATT Write Response。故 `HsBleSettings.WRITE_TYPE` **回退为 `WITHOUT_RESPONSE`**（带响应写在 HS02 上反而等满 ATT 应答）。
4. **最终修复**（`CommandScheduler.onResponseCandidate`）：匹配响应帧本身即证明写已送达 → 视为「写完成」的等价信号（取消发送看门狗、置 `SENT`、使能响应超时），立即处理该帧及已缓冲帧，不再死等永不触发的 GATT 写完成回调。

### 8.2 真机修复前后对照（`adb logcat` 实测）

| 场景 | 修复前 | 修复后 |
|---|---|---|
| 佩戴检测开关 | `SENDING → 10s → FAILED "GATT 写入超时"` | `SENDING → BLE发送99EC87 → BLE接收99EC87(93ms) → SENT → RECEIVING → COMPLETED 完成: code=0 佩戴检测开启成功`（≈114ms） |
| 随录随传·启动 | 10s 后失败 | `BLE接收 99ECD5 ack → COMPLETED 开始本地录音随录随传成功`（≈100ms） |
| 随录随传·停止 | 10s 后失败 | `BLE接收 99ECD6 ack → COMPLETED 停止本地录音随录随传成功`（≈48ms），文件保存 111s/5558 帧 |

### 8.3 复核与本轮代码变更（含真机修正）

- `CommandScheduler.onResponseCandidate`：`!writeCompleted` 时以匹配响应帧驱动完成（新增单测 `matchingResponseCompletesEvenWhenWriteNeverReportsCompletion`）。
- `HsBleSettings.WRITE_TYPE`：回退 `WITHOUT_RESPONSE`（真机结论，见 8.1-3）。
- 说明：§3.4 中「A：改 WITH_RESPONSE」被真机推翻并回退；§6.6 以本节为准。B（`HsStreamStartGate` 多源结算）保留为冗余保护（对无 99ECD5 回执的固件仍有效）。
- 全量门禁（真机修正 + 复审修复后）：`biz_ggec_hs 190 / biz_ggec_wy 34 / app 627` 单测全绿；`checkBizGgecHsBoundary`、`checkBizProtocolPurity`、`:app:assembleDebug` 通过。

### 8.4 证据留存（`.scratch/device/hs02-test/`）

- `01_home`~`11_stopped_fix2` 各阶段截图；`record_start_full.log` / `record_stop_full2.log` / `wear_toggle_full.log` 等 logcat 原始记录。
- 关键截图：连接（`05_connected`）、随录随传录音中（`08_recording`，00:00:20）、修复后停止落盘（`11_stopped_fix2`，新文件 00:01:51/3.4MB）、佩戴检测成功后开关保持 ON（`12_wear_on`）。

### 8.5 遗留项清理：开关「成功后保持用户目标值」

真机发现：命令成功后（`佩戴检测开启成功 code=0`），开关会回落到设备上报的旧值（关），看起来像"设置了没生效"。根因是呈现策略"成功后设备上报值优先"，而 HS02 状态上报滞后/不回读。已清理：

- 通用设置页：`TogglePresentation` / `LevelPresentation` 在 `CONFIRMED` 时也**优先呈现用户目标值（pending）**，仅 `FAILED`/`IDLE` 回落到设备上报值；写入中 SENDING/PROCESSING 保持禁用+乐观呈现。相应更新单测（`TogglePresentationTest` / `LevelPresentationTest`）。
- 佩戴设置独立页：`WearSettingsViewModel` 增加 `pendingTarget`——点击即先切换、成功后保持目标值、失败/断连回退设备上报值（此前该页只镜像设备上报，未做乐观/保持）。
- 真机复验：`佩戴检测` 开关点击后 `check=true`（t+1s、t+4s 持续保持 ON），命令 `完成: code=0 佩戴检测开启成功`（截图 `12_wear_on`）。

> 说明：这是"呈现策略"清理，不改变命令成功率；若设备实际未持久化该设置（其状态上报仍为关），下次进入页面会如实显示设备状态。`app` 单测 627 全绿、`:app:assembleDebug` 通过。

---

## 9. 全量复审（第二轮）结论与修复清单

> 方式：3 个独立只读复审子代理（biz_ggec_hs / app / wy+docs 跨模块）+ lead 自查关键路径 + 全量门禁 + HS02 真机复验。
> 结论：**无 P0**；核心方向正确（真机验证随录随传/功能设置在 HS02 上均返回 `code=0` 成功）。复审发现的实质问题已按下表修复。

### 9.1 已修复

| 模块 | 问题 | 修复 |
|---|---|---|
| biz_ggec_hs | 音频切片逐片十六进制日志刷屏（≈50 片/s） | `HsStreamRecordSession` 音频数据日志限流：前 3 片 + 每约 1s 采样；控制包/关键事件逐条 |
| biz_ggec_hs | 快速路径回放循环未判终态 → 完成回调异步导致 `COMPLETED` 被回退成 `RECEIVING` | 回放循环加 `command.getCompletion()!=null` 守卫；`processFrame` 入口终态直接返回 |
| biz_ggec_hs | 快速路径未置 `writeStarted` → 迟到帧隔离不登记 | 快速路径同时置 `node.writeStarted=true`；捕获局部 attemptId |
| biz_ggec_hs | 真实写完成回调迟到会二次 `setState(SENT)`/重置超时 | `onWriteCompleted` 加 `if(node.writeCompleted) return;`（幂等） |
| biz_ggec_hs | 多命令 `perform` 聚合不向上转发 `onStarted` | `HsSessionImpl.submit(List)` 聚合器首个 `onStarted` 转发一次 |
| app | 结果守卫按「值相等」判定，同值旧回调可能误确认/误回退 | 引入每次写入单调 `seq` token，`onStarted/onWriteResult` 按 seq 认领（非值相等） |
| app | `write()` 缺「同能力在途」守卫，重绘前双击/重复触发可并发下发 | `write()` 同能力已 `inFlight` 直接忽略；补单测 `duplicateWriteWhileInFlightIsIgnored` |
| app | `WearSettingsViewModel` 状态字段跨蓝牙线程/主线程无锁 | 加 `lock` 统一读写；失败/断连清空；外部事件锁外投递 |
| app | `DeviceSettingsViewModel` 持锁调用 `events.postValue` | 移出锁，锁内只算结果 |
| app | `LevelPresentation` 用黑名单判 targetWins（与 Toggle 白名单不一致） | 统一为 `inFlight() || CONFIRMED` 白名单 |
| docs | 前部仍把 `WITH_RESPONSE` 当已修、`IN_FLIGHT` 旧基线当现状、计数不一致 | 顶部加勘误、就地标注作废、计数统一（190/34/627）；本 §9 |

### 9.2 复审记录、按设计取舍（未改，理由）

- **成功后保持 pending（P1-1）**：用户明确要求「命令成功后开关显示其目标值」（§8.5 真机复验）。HS02 状态上报滞后/不回读时，如立即回采会"显示关闭"与需求相反。故成功后保持目标值；若设备实际未持久化，离开重进页面会如实反映设备状态。属**产品取舍**，非遗漏。
- **快速路径 P1-2（回环帧误完成）**：HS02 请求/成功响应同形，首枚入站同族帧即设备应答；未观察到固件回环。**第三轮已改为按通道 opt-in**（`CommandScheduler.setTrustResponseAsWriteCompletion`，仅 HS 开启，默认关闭走可靠缓冲语义），见 §9.4。
- **`HsDeviceRecordSyncSession` 已出流即接受 / 无总体超时（P2-4）、停止收尾延迟（P2-5）**：**第三轮已加固**——文件同步加总体超时兜底；随录随传停止加 ~3s 回执兜底，见 §9.4。

### 9.3 最终验证

- 单测/门禁：`biz_ggec_hs 190 / biz_ggec_wy 34 / app 627` 全绿；`checkBizGgecHsBoundary`、`checkBizProtocolPurity`、`:app:assembleDebug` 通过。
- HS02 真机（复审修复版）：佩戴检测开关 `check=true` 且 `完成 code=0`；随录随传 开始 `code=0`、停止 `code=0` 并落盘（示例 674 帧 / 13.48s）。

### 9.4 第三轮加固（已实施）

1. **快速路径按通道 opt-in**：`CommandScheduler.setTrustResponseAsWriteCompletion(boolean)`，默认 `false`（写完成可靠通道走「缓冲待 onWriteCompleted 回放」的可靠语义）；仅 `HsProtocolEngine` 构造时开启。限制快速路径的适用范围，避免对回环/可靠写通道误判。单测：`matchingResponseCompletesEvenWhenWriteNeverReportsCompletion` 显式 opt-in；默认语义由既有 `responseBeforeWriteStartedIsBufferedNotDropped` 覆盖。
2. **文件同步总体超时**：`HsDeviceRecordSyncSession` 增加 `SYNC_TOTAL_TIMEOUT_MS=120s` 兜底，超时 `cancelInternal("录音文件同步超时")`，避免永久「进行中」。
3. **停止延迟优化**：`HsStreamRecordSession` 发出停止命令后启动 `STOP_FALLBACK_MS=3s` 兜底；设备停止应答偶发延迟（实测曾达十余秒）时，到点按 best-effort 收尾（`随录随传停止完成（停止回执超时兜底）`），不再让 UI 等满 15s 看门狗。
   - 真机复验：停止在 ~3s 内完成（日志 `停止回执超时兜底`），文件正常落盘（218 帧 / 4.36s）。

### 9.5 最终门禁（第三轮后）

- `biz_ggec_hs 190 / biz_ggec_wy 34 / app 627` 全绿；`checkBizGgecHsBoundary`、`checkBizProtocolPurity`、`:app:assembleDebug` 通过。


