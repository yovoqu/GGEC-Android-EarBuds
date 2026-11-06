# lib_ai 兼容性与弃用策略（F25）

> 目标：v2 改造只做**加法**，旧调用方不改代码即可继续工作；新能力通过新类型/新方法渐进采用。

## 1. 兼容原则

1. **旧构造器保留**：`YoChatUsage(int,int,int)`、`YoChatUsage(int,int,int,Integer,Integer,BigDecimal)`、`YoResponseUsage(int,int,int)`、`YoMessageUsage(int,int)`、`YoTranscription(String,String,Double,String)` 等全部保留，语义不变。
2. **旧方法保留**：`YoChatMessage.content()`、`parts()`、`toolCalls()`、`refusal()` 等签名不变；新增 `reasoningContent()/name()/audioId()/prefix()` 只增不改。
3. **新参数可选**：`YoChatOptions`、`YoThinkingConfig`、`YoRequestOptions` 等均为可选；不设置时 wire 报文与 v1 完全一致。
4. **默认值不变**：`YoChatRequestPolicy.none()` 仍为空实现；`apply(JSONObject)` 是新 default 方法，旧实现无需改。
5. **公开边界不变**：公开类型仍不依赖 `internal.*`/`okhttp3.*`/`okio.*`。

## 2. 行为兼容清单

| 场景 | v1 行为 | v2 行为 |
|---|---|---|
| 不设 `YoChatOptions` | 只发标准字段 | 同 v1（额外字段不出现） |
| `YoChatUsage` 三参构造 | 仅三 token | 同 v1，新字段为 `null` |
| `YoMessageRequest` 不设 thinking | 无 `thinking` 字段 | 同 v1 |
| `YoResponseRequest` 不设 background | 无 `background` | 同 v1（修复前会被 `remove`，现在不设置就不出现） |
| `YoTranscription` 旧构造 | text/language/duration | 同 v1，新字段为 `null`/空 |
| `YoRealtime` 默认方言 `OPENAI_BETA` | 同 v1 | 同 v1；新增方言不影响默认 |
| `YoAiClient` 旧访问器 | chat/responses/messages/models/completions/probe | 全部保留；新增访问器另行加入 |

## 3. 弃用策略

| 项 | 处置 |
|---|---|
| `YoChatCompletionRequest.Builder` 直接 `serviceTier/metadata/modalities` | 保留；未来建议迁到 `YoChatOptions`，不强制 |
| `YoSpeech`（仅转写） | 保留；新增 `YoSpeechSynthesis` 与 `YoSynthesis`，旧接口不删除 |
| `YoTranscription` 旧构造 | 保留；新代码用 Builder |
| `YoModelCategory.IMAGE_GENERATION/VIDEO_GENERATION/RERANK/MODERATION` | 仍可识别；v2 增加对应服务后，`YoServiceKind.dedicatedFor` 不再返回 `null` |
| `rawJson` | 保留为调试/兼容逃生舱；新代码优先用结构化字段 |

## 4. 版本与发布

- 版本号建议：`0.2.0`（新增公开类型，不破坏二进制）。
- 发布前必须：`:lib_ai:testDebugUnitTest` 全绿、`:lib_ai:checkLibAiPublicBoundary` 通过、`docs/ai/acceptance-matrix.csv` 全部 `PASS`。
- 不做 git commit/push，除非用户明确要求。

## 5. 回归测试

- `com.yovo.lib_ai.*` 旧测试**一行不改**仍必须全绿。
- 新增功能测试与旧测试分目录，避免覆盖。
