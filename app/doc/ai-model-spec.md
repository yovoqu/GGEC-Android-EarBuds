# lib_ai 模型规格架构

本文记录 `lib_ai` 怎样描述一个模型：它是哪一类、收什么产出什么、体量多大、有哪些服务端能力、会不会推理，
以及这些说法是谁给的。按实际代码写成，代码位置是 `lib_ai/.../models/`（路由、目录、列表解码）和
`lib_ai/.../models/spec/`（规格的值类型）。

## 1. 为什么重做

旧版的模型层有三个结构性问题：

| 问题 | 表现 |
| --- | --- |
| 一个枚举管三件事 | `YoModelCapability` 把**模态**（看图、听音）、**玩法**（推理、工具）、**服务类别**（实时、转写、合成、向量）混在一起。"全模态"模型要同时收文本、图片、音频、视频，还要产出语音，这个枚举没有位置放它 |
| 两套平行的分类 | `YoModelKind`（对话/向量/图片/音频……）给模型列表筛选，`YoModelCapability` 给路由用，各有一份词表，说法会不一致（同一个 `realtime`，一边归"其他"，一边归"实时语音"） |
| 规格太薄 | 只有 `contextWindow` 和 `maxOutputTokens` 两个数。没有输入上限，没有思维链上限，没有函数调用、结构化输出、联网搜索、前缀续写、上下文缓存、批量推理，推理也只是一个布尔 |

另外，平台自己在 `/models` 里报的内容没有进入路由：`describe(id)` 只看 id，平台报了"能看图"，路由并不知道。

## 2. 总览

规格是几根**互相独立**的轴，每一条说法都带出处：

```
                         ┌─ 类别 category      文本 视觉 全模态 语音识别 语音合成 向量 实时 …
                         ├─ 模态 inputs·outputs 文本 图片 音频 视频 文档 向量
  YoModelSpec ───────────┼─ 限额 limits        上下文 · 输入 · 输出 · 思维链 · 向量维度 · 各模态体量
  （模型的完整规格）        ├─ 能力 capabilities  函数调用 结构化输出 联网搜索 前缀续写 上下文缓存 批量推理
                         └─ 推理 reasoning     怎么想 · 力度档位 · 预算

  三层来源，高覆盖低：   按 id 推断 ──▶ 平台 /models 上报 ──▶ 用户指定
                       INFERRED       REPORTED             USER

  调用方的要求 YoModelRequirement ──▶ YoCapabilityRouter ──▶ YoModelRoute（模型·服务·协议·端点·可靠程度）
```

## 3. 类别 `YoModelCategory`

类别回答"它是哪种东西"，决定走哪种服务，是路由的第一道筛。

| 类别 | 典型输入 → 输出 | 服务 | 说明 |
| --- | --- | --- | --- |
| `TEXT` 文本 | 文本 → 文本 | 三条对话协议 | 认不出的 id 都当文本，宁可多列一个 |
| `VISION` 视觉 | 文本、图片 → 文本 | 三条对话协议 | |
| `OMNI` 全模态 | 文本、图片、音频、视频 → 文本、音频 | 三条对话协议 | 仍是一问一答，不是全双工 |
| `SPEECH_RECOGNITION` 语音识别 | 音频 → 文本 | `AUDIO_TRANSCRIBE` | |
| `SPEECH_SYNTHESIS` 语音合成 | 文本 → 音频 | `AUDIO_SPEECH` | |
| `EMBEDDING` 向量 | 文本 → 向量 | `EMBEDDINGS` | |
| `REALTIME` 实时 | 文本、音频 → 文本、音频 | `REALTIME`（WebSocket） | 全双工，服务端自己断句 |
| `IMAGE_GENERATION` / `VIDEO_GENERATION` / `RERANK` / `MODERATION` / `OTHER` | — | 无 | 只做识别，免得它们混进对话模型列表；路由到它们会明确报"lib_ai 暂时没有对应服务" |

- **对话三类向下兼容**：`OMNI` 能当 `VISION` 用，`VISION` 能当 `TEXT` 用，反过来不行（`canServe`）。其余类别只认自己，
  全模态模型不能当实时模型用。
- 语音识别与语音合成统称"语音"（`speech()`）。它们方向相反、端点不同、参数不同，所以是两个类别。
- 类别到服务的对应只写在 `YoServiceKind.dedicatedFor`，`switch` 穷举，新增类别漏了映射会编译失败。
- 每个类别有稳定的持久化标识（`id()`），不随枚举名重命名而变。

## 4. 模态 `YoModality`

`TEXT` `IMAGE` `AUDIO` `VIDEO` `DOCUMENT`（PDF 等文件）`VECTOR`（只出现在输出侧）。
输入、输出各是一组模态，每个模态一条三态事实。类别有"典型模态"（`typicalInputs/Outputs`），
没人明确表态时拿它当推断级的底：文本模型默认收文本，但**不会**默认收图片。

## 5. 限额 `YoModelLimits`

没说过的就是 `null`，SDK **不凭 id 猜数值**。几个长度含义不同，不能混着比大小：

| 字段 | 含义 |
| --- | --- |
| `contextWindow` | 上下文窗口，**输入和输出共用**的总长度 |
| `maxInputTokens` | 单次请求最多送多少输入，可以比窗口小 |
| `maxOutputTokens` | 单次最多生成多少，可以比窗口小得多 |
| `maxReasoningTokens` | 思维链单独的上限，推理模型的"想"和"答"往往分开计 |
| `embeddingDimensions` | 向量模型的输出维度 |
| `inputMedia` | 各输入模态的体量 `YoMediaLimit(maxItems, maxBytes, maxSeconds)`：图片几张、每张多大，音频视频最长几秒；`TEXT` 的 `maxItems` 表示向量模型一次最多几段文本 |

实际容量用方法算，不要自己拿两个数比：

- `inputCapacity()`：输入上限和窗口里更紧的那个；
- `outputCapacity(inputTokens)`：输出上限和"窗口里剩下的位置"里更紧的那个，输入占满窗口时为 0。

数值只会来自平台上报或用户填写，都可信，所以限额没有出处档；几层来源叠加时，后写的非空项覆盖先写的。

## 6. 服务端能力 `YoModelCapability`

| 能力 | 含义 |
| --- | --- |
| `FUNCTION_CALLING` 函数调用 | `tools` / `tool_choice` |
| `STRUCTURED_OUTPUT` 结构化输出 | JSON 模式，或按 JSON Schema 约束 |
| `WEB_SEARCH` 联网搜索 | 服务端自带检索，不是调用方自己接的工具 |
| `PREFIX_COMPLETION` 前缀续写 | 固定助手消息的开头，让模型接着写 |
| `CONTEXT_CACHE` 上下文缓存 | 重复前缀命中后更快更省 |
| `BATCH` 批量推理 | 异步提交一批请求 |

这些是**对规格的声明与匹配**，用来选模型、拦住"明确不支持"的请求；请求构造器本身是否能发出对应字段
（例如前缀续写、缓存标记、批量提交）是另一回事，这一版没有补。

## 7. 推理 `YoReasoning`

| 字段 | 含义 |
| --- | --- |
| `mode` | `UNKNOWN` 没人说过；`NONE` 明确没有；`SUPPORTED` 会推理但开关方式没说清；`ALWAYS` 强制思考关不掉（deepseek-reasoner、o 系列）；`SWITCHABLE` 混合思考可开可关 |
| `efforts` | 可选的力度档位（`none/minimal/low/medium/high/xhigh/max`），与 Chat 请求构造器接受的取值一致 |
| `budgetAdjustable` | 能否限定思考 token 预算 |

推理面共用一个出处，因为这几项几乎总是一起被说出来的。

## 8. 出处与合成

### 8.1 三态与出处

支持不支持是三态：`YES` / `NO` / `UNKNOWN`。**平台没说不等于不支持**——把"没说"当成"不支持"，
会把能用的模型挡在路由之外；反过来，平台或用户明确说过"不支持"的，要能压过名字上的推断。

每条事实是 `YoFact(support, source)`。出处按优先级排：

| 出处 | 来源 | 可信 |
| --- | --- | --- |
| `INFERRED` | 只凭 id 词元猜的提示 | 否，界面显示成待确认 |
| `REPORTED` | 平台 `/models` 自己声明的 | 是 |
| `USER` | 用户手动确认，永久优先 | 是 |

SDK 不内置任何厂商模型清单：除了平台上报和用户指定，其余全是推断。
`YoSupportMap` 实现覆盖规则：低出处改不动高出处的结论，同级后写为准，所以几层来源各自写进来，结果与写入顺序无关。

### 8.2 对话类的细分是现算的

`TEXT` / `VISION` / `OMNI` 不是另存的结论，而是由**可信的模态事实**推出来的：

- 可信地收图片或视频 → `VISION`；
- 可信地收音频，或产出音频、图片、视频 → `OMNI`；
- 只升不降：证据没覆盖到的部分，保留已有的声明；
- 用户直接指定类别时，一律尊重，不再推；
- 只有推断来的模态**不**触发升档——名字上看着像不算数。

因为是现算的，后来的来源改了模态，类别会跟着变：用户在设置页取消"看图"，类别自动掉回文本，不会留下过期的"视觉"。
非对话类别不受模态证据影响。

### 8.3 推断只回答两件事

`YoModelInference` 是整个 SDK 唯一的分类词表，只回答"大概是哪一类服务"和"名字里有没有推理字样"：

- 不猜能不能看图、能不能听音、上下文多长、有没有函数调用——猜错的代价是"看起来支持、实际 400"；
- 类别按固定优先级判断：实时 > 审核 > 语音识别 > 语音合成 > 重排序 > 向量 > 图像生成 > 视频生成 > 旧文本补全 > 文本。
  所以 `omni-moderation-latest` 是审核，`qwen3-omni-flash-realtime` 是实时；
- `instruct` 单独出现是大量对话模型的后缀（`xxx-Instruct`），只有 `turbo-instruct` 才算旧的文本补全模型。

## 9. 平台上报怎么读

OpenAI 标准的 `/v1/models` 只有 `id`、`owned_by`、`created`，其余靠各家主机自己加字段，拼写五花八门。
`ModelReports` 把常见写法收成别名表：**读得懂的记为 `REPORTED`，读不懂的当没说**。

两个口径要分清：

- **穷举清单**（`input_modalities`、`output_modalities`，含 `architecture` 下的同名字段和 `"text+image->text"` 写法）：
  没列出的就是不支持。一个都认不出时按没说处理，不会写成"全部不支持"；
- **开放特性清单**（`supported_features`、`capabilities`、`features`、`supported_parameters`、`abilities`，
  以及 `supports_*` 平铺布尔）：列出的是支持，没列出的只是没说，只有显式的 `false`（或 `{"supported": false}`）才算不支持。

| 内容 | 读取的常见写法 |
| --- | --- |
| 上下文窗口 | `context_length` `context_window` `max_context_length` `max_context_tokens` `context_size` `max_model_len` `n_ctx`（顶层没有时看 `top_provider`） |
| 输入上限 | `max_input_tokens` `input_token_limit` `max_input_length` `max_prompt_tokens` |
| 输出上限 | `max_output_length` `max_output_tokens` `max_completion_tokens` `output_token_limit` `max_tokens`（同上） |
| 类别 | 输出模态（含 `embedding` / 无文字输出）、`type`（`embedding` `image` `speech` `tts` `transcription` `moderation` `rerank` `realtime` 等；泛泛的 `chat` 不算） |
| 能力 / 推理 / 看图 | 如 `tools`、`structured_outputs`、`web_search_options`、`prompt_caching`、`batch`、`reasoning`、`vision`、`pdf_input` 等别名 |

Anthropic 的 `capabilities` 是 `{"supported": bool}` 结构：`image_input` `pdf_input` `structured_outputs` `batch` `thinking` `effort`
逐项记为上报；`max_input_tokens` 是整个输入上下文窗口，记为 `contextWindow`，`max_tokens` 记为单次输出上限；
支持思考即视为可开关（Claude 在请求里自己决定开不开），`thinking.types.enabled` 表示能手动给预算，`effort` 里受支持的档位逐一列出。

## 10. 要求与路由

### 10.1 `YoModelRequirement`

调用方说"我要什么样的模型"，而不是"我要哪个能力位"：

```java
YoModelRequirement.text()
        .withCapability(YoModelCapability.FUNCTION_CALLING)
        .withMinContext(128_000);
YoModelRequirement.vision().withReasoning();
YoModelRequirement.realtime();
```

由**类别**（至少是哪一类）加若干**附加条件**（必须收/产出的模态、必须具备的能力、必须会推理、上下文和输出下限）叠成，
条件之间是"且"。体量条件只拦"明确不够"：规格里没有这项数值时放行。

### 10.2 `YoModelFit`：满足，以及有多可靠

`fit(spec)` 逐条核对，结论有三档：

| 结论 | 含义 |
| --- | --- |
| `REJECTED` | 类别不对，或某项必需的事实明确没有（或根本没人说过），或体量明确不够 |
| `INFERRED` | 满足，但至少有一条依据只是按 id 推断 |
| `CONFIRMED` | 满足，每条依据都来自平台上报或用户指定 |

### 10.3 路由 `DefaultCapabilityRouter`

1. 按类别定服务：对话三类取平台已声明的协议，偏好 Chat Completions → Responses → Messages；其余类别必须声明了各自的专用服务；
   SDK 暂时没有服务的类别直接报错，比走到一半 404 好；
2. 选模型：用户选定的模型只要满足就用（哪怕不在平台列表里，手动添加的就是这种）；否则先取第一个 `CONFIRMED`，
   没有再取第一个 `INFERRED`——这是"看起来支持、实际 400"的防线；
3. 拼端点（`YoServiceEndpoints`），实时语音走 `wss://…/realtime?model=…`；
4. 结果 `YoModelRoute` 带着 `fit`，界面可以据此提示"待确认"。

规格有两种给法：已解析好的 `List<YoModelSpec>`（拉过平台列表，用 `YoModelCatalog.resolveAll` 得到，更准），
或只有 id（`routeByIds`，按 id 推断再叠加用户覆盖）。

失败是新的错误类型 `YoCapabilityException`（`UNSUPPORTED_CAPABILITY`），消息固定带"需要什么样的模型、平台有哪些服务和模型"，
界面直接照抄，不会被翻成"参数不对"或 400。

## 11. 目录与用户覆盖

`YoModelCatalog` 是不可变的：`describe(id)` 只凭 id（推断 + 覆盖），`resolve(info)` 用平台列表里的一项（推断 + 上报 + 覆盖）。
`YoModelOverride` 是用户的明确表态——设置页每个勾选框一条，勾上是支持、取消是明确不支持、没碰过的不在里面。
**连"一个都不勾"也算数**，所以用户永远能纠正平台或推断的错误。覆盖按 id 原文（忽略大小写）匹配，
故意不做家族前缀匹配：用户确认的是那个具体模型，不该被将来某个同家族新版本继承。

## 12. 与 APP 的边界

APP 的领域层不认识 `lib_ai`（`checkAiLayerImports` 强制）。`data/ai/gateway/LibAiModels` 是翻译层：
类别决定模型能不能进列表（对话类与实时类），能力位只认有出处的说法，数值照搬。路由目前还没有接管 APP 的出站请求，
拉模型、选模型仍按 id 走。

## 13. 怎么扩展

| 想加 | 做法 |
| --- | --- |
| 一个类别 | `YoModelCategory` 加常量与典型模态 → `YoServiceKind.dedicatedFor` 补映射（`switch` 穷举，漏了编译失败）→ `YoModelInference` 补词表 → 需要端点时在 `YoServiceEndpoints` 补路径 |
| 一项服务端能力 | `YoModelCapability` 加常量 → `ModelReports` 补别名集合并在 `capabilities` 里登记 → 测试 |
| 平台的新字段写法 | 只改 `ModelReports` 里的别名表，规格和路由不用动 |
| 一个新平台的列表格式 | 实现 `YoModelListing`，解码时先 `YoModelInference.infer(id).toBuilder()` 再盖上报的事实 |

## 14. 测试

`lib_ai` 的 `models/spec/` 与 `models/` 下各有单测，按层对应：出处覆盖（`YoSupportMapTest`）、类别格（`YoModelCategoryTest`）、
限额算术（`YoModelLimitsTest`）、规格合成与现算类别（`YoModelSpecTest`）、推断词表（`YoModelInferenceTest`）、
目录与覆盖（`YoModelCatalogTest`）、要求匹配（`YoModelRequirementTest`）、路由（`YoCapabilityRouterTest`）、
平台上报的各种写法（`ModelReportsTest`、`ModelDirectoryTest`）。

## 15. 已知限制

- 前缀续写、上下文缓存标记、批量提交这几项能力**只有规格与匹配**，三条协议的请求构造器还没有对应字段；
- 向量、语音合成没有客户端，路由只负责选模型和端点；
- 媒体体量（图片张数、音视频时长）没有平台会在列表里报，目前只能由用户填，且还没有请求前校验；
- 名字推断从不产出视觉 / 全模态，没有上报也没有用户确认的多模态模型，在路由眼里就是文本模型。
