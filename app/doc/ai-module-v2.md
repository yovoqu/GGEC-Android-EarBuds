# AI 模块 v2 架构设计

> 状态：**待评审**。定稿后才进入实现，实现严格按本文的分层与文件树落地，不在旧代码上叠补丁。
> 前置文档：`ai-architecture.md`（v1，对话页与多平台配置）、`ui-design.md`（视觉规范）。

## 0. 结论摘要

| 决策 | 选择 | 否决的方案与原因 |
| --- | --- | --- |
| 语音放哪 | 独立限界上下文 `voice`，与 `chat` 平级 | 塞进 `AiChatSession`：它会引入麦克风、播放、长连接三种生命周期，污染纯 Java 的对话状态机 |
| 两种模式怎么统一 | `AiVoiceStrategy` 端口，OMNI 与 CASCADED 是两个实现，产出同一种 `AiVoiceState` | 在页面里 `if (mode == OMNI)` 分支：页面会变成两个页面的缝合体 |
| CASCADED 怎么接对话 | 复用现有 `AiChatSession` | 另写一条对话链路：上下文、停止、重试、标题全要重写一遍，且两份状态必然分叉 |
| Token 从哪来 | 只认平台响应里的 `usage`，本地不算 tokenizer | 本地分词：与账单对不上，且要背一份词表 |
| 费用怎么建模 | 可空字段 + 独立记录结构，不推算 | 用价目表反算：价目随平台更新，算出来的数字会被当真 |
| 设置页怎么落地 | 重新组织为「分区列表」，复用旧实现的**行为**与领域用例 | 在恢复出来的旧 Activity 上改样式：结构是围着一份已删除的对话页长出来的，改不动 |
| 多模态怎么预留 | `AiMessage` 带 `List<AiContentPart>`，旧文档只有 TEXT | 另开一条多模态消息类型：两种消息在会话里并存，适配成本翻倍 |
| lib_ai 怎么支持多模型 | 加一层**能力模型**（`YoModelCapability` + `YoModelDescriptor` + `YoCapabilityRouter`），协议与端点由 lib_ai 按能力选 | 继续按模型名子串猜类型：猜错的表现是平台返回 400，被翻成"参数不对"，排查成本全落到使用者身上 |

本轮**不做**（明确写出，避免默认被省略）：工具调用的执行循环、图片的上传/存储/解析、向量检索与 RAG。
**要做**的是：两种语音模式都真实现（全双工 WebSocket 与转写→对话→合成），平台侧 token 与费用按响应记录，
模型能力可声明、可推断、可由用户覆盖。

## 1. 能力矩阵

| 能力 | v1 现状 | 本轮目标 |
| --- | --- | --- |
| 平台 token 用量 | `AiUsage(input, output)`，只透传两家字段 | 三家协议 + 缓存命中 token + 平台费用（可空） |
| 费用/计费 | 无 | 可空 `AiCost`，随请求落盘，不进界面推算 |
| 按请求用量记录 | 无 | `AiUsageRecord` → `usage/requests.jsonl`（追加写） |
| 按会话聚合 | 无 | `AiUsageSummary`（纯函数，可单测） |
| 上下文管理 | 按条数裁剪 `AiContextPolicy` | 保留；补"系统提示词 + 历史 + 当前消息"的统一组装点 |
| 流式/取消/超时/重试 | 有（`YoAiClient` 三档超时 + 退避） | 保留；把重试次数与超时暴露到设置页 |
| 模型参数 | 温度 / 最大输出 / 上下文条数 | 补 `top_p`、`max_tokens`、`presence/frequency` 位 |
| 多模态消息 | 纯文本 | `AiContentPart`（TEXT/IMAGE/AUDIO）+ 旧文档兼容 |
| 平台/模型配置 | 领域用例齐全，**无界面** | 设置页与平台页回到界面 |
| AI 模式 | 无 | `AiMode`：`CASCADED` / `OMNI`，存设置、可切换 |
| 会话管理 | 领域齐全，**无界面** | 历史面板：列表/切换/重命名/删除/清空 |
| 语音通话 | 无 | 通话页 + 两种模式都真实现（全双工 WebSocket / 转写+TTS） |
| 照片入口 | 无 | 通话页占位按钮，**零上传代码**（唯一不实现的项） |

## 2. 分层与边界

沿用 v1 的五层与 Gradle 导入检查（`checkAiLayerImports`），新增一层同样的围栏：

```
ui/ai  ──────────────────────────────────────────────────────────────┐
  chat/ settings/ history/ call/ voice/                                │ 依赖 ↓
domain/ai ────────────────────────────────────────────────────────────┤  纯 Java
  model/ port/ policy/ usecase/ chat/ usage/ voice/                     │
data/ai ──────────────────────────────────────────────────────────────┤  依赖 lib_ai / org.json
  store/ json/ settings/ conversation/ gateway/ usage/ voice/           │
platform/ai ──────────────────────────────────────────────────────────┤  唯一依赖 Android 的实现层
  KeystoreSecretCipher · AndroidSpeechSynthesizer · AudioCapture        │
wiring/AiGraph ───────────────────────────────────────────────────────┘  装配根
lib_ai ─── 协议（Chat/Messages/Responses）+ 支持服务（catalog/models/completions/probe/errors）
```

新增的边界规则（写进 `checkAiLayerImports`）：

| 层 | 新增禁止 |
| --- | --- |
| `domain/ai/voice` | `android.*`、`com.yovo.lib_ai`、`org.json`、`java.io.File` |
| `domain/ai/model` 里的用量类型 | 同上；**不许**出现任何"单价/汇率"常量——费用只从平台响应来 |
| `ui/ai/voice` | 直接持有 `AudioRecord` / `TextToSpeech`（只允许经 `domain/ai/voice` 端口） |
| `data/ai/voice` | `android.speech.tts`（TTS 属于 `platform/ai`） |

## 3. lib_ai 多模型架构

这一节回答一个问题：**"支持多种模型"具体指什么，边界划在哪。**

### 3.1 现状的问题

v1 的 `lib_ai` 说"多平台"，落到代码其实是**多协议**：`chat` / `messages` / `responses` 三套方言 + 一层平台类。
模型层面只有 `YoModelKinds` 按 id 子串猜"这是不是对话模型"。这条路走不通：

- 同一个模型 id 在不同平台上的能力不一样（`gpt-4o` 直连能看图，某些网关镜像只给文本）；
- 新模型靠子串猜必然滞后，猜错的表现是"请求发出去了、平台返回 400"，而 400 会被翻成"参数不对"这种误导性提示；
- 语音、转写、合成这些能力根本没有对话协议的入口，只能散在别处。

所以要多一层**能力模型**：模型声明它能做什么，路由按能力选协议和端点，而不是按名字猜。

### 3.2 能力枚举与描述符

```java
enum YoModelCapability {
    CHAT_TEXT, CHAT_VISION, CHAT_AUDIO_IN,   // 对话输入的三种模态
    REASONING, TOOLS,                        // 对话的两种玩法
    REALTIME_AUDIO,                          // 全双工语音
    TRANSCRIPTION, SPEECH_SYNTHESIS,         // 语音的两端
    EMBEDDING                                // 只占位，本轮不做
}

enum YoCapabilitySource { DECLARED, INFERRED, USER }   // 能力从哪来，决定界面敢不敢这么说

record YoModelDescriptor(
        String id, String displayName,
        Set<YoModelCapability> capabilities,
        YoCapabilitySource source,
        @Nullable Integer contextWindow,
        @Nullable String family)          // "gpt-4o" / "claude-3-5" / "deepseek" / "whisper"
```

`source` 不是装饰：界面上那句"支持看图"必须有出处。`INFERRED` 的模型在设置页显示成待确认状态，
用户勾选后才升为 `USER`。这条规则挡住"看起来支持、实际 400"。

### 3.3 能力从哪来（三级来源，优先级从低到高）

| 级别 | 来源 | 做法 |
| --- | --- | --- |
| 1 | `YoKnownModels` 内置表 | 按 family + id 精确声明。本轮至少覆盖：`gpt-4o*`、`gpt-4.1*`、`o3`/`o4-mini*`、`claude-3-5`/`3-7`、`deepseek-chat`/`reasoner`、`whisper-1`、`tts-1`/`gpt-4o-mini-tts`、`text-embedding-3-*`、`gpt-4o-realtime-preview` |
| 2 | `YoModelInference` 名称推断 | 内置表没有时按模式给 `INFERRED`：`*-realtime*`→REALTIME_AUDIO，`whisper*`/`*transcri*`→TRANSCRIPTION，`tts*`/`*speech*`→SPEECH_SYNTHESIS，`embed*`→EMBEDDING，其余默认 CHAT_TEXT |
| 3 | 用户覆盖 | 设置页可给某个模型手动勾选能力，`source=USER`，永久优先 |

`YoModelKinds` 保留，降级为第 2 级的实现，公开签名不变（兼容）。

### 3.4 路由：能力 → 适配器

```java
record YoModelRoute(YoModelCapability capability, YoModelDescriptor model,
                    YoProtocol protocol,   // CHAT_COMPLETIONS / MESSAGES / RESPONSES / REALTIME / AUDIO_TRANSCRIBE / AUDIO_SPEECH
                    String endpoint)

interface YoCapabilityRouter {
    YoModelRoute route(@NonNull YoModelCapability capability, @NonNull String platformId,
                       @Nullable String preferredModelId) throws YoAiException;
}
```

| 能力 | 协议 | 端点 |
| --- | --- | --- |
| CHAT_TEXT / CHAT_VISION / CHAT_AUDIO_IN / REASONING / TOOLS | 三条对话协议里平台声明支持的那条 | `/chat/completions`、`/messages`、`/responses` |
| TRANSCRIPTION | AUDIO_TRANSCRIBE | `/audio/transcriptions` |
| SPEECH_SYNTHESIS | AUDIO_SPEECH | `/audio/speech` |
| REALTIME_AUDIO | REALTIME | `wss://…/realtime?model=…` |

找不到具备该能力的模型时抛**新错误类型** `YoAiErrorKind.UNSUPPORTED_CAPABILITY`，
带上"需要什么能力、当前平台有哪些模型"，而不是让它变成一个语焉不详的 400。

### 3.5 统一请求模型（协议无关）

```java
sealed interface YoContentPart { Text | Image(url, mimeType) | Audio(ref, mimeType) }

record YoSamplingParams(@Nullable Double temperature, @Nullable Double topP,
                        @Nullable Integer maxTokens, @Nullable Double presencePenalty,
                        @Nullable Double frequencyPenalty, List<String> stop)

record YoChatRequest(String model, List<YoContentPart> parts, List<YoToolSpec> tools,
                     YoSamplingParams params, boolean stream)
```

三个适配器只做**方言翻译**：

| 协议 | 内容怎么放 | 参数怎么放 | 工具怎么放 |
| --- | --- | --- | --- |
| Chat Completions | `messages[].content` 数组 | 顶层字段 | `tools[].function` |
| Messages | `content` blocks，`system` 单独字段 | 顶层字段 | `tools` + `tool_choice` |
| Responses | `input` 数组 | 顶层字段 | `tools` |

app 侧永远只说"我要一个能看图的对话"，不出现协议名、不出现音频格式。

### 3.6 能力 × 实现矩阵

| 能力 | v1 | 本轮 |
| --- | --- | --- |
| CHAT_TEXT | ✅ | — |
| CHAT_VISION | 结构在，图片没接 | `YoContentPart.Image` 打通到三家方言；**app 侧本轮不产生图片**（照片是占位） |
| CHAT_AUDIO_IN | ❌ | 类型与方言映射；app 侧只用于评审，不进产品流程 |
| REASONING | ✅（思考内容拆分） | — |
| TOOLS | ❌ | 只做 `YoToolSpec` 类型与请求组装，**不做执行循环**（工具执行属于下一版） |
| REALTIME_AUDIO | ❌ | `realtime/` 客户端（WebSocket 双向音频） |
| TRANSCRIPTION | ❌ | `speech/` 转写客户端 |
| SPEECH_SYNTHESIS | ❌ | `tts/` 合成客户端 + 系统 TTS 兜底 |
| EMBEDDING | ❌ | 只留枚举位 |

### 3.7 兼容与迁移

- `YoModels` 增加 `descriptors(...)` 返回 `List<YoModelDescriptor>`；旧的 id 列表方法保留，内部转一层。
- `YoChatCompletions` / `YoAnthropicMessages` / `YoResponses` 的公开方法**签名不动**，只加 `send(YoChatRequest)` 重载。
- app 的 `LibAiModelGateway` 从"拉 id 列表"改成"拉描述符"，`AiModel` 增加能力集合（可空：旧文档缺省为空表示未知）。

### 3.8 为什么不把这些放进 app

app 要回答的是"用哪个模型"，不是"这条平台用什么协议"。协议、端点、音频格式留在 `lib_ai`，
换平台或换模型时 app 一行不用改；反过来，能力判断写进 app，就会随每个新平台再抄一遍。

## 4. 领域模型扩展

### 4.1 用量与费用（`domain/ai/model` + `data/ai/usage`）

```java
// 平台给什么就记什么，缺的就是"未知"
record AiUsage(int inputTokens, int outputTokens, int totalTokens,   // total 为 0 表示平台没给，退回求和
               @Nullable BigDecimal cost,
               @Nullable Integer cachedTokens,                        // 读缓存命中
               @Nullable Integer cacheCreationTokens)                 // 写缓存
{
    AiUsage(int inputTokens, int outputTokens);   // 老构造器保留，旧调用点零改动
}

// 一次请求一条流水，只追加。失败的请求也记，否则失败率和平均耗时无从回答
record AiUsageRecord(long timestampMillis, @Nullable String conversationId,
                     String platformId, String modelId,
                     int inputTokens, int outputTokens, int totalTokens,
                     @Nullable BigDecimal cost, long elapsedMillis,
                     AiUsageStatus status, @Nullable String failureReason) {}

enum AiUsageStatus { SUCCESS, FAILED, CANCELLED }

// 聚合结果（纯函数产物，不落盘）
record AiUsageSummary(int requests, long inputTokens, long outputTokens, long totalTokens,
                      BigDecimal cost, int pricedRequests) {}
```

`AiUsageSummary.summarize(...)` / `byPlatform(...)` / `byConversation(...)` 都是纯静态函数，单测覆盖"有的记录带费用、有的不带"。

**费用只有一个金额，没有币种字段**：本轮能报费用的平台按美元计价（网关惯例），
多币种要等真有平台给不同币种时再升成 `AiCost(amount, currency, source)`，现在加空壳字段只会让人以为已经支持了。

**为什么放在 `model` 而不是单开 `usage` 包**：这些类型和 `AiMessage`/`AiConversation` 一样是被界面、网关、存储同时引用的领域值对象，
单开一个包会让 `AiUsage` 被两边同时 import，反而分散。聚合与落盘逻辑分别在 `AiUsageSummary` 与 `data/ai/usage`。

### 4.2 多模态消息（`domain/ai/model`）

```java
sealed interface AiContentPart permits AiContentPart.Text, AiContentPart.Image, AiContentPart.Audio {
    record Text(String text) implements AiContentPart {}
    record Image(String ref, String mimeType) implements AiContentPart {}   // ref 只放引用，不放字节
    record Audio(String ref, int durationMillis) implements AiContentPart {}
}
```

- `AiMessage.text()` 保留（等于所有 TEXT part 拼接），旧代码零改动。
- `AiMessage.parts()` 新增；旧文档反序列化时自动生成单个 TEXT part。
- 本轮**只**在通话页与消息模型里用到 `Audio`；`Image` 只有类型，没有上传路径。

### 4.3 模式与语音配置

```java
enum AiMode { CASCADED, OMNI }        // 显示名在界面层，领域不写中文

record AiVoiceSettings(
    AiMode mode,
    String asrPlatformId,             // CASCADED：语音识别用哪个平台（可为空=跟对话同一个）
    String ttsPlatformId,             // CASCADED：合成用哪个平台（可为空=系统 TTS）
    String omniModelId,               // OMNI：全双工模型
    int silenceTimeoutMillis)         // 判定"说完了"的静音阈值
```

`AiSettings` 增加 `AiVoiceSettings voice`，`AiChatPreferences` 增加 `topP`、`maxTokens`（与现有 `temperature`/`maxOutputTokens` 合并去重，保留旧字段名做兼容读）。

### 4.4 语音状态（两种模式共用）

```java
enum AiVoiceState { CONNECTING, LISTENING, THINKING, SPEAKING, MUTED, ENDED, FAILED }

record AiVoiceTurn(String id, boolean user, String text, boolean finalTranscript, long atMillis) {}

record AiVoiceSnapshot(AiVoiceState state, AiMode mode,
                       List<AiVoiceTurn> turns, float inputLevel,  // 0..1，页面画光晕用
                       @Nullable AiFailure failure) {}
```

## 5. 端口与适配器

| 端口（domain） | 实现（data / platform） | 说明 |
| --- | --- | --- |
| `AiUsageStore` | `FileAiUsageStore`（data） | 追加写 JSONL，读回做聚合；写入失败不影响对话 |
| `AiSpeechSynthesizer` | `SystemTtsSynthesizer`（platform） | `android.speech.tts`，失败时静默降级 |
| `AiSpeechRecognizer` | `WhisperTranscriber`（data） | `POST /v1/audio/transcriptions`，multipart wav；OpenAI 兼容 |
| `AiRealtimeLink` | `RealtimeWebSocketLink`（data） | 全双工长连接：`open/sendAudio/cancel/close` + 音频/文本/打断事件回调 |
| `AiAudioCapture` | `AndroidAudioCapture`（platform） | `AudioRecord` 16k/16bit/单声道，回调 RMS 电平 |
| `AiAudioPlayback` | `AndroidAudioPlayback`（platform） | `AudioTrack` + `AudioManager` 路由（扬声器/听筒） |
| `AiWavSink` | `WavWriter`（data） | 把采集帧包成 wav，供转写上传 |

所有实现都要能被它对应的 `Fake*` 替换（单测用），且**失败必须可读**：不吞异常，不伪造成功。

## 6. 用例层

新增 `domain/ai/usecase`：

| 用例 | 职责 |
| --- | --- |
| `SaveVoiceSettings` | 原子更新 `AiSettings.voice`，含校验（OMNI 必须有 omniModelId） |
| `RecordUsage` | 把网关回调折成 `AiUsageRecord` 交给 `AiUsageStore`；失败吞掉并只记一条日志级事件 |
| `LoadUsageSummary` | 读记录 → `AiUsageSummary`（设置页「用量」分区用） |
| `StartVoiceCall` | 按模式挑 strategy，返回一个 `AiVoiceSessionHandle`（页面只持有句柄） |

## 7. 会话管理的数据流

不新增 ViewModel：`AiChatViewModel.history()` 已经存在（v1 留下的 `List<AiHistoryItemUi>` + `watchHistory()`），
历史面板只是它的一个视图。

```
AiConversationRepositoryImpl ──summaries()──▶ ListConversations ──▶ AiChatViewModel.history()
                                                                        │ LiveData
                                        AiHistorySheet  ◀───────────────┘
                                          │ onOpen / onRename / onDelete / onClearAll
                                          ▼
                                    AiChatSession（串行执行器改状态）
```

- 面板结构：`YoSheet` + `YoList`（沿用 v1 的 `AiListSheet` 抽象），每行四段式：标题 / 摘要 / 更新时间 / 更多。
- 空态：一句"还没有对话" + 一个"开始新对话"，不放假插画。
- 重命名走 `AiDialogs.input`，删除走 `AiDialogs.confirm`（破坏性色）。

## 8. 语音子系统

### 8.1 两种模式的组合

```
                      ┌──────────── AiVoiceStrategy（端口）────────────┐
OMNI                  │  OmniVoiceStrategy                           │
  AiAudioCapture ────▶│   capture ─▶ AiRealtimeLink ─▶ 音频/文本事件   │──▶ AiVoiceSnapshot
  AiAudioPlayback ◀───│   播放 ◀─────────────────────────────────────  │
                      └───────────────────────────────────────────────┘
CASCADED              ┌───────────────────────────────────────────────┐
  AiAudioCapture ────▶│  CascadedVoiceStrategy                        │
                      │   capture ─▶ AiSpeechRecognizer ─▶ 文本       │
                      │   文本 ─▶ AiChatSession.send() ─▶ 流式回复     │──▶ AiVoiceSnapshot
  AiAudioPlayback ◀───│   回复 ─▶ AiSpeechSynthesizer ─▶ 音频          │
                      └───────────────────────────────────────────────┘
```

关键点：CASCADED 的"思考"阶段直接调 `AiChatSession`，所以**对话上下文、停止、重试、落盘全部复用**，
通话结束后这段对话原样出现在历史里——这是一条硬要求，也是不做第二套对话链路的原因。

### 8.2 状态机

```
CONNECTING ─▶ LISTENING ─┬─▶ MUTED ─▶ LISTENING
                         ├─▶ THINKING ─▶ SPEAKING ─▶ LISTENING
                         └─▶ FAILED ─▶ ENDED
任意状态 ──▶ ENDED（挂断）
```

- 静音阈值（默认 900ms）、思考超时（8s）、回答超时（30s）都在 `AiVoiceSettings` 里，可调。
- 所有状态切换由 strategy 发事件，页面**只画**，不在页面里判断状态流转。

### 8.3 两种模式都要真做（唯一的占位是"插入照片"）

`OMNI` 与 `CASCADED` 都是可用的实现，不是占位。两者都用标准协议，走用户已经配好的平台（baseUrl + API Key）：

| 模式 | 链路 | 依赖的标准接口 |
| --- | --- | --- |
| `OMNI` | WebSocket 双向音频：`input_audio_buffer.append`（PCM16 base64）↑<br>`response.audio.delta` / `response.audio_transcript.delta` ↓ | OpenAI Realtime 兼容端点（`wss://<baseUrl>/v1/realtime?model=<omniModel>`），鉴权用同一份 API Key |
| `CASCADED` | 录音 → `POST /v1/audio/transcriptions`（multipart wav）→ 文本 → `AiChatSession.send()` → 流式回复 → TTS | Whisper 兼容转写端点 + 任意对话模型 + 系统 `TextToSpeech` |

- 两个 strategy 都必须能被同一套假实现替换来做单测（`FakeRealtimeLink`、`FakeTranscriber`、`FakeSynthesizer`）。
- 网络不可达/鉴权失败/协议不兼容时，各自的错误映射到 `AiVoiceSnapshot.failure`，页面显示具体原因，不吞掉。
- 采集统一 16kHz/16bit/单声道（Realtime 要求 PCM16；转写要求 wav，由 `WavWriter` 包一层）。

**唯一不实现的是"通话中插入照片"**：按钮、占位样式、点击反馈在 P4 做，照片的选择、上传、解析、随音频一起发送都不写，
`AiContentPart.Image` 只留类型定义（见 §4.2），没有任何代码路径会创建它。

### 8.4 实现顺序（语音内部）

1. `AiAudioCapture` / `AiAudioPlayback`（platform）+ 电平回调 → 光晕能跟着声音动。
2. `SystemTtsSynthesizer` → 能念一段文字。
3. `CascadedVoiceStrategy`：转写客户端 → 复用 `AiChatSession` → TTS。这条链路先通，因为它只需要 HTTP。
4. `OmniVoiceStrategy`：WebSocket 长连接、音频上行、音频下行、文本增量、打断（用户重新开口时发 `response.cancel`）。
5. 通话页接两种 strategy，模式取自 `AiVoiceSettings`。


## 9. 界面层

### 9.1 文件树（新）

```
ui/ai/
  AiTheme.java            令牌（已有，补 RADIUS/CALL 相关常量）
  AiDialogs.java          确认 / 输入 / 动作列表（v1 恢复）
  chat/
    AiChatPage.java               对话页（标题行 + 内容 + 输入）
    AiChatViewModel.java          已存在，补 voiceSettings
    AiChatUiState / Mapper        已存在
    AiHistorySheet.java           历史面板（重建）
    AiMessageAdapter / *View      已存在
  settings/
    AiSettingsActivity.java       设置总览（分区列表）
    AiSettingsViewModel.java
    AiSettingsUiState / UiMapper
    AiPlatformActivity.java       平台详情（地址 / Key / 模型 / 测速）
    AiPlatformViewModel.java
    AiPlatformUiState / UiMapper
    AiAddPlatformDialog.java
  call/
    AiCallActivity.java           通话页（全屏）
    AiCallViewModel.java          只出 AiVoiceSnapshot，持有 StartVoiceCall 句柄
    VoiceOrbView.java             自绘光晕（唯一的自绘组件）
  voice/
    AiVoiceSessionClient.java     界面 ⇄ 领域的薄适配（不让页面碰端口）
```

### 9.2 导航

```
对话页 ──[标题 ⌄]──▶ 历史面板 ──▶ 切换/重命名/删除/清空/新对话
       ──[齿轮]────▶ 设置总览 ──▶ 平台列表 ──▶ 平台详情（地址/Key/模型/测速）
       │                        └▶ AI 模式（分段控件：级联 / 全双工）
       │                        └▶ 对话参数（温度 / top_p / 最大输出 / 上下文条数）
       │                        └▶ 用量（只读，来自 AiUsageSummary）
       ──[通话]────▶ 通话页 ──▶ 模式徽标 + 光晕 + 静音/扬声器/拍照占位/挂断
```

### 9.3 视觉

- 一律 `AiTheme` 令牌 + YoUI 组件；新增组件只有 `VoiceOrbView`（画，不是控件）。
- 分区列表 = `YoList` + `ListTextSpec/ListPrefixSpec/ListSuffixSpec`，行高 56/72，与 v1 一致。
- 通话页是全屏单页：`page` 底 + 光晕一个主角 + 底部一排圆形按钮；**不引入深色专属配色**，
  深浅色都从令牌来（深色下 `page` 自动变深）。
- 照片占位按钮比其它两个低一档（描边、无品牌色），并带"未开放"提示。

## 10. 线程与生命周期

- 采集/播放/TTS 各自一条线程或回调线程；**状态合并**在 `AiVoiceSessionClient` 的串行执行器上做，与 `AiChatSession` 同构。
- 通话页 `onStop` 只停采集与播放，不销毁会话；`onDestroy` 释放采集、播放、TTS。
- 通话中的 CASCADED 对话继续由 `AiChatSession` 落盘；页面退出不打断生成（与 v1 同一条约束）。

## 11. 存储与迁移

| 文档 | 位置 | 迁移 |
| --- | --- | --- |
| 设置 | `files/ai/settings.json` | 加 `voice` 节点；缺省时用 `AiVoiceSettings.defaults()`，**旧文档照常打开** |
| 对话 | `files/ai/conversations/*.json` | `usage` 节点扩展可空字段；老文档缺字段即 null |
| 用量 | `files/ai/usage/requests.jsonl` | 新文件，追加写；损坏行跳过，不影响读取 |
| 密钥 | `noBackupFilesDir/ai/secrets.json` | 不变 |

## 12. 测试策略

| 层 | 测什么 |
| --- | --- |
| `lib_ai` | 三家协议 usage 解析（含缺失、含 cost）、流式最后一块 usage、重试与超时边界 |
| `domain/ai` | `AiUsageSummary` 聚合、`AiContextPolicy` 组装、`AiVoiceSettings` 校验、状态机流转（用假 strategy + 假 clock） |
| `data/ai` | JSON 新旧兼容（老文档能读、新字段能回环）、JSONL 损坏行容错 |
| `ui/ai` | Mapper 取舍（模式文案、用量格式、历史行摘要），不碰 View |
| 真机 | 设置页增删改、模式切换后对话页徽标、通话页四种状态、权限拒绝、拍照占位提示 |

## 13. 实施阶段

| 阶段 | 内容 | 验收 |
| --- | --- | --- |
| **P0 领域与端口** | 4.1–4.4 的模型、6 的用例、5 的端口接口 | 纯 Java 单测通过，`checkAiLayerImports` 通过 |
| **P1 用量落地** | lib_ai usage/cost 采集 → gateway 折成记录 → `FileAiUsageStore` | JSONL 有真实记录；聚合正确 |
| **P2 模型能力层** | §3 的 `YoModelCapability` / `YoModelDescriptor` / `YoKnownModels` / `YoCapabilityRouter`，`YoChatRequest` 统一请求 | 路由单测：缺能力时报 `UNSUPPORTED_CAPABILITY`；三家方言组装正确 |
| **P3 会话管理** | 历史面板重建 + 对话页标题行入口 | 真机：新建/切换/重命名/删除/清空 |
| **P4 设置页** | 设置总览 + 平台详情 + 模型能力标注 + 模式 + 参数 + 用量 | 真机：配平台 → 选模型 → 切模式 → 对话页徽标变化 |
| **P5 语音** | 采集/播放/转写/合成/两个 strategy/通话页 | 真机：两种模式都能起一路完整对话；静音、扬声器、权限拒绝、拍照占位 |
| **P6 收口** | 文档、动效核对、独立评审、截图 | 评审 ≥ 7/10，或逐条写明取舍 |

P0 与 P2 是硬门槛：模型能力层不对，后面每个平台都要在 app 侧再补一次判断。

## 14. 实施记录（按实际代码写，含与设计的偏差）

### 14.1 已落地

| 层 | 内容 |
| --- | --- |
| `lib_ai` | 三家协议 usage 统一读法（prompt/input 别名、`cached_tokens`、`cache_read/creation`、`cost`/`total_cost`，流式末块透出）；`speech/` 转写（Whisper 兼容，wav 封装 + 重试 + 错误分类）；`realtime/` 全双工 WebSocket；`models/` 能力层（`YoModelCapability`/`YoModelDescriptor`/`YoKnownModels`/`YoModelInference`/`YoModelCatalog`/`YoServiceKind`/`YoModelRoute`/`DefaultCapabilityRouter`/`YoServiceEndpoints`） |
| `domain/ai` | 用量（`AiUsage`/`AiUsageRecord`/`AiUsageStatus`/`AiUsageSummary`/`AiUsageStore`/`RecordUsage`/`LoadUsageSummary`）；语音（`AiMode`/`AiVoiceSettings`/`AiVoiceSession`/`AiVoiceSnapshot`/`AiVoiceException` + 5 个端口）；`AiChatPreferences.topP`；`AiSettings.voice`；`AiContentPart`；`SaveVoiceSettings` |
| `data/ai` | `usage/` JSONL 账本；`voice/` 五个实现（`WhisperSpeechRecognizer`、`CascadedVoiceStrategy`、`OmniVoiceStrategy`、`RealtimeLinkAdapter`、`PcmResampler`） |
| `platform/ai` | `voice/` 三个（`AndroidAudioCapture`、`AndroidAudioPlayback`、`SystemTtsSynthesizer`） |
| `ui/ai` | 对话页（历史/通话/设置三个入口）、`settings/`（总览 + 平台详情）、`history/`（面板）、`call/`（全屏通话页 + 光晕） |

### 14.2 与设计的偏差（都写清原因）

1. **费用只有金额，没有币种字段**（§4.1 已说明）：多币种等真有平台给不同币种时再升 `AiCost`。
2. **行高**：YoUI 公共 API 只有 `setItemVerticalInset`，没有 `itemMinHeight`；用 16dp 内间距得到单行约 52dp、双行约 70dp，未精确到 56/72。
3. **模型能力层已接进 app 的模型列表**（`LibAiModels` 优先用 `YoModelCatalog` 的可信声明推 `AiModel.features`，只有推断结果时才回退平台自报的 feature 位），但**能力路由还没接管出站请求**：拉模型、选模型按 id 走，`DefaultCapabilityRouter` 目前只有单测在用。
4. **主机地址只放行 https** 已按需求放宽：回环（127/::1/localhost）、私有网段（10/8、172.16/12、192.168/16）与单标签主机名允许 http —— 自建模型服务（Ollama、LM Studio、vLLM）只有局域网 http，一刀切拒绝等于做不到"接自己的机器"。放行的地址在界面上标注「（不加密传输）」，公网仍旧强制 https。见 `AiBaseUrlPolicyTest`（5 条）。
5. **平台侧 TTS 未做**：合成只有系统 `TextToSpeech`；`/audio/speech` 适配器与端口一致，但这一版没接。
6. **工具调用**：只有类型位（`TOOLS` 能力），没有执行循环。
7. **多模态上行只留了管道**：`AiContentPart` 已接进出站装配（`LibAiChatGateway` 把每轮内容表达成部件列表再压平），`AiContentPartTest` 锁住"非文本部件不污染文本"；真正的图片上行要等相册开放。

### 14.3 真机验收（`NEPI4E0106`，1220×2712）

| 界面 | 证据 | 结论 |
| --- | --- | --- |
| AI 页就绪态（历史/新对话/通话/设置四入口 + 模型 chip） | `ui-work/shots/r12-review-fixes/s1-chat-4entries` | 通过 |
| AI 设置总览（四分区；禁用行的说明仍可读） | `ui-work/shots/r14-review2-fixes/s1-icons-and-disabled` | 通过：行首图标已按 `text2` 上色，禁用行标题 5.7:1、原因单行 |
| 平台详情（模板预填、密钥弹窗、遮罩与粘贴） | `ui-work/shots/r7-settings/s5-platform-new`、`s7-platform-prefilled`、`r8-platform/s1-key-dialog`、`s2-key-typed` | 通过 |
| 平台详情（模型区：拉取 / 手动 / 已选 / 测速 / 删除平台） | `ui-work/shots/r16-platform-page/s2-platform-detail` | 通过：已选模型用品牌色勾 + 来源副标题，破坏性动作单独成卡 |
| 用量分区（真实一笔失败请求） | `ui-work/shots/r11-history-menu/s4-usage` | 通过：请求 1 次 / 令牌 0 / 费用未上报 |
| 历史面板（三条对话、当前标识、相对时间、清空） | `ui-work/shots/r10-history/s1-sheet` | 通过 |
| 历史行「更多」菜单（重命名/删除） | `ui-work/shots/r13-history-menu/s1-menu`、`s2-after-rename-tap` | 通过：菜单为品牌色图标 + 破坏色删除；重命名对话框带字数计数与清空键 |
| 对话持久化恢复 | `ui-work/shots/r11-history-menu/s2-chat-restored` | 通过：冷启动后恢复上次对话与 1.8s·80 tokens |
| 请求失败的表现（平台原文 + 重试） | `ui-work/shots/r11-history-menu/s3-send-state` | 通过 |
| 通话页 CASCADED（聆听 / 静音） | `ui-work/shots/r8-platform/s5-call-listening`、`s7-call-muted` | 通过：光晕随真实电平呼吸，静音后转中性并停呼吸 |
| 通话页 OMNI（服务端鉴权失败） | `ui-work/shots/r9-omni/s2-call-omni`、`r12-review-fixes/s3-call-failed-action` | 通过：真实 WSS 握手被拒；失败态给出「通话已断开 + 原因 + 去设置」 |
| 拍照占位 | `ui-work/shots/r13-history-menu/s4-photo-toast`（toast 已淡出，同一时刻的 uiautomator 树含「通话中插入照片还没开放」） | 通过：只有按钮与提示，无选择/上传/解析代码 |
| 真实 LLM 文本对话（代码块 + 编号列表 + 行内代码 + 用量） | `ui-work/shots/r17-live-chat/s3-markdown` | 通过：`1.2 s · 83 tokens` 是平台给的真实用量 |
| 真实 LLM 富文本（二级标题 + 加粗 + 三列表格 + 链接） | `ui-work/shots/r17-live-chat/s6-message-detail` | 通过：表格有表头底色与行分隔，链接用品牌色 |
| 历史摘要纯文本化 | `ui-work/shots/r17-live-chat/s8-history-plain` | 通过：不再出现 `##`／`**` |
| 私有地址的明文提示 | `ui-work/shots/r18-http-policy/s1-plaintext-warning` | 通过：`http://192.168.1.10:11434（不加密传输）`，公网仍强制 https |
| AI 设置（新架构：身份卡 + 分段 + 内联滑杆） | `ui-work/shots/r22-settings-v3/s4-model-and-sliders` | 通过：滑杆拖动即时保存（1.3 / 19 退出重进仍在） |
| 平台详情（模型在前、连接折叠） | `ui-work/shots/r23-platform/s1-model-first`、`s3-expanded` | 通过：折叠行展开后四个字段出现 |
| 历史面板空态 | `ui-work/shots/r24-history/s3-empty` | 通过；加载态未截到（本地读盘毫秒级） |
| 通话失败态静止 | `ui-work/shots/r25-call/s1-failed-static` | 通过：间隔 1.3s 两帧哈希相同；静音态未验（进不到聆听） |
| 通话失败态的两个出口 | `ui-work/shots/r28-retry/s2-retry-primary` | 通过：重试（品牌胶囊）在上、去设置（中性）在下；点重试 t+0.4s 回到「正在聆听」，证明真的重开了会话 |
| 平台页「删除平台」不再是卡片 | `ui-work/shots/r29-platform-v2/s1-delete-plain` | 通过：无底色红字，与上方卡片拉开 32dp |
| 历史行标题单行 | `ui-work/shots/r30-history-title/s1-state` | 通过：16 码点上限生效，勾/时刻/⋯ 同一基线 |
| 历史面板：清空全部脱出列表卡 | `ui-work/shots/r32-review2-round2/s4-history-final` | 通过：卡片贴合内容，清空在面板底部、左对齐 20dp |
| 设置页身份卡有主角 | `ui-work/shots/r32-review2-round2/s1-settings-avatar` | 通过：首字母方块让第一块读起来是实体，另三张卡退回表单 |
| 通话失败态主按钮为去设置 | `ui-work/shots/r31-review2-fixes/s1-settings-primary` | 通过：NOT_FOUND 时只给能修的那一步，重试隐藏 |
| 历史摘要单行 | `ui-work/shots/r31-review2-fixes/s3-history-12` | 通过：时刻不再插进句子里 |
| 全量验收：对话页有内容 | `ui-work/shots/r33-acceptance/s1-chat-content` | 通过：输入条不再重复模型名 |
| 全量验收：对话页空态 | `ui-work/shots/r33-acceptance/s5-chat-empty` | 通过：只剩一句「耳机有什么问题？」 |
| 全量验收：平台页收起 | `ui-work/shots/r33-acceptance/s2-platform-collapsed` | 通过 |
| 全量验收：平台页展开 | `ui-work/shots/r33-acceptance/s3-platform-expanded` | 通过：删除平台左对齐 20dp（x=56px） |
| 全量验收：历史空态 | `ui-work/shots/r33-acceptance/s4-history-empty`（同 s4 目录） | 通过：清空后面板原地转为空态，不自动关闭 |

未验收：转写与全双工的真实音频往返——请求链路已被服务端 401 证明是通的，缺的是"有人对着设备说话"这一步。

未验收：转写→对话→合成的一次完整往返（设备上只有假 Key，转写必然 401）。

### 14.4 独立评审与处置（两轮）

**第一轮 6.5/10**。改了四条：

1. **禁用行的说明读不出来**：模式行原来把标题与说明一起压到 `hint`，实测对比度 1.6:1，那行"为什么不能选"等于没写。
2. **同一句话说了两遍**：温度/top_p 的说明里「留空用平台默认」与右侧值「平台默认」重复，说明只留含义。
3. **密钥弹窗占位被截断**在"…保存在本"，眼睛图标还压住尾字；占位压到「粘贴密钥」，承诺句交给对话页空态。证据：`ui-work/shots/rv2-review/key-placeholder-after-fix.png`（改前那两张截图不能作证）。
4. **通话失败只有原因、没有下一步**：加「去设置」，只给"改配置就能重新拨通"的失败类型。

**第二轮 7.0/10**（未达 7.5），它推翻了我上一轮的三句判断，本文件已按事实更正：

- **`ListPrefixSpec.iconTinted` 的颜色参数不落到像素上**。上一轮写"行首图标同步降到 text3"是错的——实测整页 8 处行首图标仍是纯黑。现在改用 `AiTheme.listPrefix(res, color)`：自己造一个已上色的 `ImageView` 交给列表，21 处调用点全部换掉，图标颜色这才真的由 `AiTheme` 一处决定。
- **禁用行不能用颜色表达"不可点"**。把标题降到 `text3`（2.85:1）之后，整行最淡的反而是行名——等于把上一轮的对比度问题换个位置。现在标题留在 `text2`（5.7:1），"不可点"交给三件事：没有勾选、副标题写原因、点了不做事。顺带把原因文案压到一行，消掉折行后的孤字「个」。
- **`YoList` 有 `setItemHorizontalInset(float)` / `setContainerPadding(float)`**，上一轮"没有横向内间距 API"的说法是错的。真实原因是 plate 归 `YoList` 所有，任何横向内缩都会连带把行文字推离 20dp 的标题线——所以卡片 4.7dp 与标题 20dp 的对齐是当前折中，不是没有别的办法。
- **`YoDialog` 有 `setButtonDirection(AUTO/HORIZONTAL/VERTICAL)`**，"动作竖排是被迫的"不成立；只是这一版的弹窗动作用横排还是竖排还没定，不是能力缺失。
- 失败态的两行原先是同字号同色，读不出哪个是结论：现在「通话已断开」15sp 主文色、原因 13sp 三级色。证据：`ui-work/shots/r14-review2-fixes/s2-call-failed-hierarchy`。

仍然不改、且理由站得住的只有一条：**档外字号 16/13.5/12.5 来自组件默认值**（`ListTextSpec` 只有 title/subtitle/description + 颜色，没有字号字段）。

### 14.8 这一版放弃了什么

重构一路删掉的东西，逐条记下来免得以后又"补"回去：

| 放弃的 | 为什么 |
| --- | --- |
| 助手回复外面套卡片 | 答案是一屏里唯一要读的东西，容器不承载信息；卡片留给可扫读的重复项 |
| 对话页的装饰波形 | 对话页没有在听，画波形是在骗人；波形只属于通话页的 LISTENING |
| 输入条里的麦克风 | 页头已有通话入口，两个门通一间屋 |
| 设置页的"参数目录"结构 | 每个参数点开弹窗再输入，改一次四次点击；连续量本该是滑杆 |
| 空态的示例问题 | 那四条是我们编的内容，不是产品事实；现在只说一句真话 |
| 失败页的"重试"当主按钮 | 找不到接口时重试必然失败；主按钮按失败类型决定 |
| 品牌蓝当"选中色" | 蓝色只给主操作与进行中；选中用勾形 + 字重 |
| 平台页的字段在前 | 每次进来先跨过四个不常改的字段才看得到模型；改成模型在前、连接折叠 |

### 14.7 第四轮独立评审与处置

同一套方法再评一次（另一个无上下文 agent，8 张当前截图）。**仍是 7.0**，但三条意见更硬，其中一条推翻了上一轮的处置：

| 评审指出 | 处置 |
| --- | --- |
| 通话失败态的主按钮是「重试」，而文案自己写着"模型或接口找不到"——重试必然再失败，真正能解决的「去设置」却是最弱的灰字 | **推翻 14.6 的做法**。主按钮现在按失败类型决定：`fixable`（找不到接口/鉴权/无模型…）→「去设置」是品牌胶囊、「重试」隐藏；`retryable`（网络/超时/服务端/限流）→「重试」是胶囊、「去设置」隐藏。位置固定（去设置在上），只有样式随类型变，避免层级上下跳 |
| 失败态中央仍是橙色圆盘 + 光晕，是"正在收音"的语法，与"已断开"矛盾 | 改为中性灰盘、光晕 alpha 归零 |
| 「删除平台」三处形态不一：收起态居中橙字、展开态白卡、历史里又是另一种红，且都是全屏最响 | 平台页改为与卡片同一条 20dp 左边距、左对齐、无底色。历史「清空全部」仍在列表卡内，**未做**——需要把它移出列表，留到后续 |
| 历史面板时间戳挤进摘要句子中间 | 摘要上限从 18 收到 12 码点，标题与摘要各一行，时刻回到标题行右端 |
| 设置页标"平台默认"的滑杆左端有个灰色 C 形缺口，像被裁掉的滑块 | 未设置时拇指透明（不再画一个会被轨道切掉半边的圆） |
### 14.6 第三轮独立评审（oil-ui 第 4 步）与处置

评审者是一个没有上下文的独立 agent，只给 8 张真机截图 + 产品背景 + 我们自己定的约束，不看代码与过程。**得分 7.0 / 10**。

三条主要问题与处置：

| 评审指出 | 处置 |
| --- | --- |
| 通话失败态是死胡同：没有「重试」「去设置」，橙色光晕还在装"活着"，麦克风/扬声器仍是品牌淡蓝 | 已改：`fixable()` 加入 `NOT_FOUND`（自带平台第一次通话最常撞的就是它）；失败页现在的顺序是**重试（品牌胶囊，主操作）→ 去设置（中性文字）**；失败态光晕 alpha 从 0x3C 降到 0x14；麦克风与扬声器的品牌淡底一并撤成灰色；页标题从 14sp 提到 20sp。重试经实测会真的重开一路会话（t+0.4s 回到"正在聆听"，随后再次失败） |
| 设置页四个"平台默认"滑块：拇指被轨道裁成半圆，且与"最低值"共用一套视觉 | 已改：未设置时换成 14dp 空心环 + 轨道压灰，已设置才是实心蓝拇指；行距从约 65dp 收到 56dp 网格 |
| 空输入时发送箭头两张截图颜色不一致 | 复核为**截图时机**（第二张输入框里还有字），代码只有两档。但"空输入仍像能点"成立，α 从 0.7 降到 0.4 |

约束违规里已修的：对话页空态的同义重复行已删（没有语音模型时不再显示第二句）；平台详情页「删除平台」从独占白卡改成无底色的红字行、上下留 32dp。**品牌蓝的语义**：逐屏统计后每屏至多三处，且都是"主操作 / 进行中"；评审数到的"导航选中"来自 App 外壳的底部导航栏，不在本模块范围。规则与逐屏清单写在 `ui-design.md` §2.1。

评审认可的两点（对话页通栏正文 + 平台页折叠卡的两种形态）保持不动。
### 14.5 调试播种（只在 debug 变体）

`app/src/debug/java/com/ggec/earbuds/debug/AiSeedActivity.java` + `app/src/debug/AndroidManifest.xml`。
设置页的对话框是独立弹出窗口，`uiautomator` 看不到里面的按钮，靠肉眼估算坐标点一次要试三次；
这个页面直接用 `SavePlatform` 等用例写盘（Key 经 Keystore 加密），一次 adb 调用就能把平台、模型、模式、历史数据准备好：

```
adb shell am start -n com.ggec.earbuds/.debug.AiSeedActivity \
  --es platform deepseek --es model deepseek-chat \
  --ez conversations true --es mode OMNI --es realtime gpt-4o-realtime-preview
```

release 变体不含它（清单在 `app/src/debug`），正式包里没有这个入口。



P0 是硬门槛：P0 设计不对，后面每一层都会把错误放大一次。

### 14.9 第三版重构（v4）：按调研重写平台页

这一版与前面几轮的区别：不再"在旧结构上调样式"，而是**删掉整页再按 MVVM 重写**。

#### 做了什么

| 项 | 之前 | 现在 |
| --- | --- | --- |
| 平台页结构 | 模型 / 连接（含四个字段）/ 删除，三层混在一起 | 连接（可测试，结果在行上）→ 模型（事实行 + 全部测速 + 单条重测）→ 高级（折叠：地址/协议/参数来源）→ 危险区 |
| 测速 | 是列表里的一行；结束弹「测速完成」toast | 是区块动作；进行中每行显示「测速中…」；结束不弹，结果写进对应行 |
| 参数 | 只有设置页四个全局滑杆 | 三级：模型 → 全局 → 默认；平台页高级区标注来源（此模型 / 全局 / 平台默认） |
| 对话元信息 | `3.3 s · 170 tokens` | `786 ms · 47 tok/s · 37 tokens` |

#### 代码形态（MVVM）

- `AiPlatformUiState`：不可变状态 + `RowState{IDLE,TESTING,OK,FAILED}`
- `AiPlatformUiMapper`：纯函数，连接三态、事实顺序、行状态、「最快：xxx」
- `AiPlatformViewModel`：唯一可变点，测速生命周期与所有写操作
- `AiPlatformActivity`：只渲染
- 新增 `AiParamsPolicy` + `AiModelParams` + `AiPlatformConfig.modelParams`（含 JSON 编解码）

#### 单测抓到的两个真 bug

1. 「最快」原先**解析展示文本**（`1.2 s` 被正则取成 `12`），导致最慢的模型被判为最快。改为用行内的 `firstTokenMillis` 原始值比较。
2. 参数值 `1.3` 显示成 `1.2999999523162842`（float 宽成 double 的精度尾巴）。改为两位小数 + 去尾零。

#### 证据

| 场景 | 目录 |
| --- | --- |
| 平台页重写后 | `ui-work/shots/r39-platform-rewrite/s2-inset-fixed`、`s3-after-speed` |
| 参数来源可见 | `ui-work/shots/r41-params/s2-source-visible` |
| 对话元信息三指标 | `ui-work/shots/r40-chat-speed/s1-meta` |

#### 这一版**没有**做到（不当成完成）

| 项 | 状态 |
| --- | --- |
| 设置页「全局参数开关 + 作用范围」 | 未做；数据与策略已就绪，缺控件 |
| 模型 > 8 时的搜索框 | 未做 |
| 对话页/历史面板按同一信息密度重排 | 未做（现状：对话页元信息已更新，历史面板沿用上一版） |
| 实时 tok/s（流式中滚动） | 未做；需要流式过程中的 token 计数器，usage 只在结束时到达 |
| 思考过程收起态 | 代码有，DeepSeek 不返回 reasoning，无法真机验证 |
| 静音态光晕静止 | 代码有；本机语音链路进不到聆听，无法验证 |

### 14.10 全量推进：五页并行落地（本轮）

这一轮不再是"改一个开关"，而是五条线同时推进，每页都有真机截图。

| 页面 | 本轮做了什么 | 证据 |
| --- | --- | --- |
| 历史面板 | 按 §2 重写：分组（今天/昨天/更早）、行规格（标题≤16 码点、摘要≤12、时刻挂标题行右端、⋯ 菜单）、四态、卡外清空行；分组与时刻收成一个纯函数 `When(now, then, zone)`；去掉重复的悬浮关闭钮、补底部 safe area | `r42-history/s4-verified` |
| 通话页 | 光晕六态（聆听跟真实电平、静音与失败静止）、状态行、失败出口**二选一**（`AiFailureKind.retryable()` 分派）、四钮控制排；失败后三钮置灰、挂断转中性 | `r42-call/01..04` |
| 设置页 | 参数区：全局开关（品牌色 tint）+ 作用范围（反推显示事实）+ 应用到全部模型；用量三行缺数据写「—」、费用「未上报」 | `r42-settings/s4-switch-brand` |
| 平台页 | 模型数 >8 的搜索框、结论「最快」下移到副行、⋯ 菜单加「批量：应用到全部模型」、补 `YoList.destroy()` | `r42-settings/s2-search-or-fastest`、`s3-advanced` |
| 对话页 | 用户消息长文本折叠（6 行 + 展开/收起，按实际行数判定）、气泡最大宽度 78%→82% | 见 §14.9 元信息截图 |

#### 状态说明

- **308 个单测全绿**（新增 `AiParamsPolicy` 全局开关语义 2 条、作用范围 6 条、搜索 4 条、历史分组 6 条等）。
- 「作用范围」目前是**反推显示**而非可选：数据里没有 scope 字段，界面显示的是事实（所有模型 / 仅某模型 / 当前平台 / N 个模型 / 未启用），改变它的唯一动作是「应用到全部模型」——语义是写模型覆盖，写完不再跟随全局。若要真正的"仅本平台跟随全局"，需要新增 domain 字段，尚未做。
- 并行施工期间出现过一次相互踩踏（settings 与 call 同时改、构建红、设备被两个 agent 抢焦点），已用"包级写域 + 设备令牌"的方式收口。

