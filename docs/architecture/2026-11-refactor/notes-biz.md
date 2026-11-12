# biz_ggec_hs 重构笔记（2026-11）

> 依据 `design.md` §4 / §5 / §5.1 + lead 两次契约增补（LEVEL 值域、`HsEarInCase`）与两次裁决
> （`RecordMode` 上提、provider 不得依赖中层）。实现与验证命令见文末。

## 1. 最终目录（三层单向）

```
com.yovo.biz_ggec_hs
  YoHsSdk                      唯一入口：open(Context) → nearby()/session()/catalog()/close()
  api/                         28 个契约文件，零实现、零 android/lib_bluetooth/internal 依赖
  internal/
    contract/    HsCommandLink、HsConnectionEvents、HsBleScanLink、HsRawAdvert
    broadcast/   广播识别（HsScanSnapshot/Hs01|Hs02Identity/HsBroadcastRuntime/HsScanSnapshots…）
    protocol/    帧、命令表、调度、入站事件、域存储（原位保留）
    device/      上层编排：HsSessionImpl、HsSessionState、HsSnapshotAssembler、HsCapabilityRouter、
                 HsCatalogImpl、HsConnectReadyTimeout、HsFailureMapper、
                 HsNearbyImpl、HsNearbyScanSession、HsNearbyScanCallbacks、HsNearbyDevices、HsNearbyScanLedger
    graph/       HsGraph：唯一装配根（provider + protocol + device）
    log/         HsLog
  provider/lib_bluetooth/      纯栈适配：YoBleCommandLink、HsNearbyScanner、HsRawAdverts、HsScanFailures
```

依赖方向：`provider(底层) → api/internal.contract ← internal/device(上层) → internal/protocol|broadcast(中层)`；
`internal/device` 不 import `provider`，provider 不 import `{protocol,broadcast,device,graph}`。

## 2. 删除 / 迁移清单

- 删除：`api/HsRuntime`、`api/YoHsClient`、`api/YoHsNearby`、`api/YoHsBroadcastSession`、
  `api/HsConnectionObserver`（→ `internal/contract/HsConnectionEvents`）、根包 `YoHsBroadcast`、
  `assembly/HsAssembly`、`assembly/HsBroadcastAssembly`、`model/**`、`internal/session/**`。
- `model/broadcast/*` → `internal/broadcast/*`；`model/PairedDevice` → `api/HsPairedDevice`（TLV 字节只在
  `PairedDeviceTlvParser` 转枚举）；`DualConnectionPageData` → `api/HsDualConnectionState`（不可变）；
  `RecordFileInfo` → `api/HsRecordFile`；`HsCommandResult/HsResultCallback` → `api/HsResult/HsWriteCallback`；
  `HsBattery/HsDeviceInfo/HsNearbyDevice/HsScanFailure` → `api/*`（后者 → `HsFailure`）。
- `model/PairedDeviceMapper` 删除：它只服务旧「Map 形状的公开 API」，新契约用 `HsPairedDevice` 列表，
  保留会变成无消费者的转换层（design §4 把它迁到 internal/device 的前提已不存在）。

## 3. 能力 ↔ 命令 / 状态映射（`HsCapabilityRouter`，唯一映射点）

| 能力 | 控件 | 写入命令 | 状态来源 |
|------|------|----------|----------|
| EQ_MODE | CHOICE `0..5` | `EQModeCommand(mode)` | 域状态 `eqMode` |
| CUSTOM_EQ | CURVE（10 段） | `CustomEQRealtimeCommand(gains)` | — |
| SOUND_QUALITY | CHOICE `high_quality/high_endurance` | `SoundQualityModeCommand(bool)` | — |
| LDAC | TOGGLE | `LDACCommand` | `LDACQueryCommand` 回包落域 `ldacEnabled` |
| VOLUME_ADAPTIVE | TOGGLE | `VolumeAdaptiveCommand` | — |
| GAME_MODE | TOGGLE | `GameModeCommand` | 域状态 `gameMode` |
| WEAR_DETECT | TOGGLE | `WearDetectionCommand` | — |
| VOICE_WAKEUP | TOGGLE | `VoiceWakeupCommand` | — |
| FALL_ALERT | TOGGLE | `FallAlertCommand` | — |
| VOLUME_REMIND | TOGGLE | `VolumeRemindCommand` | — |
| BEGINNER_MODE | TOGGLE | `BeginnerModeCommand(enter)` | 事件 `BeginnerModeChanged` |
| INTERPRET | TOGGLE | `SimultaneousInterpretationCommand(enter)` | — |
| DUAL_CONNECTION | TOGGLE | `SetDualConnectionCommand` | 聚合页查询 → 事件 `DualConnectionChanged` |
| TOUCH_LONG / SHORT / DOUBLE | CHOICE `<耳类型>:<操作码>` | `Long/Short/DoublePressSetCommand(action, ear)` | — |
| TOUCH_TAP | CHOICE `<耳侧>:<触击>:<功能码>` | `ControlCommand(ear, tap, function)` | — |
| FIND_EAR | ACTION | `FindAlertLeft/Right(true)`、`FindAlertStop` | 事件 `FindAlertChanged` |
| RENAME | ACTION | `DeviceRenameCommand` | — |
| REBOOT | ACTION | `DeviceRebootCommand` | — |
| RESET_CONTROL | ACTION | `ResetControlCommand` | — |
| CLEAR_PAIRING | ACTION | `ClearPairingCommand` | — |
| RECORD_LOCAL / STREAM | ACTION | `StartLocal/StopLocal`、`StartStream/StopStream` | — |
| RECORD_LIST | LIST | `GetRecordIdListCommand(0,12)` | 回包 → 事件 `RecordFilesChanged` |
| RECORD_TRANSFER | ACTION | `StartTransmitRecordCommand(index)` / `CancelTransmitRecordCommand` | — |
| 双连接页（`ReadPairedDevices`） | ACTION | `GetPairedDevicesCommand` + `GetDualConnectionPageCommand` | 事件 `PairedDevicesChanged` + `DualConnectionChanged` |
| 配对设备增删/优先/连断 | ACTION | `DeleteParingDeviceCommand`、`SetPriorityEquipmentCommand`、`ConnectPhoneCommand`、`DisconnectPhoneCommand` | 事件 `PairedDevicesChanged` |
| CONNECTION / BATTERY / DEVICE_INFO / SERVICE_STATUS / AI_ASSISTANT / OTA / OFFLINE_LOG / CLASSIC_BT | 声明态 | 无写入 | 快照 / 事件 |

错误归一（`HsFailureMapper`）：`SUCCESS→成功`、`TIMEOUT→TIMEOUT`、`FAILED→DEVICE_ERROR`、
`RESERVED→REJECTED`（会话切换/断开/释放导致的撤销）、入口未就绪 `NOT_READY`、目录外能力 `UNSUPPORTED_CAPABILITY`；
连接事件：`ready-timeout→TIMEOUT`、链路错误→`DEVICE_ERROR`、主动断开与换目标→无失败分类。

## 4. 两条裁决落地（lead 裁决，2026-11）

1. **固件自愈（选 B，不扩 API）**：会话进入 READY 后自动下发一次首页聚合状态命令（`99ECC7`），
   回包中的固件/颜色/电量/服务状态由既有聚合落地路径并入快照。**每条会话一次固定流量**：失败不重试、
   不影响 READY 与连接状态，固件保持「未上报」语义；该命令在命令策略表里是 `SINGLE_FLIGHT`，
   已有在途聚合查询时复用、不重复下发。命令构造点在 `HsCapabilityRouter.sessionBootstrapQuery()`
   （不是能力映射，只为保持协议命令构造点唯一）。没有新增 `HsAction.ReadDeviceInfo`。
2. **机型门控**：`HsSession.connect(HsTarget)`（`HsTarget(modelId, variantId, bleAddress)`），
   会话用 `HsDeviceCatalog.model(modelId)` 取该机型声明的能力集合；未登记机型 → 链路照常连接、
   但写/动作一律 `UNSUPPORTED_CAPABILITY`（不做猜测）。地址缺失/空白抛 `IllegalArgumentException`。
   不保留 `connect(String)` 重载。

## 4.1 关键行为说明（沿用 / 修正）

1. **沿用**：READY 后每会话补查一次耳机颜色（短超时 800ms）；会话起点在 `connect()` 调用线程清域；
   一次链路失效只发一条终态事件；命令串行队列与迟到帧隔离不变。
2. **修正（协议层错误分类）**：`CommandScheduler.abortCurrent/abortAll` 的回执由 `FAILED` 改为
   `RESERVED`——命令被撤销不是设备失败，上层据此归一为 `REJECTED`。否则「换目标导致在途命令中止」
   会被报成 `DEVICE_ERROR`。
3. **修正（就绪前主动断开）**：链路在就绪前被收掉时，`settleLinkDown` 现在发一次
   `onDisconnected(null)`。原实现不发任何事件，上层会永远停在 `CONNECTING`。
4. **修正（双连接页信封）**：`GetDualConnectionPageCommand` 按 EC A7/AC/AE 家族统一信封解析
   （前缀 + 2 字节长度 + TLV + 尾标）。原实现按「无长度字段」切片，与入站拼帧器不自洽，
   该帧在真实链路上无法被切出；该命令此前无消费者、无测试。
5. **接口形状**：`HsAction.RecordMode` 上提到接口层（lead 裁决），`StartRecord`/`StopRecord` 共用。

## 4.2 第二轮修复（复审口径）

1. **F1 能力集补齐（功能回退修复）**：`HsCapability` 增补 `LDAC`、`VOLUME_ADAPTIVE`、`RESET_CONTROL`、`REBOOT`，
   `HsAction` 增补 `ResetControl`。目录里 HS01 28 项 / HS02 34 项（四项两头都声明，与重构前 app 的
   `device_row_ldac`、`device_row_volume_adaptive`、`SETTING_RESET_CONTROL`、`reboot()` 一一对应）；
   `HsCapabilityRouter` 四条映射齐全且 switch 穷尽（无 default 兜底）。
   LDAC 状态只有查询回包一个来源：引擎在 `LDACQueryCommand` 完成时把整数结果落到域，会话据此投影
   `HsControlValue.Toggle`，未查询前不出现在快照里（`read` 返回 `NOT_READY`，不编默认值）。
2. **F2 `consumer-rules.pro`**：删掉指向已删类/包的 keep 行，只保留 `YoHsSdk` 与 `api/**`。
3. **F3 注释冻结**：删除 `CaseChargingStatusCommand` 里被注释掉的旧 `getTimeoutMs()`；`internal/protocol`
   已无「注释掉的代码行」。

## 4.3 第二波复审修复

1. **B1 `BEGINNER_MODE` 状态补齐**：`HsCapabilityRouter.value()` 增加 `BEGINNER_MODE` 分支。状态来源是
   **进入/退出命令的应答**（`99ECCC` / `99ECCD`，设备应答即事实），引擎在 `BeginnerModeCommand`
   成功时落域 `beginnerModeEnabled`；主动上报 `99ECD2`（耳侧 + 动作）仍走
   `HsDeviceEvent.BeginnerModeChanged`，两者不同源、互不替代（前者是开关，后者是配置）。
   未进入/退出过时不进快照，`read` 返回 `NOT_READY`，不编默认值。
2. **B2 `value()` 去掉 `default`**：改成穷尽 `HsCapability` 的 switch，明确分成「有状态来源」
   （`EQ_MODE / GAME_MODE / DUAL_CONNECTION / LDAC / BEGINNER_MODE`）与「无状态来源」两组
   逐项列出，新增能力漏分支即编译期报错。测试 `valueIsDefinedForEveryCapability` 遍历全部能力
   断言「有来源→给出值 / 无来源→null」，不再依赖 default 兜底。

## 4.4 控件形态收口（INFO / GATE）

`HsControlKind` 增补 `INFO`（只读信息）与 `GATE`（仅功能门控，不产生通用控件行），
声明态能力不再声明不存在的控件：

| kind | 能力 |
|------|------|
| TOGGLE | DUAL_CONNECTION, LDAC, VOLUME_ADAPTIVE, WEAR_DETECT, VOICE_WAKEUP, GAME_MODE, FALL_ALERT, VOLUME_REMIND, BEGINNER_MODE, INTERPRET |
| CHOICE | SOUND_QUALITY, EQ_MODE, TOUCH_LONG, TOUCH_SHORT, TOUCH_DOUBLE, TOUCH_TAP |
| CURVE | CUSTOM_EQ |
| ACTION | FIND_EAR, RESET_CONTROL, REBOOT, RENAME, CLEAR_PAIRING, RECORD_LOCAL, RECORD_STREAM, RECORD_TRANSFER |
| LIST | RECORD_LIST |
| INFO | CONNECTION, BATTERY, DEVICE_INFO, SERVICE_STATUS |
| GATE | AI_ASSISTANT, OTA, OFFLINE_LOG, CLASSIC_BT |
| LEVEL | 无（类型保留；电量改 INFO 后不再有可写等级控件） |

构造期不变量（`HsCatalogImpl.validate`，switch 穷尽 8 种 kind，无 `default`）：
`ACTION ⇒ 必有写入或动作映射`、`LEVEL ⇒ 必有 range`、`INFO/GATE ⇒ 无 range 且不可写`、
`TOGGLE/CHOICE/CURVE/LIST ⇒ 无 range`。判定与门控共用 `HsCapabilityRouter` 的
`supportsWrite` / `supportsAction`（已改为静态，目录与会话同一份判定）。
`value()` 对 INFO/GATE 返回 `null`（电量等值由快照字段承载，不另造状态）。

## 4.5 第四波收紧（单一状态来源 / 一能力一控件）

1. **`INFO` 删除，四项降 `GATE`**：`HsControlKind` 回到 7 名
   `{TOGGLE, LEVEL, CHOICE, CURVE, ACTION, LIST, GATE}`；`CONNECTION / BATTERY / DEVICE_INFO /
   SERVICE_STATUS` 全部是 `GATE`——`HsControlValue` 承载不了电量/连接态/信息/状态字典，若强行承载，
   同一事实会同时存在于快照字段（`HsBattery`/`HsDeviceInfo`）与 `values()`，违反单一状态来源。
   它们的展示由调用方既有界面承担（首页摘要卡、About 信息行、事件专项组件），biz 不渲染信息行。
   `GATE` 不变量：无 range、不可写、且不得进入 `HsSnapshot.values()`（`validateGate` 挡前两条，
   `gateCapabilitiesStayOutOfSnapshotValues` / `gateCapabilitiesNeverAppearInSnapshotValues` 挡第三条）。
2. **新增 `DUAL_CONNECTION_DEVICES`（LIST）**：双连接的「开关」与「设备列表」拆成两个能力，
   app 不再对同一能力做两种特判。`DUAL_CONNECTION` 仍是 TOGGLE（`SetDualConnectionCommand`）；
   `DUAL_CONNECTION_DEVICES` 承接 `ReadPairedDevices` 与配对设备四动作
   （`Forget / SetPriority / Connect / Disconnect`），数据只走 `PairedDevicesChanged` /
   `DualConnectionChanged` 事件。机型门控与 `DUAL_CONNECTION` 一致：HS02 有、HS01 无
   （HS01 上 `perform(ReadPairedDevices)` → `UNSUPPORTED_CAPABILITY`）。
3. 计数：HS01 **28** / HS02 **35**（HS02 多出的 7 项 = DUAL_CONNECTION, DUAL_CONNECTION_DEVICES,
   CUSTOM_EQ, TOUCH_DOUBLE, SERVICE_STATUS, BEGINNER_MODE, INTERPRET）；能力总数 **35**。

## 4.6 分类学 + 过滤规则 + 注册表（本轮）

### 分类学（api）
`HsDeviceType {EARBUDS, SPEAKER, HEADBAND}`、`HsProject(projectId, displayName)`、
`HsProductModel(modelId, displayName, projectId, deviceType, variants, capabilities)`（原 `HsDeviceModel` 改名并补两字段，旧名已删）。
目录数据：`HS01 → (HS, EARBUDS)`、`HS02 → (HS, EARBUDS)`；项目显示名「HS 系列」。
`HsDeviceCatalog.deviceTypes()/projects()` 都由 `models()` 聚合（项目集合不另存表，显示名常量表唯一）；
WY 只预留结构，未编造型号。

### 规则 / 注册表 / 视图
- `HsDeviceFilterRule(deviceTypes, projectIds, modelIds, onlyConnectable, minRssi)`：
  **维内 OR、维间 AND、空集=不限**；`none()` = 三维空 + 不限 RSSI（`MIN_RSSI_UNRESTRICTED = -127`）。
- 未登记型号（`modelId=""`）：空规则下照常出现；任一维度参与筛选即按「不匹配」排除，**不新增隐式分类**。
- `HsNearbyRegistry`（internal/device）：按地址去重、RSSI 滑动平均（窗口 8）、`lastSeenAt`、
  TTL=10s 淘汰；**lost 只有一个来源**（原 `HsNearbyScanLedger` 已删除并并入注册表）。
- `HsNearbyView`：`devices` 是命中规则且按 RSSI 降序的列表；`countsByType/Project/Model` 与
  `totalInRange` 对应**全量表**（分面计数不受当前筛选影响）。
- `HsNearby`：`setFilterRule` / `view()` / `health()` / `start(observer, HsScanProfile)`。
  `view()` 与 `health()` 走惰性淘汰（停扫后也能把过期设备变成 lost）。
- 规则变化**只重投影**：不 restart、不 stop、不清表（`ruleChangeReprojectsWithoutRestartingScanOrClearingTheTable`）。
  `start()` 是唯一清表点。
- 谓词下推：只有 RSSI 下界能用原始帧字段表达，其它维度一律回落 `null`（放行全部）；
  真源永远是注册表，谓词只省流量（`onlyRawFieldExpressibleRulesArePushedDown`）。

### 与 lib 的衔接
`internal/contract/HsBleScanLink` 增两条 **default 方法**：`setFramePredicate(Predicate<HsRawAdvert>)`
与 `applyScanProfile(HsScanProfile)`。lib 的 `ScanPlan/ScanFramePredicate/reconfigureScan` 落地后由
provider 覆盖实现；当前实现方忽略时只意味着「不省流量 / 用默认档位」，识别与规则语义不变。
`HsRawAdvert` 增 `connectable` 与 `observedAtMillis`（供 `onlyConnectable`、RSSI 平均与 TTL 使用）。

## 5. 验证（实跑）

```
.\gradlew.bat :biz_ggec_hs:compileDebugJavaWithJavac --offline --console=plain   # BUILD SUCCESSFUL
.\gradlew.bat :biz_ggec_hs:testDebugUnitTest --offline --console=plain           # BUILD SUCCESSFUL（147 tests）
.\gradlew.bat :biz_ggec_hs:check --offline --console=plain                       # BUILD SUCCESSFUL（含边界门禁 + lint）
```

旧公开名残留：`biz_ggec_hs/**`、`app/**`、`lib_*/**` 代码内 0 命中（`docs/**` 历史审计文档除外）。

## 6. 未闭合项

1. **app 侧需迁移到 `HsProductModel`**（appcore 范围）：`HsDeviceModel` 已按分类学改名删除，
   app 的 `HsCapabilityCatalogAdapter`、`FakeHsDeviceCatalog`、`HsCapabilityCatalogAdapter*Test`、
   `DeviceStackSmokeTest` 仍在引用旧名；`HsDeviceCatalog` 另新增 `deviceTypes()/projects()` 需实现。
2. **lib 注释残留**：`lib_bluetooth/internal/ble/scan/YoBleScanHub.java:53` 的注释仍写着
   `HsNearbyScanCallbacks`（该类已并入 `HsNearbyImpl`）——libbt 范围，请顺手删掉这个词。
3. `HsScanProfile` / `HsScanMode` / `HsNearbyTicker` 是本次按 `scan-architecture.md` §3.3 落地的最小契约
   （lead 只给了 `start(observer, HsScanProfile)` 的签名，未给 profile 结构），如需增字段（例如 legacy、
   硬件过滤）请给口径，我按 §5 补。

仍需 lead 同步的两件事：`design.md` §5 的 `connect(HsTarget)` 与 `api/HsTarget`；app 侧
`HsDevicePortAdapter` 的 `connect` 调用点。
