# AI 模块架构（v3 目标态）

> 配套：需求见 `ai-module-requirements.md`。本文只写"怎么搭"，不写"做什么"。
> 现状迁移按 §8 的阶段走，每个阶段都能单独编译、装机、回滚。

## 1. 分层与依赖方向

```
ui/ai/**          页面、组件、mapper、view-model      （依赖 domain + lib_common + YoUI）
      ↓ 只依赖抽象
domain/ai/**      model / policy / port / usecase     （纯 Java，无 Android 依赖）
      ↑ 实现
data/ai/**        json / store / http / voice / repo  （实现 domain 的 port）
```

规则：

1. `domain` 不出现 `android.*`、`R.*`、`Context`。这是现在就已经守住的红线，继续守。
2. `ui` 只依赖 `domain` 的抽象，不 `new` 任何 data 实现类；装配统一在模块的注入根。
3. `data` 可以依赖 `domain`，反向不行。
4. 一个页面对应一个 `UiState`（不可变 record）+ 一个 `ViewModel`（唯一可变点）。

## 2. 当前结构（已存在，保留）

| 位置 | 职责 |
| --- | --- |
| `domain/ai/model` | `AiSettings`、`AiPlatformConfig`、`AiModel`、`AiSpeedResult`、`AiFailure` 等不可变模型 |
| `domain/ai/policy` | 纯函数规则：`AiTitlePolicy`、`AiBaseUrlPolicy`、`AiApiKeyPolicy`… |
| `domain/ai/port` | 21 个端口的接口定义（chat / voice / repository / gateway / clock / id） |
| `domain/ai/usecase` | 用例：`RunSpeedTest`、`FetchPlatformModels`、`SendMessage`、`ListConversations`… |
| `data/ai/**` | 端口的实现：JSON 编解码、文件存储、HTTP 客户端、语音链路 |
| `ui/ai/**` | `chat` / `call` / `history` / `settings` / `common` / `markdown` / `widget` |

这套骨架是对的，**v3 不推倒它**。要改的是两处：模型元信息的数据通路，以及页面的信息结构。

## 3. 模型元信息：v3 的核心改动

### 3.1 问题

现在"这个模型是什么样"被拆在三个地方：`AiModel`（名字、上下文、能力）、`AiSpeedResult`（测速）、UI 的 `ModelRow`（把两者拼成一行字）。
结果是页面只能一次显示一条信息，且测速被做成了一个平级的列表项。

### 3.2 目标

引入**聚合读模型** `ModelFacts`（domain 层，只读）：

```
ModelFacts(
    model: AiModel,
    speed: AiSpeedResult?,        // 最近一次测速
    speedState: Idle | Running | Done | Failed,
    usable: boolean,              // 密钥已填 && 该模型未被标记不可用
    facts: List<Fact>             // 展示用的事实片段，由 policy 生成
)
```

- `facts` 由 `AiModelFactsPolicy`（纯函数）生成，顺序固定：速度 → 上下文 → 手动标记。
- UI 只做 `String.join(" · ")`，不再判断"该显示哪个"。
- 测速的进行中状态由 `SpeedTestSession`（一个用例级对象）持有，不再是 view-model 里的一个 boolean 加一个 set。

### 3.3 数据流

```
用户点「全部测速」
   → SpeedTestSession.start(platformId, modelIds)
        ├─ 每个模型: Running → gateway 发一次最小请求
        ├─ 结果: 写回 AiSettingsRepository（持久化）→ 发事件
        └─ 完成/取消: 清空 Running
   → Observable<AiSettings> 变化 → ViewModel 重算 ModelFacts[] → 页面重画
```

关键点：**页面不持有测速结果**，它只是 `AiSettings` 的投影。这样离开页面再回来、杀进程再打开，结果都在。

## 3.4 参数的两级模型（R-11）

数据形状：

```
AiChatPreferences(                // 全局
    globalParamsEnabled: boolean, // 默认 false
    globalScope: Model | Platform | All,
    scopeTargetId: String?,       // scope=Model 时的模型 id
    temperature: Double?,
    topP: Double?,
    maxOutputTokens: Integer?
)

AiPlatformConfig.models: List<AiModel>
AiPlatformConfig.modelParams: Map<String, AiModelParams>   // 新增：按模型覆盖
```

解析（`AiParamsPolicy.resolve`，纯函数，可单测）：

```
resolve(model, platform, prefs):
    platform.modelParams[model.id] 存在 → 用它，来源=模型
    否则 prefs.globalParamsEnabled 且 scope 覆盖该模型 → 用它，来源=全局
    否则 null，来源=默认（不发送）
```

UI 只显示解析结果与来源，不做判断。来源要写出来（`此模型` / `全局` / `平台默认`），否则用户改了全局却没变会以为是 bug。

## 4. 页面架构

每个页面固定三件套：

1. `XxxUiState`（record，不可变）+ `XxxUiState.empty()`
2. `XxxViewModel`：唯一可变点，暴露 `LiveData<XxxUiState>` + `OneShotLiveData<XxxEvent>`
3. `XxxActivity/View`：只做"状态 → 视图"的映射，不含业务判断

### 4.1 平台详情页（v3 信息结构）

```
┌ 身份区      头像 · 平台名 · 模板徽章 · 当前模型
├ 连接       密钥状态 · 上次验证 · [测试连接]  ← 结果留在本区块
├ 模型       模型 · N 个                        [全部测速 / 停止]
│            ├ 保存勾 · 模型名 · 首字·总·上下文 · ⋯
│            └ ＋ 添加模型（拉取 / 手动）
├ 高级(折叠)  接口地址 · 协议
└ 危险区     删除平台（卡外、左对齐、二次确认）
```

折叠规则：默认只展开「连接」与「模型」；「高级」收起时显示一行摘要（地址 + 协议）。

### 4.2 组件复用

`ui/ai/widget` 放跨页组件。v3 新增/固定：

| 组件 | 用在哪 | 责任 |
| --- | --- | --- |
| `AiSectionHeader` | 所有页面 | 标题 + 可选右侧动作（`模型 · 3 个` + `全部测速`） |
| `AiFactLine` | 模型行 | 把事实列表渲染成一行，` · ` 分隔 |
| `AiStatusRow` | 连接状态、测速 | 图标 + 主文案 + 右值，四态 |
| `YoSegmentButton`（Youi Kit） | 设置页 | 文本 / 语音接入形态，两段单选；说明文字在控件外单独一行 |

## 5. 状态机（页面级）

以模型行为例，一个模型在任何时刻只处于其中一种：

```
Idle ──开始测速──► Running ──成功──► Done(首字/总)
                     │
                     ├──失败──► Failed(原因) ──重测──► Running
                     └──取消──► Idle
```

约束：`Running` 必须能在界面上被感知（行内文案），否则等于没有状态。

## 6. 错误与文案

- 领域只产出 `AiFailure(kind, message)`；`AiMessages` 把 `kind` 映射成资源 id。
- 页面不拼错误文案，只决定"放在哪"（行内 / 卡片 / 标题下）。
- **禁止**用 toast 作为某个状态的唯一载体：toast 只用于"操作已生效"类确认。

## 7. 测试策略

| 层 | 方式 | 门槛 |
| --- | --- | --- |
| policy / mapper | JVM 单测 | 每个分支一条 |
| usecase | JVM 单测 + fake port | 含取消、失败、并发 |
| ViewModel | JVM 单测（同步执行器 + 假 LiveData 观察） | 状态迁移全覆盖 |
| 页面 | 真机截图 | 每个状态一张，路径写进 `ai-module-v2.md` §14.3 |

## 8. 迁移阶段（每阶段可独立交付）

| 阶段 | 内容 | 验收 |
| --- | --- | --- |
| P1 | `ModelFacts` + `AiModelFactsPolicy` + 行内事实（本阶段已动） | 模型行显示 `首字 · 总 · 上下文`；283 测试绿 |
| P2 | `SpeedTestSession` 替换 view-model 里的 `speedRunning` + `speedTesting` | 行内 Running 可见；取消后结果保留 |
| P3 | 平台页信息结构：身份 → 连接 → 模型 → 高级 → 危险区 | 折叠态截图 |
| P4 | 连接测试（R-04） | 结果留在连接区块 |
| P5 | 模型 > 8 时搜索、行尾 ⋯ 加「测这一条」 | 截图 |
| P6 | 对话页与历史面板按同一信息密度重排 | 截图 + 评审 |
| P7 | 两级参数（R-11）：数据结构 + `AiParamsPolicy` + 设置页参数区 + 高级区来源标注 | 三来源各一张截图 |

## 9. 风险与不做的选择

- **不引入 DI 框架**：现有构造注入已经够用，引入 Hilt 会把编译时间翻倍。
- **不引入数据库**：会话量与结构（JSON 文件）匹配，换 Room 不解决任何现存问题。
- **不改 lib_ai 的对外包结构**：`com.yovo.lib_ai` 已被 app 层依赖，改包名收益为零。
- **唯一保留的占位**：通话中插入照片（按钮 + 一句说明），因为需要选择器与图像编码，属于另一个需求。
