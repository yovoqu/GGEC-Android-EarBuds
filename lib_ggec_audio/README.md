# lib_ggec_audio

音频空口帧装配层（L0）+ 随录随传共享 PCM 管线（L1 契约）。**只提供机制，不认识产品**：
不知道型号、项目、能力名，也不打日志。

## 职责

| 类 | 职责 |
|----|------|
| `AudioFrameContract` | 线格式契约：4B 头 / 各类 Notify 包长 / 每包帧数；**帧几何与 Opus 单帧字节数从 `lib_ggec_opus` 的 `api.YoOpusAudioContract` 推导**，本模块不重复字面量 |
| `AudioNotifyFormat` | 下行线格式枚举（176×4 / **168×4 整包头** / 168×2 / 整包 164 / MTU 拆分 / 旧 8B 头 336）；`FrameLayout` 区分「每帧一个头」与「整包一个头」 |
| `AudioFrameAssembler` | 按首包锁定线格式，切出完整 Opus 帧；三种几何：整数倍步长 / 整包头 N 帧 / MTU 跨包补帧；残段留在缓冲，逐字节滑移重同步 |
| `AudioWireByteOrder` | **帧头字节序（产品适配参数）**：`LITTLE_ENDIAN`（HS 默认）/ `BIG_ENDIAN`（WY01 现场固件）。共享层不写死字节序 |
| `AudioPacketTracker` | 包序号连续性：序号**按帧递增**，缺口 = 推进量 − 每包帧数；包内可见 |
| `AudioFormatDetector` | 线格式判定：包长唯一即锁定；包长歧义（168）靠「相邻包首帧序号差」定布局；包内可见 |
| `AudioFrameTrace` | 观测点：格式锁定 / 判不出格式 / 序号前向跳号 / **切片级事实**；默认 `NONE` |
| `record.LoggingAudioFrameTrace` | 观测点唯一实现：`tag + AudioLog + 字节序`；**分级采样**（见下），产品不再各写一份 trace、也不再写散点 hex 诊断 |
| `record.StreamRecordConfig` | 随录随传配置契约的唯一实现（字段 + 输出模式 + 产品构造表入口） |
| `record.StreamRecordListener` | 随录随传监听器契约的唯一实现（产品 api 直接继承） |
| `record.AudioCodecProviderPort / AudioDecoder / AudioFileWriter` | 编解码与落盘端口：产品只实现工厂（`lib_ggec_opus` 适配） |
| `record.PcmAmplitude` | 16-bit 单声道 PCM 归一化振幅的唯一实现 |
| `record.StreamRecordTargets` | 落盘目标解析（自定义文件名 / 目录 / `.pcm` 后缀）的唯一实现 |
| `record.StreamRecordPipeline` | 随录随传 PCM 管线唯一实现：组包 → 解码 → 振幅 → 分流 / 落盘 → 收尾；产品差异全部收在 `Commands` 命令表 |
| `record.AudioLog` | 管线日志缝：lib 只产生消息，产品侧一行转发到自己的日志门面 |

## 字节序（产品适配，不可写死）

帧头是 4 字节序号，**字节序随产品不同**：

| 产品 | 字节序 | 依据 |
|------|--------|------|
| HS | `LITTLE_ENDIAN`（默认构造器） | 既有固件，已定稿，行为不得改变 |
| WY01 | `BIG_ENDIAN` | 现场字节证据：包首 `00 00 00 00` / `00 00 00 0A` / `00 00 00 14`（大端 0/10/20） |

共享层若写死任一侧，都会破坏另一侧：改成大端会破坏 HS；保持小端则 WY 每包只有首帧
（序号 0 在两序下相同）能解析，其余成上亿伪值 → 逐字节滑移 → 切帧错位 → `corrupted stream`。

## 观测与日志规模（数据可打印，且不刷屏）

`AudioFrameTrace` 是唯一观测出口，`LoggingAudioFrameTrace` 是唯一实现。分级：

| 场景 | 级别 | 内容 |
|------|------|------|
| 格式锁定（一次性） | INFO | 完整几何 + 字节序 |
| 正常切片（前 `DEFAULT_DUMP_EVERY_N=10` 个） | DEBUG | 序号 / 帧数 / 头部 hex（截断 44B） |
| 正常切片（后续） | 静默 | — |
| 零帧（切帧疑似失败） | WARN | 原始字节，**必须可见** |
| 序号缺口 | WARN | 缺口折算 |
| 无法识别格式 | WARN | 长度 + 暂存 |

约束：

- 绝不无条件 hex —— 440B/包、5 包/秒，一分钟数十万字符；
- 排障时把 `dumpEveryN` 调大即可看更多样本；
- 零帧是切帧失败的唯一信号，不能静默；
- **biz 不得再写散点诊断**（hex/probe），观测只能经 `AudioFrameTrace`。

## 线格式判定规则（防复发）

168 字节是唯一**有歧义**的包长，它同时对应两种布局：

| 布局 | 几何 | 相邻包首帧序号差 |
|------|------|------------------|
| `QUAD_40_168` | 整包 1 个 8B 头 + 4×40B Opus（16 kbps） | **4** |
| `DUAL_HALF_168` | 每帧 4B 头 + 80B Opus（32 kbps），2 帧 | **2** |

判定顺序：**包长唯一 → 首包锁定**；包长歧义 → 用「相邻两个 Notify 首帧 dataIndex 之差」
定夺（该差就是上一包承载的帧数），包内嵌帧头（offset 84 / 164 出现 `idx0+1`）作为次级证据。

必须遵守的两条不变式：

1. **序号按帧递增**。一包 4 帧时相邻包序号天然差 4，这不是丢包。
   缺口 = 推进量 − 每包帧数；只有缺口 > 0 才允许上报。
2. **整包头布局下，包内帧序号是隐式的**。绝不能拿包内偏移去匹配序号头，
   否则会把一包 4 帧误读成一帧 160B，交给 Opus 得到 `corrupted stream`。

回归用例：`AudioFrameAssemblerTest#locksQuad40_168FromFieldSequenceAndExtractsFourFramesPerNotify`
与 `#reportsGapOnlyWhenWholeNotifyIsMissing` 用现场序列 `0→4→8→12` 固化上述不变式。

## 依赖边界

唯一允许的模块依赖是 `lib_ggec_opus`，且只允许它的**公开面**：
`com.yovo.lib_ggec_opus.YoOpusSdk` 与 `com.yovo.lib_ggec_opus.api.**`；
`com.yovo.lib_ggec_opus.internal.**` 明令禁止（实现细节不得跨模块引用）。

几何单源性：`AudioFrameContract.SAMPLE_RATE / CHANNEL_COUNT / FRAME_PERIOD_MS /
PCM_SAMPLES_PER_FRAME / PCM_FRAME_BYTES / OPUS_BYTES_* / BIT_RATE_*` 全部由
`api.YoOpusAudioContract` 推导，方向固定为 `audio -> opus/api`（opus 不反向依赖本模块），
`AudioFrameContractTest` 断言两侧取值一致。

`checkLibGgecAudioBoundary` 结构性校验上述依赖集合（无类名/文件名白名单），同时禁止产品
关键字（`modelId|projectId|deviceType|HS0|WY0`）、`android.util.Log`、`printStackTrace`、
`catch (Throwable|Exception ignored)`。

## 谁在用

- `biz_ggec_hs`：`internal/record/HsStreamRecordSession`（共享管线 + HS 命令表）、
  `HsDeviceRecordSyncSession`（820003 控制上报包由 biz 自己的 `HsAudioPacketClassifier`
  先分流，不进入本模块）。
- `biz_ggec_wy`：`internal/record/WyStreamRecordSession`（共享管线 + 空命令表）。

两个 biz 不再各自持有装配器副本，也不再各写一份 PCM 会话管线、编解码端口与帧观测接线；
产品只保留自己的命令表（控制上报码、路径解析、协议命令）与一行日志转发。
