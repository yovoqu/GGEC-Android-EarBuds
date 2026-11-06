# lib_ai v2 全量架构设计 · 功能级数据链路与证据链路

> 本文件是 `lib_ai` 改造的**唯一施工蓝图**：每个功能（F01–F25）都写明公开 API、数据链路（从 Builder 到回调的每一步）、证据链路（官方文档 → 黄金报文 → 单测 → 断言）、验收标准与依赖。
> 主代理（Lead）以此文件作为验收依据；子代理按文件中的写作用域施工，不达标即返工。

---

## 0. 基线

- 代码基线：`lib_ai`（本次审查时 `:lib_ai:testDebugUnitTest` 全绿）。
- 构建环境：`gradlew.bat` + JDK 21 + Android SDK（`local.properties: sdk.dir=E:\Android\Sdk`）。
- 基线命令：
  ```powershell
  .\gradlew.bat :lib_ai:testDebugUnitTest --console=plain
  ```
- 证据根目录（官方文档抓取）：`Windows-YoWebDocPreview\target\bailian-docs\`（Qwen）、以及 OpenAI/Anthropic/DeepSeek 在线 Markdown（见各功能“证据链路”）。
- 编码约定：公开类型不得 import `internal.*`/`okhttp3.*`/`okio.*`（Gradle `checkLibAiPublicBoundary` 会失败）。

---

## 1. 全量功能清单

| ID | 功能 | 优先级 | 依赖 | 主责 |
|---|---|---|---|---|
| F01 | Chat 思考内容一等公民（`reasoning_content`） | P0 | — | Lead |
| F02 | Chat 多模态内容块扩展（video/file_url/cache hint/audio 格式） | P0 | — | Lead |
| F03 | Chat 通用选项与 `extra_body` | P0 | F01 | Lead |
| F04 | Chat 用量细项（reasoning/audio/image/cache_write/prediction） | P1 | — | Lead |
| F05 | Chat 方言层（OpenAI/DeepSeek/Qwen 字段映射） | P0 | F01/F03 | T1 |
| F06 | DeepSeek 方言规则与 `/beta` 前缀续写 | P0 | F05 | T1 |
| F07 | Responses 内置工具（web_search/file_search/code_interpreter/computer/mcp/image_generation/tool_search） | P0 | — | T2 |
| F08 | Responses 状态/后台/压缩/缓存/推理/verbosity | P0 | — | T2 |
| F09 | Messages 思考/缓存/结构化输出 | P0 | — | T2 |
| F10 | Messages 工具扩展（strict/input_examples/defer_loading/服务端工具） | P0 | — | T2 |
| F11 | TTS 合成端口（OpenAI/Qwen） | P1 | — | T3 |
| F12 | STT 扩展（diarization/timestamps/stream） | P1 | — | T3 |
| F13 | Embeddings（OpenAI/Qwen） | P1 | — | T4 |
| F14 | Rerank（Qwen） | P1 | — | T4 |
| F15 | Files（OpenAI/Anthropic/DeepSeek/Qwen） | P1 | — | T4 |
| F16 | Batch（OpenAI/Anthropic/Qwen） | P1 | F15 | T4 |
| F17 | Token 计数（OpenAI/Anthropic） | P1 | — | T4 |
| F18 | Moderation（OpenAI/Qwen） | P2 | — | T4 |
| F19 | 图像生成 + 异步任务（Qwen/OpenAI） | P1 | — | T3 |
| F20 | 视频生成 + 异步任务（Qwen） | P1 | F19 | T3 |
| F21 | 客户端装配与访问器（YoAiClient/YoAiAssembly） | P1 | F11–F20 | Lead |
| F22 | 模型能力注册表与目录（capabilities） | P2 | — | T4 |
| F23 | 错误/脱敏/请求选项（timeout/idempotency/rate limit） | P2 | — | Lead |
| F24 | Realtime 扩展（翻译事件/WebRTC/AOQ 抽象） | P2 | — | T3 |
| F25 | 兼容层与弃用策略 | P2 | 全部 | Lead |

---

## 2. 全局架构

### 2.1 分层

```
┌────────────────────────────────────────────────────────────┐
│ 公开 API（api/*）                                          │
│ YoAiClient / YoChatCompletions / YoResponses / YoMessages  │
│ YoEmbeddings / YoRerank / YoSpeech* / YoImage* / YoVideo*  │
│ YoBatch / YoFiles / YoTokenCounter / YoModeration          │
├────────────────────────────────────────────────────────────┤
│ 领域模型（chat/* responses/* messages/* retrieval/* media/*）│
│ 消息/内容块/请求/响应/用量/思考/缓存提示                     │
├────────────────────────────────────────────────────────────┤
│ 方言与编码（internal/dialect/* + internal/*Codec）          │
│ 字段名、必填/禁用、内容块形状、SSE 事件、错误映射             │
├────────────────────────────────────────────────────────────┤
│ 传输（internal/transport/*）                                │
│ OkHttpTransport / SSE / WebSocket / Multipart / 重试         │
├────────────────────────────────────────────────────────────┤
│ 运行时（internal/runtime/*）                                │
│ CallRegistry / CancelToken / OrderedCallbacks / YoJobRunner  │
└────────────────────────────────────────────────────────────┘
```

### 2.2 统一数据链路（每个功能都必须走这条链）

```
调用方 Builder
  → 请求领域对象（Yo*Request）
  → 方言校验（validate：不支持参数本地拒绝）
  → 方言编码（encode：语义 → wire JSON）
  → 传输（POST/GET/WS/Multipart；stream 走 SSE/WS）
  → 方言解码（decode：wire → 领域对象）
  → 用量解析（UsageFields → YoUsage）
  → 回调（onChunk/onEvent/onComplete/onFailure）
  → 账本（YoUsageLedger 记录）
```

### 2.3 统一证据链路（每个功能都必须有）

```
官方文档 URL + 章节
  → 抓取文本/示例（fixture 来源）
  → 黄金请求 JSON（fixtures/<feature>/request.json）
  → 黄金响应 JSON（fixtures/<feature>/response.json）
  → 单测：编码快照 + 解码快照 + 错误映射 + 流式事件
  → 验收断言（见各功能）
```

### 2.4 测试与 fixture 约定

- 单测路径：`lib_ai/src/test/java/com/yovo/lib_ai/<package>/<Feature>Test.java`
- fixture 路径：`lib_ai/src/test/resources/fixtures/<feature>/<case>.json`
- 断言优先级：**wire 字段精确匹配 > 语义字段匹配 > 只判非空**；禁止“只断言不抛异常”。
- 每个新功能至少 3 个用例：happy path、边界（缺字段/超限）、错误路径（4xx 映射）。

---

## 3. 逐功能设计

> 模板说明：**公开 API** 是允许新增/修改的公开类型；**数据链路** 按步骤列出类；**证据链路** 给出文档依据、fixture、测试类；**验收** 是硬门槛。

### F01 Chat 思考内容一等公民

- **目标**：`reasoning_content`/`reasoning` 在低层 Chat API 与统一补全层都可见、可流式。
- **公开 API**：
  - `chat/YoChatMessage` 增 `reasoningContent()`、Builder `.reasoningContent(String)`；
  - `chat/YoChatChoice` 不变（承载在 message）；
  - `completion/YoCompletionResult.reasoning()` 保持。
- **数据链路**：
  1. `ChatCodec.decodeMessage` 读 `message.reasoning_content`，兼容 `message.reasoning`；
  2. `ChatAccumulator` 流式把 `delta.reasoning_content` 写入消息；
  3. `ChatCompletionAdapter` 改为优先 `message.reasoningContent()`，`rawJson` 兜底保留。
- **证据链路**：
  - 文档：DeepSeek `create-chat-completion` 的 `message.reasoning_content`；Qwen `qwen-api-via-dashscope` 的 `reasoning_content`；
  - fixture：`fixtures/f01/response_reasoning.json`、`stream_reasoning.sse`；
  - 测试：`chat/ChatReasoningCodecTest`。
- **验收**：
  1. 非流式响应含 `reasoning_content` 时，`YoChatCompletion.choices()[0].message().reasoningContent()` 精确等于 fixture 值；
  2. `stream` 两个 reasoning chunk 拼接后等于完整思考文本；
  3. 没有 reasoning 字段时字段为 `null`，不抛异常。
- **依赖**：无。

### F02 Chat 多模态内容块扩展

- **目标**：一块内容能表达文本/图片/视频/音频/文件/缓存断点。
- **公开 API**：
  - `chat/YoChatContentPart.Kind` 增 `VIDEO_URL`；
  - `YoChatContentPart` 增 `fps()`、`fileUrl()`、`cacheMode()`、`cacheTtl()`、`withCache(String mode, String ttl)`；
  - 工厂：`videoUrl(url, fps)`、`fileUrl(url)`、`audio(data, format, sampleRate)`（扩展格式）；保留旧工厂。
- **数据链路**：
  1. `OpenAiChatPartEncoder` 编码 `video_url` → `{"type":"video_url","video_url":{"url"},"fps"}`；`file` 支持 `file_id/file_data/filename/file_url`；每个块可附 `prompt_cache_breakpoint`；
  2. `ChatCodec.encodeMessage` 透传 `cacheMode/cacheTtl`；
  3. 解码侧只保证文本/图片/音频，不反解视频（视频是输入侧）。
- **证据链路**：
  - 文档：Qwen `vision` 的 `video_url`/`fps`；OpenAI Chat `FileContentPart` 与 `prompt_cache_breakpoint`；Anthropic `image/document` block；
  - fixture：`fixtures/f02/request_video.json`、`request_file_url.json`、`request_cache_breakpoint.json`；
  - 测试：`chat/ChatContentPartTest`。
- **验收**：
  1. `videoUrl("https://...", 2)` 编码后 `type=video_url`、`fps=2`；
  2. `fileUrl("https://...")` 编码后 `file.file_url` 存在且不含 `file_data`；
  3. `withCache("explicit","30m")` 编码后 `prompt_cache_breakpoint.mode=explicit`、`ttl=30m`；
  4. 非法 data URL / 非法 fps 抛 `IllegalArgumentException`。
- **依赖**：无。

### F03 Chat 通用选项与 extra_body

- **目标**：把低频/厂商特有参数从主请求类移出，仍能透传且可校验。
- **公开 API**：新增 `chat/YoChatOptions`（不可变 Builder）：
  `topK, logprobs, topLogprobs, logitBias(JSONObject), user, safetyIdentifier, promptCacheKey, promptCacheOptions(JSONObject), audio(JSONObject), webSearchOptions(JSONObject), thinking(JSONObject), extraBody(JSONObject)`。
  `YoChatCompletionRequest.Builder.options(YoChatOptions)`。
- **数据链路**：
  1. `ChatCodec.encode` 调 `options.applyTo(json)`；
  2. `extraBody` 最后合并（不覆盖显式字段，冲突时显式字段优先）；
  3. 方言 `YoChatRequestPolicy.apply(JSONObject)` 可再改写（默认空）。
- **证据链路**：
  - 文档：OpenAI Chat 参数清单（`logprobs/top_logprobs/logit_bias/prompt_cache_key/safety_identifier`）；Qwen `top_k` 与 `enable_thinking`；DeepSeek `thinking`；
  - fixture：`fixtures/f03/options_all.json`；
  - 测试：`chat/ChatOptionsTest`。
- **验收**：
  1. 全部字段设置后，编码 JSON 逐字段匹配 fixture；
  2. `extraBody` 中与显式字段同名的键不得覆盖显式值；
  3. 空 options 不产生任何多余字段（与基线请求字节一致）。
- **依赖**：F01。

### F04 Chat 用量细项

- **目标**：一次调用能解释“输入/输出/思考/音频/图片/缓存读/缓存写/预测 token/费用”。
- **公开 API**：`chat/YoChatUsage` 增 `reasoningTokens/audioTokens/imageTokens/cacheWriteTokens/acceptedPredictionTokens/rejectedPredictionTokens`（均可空）；保留旧构造器。
- **数据链路**：`UsageFields` 增：
  `reasoningTokens`（`completion_tokens_details.reasoning_tokens`）、`audioTokens`（`prompt_tokens_details.audio_tokens` 与 `completion_tokens_details.audio_tokens` 分开）、`imageTokens`（`prompt_tokens_details.image_tokens`）、`cacheWriteTokens`（`prompt_tokens_details.cache_write_tokens`）、`accepted/rejected`（`completion_tokens_details.*`）。
- **证据链路**：
  - 文档：OpenAI Chat 响应 `usage` schema；DeepSeek `prompt_cache_hit_tokens/miss_tokens`；Anthropic `cache_creation/read`；
  - fixture：`fixtures/f04/usage_full.json`；
  - 测试：`chat/ChatUsageTest`。
- **验收**：
  1. fixture 全部字段解析正确；
  2. 缺失字段为 `null`，不补 0；
  3. 旧构造器行为不变（三个 token 数）。
- **依赖**：无。

### F05 Chat 方言层（OpenAI/DeepSeek/Qwen）

- **目标**：字段名、禁用项、默认值、错误映射由平台策略决定，`ChatCodec` 不写死。
- **公开 API**：`chat/YoChatRequestPolicy` 增 `default void apply(JSONObject body)`；新增 `platform/qwen/QwenPlatform`；`platform/deepseek` 完善。
- **数据链路**：
  1. `ChatCompletionsResource.spec`：`body = ChatCodec.encode(...)` → `policy.apply(body)`；
  2. `ChatCodec` 只用标准字段名 + `partEncoder.maxOutputTokensField()`；
  3. 错误映射：`YoAiErrors.fromHttp` 增厂商错误码（`context_length_exceeded`、`content_filter`）。
- **证据链路**：
  - 文档：OpenAI Chat；DeepSeek Chat；Qwen DashScope/OpenAI；
  - fixture：`fixtures/f05/request_openai.json`、`request_deepseek.json`、`request_qwen.json`；
  - 测试：`platform/ChatDialectTest`。
- **验收**：
  1. 同一语义请求生成三份 field-set 不同的 JSON，且各自符合对应文档；
  2. 平台不支持的字段不会出现在 wire JSON；
  3. 三份 fixture 编码快照逐字节一致（忽略键顺序）。
- **依赖**：F01/F03。

### F06 DeepSeek 方言规则与 `/beta` 前缀续写

- **目标**：DeepSeek 特有规则本地前置校验，前缀续写可用。
- **公开 API**：`platform/deepseek/DeepSeekChatPolicy` 增：
  - `response_format` 仅 `text|json_object`，`json_schema` 抛 `UNSUPPORTED_PARAM`；
  - `stop.size() <= 16`；
  - 剔除 `frequency_penalty/presence_penalty`；
  - `thinking` 对象允许；
  - `stop` 与 thinking 组合合法；
  - `DeepSeekPlatform.Builder.betaBaseUrl(...)`，`prefix` 能力。
- **数据链路**：
  1. `YoChatCompletionRequest` 的 `options.thinking` 编码到 `thinking`；
  2. `ChatCodec` 支持 `prefix` 标志（`YoChatMessage` 增 `prefix` 可选，仅 DeepSeek 方言发送 `prefix:true`）；
  3. `DeepSeekChatPolicy.apply` 写 `thinking`、剔除禁用字段。
- **证据链路**：
  - 文档：DeepSeek `chat_prefix_completion`（`prefix:true` + `/beta`）、`create-chat-completion`（`thinking`、`response_format`、`stop`、`frequency_penalty` deprecated）；
  - fixture：`fixtures/f06/request_thinking.json`、`request_prefix.json`、`request_stop16.json`；
  - 测试：`platform/DeepSeekDialectTest`。
- **验收**：
  1. `json_schema` 请求在本地抛 `UNSUPPORTED_PARAM`（`YoCapabilityException`），无网络调用；
  2. `stop` 17 项抛 `IllegalArgumentException`；
  3. `prefix` 请求 `baseUrl` 指向 `/beta` 且最后一条 assistant 带 `prefix:true`；
  4. `thinking:{type:"enabled"}` 原样出现在 wire JSON。
- **依赖**：F05。

### F07 Responses 内置工具

- **目标**：Responses 能声明服务端工具，不再只是 function。
- **公开 API**：`responses/YoResponseTool` 增 `ServerTool`（`kind` 枚举 + `JSONObject options`）：`WEB_SEARCH/FILE_SEARCH/CODE_INTERPRETER/COMPUTER/IMAGE_GENERATION/MCP/TOOL_SEARCH`。
- **数据链路**：
  1. `ResponsesCodec.encodeTool` 按 kind 写 `{"type":"web_search", ...}` 等；
  2. `YoResponseRequest` 增 `include`、`maxToolCalls`；
  3. 解码 `output` 时识别 `web_search_call/file_search_call/code_interpreter_call/computer_call/mcp_call/...`，新增 `YoResponseOutputItem` kind。
- **证据链路**：
  - 文档：OpenAI Responses create 的 `tools` 与 `include` 字段；
  - fixture：`fixtures/f07/request_tools.json`、`response_tools.json`；
  - 测试：`responses/ResponsesToolsTest`。
- **验收**：
  1. 7 种工具编码与官方示例一致；
  2. 响应中的 `web_search_call` 等 item 能解码为对应 kind；
  3. 未知工具类型不抛异常（保留 raw）。
- **依赖**：无。

### F08 Responses 状态/后台/压缩/缓存/推理/verbosity

- **目标**：补齐 Responses 的“生产级”字段。
- **公开 API**：`YoResponseRequest` 增：
  `background(Boolean)`, `contextManagement(JSONObject)`, `conversation(String|Object)`, `include(List<String>)`, `promptCacheOptions(JSONObject)`, `reasoning(JSONObject)`, `textVerbosity(String)`, `truncation(String)`, `maxToolCalls(Integer)`, `safetyIdentifier(String)`, `serviceTier(String)`, `metadata(JSONObject)`。
- **数据链路**：
  1. `ResponsesCodec.encode` 逐项写入；
  2. `ResponsesCodec.encodeHttp` 保留 `background`（旧代码 `json.remove("background")` 必须删除）；
  3. `YoResponseObject` 增 `status/background/usage` 已有；解码 `incomplete_details`。
- **证据链路**：
  - 文档：OpenAI Responses create（`background`/`context_management`/`conversation`/`include`/`prompt_cache_options`/`reasoning`/`text.verbosity`/`truncation`/`max_tool_calls`/`safety_identifier`/`service_tier`/`metadata`）；
  - fixture：`fixtures/f08/request_full.json`、`response_background.json`；
  - 测试：`responses/ResponsesStateTest`。
- **验收**：
  1. HTTP 编码包含 `background:true`（旧实现会删掉，必须回归）；
  2. `include` 与 `reasoning` 编码逐字段匹配；
  3. 非流式 `status=incomplete` + `incomplete_details.reason` 可读。
- **依赖**：无。

### F09 Messages 思考/缓存/结构化输出

- **目标**：Anthropic 的 thinking / cache_control / output_config 可用。
- **公开 API**：
  - 新增 `messages/YoThinkingConfig`：`type(adaptive/enabled/disabled/between_tools)`、`budgetTokens`、`display(summarized/omitted)`；
  - `YoMessageRequest` 增 `thinking(YoThinkingConfig)`、`cacheControl(JSONObject)`、`outputConfig(JSONObject)`、`metadata(JSONObject)`、`serviceTier(String)`；
  - `YoMessageContent` 增 `cacheControl` 字段与 `.withCache(...)`；`YoMessageTool` 增 `cacheControl`。
- **数据链路**：
  1. `MessagesCodec.encode` 写 `thinking`、`cache_control`、`output_config`、`metadata`；
  2. `YoMessageContent` 的块级 `cache_control` 写入 `encodeBlock`；
  3. 解码 `usage.cache_creation/read` 已有。
- **证据链路**：
  - 文档：Anthropic `thinking`（adaptive/display/effort）、`prompt-caching`（cache_control 5m/1h）、Structured outputs（output_config.format）；
  - fixture：`fixtures/f09/request_thinking.json`、`request_cache.json`；
  - 测试：`messages/MessagesThinkingTest`。
- **验收**：
  1. `thinking:{type:"adaptive",display:"summarized"}` 编码一致；
  2. system 块与 user 块的 `cache_control` 均能编码；
  3. `output_config.format` 编码一致。
- **依赖**：无。

### F10 Messages 工具扩展

- **目标**：严格工具、示例、延迟加载、服务端工具可用。
- **公开 API**：`YoMessageTool` 增 `strict/inputExamples/deferLoading`；新增 `YoMessageServerTool`（`WEB_SEARCH/WEB_FETCH/CODE_EXECUTION/BASH/TEXT_EDITOR/TOOL_SEARCH/COMPUTER/MEMORY/ADVISOR`）。
- **数据链路**：
  1. `MessagesCodec.encode` 的 tools 数组写入 `strict/input_examples/defer_loading`；
  2. 服务端工具按官方 `type/name` 编码；
  3. 解码 `server_tool_use`、`web_search_tool_result` 等块为 `YoMessageContent` 新类型。
- **证据链路**：
  - 文档：Anthropic `messages` 的 tools/ server_tool_use / web_search_tool_result schema；
  - fixture：`fixtures/f10/request_server_tools.json`；
  - 测试：`messages/MessagesToolsTest`。
- **验收**：
  1. `strict:true`、`input_examples`、`defer_loading` 编码一致；
  2. `web_search_20250305`、`web_fetch_20250910` 编码一致；
  3. 响应中的 `server_tool_use` 能解码且不丢 raw。
- **依赖**：无。

### F11 TTS 合成端口

- **目标**：文本 → 音频字节/流；OpenAI 与 Qwen 双方言。
- **公开 API**：新增 `api/YoSynthesis` + `speech/YoSynthesisRequest`、`YoSynthesisResult`、`YoSynthesisStreamListener`。
- **数据链路**：
  1. OpenAI：`POST /v1/audio/speech` JSON（input/model/voice/instructions/response_format/speed/stream_format）；
  2. Qwen：`POST /api/v1/services/audio/tts/SpeechSynthesizer`（input.text/voice/format/sample_rate）；流式 `X-DashScope-SSE`；
  3. 响应：OpenAI 二进制（audio/*）；Qwen `output.audio.url` 或 SSE `output.audio.data` base64。
- **证据链路**：
  - 文档：OpenAI `audio/speech/create`；Qwen `non-realtime-tts-user-guide` / `音频生成 API参考`；
  - fixture：`fixtures/f11/openai_request.json`、`qwen_request.json`、`qwen_response.json`；
  - 测试：`speech/SynthesisTest`。
- **验收**：
  1. 两种请求编码逐字段匹配；
  2. OpenAI 响应按 `audio/*` 返回字节，不误当 JSON；
  3. Qwen `output.audio.data` base64 可解码为 WAV 头（RIFF）；
  4. 参数越界（speed 超出 0.25–4.0）本地拒绝。
- **依赖**：无。

### F12 STT 扩展

- **目标**：转写结果支持分段/时间戳/说话人/流式。
- **公开 API**：`speech/YoTranscription` 增 `segments/words/speakers/usage`；`YoTranscriptionRequest` 增 `responseFormat/timestamps/stream/diarizationEnabled/knownSpeakers`。
- **数据链路**：
  1. OpenAI multipart：`file/model/response_format/stream/timestamp_granularities/chunking_strategy/known_speaker_names`；
  2. 响应 `verbose_json/ diarized_json` → `YoSegment`；
  3. 流式 SSE `transcript.text.delta/done` → 回调。
- **证据链路**：
  - 文档：OpenAI `audio/transcriptions/create`；
  - fixture：`fixtures/f12/verbose.json`、`diarized.json`、`stream.sse`；
  - 测试：`speech/TranscriptionExtTest`。
- **验收**：
  1. `verbose_json` 的 segments/words 解析正确；
  2. `diarized_json` 的 speaker 标签保留；
  3. 流式 delta 拼接等于 done.text。
- **依赖**：无。

### F13 Embeddings

- **目标**：文本/多模态向量。
- **公开 API**：`api/YoEmbeddings` + `retrieval/YoEmbeddingRequest`、`YoEmbeddingResult`。
- **数据链路**：
  1. OpenAI `POST /v1/embeddings`（model/input/dimensions/encoding_format）；
  2. Qwen `POST /api/v1/services/embeddings/text-embedding/text-embedding`（input.texts/parameters.dimension）；
  3. 响应 `data[].embedding` / `output.embeddings[].embedding`。
- **证据链路**：
  - 文档：OpenAI `embeddings/create`；Qwen `text-embedding-synchronous-api`；
  - fixture：`fixtures/f13/openai_request.json`、`qwen_request.json`、`openai_response.json`；
  - 测试：`retrieval/EmbeddingsTest`。
- **验收**：
  1. 单条与批量输入编码正确；
  2. `dimensions` 支持列表校验；
  3. 响应向量长度与请求一致。
- **依赖**：无。

### F14 Rerank

- **目标**：候选文档相关性排序。
- **公开 API**：`api/YoRerank` + `retrieval/YoRerankRequest`、`YoRerankResult`。
- **数据链路**：
  1. `POST /api/v1/services/rerank/text-rerank/text-rerank`；
  2. `query`/`documents` 支持字符串或 `{text|image|video}`；
  3. 响应 `output.results[].relevance_score`.
- **证据链路**：
  - 文档：Qwen `text-rerank-api`；
  - fixture：`fixtures/f14/request.json`、`response.json`；
  - 测试：`retrieval/RerankTest`。
- **验收**：
  1. top_n 截断正确；
  2. return_documents 时文档原文保留；
  3. 分数排序降序。
- **依赖**：无。

### F15 Files

- **目标**：上传/查询/删除/临时 URL。
- **公开 API**：`api/YoFiles` + `retrieval/YoFileRef`、`YoFileUploadRequest`。
- **数据链路**：OpenAI `/v1/files`、Anthropic `/v1/files`、DeepSeek Files API、Qwen `/v1/files` 与临时 URL；multipart 统一由 `MultipartBody` 构造。
- **证据链路**：文档四家 Files API；fixture `fixtures/f15/upload.json`；测试 `retrieval/FilesTest`。
- **验收**：上传返回 fileId；查询列表分页；删除幂等；无密钥泄漏。
- **依赖**：无。

### F16 Batch

- **目标**：批量提交与结果下载。
- **公开 API**：`api/YoBatch` + `retrieval/YoBatchJob`。
- **数据链路**：OpenAI `/v1/batches`（input_file_id/endpoint/completion_window）；Anthropic `/v1/messages/batches`；Qwen `/v1/batches`；轮询 `GET /v1/batches/{id}`。
- **证据链路**：OpenAI batches create；Anthropic batch-processing；Qwen batch docs；fixture `fixtures/f16/create.json`；测试 `retrieval/BatchTest`。
- **验收**：状态机 `validating→in_progress→completed`；失败原因可读；结果文件按 `custom_id` 对齐。
- **依赖**：F15。

### F17 Token 计数

- **目标**：调用前估算输入 token。
- **公开 API**：`api/YoTokenCounter` + `retrieval/YoTokenCountRequest/Result`。
- **数据链路**：Anthropic `POST /v1/messages/count_tokens`；OpenAI `POST /v1/responses/input_tokens`；响应 `input_tokens`。
- **证据链路**：Anthropic token-counting；OpenAI input tokens；fixture `fixtures/f17/request.json`；测试 `retrieval/TokenCountTest`。
- **验收**：结果 >0；不支持的服务端工具本地提示；与真实调用 usage 差异记录。
- **依赖**：无。

### F18 Moderation

- **目标**：输入/输出内容审核。
- **公开 API**：`api/YoModeration` + `retrieval/YoModerationRequest/Result`。
- **数据链路**：OpenAI `POST /v1/moderations`；Qwen 通过 `X-DashScope-DataInspection` 或独立审核服务（可选）。
- **证据链路**：OpenAI moderations；Qwen content-security；fixture `fixtures/f18/response.json`；测试 `retrieval/ModerationTest`。
- **验收**：`flagged/categories/category_scores` 解析；多模态输入（text/image）分开计分。
- **依赖**：无。

### F19 图像生成 + 异步任务

- **目标**：文生图/编辑，统一异步任务。
- **公开 API**：`api/YoImageGeneration`；`media/YoImageRequest`、`YoImageResult`；`api/YoJob<T>`、`YoJobStatus`。
- **数据链路**：
  1. Qwen `POST /api/v1/services/aigc/multimodal-generation/generation`（同步）；
  2. OpenAI `POST /v1/images/generations`；
  3. 统一 `YoJobRunner` 封装轮询与超时。
- **证据链路**：Qwen `text-to-image`/`image-model`；OpenAI Images；fixture `fixtures/f19/request.json`、`response.json`；测试 `media/ImageGenerationTest`。
- **验收**：返回图片 URL/base64；`n` 数量正确；失败状态可读。
- **依赖**：无。

### F20 视频生成 + 异步任务

- **目标**：文/图生视频。
- **公开 API**：`api/YoVideoGeneration`；`media/YoVideoRequest/Result`。
- **数据链路**：Qwen `POST /api/v1/services/aigc/video-generation/video-synthesis` + `X-DashScope-Async: enable`；`GET /api/v1/tasks/{task_id}`；`output.video_url`。
- **证据链路**：Qwen `wan3-video-generation-guide`；fixture `fixtures/f20/request.json`、`task_succeeded.json`；测试 `media/VideoGenerationTest`。
- **验收**：提交返回 task_id；轮询状态；结果 URL 可下载；超时取消。
- **依赖**：F19。

### F21 客户端装配与访问器

- **目标**：`YoAiClient` 暴露所有服务；`YoAiAssembly` 按平台协议装配。
- **公开 API**：`YoAiClient.embeddings()/rerank()/synthesis()/transcription()/images()/videos()/batch()/files()/tokenCounter()/moderation()`；单平台与按平台 id 两个重载。
- **数据链路**：`YoAiAssembly.open` 构建各资源；关闭时统一 `shutdown`。
- **证据链路**：`wiring/YoAiAssemblyTest`；测试每个访问器非空、关闭后抛 `IllegalStateException`。
- **验收**：所有访问器可用；无资源泄漏；`close()` 幂等。
- **依赖**：F11–F20。

### F22 模型能力注册表与目录

- **目标**：模型能力可查询，路由可依据能力选模型。
- **公开 API**：`models/YoCapabilityRegistry`；`YoModelListing.capabilities()`；`YoModelSpec` 已具备。
- **数据链路**：解析 Anthropic Models API `capabilities.*`、OpenAI `/models`、Qwen `/models`；静态表补充；用户覆盖。
- **证据链路**：Anthropic models API；fixture `fixtures/f22/anthropic_models.json`；测试 `models/CapabilityRegistryTest`。
- **验收**：三态 `YES/NO/UNKNOWN` 正确；`YoModelSpec.category` 由模态推导；路由能按 `FUNCTION_CALLING+VISION` 选模型。
- **依赖**：无。

### F23 错误/脱敏/请求选项

- **目标**：错误分类完整、日志安全、请求可配超时/幂等。
- **公开 API**：`error/YoAiErrorKind` 增 `CONTENT_FILTERED/CONTEXT_LENGTH/UNSUPPORTED_PARAM/OVERLOADED`；新增 `api/YoRequestOptions`；`internal/security/YoRedactor`。
- **数据链路**：`YoAiErrors.fromHttp` 依据 `error.type/code/message` 分类；`YoRedactor` 在异常/日志入口脱敏。
- **证据链路**：各厂商错误码文档；fixture `fixtures/f23/errors.json`；测试 `error/ErrorMappingTest`、`internal/security/RedactorTest`。
- **验收**：8 种错误映射正确；`Authorization`/`sk-`/base64 不出现；**Chat 链路端到端验证** `Call.timeout()` 与 `Idempotency-Key`（`HttpRequestSpec` 的可选字段），其他资源复用同一 spec 时默认空、旧行为不变。
- **依赖**：无。

### F24 Realtime 扩展

- **目标**：Realtime 支持翻译事件与 WebRTC/AOQ 抽象（设计+接口，不要求本轮全实现）。
- **公开 API**：`realtime/RealtimeTransport` 接口；`YoRealtimeEventType` 增 `TRANSLATION_DELTA`；`YoRealtimeConfig` 增 `translationTarget`。
- **数据链路**：现有 `RealtimeProtocol` 事件映射扩展；WebRTC/AOQ 仅接口占位，不破坏 WebSocket。
- **证据链路**：Qwen `realtime`/`qwen-audio-realtime-user-guides`；fixture `fixtures/f24/translation.sse`；测试 `realtime/RealtimeTranslationTest`。
- **验收**：WebSocket 现有用例全绿；翻译事件可回调；WebRTC 接口不参与默认装配。
- **依赖**：无。

### F25 兼容层与弃用策略

- **目标**：旧 API 行为不破坏；新增功能可渐进采用。
- **公开 API**：保留旧构造器/旧方法；新增方法默认实现；`DEPRECATED.md` 标注。
- **数据链路**：旧字段仍按旧编码；新 options 仅在设置时生效。
- **证据链路**：基线测试全绿；`docs/ai/lib-ai-compat-matrix.md`。
- **验收**：所有旧测试不改一行仍通过；公开 API 二进制兼容（新增方法不破坏）。
- **依赖**：全部。

---

## 4. 跨切面设计

### 4.1 用量与账本
- `YoUsageLedger` 记录 `YoUsageRecord`：platform/model/source/tokens/cached/cacheWrite/reasoning/mediaSeconds/cost/currency/atMillis/billingUnit。
- 每个新服务必须回填 usage；无法回填时字段为 `null`，不估算。

### 4.2 缓存
- 统一 `YoCacheHint`（后续实现）：OpenAI `prompt_cache_options + prompt_cache_breakpoint`；Anthropic `cache_control`；DeepSeek 自动；Qwen 隐式。
- 本阶段先落 `YoChatContentPart.withCache` 与 `YoMessageContent.withCache`，其余服务后续。

### 4.3 错误
- 新增错误类型，禁止在 UI 层解析 HTTP 状态码；`YoAiErrors.kindOf` 唯一分类入口。

### 4.4 安全
- 密钥只经 `YoCredentialProvider`；`toString` 不含 key；`YoRedactor` 对所有日志/异常生效；fixture 中不得出现真实 key。

### 4.5 并发与取消
- 所有新资源复用 `CallRegistry/CancelToken/OrderedCallbacks`；`YoJobRunner` 轮询可取消；`close()` 取消全部。

---

## 5. 验收机制（Lead 执行）

### 5.1 Definition of Done（每个功能）
1. 公开 API 与本文一致（签名、可空性、Builder）；
2. 数据链路每一步有对应实现类；
3. fixture 存在且来自官方文档示例；
4. 单测覆盖 happy/边界/错误三路径；
5. `.\gradlew.bat :lib_ai:testDebugUnitTest` 全绿；
6. 公开边界检查通过（不 import internal/okhttp）；
7. 无新增编译警告/弃用；
8. 文档与验收矩阵状态更新为 `PASS`。

### 5.2 返工规则
- 任一验收项失败 → 任务状态回 `pending`，子代理 24h 内返工；Lead 只接受“测试证明”，不接受口头说明。
- 破坏旧测试 → 直接打回。
- fixture 与文档不一致 → 打回。

### 5.3 代码审查清单
- [ ] 请求/响应字段与官方文档逐项对齐；
- [ ] 空值/缺字段不抛异常；
- [ ] 不支持参数在本地拒绝（不发网络）；
- [ ] 流式事件可拼接为完整结果；
- [ ] usage 全解析；
- [ ] 错误映射正确；
- [ ] 无密钥/大 base64 进入日志；
- [ ] 线程/取消/关闭正确。

---

## 6. 子代理分工与写作用域

| 子代理 | 任务 | 允许写 | 禁止写 |
|---|---|---|---|
| Lead | F01–F04、F21、F23、F25、集成、验收 | `chat/*`、`internal/chat/*`、`api/YoAiClient.java`、`wiring/*`、`error/*`、`docs/ai/*` | 其他 |
| T1 `chat-dialects` | F05/F06 | `platform/**`、`catalog/**`、`src/test/java/com/yovo/lib_ai/platform/**` | `chat/**`、`internal/chat/**`、`responses/**`、`messages/**` |
| T2 `responses-messages` | F07–F10 | `responses/**`、`messages/**`、`internal/responses/**`、`internal/messages/**`、对应 test | `chat/**`、`platform/**`、`api/**` |
| T3 `speech-media` | F11/F12/F19/F20/F24 | `speech/**`、`realtime/**`、新增 `media/**`、新增 `api/YoSynthesis.java` 等新文件、对应 test | `chat/**`、`responses/**`、`messages/**`、`api/YoAiClient.java` |
| T4 `retrieval-platform` | F13–F18/F22 | 新增 `retrieval/**`、`api/YoEmbeddings.java` 等新文件、`models/**`、`internal/models/**`、对应 test | `chat/**`、`responses/**`、`messages/**`、`api/YoAiClient.java` |

> `api/YoAiClient.java` 与 `wiring/YoAiAssembly.java` 只能 Lead 修改，避免多代理冲突。
> 所有新增公开类型必须走 `checkLibAiPublicBoundary`：不 import `internal.*`、`okhttp3.*`、`okio.*`。

---

## 7. 里程碑

| 里程碑 | 内容 | 出口条件 |
|---|---|---|
| M0 | 架构/验收矩阵冻结 | 本文 + CSV 审核通过 |
| M1 | F01–F04 合并 | 基线测试 + 新单测全绿 |
| M2 | F05–F10 合并 | Chat 三方言 + Responses/Messages 新功能测试全绿 |
| M3 | F11–F20 合并 | TTS/STT/Embedding/Rerank/Files/Batch/Token/Moderation/Image/Video 测试全绿 |
| M4 | F21–F25 合并 | 客户端访问器、模型能力、错误/脱敏、兼容测试全绿 |
| M5 | 全量回归 | `:lib_ai:testDebugUnitTest` 全绿 + 验收矩阵全 PASS |
