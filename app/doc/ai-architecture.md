# AI 页面架构设计

本文记录 AI 标签页（对话）与 AI 设置（多平台 API Key、拉取模型、测速选模型）的架构，按实际代码写成。
范围包括 `lib_ai` 新增的支持服务，以及 `app` 里的领域层、数据层、平台层、界面层和装配根。

## 1. 目标与约束

| 目标 | 落点 |
| --- | --- |
| 对话页：流式回复、思考过程、停止、重试、历史、Markdown 渲染 | `AiChatSession` + `ui/ai/chat` |
| 设置页：多平台各存一把 API Key，拉取模型，测速并选择模型 | `ui/ai/settings` + 平台相关用例 |
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
| 模型目录 | `YoModels` | 拉取模型列表。`YoModelListing` 描述一个平台"怎么列模型"（`OpenAiModelListing` 单页，`AnthropicModelListing` 游标翻页）；`YoModelKinds` 把模型 id 归类（对话、向量、图片、语音……），只留对话模型；平台不支持列表时明确报错而不是猜 |
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

每个页面都是同一个模式：**ViewModel 只出状态，Mapper 做取舍，视图只画**。

```
 领域状态 ──Mapper──▶ UiState(record) ──LiveData──▶ 页面 ──▶ 视图
                         ▲                           │
                         └──────── ViewModel ◀───────┘ 用户操作
```

| 页面 | ViewModel | 状态 / 映射 | 视图 |
| --- | --- | --- | --- |
| 对话 | `AiChatViewModel` | `AiChatUiState`、`AiMessageUi`、`AiChatUiMapper`、`AiModelSheetMapper` | `AiChatPage`、`AiMessageAdapter`、`AiAssistantMessageView`、`AiUserMessageView`、`AiComposerView`、`AiWelcomeView`、`AiHistorySheet`、`AiModelSheet` |
| 设置总览 | `AiSettingsViewModel` | `AiSettingsUiState`、`AiSettingsUiMapper` | `AiSettingsActivity`、`AiAddPlatformDialog` |
| 平台详情 | `AiPlatformViewModel` | `AiPlatformUiState`、`AiPlatformLocal`、`AiPlatformUiMapper` | `AiPlatformActivity` |

### UI 基础设施

界面层不直接散落窗口级样板，统一收在下面几个位置：

| 位置 | 职责 |
| --- | --- |
| `ui/common/YoUi` | edge-to-edge；顶部状态栏留白的唯一入口（`YoPage` 页面不再补） |
| `ui/common/YoComponents` | 页面级 YoUI 组件释放集合：逆序、幂等，Activity 销毁时统一 `close()` |
| `ui/common/YoPageActivity` | 以 `YoPage` 为窗口根的 Activity 基类，建页/销毁/edge-to-edge 只做一次 |
| `ui/ai/AiTheme` | AI 界面的颜色、字号、间距、圆角与文本/图标/背景工厂；颜色取自 `YoColors` 快照 |
| `ui/ai/AiKeyboard` | 键盘收起，对话框与输入区共用 |
| `ui/ai/AiDialogs` | 确认、动作列表、单行/多行输入、滑块；统一 `YoDialog.Builder.show()`，关闭即销毁；多行编辑用 `YoTextInput` | 
| `ui/ai/settings/AiSectionList` | 设置总览/平台详情的一块：标题/说明在列表外，中间一张独立 `YoList`；行 Ripple 走独立列表几何 | 
| `ui/ai/chat/AiListSheet` | 历史/模型面板共用的 `YoSheet` + `YoList` 骨架，行内组件随刷新回收 |

规则：页面创建的 YoUI 组件（菜单、面板、按钮、输入框、滑块）必须登记到 `YoComponents`；
已经用 `YoPage` 的页面不再补顶部 inset；左右/底部由 `YoTabs`、`YoPage` 负责，页面不得重复补；
除 `com.yohu.youi.api.*` 外不得引用库内部包。

要点：

- **Markdown**：`MarkdownParser` 是纯 Java，输出 `MdBlock` 值类型；对任意前缀都稳定，所以流式输出到一半也能解析。
  `MarkdownView` 按位置比较前后两次的块，没变的不碰、同类型的原地更新、类型变了才换视图，长回答边生成边显示不闪烁。
  链接由 `LinkTextView` 自己判断点击（不抢走父级的长按），并且只放行 `http`/`https`/`mailto`（`MdLinks`）。
- **对话列表**：`stackFromEnd` 让最新内容贴底；在底部时自动跟随，向上翻看时不抢滚动位置，并出现"回到底部"按钮。
- **输入区底部留白**：悬浮标签栏高度（`YoTabs.setOnContentInsetListener`）和输入法高度（`WindowInsets`）取较大者。
- **设置类页面**：`AiSectionList` 包一张独立 `YoList`，标题/说明在列表外面；列表项由 `ListPrefixSpec` /
  `ListTextSpec` / `ListSuffixSpec` 描述，每次状态变化按新状态重填。
  测速时每个模型行的"排队 / 测试中 / 首字延迟 / 失败 / 超时"由对应的列表项显示。
- **输入校验**：规则在领域策略里，ViewModel 同步调用并把 `AiValidationError` 返回给对话框，
  对话框保持打开并把错误显示在输入框上，内容不丢；通过后才异步写入。
- **一次性事件**（提示、打开页面、关闭页面）走 `OneShotLiveData`，重建后不重放。

### 平台详情页的状态

持久的设置来自仓库，临时的（搜索词、排序方式、拉取进度、测速进度）由 `AiPlatformViewModel` 持有，
两块合并成界面状态。所有可变状态都在同一把锁下修改并在锁内发布，保证后发布的永远是更新的；
读写磁盘、访问网络的用例一律在锁外执行。

- **自动拉取**：配置了密钥却从没拉取过模型时（打开页面、保存密钥、换了服务地址），自动拉取一次，同时验证密钥是否可用；
  失败后不会反复重试，用户点"重试"。
- **测速**：对当前列表（已按搜索词过滤、按排序方式排好）的前 24 个模型测速；每个结果一到就写回配置，
  所以中途离开页面已测完的数据也在；离开页面时取消未完成的测速。停止在拿到网关句柄之前发出也能生效。
- **使用最快**：测速完成后，如果最快的模型不是当前模型，出现提示条，一键切换。

## 7. 装配与线程

`AiGraph.get(context)` 是应用级单例，持有：

| 执行器 | 线程 | 用途 |
| --- | --- | --- |
| `ai-io` | 4 | ViewModel 的读写、拉取模型 |
| `ai-chat` | 1 | `AiChatSession` 的串行执行器 |
| `ai-callbacks` | 1 | lib_ai 的回调。单线程保证同一次调用的增量按顺序送达 |

三种 ViewModel 都由同一个 `ViewModelProvider.Factory` 创建；平台详情页从启动参数 `AiGraph.ARG_PLATFORM_ID` 取平台标识。
`AiSettingsFeed` 把"启动时读一次"和"之后的变化通知"合成一条不会倒退的流：读盘慢于通知时，读到的旧值会被丢掉。

## 8. 安全

- 密钥只经 `EncryptedSecretStore` 落盘；`AiPlatformConfig.toString()` 打印 `apiKey=redacted`；
  `LibAiFailures.redact` 在错误文字里遮掉密钥和 `sk-…` 形状的串。
- 服务地址只接受 https（输入没写协议头时补 `https://`），密钥随每个请求发出，明文传输等于泄露。
- 密钥输入框用密码类型（带显示开关）；列表里只显示头尾（`sk-••••••••3xyz`）。
- 链接只放行网页和邮件；密文不进备份。

## 9. 测试

| 范围 | 内容 |
| --- | --- |
| `lib_ai`（63 个） | 目录与归类、翻页、补全三协议的事件拆分、延迟探测、错误分类、地址整理、传输取消标记 |
| `app` 领域（策略、用例、会话） | 输入规则、各用例的边界、`AiChatSession` 的状态机时序（停止后的迟到回调、恢复被中断的消息等） |
| `app` 数据 | JSON 往返和容错、仓库的原子更新与失败时不通知、对话索引重建、网关（MockWebServer 做端到端） |
| `app` 界面（ViewModel、Mapper、Markdown 解析） | 对话、设置、平台详情三个 ViewModel，包括拉取、测速、停止、清理、字符串资源格式 |

测速和拉取相关的时序用 `QueueExecutor`（任务先排队、由测试决定何时执行）演练，例如"后台任务还没跑起来用户就点了停止"。

## 10. 真机验证与 YoUI 使用注意

在 moto edge 60 pro（Android 15，应用目标 SDK 37）上跑过：首次进入的引导、设置总览、平台详情、拉取模型、测速、输入校验、
对话列表（Markdown、思考块、异常说明）、历史面板、模型面板、长按菜单、输入法弹出。接口用的是一把假密钥，
所以真实网络链路走到了平台并拿到了 401，对应的失败提示、拉取失败条、测速"失败"状态都已看过；
正常返回的流式回复由单元测试和 MockWebServer 覆盖，没有用真实密钥验证。

这几条是踩出来的，改界面时要留意：

| 现象 | 原因与做法 |
| --- | --- |
| 对话框、面板里按返回键，整个页面一起关掉 | YoUI 靠 `OnBackInvokedCallback` 接管返回键，而 Android 15 及以下默认不启用它，所以清单必须显式写 `android:enableOnBackInvokedCallback="true"`，已写在 `AndroidManifest.xml` |
| 在面板里弹出的对话框、菜单看不见 | 它们被面板盖在下面。要做这类操作，先收起面板再弹，用底部动作列表 `AiDialogs.choices` 代替锚定菜单 |
| 输入类对话框关闭后键盘还留着 | 对话框关闭时由 `AiKeyboard.hide(context)` 主动收起 |
| 多个输入框的对话框被键盘盖住 | 这类对话框顶部对齐（`Alignment.TOP`） |
| 给 `ImageView` 着半透明色（`text2`、`text3`）结果是黑色 | `setColorFilter(color)` 默认 `SRC_ATOP`，半透明色盖在黑色图标上还是黑的；统一用 `SRC_IN` |
| `FrameLayout` 里放 `MATCH_PARENT` 的空 `View` 把父容器撑满 | 空 `View` 在 `AT_MOST` 下取满可用高度；输入区的"不可用遮罩"因此改成不可聚焦加点击回调 |
| 面板内容隐约透出后面的页面 | 面板默认是模糊背景，改用 `BackdropMode.SOLID` 加不透明的表面色 |
| 字符串资源 `" · "` 首尾的空格丢失 | 资源里首尾空白会被去掉，要用引号包起来 |

## 11. 已知限制

- `max_completion_tokens` 是 Chat 协议编码里发送的字段名，DeepSeek 可能忽略"最大输出长度"设置。
- 对话里不支持图片等多模态输入；`AiModel` 记录了视觉能力，只用于标签显示。
- 不做对话内容的全文搜索；历史按时间排序。
- 深色模式只检查了代码路径（颜色全部来自 YoUI 令牌），没有在设备上切换确认。
- `lint` 里有一条与本功能无关的既有错误：`lib_common` 的下载通知缺少 `POST_NOTIFICATIONS` 权限声明。