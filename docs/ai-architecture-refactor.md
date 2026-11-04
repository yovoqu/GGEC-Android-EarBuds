# AI 模块架构梳理与全量重构设计

> 范围：`app` 内的 AI 设置 / 语音 / 模型拉取链路，以及它对 `lib_ai` 的依赖方式。
> 目标：先看清现状，再定目标架构，最后按阶段全量重构；不做"哪疼贴哪"的补丁。

---

## 一、现状架构（As-Is）

### 1. 模块与依赖

```
app
 ├─ ui/ai/**                 界面（Activity / Screen / View / ViewModel / UiState / UiMapper）
 ├─ domain/ai/**             领域（模型 / 端口 Port / 用例 UseCase / 策略 Policy）
 ├─ data/ai/**               适配（设置仓库、JSON 编解码、SecretStore、lib_ai 网关、语音实现）
 ├─ platform/ai/**           Android 平台实现（音频、SharedPreferences）
 └─ wiring/AiGraph.java      手工装配（无 DI 框架）
lib_ai                        协议 SDK（平台目录 / 协议编码 / 实时语音 / 模型列表）
```

调用方向总体正确：`ui → domain ← data`，`data → lib_ai`。问题集中在 **domain 内部的聚合设计** 与 **data/domain 的职责边界**。

### 2. 设置数据流（现状）

```
UI 事件
  → AiSettingsViewModel
    → AiSettingsChanges.xxx()  （纯函数）
      → AiSettingsRepository.update()
        → 内存快照 current（锁内读-改-写）
        → 观察者 observe → UiMapper → AiSettingsUiState → Screen
        → 后台串行落盘：AiSettingsJson.encode → settings.json
                        SecretStore（密钥单独加密）
```

读取时 `AiSettingsRepositoryImpl.read()` 把 `SecretStore` 的密钥合并进 `AiSettings` 快照（`AiPlatformConfig.apiKey` / `AiVoiceConfig.apiKeys`），再交给 UI 和网关。

### 3. 语音模型数据结构（现状，问题的核心）

`AiVoiceConfig` 只有一个"当前服务商指针"，其余字段是**全局共享槽位**：

```java
record AiVoiceConfig(
    AiVoiceModel model,               // 当前选中的服务商
    Map<AiVoiceModel,String> apiKeys, // 每家的 Key（这部分是对的：按家分开）
    String baseUrl,                   // ← 只有一份，属于"最后操作的那一家"
    String realtimeModelId,           // ← 只有一份
    AiRealtimeDialect dialect,        // ← 只有一份
    List<AiListedModel> realtimeModels// ← 只有一份
)
```

切换服务商 `withModel(next)` 必须把 `baseUrl / realtimeModelId / realtimeModels` 清空（注释写得很直白："上一家的，跟着换过去会连错"）。

**这就是 bug 根因**：

```
Qwen 拉取 16 个模型 → 存在唯一的 realtimeModels
点 ChatGPT          → withModel() 清空 realtimeModels
再点 Qwen           → 列表已经没了
```

不是显示层问题，是**数据模型把"每家自己的状态"塞进了"当前选择"的公共槽位**。Key 已经按家分开了，地址/模型/列表却没有，属于典型的部分解耦。

### 4. 其它结构性缺陷（按严重度）

| # | 缺陷 | 位置 | 后果 |
|---|------|------|------|
| A1 | 语音 provider 状态共享槽位 | `domain/ai/model/AiVoiceConfig.java` | 切换服务商丢数据（本次 bug） |
| A2 | 网关层做业务筛选 | `data/ai/gateway/LibAiVoiceModelGateway.java` + `LibAiModels.realtime()` | 端口契约写"返回全部、不做筛选"，实现却筛选；数据层替领域做了决定，职责反了 |
| A3 | 语音传输写死 OpenAI 平台 | `LibAiVoiceModelGateway` 固定 `OpenAiPlatform.ID` | 新增/特殊服务商协议无法扩展，厂商知识散落在 data 层 |
| A4 | 密钥混入领域对象 | `AiPlatformConfig.apiKey()`、`AiVoiceConfig.apiKeys()`、`AiSettingsRepositoryImpl.read()` | 领域配置里带秘密，序列化稍有不慎就泄漏；密钥有两份真相（内存快照 + SecretStore） |
| A5 | JSON 编解码与领域字段强耦合 | `data/ai/json/AiSettingsJson.java` | 每加一个字段要改 encode/decode/构造/迁移多处；v4→v5 已出现 `realtimeModels` 与 `models` 两套 key |
| A6 | 模型概念重复 | `AiModel`（文本平台）与 `AiListedModel`（语音） | 两套"模型引用 + 类别"，筛选/展示逻辑各写一遍 |
| A7 | 服务商预设与平台目录两处定义 | `AiVoiceModel`（baseUrl/默认模型） vs `lib_ai/YoPlatformCatalog`（host/protocol） | 同一件事两份事实，容易漂移 |
| A8 | UI 状态与重建靠字符串签名 | `VoiceConnectionCard.signature`、`VoiceModelCard.rowsSignature` | 字段一多就难维护，重建条件容易漏 |
| A9 | 拉取无取消/无按家缓存时间 | `FetchVoiceModels` | 每次全量网络 + 全量写文档；切家时旧请求可能覆盖新状态 |
| A10 | 死数据/死代码残留 | `AiRealtimeDialect` 全套 UI 已删但仍存于配置；`LibAiModels.all()` 无人用；`ai_settings_voice_dialect` 等字符串 | "多重数据"噪音，读者难以判断真实数据源 |

### 5. 现状小结

- **好的部分**：分层方向正确；`AiSettingsChanges` 纯函数；仓库单写者 + 观察者；密钥单独加密存储；UI 不直接碰存储。
- **要解决的**：语音状态没有"按 provider 聚合"；data/domain 边界反转；秘密与配置未分离；模型类型重复；预设事实两处。
- **本次 bug 只是 A1 的表征**，单独修 A1 能止血，但不解决 A2–A10 会继续长出新 bug。

---

## 二、目标架构设计（To-Be）

### 1. 设计原则

1. **聚合按归属划分**：属于哪一家的数据，就存在哪一家名下；"当前选择"只是指针。
2. **数据层只做 I/O，不做业务判断**：网关只负责"按目标拉原始结果"，筛选/降级/选中策略属于领域。
3. **秘密与配置分离**：设置文档永远不含密钥；领域配置只暴露 `configured` 事实，密钥在调用瞬间由凭证端口提供。
4. **单一事实来源**：服务商能力（host/协议/默认模型/是否支持实时）只在一处声明，data 层翻译给 `lib_ai`。
5. **UI 只读投影**：UiState 是按 provider 分组的只读视图，不再持有跨家共享槽位。
6. **可迁移**：v4 文档能无损升级；未知/损坏字段宽容降级。

### 2. 目标领域模型

```
AiSettings
 ├─ List<AiPlatformConfig> platforms          // 文本平台（保持现状）
 ├─ AiSelection textSelection
 ├─ AiVoiceSettings voice
 └─ AiChatPreferences preferences

AiVoiceSettings
 ├─ AiVoiceProviderId active                   // 当前指针（枚举/稳定 id）
 └─ Map<AiVoiceProviderId, AiVoiceProvider> providers

AiVoiceProvider                                // 一家自己的全部非秘密状态
 ├─ String baseUrlOverride                    // "" = 用预设
 ├─ String selectedRealtimeModelId            // "" = 用预设
 ├─ List<AiModelRef> realtimeModels           // 该家已拉取、可用于实时的模型
 ├─ long modelsFetchedAtMillis
 └─ (方言不进入用户配置；传输层固定默认)
```

- `AiVoiceProvider` **不包含 Key**。
- `AiVoiceSettings.provider(id)` 返回 `EMPTY` 兜底；切换 `active` 只改指针。
- 兼容旧名：`AiVoiceModel` 继续作为服务商枚举（id/显示名/预设），但不再承担"当前状态"。

### 3. 秘密边界

新增端口：

```java
interface AiCredentialStore {
    Optional<String> secret(AiCredentialRef ref);      // 读
    void put(AiCredentialRef ref, String secret);
    void remove(AiCredentialRef ref);
    boolean configured(AiCredentialRef ref);           // UI 只需要这个
}
// AiCredentialRef: platform(id) | voiceProvider(id)
```

- 设置文档 / `AiSettings` 只保存非秘密配置。
- `AiSettingsUiState` 中每家 provider 带 `configured` 布尔，由凭证端口投影，不携带明文。
- 聊天/语音网关在**建立连接时**通过 `AiCredentialStore` 取密钥；避免密钥进入领域快照。
- 迁移期可先只把**语音**密钥移出领域（风险低），文本平台随后一步。

### 4. 模型目录与筛选职责

```
domain:
  AiVoiceProviderCatalog        // 每家的 preset：显示名、默认 host、协议族、默认实时模型、能力
  VoiceModelPolicy              // 纯策略：从原始模型列表选出"能接实时会话"的
  FetchVoiceModels              // 用例：target = provider + credential；raw = gateway.fetch；usable = policy.filter(raw)
data:
  LibAiVoiceModelGateway        // 只做 fetch(target) → List<AiModelRef>（不筛选）
  LibAiPlatformCatalogAdapter   // 把 AiVoiceProviderCatalog 的声明翻译成 lib_ai 的 YoPlatform
```

- 修正 A2：筛选从 data 上移到 domain `VoiceModelPolicy`。
- 修正 A3/A7：服务商 → 协议栈的映射由 `AiVoiceProviderCatalog` 单一声明，data 只翻译。
- 修正 A6：统一 `AiModelRef(id, displayName, category, ...)`；文本/语音共用，类别语义来自 `lib_ai`。

### 5. 目标数据流

```
读：
  AiSettingsRepository.load() → AiSettings（无秘密）
  AiCredentialStore.configured()/secret() → 投影 configured
  → AiSettingsUiMapper → AiSettingsUiState（按 provider 分组）→ Screen

写（配置）：
  UI → ViewModel → AiSettingsChanges（纯） → Repository.update（单写者）
    → observe → UiState

写（秘密）：
  UI → ViewModel → AiCredentialStore.put/remove → configured 观察者刷新

拉模型：
  FetchVoiceModels(providerId)
    → provider = settings.voice.provider(providerId)
    → key = credentialStore.secret(voice(providerId))
    → raw = gateway.fetch(target(provider, key))
    → usable = VoiceModelPolicy.realtimeOnly(raw)
    → repository.updateProvider(providerId, usable, now)   // 只改这一家，绝不碰别家

语音通话：
  ResolveVoicePlan(activeProvider)
    → provider state + credential → VoiceSessionPlan(baseUrl, model, keyRef)
    → RealtimeVoiceSession / LinkAdapter
```

### 6. 持久化（v5）

```json
{
  "v": 5,
  "voice": {
    "model": "qwen",
    "providers": {
      "chatgpt": { "baseUrl": "...", "realtimeModel": "...", "models": [...] },
      "qwen":    { "realtimeModel": "qwen-audio-3.1-realtime-flash", "models": [...] }
    }
  }
}
```

- 每家一个对象，互不覆盖；缺省字段不写。
- 旧 v4 的单组字段在解码时迁移到"当前 model"对应的 provider。
- 密钥永不出现（由 `SecretStore` 独立别名 `voicekey.<id>` 管理）。

### 7. 目标目录结构（增量，不推倒现有分层）

```
domain/ai/model/
  AiVoiceSettings.java        (新)
  AiVoiceProvider.java        (新，替代 AiVoiceProviderState)
  AiModelRef.java             (新，合并 AiModel/AiListedModel 的引用部分)
domain/ai/policy/
  VoiceModelPolicy.java       (新，实时筛选)
  VoiceProviderCatalog.java   (新，服务商能力声明)
domain/ai/port/
  AiCredentialStore.java      (新)
  AiSettingsRepository.java   (调整：只返回非秘密配置)
data/ai/store/
  EncryptedCredentialStore.java (适配现 SecretStore)
data/ai/gateway/
  LibAiVoiceModelGateway.java (改为纯 fetch，不筛选)
ui/ai/settings/voice/
  ProviderVoiceViewModel.java (新，按家投影)
```

### 8. 关键不变量（可写成测试）

1. `settings.voice.withActive(qwen)` 不改变 `providers.qwen` 与 `providers.chatgpt` 的任何字段。
2. 任意 provider 的 `baseUrl/selectedModel/models` 不会出现在另一家的读取结果里。
3. `settings.json` 文本永不含任何密钥或密钥片段。
4. v4 文档升级后，当前 provider 的 baseUrl/选中模型/列表完整保留；其它 provider 为空。
5. `VoiceModelPolicy.realtimeOnly` 结果全部具备实时类别；文本/向量/合成不进入语音候选。
6. 拉取结果按 provider 归属写入，旧请求晚到不得覆盖新 provider 状态（带 revision/取消校验）。

---

## 三、全量重构实施计划（分阶段，可回滚）

### 阶段 0：基线保护
- 补"切换保留"回归测试（先红后绿）：Qwen 拉取 → 切 ChatGPT → 切回 Qwen，列表与选中仍在。
- 冻结当前行为快照（settings.json v4 样本），作为迁移对照。

### 阶段 1：语音聚合重构（对应 A1）
- 引入 `AiVoiceProvider` + `AiVoiceSettings`；`AiVoiceConfig` 仅作兼容门面或直接替换。
- `withActive()` 只改指针；JSON v5 + v4 迁移。
- 仓库读写改为"每家一份"。
- 产出：bug 消失，单测覆盖切换保留。

### 阶段 2：职责归位（A2/A3/A7）
- `LibAiVoiceModelGateway` 去掉筛选，恢复"原始拉取"。
- 新增 `VoiceModelPolicy`（domain）做实时筛选与选择策略。
- 新增 `VoiceProviderCatalog` 单一声明 host/协议/默认模型；data 映射到 `lib_ai`。
- 删除 `LibAiModels.all()` 等死代码。

### 阶段 3：秘密边界（A4）
- 新增 `AiCredentialStore`；`AiVoiceProvider` 去掉 Key（先语音，后文本平台）。
- UI `configured` 由凭证端口投影。
- 网关在连接时取密钥；`AiSettings` 不再携带明文。

### 阶段 4：模型类型统一（A6）
- 合并 `AiModel` / `AiListedModel` 为 `AiModelRef`（或明确两个语义并只保留一个引用类型）。
- 文本与语音的筛选策略都基于同一类别模型。

### 阶段 5：UI 与性能（A8/A9）
- UiState 按 provider 分组，卡片按 id 绑定；去掉字符串签名重建。
- 拉取按家缓存 `modelsFetchedAtMillis`，切家不重拉；请求带取消/版本号，晚到结果丢弃。
- 文档只存必要字段，避免把上千条原始目录反复落盘。

### 阶段 6：清理与验收
- 删死字符串/死枚举引用；ArchUnit/自定义 Gradle 检查补充"data 不得做模型筛选""domain 不得 import 密钥存储"。
- 设备端验收脚本：Qwen 拉取 → ChatGPT → Qwen 保留；密钥不出现在文档。

### 验收标准
- 切换服务商不再丢任何一家的模型/地址/选择。
- 设置文档零密钥；v4→v5 迁移无损。
- 语音候选只含实时模型；文本平台列表不受影响。
- 单测 + 设备冒烟全绿；无首帧卡顿回归。

---

## 四、当前工作区状态（诚实说明）

在收到"先梳理、再设计"之前，工作区已经落了一版**最小止血**（阶段 1 的雏形，已编译通过、单测通过）：

- 新增 `AiVoiceProviderState`（按服务商存 baseUrl / 选中模型 / 方言 / 已拉列表）；
- `AiVoiceConfig` 改为 `model + apiKeys + Map<provider, AiVoiceProviderState>`，`withModel` 不再清空；
- `AiSettingsJson` 升到 v5，带 v4 迁移；
- `AiSettingsRepositoryImpl.read()` 改为按 provider 组装；
- 测试夹具同步。

它验证了数据模型方向正确，但**仍留有 A2（网关筛选）、A4（密钥在领域）、A6/A7（模型/目录重复）、A8/A9（UI/请求）**。按本设计应继续做阶段 2–6，而不是停在"能跑"。

> 建议：保留这版作为阶段 1 的起点，先评审本设计；确认后再按阶段 2–6 全量推进。若不接受任何未经评审的改动，可先回滚这版 WIP 到干净基线，再从阶段 0 开始。

---

## 五、需要你拍板的两个点

1. **范围**：本次"全量重构"是仅覆盖 AI 设置/语音链路（本文范围），还是把文本平台 + 聊天链路也并入（阶段 3/4 会动到 `AiPlatformConfig` 与聊天网关）？
2. **密钥边界**：阶段 3 是否一次做到"领域零密钥"（改动大、收益高），还是先语音、文本平台保持现状（风险低、分两步）？

---

## 六、实施进度（滚动更新）

已确认的取舍：**范围=AI 全链路（含文本平台与聊天）；密钥=一次全量清零；WIP=回滚到干净基线后按本设计重来。**

- [x] 回滚未评审 WIP，恢复干净基线并全绿。
- [x] 阶段 1：`AiVoiceConfig = model(指针) + apiKeys + Map<provider, AiVoiceProvider>`；切换只改指针。JSON v5（providers 分组）+ v4 迁移（含 `realtimeModels` 旧键兼容）。
- [x] 阶段 2：实时筛选从网关上移到 `domain/ai/policy/VoiceModelPolicy`；`LibAiVoiceModelGateway` 恢复"纯拉取"；删除 `LibAiModels.realtime()`。
- [x] 清理方言旧代码：删除 `AiRealtimeDialect`、端口 `dialect` 字段、`withDialect`/`selectChatGptDialect`、UI `DialectOption`/`dialectId`/字符串；通话固定走厂商默认线路。
- [x] 严格 MVVM 瘦身（本阶段）：`VoiceSettingsView` 去掉无用 `YoComponents` 依赖；`VoiceModelOption` 收敛为仅 id（语音只列实时模型，类别胶囊删除）；重命名 `selectChatGptModel → selectRealtimeModel`；修掉 Hero 卡重复 setText。
- [x] 设备验收：Qwen 拉取 16 → 切 ChatGPT（0）→ 切回 Qwen（16 保留）；`settings.json` v5 按 provider 分组且不含密钥。
- [x] 零密钥设备验收：语音 Key 只在加密存储，`settings.json` 无任何 `sk-`；切换服务商仍保留各家模型。
- [x] 阶段 3：`AiCredentialStore`（`EncryptedCredentialStore` 复用 SecretStore 别名）+ 领域零密钥：`AiPlatformConfig.apiKey` → `configured` 布尔、`AiVoiceConfig.apiKeys` → `configuredProviders` 集合；聊天/模型/测速/实时网关在建立连接时按 id 取密钥；`SavePlatform`/语音 Key 保存写凭证端口；仓库只读凭证库推导 `configured`，从不落明文。
- [x] 阶段 4：模型引用统一为 `AiModel`（删除 `AiListedModel`），文本平台与语音共用同一模型类型（类别/名称/上下文都在一个 record 里）。
- [x] 阶段 5：请求归属隔离——`FetchVoiceModels` 在开始时捕获 provider，返回后通过 `withRealtimeModelsFor(owner, …)` 写回发起方，晚到的结果不会写进用户已切换到的别家；每家模型列表本来就按 provider 持久化，切换不重拉。UI 已按 provider 投影，视图签名仅用于局部重绑。
- [x] 阶段 6：删除死代码（`AiListedModel`、`AiFormat.maskKey`、方言残留）；补充架构检查：domain 禁止 import `com.ggec.earbuds.data`，data/ai/gateway 禁止 import `domain.ai.policy`（筛选不得回流网关）。

