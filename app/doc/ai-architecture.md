# AI 页面架构设计

本文记录 AI 标签页（对话）与 AI 设置（多平台 API Key、拉取模型、测速选模型）的架构，按实际代码写成。
范围包括 `lib_ai` 新增的支持服务，以及 `app` 里的领域层、数据层、平台层、界面层和装配根。

## 1. 目标与约束

| 目标 | 落点 |
| --- | --- |
| 对话页：流式回复、思考过程、停止、重试、历史、Markdown 渲染 | `AiChatSession` + `ui/ai/chat` |
| AI 页签：对话本身（流式回复、思考过程、停止、重试、Markdown 渲染） | `AiChatSession` + `ui/ai/chat` |
| `lib_ai` 提供"支持服务"，而不是把功能耦合进某个调用 | `catalog` / `models` / `completion` / `probe` / `error` 五组服务，全部建立在三条协议的公共端口之上 |
| 每个功能细节都先有架构再实现，并且可测 | 依赖只朝一个方向，层边界由 Gradle 任务强制 |

约束：

- 界面全部用 YoUI（AAR）和原生视图搭建，不引入新的 UI 框架。
- API Key 只存本机，经 Android Keystore 加密，不进日志、不进备份、不进界面文字。
- 领域层是纯 Java，不认识 Android，也不认识 `lib_ai`。

## 2. 分层

```
                 ┌────────────────────────────────────────────┐
  ui/ai          │ 视图：Page / Activity / Adapter / 自定义视图   │  依赖 ↓
                 │ ViewModel + UiState + UiMapper（无 Android）   │
                 └──────────────┬─────────────────────────────┘
  domain/ai      │ model · port · policy · usecase · chat/AiChatSession │  纯 Java，只依赖自己
                 └──────────────▲─────────────────────────────┘
                                │ 实现端口
  data/ai        │ store · json · settings · conversation · gateway     │  依赖 lib_ai、org.json
  platform/ai    │ KeystoreSecretCipher（唯一依赖 Android 的实现）       │
                 └──────────────▲─────────────────────────────┘
  wiring         │ AiGraph：应用级单例，造出全部对象并按需造 ViewModel   │
                 └────────────────────────────────────────────┘
  lib_ai         │ 协议（Chat / Messages / Responses）+ 支持服务         │
```

边界规则（`app/build.gradle` 的 `checkAiLayerImports`，挂在 `preBuild` 上，违反即构建失败）：

| 层 | 禁止出现 |
| --- | --- |
| `domain/ai` | `com.yovo.lib_ai`、`org.json`、`platform`、`wiring`（原有的 `checkShellLayerImports` 另外禁止 `android.`） |
| `data/ai` | `platform`、`wiring` |
| `ui/ai` | `com.yovo.lib_ai`、`org.json`、`platform`，以及仓库、网关、目录这几个端口接口 |
| `ui/ai/**/*ViewModel.java` | `android.view/widget/content/app`、`com.yohu.youi`、`wiring` |
| `platform` | `ui`、`com.yohu.youi`、`wiring` |

## 3. lib_ai 支持服务

原有的三条协议端口（`YoChatCompletions`、`YoAnthropicMessages`、`YoResponses`）不动。
新增的服务都建立在这些公共端口之上，各平台的报文差异只留在平台类里：

| 服务 | 入口 | 职责 |
| --- | --- | --- |
| 平台目录 | `YoPlatformCatalog.standard()` | 内置平台（OpenAI、Anthropic、DeepSeek、SenseNova）和两类自定义兼容平台的描述：默认地址、支持的协议、密钥提示、控制台地址；统一的地址整理规则 `normalizeBaseUrl` |
| 模型目录 | `YoModels` | 拉取模型列表。`YoModelListing` 描述一个平台"怎么列模型"（`OpenAiModelListing` 单页，`AnthropicModelListing` 游标翻页）；每一项带一份 `YoModelSpec` 规格——平台在列表里报的类别、模态、上下文与输出长度、函数调用等能力、推理方式逐项记为上报，没报的不猜；平台不支持列表时明确报错而不是猜 |
| 模型规格 | `YoModelSpec` / `YoModelCatalog` | 模型的完整规格：类别（文本、视觉、全模态、语音识别、语音合成、向量、实时……）、输入输出模态、限额（上下文、输入、输出、思维链……）、服务端能力（函数调用、结构化输出、联网搜索、前缀续写、上下文缓存、批量推理）、推理方式；每条说法带出处（推断 / 平台上报 / 用户），详见 `ai-model-spec.md` |
| 能力路由 | `YoModelRequirement` / `YoCapabilityRouter` | 调用方说"要什么样的模型"（类别 + 附加条件），路由选服务、选模型、拼端点，并告诉你这个选择是确认过的还是只是推断；选不出时抛 `UNSUPPORTED_CAPABILITY`，不是一个语焉不详的 400 |
| 统一补全 | `YoCompletions` | 一次请求、一种事件流，背后按协议适配（`Chat/Messages/ResponsesCompletionAdapter`）。思考内容从原始 JSON 里拆出来：`reasoning_content`、`reasoning`、`thinking_delta`、`response.reasoning_summary_text.delta` |
| 延迟探测 | `YoLatencyProbe` | 对一批模型并发发一句极短的话，记录首字延迟和总耗时。走**不重试**的传输，结果才反映真实延迟 |
| 错误分类 | `YoAiErrors.kindOf` | 把 HTTP 状态、传输异常、协议异常折成 `YoAiErrorKind`（鉴权、权限、额度、限流、未找到、参数、服务端、网络、超时、取消、协议……） |

配套改动：`YoAiClient` 增加 `models()/completions()/latencyProbe()` 访问器和超时设置；
`YoAiTransportException` 增加 `cancelled()` 标记，区分"用户取消"和"网络失败"；
Responses 协议里助手历史消息改用 `output_text` 编码。

## 4. 领域层 `domain/ai`

- **model**：`AiPlatformConfig`（连接信息 + 模型缓存 + 测速结果）、`AiSettings`（平台列表 + 当前选择 + 对话参数）、
  `AiConversation`/`AiMessage`（含 `STREAMING`/`COMPLETE`/`STOPPED`/`ERROR` 状态）、`AiFailure`/`AiFailureKind`、
  `AiValidationError`。全部是不可变 record，`toString` 不含密钥。
- **port**：`AiSettingsRepository`、`AiConversationRepository`、`AiPlatformCatalog`、`AiModelGateway`、
  `AiSpeedTestGateway`、`AiChatGateway`、`AiClock`、`AiIdGenerator`。领域只说端口，数据层实现。
- **policy**：输入规则和判断，都是无状态纯函数——`AiApiKeyPolicy`（只收可见 ASCII，自动去引号和 `Bearer`）、
  `AiBaseUrlPolicy`（只收 https）、`AiModelIdPolicy`、`AiSpeedRanking`（首字快的在前）、`AiContextPolicy`（按条数裁剪上下文）、
  `AiTitlePolicy`、`AiMessageRecovery`（把上次被杀时停在 `STREAMING` 的消息改成已停止）。
- **usecase**：`SavePlatform`、`FetchPlatformModels`、`AddManualModel`、`RemoveModel`、`RemovePlatform`、`SelectModel`、
  `RunSpeedTest`、`SaveChatPreferences`、`ListConversations`、`Load/ObserveAiSettings`、`LoadPlatformTemplates`。
  需要"读-改-写"的都在仓库的原子 `update` 里做，所以界面连续改两个参数、测速回调和用户编辑同时到达都不会互相覆盖。
- **chat/AiChatSession**：应用级的对话状态机。所有命令投递到同一个串行执行器，状态只在那里修改；
  网关回调带令牌，过期的回调（已停止、已切换对话）直接忽略；每次变化发布一个不可变的 `AiChatState`。
  因为它不依赖界面，旋转、切后台、关页面都不会中断生成。

## 5. 数据层 `data/ai` 与平台层

- **store**：`FileAiDocumentStore`（临时文件 + 原子改名写入，名字校验防路径穿越）、
  `EncryptedSecretStore`（密钥以 `apikey.<platformId>` 为别名，放在 `secrets.json`）、`SecretCipher` 端口。
  `StorageGuard` 把存储层的 `AiStorageException` 统一折成领域的 `AiFailure(STORAGE)`，
  所以"磁盘满了""密钥库坏了"表现为一句提示，而不是线程崩溃。
- **json**：`AiSettingsJson`、`AiConversationJson`，用 `org.json` 编解码，容忍缺字段和未知字段；设置文档里没有密钥。
- **settings / conversation**：`AiSettingsRepositoryImpl` 内存一份快照，锁内读-改-写再整份落盘，通知在锁外；
  `AiConversationRepositoryImpl` 一段对话一个文档加一份索引，索引损坏时扫描文档重建，所以索引只是缓存。
- **gateway**：`LibAiClientPool` 按（平台、模板、地址、密钥摘要）缓存 `YoAiClient` 并以租约借出，
  有人在用就绝不关闭；`LibAiModelGateway`、`LibAiSpeedTestGateway`（并发 3、单个 25 秒超时）、`LibAiChatGateway`
  把 lib_ai 的事件折成领域端口的回调；`LibAiFailures` 负责错误分类和脱敏（密钥以及形似 `sk-…` 的串都会被遮掉）。
- **platform/ai**：`KeystoreSecretCipher`，AndroidKeyStore 里的 AES-256-GCM，输出 `Base64(IV ‖ 密文 ‖ 标签)`。

文件位置：设置和对话在 `filesDir/ai`；密文在 `noBackupFilesDir/ai`，不参与云备份——它只有在这台设备的密钥库里才解得开。

## 6. 界面层 `ui/ai`

AI 的界面只剩对话页，挂在 AI 页签里——应用一共三个主页：**设备、AI、我的**。

每个页面都是同一个模式：**ViewModel 只出状态，Mapper 做取舍，视图只画**。

```
 领域状态 ──Mapper──▶ UiState(record) ──LiveData──▶ 页面 ──▶ 视图
                         ▲                           │
                         └──────── ViewModel ◀───────┘ 用户操作
```

| 页面 | ViewModel | 状态 / 映射 | 视图 |
| --- | --- | --- | --- |
| 对话 | `AiChatViewModel` | `AiChatUiState`、`AiMessageUi`、`AiChatUiMapper` | `AiChatPage`、`AiMessageAdapter`、`AiAssistantMessageView`、`AiUserMessageView`、`AiComposerView`、`AiWelcomeView` |

界面上的配置入口全部删除：AI 设置页、平台详情页（API Key 配置）、历史面板、模型面板、自定义平台对话框都不再有，
`ui/ai/settings` 包里一个界面类都不剩，`AiDialogs` 只剩"确认删除"。

也就是说界面上没有地方能改 API Key、换模型或清空历史。这些能力留在领域层和数据层：
`SavePlatform`、`FetchPlatformModels`、`AddManualModel`、`RemoveModel`、`RemovePlatform`、`SelectModel`、
`RunSpeedTest`、`SaveChatPreferences` 等用例，以及带 Keystore 加密的仓库都还在，只是当前没有入口调用。
对话偏好（2026-10-29 起）只剩系统提示词与"是否显示思考过程"；温度、top_p、最大输出、上下文条数
不再进设置文档，由 `AiGenerationDefaults` 在发请求时给定。

### UI 基础设施

界面层不直接散落窗口级样板，统一收在下面几个位置：

| 位置 | 职责 |
| --- | --- |
| `ui/common/YoUi` | edge-to-edge；顶部状态栏留白的唯一入口（`YoPage` 页面不再补） |
| `ui/common/YoComponents` | 页面级 YoUI 组件释放集合：逆序、幂等，Activity 销毁时统一 `close()` |
| `ui/common/YoPageActivity` | 以 `YoPage` 为窗口根的 Activity 基类，建页/销毁/edge-to-edge 只做一次 |
| `ui/ai/AiTheme` | AI 界面的颜色、字号、间距、圆角与文本/图标/背景工厂；颜色取自 `YoColors` 快照 |
| `ui/ai/AiKeyboard` | 键盘收起，输入区与对话框共用 |
| `ui/ai/AiDialogs` | 只保留确认框；统一 `YoDialog.Builder.show()`，关闭即销毁 |

规则：页面创建的 YoUI 组件（菜单、按钮、输入框）必须登记到 `YoComponents`；
已经用 `YoPage` 的页面不再补顶部 inset；左右/底部由 `YoTabs`、`YoPage` 负责，页面不得重复补；
除 `com.yohu.youi.api.*` 外不得引用库内部包。

要点：

- **Markdown**：`MarkdownParser` 是纯 Java，输出 `MdBlock` 值类型；对任意前缀都稳定，所以流式输出到一半也能解析。
  `MarkdownView` 按位置比较前后两次的块，没变的不碰、同类型的原地更新、类型变了才换视图，长回答边生成边显示不闪烁。
  链接由 `LinkTextView` 自己判断点击（不抢走父级的长按），并且只放行 `http`/`https`/`mailto`（`MdLinks`）。
- **对话列表**：`stackFromEnd` 让最新内容贴底；在底部时自动跟随，向上翻看时不抢滚动位置，并出现"回到底部"按钮。
- **输入区底部留白**：悬浮标签栏高度（`YoTabs.setOnContentInsetListener`）和输入法高度（`WindowInsets`）取较大者。
- **模型标签**：输入区左下角只显示"平台 · 模型"，纯展示、不可点——没有选择面板可开。
- **一次性事件**（提示）走 `OneShotLiveData`，重建后不重放。

## 7. 装配与线程

`AiGraph.get(context)` 是应用级单例，只装配对话要用的东西：文档存储、带 Keystore 加密的密钥存储、设置仓库、
对话仓库、`LibAiChatGateway`、`AiChatSession`，以及对话页的 `AiChatViewModel`。

| 执行器 | 线程 | 用途 |
| --- | --- | --- |
| `ai-chat` | 1 | `AiChatSession` 的串行执行器 |
| `ai-callbacks` | 1 | lib_ai 的回调。单线程保证同一次调用的增量按顺序送达 |

配置界面撤掉之后，`AiGraph` 不再装配平台目录、输入策略和各平台用例；那些类仍留在领域层与数据层。

## 8. 安全

- 密钥只经 `EncryptedSecretStore` 落盘；`AiPlatformConfig.toString()` 打印 `apiKey=redacted`；
  `LibAiFailures.redact` 在错误文字里遮掉密钥和 `sk-…` 形状的串。
- 服务地址只接受 https（`AiBaseUrlPolicy`；输入没写协议头时补 `https://`），密钥随每个请求发出，明文传输等于泄露。
- 界面上不再有密钥输入框，也没有显示半掩码的列表；密钥只经上面那条加密链路落盘。
- 链接只放行网页和邮件；密文不进备份。

## 9. 测试

| 范围 | 内容 |
| --- | --- |
| `lib_ai` | 模型规格（类别、模态、限额、能力、推理，出处覆盖与现算类别）、推断词表、平台上报的各种写法、要求匹配与路由、翻页、补全三协议的事件拆分、延迟探测、错误分类、地址整理、传输取消标记 |
| `app` 领域（策略、用例、会话） | 输入规则、各用例的边界、`AiChatSession` 的状态机时序（停止后的迟到回调、恢复被中断的消息等） |
| `app` 数据 | JSON 往返和容错、仓库的原子更新与失败时不通知、对话索引重建、网关（MockWebServer 做端到端） |
| `app` 界面（ViewModel、Mapper、Markdown 解析） | 对话 ViewModel 与 Mapper、Markdown 解析、字符串资源格式 |

测速和拉取相关的时序用 `QueueExecutor`（任务先排队、由测试决定何时执行）演练，例如"后台任务还没跑起来用户就点了停止"。

## 10. 真机验证与 YoUI 使用注意

在 moto edge 60 pro（Android 15，应用目标 SDK 37）上跑过：三个主页签、首次进入的引导、
对话列表（Markdown、思考块、异常说明）、长按菜单、输入法弹出；那时还带着配置界面（平台详情、拉取模型、测速、输入校验）。
接口用的是一把假密钥，所以真实网络链路走到了平台并拿到了 401，对应的失败提示、拉取失败条、测速"失败"状态都已看过；
正常返回的流式回复由单元测试和 MockWebServer 覆盖，没有用真实密钥验证。

配置界面（AI 设置页、平台页）撤掉之后重新在真机上跑过：三个主页签切换、AI 页签的引导卡片与输入区、长按菜单与确认框都正常，且不再有任何进入配置的入口。

这几条是踩出来的，改界面时要留意：

| 现象 | 原因与做法 |
| --- | --- |
| 对话框、面板里按返回键，整个页面一起关掉 | YoUI 靠 `OnBackInvokedCallback` 接管返回键，而 Android 15 及以下默认不启用它，所以清单必须显式写 `android:enableOnBackInvokedCallback="true"`，已写在 `AndroidManifest.xml` |
| 输入类对话框关闭后键盘还留着 | 对话框关闭时由 `AiKeyboard.hide(context)` 主动收起 |
| 多个输入框的对话框被键盘盖住 | 这类对话框顶部对齐（`Alignment.TOP`） |
| 给 `ImageView` 着半透明色（`text2`、`text3`）结果是黑色 | `setColorFilter(color)` 默认 `SRC_ATOP`，半透明色盖在黑色图标上还是黑的；统一用 `SRC_IN` |
| `FrameLayout` 里放 `MATCH_PARENT` 的空 `View` 把父容器撑满 | 空 `View` 在 `AT_MOST` 下取满可用高度；输入区的"不可用遮罩"因此改成不可聚焦加点击回调 |
| 字符串资源 `" · "` 首尾的空格丢失 | 资源里首尾空白会被去掉，要用引号包起来 |

## 11. 已知限制

- `max_completion_tokens` 是 Chat 协议编码里发送的字段名，DeepSeek 可能忽略"最大输出长度"设置。
- 对话里不支持图片等多模态输入；`AiModel` 记录了视觉能力，只用于标签显示。
- 不做对话内容的全文搜索；历史按时间排序。
- 深色模式只检查了代码路径（颜色全部来自 YoUI 令牌），没有在设备上切换确认。
- `lint` 里有一条与本功能无关的既有错误：`lib_common` 的下载通知缺少 `POST_NOTIFICATIONS` 权限声明。
