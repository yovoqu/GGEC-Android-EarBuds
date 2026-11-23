# 随录随传音频链路架构重构方案（执行计划）

> 依据：`review-wy-streamrecord-logcat.md` + 设备端录音参数（16 kHz / 16 bit / 单声道 / 20 ms；PCM 640 B/帧 = 256 kbps；Opus 40 B/帧 = 16 kbps；压缩比 16:1）。
> 目标：从架构上消除“传输回调边界 == 协议包边界”的错误假设，串行化资源生命周期，统一参数单一信源，删除冗余与魔法数，并保持 HS 现有行为不变。

## 0. 不可破坏的约束

- lib_ggec_audio 边界门禁（`checkLibGgecAudioBoundary`）：
  - `src/**` 只允许 `import com.yovo.lib_ggec_opus.api.**` / `com.yovo.lib_ggec_opus.YoOpusSdk` 之外的 com.yovo 依赖；禁止 `internal.**`。
  - 任何一行（含注释）不得出现 `modelId` `projectId` `deviceType` `HS0` `WY0`。
  - 禁止 `android.util.Log`、`printStackTrace`、`catch(Throwable|Exception ignored)`。
- lib_ggec_opus 边界门禁：`api/**` 不得 import `internal/**` 或 `com.bes.opus`；vendored 只能依赖 `api/**`；`com.bes.opus` 只允许出现在 `src/vendored/java`。
- biz_ggec_wy / biz_ggec_hs 边界门禁：`api/**` 只允许 java / androidx.annotation / 本模块 api / `com.yovo.lib_ggec_audio.record.**`；`api|internal` 不得 import `lib_bluetooth` / `android.bluetooth`；非 provider 的 main 代码不得 import `lib_bluetooth` / `lib_ggec_opus` / `lib_common`；`provider/lib_bluetooth` 不得 import `lib_common`（除 log 缝）。
- 保持 `AudioFrameAssembler` / `StreamRecordPipeline` 既有 public 构造器与方法签名（HS 与现有测试依赖），可新增。
- 不改动 `AudioFrameContract.SAMPLE_RATE/CHANNEL_COUNT/FRAME_PERIOD_MS/PCM_*/OPUS_BYTES_*` 的现有取值。
- 不执行 `git commit`。

## 1. 目标架构

```
[SPP 字节流]  (任意分片/合批，不代表协议帧边界)
      │
      ▼
产品适配层 (biz_ggec_wy.WyStreamRecordSession / biz_ggec_hs.HsStreamRecordSession)
  - 仅做“控制帧 vs 音频字节”判别（精确 8B 控制帧前缀），其余字节原样透传
      │  onAudioChunk(byte[])
      ▼
lib_ggec_audio.StreamRecordPipeline
  - 每会话一个 Session{Assembler, Decoder, Writer, Listener, Config, Commands, generation}
  - start/stop 只操作当前 Session；旧会话清理只触及旧对象
      │
      ▼
lib_ggec_audio.AudioFrameAssembler
  - 纯字节流组帧：始终 append → 未锁定则从“累计缓冲”几何自证锁定 → 按步长切帧
  - 与 chunk 长度无关；允许前导垃圾字节通过滑动重同步剔除
      │  List<byte[]> (每帧 40B Opus)
      ▼
AudioDecoder (lib_ggec_opus: 16 kHz/单声道/20 ms，wire bitrate 16 kbps)
```

## 2. 工作流拆分（写作用域互斥）

### WS-A `lib_ggec_audio`（唯一 owner）
文件：
- `src/main/java/com/yovo/lib_ggec_audio/AudioFrameAssembler.java`
- `src/main/java/com/yovo/lib_ggec_audio/AudioFormatDetector.java`
- `src/main/java/com/yovo/lib_ggec_audio/AudioFrameContract.java`（仅新增常量/方法）
- `src/main/java/com/yovo/lib_ggec_audio/record/StreamRecordPipeline.java`（生命周期重构）
- 对应测试：`src/test/java/com/yovo/lib_ggec_audio/**`

任务：
1. **A1 组帧与检测解耦**
   - `assemble(byte[])`：`null`/空 → `trace.onFormatUnresolved(0,size)` 返回空；否则**无条件** `appendToBuffer(chunk)`。
   - 未锁定：`AudioFormatDetector.scanStream(buffer, size, byteOrder)` 返回 `StreamLock{format, startOffset}`；命中则 `consumeBufferHead(startOffset)`、`lockedFormat=...`、`indexTracker.setFramesPerNotify(...)`、`trace.onFormatLocked`。未命中且 `size > MAX_PROBE_BYTES` → `clearPending()` 并 trace。
   - 保留快路径：先 `uniqueFormatFor(chunk.length)`（保持 176→QUAD_40_176、440→DECA_40_440 等既有枚举断言）；不命中再走 `scanStream`。
   - 锁定后 `MTU_SPLIT` 走原专用状态机；其它按 `getFrameStrideBytes()` 切帧。
   - 删除未使用的 1 参 `drainPerFrameHeaderLayout(AudioNotifyFormat)` 私有重载。
   - `MAX_PROBE_BYTES` 提高（如 `NOTIFY_BURST_440 * 8`）；缓冲容量≥16 KiB，保证合批/探测都不丢头。
2. **A2 `AudioFormatDetector.scanStream`（核心算法，须精确实现）**
   - 输入：累计缓冲 `buf[0..size)`、字节序。
   - 对候选起始偏移 `p = 0..MAX_SKIP(=64)` 且 `p + 最小校验长度 <= size`：
     - `idx = readIndex(buf,p,bo)`，要求 `idx >= STARTING_DATA_INDEX`。
     - 按确认强度依序验证（每条要求足够字节）：
       - **stride 44（DECA_40_440，可同时代表 QUAD_40_176 的 40B 子帧）**：`readIndex(p+44)==idx+1 && readIndex(p+88)==idx+2 && readIndex(p+132)==idx+3`。
       - **stride 84（DUAL_HALF_168）**：`readIndex(p+84)==idx+1 && readIndex(p+168)==idx+2`。
       - **stride 164（FULL_FRAME / MTU_SPLIT 统一 164 子帧）**：`readIndex(p+164)==idx+1` 且（若 ≥ p+328）`readIndex(p+328)==idx+2`。
       - **stride 168（歧义消解）**：若 `readIndex(p+168)==idx+1` → `STANDARD_LEGACY`；若 `readIndex(p+168)==idx+4` → `QUAD_40_168`（要求 8B 头布局）。
     - 命中多个时取“确认次数最多/步长最小且全匹配”的一个；同强度按 44 → 84 → 164 → 168 优先。
   - 返回 `null` 表示证据不足（等更多数据）。禁止使用 `chunk.length` 选格式。
   - 新增包内可见 `static final class StreamLock`。
3. **A3 管线生命周期**
   - 引入私有 `Session` 持有：`generation, AudioFrameAssembler, AudioDecoder, AudioFileWriter, File targetFile, StreamRecordListener, StreamRecordConfig, Commands, long totalPcmFrames, boolean closed`。
   - `start`：同步；`current != null` → `listener.onError("录音已在进行中")` 返回；创建新 Session（**每会话独立 Assembler**，构造时传同一 `byteOrder`/trace），`current=session`，再 `commands.start(...)`。成功回调需校验 `current==session` 后 `listener.onStarted`；失败 → `finish(session,...)`。
   - `onAudioChunk`：捕获 `Session s=current`；空则返回；`decodeExecutor.execute(() -> decodeChunk(s, chunk))`。
   - `decodeChunk(s,chunk)`：`if(s.closed) return;` 用 `s.assembler` 解包、`s.decoder` 解码、累计 `s.totalPcmFrames`、`s.config/s.listener` 分流、`s.writer` 落盘。
   - `stop`：同步捕获 `s`；无则回调 null；否则 `s.commands.stop(f -> finish(s, callback, f, f==null?null:ERROR_STOP_FAILED))`。
   - `stopByDevice`：`finish(current,null,null,null)`。
   - `finish(s,callback,failure,errorMessage)`：同步；`s!=current` 或已 closed → 回调 null 返回；`s.closed=true; current=null;` 计算 `duration=s.totalPcmFrames*AudioFrameContract.FRAME_PERIOD_MS`（<=0 时用墙钟），日志；`decodeExecutor.execute(() -> { close writer; release decoder; })`；`dispatcher.post(() -> { onError?; onFileSaved?; onStopped; callback; })`。
   - `release`：finish 当前会话 + `decodeExecutor.shutdown()`。
   - 保持 `Dispatcher`/`Commands`/`Reply`/`StopCallback` 接口与 7 参/8 参构造器不变。
4. **A4 测试**（新增在 `AudioFrameAssemblerTest` / `StreamRecordPipelineTest`）
   - 合批：`assemble(deca440(0)) + assemble(deca440(10))` 两个连续包合批成一个 880 字节 `assemble()` → 20 帧，锁定 DECA。
   - 拆包：一个 440 包拆成 300 + 140 两次 `assemble()` → 10 帧，顺序正确。
   - 前导垃圾：8 字节控制帧 + 440 数据 → 丢弃前导、10 帧。
   - 短切片：单独 5 字节返回空但 `pendingBytes()==5`，随后接 168 包可正确锁定并出帧（**更新**原 `rejectsSubMinimumChunkWithoutPollutingState` 断言）。
   - 回归：所有既有用例（含小端默认、176/440/168/MTU/336/164）保持通过。
   - 管线快重启：stop 后立即 start，旧 decoder 被释放、新 decoder 未被释放，且新会话可解码（用可控 Executor 或延时竞态断言）。

### WS-B `lib_ggec_opus`
文件：
- `src/main/java/com/yovo/lib_ggec_opus/api/YoOpusAudioContract.java`
- `src/main/java/com/yovo/lib_ggec_opus/../vendored/java/com/bes/opus/OpusDecoder.java`
- `src/main/java/com/yovo/lib_ggec_opus/../vendored/java/com/bes/opus/OpusUtils.java`
- `src/main/cpp/opus/HuyaDecoder.cpp` / `include/HuyaDecoder.h`
- `src/test/java/com/yovo/lib_ggec_opus/**`

任务：
1. `YoOpusAudioContract` 新增：
   - `OPUS_BIT_RATE_16K = OPUS_BYTES_16K * 8 * 1000 / FRAME_PERIOD_MS`（=16000）
   - `OPUS_BIT_RATE_32K` / `OPUS_BIT_RATE_64K` 同理；`PCM_BIT_RATE = PCM_FRAME_BYTES*8*1000/FRAME_PERIOD_MS`（=256000）。
   - `WIRE_OPUS_BIT_RATE` 取 16K；`COMPRESSION_RATIO = PCM_BIT_RATE / WIRE_OPUS_BIT_RATE`（=16）。
   - 保留 `BIT_RATE_DEFAULT=32000` 等既有常量不变（其它代码/测试依赖）。
2. `OpusDecoder()` 构造：`init_native(-1, 0, 0, YoOpusAudioContract.PCM_FRAME_BYTES, YoOpusAudioContract.WIRE_OPUS_BIT_RATE)`，让声明码率与 40B/20ms 一致。
3. `HuyaDecoder.cpp`：
   - 初始化日志把“帧周期”改为打印 `VOICE_FRAME_PERIOD_IN_MS`（20ms）并单独打印枚举 `OPUS_FRAMESIZE_20_MS`（5004），消除 5004 的误导。
   - `init()` 只保留 decoder 有意义的 ctl（如 `OPUS_SET_FORCE_CHANNELS` / `OPUS_GET_LOOKAHEAD`）；删除 encoder-only 的 `OPUS_SET_VBR/VBR_CONSTRAINT/COMPLEXITY/INBAND_FEC/DTX/PACKET_LOSS_PERC/SIGNAL/BANDWIDTH/EXPERT_FRAME_DURATION`（或集中到一个不调用的 `applyEncoderOnlyControls` 注释块）。
   - 全局配置变量处加注释说明“进程内单配置”，不改行为。
4. `OpusUtils.Shorts2Bytes` 固定小端（`getBytes(s, false)`），使 PCM 落盘字节序与 `PcmAmplitude`/文件约定一致（不依赖 CPU 端序探测）。
5. 测试：`YoOpusAudioContractTest` 断言 40B→16000、640B→256000、压缩比 16:1；`OpusUtils` 端序测试。
6. 尽量跑 `:lib_ggec_opus:testDebugUnitTest`；C++ 改动用 `:lib_ggec_opus:assembleDebug`（或等效 native 任务）编译验证（可仅 x86_64）。

### WS-C `biz_ggec_wy`（依赖 WS-A 完成后）
文件：
- `src/main/java/com/yovo/biz_ggec_wy/internal/record/WyStreamRecordSession.java`
- 新增 `src/main/java/com/yovo/biz_ggec_wy/internal/record/WyStreamControlFrames.java`
- `src/main/java/com/yovo/biz_ggec_wy/provider/lib_bluetooth/WyClassicRadio.java`
- 测试：`src/test/java/com/yovo/biz_ggec_wy/**`

任务：
1. **C1 控制帧常量单一来源**：新建 `WyStreamControlFrames`（包内可见）：
   - `START`/`STOP` 8 字节常量；`FRAME_LEN=8`；`isStopFrame(byte[])`、`isAnyControlFrame(byte[])`、`statusAt(byte[],int)`。
   - `WyStreamRecordSession` 删除内联字节数组，全部改用该类。
2. **C2 音频入口不再丢短切片**：
   ```
   if (!pipeline.isRecording()) return;
   if (WyStreamControlFrames.isStopFrame(chunk)) { log; pipeline.stopByDevice(); return; }
   if (WyStreamControlFrames.isAnyControlFrame(chunk)) { log; return; } // 防御：控制帧不进音频管线
   pipeline.onAudioChunk(chunk); // 任意长度（含 <44）都交给装配器按流处理
   ```
   删除 `chunk.length < 44 { return; }` 与硬编码 44。
3. **C3 去重复断开**：`handleStartResponse`/`handleStopResponse` 中不再显式 `radio.disconnectAudioSpp()`；统一由 `pipeline.finish → commands.detach()` 断音频（保证一次）。
4. **C4 失败映射补全**：`WyClassicRadio.mapFailure` 增加 `SPP_LOCAL_CLOSED`、`SPP_REMOTE_CLOSED` → `WyControl.Failure.DISCONNECTED`（其余保持）。
5. **C5 主动关闭不报错**：`WyClassicRadio` 增加 `audioCloseIntended`（锁内）：
   - `connectAudioSpp` 起始置 false；`disconnectAudioSpp` 置 true。
   - 音频观察者：若 `audioCloseIntended` 且状态为 `DISCONNECTED/FAILED`，对 `listener.onAudioStateChanged` 传 `failure=null`；新连接建立后复位。
   - 目标：日志不再出现“正常停止 → DISCONNECTED, failure=DEVICE_ERROR”。
6. **C6 测试**：
   - `WyStreamRecordSessionTest`：新增“合批 880 字节 → 20 帧”“拆包 300+140 → 10 帧”“8B 控制帧不进管线”“短音频字节不丢”。
   - `WyClassicRadioTest`：新增/调整“本地主动断开音频通道不产生 DEVICE_ERROR”；修正因 mapFailure 改动受影响的断言。
   - 跑 `:biz_ggec_wy:testDebugUnitTest` 与 `checkBizGgecWyBoundary`。

### WS-D 验证与评审（Lead + Reviewer）
- 全量：`:lib_ggec_audio:testDebugUnitTest :lib_ggec_opus:testDebugUnitTest :biz_ggec_wy:testDebugUnitTest :biz_ggec_hs:testDebugUnitTest`
- 门禁：四个 `checkXxxBoundary`。
- 编译：`:lib_ggec_audio:assembleDebug :lib_ggec_opus:assembleDebug :biz_ggec_wy:assembleDebug :biz_ggec_hs:assembleDebug`（按需）。
- 产出 `docs/architecture/2026-11-refactor/stream-record-refactor.md`（新架构、 invariants、变更点、测试矩阵、遗留风险）。
- 独立 Reviewer 只读复审：竞态、边界门禁、HS 回归、参数对齐。

## 3. 禁止事项
- 不要改 `AudioFrameContract` 现有常量值；不要改 `AudioFrameAssembler` 构造器语义（默认小端）。
- 不要删 `AudioNotifyFormat` 任何枚举（HS 可能用到）。
- 不要把产品协议常量下沉到 `lib_ggec_audio`（保持产品无关）。
