# 语音通话：各部分状态机架构

> 本文按"每一部分一条状态机"组织：设备双工、采集、播放、链路、对话、打断、UI 投影。
> 每条状态机给出：状态、事件、转移、不变量、落点代码。
> 所有厂商差异都来自实测文档，不是猜的（§0 列出出处）。

---

## 0. 调研依据（可核对）

| 来源 | 关键事实 |
|---|---|
| 阿里云百炼《客户端事件》`help.aliyun.com/zh/model-studio/client-events` | 通义 `turn_detection` 只有 `type`（`server_vad`/`semantic_vad`/`semantic_vad_ec`）、`threshold`（**[-1.0, 1.0]**，默认 0.5）、`silence_duration_ms`（[200,6000]，默认 800）、`idle_timeout_ms`（[5000,30000]）；**没有** `create_response`/`interrupt_response`/`prefix_padding_ms`/`eagerness` |
| 同上，《response.cancel》 | "若当前无响应可取消，服务端将返回错误事件" → **没有活动响应时不能发 cancel** |
| 同上，《session.update audio.input.format》 | Qwen3.8 omni flash realtime 输入默认 **16000 Hz**；多通道只允许 16k |
| 同上，《WebSocket 接入指南》 | VAD 模式：`speech_started` → `speech_stopped` → `committed` → `conversation.item.created`；Manual 模式：`turn_detection: null` + `input_audio_buffer.commit` + `response.create` |
| OpenAI《Realtime VAD》`developers.openai.com/api/docs/guides/realtime-vad` | `server_vad`：`threshold` [0,1]、`prefix_padding_ms`、`silence_duration_ms`、`create_response`、`interrupt_response`；`semantic_vad`：`eagerness` |
| OpenAI《Realtime conversations》 | WebSocket 下打断由客户端负责：`speech_started` 立即停播 → 记录已播毫秒 → `conversation.item.truncate(item_id, content_index, audio_end_ms)` → `response.done(status=cancelled)`；WebRTC 由服务端管理播放缓冲 |
| LiveKit Agents `audio_recognition.py` | **播放态与生成态是两条独立生命周期**：`_on_start_of_agent_speech` / `_on_end_of_agent_speech` 跟踪"可听播放"，打断决策同时看 VAD、播放态和生成态 |
| Android `AcousticEchoCanceler` | AEC 需要 `VOICE_COMMUNICATION` 音源 + 同一 AudioRecord session；播放侧必须 `USAGE_VOICE_COMMUNICATION` + `MODE_IN_COMMUNICATION` 提供参考信号 |

---

## 1. 分层总览

```
┌────────────────────────────────────────────────────────────────┐
│ L5 UI：AiCallScreen / 光晕 / 转写                                │
│    只消费 VoiceStatusModel / VoiceMeter / Transcript 三条流      │
├────────────────────────────────────────────────────────────────┤
│ L4 对话状态机 VoiceCallReducer（纯函数）                          │
│    IDLE/CONNECTING/LISTENING/THINKING/SPEAKING/DRAINING/MUTED    │
├────────────────────────────────────────────────────────────────┤
│ L3 打断状态机（由 reducer 的 StopAssistant 效果表达）             │
│    是否允许打断 × 是否还有生成 × 已播毫秒                        │
├────────────────────────────────────────────────────────────────┤
│ L2 端口：AiAudioCapture / AiAudioPlayback / AiRealtimeLink       │
├────────────────────────────────────────────────────────────────┤
│ L1 平台：AndroidAudioCapture / AndroidAudioPlayback              │
│         RealtimeLinkAdapter → lib_ai（按方言编码）               │
└────────────────────────────────────────────────────────────────┘
```

**边界原则**：L4 只产数据（state + effects），不知道线程、Android、厂商；L1/L2 只管设备与协议，不做业务决策。厂商差异收在 `lib_ai` 的方言层（§8），不向上泄漏。

---

## 2. A. 设备双工状态机（VoiceDuplex）

这是整条链路的第一性约束：**有没有回声消除，决定能不能在助手说话时开麦**。

### 状态

| 状态 | 含义 |
|---|---|
| `UNRESOLVED` | 会话还没打开采集，不知道设备能力（装配根请求值不算事实） |
| `HALF_DUPLEX` | 助手发声期间必须关上行 |
| `FULL_DUPLEX` | 助手发声期间保持上行，允许语音打断 |

### 事件

`StartCapture 完成`（附带 `echoCancellationActive()` 事实）、`Stop`。

### 转移

| 从 | 事件 | 守卫 | 到 | 副作用 |
|---|---|---|---|---|
| UNRESOLVED | 采集已启动 | 私密路由（耳机/蓝牙/USB）或 `AEC=true` | FULL_DUPLEX | 上行闸门按 FULL 规则重算 |
| UNRESOLVED | 采集已启动 | 其它 | HALF_DUPLEX | 上行闸门按 HALF 规则重算 |
| 任意 | Stop | — | 不变 | 不产生新的设备动作 |

### 不变量

1. **只降不猜**：请求 FULL 但 AEC 没挂上 → 必落 HALF；
2. 双工只影响"助手发声期间是否上行"，不影响静音（静音永远关上行）；
3. 双工是**事实**（AEC 是否 attach 成功），不是用户设置。

### 落点

`domain/ai/voice/VoiceDuplex.java`、`VoiceCallEvent.DuplexResolved`、`RealtimeVoiceSession.execute(StartCapture)`、`AndroidAudioCapture.echoCancellationActive()`。

---

## 3. B. 采集状态机（AiAudioCapture）

### 状态

| 状态 | 含义 | 采集线程 | 回调 PCM |
|---|---|---|---|
| `IDLE` | 未启动 | 无 | — |
| `STARTING` | 正在打开 AudioRecord / 挂 AEC | 无 | — |
| `RUNNING` | 常开，上行开启 | 有 | 有效帧 |
| `GATED` | 常开，上行关闭（半双工自听 / 静音） | 有 | **空数组**（电平仍有效） |
| `CLOSING` | 正在 stop/join/release | 退出中 | — |
| `FAILED` | 权限/设备失败 | 无 | `onFailure` |

### 事件

`start(rate)`、`setUplinkEnabled(bool)`、`stop()`、读线程异常、`releaseRecord()`。

### 转移

| 从 | 事件 | 到 | 副作用 |
|---|---|---|---|
| IDLE | start | STARTING | 检查权限 → 打开 AudioRecord → 挂 AEC |
| STARTING | 成功 | RUNNING | 起读线程 |
| STARTING | 无权限/设备忙 | FAILED | `onFailure(kind)` |
| RUNNING | setUplinkEnabled(false) | GATED | 回调改送空 PCM，**电平照算** |
| GATED | setUplinkEnabled(true) | RUNNING | 恢复送 PCM |
| RUNNING/GATED | stop | CLOSING | stop() 唤醒 read；join；release；释放 AEC |
| 任意 | 读异常 | FAILED | 读线程自行 release，再 `onFailure` |

### 不变量

1. **电平与上行解耦**：GATED 仍有电平，否则光晕会"死掉"；
2. `start` 幂等：重复 start 不叠第二条读线程（两条线程抢同一个 AudioRecord 会互相截断）；
3. 释放顺序固定：`stop() → join → release AudioRecord → release AEC`；反过来会踩已释放 session；
4. 读线程抛错不能穿到调用方（用 `onFailure`）。

### 落点

`platform/ai/voice/AndroidAudioCapture.java`、`domain/ai/port/AiAudioCapture.java`。

---

## 4. C. 播放状态机（AiAudioPlayback）

### 状态

| 状态 | 含义 |
|---|---|
| `IDLE` | 未打开 AudioTrack |
| `OPEN` | AudioTrack 已 play，队列空 |
| `QUEUED/PLAYING` | 队列非空，排空线程在写 |
| `FLUSHING` | 丢弃队列、重置播放头 |
| `STOPPED` | 已 release |
| `FAILED` | AudioTrack 初始化/写入失败 |

### 事件

`start(rate)`、`write(pcm, itemId, contentIndex)`、`flush()`、`captureHeard()`、`stop()`、写入返回负值。

### 转移

| 从 | 事件 | 到 | 副作用 |
|---|---|---|---|
| IDLE | start | OPEN | 建 AudioTrack(USAGE_VOICE_COMMUNICATION/MODE_IN_COMMUNICATION) |
| OPEN | write | PLAYING | 入队；记录 itemId/contentIndex |
| PLAYING | 写出一块 | PLAYING | **累计 playedBytes**（只算真正写进设备的） |
| PLAYING | flush | OPEN | 清队列；pause→flush→play |
| PLAYING/OPEN | captureHeard | 不变 | 返回 `Heard(itemId, contentIndex, elapsedMs)` 并清零 |
| 任意 | stop | STOPPED | 停写线程、release AudioTrack、还原 AudioManager 模式 |
| 任意 | write 返回 <0 | FAILED | 释放并退出 |

### 不变量

1. **只有真正写进 AudioTrack 的字节才算"用户听到"**：队列里/还没写的不能算，否则截断毫秒偏大，服务端会以为用户听过后半句；
2. `captureHeard` 取一次就清零：打断只该截断一次，按钮与 VAD 同时触发也只截一次；
3. 换 item 时进度归零：不能把上一条的毫秒加到下一条；
4. `write` 不阻塞状态机线程（独立排空线程），否则打断要排在几秒音频之后才生效。

### 落点

`platform/ai/voice/AndroidAudioPlayback.java`、`domain/ai/port/AiAudioPlayback.java`。

---

## 5. D. 链路会话状态机（lib_ai）

### 状态

| 状态 | 含义 |
|---|---|
| `NEW` | 未 open |
| `CONNECTING` | WebSocket 握手中 |
| `OPEN` | 会话已建立（Qwen 连上即放行；GA 等 `session.updated`） |
| `CLOSING` | 本地 close 中 |
| `CLOSED` | 已关闭（本地或对端） |
| `FAILED` | 握手/协议/网络错误 |

### 转移

| 从 | 事件 | 到 |
|---|---|---|
| NEW | open | CONNECTING |
| CONNECTING | `session.created/updated`（按方言） | OPEN |
| CONNECTING | error/close | FAILED |
| OPEN | 对端 close | CLOSED |
| OPEN | 本地 close | CLOSING → CLOSED |
| 任意 | 本地重复 close | 空操作（幂等） |

### 不变量

1. `close()` 幂等：挂断可能从页面、系统回收、超时、网络断开多处并发触发；
2. `open()` 只能一次；重连要新实例，避免旧收尾事件串到新会话；
3. 断线不自动重连：只折成一次 `Failed` + `Closed`，是否重连是上层的事（它才知道是不是用户自己挂的）。

### 落点

`lib_ai/realtime/RealtimeWebSocketLink.java`、`YoRealtimeSession`、`YoRealtime.Connection`。

---

## 6. E. 对话状态机（VoiceCallReducer，核心）

### 状态

| 状态 | 含义 | 上行 | 可打断 |
|---|---|---|---|
| `IDLE` | 未开始 | 关 | 否 |
| `CONNECTING` | 链路握手中 | 关 | 否 |
| `LISTENING` | 麦克风在听 | **开** | — |
| `THINKING` | 用户说完，服务端在生成 | 半双公关 | 是 |
| `SPEAKING` | 正在播下行音频 | 半双公关 | 是 |
| `DRAINING` | 生成完但本地还没播完 | 半双公关 | 是 |
| `MUTED` | 用户静音 | 关 | 是 |
| `FAILED` | 终态 | 关 | 否 |
| `ENDED` | 终态 | 关 | 否 |

> `DRAINING` 是**内部状态**，对外投影为 `SPEAKING`：用户视觉上"还在说话"，但状态机已经禁止开麦。

### 事件

`Start / Opened / DuplexResolved / SpeechStarted / SpeechStopped / AssistantText / AssistantAudio / ResponseDone / PlaybackDrained / CaptureFrame / Muted / Interrupt / Stop / Failed`

### 转移表

| 从 | 事件 | 守卫 | 到 | 副作用 |
|---|---|---|---|---|
| IDLE | Start | — | CONNECTING | Publish |
| CONNECTING | Opened | — | LISTENING | StartCapture, SetUplink(true), Publish |
| LISTENING | SpeechStarted | — | LISTENING | 无（不 cancel） |
| LISTENING | AssistantText | — | THINKING | Publish, 闸门重算 |
| THINKING | AssistantAudio | — | SPEAKING | StartPlayback, PlayAudio, Publish |
| SPEAKING | AssistantAudio | — | SPEAKING | PlayAudio |
| SPEAKING | ResponseDone | 有音频 | DRAINING | ScheduleDrain(音频时长+120ms), Publish |
| THINKING | ResponseDone | 无音频 | LISTENING | Publish, 开闸 |
| DRAINING | PlaybackDrained | — | LISTENING | Publish, 开闸 |
| THINKING/SPEAKING/DRAINING | SpeechStarted | **全双工** | LISTENING | **StopAssistant(cancel=true)**，Publish |
| THINKING/SPEAKING/DRAINING | SpeechStarted | **半双工** | 不变 | **忽略**（回声） |
| 任意 | Interrupt | assistantActive | LISTENING | StopAssistant(cancel = 是否还在生成) |
| LISTENING/MUTED | Muted(bool) | — | MUTED/LISTENING | SetUplink, Publish |
| LISTENING | SpeechStopped | — | THINKING | Publish, 关闸 |
| 任意非终态 | Stop | — | ENDED | StopCapture, StopPlayback, CloseLink, Publish |
| 任意非终态 | Failed（首个） | — | FAILED | 同上 |
| 终态 | 任意 | — | 不变 | 无 |

### 不变量

1. 半双工下 `THINKING/SPEAKING/DRAINING` **上行必关**；
2. 闸门关闭期间收到的 `SpeechStarted` **一律忽略**：那只能是回声；
3. `ResponseDone` 有音频必进 `DRAINING`，`PlaybackDrained` 才回 `LISTENING`；
4. 终态幂等；迟到事件静默；
5. 所有字段只在 actor 单线程改，快照只从它发。

### 落点

`domain/ai/voice/VoiceCallReducer.java`、`data/ai/voice/RealtimeVoiceSession.java`。

---

## 7. F. 打断状态机（StopAssistant）

打断不是一个布尔值，而是一组有先后依赖的动作。

### 子状态

| 子状态 | 条件 |
|---|---|
| `NOT_ACTIVE` | 当前没有助手发言（LISTENING） |
| `ARMED` | 有助手发言（THINKING/SPEAKING/DRAINING） |
| `SUPPRESSED` | ARMED 且半双工 → 回声不可能打断 |
| `COMMITTED` | 决定打断，执行 StopAssistant |

### 转移

| 从 | 事件 | 守卫 | 到 | 动作 |
|---|---|---|---|---|
| NOT_ACTIVE | SpeechStarted/Interrupt | — | NOT_ACTIVE | 无 |
| ARMED | SpeechStarted | 半双工 | SUPPRESSED | 无（忽略回声） |
| ARMED | SpeechStarted | 全双工 | COMMITTED | 执行 |
| ARMED | Interrupt（按钮） | — | COMMITTED | 执行 |
| COMMITTED | — | — | NOT_ACTIVE | 见下 |

### COMMITTED 的固定顺序（不可换序）

```
1. heard = playback.captureHeard()      // 先取进度，此时队列还没清
2. playback.flush()                     // 清空队列
3. link.send(Clear)                     // 丢掉未提交的上行
4. if (还有生成) link.send(Cancel)      // 通义：没有活动响应时 cancel 会回错误
5. if (heard != null) link.truncate(heard.itemId, heard.contentIndex, heard.elapsedMs)
```

### 不变量

1. **先取进度再 flush**：反过来只剩 0ms，服务端会以为用户什么都没听到；
2. **只在还有生成时 cancel**：`generating()` = THINKING/SPEAKING；DRAINING 表示生成已结束；
3. **截断只在有 itemId 且 elapsedMs>0 时发**：老网关不给 itemId，不能瞎截；
4. 任何一步失败不阻断后续（`bestEffort`），本地已经回聆听。

### 落点

`VoiceCallEffect.StopAssistant`、`RealtimeVoiceSession.stopAssistant()`、`AiRealtimeLink.truncate()`、`AiAudioPlayback.captureHeard()`。

---

## 8. 方言隔离（高内聚、低耦合的关键）

### 问题

OpenAI 与通义的 `turn_detection` 是**两套 schema**，不是同一 schema 的方言变体：

| 字段 | OpenAI | 通义 |
|---|---|---|
| `threshold` | [0, 1] | **[-1.0, 1.0]** |
| `prefix_padding_ms` | 有 | **无** |
| `silence_duration_ms` | 有 | 有（[200,6000]，默认 800） |
| `create_response` | 有 | **无** |
| `interrupt_response` | 有 | **无** |
| `eagerness` | 有（semantic_vad） | **无** |
| `idle_timeout_ms` | 无 | 有（[5000,30000]） |
| `type` 取值 | server_vad / semantic_vad | server_vad / semantic_vad / **semantic_vad_ec** |

### 设计：密封类型 + 构造期校验

```java
sealed interface YoRealtimeTurnDetection {
    record Disabled() ...
    record OpenAi(Kind kind, Double threshold, Integer prefixPaddingMs,
                  Integer silenceDurationMs, String eagerness,
                  Boolean createResponse, Boolean interruptResponse) ...
    record Qwen(String type, Double threshold,
                Integer silenceDurationMs, Integer idleTimeoutMs) ...
}
```

- 每个变体只持有自己的字段，**各自校验取值范围**（Qwen 的 `threshold` 允许负数，OpenAI 不允许）；
- 编码按变体分派：`encodeOpenAiTurn` / `encodeQwenTurn`，**没有 `boolean qwen` 开关**；
- `YoRealtimeConfig.build()` 校验**变体与方言匹配**：把 `OpenAi` 发给通义、或把 `Qwen` 发给 OpenAI，在构造期抛 `IllegalArgumentException`，而不是等服务器 400。

### 上行线格式也由方言决定

| 方言 | 上行线速率 | 来源 |
|---|---|---|
| 通义 Qwen | **16 kHz** | 百炼文档：3.8 多通道只能 16k，默认 16k |
| OpenAI | 24 kHz | OpenAI pcm16 按 24k 解释 |

`AiRealtimeLink.inputWireSampleRateHz()` 由适配器按方言返回；会话只有"线速率 == 采集 16k"才跳过重采样，否则 16k→24k。**不把 16k 的字节按 24k 送**（会音调变低、语速变慢）。

### 落点

`lib_ai/realtime/YoRealtimeTurnDetection.java`、`RealtimeProtocol.encodeTurn/encodeOpenAiTurn/encodeQwenTurn`、`YoRealtimeConfig.requireTurnDetectionMatchesDialect`、`RealtimeLinkAdapter`、`AiRealtimeLink.inputWireSampleRateHz`。

---

## 9. G. UI 投影状态机

### 三条独立流（不共享相等性）

| 流 | 内容 | 更新频率 | 相等性 |
|---|---|---|---|
| `VoiceStatusModel` | phase/title/action/live/interruptible/muted/speaker/ended | 只在状态变化 | record equals |
| `VoiceMeter` | 0..1 电平 | 50ms 节流或变化≥0.05 | float |
| `Transcript` | 多轮文本 | 文本变化时 | String |

### 为什么分开

- 电平每帧变；如果并进状态记录，`equals` 永远不等，整页每帧重建；
- 转写每 token 变；并进状态会触发全页 render；
- 分开之后，光晕/文本只更新自己的 View，状态动画不被高频流打断。

### 落点

`ui/ai/call/AiCallViewModel.java`（`publishSnapshot`）、`AiCallScreen/AiCallStageView.setLevel/setTranscript`。

---

## 10. H. 错误与终态

| 错误来源 | 归类 | 处理 |
|---|---|---|
| 采集权限/设备 | `PERMISSION`/`UNSUPPORTED_CAPABILITY` | FAILED，页面给"去设置" |
| 链路鉴权 | `AUTHENTICATION` | FAILED，去设置 |
| 网络/超时 | `NETWORK`/`TIMEOUT` | FAILED，可重试 |
| 内容过滤/上下文超长 | `CONTENT_FILTERED`/`CONTEXT_LENGTH` | FAILED，提示换说法 |
| 平台过载 | `OVERLOADED` | FAILED，可重试 |
| 播放初始化失败 | `UNKNOWN` | FAILED，释放设备 |

**终态不变量**：`FAILED`/`ENDED` 之后所有事件静默；释放路径每步 `bestEffort`，一步失败不挡其它步。

---

## 11. 功能 → 状态机 → 代码 对照

| 功能 | 状态机 | 代码 |
|---|---|---|
| 半双工防自激 | 双工 + 对话闸门 | `VoiceDuplex`、`VoiceCallReducer.uplinkWanted` |
| 全双工语音打断 | 双工 + 打断 | `StopAssistant(cancel=true)` |
| 按钮打断 | 打断 | `VoiceCallEvent.Interrupt` |
| 精确截断 | 播放进度 + 打断 | `AiAudioPlayback.captureHeard`、`AiRealtimeLink.truncate` |
| 播放排空后才开麦 | 对话 DRAINING | `ScheduleDrain`/`PlaybackDrained` |
| 静音 | 采集闸门 | `Muted` → `SetUplinkEnabled(false)` |
| 转写显示 | UI 投影 | `AiCallViewModel.transcript` |
| 方言参数隔离 | 密封类型 | `YoRealtimeTurnDetection`、`requireTurnDetectionMatchesDialect` |
| 上行线速率 | 方言 | `AiRealtimeLink.inputWireSampleRateHz` |

---

## 12. 已知边界

1. **客户端 VAD 未实现**：当前完全依赖服务端 VAD（`threshold`/`silence_duration_ms`）。端侧 VAD 价值在于弱网与静音判定，列入下一阶段。
2. **AEC 有效性未在每台机型上实测**：设计用"事实探测 + 半双工兜底"，不假设 AEC 一定好用；产品默认半双工。
3. **语义 VAD（`semantic_vad`/`semantic_vad_ec`）未启用**：通义支持，但需要按场景调参；当前用 `server_vad`。
4. **输出路由已进入双工决策**：`AndroidAudioRoute` 查蓝牙 A2DP/SCO、有线耳机、USB 耳机 → 私密路由直接全双工；外放/听筒按 AEC 事实决定。未接 `AudioDeviceCallback` 动态热插拔（通话中换设备不重算）。
5. **工具调用**：协议层已支持（`ToolCall`/`ToolOutput`/`CreateResponse`），通话 UI 尚未接；状态机需要新增 `TOOL_RUNNING` 子状态。

---

## 13. 落地与验证

### 13.1 单元测试

```
:lib_ai:testDebugUnitTest   410 tests / 0 failures / 0 errors
:app:testDebugUnitTest      341 tests / 0 failures / 0 errors
```

新增的架构约束测试：

| 测试 | 守住的东西 |
|---|---|
| `TurnDetectionDialectTest`（10 条） | 方言字段集互斥；错配在 `build()` 就抛；Qwen threshold 允许负数、OpenAI 不允许；`Disabled` 编码成 JSON null |
| `VoiceDuplexPolicyTest`（4 条） | 路由 × AEC 的双工矩阵；请求 HALF 永不被升级；请求 FULL 在外放无 AEC 时降级 |
| `VoiceCallReducerTest`（11 条） | DRAINING、闸门、半双工忽略回声、全双工打断、DRAINING 打断不 cancel、终态幂等 |
| `RealtimeVoiceSessionTest` | actor 与端口协作（闸门、播放、失败释放、停止静默） |

### 13.2 真机（motorola edge 60 pro / qwen3.8-omni-flash-realtime）

| 测试 | 结果 |
|---|---|
| 真实 App：MainActivity → AI → 语音通话 | 页面进入"正在聆听"，真实 Qwen 建连成功（截图 [qwen-call-screen.png](qwen-call-screen.png)） |
| 注入式测试脚本与音频 | 已全部删除（`qwen_prompt.pcm/.wav` 与 `live/` 测试包），验证不再依赖伪造 PCM |

### 13.3 本机双工实测结论

- 手机外放 + 本机 AEC：全双工仍会自激（AEC 未达到可用水平）→ 策略正确降为 **HALF**；
- 因此产品策略是"按路由决定"：**耳机/蓝牙/USB 走 FULL（可语音打断），外放/听筒走 HALF（按钮打断）**。
- 这个结论来自实测，不是假设：`VoiceDuplexPolicy` 把"私密路由"作为全双工的独立充分条件。
