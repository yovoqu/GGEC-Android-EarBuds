# Qwen（阿里云百炼 Model Studio）全模型调用方案与输入输出数据处理手册

> 取证方式：使用 `D:\A_yoprogram\Github_Gallery\Windows-YoWebDocPreview` 的抓取/转换能力（`yohu-library` 的 `export_one`：通用网页适配器 GET + 正文提取 + Markdown 转换），批量抓取百炼官方文档并落盘为 Markdown，再逐页核对。
> 抓取产物目录：`D:\A_yoprogram\Github_Gallery\Windows-YoWebDocPreview\target\bailian-docs\`（40+ 篇）。入口文档：`https://docs.bailian.console.aliyun.com/zh/model-studio/get-api-key`。
> 文档快照时间：2026-11（文档站当前版本模型命名已到 Qwen3.8 / DeepSeek-V4 / GLM-5.3 等，具体可用性以你所在地域控制台为准）。
> 本文只做“接口与数据形态”沉淀，不含任何密钥。

---

## 0. 一页结论（先看这个）

1. **所有能力最终收敛到 5 类协议**：
   - ① OpenAI 兼容 `Chat Completions`（`/compatible-mode/v1/chat/completions`）——文本、视觉、Omni 离线、MT、OCR、意图、GUI-Plus 都能走；
   - ② OpenAI 兼容 `Responses`（`/compatible-mode/v1/responses`）——内置 web_search / code_interpreter，`previous_response_id` 管上下文；
   - ③ Anthropic 兼容 `Messages`（`/apps/anthropic/v1/messages`）；
   - ④ DashScope 原生（`/api/v1/services/...`）——参数最全，多模态/向量/重排/语音/图像/视频/异步任务都在这里；
   - ⑤ 实时 WebSocket（`wss://.../api-ws/v1/realtime?model=...`）——Omni-Realtime / Audio-Realtime。
2. **输入数据的三种承载形态**：纯字符串；`content` 数组（`text` / `image_url` / `video_url` / `input_audio`）；`data:` URI 或公网 URL（Base64 内联只在数组元素里）。
3. **输出数据的三种形态**：同步 JSON（`choices[].message` / DashScope `output`）；SSE 增量（`choices[].delta` / `output.audio.data`）；异步任务（`task_id` → `GET /api/v1/tasks/{task_id}` → 结果 URL）。
4. **计量字段**：OpenAI 侧 `usage.{prompt_tokens,completion_tokens,total_tokens,prompt_tokens_details,completion_tokens_details}`；DashScope 侧 `usage.{input_tokens,output_tokens,total_tokens}` 或 `usage.total_tokens`。
5. **图像/视频/长音频/批量** 一律“先提交、后轮询、拿 URL”，URL 通常 24h 有效，必须落盘。

---

## 1. 接入基线

### 1.1 域名与地域
- 新版业务空间专属域名（推荐）：
  - 北京：`https://{WorkspaceId}.cn-beijing.maas.aliyuncs.com`
  - 新加坡：`https://{WorkspaceId}.ap-southeast-1.maas.aliyuncs.com`
  - 美国（弗吉尼亚）：`https://{WorkspaceId}.us-east-1.maas.aliyuncs.com`
  - 德国（法兰克福）：`https://{WorkspaceId}.eu-central-1.maas.aliyuncs.com`
  - 日本（东京）：`https://{WorkspaceId}.ap-northeast-1.maas.aliyuncs.com`
  - 中国香港：`https://{WorkspaceId}.cn-hongkong.maas.aliyuncs.com`
- 旧域名仍可用：`https://dashscope.aliyuncs.com`（北京）、`https://dashscope-intl.aliyuncs.com`（新加坡）。
- **API Key 与地域绑定**：跨地域用错 Key 返回 401 `invalid_api_key`（不是权限问题）。

### 1.2 路径前缀
| 协议 | base_url / endpoint |
|---|---|
| OpenAI 兼容 | `.../compatible-mode/v1` → `/chat/completions`、`/completions`、`/responses`、`/embeddings`、`/files`、`/batches`、`/conversations` |
| Anthropic 兼容 | `.../apps/anthropic` → `/v1/messages` |
| DashScope 原生 | `.../api/v1` → `/services/aigc/text-generation/generation`、`/services/aigc/multimodal-generation/generation`、`/services/aigc/video-generation/video-synthesis`、`/services/audio/asr/transcription`、`/services/audio/tts/SpeechSynthesizer`、`/services/embeddings/text-embedding/text-embedding`、`/services/rerank/text-rerank/text-rerank`、`/tasks/{task_id}` |
| Realtime | `wss://{WorkspaceId}.{region}.maas.aliyuncs.com/api-ws/v1/realtime?model={model}` |
| 决策模型 | `.../compatible-mode/v1/systemone`（SDK 自动拼接） |

### 1.3 鉴权与公共头
```
Authorization: Bearer $DASHSCOPE_API_KEY
Content-Type: application/json
X-DashScope-Async: enable        # 异步任务（图像/视频/ASR filetrans/批量）
X-DashScope-SSE: enable          # DashScope 流式
X-DashScope-DataInspection: {"input":"cip","output":"cip"}   # AI 安全护栏（可选）
```

---

## 2. 模型目录（按文档“选择模型/标准模型”页）

### 2.1 文本生成 & 视觉理解（同一批模型同时支持文本+图片+视频输入）
推荐：
| 模型 ID | 定位 | 上下文 | 最大输出 | 输入 | 输出 |
|---|---|---|---|---|---|
| qwen3.8-max | 旗舰/复杂推理与编程 | 1M | 128k | 文本、图像、视频 | 文本 |
| qwen3.7-plus | 性能/成本均衡（默认） | 1M | 128k | 文本、图像、视频 | 文本 |
| qwen3.8-flash | 快速低成本 | 1M | 128k | 文本、图像、视频 | 文本 |
| deepseek-v4-pro-0813 / deepseek-v4.1-flash | 第三方文本 | 1M | — | 文本 | 文本 |
| kimi-k3 / kimi-k3.1-pro、glm-5.3、MiniMax-M3.1-Flash、mimo-v2.6-pro-ultraspeed、step-5-preview | 第三方 | 1M | — | 文本（部分支持图） | 文本 |

旧版（仍可用）：qwen3.7-max/flash、qwen3.6-plus/flash、qwen3.5-plus/flash、qwen3-coder-flash、qwen-mt-plus/flash/lite、qwen-long、qwen-plus-character、qwen-max、qwen-plus、qwen-flash、qwen3-vl-plus 等。
- 图像/视频参数上限（qwen3.8 系列）：单图最大 16M 像素、视频最长 2 小时/2GB、最多 2048 张图 / 64 个视频。

### 2.2 全模态 Omni
| 模型 | 输入 | 输出 | 调用 |
|---|---|---|---|
| qwen3.8-omni-flash | 文本、图片、音频、视频 | 文本 | Chat Completions / Responses |
| qwen3.5-omni-plus | 文本、图片、音频、视频 | 文本 + 音频 | Chat Completions（离线语音输出） |
| qwen3.8-omni-flash-realtime | 文本、流式音频、视频帧 | 文本 + 音频 | WebSocket / WebRTC / AOQ |
| qwen-audio-3.1-realtime-plus / -flash | 流式音频 | 音频 + 文本 | 实时 WebSocket |

### 2.3 语音
- ASR：`qwen-audio-3.1-asr-flash-message`（实时消息）、`-asr-flash-streaming`（实时流）、`-asr-flash`（非实时，同步）、`-asr-flash-filetrans`（非实时，异步长音频，≤2GB/12h）。
- TTS：`qwen-audio-3.1-tts-plus`、`-tts-flash`（实时/非实时）。
- 音频生成（场景音）：`qwen-audio-3.1-tts-next`（语音+音效+环境音，≤3000 字符，播客 240s / 其他 120s）。

### 2.4 图像 / 视频 / 3D / 世界模型
| 类 | 模型 | 说明 |
|---|---|---|
| 文生图/编辑 | qwen-image-3.0-pro / 3.0、qwen-image-2.x、wan2.7-image-pro / wan2.7-image、wan2.6/2.5/2.2/2.1、z-image-turbo、wanx2.1-imageedit | 输出最大 6 张（qwen-image）/4 张（wan），分辨率 2K/4K |
| 视频 | wan3.0-video / wan3.0-video-prime（All-in-One）、happyhorse-1.1-t2v/i2v/r2v、happyhorse-1.0-video-edit | 异步任务 |
| 3D | Tripo/Tripo-H3.1、Tripo/Tripo-P1.0 | 文/图生 3D |
| 世界模型 | happyoyster-1.0-adventure/directing/acting | 实时交互世界模型（邀测） |

### 2.5 向量与重排
| 模型 | 类型 | 维度 | 单条最大 Token |
|---|---|---|---|
| qwen3.7-text-embedding | 文本 Embedding | 256~2560（默认1024） | 131,072 |
| qwen3.7-text-embedding-flash | 文本 Embedding | 256~1024 | 131,072 |
| text-embedding-v4 / v3 / v2 / v1 | 文本 Embedding | 64~2048 等 | 8192 / 2048 |
| qwen3-vl-embedding、qwen2.5-vl-embedding | 多模态（融合+独立向量） | 256~2560 / 512~2048 | 32,000 |
| tongyi-embedding-vision-plus/flash | 多模态 | 64~1152 / 64~768 | 1,024 |
| qwen3.7-text-rerank、qwen3-rerank、gte-rerank-v2 | 文本重排 | — | 30k / 4k / 4k 每条 |
| qwen3-vl-rerank | 多模态重排 | — | 8,000 每条 |

### 2.6 领域模型
- 翻译：`qwen-mt-plus` / `qwen-mt-flash` / `qwen-mt-lite`（文本）、图像翻译、全模态翻译（Qwen-MT-Uni）。
- 长文档：`qwen-long`（10M，文件上传+引用）。
- 角色扮演：`qwen-plus-character` / `qwen-flash-character`。
- 数据挖掘：`qwen-doc-turbo`（含 PPT 生成 skill）。
- 深入研究：`qwen-deep-research`。
- 对话分析：通义晓蜜。
- 界面交互：`gui-plus` / `gui-plus-2026-02-26`。
- 文字提取：`qwen3.5-ocr`（Qwen-OCR）。
- 意图理解：`tongyi-intent-detect-v3`。
- 决策：`decision-model-preview`。

---

## 3. 协议 A：OpenAI 兼容 Chat Completions（主通道）

### 3.1 请求体
```json
{
  "model": "qwen3.8-max",
  "messages": [
    {"role": "system", "content": "You are a helpful assistant."},
    {"role": "user", "content": "你好"}
  ],
  "stream": false,
  "stream_options": {"include_usage": true}
}
```
- `messages`：`role` ∈ `system|user|assistant|tool`；通常 `system` 只能出现在 `messages[0]`；最后一轮必须是 `user`；`user/assistant` 交替。
- `content` 两种形态：
  1. **string**：纯文本；
  2. **array**：多模态内容块
     - `{"type":"text","text":"..."}`
     - `{"type":"image_url","image_url":{"url":"https://... 或 data:image/png;base64,..."}}`
     - `{"type":"video_url","video_url":{"url":"https://... 或 data:video/mp4;base64,..."},"fps":2}`
     - `{"type":"input_audio","input_audio":{"data":"https://... 或 base64","format":"wav"}}`
  3. 视频也可以用“图像帧列表”：DashScope 原生 `{"video":[...],"fps":2}`；OpenAI 侧用多张 `image_url` + `fps` 语义。

### 3.2 常用参数（OpenAI 兼容页）
`model`、`messages`、`temperature`[0,2)、`top_p`(0,1)、`top_k`、`presence_penalty`[-2,2]、`frequency_penalty`、`repetition_penalty`、`n`(1-4)、`max_tokens`、`max_completion_tokens`（含思维链，思考模型推荐）、`seed`、`stop`、`stream`、`stream_options.include_usage`、`logprobs`、`top_logprobs`、`response_format`（`text` / `json_object` / `json_schema`）、`tools`、`tool_choice`、`parallel_tool_calls`、`enable_thinking`、`thinking_budget`、`reasoning_effort`（Responses 侧）、`enable_search`、`search_options`、`enable_code_interpreter`、`vl_high_resolution_images`、`max_pixels`/`min_pixels`（部分模型）、`use_multichannel`（Omni 空间音频）。

工具定义：
```json
{"type":"function","function":{"name":"get_weather","description":"...","parameters":{"type":"object","properties":{"location":{"type":"string"}},"required":["location"]}}}
```
- `tool_choice`: `auto|none|required|{"type":"function","function":{"name":"..."}}`（思考模型不支持强制指定）。
- 工具结果轮：`{"role":"tool","tool_call_id":"...","content":"..."}`。

### 3.3 非流式响应
```json
{
  "id": "chatcmpl-...",
  "object": "chat.completion",
  "created": 1735120033,
  "model": "qwen3.8-max",
  "choices": [{
    "index": 0,
    "message": {
      "role": "assistant",
      "content": "……",
      "reasoning_content": "……",
      "tool_calls": [{"id":"...","type":"function","function":{"name":"...","arguments":"{...}"}}],
      "audio": {"data":"<base64>","expires_at":1735120033}
    },
    "finish_reason": "stop",
    "logprobs": null
  }],
  "usage": {
    "prompt_tokens": 3019, "completion_tokens": 104, "total_tokens": 3123,
    "prompt_tokens_details": {"cached_tokens": 2048, "text_tokens": 3019, "image_tokens": 0},
    "completion_tokens_details": {"text_tokens": 104, "reasoning_tokens": 0, "audio_tokens": 0}
  }
}
```
- `finish_reason`: `stop | length | tool_calls | null(生成中)`。
- `content` 在 `qwen-vl/audio` 系列可为数组；`vl_enable_image_hw_output` 时附带 `image_hw`。

### 3.4 流式响应（SSE）
- `object: chat.completion.chunk`；`choices[0].delta` 含 `role`（仅首块）、`content`、`reasoning_content`、`tool_calls[]`（`index/id/function.name/function.arguments` 增量）。
- `include_usage=true` 时最后一个 chunk `choices: []` 且带完整 `usage`。
- Omni 语音输出：`delta.audio.data` 为 Base64 音频增量，`expires_at` 为时间戳。

### 3.5 DashScope 原生通道（同一模型，另一套信封）
请求：
```json
{
  "model": "qwen3.8-max",
  "input": {"messages": [{"role":"user","content":"你好"}]},
  "parameters": {"result_format":"message","incremental_output":true,"max_tokens":1024,"temperature":0.7,"top_p":0.8,"seed":123,"stop":["\n\n"],"tools":[],"tool_choice":"auto","enable_search":false,"response_format":{"type":"json_object"}}
}
```
- 纯文本模型：`POST /api/v1/services/aigc/text-generation/generation`
- 多模态模型（qwen3.7-plus / qwen3-vl-plus 等）：`POST /api/v1/services/aigc/multimodal-generation/generation`
- 多模态 `content`：`[{"text":"..."},{"image":"https://..."}]` 或 `[{"video":"https://...","fps":2}]`；音频 `[{"audio":"https://..."}]`。
- 响应：
```json
{"request_id":"...","output":{"text":"...", "finish_reason":"stop",
  "choices":[{"finish_reason":"stop","message":{"role":"assistant","content":"...","reasoning_content":"...","tool_calls":[...]}}]},
 "usage":{"input_tokens":22,"output_tokens":17,"total_tokens":39}}
```
- `result_format=text` 时结果在 `output.text`；`result_format=message` 时在 `output.choices[].message`（VL/Audio 的 `content` 为数组）。
- 流式：`incremental_output=true`，SSE 事件逐块返回。

---

## 4. 协议 B：OpenAI Responses
- Endpoint：`POST .../compatible-mode/v1/responses`
- 请求核心：`model`、`input`（string 或消息数组）、`instructions`、`tools`（`{"type":"web_search"}`、`{"type":"code_interpreter"}`、自定义 function）、`reasoning.effort`、`previous_response_id`、`conversation`、`stream`。
- 响应：`id`（`resp_...`）、`output` 数组（`message` / `reasoning` / `web_search_call` / `function_call` 等 item）、`output_text` 便捷字段、`usage.{input_tokens,output_tokens,total_tokens,input_tokens_details.cached_tokens,output_tokens_details.reasoning_tokens}`。
- 上下文：`previous_response_id=response1.id`（有效期 7 天）；与 `conversation` 互斥。
- 流式事件：`response.output_text.delta`、`response.completed`。

---

## 5. 协议 C：Anthropic 兼容 Messages
- Endpoint：`POST .../apps/anthropic/v1/messages`（base_url 到 `.../apps/anthropic`）
- 请求：`model`、`max_tokens`（必填）、`messages:[{role,content}]`、`system`、`tools`（含 `{"type":"web_search_20250305","name":"web_search"}`）、`stream`。
- 内容块：`{"type":"text","text":...}`、`{"type":"image","source":{"type":"base64|url",...}}`、`{"type":"video",...}`。
- 响应：`id`、`type:"message"`、`role:"assistant"`、`content:[{type:"text"/"tool_use"/"web_search_tool_result",...}]`、`stop_reason`、`usage.{input_tokens,output_tokens,cache_creation_input_tokens,cache_read_input_tokens,server_tool_use.web_search_requests}`。

---

## 6. 协议 D：Conversations（服务端会话对象）
- `POST/GET/DELETE .../compatible-mode/v1/conversations`、`.../{conversation_id}`、`.../{conversation_id}/items`、`.../items/{item_id}`。
- 用途：服务端保存对话 item，Responses 用 `conversation` 参数引用，免手工传历史。

---

## 7. 协议 E：Batch 与 Files
### Batch
- `POST /v1/batches`：`input_file_id`、`endpoint`（如 `/v1/chat/completions`）、`completion_window:"24h"`、`metadata`。
- `GET /v1/batches/{batch_id}` 查询；结果在输出文件（JSONL，每行含 `custom_id` 与 `response`）。
- 输入 JSONL：每行 `{"custom_id":"...","method":"POST","url":"/v1/chat/completions","body":{...}}`。
- 仅 OpenAI 兼容格式；DashScope SDK 不提供 Batch。

### Files
- OpenAI 兼容：`POST /v1/files`（multipart：`file`、`purpose=batch|vision|...`）、`GET /v1/files`、`GET /v1/files/{file_id}`、`DELETE /v1/files/{file_id}`。
- DashScope 原生：`POST /api/v1/files`、`GET /api/v1/files/{file_id}`、`GET /api/v1/files`。
- 临时 URL：`POST` 上传本地文件换取 `get-temporary-file-url`（供多模态引用），有效期有限。
- 文件引用：Chat 中可用 `file_id`（如 `qwen-long` 的 file 消息），或在 Responses 中使用 `file` 输入项。

---

## 8. 各模态细目（输入 → 输出）

### 8.1 视觉理解（Qwen-VL / Qwen3.x）
输入 `content` 数组：
```json
{"role":"user","content":[
  {"type":"text","text":"这是什么"},
  {"type":"image_url","image_url":{"url":"https://... 或 data:image/jpeg;base64,..."}}
]}
```
- 视频：`{"type":"video_url","video_url":{"url":"..."},"fps":2}`；`fps` ∈ [0.1,10]，默认 2.0；`max_frames` 仅 DashScope SDK。
- 图像分辨率控制：`vl_high_resolution_images=true`（Qwen3.8/3.7/3.6/3.5/VL 系列，固定 16384 Token 上限，忽略 `max_pixels`）；否则 `max_pixels`（默认 2621440，最大 16777216）。
- 输出：同 Chat 文本响应；`finish_reason` 可为 `stop/length`；QVQ 只支持流式。

### 8.2 OCR（Qwen-OCR，如 qwen3.5-ocr）
- DashScope 多模态：
```json
{"model":"qwen3.5-ocr",
 "input":{"messages":[{"role":"user","content":[{"image":"https://...","min_pixels":3072,"max_pixels":8388608,"enable_rotate":false}]}]},
 "parameters":{"ocr_options":{"task":"key_information_extraction","task_config":{"result_schema":{"发票号码":"..."}}}}}
```
- 输出：`output.choices[0].message.content[0]["ocr_result"]`（纯文本/结构化 JSON，取决于 `task`）。
- 也可走 OpenAI Vision 兼容（`image_url`）。

### 8.3 全模态 Omni（离线）
输入：
```json
{"role":"user","content":[
  {"type":"text","text":"总结这段录音"},
  {"type":"input_audio","input_audio":{"data":"https://....wav","format":"wav"}}
]}
```
- 也支持 `image_url`、`video_url`。
- 输出：`choices[].message.content`（文本）+ `reasoning_content`；`qwen3.5-omni-plus` 另有 `message.audio.data`（Base64 音频）。
- 默认开启思考；计费拆分 `completion_tokens_details.reasoning_tokens`。
- 空间音频：`use_multichannel=true`（双声道/四声道 FOA）。

### 8.4 实时 Omni / 实时语音（WebSocket）
- 地址：`wss://{WorkspaceId}.{region}.maas.aliyuncs.com/api-ws/v1/realtime?model=qwen3.8-omni-flash-realtime`
- 客户端事件：`session.update`（`modalities:["text"]` 或 `["audio","text"]`、`voice`、`input_audio_format:"pcm"`、`input_audio_transcription`、`turn_detection`、`tools`）、`input_audio_buffer.append`（`audio`: Base64 PCM）、`input_audio_buffer.commit`、`response.create`、`response.cancel`。
- 服务端事件：`session.created/updated`、`input_audio_buffer.speech_started/stopped`、`conversation.item.created`、`response.created`、`response.content_part.added`、`response.audio.delta`（Base64 PCM）、`response.audio_transcript.delta`、`response.text.delta`、`response.done`、`error`（`error.type/code/message/param`）。
- 音频规格：输入 16kHz PCM（多通道 16kHz）；输出默认音色 `Tina`。

### 8.5 语音识别（非实时）
**同步（qwen-audio-3.1-asr-flash）**：
```json
POST /api/v1/services/aigc/multimodal-generation/generation
{"model":"qwen-audio-3.1-asr-flash",
 "input":{"messages":[{"role":"user","content":[{"type":"input_audio","input_audio":{"data":"{AUDIO_URL}"}}]}]},
 "parameters":{"format":"wav","sample_rate":"16000"}}
```
→ `output.sentence.text` / `output.text`（无 `choices`）。

**异步（qwen-audio-3.1-asr-flash-filetrans）**：
```json
POST /api/v1/services/audio/asr/transcription   Header: X-DashScope-Async: enable
{"model":"qwen-audio-3.1-asr-flash-filetrans",
 "input":{"file_urls":["{AUDIO_URL}"]},
 "parameters":{"channel_id":[0],"language_hints":["zh","en"],"diarization_enabled":true,"special_word_filter":{...}}}
```
→ `output.task_id` → `GET /api/v1/tasks/{task_id}` → `output.results[].transcription_url`（JSON，24h）→ 内含 `transcripts[].sentences[{begin_time,end_time,text,speaker_id}]`。

### 8.6 语音合成（非实时）
```json
POST /api/v1/services/audio/tts/SpeechSynthesizer
{"model":"qwen-audio-3.1-tts-flash",
 "input":{"text":"你好，欢迎使用阿里云百炼。","voice":"longanhuan_v3.1","format":"wav","sample_rate":24000}}
```
→ `output.audio.url`（24h）；流式时 SSE 的 `output.audio.data` 为 Base64 片段。
- CosyVoice / MiniMax 走同一端点，`input.voice_setting` 控制 speed/pitch/emotion（MiniMax）。

### 8.7 音频生成（场景音）
```json
POST /api/v1/services/audio/tts/SpeechSynthesizer
{"model":"qwen-audio-3.1-tts-next",
 "input":{"text_prompt":"@voice1 说：…… @voice2 回答：……","references":[{"audio_data":"data:audio/wav;base64,..."}],"format":"wav","sample_rate":48000,"channels":2,"volume":50,"rate":1.0,"seed":42,"enable_aigc_tag":false}}
```
→ `output.audio.url/duration/expires_at`，`usage.duration`（秒）。参考音频 ≤3 条、每条 ≤30s/10MB，支持 WAV/MP3/OGG Opus。

### 8.8 图像生成 / 编辑
```json
POST /api/v1/services/aigc/multimodal-generation/generation
{"model":"qwen-image-3.0-pro",
 "input":{"messages":[{"role":"user","content":[{"text":"一只戴宇航头盔的柴犬，电影感"}]}]},
 "parameters":{"size":"2048*2048","n":1,"prompt_extend":true,"prompt_extend_mode":"direct","seed":123,"watermark":false}}
```
→ `output.choices[0].message.content[0]["image"]`（URL）；编辑时 content 里再放 `{"image":"..."}` 参考图。
- wan2.7-image-pro：`size` 支持 `1K/2K/4K` 或 `宽*高`（768~4096），`enable_sequential`（组图，n 1~12），`thinking_mode`。
- 另：`wan2.7-image-pro` 支持调色盘/多图参考（≤9 张）。

### 8.9 视频生成（Wan3.0）
```json
POST /api/v1/services/aigc/video-generation/video-synthesis   Header: X-DashScope-Async: enable
{"model":"wan3.0-video",
 "input":{"prompt":"……","media":[{"type":"reference_image","url":"..."}]},  
 "parameters":{"resolution":"480P","ratio":"adaptive","duration":20,"prompt_extend":true}}
```
→ `output.task_id` → `GET /api/v1/tasks/{task_id}` → `output.video_url`（或 results）。
- All-in-One：`input.media[].type`（reference_image / reference_video / reference_audio / first_frame / last_frame）+ 提示词意图自动路由；素材分别用“图1/视频1/音频1”指代，最多 20 个。

### 8.10 文本 Embedding
**OpenAI 兼容**：`POST /v1/embeddings`
```json
{"model":"qwen3.7-text-embedding","input":"文本 或 [\"a\",\"b\"] 或 file","dimensions":1024,"encoding_format":"float"}
```
→ `data:[{embedding:[float...],index,object:"embedding"}]`、`model`、`object:"list"`、`usage.total_tokens`（flash 还返回 `prompt_tokens`）。
**DashScope 原生**：`POST /api/v1/services/embeddings/text-embedding/text-embedding`
```json
{"model":"qwen3.7-text-embedding","input":{"texts":["a","b"]},"parameters":{"dimension":1024,"output_type":"dense"}}
```
→ `output.embeddings:[{embedding,index,text_index}]`、`usage.total_tokens`。
- 批大小：qwen3.7 系列 ≤20 行；v3/v4 ≤10；v1/v2 ≤25。
- 输出类型可 `dense` / `dense&sparse`（稀疏向量）。

### 8.11 多模态 Embedding
- `POST /api/v1/services/embeddings/multimodal-embedding/multimodal-embedding`（以 SDK 为准）。
- 请求 `input.contents:[{text}|{image}|{video}]`；`parameters.dimension`。
- 输出：融合向量或独立向量（按模型），字段 `output.embeddings[]`。
- SDK 的 `input` 与 HTTP 的 `input.contents` 结构不同。

### 8.12 重排 Rerank（仅 DashScope）
```json
POST /api/v1/services/rerank/text-rerank/text-rerank
{"model":"qwen3.7-text-rerank",
 "input":{"query":"什么是文本排序模型","documents":["...","..."]},
 "parameters":{"top_n":2,"return_documents":true,"instruct":"Given a web search query, retrieve relevant passages that answer the query."}}
```
→ `output.results:[{index,relevance_score,document?}]`（按分数降序，0~1 仅本请求内可比）、`usage.{prompt_tokens,total_tokens}`。
- 多模态：`query`/`documents` 用 `{"text"|"image"|"video": "..."}`；视频仅 URL；`fps` ∈ [0,1]。

### 8.13 机器翻译（Qwen-MT）
- OpenAI 兼容 `chat/completions`，`extra_body={"translation_options":{"source_lang":"auto","target_lang":"English","terms":[...],"domains":[...]}}`。
- 仅单轮，不支持 system message；输出在 `choices[0].message.content`。
- 图像翻译 / 全模态翻译走各自 API（Qwen-MT-Image / Qwen-MT-Uni），请求含 `image_url` 与 `translation_options`，输出 `translated_image_url` / 文本。

### 8.14 决策模型
- `POST .../compatible-mode/v1/systemone`
```json
{"model":"decision-model-preview","state":"工单文本/对话/对象","questions":{"department":{"type":"choice","criteria":{"billing":"...","technical":"..."}},"escalate":{"type":"noul"},"severity":{"type":"score","criteria":["轻微","部分","核心不可用","严重"]}}}
```
→ `answers.{id}.{type,choice|noul|score,probabilities,confidence,legend}`、`usage.input_tokens`、`latency_ms`。
- 问题数 ≤16；choice ≤255；score 2~255 级；上下文 ≤65536 token。

### 8.15 意图理解（tongyi-intent-detect-v3）
- OpenAI/ DashScope Chat 调用；输入 messages，输出 `choices[0].message.content`，可同时输出意图与函数调用信息（tools 格式同 Function Calling）。

### 8.16 GUI-Plus
- OpenAI 兼容或 DashScope 多模态；`model:"gui-plus"` / `gui-plus-2026-02-26`。
- 输入消息含截图 `image_url` + 指令 text；输出 `output_text` / `choices[0].message.content`（动作指令/结构化）。

### 8.17 Deep Research
- `model:"qwen-deep-research"`（DashScope/兼容通道）；输出 `output.message`（含研究报告与引用）；过程会规划多轮 web_search。

---

## 9. 输入输出数据处理要点（工程视角）

1. **统一消息模型**：`role + content`；`content` 用“块数组”表达多模态，不要拼 HTML。
2. **媒体二选一**：公网 URL 或 `data:<mime>;base64,<data>`；服务端需能访问 URL；Base64 体积大，注意请求体上限与内存。
3. **视频优先传 URL**；帧列表方式必须带 `fps`，否则时序语义丢失。
4. **异步任务三件套**：`X-DashScope-Async: enable` → 拿 `task_id` → 轮询 `GET /api/v1/tasks/{task_id}`；结果 URL 24h 过期，立即下载落盘（图片/视频/音频/转写 JSON 都是）。
5. **流式解析**：OpenAI 侧 SSE `data: {...}` 逐行；DashScope 侧 `X-DashScope-SSE: enable`；增量字段 `delta.content` / `delta.reasoning_content` / `delta.audio.data`；拼接 tool_calls 的 `arguments` 再 JSON 解析。
6. **思考内容**：`reasoning_content` 与最终答案分离；`max_completion_tokens` 同时限制思考+回答；`thinking_budget` 控制思考上限；多轮时可选择是否回传历史 `reasoning_content`（GLM 有 `clear_thinking`）。
7. **Token 计量**：输入含 `image_tokens`/`audio_tokens`；缓存命中看 `prompt_tokens_details.cached_tokens`；输出思考看 `completion_tokens_details.reasoning_tokens`。
8. **结构化输出**：`response_format={"type":"json_schema","json_schema":{"name":"...","schema":{...},"strict":true}}`；DashScope 侧 `response_format` 放 `parameters`，需手写 JSON Schema。
9. **错误信封**：OpenAI 侧 HTTP 4xx/5xx + `{"error":{...}}`；DashScope 侧 `{"request_id","code","message"}`；常见 `InvalidApiKey / AccessDenied / Throttling.RateQuota / DataInspectionFailed / Model not exist`。
10. **地域与 Key**：Key 绑地域；文件/任务/会话也按地域隔离。

---

## 10. 与本地 `lib_ai` 的对接映射（本仓库）

| Qwen 能力 | 建议复用的 lib_ai 抽象 |
|---|---|
| Chat Completions（文本/视觉/Omni） | `api/YoChatCompletions` + `chat/YoChatMessage`、`YoChatContentPart`、`YoChatPartEncoder`、`YoChatTool*` |
| 流式 | `internal/transport/SseDecoder` + `chat/YoChatStreamListener`；Omni 音频走同一条 SSE |
| Responses | `api/YoResponses` + `responses/YoResponse*`、`internal/responses/*` |
| Anthropic Messages | `api/YoAnthropicMessages` + `messages/YoMessage*`、`platform/anthropic/*` |
| DashScope 原生信封 | 目前 lib_ai 以 OpenAI/Anthropic 为主；新增 `platform/dashscope`，把 `input/parameters` 映射到 `YoChatCompletionRequest` 的传输层 |
| 实时 Omni / Audio | `realtime/YoRealtime*`（事件类型、音频格式、turn detection 已有骨架） |
| 语音识别/合成 | `speech/YoSpeech`、`YoWhisperSpeech`、`SpeechEndpoints`、`WavEncoder` |
| Embedding / Rerank | 需新增 `api/YoEmbeddings`、`YoRerank`（当前无对应类） |
| 图像/视频异步任务 | 需新增 `api/YoAsyncTasks`（task 提交 + 轮询 + 结果下载） |
| 模型目录/能力 | `models/YoModelSpec`、`YoCapabilityRouter`、`YoServiceEndpoints` 可按本文表格填充 `qwen*` 条目 |

---

## 11. 覆盖与未决

已核对页面（节选）：
`get-api-key`、`models`、`what-is-model-studio`、`text-generation-model`、`text-generation`、`vision`、`qwen-api-via-dashscope`、`qwen-api-via-openai-chat-completions`、`compatibility-of-openai-with-dashscope`、`compatibility-with-openai-responses-api`、`qwen-vl-compatible-with-openai`、`qwen-vl-ocr`、`qwen-mt`、`qwen-mt-image`、`qwen-mt-uni`、`audio-generation`、`audio-generation-api`、`qwen-omni`、`realtime`、`realtime-api-overview`、`server-events`、`client-events`、`asr-model`、`speech-recognition`、`non-realtime-speech-recognition-user-guide`、`tts-model`、`speech-synthesis`、`non-realtime-tts-user-guide`、`s2s-model`、`text-to-image`、`image-model`、`image-edit-guide`、`wan3-video-generation-guide`、`embedding-interfaces-compatible-with-openai`、`text-embedding-synchronous-api`、`multimodal-embedding-api-reference`、`text-rerank-api`、`batch-inference`、`batch-interfaces-compatible-with-openai`、`async-task-api`、`manage-asynchronous-tasks`、`decision-model-api`、`gui-plus-interface-interaction-model`、`intent-detect-capability`、`qwen-deep-research-api`、`anthropic-api-messages`、`openai-file-interface`、`openai-compatible-conversations`、`base-url`、`regions`、`install-sdk`。

未完全展开/需以控制台实测的：
- 多模态 Embedding 的 HTTP 精确路径与 `input.contents` 字段（SDK 与 HTTP 结构不一致，文档已明示）。
- 各模型 `max_tokens` 默认值/上限表（随模型版本变化，见各模型详情页）。
- Batch 各字段的完整枚举（`completion_window`、`metadata` 限制）。
- 世界模型 happyoyster、3D Tripo 的请求体（仅见目录页，未见完整 API 页）。
- 实时 WebRTC/AOQ 的信令细节（仅见入口与示例链接）。

> 原始抓取正文在 `Windows-YoWebDocPreview\target\bailian-docs\`，需要逐字核对时直接读对应 `.md`。
