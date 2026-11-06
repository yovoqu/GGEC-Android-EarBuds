# lib_ai 缺口审查与目标架构设计

> 范围：`lib_ai`（Android 协议 SDK）对 OpenAI / Anthropic / DeepSeek / 阿里云百炼(Qwen) 四家主流厂商接口的对齐度。
> 方法：① 通读 `lib_ai` 全部公开 API 与内部 codec/transport；② 抓取并核对四家 2026 版官方文档（OpenAI `developers.openai.com`、Anthropic `platform.claude.com`、DeepSeek `api-docs.deepseek.com`、百炼 `docs.bailian.console.aliyun.com`）；③ 以“能力/协议/数据形态”三轴做矩阵比对。
> 结论先说：**lib_ai 的骨架（平台适配器 + 三条对话协议 + 统一补全 + 实时语音 + 模型规格 + 用量账本）方向是对的，但只覆盖了“文本对话 + 实时语音 + Whisper 转写”这一竖井；2026 年主流 API 的能力面已经扩展到 Responses/内置工具/思考预算/提示缓存/TTS/Embedding/Batch/Files/Token 计数/异步媒体任务，lib_ai 在这些面上是空白或半成品。**

---

## 0. 执行摘要（先看这 10 条）

| # | 结论 | 严重度 |
|---|---|---|
| 1 | **思考内容在 Chat 通道被丢弃**：`ChatCodec.decodeMessage` 不读 `reasoning_content`，`YoChatMessage` 也没有该字段。DeepSeek / Qwen3.8 / GLM 的思考结果拿不到。 | P0 |
| 2 | **Chat 请求参数面过窄**：缺 `top_k`、`logprobs/top_logprobs`、`logit_bias`、`user/safety_identifier`、`prompt_cache_key/options`、`audio` 输出配置、搜索类模型的 `web_search_options`、`thinking` 对象、`extra_body`。 | P0 |
| 3 | **内容块模型缺三类关键块**：`video_url`、`file_url`、`cache_control/cache breakpoint`；多模态 `input_audio` 只认 wav/mp3。Qwen 视频、OpenAI 文件、Anthropic 缓存全部无法表达。 | P0 |
| 4 | **Responses 只支持 function 工具**：缺 `web_search`/`file_search`/`code_interpreter`/`computer`/`mcp`/`image_generation`/`tool_search` 等内置工具，缺 `background`、`include`、`context_management`、`prompt_cache_options`、`reasoning`、`text.verbosity`、`truncation`、`conversation`。 | P0 |
| 5 | **Messages 缺思考与缓存**：`YoMessageRequest` 没有 `thinking`、`cache_control`、`output_config/effort`；`YoMessageTool` 没有 `strict/input_examples/defer_loading`，也没有服务端工具（web_search/web_fetch/code_execution）。 | P0 |
| 6 | **DeepSeek 方言只做了半套**：只有 `max_tokens` 字段名和文件块形状；没有 `thinking` 对象、没有 `/beta` 前缀续写（`prefix:true`）、没有 `json_object` 限制校验、没有 `stop<=16`、没有 `frequency/presence_penalty` 剔除。 | P0 |
| 7 | **只有 STT，没有 TTS**：`YoSpeech` 仅 `/audio/transcriptions`，`YoTranscription` 只有 text/language/duration；缺 `/audio/speech`、流式 TTS、SSE、`instructions`、`response_format`、`speed`。 | P1 |
| 8 | **Embedding / Rerank / Batch / Files / Moderation / Token 计数 / 图像视频生成全空白**；`YoServiceKind` 与 `YoServiceEndpoints` 已有 `EMBEDDINGS/AUDIO_SPEECH` 的“半个实现”，但没有资源类、没有 `YoAiClient` 入口。 | P1 |
| 9 | **用量模型太粗**：`YoChatUsage` 只有 prompt/completion/total + cached + cacheCreation + cost；缺 `reasoning_tokens`、`audio_tokens`、`image_tokens`、`cache_write_tokens`、`accepted/rejected_prediction_tokens`；TTS/ASR 的费用按“秒”而不是 token。 | P1 |
| 10 | **模型目录只覆盖 OpenAI 兼容 `/models`**：没有把 Anthropic Models API 的 `capabilities.*`、OpenAI 的模型能力、Qwen 的模态/上下文元数据规范化；`YoModelCategory` 已认 `IMAGE_GENERATION/VIDEO_GENERATION/RERANK/MODERATION` 但 `YoServiceKind.dedicatedFor` 返回 `null`（只识别、不服务）。 | P2 |

---

## 1. 本次审查的代码证据（可逐条复核）

| 主题 | 位置 | 观察到的事实 |
|---|---|---|
| 三条协议 | `api/YoProtocol.java` | 只有 `CHAT_COMPLETIONS / RESPONSES / MESSAGES`；无 `EMBEDDINGS/IMAGES/...`（正确设计，但服务层没补齐） |
| 客户端入口 | `api/YoAiClient.java` | 只暴露 chat/responses/messages/models/completions/latencyProbe；无 embeddings、speechSynthesis、rerank、batch、files |
| 统一补全 | `api/YoCompletions.java`、`completion/YoCompletionRequest.java` | 只有 system/turns/temperature/topP/maxOutputTokens；不含 tools、结构化输出、思考、缓存 |
| Chat 请求 | `chat/YoChatCompletionRequest.java` | 有 temperature/topP/maxCompletionTokens/tools/toolChoice/responseFormat/stop/parallelToolCalls/reasoningEffort/frequencyPenalty/presencePenalty/n/seed/store/serviceTier/metadata/modalities；**无 topK/logprobs/topLogprobs/logitBias/user/safetyIdentifier/promptCacheKey/promptCacheOptions/audio/webSearchOptions/thinking/extraBody** |
| Chat 消息 | `chat/YoChatMessage.java` | 有 role/content/parts/toolCalls/toolCallId/refusal；**无 reasoningContent、name、audioId** |
| Chat 内容块 | `chat/YoChatContentPart.java` | `TEXT/IMAGE_URL/INPUT_AUDIO/FILE`；**无 VIDEO_URL、FILE_URL、CACHE_BREAKPOINT**；`input_audio` 仅 wav/mp3；`file` 仅 fileId/fileData+filename |
| Chat 编解码 | `internal/chat/ChatCodec.java` | **`decodeMessage` 不读 `reasoning_content`**、不读 `annotations`、不读 `message.audio`、不读 `logprobs`；`decodeUsage` 只填 3 个 token 数 + cache 两项 + cost；编码只写固定字段集 |
| Chat 响应 | `chat/YoChatChoice.java`、`YoChatCompletion.java`、`YoChatChunk.java` | 只有 index/message/finishReason + id/model/choices/usage/rawJson；无 logprobs、annotations、audio |
| Chat 用量 | `chat/YoChatUsage.java` | prompt/completion/total/cached/cacheCreation/cost；**无 reasoning/audio/image/cache_write/accepted/rejected** |
| 平台适配 | `platform/YoPlatform.java` | `protocols()/chatTarget()/responsesTarget()/messagesTarget()/chatPartEncoder()/chatPolicy()/responsesPolicy()/messagesPolicy()/modelListing()`；扩展点集中在 policy/codec，但只覆盖“检查 + 少量 apply” |
| DeepSeek 方言 | `platform/deepseek/DeepSeekChatPartEncoder.java`、`DeepSeekChatPolicy.java` | 仅 `max_tokens` 字段名 + 扁平 file 块 + 图片数量/URL 长度检查；**无 thinking/prefix/beta/json_object 限制** |
| SenseNova 方言 | `platform/sensenova/SenseNovaResponsesPolicy.java` | 已有 `apply(JSONObject)` 先例（注入 `enable_thinking`），可推广为通用方言 |
| Responses 请求 | `responses/YoResponseRequest.java` | 有 model/input/instructions/previousResponseId/tools/toolChoice/parallelToolCalls/maxOutputTokens/temperature/topP/store/textFormat/generate；**无 background/contextManagement/include/conversation/promptCacheOptions/reasoning/textVerbosity/truncation/maxToolCalls/safetyIdentifier/serviceTier/metadata** |
| Responses 工具 | `responses/YoResponseTool.java` | 只有 `function(name,description,parameters,strict)`；**无内置工具类型** |
| Responses 连接 | `api/YoResponsesConnection.java` | 已支持 WebSocket + `create(streamId,...)` + `steer(...)`，这一点领先多数 SDK |
| Messages 内容 | `messages/YoMessageContent.java` | 有 TEXT/IMAGE/DOCUMENT/TOOL_USE/TOOL_RESULT/THINKING/REDACTED_THINKING；**无 cache_control、无 citations 配置、无 image transformations、无 server_tool_use/web_search_tool_result** |
| Messages 请求 | `messages/YoMessageRequest.java` | 有 maxTokens/messages/system/tools/toolChoice/temperature/topP/stopSequences/userId/topK；**无 thinking/cacheControl/outputConfig/metadata/serviceTier/contextManagement/container** |
| Messages 工具 | `messages/YoMessageTool.java` | 仅 name/description/inputSchema；**无 strict/input_examples/defer_loading/cache_control**；无服务端工具 |
| 语音 | `speech/YoSpeech.java`、`YoTranscription.java`、`SpeechEndpoints.java` | 仅 `/audio/transcriptions`；结果仅 text/language/duration/rawJson；**无 TTS、无翻译、无流式、无 diarization/timestamps/logprobs** |
| 实时语音 | `realtime/YoRealtimeDialect.java`、`YoRealtimeEventType.java`、`YoRealtimeConfig.java` | 已支持 `OPENAI_BETA / OPENAI_GA / QWEN` 三方言；事件抽象为 OPENED/SPEECH_STARTED/STOPPED/TEXT_DELTA/AUDIO_CHUNK/RESPONSE_DONE/SESSION_UPDATED/USER_TRANSCRIPT/TOOL_CALL/ERROR/CLOSED；**无 WebRTC/AOQ、无翻译事件、无 output modality 之外的服务端 VAD 细项** |
| 模型规格 | `models/spec/YoModelSpec.java`、`YoModelCategory.java`、`YoModelLimits.java`、`YoReasoning.java`、`YoModelCapability.java` | 设计很完整；但 `IMAGE_GENERATION/VIDEO_GENERATION/RERANK/MODERATION` 仅识别；`YoServiceKind.dedicatedFor` 对它们返回 `null` |
| 服务种类 | `models/YoServiceKind.java`、`YoServiceEndpoints.java` | 已有 `AUDIO_SPEECH`、`EMBEDDINGS` 枚举与端点拼装；**但没有对应 Resource、没有 YoAiClient 访问器** |
| 传输 | `internal/transport/OkHttpTransport.java` | 已处理 408/429/5xx 重试、`Retry-After`/`retry-after-ms`、取消、流式；质量好；缺幂等键/请求级超时/流式重试策略 |
| 错误 | `error/YoAiErrorKind.java` | 13 类，覆盖 auth/permission/quota/rate/notfound/badrequest/server/network/timeout/cancelled/protocol/unsupported/unknown；**缺 CONTENT_FILTERED、CONTEXT_LENGTH、UNSUPPORTED_PARAM、OVERLOADED 的独立归类** |

---

## 2. 厂商能力矩阵（2026 版文档核对）

图例：● 原生支持；◐ 部分/受限；○ 无；— 未验证。

| 能力/协议 | OpenAI | Anthropic | DeepSeek | Qwen(百炼) |
|---|---|---|---|---|
| Chat Completions | ● `/v1/chat/completions` | ○（用 Messages） | ● `deepseek-flash/deepseek-v4-pro` | ● `/compatible-mode/v1/chat/completions` |
| Responses | ● `/v1/responses`（主推） | ○ | ● `/responses` | ● `/compatible-mode/v1/responses` |
| Anthropic Messages | ○ | ● `/v1/messages` | ● `/anthropic/v1/messages` | ● `/apps/anthropic/v1/messages` |
| Realtime（WebSocket） | ● beta + GA | ○ | ○ | ● `/api-ws/v1/realtime?model=` |
| Realtime（WebRTC/AOQ） | ● WebRTC | ○ | ○ | ● WebRTC + AOQ |
| TTS | ● `/v1/audio/speech` | ○ | ○ | ● `audio/tts/SpeechSynthesizer` |
| STT | ● `/v1/audio/transcriptions`（含 diarize/stream） | ○ | ○ | ● 同步/异步/实时 |
| 音频生成（场景音） | ○（TTS 近似） | ○ | ○ | ● `qwen-audio-3.1-tts-next` |
| Embeddings | ● `/v1/embeddings` | ●（Embeddings 文档） | ○（未见） | ● `/embeddings` + 原生 |
| Rerank | ○ | ○ | ○ | ● `text-rerank` / `qwen3-vl-rerank` |
| 图像生成/编辑 | ● Images API + `image_generation` 工具 | ○ | ○（视觉输入） | ● `qwen-image`/`wan` |
| 视频生成 | ○（Sora 未在本次核对的 API 索引中） | ○ | ○ | ● `wan3.0-video` |
| Batch | ● `/v1/batches` | ● `/v1/messages/batches` | 未列 | ● `/v1/batches` |
| Files | ● `/v1/files` + Uploads + Vector Stores | ● `/v1/files` | ● Files API | ● `/v1/files` + 临时 URL |
| 结构化输出 | ● `json_schema`（Chat/Responses） | ● `output_config.format`（Structured outputs） | ◐ 仅 `json_object` | ● `json_schema` |
| 思考/推理 | ● `reasoning_effort` / `reasoning.effort` | ● `thinking:{adaptive/enabled/disabled/between_tools}` + `effort` + `budget_tokens` | ● `thinking:{type}` + `reasoning_effort` | ● `enable_thinking`/`thinking_budget` |
| 提示缓存 | ● 自动 + 显式 `prompt_cache_breakpoint`（30m/12h） | ● `cache_control`（自动或显式，5m/1h） | ● 自动磁盘缓存（`prompt_cache_hit/miss`） | ● 隐式上下文缓存 |
| 客户端工具 | ● function + custom | ● client tools + `strict/input_examples/defer_loading` | ● function（thinking 模式不支持 required/named） | ● function |
| 服务端内置工具 | ● web_search/file_search/code_interpreter/computer/image_generation/mcp/tool_search/apply_patch | ● web_search/web_fetch/code_execution/bash/text_editor/tool_search/computer/memory/advisor + MCP connector | ● Search & Fetch API（文档级） | ● web_search/code_interpreter/文搜图/图搜图/知识检索/MCP |
| Token 计数 | ● `/v1/responses/input_tokens` | ● `/v1/messages/count_tokens` | 未列 | 未列（有 usage 估算） |
| 会话状态 | ● Conversations + `previous_response_id` | ● Message threads / compaction | ● Responses API | ● Conversations |
| 内容审核 | ● Moderations + `moderation` 参数 | ○（Safety 由服务端） | 内容安全过滤 | ● `X-DashScope-DataInspection` / AI 安全护栏 |

---

## 3. 缺口清单（按优先级）

### P0：不补就会在真实厂商上“报错或丢数据”

| ID | 缺口 | 影响 | 建议 |
|---|---|---|---|
| G1 | `ChatCodec.decodeMessage` 丢 `reasoning_content`；`YoChatMessage`/`YoChatChoice` 无思考字段 | DeepSeek 思考、Qwen3.8 思考、GLM 思考全部不可见；用户只看到最终答案，无法做“思考中”UI | 在 `YoChatMessage` 增加 `reasoningContent`，ChatCodec 解码/编码双向支持；`YoChatUsage` 增 `reasoningTokens` |
| G2 | 内容块缺 `VIDEO_URL` / `FILE_URL` / `CACHE_BREAKPOINT` | Qwen 视频输入、OpenAI file 输入、OpenAI/Anthropic 缓存断点都无法表达 | `YoChatContentPart.Kind` 扩为 `TEXT/IMAGE_URL/VIDEO_URL/INPUT_AUDIO/FILE/CACHE_BREAKPOINT`；FILE 增 `fileUrl`；所有块带可选 `cacheBreakpoint` |
| G3 | Chat 请求缺 `top_k/logprobs/top_logprobs/logit_bias/user/safety_identifier/prompt_cache_key/prompt_cache_options/audio/web_search_options/thinking/extra_body` | 参数被静默丢弃，用户看到“怎么调都不生效” | 在 `YoChatCompletionRequest` 增通用 `YoChatOptions`（可空）+ `extraBody(JSONObject)`；编码器按方言映射 |
| G4 | Responses 只支持 function 工具 | 无法使用 OpenAI 的 web_search/file_search/code_interpreter/mcp；与 2026 主推 API 脱节 | `YoResponseTool` 增 `ServerTool(type, options)`；`YoResponseRequest` 增 `background/include/contextManagement/conversation/promptCacheOptions/reasoning/textVerbosity/truncation/maxToolCalls/safetyIdentifier/serviceTier/metadata` |
| G5 | Messages 缺 `thinking`、`cache_control`、`output_config`、`strict` 工具 | Claude 5 思考/缓存/结构化输出无法使用；`cache_control` 缺失导致长上下文成本翻倍 | `YoMessageRequest` 增 `YoThinkingConfig`（type/display/budget/effort）；`YoMessageContent`/`YoMessageTool` 增 `cacheControl`；工具增 `strict/inputExamples/deferLoading` |
| G6 | DeepSeek 方言半套 | `json_schema` 请求 400、`stop` 超过 16 被拒、`frequency/presence_penalty` 无意义字段、`prefix` 续写不可用 | `DeepSeekChatPolicy` 增加：`responseFormat!=TEXT/JSON_OBJECT` → 拒绝；`stop.size()>16` → 拒绝；剔除 `frequency/presence`；`DeepSeekChatPartEncoder.maxOutputTokensField()` 已处理 `max_tokens`；新增 `DeepSeekPrefix` 能力位与 `/beta` base URL |
| G7 | `YoChatUsage` 细节不足 | 成本核算与“思考/音频/图片”用量不可解释 | 增 `reasoningTokens/audioTokens/imageTokens/cacheWriteTokens/acceptedPredictionTokens/rejectedPredictionTokens`；`UsageFields` 解析 `prompt_tokens_details.*`、`completion_tokens_details.*` |

### P1：能力空白，影响产品上限

| ID | 缺口 | 影响 | 建议 |
|---|---|---|---|
| G8 | 无 TTS 资源 | 耳机 App 的语音播报只能外挂 | 新增 `YoSpeechSynthesis`（`/audio/speech` + `/audio/tts/SpeechSynthesizer` 两种方言），支持 `input/model/voice/instructions/responseFormat/speed/streamFormat` |
| G9 | STT 结果太窄 | 无法做说话人分离、时间戳、流式字幕 | `YoTranscription` 增 `segments/words/speakers/usage`；请求增 `responseFormat/timestamps/stream/diarization/knownSpeakers` |
| G10 | 无 Embedding/Rerank | RAG、语义搜索、知识库无法落地 | 新增 `api/YoEmbeddings`、`api/YoRerank`；请求/响应按 OpenAI/Qwen 双方言 |
| G11 | 无 Batch/Files | 批量离线任务、文件引用/临时 URL 无法用 | 新增 `api/YoBatch`、`api/YoFiles`；统一 `YoBatchJob`（submit/retrieve/cancel/list）与 `YoFileRef` |
| G12 | 无图像/视频生成 | Qwen 图像/视频能力完全未接入 | 新增 `YoImageGeneration`、`YoVideoGeneration`；统一 `YoGenerationJob`（异步任务轮询） |
| G13 | 无 Token 计数 | 无法做上下文预算、路由与前置校验 | 新增 `YoTokenCounter`（Anthropic `/messages/count_tokens`、OpenAI `/responses/input_tokens`） |
| G14 | 无 Moderation | 端侧内容安全只能靠服务端 | 新增 `YoModeration`（OpenAI `/moderations`、Qwen `X-DashScope-DataInspection` 可选） |
| G15 | 模型目录不完整 | 无法按“模态/上下文/思考/工具”自动选模型 | `YoModelListing` 增 `capabilityOf(model)`；解析 Anthropic `capabilities.*`、OpenAI model 元数据、Qwen `list-models` |
| G16 | `YoServiceKind` 有枚举无服务 | 路由会“知道有这条路，但走不通” | 要么补齐 Resource，要么在 `dedicatedFor` 返回 `null` 时明确抛 `UNSUPPORTED_CAPABILITY`，不要静默 |

### P2：工程/体验/架构债

| ID | 缺口 | 建议 |
|---|---|---|
| G17 | 无请求级超时/幂等键 | `YoRequestOptions` 增 `timeoutMs/idempotencyKey`；流式单独 `streamIdleTimeout` |
| G18 | 无 `Retry-After` 之外的限流头解析 | 解析 `x-ratelimit-*`，暴露 `YoRateLimitSnapshot` |
| G19 | 无日志脱敏策略 | 统一 `YoRedactor`（Authorization/api key/base64 大字段） |
| G20 | `YoMessageToolChoice` 不支持 `disable_parallel_tool_use` | 补 `parallelToolCalls` / `disableParallelToolUse` |
| G21 | `YoChatTool` 无 `custom` 工具（OpenAI custom tool call） | 扩 `Kind.FUNCTION/CUSTOM` |
| G22 | `YoModelCategory.MODERATION` 无输入输出定义（产物是判定） | 增加 `YoModelCategory.RESULT` 或 `YoVerdict` 输出类型 |
| G23 | `YoSpeech` 用 per-call baseUrl/apiKey，而 `YoAiClient` 用平台绑定 | 统一到 `YoAiClient.speech(platformId)`，避免两套凭据路径 |
| G24 | 错误码无 `CONTENT_FILTERED/CONTEXT_LENGTH/UNSUPPORTED_PARAM` | 扩 `YoAiErrorKind`，映射各厂商 `finish_reason=content_filter`、`context_length_exceeded`、400 参数错误 |

---

## 4. 目标架构（v2）

### 4.1 设计原则（在现有代码上增量，不推倒）

1. **协议（Protocol）与服务（Service）分离**：`YoProtocol` 仍只表示三条“对话方言”；`YoServiceKind` 扩为所有能力服务（chat/messages/responses/realtime/tts/stt/embeddings/rerank/images/video/batch/files/token-count/moderation）。
2. **方言（Dialect）承担全部厂商差异**：字段名、必填/禁用参数、错误码、内容块形状、事件名，全部由 `YoDialect` 的子类处理；domain 层不出现 `if (platform == deepseek)`。
3. **领域模型只表达“语义”**：`YoContentPart` 是语义联合体；`cache_control`、`provider_extras` 这类是“传输提示”，放在可选 `WireHint` 里，不污染语义。
4. **能力可查询**：每个模型/平台都通过 `YoCapabilityRegistry` 返回 `YoSupport` 三态；调用前用 `YoModelSpec.accepts/produces/supports` 做校验，避免“发出去才 400”。
5. **异步任务是一等公民**：图像/视频/长音频/批量统一 `YoJob<T>`（submit → poll → cancel → result），不让每个 API 自己写轮询。
6. **用量与费用可解释**：`YoUsage` 记录 token 细节、媒体秒数、缓存读写、币种；`YoUsageLedger` 按平台/模型/来源聚合。
7. **安全边界**：密钥只在 `YoCredentialProvider` 里，`toString()` 永不输出；请求/响应日志统一脱敏。

### 4.2 领域模型（建议的新类图）

```
YoContentPart (sealed interface)
 ├─ TextPart(text, cacheHint?)
 ├─ ImagePart(source: Url|Base64|FileId, detail, transformations?)
 ├─ VideoPart(source: Url|Base64|Frames, fps?)
 ├─ AudioPart(source: Url|Base64, format, sampleRate?)
 ├─ FilePart(fileId|fileUrl|fileData, filename, mimeType?)
 ├─ ToolUsePart(id, name, input)
 ├─ ToolResultPart(toolUseId, content, isError)
 ├─ ThinkingPart(text, signature)
 ├─ RedactedThinkingPart(data)
 └─ CacheBreakpointPart(mode, ttl?)          // OpenAI/Anthropic 复用
```

```
YoMessage(role: system|developer|user|assistant|tool,
          content: String?,
          parts: List<YoContentPart>?,
          toolCalls: List<YoChatToolCall>,
          toolCallId: String?,
          reasoningContent: String?,
          refusal: String?,
          name: String?,
          audioId: String?)
```

```
YoGenerationRequest
 ├─ model, messages/turns, system
 ├─ YoGenerationParams (temperature/topP/topK/maxOutputTokens/stop/seed/...)
 ├─ YoReasoningRequest (mode, effort, budgetTokens, display)
 ├─ YoOutputFormat (text/jsonObject/jsonSchema(name,schema,strict))
 ├─ YoCacheHint (mode: auto|explicit, ttl, breakpoints)
 ├─ List<YoChatTool> (function/custom)
 ├─ List<YoServerTool> (web_search/file_search/code_interpreter/computer/mcp/...)
 ├─ YoRequestOptions (timeoutMs/idempotencyKey/user/safetyIdentifier/metadata/extraBody)
 └─ protocol: YoProtocol? + serviceKind: YoServiceKind?
```

```
YoResponse
 ├─ id/model/created
 ├─ choices: List<YoChoice(index, message, finishReason, logprobs, annotations)>
 ├─ outputItems: List<YoOutputItem>   // Responses 专用
 ├─ usage: YoUsage
 ├─ rawJson
```

```
YoUsage
 ├─ inputTokens, outputTokens, totalTokens
 ├─ reasoningTokens?, audioTokens?, imageTokens?, cacheWriteTokens?, cachedTokens?
 ├─ acceptedPredictionTokens?, rejectedPredictionTokens?
 ├─ mediaSeconds? (TTS/ASR)
 └─ cost?, currency?
```

### 4.3 服务层端口（新增）

| 端口 | 关键方法 | 说明 |
|---|---|---|
| `YoChatCompletions`（已有） | `complete/stream` | 扩展参数模型即可 |
| `YoResponses`（已有） | `complete/stream/ws` | 扩内置工具与状态字段 |
| `YoAnthropicMessages`（已有） | `complete/stream` | 扩 thinking/cache/output_config |
| `YoEmbeddings`（新） | `embed(YoEmbeddingRequest)` / `embedBatch` | OpenAI `/v1/embeddings` 与 Qwen 原生双方言 |
| `YoRerank`（新） | `rerank(YoRerankRequest)` | 仅 Qwen/Cohere 类；返回 `score/index/document` |
| `YoSpeechRecognition`（新，合并 YoSpeech） | `transcribe/streamTranscribe` | 同步/异步/流式；结果含 segments/speakers |
| `YoSpeechSynthesis`（新） | `synthesize(YoSynthesisRequest)` / `stream` | OpenAI `/audio/speech`、Qwen `/audio/tts/SpeechSynthesizer` |
| `YoImageGeneration`（新） | `generate(YoImageRequest) -> YoJob<YoImageResult>` | Qwen 多模态同步 / OpenAI Images |
| `YoVideoGeneration`（新） | `generate(YoVideoRequest) -> YoJob<YoVideoResult>` | Qwen `video-synthesis` 异步 |
| `YoBatch`（新） | `create/retrieve/cancel/list/results` | OpenAI/Qwen `/v1/batches`、Anthropic batches |
| `YoFiles`（新） | `upload/list/get/delete/tempUrl` | OpenAI/Anthropic/DeepSeek/Qwen |
| `YoTokenCounter`（新） | `count(YoCountRequest)` | Anthropic/OpenAI |
| `YoModeration`（新） | `moderate(YoModerationRequest)` | OpenAI/Qwen 可选 |
| `YoConversations`（可选） | `create/get/append/delete` | OpenAI Conversations / Qwen conversations |

### 4.4 方言层（新）

用 `YoDialect` 取代目前“散落的 Policy + PartEncoder”：

```java
public interface YoDialect {
    YoProtocol protocol();
    // 请求编码：语义请求 -> wire JSON；方言决定字段名/禁用项/默认值
    JSONObject encode(YoGenerationRequest request) throws JSONException;
    // 响应解码：wire JSON -> 语义响应；方言决定 reasoning/annotations/audio 位置
    YoResponse decode(String raw);
    // 流式解码：SSE data -> 统一事件
    YoStreamEvent decodeEvent(String data);
    // 错误映射
    YoAiErrorKind errorKind(int status, JSONObject body);
    // 能力校验：哪些参数在该方言不支持
    void validate(YoGenerationRequest request);
}
```

现有 `YoChatPartEncoder.maxOutputTokensField()` 是好的雏形，保留并升级为 `YoDialect.fieldNames()`。

### 4.5 能力注册表与模型目录

- `YoCapabilityRegistry`：从 `YoModelSpec` + 平台声明 + 用户覆盖生成；每个能力 `YoFact` 三态。
- `YoModelListing` 增 `capabilities`：解析
  - Anthropic Models API：`capabilities.thinking.types.*.supported`、`context_window`、`max_output_tokens`；
  - OpenAI：`/v1/models` 能力有限，靠静态表 + 用户覆盖；
  - Qwen `/compatible-mode/v1/models` 与 `list-models` 的模态/上下文；
  - DeepSeek `/models` 仅 id，靠静态表。
- 对 `IMAGE_GENERATION/VIDEO_GENERATION/RERANK/MODERATION`，`YoServiceKind.dedicatedFor` 返回实际服务，不再 `null`。

### 4.6 异步任务抽象

```java
public interface YoJob<T> {
    String id();
    YoJobStatus status();          // QUEUED/RUNNING/SUCCEEDED/FAILED/CANCELLED
    T result();                    // 成功时可用
    void cancel();
}
public interface YoAsyncService {
    <T> YoJob<T> submit(YoJobRequest request, YoJobDecoder<T> decoder);
}
```
- 统一轮询间隔/退避/超时；结果 URL 24h 过期提醒；
- 支持 `X-DashScope-Async: enable`、OpenAI `background: true`、Anthropic batch。

### 4.7 提示缓存抽象

```java
public final class YoCacheHint {
    public enum Mode { AUTO, EXPLICIT }
    Mode mode; Duration ttl; List<YoCacheBreakpoint> breakpoints;
}
```
- OpenAI：`prompt_cache_options{mode,ttl}` + 每个 block `prompt_cache_breakpoint`
- Anthropic：顶层/块级 `cache_control{type:"ephemeral",ttl:"5m"|"1h"}`
- DeepSeek：自动，无需字段；仅解析 `prompt_cache_hit_tokens/miss_tokens`
- Qwen：隐式；解析 `usage.prompt_tokens_details.cached_tokens`

### 4.8 思考/推理抽象

```java
public final class YoReasoningRequest {
    enum Mode { AUTO, ADAPTIVE, ENABLED, DISABLED }
    Mode mode;
    String effort;        // minimal/low/medium/high/xhigh/max（按方言映射）
    Integer budgetTokens; // Anthropic extended thinking / Qwen thinking_budget
    String display;       // summarized/omitted
}
```
- OpenAI：`reasoning_effort` / Responses `reasoning.effort`
- Anthropic：`thinking` + `effort`
- DeepSeek：`thinking{type}` + `reasoning_effort`
- Qwen：`enable_thinking` + `thinking_budget`

### 4.9 工具与内置工具

```java
public sealed interface YoTool {
    record Function(name, description, JSONSchema, strict, inputExamples, deferLoading) implements YoTool {}
    record Custom(name, description, format) implements YoTool {}
    record Server(ServerKind kind, JSONObject options) implements YoTool {} // WEB_SEARCH/FILE_SEARCH/CODE_INTERPRETER/COMPUTER/MCP/IMAGE_GENERATION/TOOL_SEARCH
}
```
- OpenAI Responses：`{"type":"web_search"}`、`{"type":"file_search","vector_store_ids":[...]}`、`{"type":"code_interpreter","container":...}`、`{"type":"mcp","server_label":...}`；
- Anthropic：`web_search_20250305`、`web_fetch_20250910`、`code_execution_*`、`tool_search_*`、`memory_*`；
- Qwen：`enable_search`/`search_options`、`enable_code_interpreter`、`skill`；
- DeepSeek：仅 function。

### 4.10 流式事件总线

统一事件：

```
YoStreamEvent
 ├─ TextDelta(text)
 ├─ ReasoningDelta(text)
 ├─ ToolCallDelta(index,id,name,argsDelta)
 ├─ AudioDelta(base64, sampleRate)
 ├─ AnnotationDelta(citation)
 ├─ UsageUpdate(usage)
 ├─ Done(finishReason)
 └─ Error(kind,message)
```
- Chat SSE：`choices[].delta.content/reasoning_content/tool_calls/audio.data`
- Anthropic SSE：`content_block_delta`（text_delta/thinking_delta/input_json_delta/signature_delta）
- Responses SSE：`response.output_text.delta`/`response.reasoning_summary_text.delta`/`response.function_call_arguments.delta`/`response.completed`
- Realtime WS：已有 `YoRealtimeEventType`，保持独立但可复用 `YoStreamEvent` 的文本/音频部分

### 4.11 实时语音

- 保留现有 `YoRealtime*` 设计（方言枚举、事件抽象、音频格式）；
- 增 `RealtimeTransport` 抽象：`WebSocketTransport` / `WebRtcTransport` / `AoqTransport`；
- 增翻译事件（Qwen `translation.*`）与 `session.video` 配置；
- `YoRealtimeConfig` 增 `outputModalities/text only`、`noiseReduction`、`turnDetection` 已就绪。

### 4.12 用量与计费

- `YoUsage` 按“模态 + 缓存 + 推理”拆分；
- `YoUsageLedger` 增 `mediaSeconds`、`costCurrency`、`cacheWriteTokens`；
- TTS/ASR 按秒计费，`YoUsageRecord` 增加 `billingUnit: TOKENS|SECONDS|IMAGES`。

### 4.13 错误与重试

- `YoAiErrorKind` 增 `CONTENT_FILTERED`、`CONTEXT_LENGTH`、`UNSUPPORTED_PARAM`、`OVERLOADED`；
- `YoAiHttpException` 保留 `requestId`、`retryAfterMs`、`providerCode`；
- 流式：首字节前可重试，首字节后不重试（避免重复文本）；`Retry-After` 已支持。

### 4.14 安全

- `YoCredentialProvider` 统一取密钥；`YoAiClient` 不持有明文；
- `YoRedactor` 对日志/异常做正则脱敏（`Authorization`, `sk-`, `data:...;base64,`）；
- 不记录 `rawJson` 中的大字段；`rawJson` 仅调试模式。

---

## 5. 建议的类/包改动清单

```
api/
  YoAiClient.java                 + embeddings()/rerank()/speech()/images()/videos()/batch()/files()/tokens()
  YoEmbeddings.java               (new)
  YoRerank.java                   (new)
  YoSpeechRecognition.java        (new, 替代/包装 YoSpeech)
  YoSpeechSynthesis.java          (new)
  YoImageGeneration.java          (new)
  YoVideoGeneration.java          (new)
  YoBatch.java                    (new)
  YoFiles.java                    (new)
  YoTokenCounter.java             (new)
  YoModeration.java               (new)
chat/
  YoChatCompletionRequest.java    + options/extraBody
  YoChatMessage.java              + reasoningContent/name/audioId
  YoChatContentPart.java          + VIDEO_URL/FILE_URL/CACHE_BREAKPOINT
  YoChatUsage.java                + reasoning/audio/image/cacheWrite/prediction
  YoChatOptions.java              (new, 通用可选参数)
  YoServerTool.java               (new)
messages/
  YoMessageRequest.java           + thinking/cacheControl/outputConfig/metadata/serviceTier
  YoMessageContent.java           + cacheControl/citations/transformations
  YoMessageTool.java              + strict/inputExamples/deferLoading
  YoThinkingConfig.java           (new)
responses/
  YoResponseRequest.java          + background/include/contextManagement/conversation/promptCacheOptions/reasoning/textVerbosity/truncation/maxToolCalls/safetyIdentifier/serviceTier/metadata
  YoResponseTool.java             + ServerTool
models/
  YoServiceKind.java              + EMBEDDINGS/RERANK/IMAGES/VIDEO/BATCH/FILES/TOKEN_COUNT/MODERATION 实际映射
  YoModelListing.java             + capabilities()
  YoCapabilityRegistry.java       (new)
internal/
  dialect/YoDialect.java          (new)
  dialect/OpenAiChatDialect.java  (new)
  dialect/OpenAiResponsesDialect.java
  dialect/AnthropicMessagesDialect.java
  dialect/DeepSeekChatDialect.java
  dialect/QwenChatDialect.java
  async/YoJob.java / YoJobRunner.java
  files/YoFileStore.java
  cache/YoCacheHint.java
  usage/UsageFields.java          + 细项解析
speech/
  YoSpeech.java                   + synthesize/stream
  YoTranscription.java            + segments/speakers/usage
```

---

## 6. 分阶段路线图（可回滚）

### 阶段 1（1–2 周）：止血
- G1 思考内容解码；G2 内容块扩展；G7 用量细项；
- 新增 `YoChatOptions`/`extraBody`，先让参数“能透传”；
- 测试：DeepSeek `reasoning_content`、Qwen `enable_thinking`、OpenAI `prompt_cache_breakpoint` 的编解码快照。

### 阶段 2（2–3 周）：协议补全
- G3/G4/G5/G6：Chat/Responses/Messages 参数与工具补全；DeepSeek 方言修正；
- 引入 `YoDialect`，把现有 `Policy/PartEncoder` 迁进去（保留旧类做兼容）；
- 测试：四家请求体黄金快照 + 错误码映射。

### 阶段 3（3–4 周）：服务扩展
- G8/G9/G10/G11/G12/G13/G14：TTS/STT 扩展、Embeddings/Rerank/Batch/Files/Images/Video/Token 计数/Moderation；
- 引入 `YoJob` 异步任务统一层；
- 测试：各服务的 mock server 端到端。

### 阶段 4（2 周）：实时与目录
- G15：模型能力目录（Anthropic capabilities / OpenAI / Qwen）；
- 实时增 WebRTC/AOQ 抽象与翻译事件；
- 测试：模型路由“按能力选模型”用例。

### 阶段 5（持续）：工程化
- G17–G24：超时/幂等/限流头/脱敏/错误码/兼容层；
- 发布 `lib_ai` 公开 API 的兼容矩阵与弃用策略。

---

## 7. 验收标准（可写成测试）

1. 同一份 `YoGenerationRequest` 能分别编码成 OpenAI Chat / Anthropic Messages / DeepSeek Chat / Qwen DashScope 的合法 JSON；字段集合差异由方言测试断言。
2. DeepSeek 返回 `reasoning_content` 时，`YoChatMessage.reasoningContent` 非空，且流式 `ReasoningDelta` 能逐段回调。
3. 发送 `responseFormat=JSON_SCHEMA` 到 DeepSeek 时，客户端在本地抛 `UNSUPPORTED_PARAM`，不发网络请求。
4. Anthropic `cache_control` 可放在 system/block/tool 上，编码后与官方示例逐字一致。
5. `YoJob<YoImageResult>` 对 Qwen 图像任务能完成 submit→poll→URL 下载，超时/失败有明确状态。
6. `YoUsage` 能从四家响应中解析出至少 `input/output/total`，并在有字段时解析 `cached/reasoning/audio/image`。
7. `YoAiClient.close()` 能取消所有在途请求、关闭连接池与回调线程；重复 close 幂等。
8. `settings`/日志中不出现任何密钥或 base64 原文。

---

## 8. 取舍与风险

- **不把“所有厂商”塞进一个枚举**：`YoProtocol` 保持三条对话协议；其余能力走 `YoServiceKind`。否则平台设置会存下无数无意义值。
- **方言层会变厚**：这是必要的复杂度；把差异集中在 `dialect/*`，比散落在 domain/UI 更可控。
- **模型能力元数据不可全信**：厂商 `/models` 往往只给 id；以“静态表 + 用户覆盖 + 运行时探测”三层合并，`YoSupport.UNKNOWN` 不等于不支持。
- **异步任务 URL 过期**：所有 `YoJob` 结果必须立即落盘或转存，不能只存 URL。
- **实时语音**：WebRTC/AOQ 的引入会显著增加 Android 侧复杂度；先做 WebSocket（现有）再评估 WebRTC。

---

## 9. 未决问题（需要产品/架构确认）

1. `lib_ai` 是否要覆盖 Gemini / xAI / Moonshot / Zhipu 原生协议？本报告只审了 OpenAI/Anthropic/DeepSeek/Qwen 四家；其余可按同一 `YoDialect` 框架增量接。
2. 图像/视频生成是否属于 `lib_ai` 还是放在 `app/data`？建议放 `lib_ai` 的 `YoImageGeneration`/`YoVideoGeneration`，`app` 只做展示与下载。
3. TTS 的音频播放/缓存策略（采样率、声道、AAC/Opus）由 `lib_ai` 统一还是由 `app` 决定？建议 `lib_ai` 只产出字节/流，播放策略在 `app`。
4. 是否需要 `YoConversations` 服务端会话？若产品只用本地会话，可延后。
5. 是否启用 `extra_body` 逃生舱？建议启用但标记“非稳定 API”，用于厂商新参数先行验证。

---

## 10. 附：本次核对的关键官方文档

- OpenAI：`/api/reference/resources/chat/subresources/completions/methods/create`、`/api/reference/resources/responses/methods/create`、`/api/reference/resources/audio/subresources/{speech,transcriptions}/methods/create`、`/api/docs/guides/prompt-caching`、`/api/reference/resources/{embeddings,batches,files,moderations,conversations}`。
- Anthropic：`/docs/en/api/messages`、`/docs/en/build-with-claude/{thinking,prompt-caching,token-counting,batch-processing,structured-outputs,files}`。
- DeepSeek：`/api/create-chat-completion`、`/guides/{reasoning_model,chat_prefix_completion,kv_cache,fim_completion,json_mode,tool_calls,files_api,responses_api,anthropic_api}`。
- 百炼：见 `docs/ai/qwen-model-api-handbook.md` 的“覆盖与未决”清单。

