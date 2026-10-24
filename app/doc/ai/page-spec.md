# AI 模块页面级规格（v5）

> 状态：**待确认**，与 `requirements.md`（需求编号、术语、数值格式）配套。本文只写"长什么样、数据从哪来、每种状态怎么表现"；为什么这样做见需求条目与 `research.md`。
> 取代：`app/doc/ai-module-page-spec.md`、`ai-module-component-spec.md`、`ui-design.md` 中 AI 部分（重写那一轮归档）。

## 1. 全局约定

### 1.1 页面与容器

| 项 | 规定 |
|---|---|
| 页面 | 设置页、平台页、通话页为 Activity，用 `YoPage`（标题栏 56 dp，返回键在左）。对话页是壳层"AI"页签内的页面，自带页头。历史是对话页上的 `YoSheet`。 |
| 页面背景 | `backgroundSecondary`；卡片 `backgroundPrimary`（`YoCard`，圆角 16 dp，无阴影）。通话页背景 `backgroundPrimary`。 |
| 边距 | 页面左右 16 dp；卡片之间 12 dp；卡片内行左右 16 dp；分组标题与卡片 8 dp。 |
| 分组标题 | 卡片上方，12 sp，`fontSecondary`，左缩进 16 dp，高 32 dp。 |
| 列表行 | `YoList`：单行 48 dp、双行 64 dp、三行 96 dp（YoUI `ListDimens`）；分割线左缩进 16 dp。 |
| 触控 | 所有可点元素 ≥48×48 dp（视觉可以更小，用触控扩展）。 |
| 面板 | `YoSheet`，顶部圆角 32 dp，带拖拽条；默认高度见各面板；键盘弹出时随键盘上移。 |
| 菜单 | `YoMenu`，从触发元素锚定弹出。 |
| 确认框 | `YoDialog`：标题＋一段后果说明＋"取消"（左）/动作（右）；危险动作按钮文字 `warning` 色。 |
| 行内进度 | `YoLoadingProgress` 16 dp，放在该行右侧或文字前；不遮挡页面、不阻塞其他操作。 |
| toast | 只用于"已复制"（NFR-07）。 |
| 横幅 | 页面顶部（标题栏下）全宽 40 dp，背景 `componentBackgroundSecondary`，左侧 16 dp 图标＋14 sp 文字。 |

### 1.2 颜色角色

| 用途 | YoColors 角色 |
|---|---|
| 主文字（标题、正文、列表主行） | `fontPrimary` |
| 副文字（副行、事实行、摘要） | `fontSecondary` |
| 元信息（时间、元信息行、计数、来源标签文字） | `fontTertiary` |
| 禁用控件文字 | `textDisabled`；禁用原因本身用 `fontSecondary`（要读得清） |
| 可交互文字/图标、"当前"标签、选中态 | `brand` |
| 错误、失败、危险按钮 | `warning`（红） |
| 需重测、已下线、过期、被裁剪提示、"未设置"会导致功能不可用 | `alert`（橙） |
| 连接正常的状态点 | `confirm`（绿） |
| 标签底色 | `componentBackgroundSecondary`，文字 12 sp |
| 分割线 | `componentDivider` |
| 遮罩 | `scrim` |

禁止在 AI 代码与布局中出现十六进制颜色或自定义 `@color/`（NFR-06）。

### 1.3 字号与密度阶梯（对话页、历史面板、各列表共用）

| 级别 | 字号 / 行高 | 字重 | 用在 |
|---|---|---|---|
| L1 | 20 sp / 28 sp | Medium | 页标题、面板标题、通话页状态文字、用量大数字 |
| L2 | 16 sp / 24 sp | Regular（标题行 Medium） | 消息正文、列表主行、历史行标题、输入框 |
| L3 | 14 sp / 20 sp | Regular | 列表副行、事实行、历史摘要、空态说明、失败卡说明 |
| L4 | 12 sp / 16 sp | Regular | 元信息行、时间、标签、分组标题、禁用原因 |

对话页消息之间垂直间距 16 dp；历史行之间无额外间距（行高决定）。两处同类信息字号一致：标题 L2、摘要/副行 L3、时间/元信息 L4（CHAT-16）。尺寸写进 `res/values/ai_dimens.xml` 的 `ai_text_l1…l4`，两处只引用这组资源。

### 1.4 七态的通用定义

| 状态 | 判定 | 通用表现 |
|---|---|---|
| 加载 | 首次读本地数据未完成 | 内容区居中 `YoLoadingProgress` 24 dp；超过 300 ms 才显示，避免闪烁 |
| 空 | 读取完成且无数据 | 居中：L2 主句＋L3 说明＋最多一个主按钮、一个次按钮 |
| 内容 | 有数据 | 正常列表 |
| 错误 | 本地读取失败（`AiFailureKind.STORAGE`）或该区域请求失败 | 区域内错误条：`warning` 图标＋L3 文案＋"重试"；不清空已有内容 |
| 离线 | `AiNetworkMonitor` 报告无网络 | 顶部横幅（§1.1）；需要网络的按钮禁用并给原因"当前离线"；本地数据照常显示 |
| 无权限 | 本模块唯一的系统权限是麦克风（通话页）；其他页面的"无权限"指平台返回 PERMISSION | 通话页：权限说明态；其他页：作为错误态的一种 |
| 禁用 | 前置条件不满足 | 控件 `textDisabled`＋就近一行原因（L4/L3，`fontSecondary`）；不隐藏控件 |

### 1.5 文案与格式

界面文案以 `requirements.md` 中引号文字为准；数值格式见其 §1 表（`AiFormat`）。省略号统一用 `…`。按钮文案是动词（"测试"、"拉取模型"），不用"确定"。

## 2. 对话页（AI 页签）

### 2.1 结构（自上而下）

```
┌ 页头 56dp ───────────────────────────────┐
│ [历史]   新对话                [通话][更多] │
│          OpenAI · gpt-4o ▾                 │
├ 离线横幅 40dp（仅离线）──────────────────┤
│ 消息列表（填满）                           │
│   用户消息（右，气泡）                     │
│   回答（左，全宽）                         │
│     ▸ 思考过程 · 12 s                      │
│     正文 Markdown                          │
│     gpt-4o · 首字 0.8 s · 41 tok/s · …     │
│                         [回到底部]（浮动） │
├ 不可发原因 32dp（仅需要时）──────────────┤
│ 输入条 ≥56dp                              │
└───────────────────────────────────────────┘
```

### 2.2 字段

| 字段 | 数据来源 | 为空 / 缺失时 |
|---|---|---|
| 主标题 | `AiConversation.title` | "新对话" |
| 副标题 | `AiSelection` → `AiPlatformConfig.name` ＋ `AiModel` 显示名 | "选择模型 ▾"（`brand`） |
| 离线横幅 | `AiNetworkMonitor`（新端口） | 在线时不占位 |
| 用户消息正文 | `AiMessage.text`（role=USER） | 不会为空（发送条件保证） |
| 回答正文 | `AiMessage.text`（role=ASSISTANT） | 生成中且无正文：进行中指示；完成且无正文且无失败："（没有内容）"L3 `fontTertiary` |
| 思考块标题 | `AiMessage.reasoning` 非空；时长＝`AiMessage.reasoningMillis`（新增） | 无思考内容不显示该块；时长缺失只显示"思考过程" |
| 元信息·模型 | `AiMessage.modelLabel` | 省略该段 |
| 元信息·首字 | `AiMessage.firstTokenMillis`（新增） | 旧消息无此值：省略 |
| 元信息·速度 | 生成中 `AiSpeedPolicy.live(...)`；结束 `AiSpeedPolicy.average(message)` | 不满足 SPD-A-03 阈值：省略 |
| 元信息·tokens | `AiMessage.usage.outputTokens` | 平台未回报：省略（不显示估算数） |
| 非正常结束行 | `AiMessage.finishReason` / `status` | STOP 正常结束不显示 |
| 失败卡 | `AiMessage.failure`（kind、message、httpStatus） | 无失败不显示 |
| 不可发原因 | `AiSendPolicy.evaluate(...)` | 可发送或仅"输入为空"时不显示 |
| 输入框 | ViewModel 内存草稿，切对话时存 `AiConversation.draft`（新增可空字段） | 占位"输入消息" |

### 2.3 组件

**页头**
- 位置尺寸：顶部，高 56 dp，左右 4 dp；左右图标按钮 48×48（图标 24 dp `iconPrimary`）；中间标题区居中，宽度＝屏宽 − 2×右侧按钮总宽；主标题 L2 Medium 单行省略；副标题 L4 `fontSecondary` 单行省略＋"▾"。
- 状态：常规；无当前模型时副标题 `brand`；新对话时"重命名""删除对话"禁用；生成中"删除对话"禁用。
- 交互：历史→历史面板；标题区（主副整体）→模型面板；通话→通话页；更多→菜单"新对话"/"重命名"/"删除对话"。
- 边界：平台名＋模型名过长时优先保留模型名（平台名截到 8 字＋…）。

**消息列表**
- 页头与输入条之间，RecyclerView，上下内边距 16 dp，消息间距 16 dp。
- 自动滚动：距底 ≤48 dp 视为贴底，新内容跟随；否则不跟随并显示"回到底部"浮动按钮（40 dp 圆形，`componentBackgroundPrimary`＋阴影，输入条上方 12 dp、右 16 dp）。
- 边界：单条 10 万字；Markdown 渲染节流 100 ms（NFR-09），流式时只重排最后一条。

**用户消息**：右对齐气泡，最大宽 80%，内边距 12/10 dp，圆角 16 dp，背景 `componentBackgroundSecondary`，正文 L2。长按→"复制"。

**回答**
- 左对齐全宽，无气泡；自上而下：思考块（可选）→正文（Markdown，L2）→元信息行（L4 `fontTertiary`，上间距 8 dp）→非正常结束行（L4 `alert`）→失败卡（可选）。
- 状态：生成中（正文末尾三点动效；元信息行显示"≈ 38 tok/s"）、完成、停止、失败、未完成（进程被杀后恢复：元信息行位置"未完成"L4 `alert`＋"重新生成"文字按钮）。
- 长按→"复制"、"重新生成"（仅最后一条且不在生成中）。

**思考块**
- 回答顶部，高 32 dp 可点标题行"▸ 思考过程 · 12 s"（L3 `fontSecondary`）；展开后正文 L3 `fontSecondary`，左侧 2 dp 竖线 `componentDivider`，最多显示 12 行，超出在块内滚动。
- 状态：思考中（"思考中 · 3 s"每秒更新，收起）、完成（收起）、展开。

**失败卡**
- 回答区内，`YoCard` 背景 `componentBackgroundSecondary`，内边距 12 dp；首行 16 dp `warning` 图标＋L3 Medium 分类文案；第二行 L4 平台返回的简短信息（截 120 字，过滤 API Key）；右下文字按钮"重试"和/或"去设置"（`brand`）。
- 交互：重试→重发同一请求，卡片换成生成中；去设置→平台页，并带"定位到哪一行"参数（CON-05）。

**不可发原因行**：输入条正上方，高 32 dp，L4 `fontSecondary`，左 16 dp；可处理的原因带链接色："先选择模型"→模型面板；"当前平台没有 API Key"→平台页。

**输入条**
- 底部，左右 12 dp、下 8 dp（加导航栏 inset）；`YoTextInput` 多行 1–6 行，圆角 20 dp，背景 `componentBackgroundSecondary`，内边距左 16 右 56 dp；发送/停止按钮 40 dp 圆形，位于输入框内右下 8 dp。
- 状态：空（发送 `textDisabled`）、可发送（`brand` 填充＋`iconOnPrimary` 箭头）、禁用（有原因时，同空）、生成中（停止：方块图标）。
- 交互：回车换行（不发送）；发送后清空并收回 1 行；生成中点停止→CHAT-07。
- 边界：粘贴长文允许；键盘弹出时贴底则列表跟随。

**空态（三种，替代消息列表区）**：居中，最大宽 280 dp；48 dp 图标 `iconTertiary`；L2 主句；L3 说明；`YoButton` 主按钮。文案见 CHAT-03。就绪态的"{平台} · {模型}"可点→模型面板。

**模型面板（Sheet，60% 高，可拖到 90%）**
- 标题"选择模型"；>8 个模型时顶部搜索框；分组标题＝平台名（当前平台在前）；行：显示名 L2＋事实行 L3（"首字 645 ms · 128K 上下文"，都没有则单行 48 dp），右侧当前模型打勾 `brand`；已下线行排组末，文字 `fontTertiary`＋"已下线"标签，仍可选。
- 底部固定"管理平台"文字按钮→设置页。
- 空：无平台"还没有平台"＋"添加平台"；有平台无对话模型"平台里还没有模型"＋"去添加"→第一个平台的平台页。

## 3. 历史面板

### 3.1 结构

```
┌ 拖拽条 ─────────────────────────────┐
│ 历史对话                [新对话][⋯] │  56dp
│ [搜索对话                 ]          │  48dp
│ 今天                                 │  分组标题 32dp
│ 标题……………………  当前  14:20         │  行 64dp
│ 摘要……………………                       │
│ 昨天 …                               │
└──────────────────────────────────────┘
```

### 3.2 字段

| 字段 | 数据来源 | 为空 / 缺失时 |
|---|---|---|
| 行标题 | `AiConversationSummary.title` | "新对话" |
| 行时间 | `AiConversationSummary.updatedAtMillis` → `AiFormat.historyTime` | 不会缺失 |
| 行摘要 | `AiConversationSummary.preview`（最后一条消息纯文本，去 Markdown） | "（没有消息）"`fontTertiary` |
| 当前标签 | `summary.id == currentConversationId` | 不显示 |
| 搜索命中摘要 | `AiHistorySearch.match(...)` 返回片段与命中区间 | 只命中标题时显示普通摘要 |
| 分组 | `AiHistoryGrouping.group(summaries, now)` | 空组不显示 |

### 3.3 组件

**面板头**：高 56 dp；标题 L1；右侧"新对话"图标按钮、"更多"（菜单仅"清空全部"，N=0 禁用）。

**搜索框**：`YoTextInput` 单行，高 40 dp（触控 48），左右 16 dp，左侧搜索图标，有内容时右侧清除按钮；防抖 200 ms；搜索时列表不分组。

**历史行**：双行 64 dp；主行标题 L2 单行省略，右侧时间 L4 `fontTertiary`；副行摘要 L3 `fontSecondary` 单行省略；当前对话在时间左侧加"当前"标签（`brand`）。命中词 `brand`＋Medium。
- 交互：点→打开对话并关面板；长按→菜单"重命名"、"删除"。
- 边界：1,000 条按页加载（每页 50），滚到底自动加载。

**重命名**：`YoDialog` 单行输入，预填原标题并全选，1–40 字，空时"重命名"禁用。

**空态**：无对话"还没有历史对话"＋"开始的对话会保存在这里"；搜索无结果"没有找到「{q}」"。

## 4. 设置页

### 4.1 结构（SET-01 顺序）

```
YoPage 标题"AI 设置"
当前模型  [当前模型        OpenAI · gpt-4o  >]
平台      [OpenAI                  当前    >]
          [  OpenAI · 12 个模型 · ● 正常     ]
          [DeepSeek                        >]
          [+ 添加平台                        ]
对话      [系统提示词  你是…（两行）        >]
          [上下文条数                  20  >]
          [显示思考过程              (开)    ]
生成参数  [使用全局参数              (关)    ]
          （开启后）温度 / Top P / 最大输出 / 作用范围
说话方式  [说话方式        级联 | 全双工      ]
          [转写模型   OpenAI · whisper-1   >]
          [朗读声音   系统默认             >]
          [停顿判定   900 ms               >]
用量      [今天 12 次请求 · 3,456 tokens   >]
隐私说明  （L3 文字段落）
```

### 4.2 字段

| 字段 | 数据来源 | 为空 / 缺失时 |
|---|---|---|
| 当前模型值 | `AiSelection` | "未选择"（`fontTertiary`） |
| 平台行名称 | `AiPlatformConfig.name` | 不会为空（PLT-04） |
| 平台行副行 | 模板名＋`models.size`＋`AiConnectionMapper.summary(...)` | 无模型"还没有模型"；未测试"未测试" |
| 系统提示词摘要 | `AiChatPreferences.systemPrompt` 前两行 | "未设置" |
| 上下文条数 | `AiChatPreferences.contextMessages` | 0 → "全部" |
| 显示思考过程 | `AiChatPreferences.showReasoning` | 默认开 |
| 全局参数 | `globalParamsEnabled`、temperature/topP/maxOutputTokens、`globalScope`＋`globalModelKeys`（新增） | 各项为空显示"平台默认" |
| 说话方式 | `AiVoiceSettings.mode` | 级联 |
| 转写模型 / 实时模型 | `AiVoiceSettings.transcriptionModel` / `realtimeModel` | "未设置"（`alert`，通话会被禁用） |
| 朗读声音 | `AiVoiceSettings.synthesisVoice` | "系统默认" |
| 停顿判定 | `AiVoiceSettings.silenceTimeoutMillis` | 900 ms |
| 用量摘要 | `LoadUsageSummary(TODAY)` | "今天还没有用量" |

### 4.3 组件

**平台卡**：每平台一行双行 64 dp；主行名称 L2＋"当前"标签（如适用）＋箭头；副行 L3 `fontSecondary`，连接状态段带 6 dp 状态点（正常 `confirm`、失败 `warning`、需重测 `alert`、未测试无点）。末行"+ 添加平台"单行 48 dp `brand`。
- 交互：点平台→平台页；点添加→模板面板。
- 边界：0 个平台时卡片只有"+ 添加平台"，上方 L3 提示"添加一个平台后才能对话"。

**模板面板（Sheet，自适应高）**：标题"添加平台"；六行双行：主行模板名，副行一句说明（如"官方接口，需要 OpenAI 的 API Key"/"兼容 OpenAI 接口的任何服务，需要填写接口地址"）。点即创建（PLT-02）。

**系统提示词面板（Sheet，90%）**：多行输入充满；底部右侧计数"{n}/4000"L4（超过时 `warning` 且不保存）；"恢复默认"文字按钮；关闭面板即保存。

**上下文条数**：`YoDialog` 内 `YoSlider` 0–50，步长 1，显示当前值（0 为"全部"）。

**全局参数**：开关行；开启后展开四行：温度、Top P、最大输出（值为数字或"平台默认"，点开编辑对话框：滑块/数字＋"平台默认"复选）、"作用范围"（"全部模型"/"{N} 个模型"，点开范围面板：单选"全部模型"/"指定模型"，后者下方是按平台分组的对话模型多选）。关闭开关时四行折叠，值保留。

**说话方式**：`YoSegmentButton` 两段；全双工不可用时该段禁用，下方 L4 原因"需要支持实时语音的平台"。转写模型/实时模型行→模型选择面板（§7.4，只列对应能力的模型）。朗读声音行→系统 TTS 声音列表；无可用声音时值"不可用"`alert`，点击→系统朗读设置。停顿判定→`YoDialog` 滑块 300–4000 ms，步长 100。

**用量行**：双行；点开用量面板（§7.5）。

**隐私说明**：卡片外 L3 `fontSecondary` 段落，左右 16 dp，下边距 24 dp。

## 5. 平台页

### 5.1 结构（顺序固定：身份 → 连接状态 → 模型 → 高级 → 危险区）

```
YoPage 标题＝平台名称
① 身份     [名称          OpenAI 2       ]（可编辑）
           [模板          OpenAI         ]（只读）
② 连接     [● 正常 · 首字 640 ms   [测试]]
           [  用 gpt-4o-mini 测试 · 3 分钟前]
           [API Key       sk-••••a1b2  获取]
           [测试模型      gpt-4o-mini     >]
③ 模型 · 12            [全部测速][添加 ▾][⋯]
           [搜索模型（>8 个时）             ]
           [最快 gpt-4o-mini · 412 ms · 1 个失败]
           [gpt-4o           当前          ⋯]
           [首字 645 ms · 128K 上下文        ]
           …
④ 高级     api.openai.com · Chat Completions · 无单独参数  ▸
           （展开）接口地址 / 请求地址预览 / 协议 / 生成参数
⑤ 危险区   [删除平台]（warning 文字）
```

### 5.2 字段

| 字段 | 数据来源 | 为空 / 缺失时 |
|---|---|---|
| 标题栏 | `AiPlatformConfig.name` | — |
| 名称 | 同上 | 编辑中清空→行内错误，失焦恢复 |
| 模板 | `AiPlatformTemplate.name`（按 `templateId`） | 模板已不在目录（旧数据）："未知模板"`fontTertiary` |
| 连接主文案 | `AiConnectionMapper.main(connection, currentFingerprint, preconditions, testing)` | "未测试" |
| 连接副行 | `connection.modelId`＋`testedAtMillis` | 未测试不显示 |
| API Key | `SecretStore`（经 `AiPlatformConfig.apiKey`）→ `AiFormat.maskKey` | "未填写"（`alert`） |
| 获取链接 | 模板的 API Key 页面地址（lib_ai 描述符已有） | 自定义模板无：不显示 |
| 测试模型 | `AiPlatformConfig.testModelId`（新增）→ CON-03 回退 | 无对话模型："先添加模型" |
| 模型计数 | `models.size` | 0 |
| 模型行 | `AiModel`＋`speedResults[id]`＋选中/测速中 → `AiModelRowMapper` | 见 MOD-02 |
| 批量结论 | ViewModel 内存（本次批量） | 未批量不显示 |
| 高级摘要 | `baseUrl` 主机名＋`protocol`＋单独参数模型数 | 自定义无地址："未填写接口地址"（`alert`） |
| 接口地址 | `AiPlatformConfig.baseUrl` | 自定义为空：占位"https://example.com"＋"必填" |
| 请求地址预览 | `AiEndpointPreview.of(baseUrl, protocol)` | 地址不合法：不显示 |
| 协议 | `AiPlatformConfig.protocol`，可选集＝模板 `protocols` | 只有一种：只读行 |
| 参数来源汇总 | `modelParams` 中至少一项非空的模型数 | "无" |

### 5.3 组件

**① 身份卡**：两行单行 48 dp。名称行右侧是可编辑文本（点击变 `YoTextInput`，"完成"或失焦保存），错误时行下 L4 `warning` 原因。模板行值 `fontSecondary`，无箭头。

**② 连接卡**
- 状态行（双行 64 dp）：左侧 8 dp 状态点（未测试空心 `iconTertiary`、测试中 16 dp 进度、正常 `confirm`、失败 `warning`、需重测 `alert`）；主文案 L2；副行 L3 `fontSecondary`；右侧小号 `YoButton`"测试"/"取消"。前置条件不满足：按钮禁用，主文案换成原因（`fontSecondary`），无状态点。
- 失败时整行可点：滚到指向行并对该行做 1 s 背景高亮（`interactionPressed`），需要时先展开高级区。
- API Key 行（单行 48 dp）：值为掩码；右侧"获取"文字按钮（有链接时，打开浏览器）；点行→`YoDialog` 密码输入框（显示/隐藏切换，不预填，占位为模板提示如"sk-…"），"保存"后立即生效，连接状态变为需重测。不提供复制。
- 测试模型行（单行 48 dp）：值为显示名；点→模型选择面板（本平台对话模型）。

**③ 模型区**
- 标题行（卡片外 48 dp，L2 Medium）："模型 · {N}"，右侧"全部测速"、"添加 ▾"（菜单）、"⋯"（菜单："删除测速失败的模型（{N}）"、"多选"）。
- 进行中替换：拉取中→"拉取中…"＋进度；批量测速→"测速中 {k}/{n}"＋"取消"；多选→"已选 {N}"＋"全选"、"测速"、"删除"、"完成"。
- 搜索框（>8 个）：同历史搜索框，占位"搜索模型"。
- 错误条（拉取失败）：卡片顶部 L3 `warning`"拉取失败 · {分类文案}"＋"重试"；下次成功消失。
- 结论行：卡片顶部 L3 `fontSecondary`（SPD-B-06），离开页面消失。
- 模型行：双行 64 dp（有显示名且不同于 ID 时三行 96 dp，第三行 ID L4 `fontTertiary`）。主行 L2 显示名＋标签（"当前"`brand`、"已下线"`alert`、类型标签）；事实行 L3；右侧"⋯"48 dp→行菜单（MOD-09）。测速中：事实行开头 16 dp 进度＋"测速中…"。多选模式：左侧复选框，隐藏"⋯"。
- 交互：点行＝打开行菜单（同"⋯"，避免误设当前）；长按＝进入多选并选中该行。
- 边界：500 个模型流畅（DiffUtil）；测速中删除该模型→取消其请求；批量测速中"多选"禁用。

**④ 高级区**
- 收起：48 dp，"高级"L2＋摘要 L3（ADV-01），右侧 ▸/▾。
- 展开：接口地址行（双行，可编辑，右侧"恢复默认"）；请求地址预览（L4 `fontTertiary`，长按复制）；协议行（单选面板；只一种时只读）；生成参数行（"{N} 个模型单独设置"→参数汇总面板 §7.1）。
- 自定义且无地址：默认展开，地址行标签后"必填"`warning`。

**⑤ 危险区**：卡片单行 48 dp，"删除平台"`warning` 居左；点→确认框（PLT-07）。

## 6. 通话页

### 6.1 结构

```
YoPage（无标题文字，左上"关闭"，右上 平台 · 模型 L4）
        （中上）状态文字 L1      通话时长 L4
        （中）动效区 200dp
        （下）字幕：你：……（L3 fontSecondary）
                     回答：……（L2）
 [静音]        [音频输出]        [挂断]
```

### 6.2 字段

| 字段 | 数据来源 | 为空 / 缺失时 |
|---|---|---|
| 状态文字 | `AiVoiceSnapshot.state` → `AiCallStateMapper` | 接通中 |
| 通话时长 | `AiVoiceSnapshot.connectedAtMillis`（新增）＋`AiClock` | 接通前不显示 |
| 平台·模型 | 级联：当前模型；全双工：`AiVoiceSettings.realtimeModel` | — |
| 用户字幕 | `AiVoiceSnapshot.lastUserText`（新增） | 不显示该行 |
| 回答字幕 | `AiVoiceSnapshot.lastAssistantText`（新增，随朗读推进） | 不显示该行 |
| 静音 | `AiVoiceSnapshot.muted` | 关 |
| 音频输出 | `AudioManager` 可用输出 | 只有扬声器时按钮禁用 |
| 缺失项 | `AiCallReadinessPolicy.missing(settings, selection, catalog, permission, online)` | 全满足不显示 |

### 6.3 组件

- **状态文字**：顶部下 96 dp 居中 L1；时长 L4 `fontTertiary` 在下方 4 dp。
- **动效区**：居中 200×200 dp（`VoiceOrbView` 重做）；聆听＝随输入音量缩放、思考＝缓慢旋转、说话＝随输出音量波动；其他状态静止，透明度 40%；系统关闭动画时全部静止。
- **字幕**：动效区下 24 dp，左右 32 dp，用户与回答各最多 3 行（超出显示末尾 3 行）。
- **控制按钮**：底部距导航栏 48 dp，三按钮等分，64 dp 圆形＋下方 L4 文字；静音开启时按钮反色填充；挂断 `warning` 填充；音频输出点开菜单（扬声器/听筒/耳机，当前打勾）。
- **打断**：说话中点动效区或检测到用户语音即打断（CALL-06）。
- **禁用态**（前置缺失）：动效区换成 48 dp 图标＋L2"还不能开始语音通话"＋缺失项列表（每项 L3）＋主按钮"去设置"；控制区只保留"关闭"。
- **无权限态**：L2"语音通话需要使用麦克风"＋L3 说明＋主按钮"授权"（永久拒绝后"去系统设置"）。
- **失败态**：状态文字"通话中断 · {分类文案}"（`warning`），控制区换成"重新接通"/"挂断"。
- **结束态**："通话结束 · 3:21"，1.5 s 后关闭页面。
- 边界：来电/音频焦点丢失→自动静音并显示"已暂停"；蓝牙耳机断开→切回扬声器，状态文字下 L4"已切到扬声器"2 s。

## 7. 公共面板

### 7.1 单模型参数面板（Sheet，自适应高）
- 标题"{模型} 的生成参数"；三行（温度、Top P、最大输出）：名称 L2、生效值 L2 右对齐、来源标签 L4（"此模型"`brand`/"全局"`fontSecondary`/"平台默认"`fontTertiary`）；被协议裁剪时第二行 L4 `alert`"此协议最大 1，发送时按 1"。
- 点行→编辑对话框：滑块（温度步长 0.1、Top P 0.05）或数字输入（最大输出）＋"清除此模型设置"（无此模型值时隐藏）。
- 来源：`AiParamsPolicy.resolve(platform, model, prefs)` 返回每项 值＋来源。
- 参数汇总面板（ADV-05）：列出单独设置过的模型，每行"{模型}"＋副行"温度 0.7 · 最大输出 2,048"，点进本面板。

### 7.2 拉取模型面板（Sheet，90%）
- 标题"选择要添加的模型"；搜索框；分组（对话/向量/图像/语音/审核/其他，空组隐藏），分组标题右侧"全选本组"；行：复选框＋ID L2＋上下文 L4（有时）；已添加行禁用并显示"已添加"；底部固定主按钮"添加 {N} 个"（N=0 禁用）。
- 空：远端返回 0 个"平台没有返回模型，可以手动添加"＋"手动添加"。

### 7.3 手动添加/编辑模型面板（Sheet，自适应高）
- 字段：模型 ID（必填，编辑时只读）、显示名（可选，占位同 ID）、上下文（数字，后缀"K"，可选）、类型（`YoSelect`，默认"对话"）。错误 L4 `warning` 在字段下。底部主按钮"添加"/"保存"。

### 7.4 模型选择面板（测试模型、转写模型、实时模型）
- 与对话页模型面板同样式；按用途过滤；选择即保存并关闭。

### 7.5 用量面板（Sheet，90%）
- 顶部 `YoSegmentButton`"今天/近 7 天/近 30 天/全部"；指标 2×2：请求（L1 大数字＋L4"其中 {M} 次失败"）、输入 token、输出 token、费用（无费用记录时不显示该格，其余 3 格一行）；"按模型"前 5＋"其他"（名称 L2，右侧 tokens L3）；"按来源"四行；底部"清空用量记录"`warning`。
- 空："这段时间没有用量"。
- 来源：`LoadUsageSummary(range)` → `AiUsageSummary`（新增 failedCount、bySource、costs）。

## 8. 七态矩阵

"—"表示该状态在此区域不存在（括号写原因）。截图文件名 `{页}-{状态}.png`；预检截图在 `app/doc/ai/captures/`，真机截图在 `app/doc/device-captures/ai/`。

| 区域 | 加载 | 空 | 内容 | 错误 | 离线 | 无权限 | 禁用 |
|---|---|---|---|---|---|---|---|
| 对话页 | 读对话：居中进度（>300 ms）`chat-loading` | 三种空态 `chat-empty-noplatform` `chat-empty-nomodel` `chat-empty-ready` | `chat-content`、生成中 `chat-streaming` | 失败卡 `chat-error`；本地读取失败错误条 | 横幅＋发送禁用 `chat-offline` | 平台 PERMISSION→失败卡＋去设置（同 `chat-error` 样式） | 发送禁用＋原因 `chat-disabled` |
| 历史面板 | `history-loading` | `history-empty`、搜索无结果 `history-search-empty` | `history-content`、`history-search` | 读取失败错误条 `history-error` | —（纯本地，照常可用） | —（无系统权限） | 清空全部 N=0 禁用（含在 empty） |
| 设置页 | `settings-loading` | 无平台 `settings-empty` | `settings-content`、全局参数展开 `settings-params` | 读取失败错误条 `settings-error` | 横幅提示（本页无联网操作）`settings-offline` | — | 全双工不可用禁用＋原因（含在 content） |
| 平台页·连接 | —（本地即时） | 未测试 `platform-untested` | 正常 `platform-ok` | 失败 `platform-failed`、需重测 `platform-stale` | 测试禁用"当前离线" `platform-offline` | PERMISSION 失败（同失败态） | 缺 Key/地址/模型 `platform-disabled` |
| 平台页·模型 | 拉取中 `platform-fetching` | `platform-models-empty` | `platform-models`、`platform-speed-running`、`platform-speed-done`、`platform-multiselect`、`platform-search` | 拉取失败 `platform-fetch-error`；行内测速失败（含在 models） | 拉取/测速禁用（含在 offline） | — | 拉取禁用"先填写 API Key"（含在 models-empty） |
| 平台页·高级 | — | 自定义无地址：展开＋必填 `platform-custom-new` | 收起/展开 `platform-advanced` | 地址格式错误行内（含在 custom-new） | — | — | 单协议只读（含在 advanced） |
| 通话页 | 接通中 `call-connecting` | —（无列表数据） | `call-listening` `call-thinking` `call-speaking` `call-muted` | `call-error` | 禁用态原因"当前离线" `call-offline` | 麦克风未授权 `call-permission` | 前置缺失 `call-disabled` |
| 用量面板 | 进度（>300 ms） | `usage-empty` | `usage-content`、`usage-content-cost` | 读取失败错误条 | —（本地） | — | — |

## 9. 数据与分层

### 9.1 领域记录变更（全部保留旧构造函数，新字段给默认值）

| 记录 | 新增字段 | 默认 | 用于 |
|---|---|---|---|
| `AiPlatformConfig` | `testModelId`、`connection`（`AiConnectionResult`） | null | CON-03/06 |
| `AiConnectionResult`（新） | state(OK/FAILED)、firstTokenMillis、failureKind、message、modelId、testedAtMillis、fingerprint | — | CON |
| `AiModel` | `kind`（`AiModelKind`）、`remoteMissing` | CHAT / false | MOD-02/07（顺序沿用列表顺序） |
| `AiSpeedResult` | `fingerprint` | null（视为"未知"，不标过期） | SPD-B-07 |
| `AiChatPreferences` | `globalScope`(ALL/SELECTED)、`globalModelKeys` | ALL / 空 | GEN-02 |
| `AiMessage` | `firstTokenMillis`、`reasoningMillis`、`platformId`、`modelId` | null | SPD-A、CHAT-08、CHAT-10 |
| `AiConversation` | `draft` | null | 输入草稿 |
| `AiUsageRecord` | `source`(CHAT/CALL/CONNECTION_TEST/SPEED_TEST)、`cost`（金额＋币种） | CHAT / null | USE-03/05 |
| `AiUsageSummary` | `failedCount`、`bySource`、`costs` | 0 / 空 / 空 | USE |
| `AiVoiceSnapshot` | `lastUserText`、`lastAssistantText`、`connectedAtMillis`、`muted`、`outputRoute` | null / false | CALL |

新端口：`AiNetworkMonitor`（在线状态，平台实现用 `ConnectivityManager`）。连接测试复用测速网关的"首片段即停"能力，不另开网关。

lib_ai 改动（尽量为零）：①首字由 app 层用 `AiClock` 在第一个 delta 事件打点，lib_ai 不改；②`YoLatencyProbe` 已返回 `firstTokenMillis`，需验证它在首片段后会断开，不会则加"首片段即停"选项；③模型类型用已有的 `YoModelInfo.kind`。②在实施第一轮用 MockWebServer 验证，结论写进 `refactor-log.md`。

### 9.2 每页分层落点（Java＋`androidx.lifecycle` ViewModel/LiveData，沿用现有依赖）

| 页 | State | Mapper（纯函数） | Policy（纯函数，domain） | ViewModel | View |
|---|---|---|---|---|---|
| 对话 | `ChatState`、`MessageItem`、`ComposerState`、`ChatEmpty` | `ChatStateMapper`、`MessageItemMapper` | `AiSendPolicy`、`AiSpeedPolicy`、`AiContextPolicy`、`AiTitlePolicy`、`AiFailureActionPolicy` | `ChatViewModel` | `ChatPage`、`MessageAdapter`、`AnswerView`、`ComposerView`、`ModelPickerSheet` |
| 历史 | `HistoryState`、`HistoryRow` | `HistoryStateMapper` | `AiHistoryGrouping`、`AiHistorySearch` | `HistoryViewModel` | `HistorySheet` |
| 设置 | `SettingsState` | `SettingsStateMapper` | `AiParamsPolicy`、`AiVoiceAvailabilityPolicy` | `SettingsViewModel` | `SettingsActivity`、各面板 |
| 平台 | `PlatformState`、`ConnectionState`、`ModelRow`、`AdvancedState` | `PlatformStateMapper`、`AiConnectionMapper`、`AiModelRowMapper` | `AiConnectionPolicy`（前置条件、指纹、过期）、`AiPlatformNamePolicy`、`AiEndpointPreview`、`AiBatchSpeedPolicy` | `PlatformViewModel` | `PlatformActivity`、`ModelAdapter`、各面板 |
| 通话 | `CallState` | `AiCallStateMapper` | `AiCallReadinessPolicy`、`AiCallStateMachine` | `CallViewModel` | `CallActivity`、`VoiceOrbView` |
| 公共 | — | `AiFormat` | — | — | `ai_dimens.xml` |

依赖方向：View → ViewModel → usecase/port（domain）← data 实现；`wiring/AiGraph` 是唯一同时看到 ui 与 data 的地方。ViewModel 经 `ViewModelProvider.Factory` 从 `AiGraph` 取用例。一次性事件（跳转、确认框、复制）走单独的 Event 流，不放进 State。

### 9.3 保留与重写的边界（待 Q16 确认）

- 重写：`ui/ai/{chat,history,settings,call,common,widget}` 全部页面代码、布局、相关 strings。
- 保留并扩展：`ui/ai/markdown`（解析器，不是页面）、`domain/ai/**`（加字段、加 Policy）、`data/ai/**`（加字段读写）、`wiring/AiGraph`、`lib_ai`。

## 10. 截图与验收步骤

- 预检（沙箱，Robolectric 原生绘制）：`AiScreenshotTest` 按 §8 文件名输出到 `app/doc/ai/captures/`；每页重写完成即生成并提交。
- 真机（用户 Windows 机，Q1）：实施时提供 `app/doc/ai/device-capture.ps1`：`adb install -r app\build\outputs\apk\debug\app-debug.apk` → 通过调试 Intent 逐页逐状态打开 → `adb exec-out screencap -p` 存到 `app/doc/device-captures/ai/`，文件名同 §8。
- 每轮自查：对照 §2–§7 的字段与状态逐项勾，结果写进 `refactor-log.md`。
