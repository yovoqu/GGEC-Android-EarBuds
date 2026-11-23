# WY 随录随传 代码/日志审查报告

审查对象：`logcat-export.txt`（2026-11-19 09:12:04 ~ 09:12:51，进程 com.ggec.earbuds）
涉及模块：`lib_ggec_audio`（组包/解码管线）、`lib_ggec_opus`（Opus 解码）、`biz_ggec_wy`（WY 会话/电台/录音）、`lib_bluetooth`（SPP 收发）

---

## 0. 结论摘要（TL;DR）

1. **命令通道 / 音频通道职责分离成立**：开始/停止命令 `99EC D5/D6` 经控制 SPP（UUID 7777…）收发；音频流数据只经音频 SPP（UUID 8888…）传输。代码路径与日志时序双重证实。
2. **编解码链路对当前 WY01 固件是正确的**：本次日志 3 段会话，锁定 `DECA_40_440 / PER_FRAME_HEADER / 40B Opus / 10 帧/包 / 440B / BIG_ENDIAN`；79 个音频包 → 790 帧 PCM 全部解出，**0 解码失败、0 序号缺口告警**；时长换算与墙钟吻合（430→8600ms、360→7200ms）。
3. **但存在若干设计/健壮性缺陷**，当前固件“恰好按 440B 均匀出包”把它们掩盖了。最值得修的三条：
   - **P1** 格式判定依赖“单次 SPP 回调长度 == 包长”，SPP 是字节流（读线程会合批/拆包），一旦出现 880/300 等切片就会永久锁不到格式、整场无帧。
   - **P1** `<44` 字节的音频切片被直接丢弃（两处），流式场景会丢字节、失步。
   - **P1** `StreamRecordPipeline.finish()` 的清理任务在解码线程延迟执行，却读写共享字段 `decoder/fileWriter`；快速“停→开”时会把新会话刚建的解码器/写入器关掉。
4. **参数/格式对齐**：绝大部分几何参数完全对齐；有两处不一致——解码器声明码率 32000，而 40B/20ms 实为 16kbps；日志 `帧周期=5004` 是 `OPUS_FRAMESIZE_20_MS` 枚举值而非毫秒，字段名有误导。

---

## 1. 日志事实核对

### 1.1 三段会话（帧数 × 20ms = 时长，均自洽）

| 会话 | 开始（首包/开始成功） | 停止命令 | 采样已解帧 | 折算时长 | 文件 |
|---|---|---|---|---|---|
| S1（日志开始前启动） | — | 09:12:14.803 | 350 帧 | 7000 ms | WY_stream_20261119_091146.pcm |
| S2 | 09:12:32.715 | 09:12:41.275 | 430 帧 | 8600 ms | WY_stream_20261119_091232.pcm |
| S3 | 09:12:44.391 | 09:12:51.661 | 360 帧 | 7200 ms | WY_stream_20261119_091244.pcm |

- 全量统计：`SPP接收音频数据…440B` 79 条，`PCM解码数据…640B` 790 条 = 79×10，**每包 10 帧一刀不差**。
- `解码失败`=0、`前向跳号/缺口`=0、`切片未产出帧`=0、`无法识别格式`=0；`锁定音频 Notify 格式` 仅 2 次（S2/S3 各一次），与“两次 start”完全对应。
- S2/S3 帧时长与墙钟误差 <0.1s，说明这两段**无丢包**。（S1 因日志从会话中途开始，无法评估其 7s/窗口 的差异。）

### 1.2 包头/帧几何实测

```
Notify(440B) = F0 F1 F2 ... F9
Fi = [4B 大端 frameIndex] + [40B Opus]
frameIndex: 0..9 → 10..19 → 20..29 ...
```

- 包首实测 `00 00 00 00` / `00 00 00 0A` / `00 00 00 14` = 大端 0 / 10 / 20 → 与 `AudioWireByteOrder.BIG_ENDIAN`、`FRAMES_PER_BURST_10` 对齐。
- 440 = 10 × (4 + 40)，与 `AudioNotifyFormat.DECA_40_440` 完全一致。
- 注意：`4B 41` / `4B 01` / `48 07` 等是**第 0 帧 Opus 负载的首字节（TOC）**，不是“魔数包头”，切帧时不能拿它当包标志。

### 1.3 控制帧实测

```
99 EC D5/D6 | 00 01 | 00 | 12 34   （8 字节）
命令码        | 长度域 | 状态 | 固定尾
```
`99 EC D5 00 01 00 12 34`（开始）、`99 EC D6 00 01 00 12 34`（停止），状态在字节下标 5（= hex 字符 10..11）。与 HS `StartStreamRecordCommand`（`99ECD5`+`000100`+`1234`，`substring(10,12)` 取状态）**完全对齐**。

### 1.4 通道归属实测

| 事件 | S2 | S3 |
|---|---|---|
| 控制 SPP（7777…） | `spp-…748094-14`（全程复用） | 同左，复用 -14 |
| 音频 SPP（8888…） | `spp-…752147-15` | `spp-…764063-16`（按会话新建） |
| 发送开始/停止命令的连接 | **-14（控制）** | **-14（控制）** |
| 音频数据到达的连接 | **-15（音频）** | **-16（音频）** |

时序：`连接音频 SPP → 控制通道发 D5 → 控制通道收 D5 ack → 音频 SPP 出流`；`控制通道发 D6 → 收 D6 ack → 断开音频 SPP`。完全符合“命令通道管开关、音频通道管流”。

---

## 2. 通道职责核对（是否“命令通道收发开始结束 / 音频通道传流”）

**结论：成立。**

代码证据：

- 开始：`WyStreamRecordSession.WyRecordCommands.start` 先 `radio.connectAudioSpp(...)`，成功后才 `radio.sendControl(CMD_START_STREAM_RECORD, ...)`（`WyStreamRecordSession.java:292-330`）。
- 停止：`WyRecordCommands.stop` 只 `radio.sendControl(CMD_STOP_STREAM_RECORD, ...)`（`:333-362`），收到 ack 后 `detach()` 再断音频。
- `sendControl` 固定发到 `controlConnectionId`（`WyClassicRadio.java:462-479`）；音频通道是独立 `AUDIO_UUID` 连接（`:311-387`）。
- 音频数据：`onSppDataReceived(audio)` → `listener.onAudioDataReceived` → `WySession` → `audioChunkConsumer` → `recordSession.onAudioChunkReceived` → `pipeline.onAudioChunk`（`WySession.java:144-152`、`WyGraph.java:54-57`）。
- 控制数据：`onSppDataReceived(control)` → `WySession.handleControlData` → `controlDataConsumer` → `recordSession.onControlDataReceived`（解析 D5/D6 ack）（`WySession.java:137-142,181-188`）。

小建议：`onAudioChunkReceived` 里保留了“音频通道夹带 99EC D6”的兜底分支（`:217-227`），当前固件 D6 走控制通道，该分支属防御性冗余；保留可以，但要用常量、且不要因此丢掉正常短帧（见 3.2）。

---

## 3. 编解码审查

### 3.1 正确的部分（可保留）

- 切帧几何：`PER_FRAME_HEADER` 步长 = 4+40 = 44，440 可整除；`framesCoveredByNotify = 440/44 = 10`（`AudioNotifyFormat.java:124-146`）。
- 字节序：WY 构造管线/装配器时传入 `BIG_ENDIAN`（`WyStreamRecordSession.java:81-86`、`StreamRecordPipeline.java:99-114`），与实测一致。
- 解码输出：`getSingleFrameOutputSize() = 16000*20/1000*2*1 = 640B`，`opus_decode(..., frame_size=320)` → 20ms/320 样本 → 640B（`HuyaDecoder.cpp:32-38,118-128`、`OpusDecoder.java:27-32`），与日志 `共640B` 一致。
- 时长：`totalPcmFrames * 20ms`（`StreamRecordPipeline.java:255`），350/430/360 → 7000/8600/7200ms，与日志一致。

### 3.2 【P1】格式判定依赖单次回调长度（SPP 合批/拆包会翻车）

- 位置：`AudioFrameAssembler.detect()` 用 `chunk.length` 走 `uniqueFormatFor(chunk.length)`（`AudioFrameAssembler.java:166-217`，尤其 `:173`）。
- 事实：SPP 是字节流。`YoCbtSppReader` 读线程会把“当次 read + available() 剩余”合成一个 batch 一次性回调（`YoCbtSppReader.java:98-172`，`dispatchBatch/drainAvailable :161-186`），**回调长度不保证等于 440**：设备突发时可能是 880/1320，慢速时可能是 300+140。
- 后果：
  - 若首包是 880：`uniqueFormatFor(880)=null`；`probeLength=880`，`formatFor(880,…)` 无任何 `notifyBytes==880` 的候选 → 永远无法锁定；缓冲累积到 `MAX_PROBE_BYTES=880` 后 `clearPending()` 反复清空（`:39-40,126-138`）→ **整场 0 帧、0 告警（只有“未锁定”静默）**。
  - 若首包被拆成 300+140：`detect()` 读的是 `chunk` 而非累计 `buffer`，第二片从帧中间开始，`readIndex` 读到的是负载随机值，可能误判/反复重取证。
- 当前为什么没炸：本次固件严格 200ms 一包、每包一次 440B 写，读线程恰好 1:1 出包；日志 79 条全是 440B。
- 修复方向（不依赖 chunk 边界）：先 `appendToBuffer(chunk)`，再在**累计缓冲区**上做几何自证锁定，例如：
  ```
  int i0 = readIndex(buffer, 0, bo);
  if (size >= 176 && readIndex(buffer,44,bo)==i0+1
                  && readIndex(buffer,88,bo)==i0+2
                  && readIndex(buffer,132,bo)==i0+3) lock DECA_40_440;
  ```
  对 `176/440/336` 这类“包长唯一”的格式同样可在缓冲头直接按步长验证。这样 880 合批、300+140 拆包都能锁。

### 3.3 【P1】`<44` 字节音频切片被丢弃（流式失步）

- 位置：`WyStreamRecordSession.onAudioChunkReceived` 开头 `if (chunk.length < 44) { … return; }`（`WyStreamRecordSession.java:213-230`，`:218`）；`AudioFrameAssembler.assemble` 开头 `if (chunk.length < SUBFRAME_44) { trace…; return empty; }`（`AudioFrameAssembler.java:114-124`），**不写入缓冲**。
- 后果：SPP 若出现 &lt;44 字节的短读（首读、被中断的读、控制/数据混排），这些字节被直接丢掉，后续流从此错位（`isPlausible` 会靠逐字节滑移“找回来”，但已丢的帧无法恢复，且可能误判）。
- 修复：**音频通道收到的任何字节都必须 `appendToBuffer` 后交给 `drain`**；控制帧判定用“长度==8 且前缀 99EC”而非 `length<44`；`<44` 只应作为“暂不足以切帧”返回 0，而不是丢弃。

### 3.4 【P2】解码器码率参数与实际帧长不对齐（并误导日志）

- 实测：`40B / 20ms` = `40*8 / 0.02 = 16000 bps`（16kbps）。`AudioNotifyFormat.DECA_40_440` 也用 `OPUS_BYTES_16K=40`、`BIT_RATE_16K=16000`（`AudioNotifyFormat.java:29-34`、`AudioFrameContract.java:27-33`）。
- 但解码器初始化：`OpusDecoder()` → `init_native(-1,0,0,640,0)`（`OpusDecoder.java:27-32`），bitRate=0 → `VOICE_OPUS_BITRATE=32000`（`HuyaDecoder.cpp:56-61`、`HuyaDecoder.h:17`）。日志 `入参码率=0 生效码率=32000`。
- 影响：Opus **解码器**不使用码率（码率是编码侧参数），故功能不受影响；但“生效码率=32000”与实际 16kbps 矛盾，排查时会误导，且未来若做 FEC/丢包补偿会按错误预期算。
- 另：`帧周期=5004` 不是 5.004 秒，而是 `OPUS_FRAMESIZE_20_MS` 的枚举值（`HuyaDecoder.h:19`，`getFramePeriod()` 返回值）。日志字段名 `帧周期=%d` 有强误导。
- 建议：解码器 bitRate 传 `BIT_RATE_16K`（或干脆不传并让日志打印“decoder 忽略码率”）；日志把 `帧周期` 改成打印 `FRAME_PERIOD_MS=20ms`，把 `5004` 标注为 `OPUS_FRAMESIZE_20_MS`。

### 3.5 【P2】解码器初始化里大量 encoder-only 控制项

`HuyaDecoder::init()`（`HuyaDecoder.cpp:84-106`）对 **decoder** 调用了 `OPUS_SET_VBR / VBR_CONSTRAINT / COMPLEXITY / INBAND_FEC / DTX / PACKET_LOSS_PERC / SIGNAL / BANDWIDTH / EXPERT_FRAME_DURATION`。这些是编码器参数，对 decoder 基本是 `OPUS_UNIMPLEMENTED` / 空操作。建议拆出 `initDecoderCtl()`，只保留 decoder 有意义的项（如需 `OPUS_SET_GAIN`/`OPUS_SET_FORCE_CHANNELS`），避免“看起来配了一堆其实没生效”。

### 3.6 【P2】解码器配置是文件级全局变量（非实例隔离）

`HuyaDecoder.cpp:10-14` 的 `mFramePeriod / mSingleFrameInputSize / mTotalFrameInputSize / mSingnleFrameOutputSize / mBiteRate` 是全局量，`initDecoder()` 每次 `new OpusDecoder()` 都改写。HS、WY 各自持有解码器但共用同一 native 库 → 多产品并存或未来多会话会互相覆盖配置。建议移入 `HuyaDecoder` 成员，或加锁并明确“单实例”约束。

### 3.7 其它对齐/清理项（P3）

- `StreamRecordPipeline.finish` 用字面量 `20L` 当帧周期（`:255`），与 `AudioFrameContract.FRAME_PERIOD_MS` 重复；应引用常量。
- `WyStreamRecordSession` 里 `<44`、`>=8` 等魔法数以常量 `AudioFrameContract.SUBFRAME_44` 表达更稳。
- WY 直接用字节数组硬编码 `CMD_START/STOP`（`WyStreamRecordSession.java:31-37`），与 HS 的 `Start/StopStreamRecordCommand` 是两份定义；建议抽到共享协议常量，避免一处改另一处忘。
- `AudioFrameContract` 类注释写“4 字节小端 dataIndex”（`:8`），但 WY 实际大端；应改为“字节序由产品声明”。
- `decode_native` 的 `encodedLength` 参数未使用（JNI 里 `(void) encodedLength`，用 `GetArrayLength`），可删或保留并注释。
- `getSingleFrameOutputSize()` 固定按 20ms 算出 640B；`opus_decode` 的 `frame_size` 上限 320，若未来固件发 40/60ms 帧会 `OPUS_BUFFER_TOO_SMALL` 丢帧。多格式族仍只支持 20ms 帧，需在契约里写明。

---

## 4. 代码设计/并发问题

### 4.1 【P1】`finish()` 清理任务与 `start()` 竞争，快速重开会让新会话失效

- 位置：`StreamRecordPipeline.finish()` 在 `decodeExecutor.execute(() -> { fileWriter.close(); fileWriter=null; decoder.release(); decoder=null; frameAssembler.reset(); …})`（`StreamRecordPipeline.java:237-289`，`:263-272`）。
- 问题：`finish()` 只把 `recording=false` 立即置位，但**释放/置空是在解码线程上延迟执行的**，且直接操作共享字段。若用户在清理任务跑完前再次 `start()`（`start()` 是 synchronized 且 `recording==false` 会通过），`start()` 会把 `decoder/fileWriter` 换成**新实例**；随后排队中的清理任务执行 `fileWriter.close(); fileWriter=null; decoder.release(); decoder=null;`，把**新会话**的解码器/写入器关掉 → 新会话静默无输出。
- 本次日志 S2→S3 间隔 2.8s，未触发；但这是可复现的竞态。
- 修复：在 `finish()` 锁内先把 `decoder/fileWriter` 捕获到局部变量并**立即置 null**，再提交清理局部变量；同时给每次 start 一个 `generation`，清理任务只作用于对应代次：
  ```
  AudioFileWriter w; AudioDecoder d;
  synchronized (this){ w = fileWriter; fileWriter=null; d = decoder; decoder=null; }
  decodeExecutor.execute(() -> { if(w!=null) w.close(); if(d!=null) d.release(); … });
  ```
  并且在 `decodeChunk` 里已按 `currentDecoder = decoder` 做空判断（`:204-207`），配合置 null 可避免新任务误用。

### 4.2 【P2】正常停止被上报成 `DISCONNECTED, failure=DEVICE_ERROR`

- 日志：每次正常停止后必现两次 `音频流 SPP 状态变更: DISCONNECTED`（一次 `failure=null`，一次 `failure=DEVICE_ERROR`，见 09:12:41.413/414、09:12:51.761）。
- 根因链：`radio.disconnectAudioSpp()` → `client.disconnectSpp` → `YoCbtSppConnection.close(true)` 置 `YO_CBT_SPP_LOCAL_CLOSED` 并以 `DISCONNECTED` 广播（`YoCbtSppConnection.java:590-624`）→ `WyClassicRadio.mapFailure()` 的 switch 未覆盖 `*_LOCAL_CLOSED / *_REMOTE_CLOSED`，落到 `default -> DEVICE_ERROR`（`WyClassicRadio.java:541-552`）。
- 影响：本产品 `WySession.handleAudioSppState` 仅打印，暂无功能影响；但任何监听音频状态的消费者都会把“主动收尾”当设备错误，属噪声/误报。
- 修复：`mapFailure` 增加 `LOCAL_CLOSED → null/CANCELLED`、`REMOTE_CLOSED → DISCONNECTED`；主动 `disconnectAudioSpp` 期间标记“主动关闭”，对 `onAudioStateChanged` 抑制 failure。

### 4.3 其它

- `handleStopResponse` 先 `radio.disconnectAudioSpp()`，随后 `reply.onReply` → `finish` → `commands.detach()` 再 `disconnectAudioSpp()`，**双重断开**（幂等但冗余，建议只保留 detach 一处）。
- `onControlDataReceived` 以“扫描 99EC + 固定 8 字节 + status=data[i+5]”匹配 ack（`:195-210`）；当前 8 字节固定帧成立，但与 HS 命令类共用协议定义更稳（避免将来加长帧时状态位错位）。

---

## 5. 参数/格式对齐矩阵

| 参数/格式 | 代码取值（来源） | 日志实测 | 结论 |
|---|---|---|---|
| 采样率 | 16000（`VOICE_OPUS_SAMPLE_RATE` / `YoOpusAudioContract.SAMPLE_RATE`） | 16000 | ✅ 对齐 |
| 声道 | 1 | 1 | ✅ |
| 帧周期 | 20ms（`FRAME_PERIOD_MS`；native `OPUS_FRAMESIZE_20_MS=5004`） | `帧周期=5004` | ⚠ 语义对齐，日志名误导 |
| 单帧 PCM | 640B（`PCM_FRAME_BYTES` = 320×2） | 640B | ✅ |
| 单帧 Opus | 40B（`OPUS_BYTES_16K`） | 40B | ✅ |
| 每包帧数 | 10（`FRAMES_PER_BURST_10`） | 10 | ✅ |
| 包长 | 440B（`NOTIFY_BURST_440`=44×10） | 440B | ✅ |
| 帧头 | 4B（`HEADER_BYTES`），每帧一个 | 4B/帧 | ✅ |
| 字节序 | BIG_ENDIAN（WY 声明） | 00/0A/14 → 0/10/20 | ✅ |
| 码率 | 解码器默认 32000 | 生效 32000；实际 40B/20ms=16000 | ❌ 不一致（解码无功能影响） |
| 时长换算 | frames×20ms | 350/430/360→7000/8600/7200 | ✅ |
| 命令帧 | 8B `99EC D5/D6 00 01 00 12 34` | 同左 | ✅（与 HS 命令类一致） |
| 状态字节 | 下标 5（`data[i+5]`） | ack status=0x00 | ✅ |

---

## 6. 修复优先级清单

| 级别 | 项 | 文件:行 |
|---|---|---|
| P1 | 格式判定改为累计缓冲几何自证，不依赖 chunk.length | `AudioFrameAssembler.java:165-217` |
| P1 | 短切片不丢弃，恒 append 后再 drain；控制帧用长度==8 判定 | `WyStreamRecordSession.java:213-230`、`AudioFrameAssembler.java:114-124` |
| P1 | `finish()` 清理捕获局部变量并置空 + 代次保护 | `StreamRecordPipeline.java:237-289` |
| P2 | `mapFailure` 补 LOCAL/REMOTE_CLOSED；主动关闭抑制 failure | `WyClassicRadio.java:541-552` |
| P2 | 解码器 bitRate 与实际帧长对齐；日志打印 20ms 而非 5004 | `OpusDecoder.java:27-32`、`HuyaDecoder.cpp:47-52` |
| P2 | 拆分 decoder-only 初始化，去掉 encoder-only ctl | `HuyaDecoder.cpp:84-106` |
| P2 | 解码器配置改为实例成员 | `HuyaDecoder.cpp:10-14` |
| P3 | 魔法数/时长用常量；WY 命令常量与 HS 统一；修正注释“小端” | 多处 |
| P3 | 去掉重复 `disconnectAudioSpp` | `WyStreamRecordSession.java:258-271,364-373` |

---

## 7. 一句话总评

通道划分与命令/数据分离做得对，编解码在当前固件上实测干净（0 失败/0 缺口）；真正需要加固的是**“流式输入不能假设回调边界等于协议包边界”**（格式判定 + 短片缓冲）与**“停止清理和重启的资源生命周期串行化”**这两处设计假设——它们在本机现网节拍下被掩盖，换固件/换传输节奏就会暴露。
