# AI 模块调研（v5，读源码版）

> 本文是 v5 需求（`requirements.md`）与页面规格（`page-spec.md`）的依据。
> 结论只引用**本轮读到的源码原文**；读不到的明确标"未查证"，不写成结论。

---

## 0. 方法与可信度

- **取证方式**：2026-10-24 在构建沙箱里对 20 个开源客户端执行 `git clone --depth 1`，直接读源码与语言包。
  每条证据写成 `项目 文件:行号`，对应提交见 §1。
- **与 v4 的区别**：v4 调研时 `github.com` 被 DNS 拦截，16 条里有 9 条是"搜索摘要（B 档）"。本轮 GitHub 可直连，
  §5 逐条复核了 v4 的 B 档结论。
- **不计入的项目**：Msty、Chatwise、LM Studio 应用本体闭源，读不到源码，**本轮不引用**；
  v4 中引用它们的结论（如"Chatwise 测速后给最快可用结论"）一律视为**未查证**。
- **克隆了但没用的**：Maid、Ollama App，本轮未从中得出结论，不计入数量。
- 原始笔记（含更多文案原文）在沙箱 `/data/research/notes.md`，要点已全部并入本文。

## 1. 项目清单（18 个有原文证据）

| # | 项目 | 仓库 @ 提交（日期） | 形态 | 本轮读了什么 |
| --- | --- | --- | --- | --- |
| 1 | Cherry Studio | CherryHQ/cherry-studio @4558f53（10-24） | Electron | 平台页、连接检查、批量模型检测、模型管理、助手参数、zh-cn 文案 |
| 2 | LobeChat | lobehub/lobe-chat @ff1b24c（10-24） | Web | 平台配置、连通性检查、模型列表、消息测速口径、话题分组 |
| 3 | NextChat | ChatGPTNextWeb/NextChat @1d3dc33（10-20） | Web | 面具级"使用全局设置" |
| 4 | Open WebUI | open-webui/open-webui @91ea4e3（10-16） | Web | 连接验证、三层参数合并、通话浮层、历史分组 |
| 5 | Chatbox | chatboxai/chatbox @0ac6385（09-24） | Electron/移动 | 平台页、模型能力测试 |
| 6 | Jan | janhq/jan @a4f0ddd（10-23） | Tauri | 平台页、密钥检测分类、单模型参数 |
| 7 | AnythingLLM | Mintplex-Labs/anything-llm @a5bb8b9（10-23） | Web/桌面 | 工作区 vs 系统模型、温度空值语义 |
| 8 | big-AGI | enricoros/big-AGI @306cff4（10-23） | Web | 同类多实例、模型默认/用户参数分存 |
| 9 | RikkaHub | rikkahub/rikkahub @fa5d5f3（10-24） | **Android 原生** | 平台详情页签、连接测试、模型区、助手参数、历史分组、统计页 |
| 10 | GPTMobile | Taewan-P/gpt_mobile @d99a41f（10-10） | **Android 原生** | 平台实体与设置页分节、页面状态文案 |
| 11 | PocketPal AI | a-ghorbani/pocketpal-ai @213cdfe（10-23） | RN 移动 | 消息气泡速度、参数来源与继承 |
| 12 | SillyTavern | SillyTavern/SillyTavern @06bde93（09-14） | Web | 常驻连接状态、Test Message |
| 13 | Kelivo | Chevey339/kelivo @199647a（10-23） | Flutter 移动 | 平台详情、批量检测与清理 |
| 14 | Page Assist | n4ze3m/page-assist @6898576（10-20） | 浏览器扩展 | 当前聊天/全局/模型三层参数 |
| 15 | Chatbot UI | mckaywrigley/chatbot-ui @81328b6（2024-06，停更） | Web | 工作区默认、按模型限定参数范围 |
| 16 | LibreChat | danny-avila/LibreChat @94ae8a4（10-23） | Web | 参数面板、默认值展示、预设 |
| 17 | Enchanted | gluonfield/enchanted @dc9bff8（07-07） | iOS 原生 | 对话页离线态、定时探活、TTS 缺音色出口 |
| 18 | ChatterUI | Vali-98/ChatterUI @1753a4a（10-01） | RN Android | 连接模板、拉取失败分类 |

---

## 2. 跨项目的五条硬结论

1. **"连得上"和"能出字"是两件事。** Open WebUI（`backend/open_webui/routers/openai.py:991-1016`）和 Jan
   （`web-app/src/routes/settings/providers/$providerName.tsx:430-470`）只请求 `/models`；LobeChat
   （`ProviderConfig/Checker.tsx:136-186`）、SillyTavern（`public/scripts/openai.js:6172-6184`）、Cherry Studio
   （`hooks/providerSetting/useProviderConnectionCheck.ts`）发一条真实对话。只有后者能证明"密钥对、模型能用"。
2. **结果写在行里，而且要留下来。** Cherry Studio（`ModelList/ModelCheckStatus.tsx`）、Kelivo
   （`lib/features/provider/pages/provider_detail_page.dart:1457`）、Jan（`$providerName.tsx:924`）把结果写在对应行；
   但三者都只存内存，离开页面就没了。用 toast 报结果的（Open WebUI、SillyTavern、Jan 拉取、Cherry Studio 连接失败）
   关掉就找不回。**我们要行内＋持久化。**
3. **参数要能"回到不发送"。** Cherry Studio 每个参数带 `enableXxx` 且默认 false
   （`src/renderer/utils/resourceCatalog/assistantForm.ts:86-90`）；Open WebUI 每个参数有"默认/自定义"
   （`chat/Settings/Advanced/AdvancedParams.svelte`）；RikkaHub 用 `temperature: Float? = null`（`data/model/Assistant.kt:25`）。
   Page Assist 用 `||` 判空，温度 0 会被当成未设置（`src/models/index.ts:124`）——**未设置必须是 null，不能是假值。**
4. **"改过即固定"有成熟先例，但粒度都太粗。** NextChat 改任一参数就把整份配置脱离全局
   （`app/components/mask.tsx:84-94`）；PocketPal 标"继承设置/自定义设置"（`src/locales/zh.json` palGenerationSettingsSheet）。
   两者都看不出**哪一个参数**来自哪里。
5. **测速口径要先定义再显示。** LobeChat 写死了口径：TTFT＝从发送到首个 token；tok/s＝**从首个 token 起算**
   （`locales/zh-CN/chat.json` messages.tokenDetails.speed）。Cherry Studio、PocketPal 在消息上同时给首字与速度。
   EarBuds 现有实现用"总耗时"算速度（`ui/ai/chat/AiAssistantMessageView.java:357` → `AiFormat.tokensPerSecond`），
   把首字等待也算了进去，数值偏低。

---

## 3. 组件四问

每个组件回答：① 负责什么　② 为什么必须存在（回答用户哪个问题）　③ 别人怎么做（好 ✅ / 差 ❌）　④ 我们的决定，以及为什么不选另一种。

### 3.1 平台列表与添加平台（设置页）

1. **职责**：列出已接入的每一套连接，提供进入与新增。
2. **回答**："我接了几套？哪套能用？现在用的是哪套？"
3. **别人**：big-AGI 允许同一厂商 "Add another … service"（`models-modal/ModelsServiceSelector.tsx:262-268`）✅；
   ChatterUI 先选连接模板再填（`ConnectionsManagerScreen/TemplatePicker.tsx`）✅；
   LobeChat 分"已启用/未启用"两段并支持自定义排序（`modelProvider.json` menu.list.*），对只有 1–3 个平台的手机用户过重 ❌。
4. **决定**：设置页"平台"分组，每行＝名称＋一句状态（连接状态 · 模型数），当前平台标"当前"；末行"添加平台"
   → 选模板（内置 4 个＋自定义 OpenAI 兼容＋自定义 Anthropic 兼容）→ 直接进平台页并弹出 API Key 输入。
   **不做启用/停用开关**：平台少，"没填 API Key"本身就等于不可用，再加开关会多一个真相来源。

### 3.2 平台身份（平台页 ①）

1. **职责**：说明"这是哪一套配置"。
2. **回答**："我改的是哪个？它基于哪个模板？"
3. **别人**：GPTMobile 把名称放在 Connection 分节（`presentation/ui/setting/PlatformSettingScreen.kt:232`）；
   Cherry Studio 用独立的 ProviderHeader（`ProviderSetting.tsx:118`）。
4. **决定**：页标题＝平台名；身份卡两行：名称（可改）、模板（只读，如"DeepSeek · 内置模板"）。
   当前平台在身份卡第三行写"当前平台"。不做首字母方块头像（装饰，不回答问题）。

### 3.3 连接状态（平台页 ②）

1. **职责**：给出"这套配置此刻能不能用"的**持久**结论，并能复测。
2. **回答**："通不通？不通卡在哪一步？"
3. **别人**：SillyTavern 在 Connect 旁常驻状态文字与指示灯（`public/index.html:2362-2423`、`public/script.js:813-818`）✅；
   LobeChat 结果行内、记住检查用的模型（`Checker.tsx:227` updateAiProviderConfig）✅，但刷新即失 ❌；
   Chatbox 一次测文本/视觉/工具三项（`src/renderer/utils/model-tester.ts:50-150`），结果只在弹窗 ❌；
   Jan 把错误分成 401/403/429/网络（`$providerName.tsx:452-454`）✅；Open WebUI 只列模型且用 toast ❌；
   SillyTavern 在按钮提示里写明会计费（`index.html:4073`）✅。
4. **决定**：
   - 一行常驻：`连接状态　正常 · 首字 0.8 s`，副行 `用 deepseek-chat 测试 · 10:32`，右侧按钮"测试"。
   - 测试＝用"测试模型"发一条最小流式请求，走真实对话同一条链路。**不新造 ping**：多一条链路就多一处与真实调用不一致。
   - 失败按 `AiFailureKind` 分到"哪一步"，并指出"去改哪一行"。
   - 结果随平台持久化；接口地址/协议/API Key 任一变更后标"配置已改，需重新测试"。
   - **不选** Chatbox 的三项能力测试：耳机场景只用文本与语音，视觉/工具测试多花钱，结论也无处使用。

### 3.4 API Key 行

1. **职责**：录入、遮挡、替换密钥。
2. **回答**："填了没有？填的是哪一把？"
3. **别人**：GPTMobile 已填时显示 "Key set"（`res/values/strings.xml` api_key_set）；Jan 支持多把并逐把检测；Cherry Studio 支持多把。
4. **决定**：单把；显示掩码 `sk-…a1b2`（前 3 后 4）；未填显示"未填写"，并作为连接测试、拉取模型的前置条件。
   **不做多把轮询**（开放问题 Q12）。

### 3.5 高级（平台页 ④，折叠）

1. **职责**：放"改坏了会让整套连接失效、但多数人不需要碰"的配置。
2. **回答**："请求到底发到哪？用的哪种协议？参数从哪来？"
3. **别人**：Chatbox 在 API Host 下给 Preview 显示完整请求地址，并说明结尾 `/`、`#` 的含义（`routes/settings/provider/$providerId.tsx`）✅；
   ChatterUI 提示"注意：使用完整 URL 路径"（`i18n/locales/zh.json` connections.add.fullUrlHint）；
   RikkaHub 把高级做成独立页签（`SettingProviderDetailPage.kt:152-160`），看高级时看不到连接状态 ❌。
4. **决定**：同页折叠卡。折叠时一行摘要（协议 · 主机名 · 参数来源概况）；展开后：接口地址（下方实时预览最终请求地址）、
   协议（模板只支持一种时只读）、参数来源（本平台各模型生效来源汇总，可进入单个模型的参数）。
   自定义平台地址为空时默认展开并标"必填"。**不用页签**：手机上一页滚完更快，连接状态始终在上方可见。

### 3.6 模型列表（平台页 ③）

1. **职责**：本平台有哪些模型、哪个是当前、各自的事实（首字、上下文）。
2. **回答**："有哪些？现在用哪个？哪个快？能装多长的对话？"
3. **别人**：LobeChat 搜索结果计数"搜索到 {{count}} 个模型"、空态"暂无可用模型 / 请创建自定义模型或拉取模型后开始使用吧"✅；
   Cherry Studio 拉取后标"失效"并可"清理失效模型"（`zh-cn.json` settings.models.manage.*）✅；
   big-AGI 提示上下文与最大输出（`ModelsList.tsx:151-153`）；Kelivo/RikkaHub "输入模型名称筛选"。
4. **决定**：标题"模型 · N"；每行两行：名称 / 事实行（`首字 645 ms · 128K 上下文`，缺哪段省哪段）；
   当前模型行首有选中标记；超过 8 个出现搜索框；拉取后远端已不存在的标"远端已下线"（不自动删）。
   排序＝当前模型置顶，其余按添加顺序。**不按测速排序**：测完行会跳，容易点错。

### 3.7 添加模型（拉取 / 手动）

1. **职责**：把模型加进本平台。
2. **回答**："这个平台有哪些模型可加？我知道 id，能不能直接填？"
3. **别人**：LobeChat "获取模型列表 / 上次更新时间：{{time}}"、拉取去重提示 ✅；Jan 手动添加带"查看 {{provider}} 的模型列表"外链
   与"模型已存在"校验（`locales/zh-CN/providers.json` addModel.*）✅；Cherry Studio 手动添加字段多达 9 个 ❌（手机过重）；
   Jan 拉取结果用 toast ❌。
4. **决定**：同一入口"添加模型"分两条路：
   - **拉取列表**：请求平台 `/models`，在面板里多选（已加的显示"已添加"、不可再选），确认后加入；失败原因写在面板内。
   - **手动填写**：模型 id（必填，去首尾空格，重复即拦）、显示名（选填）、上下文长度（选填）。
   **不做**分组与能力勾选：我们只用对话与语音两类，能力由协议与模型类型决定。

### 3.8 平台页测速（只测首字）

1. **职责**：对一个或多个模型测"多久开始出字"。
2. **回答**："换哪个模型等得最短？"
3. **别人**：Cherry Studio 批量检测可选并发与超时（默认 15 s，`ModelCheckDialog.tsx:238,448`），开始前提示费用、跳过图像/音频模型
   （`zh-cn.json` settings.models.check.disclaimer）✅；行内"检测中…/通过/失败"✅；Kelivo 检测后"删除不可用"（`:6960`）✅；
   以上都不持久 ❌。
4. **决定**：只测首字（探针回答很短，没有生成窗口，算 tok/s 没有意义）；单条在行菜单，批量在区块头"全部测速"或多选后"测速"；
   进行中行内"测速中…"、区块头"测速中 3/12 · 取消"；完成后结果写进行并持久化，区块头一句结论"最快 deepseek-chat · 645 ms"。
   非对话模型跳过并在行内写"不测速"。**不用 toast。**

### 3.9 对话页速度（真实对话采样）

1. **职责**：用真实对话自身的时间戳与 usage 说明这一轮有多快，不另发请求。
2. **回答**："它现在出字快不快？刚才那条答得多快？"
3. **别人**：LobeChat 口径（TTFT 从发送算、tok/s 从首个 token 算）✅；Cherry Studio "首 Token 耗时 / {{value}} Token/秒 / 等待"
   （`zh-cn.json` chat.message.token_details.*）✅；PocketPal 同一行给 `tokens/秒 · 毫秒 TTFT · 被中断/已截断`（`zh.json` bubble.*）✅。
4. **决定**：流式中在元信息位置显示**实时速度**（每 0.5 s 刷新）；结束后换成**本轮平均速度**＋首字＋输出 token。
   流式阶段平台不给 token 数，按增量片段估算并加"≈"，结束拿到 usage 后替换为准确值（开放问题 Q2）。

### 3.10 生成参数（全局 / 此模型 / 平台默认）

1. **职责**：决定每次请求带不带温度、Top P、最大输出，带多少。
2. **回答**："这个模型现在用什么参数？是谁定的？我改全局会不会冲掉单独调好的？"
3. **别人**：Open WebUI 对话 > 用户全局 > 模型（`backend/open_webui/utils/payload.py:94` 只在缺键时填模型值）；
   Page Assist 对 OpenAI 兼容模型是 模型 > 对话/全局（`src/models/index.ts:104-130`）；
   NextChat 改过即脱离全局、恢复需确认（`mask.tsx:84-94,222-246`）✅；PocketPal 标"继承/自定义"，给"重置为全局默认值/系统默认值"✅；
   LibreChat 控件旁写"（默认值：X）"（`SidePanel/Parameters/DynamicSlider.tsx:182-184`）✅；AnythingLLM 空值占位"提供商默认值"✅；
   Chatbot UI 的"工作区默认"在新对话里没生效（`components/chat/chat-hooks/use-chat-handler.tsx:163-172` 整段注释）❌。
4. **决定**：优先级 **此模型 > 全局（开启且作用范围含该模型）> 平台默认（不发送）**；全局默认关闭；
   "改过即固定"按**单个参数**生效（比 NextChat 整份更细）；每个值旁标来源；每个参数可"清除此模型的值"。
   **不选** Open WebUI 的"对话 > 全局 > 模型"：本需求明确要求模型单独设置后不被全局覆盖。

### 3.11 对话页页头

1. **职责**：说明"我在哪段对话、谁在答"，并放三个出口（历史、通话、更多）。
2. **回答**："这是哪段对话？现在是哪个模型在答？"
3. **别人**：Enchanted 在对话页直接放不可达横幅并给出口（`Enchanted/UI/iOS/ChatView_iOS.swift:21,47` + UnreachableAPIView）✅；
   GPTMobile、RikkaHub 都把模型选择放在对话页顶部。
4. **决定**：左"历史"；中间两行（对话标题 / `平台 · 模型 ▾`，点开模型面板）；右"通话""更多"。
   模型名只在页头出现一次，输入条不再重复。

### 3.12 助手消息与元信息行

1. **职责**：展示正文、思考过程、这一轮的事实（模型、首字、速度、token、结束原因）与操作。
2. **回答**："它答了什么？想了多久？答完整了吗？"
3. **别人**：PocketPal 把"被中断 / 已截断 — 可能是上下文已满"放进同一组元信息（`zh.json` bubble.interrupted/truncated）✅；
   LobeChat 思考过程"思考了 {{duration}}"（`chat.json` workflow.thoughtForDuration）。
4. **决定**：正文通栏不套卡；思考过程折叠块标"思考过程 · 12 s"；元信息一行
   `deepseek-chat · 首字 0.8 s · 41 tok/s · 1,234 tokens`；非正常结束在其下单独一行（已停止 / 已截断 / 被拦截 / 空回答）。

### 3.13 失败卡

1. **职责**：在出错的那条回答位置说明原因与出路。
2. **回答**："为什么没回答？我该做什么？"
3. **别人**：LobeChat 错误行内渲染在触发处（`Checker.tsx:189`）✅；Cherry Studio 连接失败用 toast 带重试 ❌。
4. **决定**：原因标题（按失败分类的一句话）＋可展开细节（HTTP 状态、平台原文）＋最多两个按钮
   （"重试"只给临时故障；"去设置"只给可修的配置错误）。

### 3.14 输入条

1. **职责**：输入、发送、停止。
2. **回答**："现在能不能发？不能是为什么？"
3. **别人**：Chatbox 在不能检查时直接写缺什么（`'API Key is required to check connection'`）✅。
4. **决定**：1–6 行自增高；发送与停止同一位置；不可发时占位文案写明原因（没接平台 / 没模型 / 没网络）。

### 3.15 历史面板

1. **职责**：找回、切换、整理过去的对话。
2. **回答**："我之前问过什么？在哪段里？"
3. **别人**：Open WebUI 今天/昨天/过去 7 天/过去 30 天（`src/lib/utils/index.ts:1342-1348`）；RikkaHub 今天/昨天/日期、置顶、
   "搜索聊天"（`ui/pages/chat/ChatDrawerVM.kt:182-198`）；LobeChat "搜索话题…""暂无搜索结果"，重命名说明"保持简短且易于识别"（`topic.json`）。
4. **决定**：底部面板，行密度与对话页一致；分组 今天/昨天/过去 7 天/过去 30 天/更早；搜索标题与正文；
   行＝标题＋时间 / 摘要；操作 重命名、删除；"清空全部"在列表外并二次确认。**不做置顶与收藏**（Q11）。

### 3.16 通话页

1. **职责**：免手对话，听、想、说三态清楚，随时可打断、静音、挂断。
2. **回答**："它在听吗？听到了什么？它在说什么？"
3. **别人**：Open WebUI 连接中/正在倾听/正在思考/说话时"点击以中断"，先听到声音、再静默 2 s 判定说完
   （`chat/MessageInput/CallOverlay.svelte:244,459-461`）✅；Enchanted 系统 TTS 缺音色时给出系统设置路径与按钮
   （`Enchanted/UI/Shared/Settings/SettingsView.swift:143-162`）✅。
4. **决定**：七个状态（接通中/聆听/思考/说话/静音/失败/结束），动效只在聆听/思考/说话；字幕显示最近一轮；
   底部三个控制（静音、音频输出、挂断）；失败只给一个主按钮。

### 3.17 用量

1. **职责**：告诉用户用了多少（次数、token、平台回报的费用）。
2. **回答**："我用了多少？花在哪个模型、哪件事上？"
3. **别人**：RikkaHub 统计页按模型、按来源（对话/压缩/记忆/OCR/建议/标题/翻译）、按日/月（`ui/pages/stats/StatsPage.kt`）✅；
   LibreChat 以服务端余额呈现（`com_nav_balance*`），不适用于自带 API Key 的客户端。
4. **决定**：设置页"用量"分组：时间范围（今天/近 7 天/近 30 天/全部）＋请求次数（含失败数）＋输入/输出 token＋费用（仅平台回报时出现）
   ＋按模型前 5。每条记录带来源（对话/通话/连接测试/测速），测试与测速的消耗也计入。

### 3.18 设置页整体

1. **职责**：一处看清"现在用什么"，并进入各项配置。
2. **回答**："我现在用的是哪个？说话方式、参数、用量各是什么？"
3. **别人**：GPTMobile 分节 Connection / Model and generation / Advanced generation（`PlatformSettingScreen.kt:232-320`），
   页面状态各有文案（Loading platform… / Couldn’t load platform / This platform may have been removed.）✅。
4. **决定**：顺序 当前模型 → 平台 → 对话 → 生成参数 → 说话方式 → 用量 → 隐私说明；改动即时保存，不设"保存"按钮
   （RikkaHub 手动保存＋"保存成功" toast，`SettingProviderDetailPage.kt:264` ❌）。

---

## 4. 反面清单（明确不做）

| 做法 | 出处 | 为什么不做 |
| --- | --- | --- |
| 用 toast 报连接/测速/拉取结果 | Open WebUI、SillyTavern、Jan、Cherry Studio | 结果关掉即失，且盖住内容 |
| 只请求 `/models` 当作"连接正常" | Open WebUI、Jan | 证明不了密钥能用该模型、能出字 |
| 检测结果只存内存 | Cherry Studio、Kelivo、LobeChat | 重进页面就不知道哪个快 |
| 平台页拆页签 | RikkaHub、Kelivo | 看模型时看不到连接是否正常 |
| 用假值判"未设置" | Page Assist `src/models/index.ts:124` | 温度 0 无法设置 |
| 能设不生效的"默认" | Chatbot UI `use-chat-handler.tsx:163-172` | 设置必须有可观察的效果与测试 |
| 手动保存＋"保存成功" | RikkaHub `SettingProviderDetailPage.kt:264` | 多一步，且状态靠 toast |

## 5. v4 B 档结论复核

| v4 条目 | v4 说法 | 本轮原文 | 结论 |
| --- | --- | --- | --- |
| Cherry Studio | 健康检查结果内联进模型列表 | `ModelCheckStatus.tsx` 行内状态＋Popover 明细 | **成立** |
| Chatbox | 连接测试是独立一步，结果留在页面 | 结果在 "Model Test Results" 弹窗（`$providerId.tsx:418-460`） | **不成立**：结果不留在页面 |
| Jan | 本地模型扫描＋状态灯 | 读到的是密钥逐把检测、行内分类结果 | 状态灯**未查证** |
| LibreChat | 端点分组，模型按端点归拢 | 参数集按端点×模型取 `paramSettings`（`Parameters/Panel.tsx:56-72`） | 部分成立 |
| AnythingLLM | 供应商卡片＋连通性提示 | 读到的是工作区/系统两级模型 | **未查证** |
| big-AGI | 能力矩阵（上下文/价格/模态） | 上下文与最大输出提示、价格可覆盖 | 部分成立 |
| SillyTavern | 连接/生成/界面分组，高级折叠 | 读到常驻连接状态与 Test Message | 折叠**未查证** |
| Msty / Chatwise | 侧栏模型树 / 最快可用提示 | 闭源 | **未查证，删除** |
