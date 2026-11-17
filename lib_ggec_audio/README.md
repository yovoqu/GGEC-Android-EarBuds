# lib_ggec_audio

音频空口帧装配层（L0）+ 随录随传共享 PCM 管线（L1 契约）。**只提供机制，不认识产品**：
不知道型号、项目、能力名，也不打日志。

## 职责

| 类 | 职责 |
|----|------|
| `AudioFrameContract` | 线格式契约：4B 头 / 各类 Notify 包长 / 每包帧数；**帧几何与 Opus 单帧字节数从 `lib_ggec_opus` 的 `api.YoOpusAudioContract` 推导**，本模块不重复字面量 |
| `AudioNotifyFormat` | 下行线格式枚举（176×4 / 168×2 / 整包 164 / MTU 拆分 / 旧 8B 头 336） |
| `AudioFrameAssembler` | 按首包锁定线格式，切出完整 Opus 帧；残段留在缓冲，逐字节滑移重同步 |
| `AudioPacketTracker` | 包序号连续性（包内）：只接受「下一个」或有限前向跳号，回退即不可信；**包内可见** |
| `AudioFormatDetector` | 线格式指纹判定（包长优先、序号区分布局、残段回落 MTU）；**包内可见** |
| `AudioFrameTrace` | 观测点：格式锁定 / 判不出格式 / 序号前向跳号；默认 `NONE` |
| `record.LoggingAudioFrameTrace` | 观测点唯一实现：`tag + AudioLog` 落三条事实，产品不再各写一份 trace |
| `record.StreamRecordConfig` | 随录随传配置契约的唯一实现（字段 + 输出模式 + 产品构造表入口） |
| `record.StreamRecordListener` | 随录随传监听器契约的唯一实现（产品 api 直接继承） |
| `record.AudioCodecProviderPort / AudioDecoder / AudioFileWriter` | 编解码与落盘端口：产品只实现工厂（`lib_ggec_opus` 适配） |
| `record.PcmAmplitude` | 16-bit 单声道 PCM 归一化振幅的唯一实现 |
| `record.StreamRecordTargets` | 落盘目标解析（自定义文件名 / 目录 / `.pcm` 后缀）的唯一实现 |
| `record.StreamRecordPipeline` | 随录随传 PCM 管线唯一实现：组包 → 解码 → 振幅 → 分流 / 落盘 → 收尾；产品差异全部收在 `Commands` 命令表 |
| `record.AudioLog` | 管线日志缝：lib 只产生消息，产品侧一行转发到自己的日志门面 |

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
