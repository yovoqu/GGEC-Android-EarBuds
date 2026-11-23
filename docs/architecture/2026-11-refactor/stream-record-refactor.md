# 随录随传音频链路架构重构说明

版本：2026-11 · 对应提交前工作区（未 commit）
依据：`review-wy-streamrecord-logcat.md`、`refactor-plan.md`、设备端录音参数

## 1. 背景

日志审查暴露的核心问题是三个错误假设：

1. **“SPP 回调边界 == 协议包边界”**——SPP 是字节流，读线程会把多次读合批/拆包，长度并非固定 440；
2. **“停止清理可以延迟但资源字段可共享”**——`finish()` 在解码线程延迟关闭共享的 `decoder/fileWriter`，快速“停→开”会把新会话资源关掉；
3. **“解码码率可沿用默认 32000”**——实际线格式为 40B/20ms = 16 kbps，日志 `帧周期=5004` 也把 Opus 帧时长枚举误当成毫秒。

本次重构从架构上消除这三点，并收敛控制帧/参数常量、补全失败映射。

设备端录音参数（本次统一的物理真值）：

| 项 | 值 | 推导 |
|---|---|---|
| 采样 | 16 kHz / 16 bit / 单声道 / 20 ms | — |
| PCM | 640 B/帧 | 320 样本 × 2 B |
| PCM 码率 | 256 kbps | 640×8×1000/20 |
| Opus | 40 B/帧 | 16 kbps |
| 压缩比 | 16:1 | 256 / 16 |

## 2. 目标架构

```
SPP 字节流（任意分片/合批）
   │
   ├─ 控制通道(7777…) ──► 产品会话(WyStreamRecordSession/HsStreamRecordSession)
   │                         └─ 8B 控制帧判别 → 开始/停止 ACK、远端停录
   └─ 音频通道(8888… / 820003) ──► 控制帧? 精确 8B → 分流
                                    └─ 其余字节（任意长度）原样透传
                                          │
                                          ▼
                        lib_ggec_audio.StreamRecordPipeline
                        └─ 每会话 Session{Assembler,Decoder,Writer,Listener,Config,Commands,gen}
                                          │
                                          ▼
                        lib_ggec_audio.AudioFrameAssembler（纯字节流组帧）
                        └─ append → 累计缓冲几何自证锁定 → 按步长切 40B Opus
                                          │
                                          ▼
                        lib_ggec_opus 解码（16k/1ch/20ms，wire 16kbps）→ 640B PCM
```

### 关键不变量

- **输入是流，不是包**：`AudioFrameAssembler.assemble()` 无条件把字节追加到累计缓冲；格式锁定与切帧只看累计缓冲的字节几何，绝不看 `chunk.length`。
- **前导垃圾可滑移**：锁定扫描允许跳过最多 64 字节前导控制/错位字节（`AudioFormatDetector.scanStream` 返回 `startOffset`）。
- **会话隔离**：每个录音会话独立持有 Assembler/Decoder/Writer；`finish(session)` 只关闭传入的旧会话对象，`current` 置空后再允许新会话。
- **产品无关**：lib_ggec_audio 不认识产品、不认识协议（不出现 modelId/HS0/WY0，不引用内部实现），只依赖 `lib_ggec_opus.api`。
- **命令/数据分道**：开始/停止命令只走控制通道；音频流只走音频通道；音频通道若夹带 8B 控制帧则分流，不进入音频管线。

## 3. 模块变更

### 3.1 lib_ggec_audio

- `AudioFrameAssembler`：重写为“恒 append + 累计缓冲切帧”，删除旧 `detect/probeIndex/probeLength` 与未使用的 1 参私有重载；`MAX_PROBE_BYTES = 440×8`，缓冲 16 KiB。
- `AudioFormatDetector`：新增 `scanStream(buf,size,byteOrder)` 与 `StreamLock{format,startOffset}`，按步长 44→84→164→168 做相邻帧头 `index+1/+2/+3` 几何自证；保留 `uniqueFormatFor` 快路径与 `readIndex`。
- `record/StreamRecordPipeline`：引入私有 `Session`，把 Assembler/Decoder/Writer/Listener/Config/Commands/`totalPcmFrames` 全部挂到会话；`start/stop/stopByDevice/finish/release` 只操作当前会话或传入的旧会话；时长改用 `AudioFrameContract.FRAME_PERIOD_MS`。

### 3.2 lib_ggec_opus

- `YoOpusAudioContract`：新增 `OPUS_BIT_RATE_16K/32K/64K`、`PCM_BIT_RATE`、`WIRE_OPUS_BIT_RATE(=16k)`、`COMPRESSION_RATIO(=16)`；全部由字节数推导，既有 `BIT_RATE_DEFAULT=32000` 保持不变。
- `OpusDecoder`：构造传入 `WIRE_OPUS_BIT_RATE`，`生效码率` 与 40B/20ms 一致。
- `HuyaDecoder`：日志区分“帧周期=20ms”与 `OPUS_FRAMESIZE_20_MS=5004`（枚举）；`init()` 只保留 decoder 有意义的 ctl（FORCE_CHANNELS / LOOKAHEAD），删除 encoder-only ctl；`decode()` 行为不变。
- `OpusUtils.Shorts2Bytes`：固定小端输出，PCM 端序不依赖 CPU 探测。

### 3.3 biz_ggec_wy

- 新增 `WyStreamControlFrames`：`START/STOP`、`FRAME_LEN=8`、`STATUS_INDEX=5`、`isStopFrame/isAnyControlFrame/statusAt`，控制帧唯一信源。
- `WyStreamRecordSession`：音频入口不再按 `<44` 丢弃；精确 8B 控制帧分流（停止→`stopByDevice`，其它忽略），其余字节原样交管线。删除 `handleStart/StopResponse` 中的显式断音频（统一由 `pipeline.finish → commands.detach` 一次断开）。
- `WyClassicRadio`：`mapFailure` 补 `SPP_LOCAL_CLOSED/SPP_REMOTE_CLOSED → DISCONNECTED`；新增 `audioCloseIntended`，主动关闭音频通道的终态不再对外报 `DEVICE_ERROR`。

### 3.4 biz_ggec_hs

- 仅测试适配：`HsAudioPacketClassifierTest` 中“短切片被缓存”断言改为 `pendingBytes()==输入长度`（HS 控制包仍由 `HsAudioPacketClassifier` 在进管线前过滤；装配器默认小端行为不变）。

## 4. 通道时序（与日志一致）

```
连接音频 SPP(8888) ──► 控制通道发 99ECD5 ──► 控制通道收 99ECD5 ack ──► 音频 SPP 出 440B 流
控制通道发 99ECD6 ──► 控制通道收 99ECD6 ack ──► pipeline.finish → detach → 断开音频 SPP
远端 D6：控制通道或音频通道收到 8B D6 → pipeline.stopByDevice() → detach
```

## 5. 生命周期（Session）

状态：`IDLE(current==null)` ↔ `RECORDING(current!=null)`。

- `start`：`current!=null` → 拒绝；否则新建 Session（独立 Assembler/Decoder/Writer）→ `current=session` → `commands.start`。
- `onAudioChunk`：捕获 `current`，投递到单线程 `decodeExecutor`。
- `stop`：`current.commands.stop` → ack → `finish(session)`。
- `finish(session)`：`session==current` 才生效；锁内 `closed=true; current=null;`；`commands.detach()`；把该会话 writer/decoder 抓成局部变量交解码线程关闭；回调 onError?→onFileSaved?→onStopped→callback。
- 快速 `stop→start`：旧会话清理只关闭旧 writer/decoder，新会话对象不受影响（有单测覆盖）。

## 6. 测试矩阵

| 模块 | 用例 | 结果 |
|---|---|---|
| lib_ggec_audio | 24（含合批880→20帧、拆包300+140→10帧、前导垃圾、短切片缓存、快重启竞态、首片164/88 不误锁） | 全通过 |
| lib_ggec_opus | 10（契约 5 + 端序 3 + PcmWriter 2） | 全通过 |
| biz_ggec_wy | 33（含 880 合批、300+140 拆包、8B 控制帧分流、短字节不丢、主动断开不报 DEVICE_ERROR） | 全通过 |
| biz_ggec_hs | 167（含适配后的 classifier 断言） | 全通过 |
| 边界门禁 | lib_ggec_audio / lib_ggec_opus / biz_ggec_wy / biz_ggec_hs | 全部通过 |

合计 234 个单测，0 失败 / 0 错误。

全量命令（已 BUILD SUCCESSFUL）：
```
.\gradlew.bat :lib_ggec_audio:testDebugUnitTest :lib_ggec_opus:testDebugUnitTest \
  :biz_ggec_wy:testDebugUnitTest :biz_ggec_hs:testDebugUnitTest \
  :lib_ggec_audio:checkLibGgecAudioBoundary :lib_ggec_opus:checkLibGgecOpusBoundary \
  :biz_ggec_wy:checkBizGgecWyBoundary :biz_ggec_hs:checkBizGgecHsBoundary --console=plain
```

## 7. 评审后加固（第二轮）

独立 Reviewer 复审后追加的必须项，均已修合并回归：

- **格式锁定顺序**：`tryLockFormat` 改为「整包长度快路径（仅 176/440 | 40B 子帧）→ 多帧几何自证 → 单帧 164 兜底」。禁止用单子帧长度（44/84/164）做快路径，避免 40B 流首个 164B 切片被误锁成 160B 步长（P1）。新增回归 `partialFirstChunk164DoesNotMislockFullFrame` / `partialFirstChunk88DoesNotMislockDualHalf`。
- **锁序去死锁**：`WyStreamRecordSession` 的开始/停止“超时 / 发送失败”回执改为先在 `protocolLock` 内摘除待回执、**出锁后**再 `reply.onReply(...)`，与 `handleStart/StopResponse` 一致，消除 `protocolLock ↔ pipeline 监视器` 的 ABBA 死锁窗口（P2）。
- **音频连接代次**：`WyClassicRadio` 增加 `audioGeneration`，旧音频连接的迟到终态按代次丢弃，避免误报/误抑制（P2）。
- **可见性**：`Session.totalPcmFrames` 改为 `volatile`；构造器 Javadoc 明确 `decodeExecutor` 必须单线程 FIFO（P3）。
- **死代码清理**：删除 `AudioFormatDetector.formatFor(...)` 两个重载、`AudioPacketTracker.isExactNext/getFramesPerNotify`、`HuyaDecoder.h` 未用的 `VOICE_FRAME_PERIOD` 宏；控制命令码收敛为 `WyStreamControlFrames.CMD_START/CMD_STOP`（P3）。

## 8. 残余风险与后续

1. `scanStream` 依赖“序号连续递增”的几何证据；若固件序号出现回绕/重置（极少），会重新取证（返回 null 等更多数据）而非误锁；已有回绕处理（`isPlausible` 回退滑移）。
2. `WyStreamControlFrames.isStopFrame/isAnyControlFrame` 是 8 字节全等匹配；正式 D6 走控制通道，音频通道夹带控制帧属防御分支；若未来控制帧变长需同步扩展。
3. lib_ggec_opus native 配置仍为“进程内单配置”（HS/WY 同参），真正多参数并存需把配置成员化（本次按低风险原则仅注明）。
4. 解码器 `frame_size` 上限按 20ms 固定；若未来固件发 40/60ms 帧需同步扩展。
5. `AudioFormatDetector.scanStream` 对 stride44 统一返回 `DECA_40_440`（与 `QUAD_40_176` 同一 40B 子帧几何）；功能等价，`getActiveFormat()` 在“非整包长度首包”场景可能显示 DECA 而非 QUAD，仅影响可观测枚举，不影响切帧。

## 9. 变更文件清单

lib_ggec_audio：`AudioFrameAssembler.java`、`AudioFormatDetector.java`、`record/StreamRecordPipeline.java`、`AudioFrameAssemblerTest.java`、`record/StreamRecordPipelineTest.java`
lib_ggec_opus：`api/YoOpusAudioContract.java`、`vendored/java/com/bes/opus/OpusDecoder.java`、`vendored/java/com/bes/opus/OpusUtils.java`、`main/cpp/opus/HuyaDecoder.cpp`、`main/cpp/opus/include/HuyaDecoder.h`、`YoOpusAudioContractTest.java`、`OpusUtilsEndiannessTest.java`
biz_ggec_wy：`internal/record/WyStreamRecordSession.java`、`internal/record/WyStreamControlFrames.java`、`provider/lib_bluetooth/WyClassicRadio.java`、`WyStreamRecordSessionTest.java`、`WyClassicRadioTest.java`
biz_ggec_hs：`HsAudioPacketClassifierTest.java`
文档：`review-wy-streamrecord-logcat.md`、`refactor-plan.md`、`stream-record-refactor.md`
