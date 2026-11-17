# biz_ggec_hs

HS 产品族（HS01 / HS02）的知识真源：型号目录、能力描述符、命令表、链路会话与录音会话。
**以现实现为准**——本文件就是 `biz_ggec_hs` 的目录契约，旧的「目标包结构」表（design §4 那一版）已失效，不再引用。

## 1. 对外权威面（`api/` + 根门面）

外部（app 的 provider、测试）只允许经下面这条面接入，**不 import `internal/**` 与 `provider/**`**：

| 文件 | 职责 |
|------|------|
| `YoHsSdk`（根门面） | 唯一入口：`open(context)` / `close()`，转发扫描、连接、快照、读写、动作、录音 |
| `api/HsDevice` | 项目 / 型号 / 设备形态 / 能力枚举 / 描述符（`Feature`、`Option`、`Range`）/ 双模身份 / 电量 / 信息 / 快照 |
| `api/HsControl` | 连接状态、写入值与动作、失败分类、结果 `Outcome`、监听器与设备事件 |
| `api/HsScan` | 扫描筛选规则、已识别设备、扫描视图与健康度 |
| `api/HsRecordApi` | 录音契约：随录随传、耳机本地录音、文件同步、手机端录音文件管理 |
| `api/StreamRecordConfig` / `api/StreamRecordListener` / `api/SyncProgressListener` | `HsRecordApi` 的入参与回调载荷 |

`api/` 包的自律：只依赖 `java.*`、`androidx.annotation`、`api` 自身；不 import `internal/**`、`provider/**`、蓝牙栈。

## 2. 能力描述符（单一真源）

- 每个 `HsDevice.Capability` 恰好一条 `HsDevice.Feature`，在 `internal/center/catalog/HsCatalogImpl` 构造期建立并校验。
- 目录不变量（违反即抛，无 `default` 兜底）：`ACTION` 必须有可达命令；`LEVEL` 必须带值域；`CURVE`
  必须同时带频段 `options` 与增益 `Range`；`GATE` 不得带值域也不得可写；`TOGGLE / CHOICE / LIST` 不得带值域。
- `CUSTOM_EQ`：频段表与增益域都从协议命令 `CustomEQRealtimeCommand`（`BAND_LABELS` /
  `GAIN_MIN|MAX|STEP`）派生，目录里没有第二份频段表；界面只渲染描述符，描述符缺失即不可用。
- 型号差异只在目录里声明（HS01 28 项能力、HS02 在 HS01 之上加 7 项）；调用方不写机型分支。

## 3. `internal/center/**` 声明结构（现实现）

`center` 是「协议与链路的中层」，按职责分块，两块之间互不点名：

```
internal/center/
  HsGraph                      组合根：唯一装配 provider 适配、协议引擎、会话、目录、录音的地方
  broadcast/                   广播解析（HS01 / HS02 广告、身份、产品目录、扫描快照）
  catalog/                     能力目录与选项 id（HsCatalogImpl、HsOptionIds）
  exchange/                    帧路由与流量（HsFrameRoute、HsTraffic）
  link/                        经典链路连接计划与握手（HsClassicConnect、HsClassicPlan）
  port/                        中层端口（BLE/经典链路、音频数据链路、编解码、文件写入）
  protocol/                    协议引擎与命令族
    Command / CommandScheduler / CommandPolicy* / HsProtocolEngine / ProtocolValues
    batterycmd/  controlcmd/  devicecmd/  dualconnectioncmd/  filerecordcmd/
    functioncmd/ musiccmd/    streamrecordcmd/ internalcmd/  event/  inbound/
```

`internal/` 其余部分（上层）：

```
internal/interact/   能力 ↔ 命令路由（HsCapabilityRouter）、快照装配、型号词汇
internal/link/       连接会话（HsSessionImpl）、失败映射、就绪看门狗
internal/record/     录音会话：随录随传（共享 PCM 管线 + HS 命令表）、设备录音同步、路径解析、
                     820003 控制报文分类器、文件 ID 契约校验
internal/scan/       扫描实现、注册表、排序与 ticker
provider/            依赖适配：lib_bluetooth（蓝牙栈）、lib_common/log（日志缝）、lib_ggec_opus（编解码）
```

## 4. 与其他模块的边界

- 帧装配（线格式识别 / 切帧 / 包序号连续性 / 帧几何契约）与随录随传 PCM 管线（组包 → 解码 →
  振幅 → 分流 / 落盘 → 收尾）都在 `lib_ggec_audio` 的 `record` 包里，本模块不再保有副本；
  `internal/record` 只保留自己的**命令表与产品差异**：开始/停止协议命令、820003 控制上报码
  （`HsAudioPacketClassifier`）、路径解析、日志接线的三条转发（`HsRecordLog`）。
- 依赖只向下：`lib_bluetooth`、`lib_common`、`lib_ggec_audio`（其内部只依赖 `lib_ggec_opus`）。
- 结构门禁在 `build.gradle` 的 `checkBizGgecHsBoundary`：按目录 + import 归属判定，无类名/文件名白名单。
