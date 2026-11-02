# APP 架构 v5：全量重构设计

> 状态：已落地的严格 Android MVVM 基线，2026-10-31。
> 范围：`app` 模块的进程装配、状态、线程、持久化、页面、YoUI 生命周期、AI 设置/对话/通话/历史，以及 shell 导航。
> 不在本次范围：`lib_ai` 的协议实现、`lib_bluetooth` 的设备协议、YoUI 内部实现。
>
> 本文描述当前源码已落地的分层与约束；未实现项不列入当前架构。

## 1. 一页结论

当前 APP 的问题不是“某个页面需要换控件”，而是五个系统性边界没有建立：

1. **持久化边界**：分段点击回调在主线程同步执行 JSON 编码、文件写入、`fsync`、改名。
2. **页面生命周期边界**：每次模式切换都销毁并重建 `YoList`/`YoSelect`，页面组件寿命短于页面本身。
3. **状态边界**：领域状态、操作状态、页面临时状态、一次性事件混在 ViewModel/Activity 中，存在多个真相来源。
4. **线程边界**：部分用例同步执行，部分由 ViewModel 手工投递到 executor，线程语义由调用点决定而不是由端口定义。
5. **模块边界**：单模块内的包边界虽有 Gradle 检查，但 feature 之间没有独立 API，装配根持续膨胀。

当前架构：

```text
View（Activity / Screen / Panel / Section / Dialog）
        │ 只渲染 UiState、只转发用户事件
        ▼
ViewModel
        │ 业务状态、状态迁移、事件与一次性副作用
        ▼
UseCase / Policy / Session
        │ 领域决策与串行状态机
        ▼
Repository / Gateway / Port 实现
        │ 持久化、网络、Keystore、音频
        ▼
Data / Platform
```

硬约束：

- 主线程不读磁盘、不写磁盘、不加密、不序列化大对象、不发网络。
- 一个聚合只有一个内存写者；所有写都经过同一事务入口。
- 页面创建的 YoUI 组件与页面同寿命；模式切换只改变可见性和绑定值。
- ViewModel 可以接收由装配根提供的工作执行器，但不得在内部创建线程池；业务决策优先下沉到 UseCase/Policy。
- 所有跨层通信使用不可变快照、类型化命令、类型化事件；不用可变共享对象。
- 领域层保持纯 Java，不依赖 Android、`lib_ai`、JSON 或 YoUI。

## 2. 当前问题证据

本次卡顿链路已在当前 APK 上复现：

| 问题 | 当前代码证据 | 实测 |
| --- | --- | --- |
| 分段回调同步落盘 | `AiSettingsActivity:168-169` → `AiSettingsViewModel:122-123` → `AiSettingsRepositoryImpl:66-85,161` → `FileAiDocumentStore:48-65` | 6 次切换中 `fsync` 与业务回调几乎完全重合，单次约 `9–27ms` |
| 每次切换全量重建 | `AiSettingsActivity:456-493` 每次 `clearRows()` 后重新 `newList`/`new YoSelect` | 语音方向 `render` 约 `24–91ms`；实时模型下拉单次约 `33–45ms` |
| 语音模式组件更重 | `AiSettingsActivity:252-398` 创建 1 个 `YoList` + 3 个 `YoSelect` | 当前版本文本→语音最长完成帧约 `163–218ms` |
| 平台页也全量重建 | `AiPlatformActivity:164-187` 每次清空 `column`、重建所有列表 | 同一模式在平台页、设置页重复出现 |
| 操作状态分散 | `AiPlatformViewModel` 同时持有 settings、testing、speedRunning、searchQuery、keyRevealed | 转屏/重进/并发操作时难以统一恢复 |
| LiveData 分裂 | `AiHistoryViewModel` 用 items/loaded/loadFailed 三条流；页面手工合成 | 中间态可能被观察到，页面需要多路合并 |
| 线程语义不统一 | 设置 VM 同步执行部分用例、部分手工 `io.execute`；平台 VM 又注入 executor | 是否阻塞主线程取决于调用点，而不是数据层契约 |

已证实的 SDK 次要成本：

- 分段滑块动画计划构建约 `10.96ms`，滑块自绘约 `1.5–2.3ms`。
- `YoList.addItem()` 每加一行都调用一次 `notifyDataChanged()`；`ListAdapter.setItems()` 在调用线程执行 `DiffUtil.calculateDiff()`。
- `YoList` 每次创建 `RecyclerView`、`ListChrome`、主题绑定；阴影槽使用软件层。
- `YoSelect` 每次创建 `MenuBuilder`、触发器、材质和控制器；逐项 `addOption` 都会 `render()`。

SDK 不是本次主因，但 APP 设计必须避免高频创建这些对象，也不能依赖 SDK 内部实现。

## 3. 当前模块结构

### 3.1 单模块内的严格 MVVM 分层

```text
com.ggec.earbuds
├── wiring             AppContainer / AppExecutors / AiGraph / ShellGraph
├── domain
│   ├── ai/model       不可变领域模型
│   ├── ai/port        仓库、网关、音频端口
│   ├── ai/usecase     领域用例
│   ├── ai/policy      纯规则
│   ├── ai/chat        AiChatSession 串行状态机
│   └── ai/voice       AiVoiceSession 契约
├── data
│   ├── ai/json        持久化编解码
│   ├── ai/store       文件与加密存储
│   ├── ai/settings    设置仓库
│   ├── ai/conversation 会话仓库
│   ├── ai/gateway     lib_ai 网关适配
│   └── ai/voice       实时语音会话实现
├── platform            Android / Keystore / 音频实现
└── ui
    ├── ai/settings     View + ViewModel + Mapper
    ├── ai/chat         View + ViewModel + Mapper
    ├── ai/call         View + ViewModel + Mapper
    ├── ai/history      View + ViewModel + Mapper
    └── shell           View + ViewModel + Mapper
```

### 3.2 文件职责

- `*Activity`：只做权限、生命周期、ViewModel 获取和跨页面导航。
- `*Screen` / `*Panel` / `*Section` / `*View`：只做布局、绑定 UiState、转发点击。
- `*ViewModel`：持有业务状态与决策，订阅用例/会话，发布单一 UiState 和一次性事件。
- `*UiMapper`：纯 Java 的状态映射，不碰 View、Context、存储。
- `*Dialogs` / `*Menus`：只负责对话框与菜单的展示和确认回调。
- `*Graph` / `AppContainer` / `AppExecutors`：只做装配与线程语义。

同一文件只承担一类职责；业务状态与决策统一归 ViewModel。

### 3.3 依赖规则

| 层 | 可以依赖 | 禁止依赖 |
| --- | --- | --- |
| `ui` | `domain`、`ui/common`、`wiring`（只由宿主取容器） | `data` 实现、具体 gateway、`lib_ai`、JSON、文件 |
| `domain` | Java 标准库、自身模型 | Android、JSON、`lib_ai`、文件系统、YoUI |
| `data` | `domain`、`lib_ai`、JSON、数据存储实现 | `ui`、YoUI、装配根 |
| `platform` | Android、Keystore、音频 API | `ui`、YoUI、装配根 |
| `wiring` | `domain`、`data`、`platform`、`ui` 的 ViewModel 构造 | 业务逻辑、页面状态 |

当前 Gradle 守卫：

- `checkAiLayerImports`：领域层不认识 `lib_ai`、JSON、平台或装配；数据层不依赖 UI 和装配；AI UI 不直连仓库、网关或 `lib_ai`；ViewModel 不持有 `Context` 或创建视图。
- `checkShellLayerImports`：UI 不直接依赖 `data`；ShellViewModel 不依赖 View/YoUI；领域与数据不反向依赖 UI。
- `checkMainThreadIoImports`：禁止 UI 层出现 `FileOutputStream`、`FileDescriptor`、`Files`、`org.json`、具体仓库实现。
- `checkComponentOwnership`：禁止页面裸调 `YoList`/`YoSelect`/`YoMenu`/`YoDialog.destroy()`，释放统一走 `YoComponents`。

## 4. 核心运行时

### 4.1 进程装配

`AppContainer` 是唯一应用级装配根，替代 `AiGraph` + `ShellGraph` 分散装配。

```java
public final class AppContainer {
    private final AppExecutors executors;
    private final AiGraph ai;
    private final ShellGraph shell;

    public AppExecutors executors();
    public AiGraph ai();
    public ShellGraph shell();
}
```

约束：

- `Application.onCreate()` 只注册进程级能力（刷新率、崩溃日志、StrictMode），不读设置、不初始化网络。
- `AppContainer` 在 `Application.onCreate()` 只创建一次；`AiGraph` 只创建应用级会话、仓库与线程。
- 所有单例都在 `AppContainer` 内，禁止业务代码再写静态 `getInstance()`。

### 4.2 线程设施

`AppExecutors` 定义线程语义，由 `AiGraph` 在创建 ViewModel 时选择并注入对应执行器：

| Executor | 线程数 | 用途 | 禁止 |
| --- | --- | ---: | --- |
| `settingsPersistence` | 1 | 设置快照串行落盘、密钥写入 | 网络、UI |
| `settingsOperation` | 2 | 拉模型、测速、平台操作 | UI、设置写盘 |
| `historyIo` | 1 | 会话列表读取 | 网络、UI |
| `chatState` | 1 | 对话状态机串行迁移 | 阻塞 I/O |
| `voiceState` | 1 | 通话状态机串行迁移 | UI |
| `realtimeFetch` | 1 | ChatGPT 实时模型列表拉取 | UI、文件 |
| `aiCallbacks` | 1 | lib_ai 回调按调用顺序送达 | UI、文件 |

UI 状态发布本身仍由 LiveData 在主线程分发；`AppExecutors` 只定义后台工作的归属。

规则：

- 每个状态机只有一个执行器，命令按到达顺序执行。
- 状态机内部不得同步等待网络或磁盘；等待都表现为状态和事件。
- 数据层公开方法标注 `@WorkerThread`；UI 公开方法标注 `@MainThread`。
- 当前 ViewModel 由装配根显式注入领域用例和专用 `Executor`；它们不在 ViewModel 内创建线程池。线程选择只属于装配根。

### 4.3 反应式原语

领域层使用纯 Java 的 `Subscription` + `Consumer`，不依赖 LiveData。每个页面只有一个主 `LiveData<UiState>`；一次性提示单独走 `OneShotLiveData<Event>`。不再用多条互相关联的 `LiveData` 由 View 手工合并。

## 5. Settings feature

### 5.1 聚合与持久化状态

`AiSettings` 是唯一不可变聚合根。磁盘状态由独立枚举表达：

```java
enum AiSettingsPersistenceStatus {
    SAVED,
    SAVING,
    FAILED
}
```

内存值与磁盘状态分离：内存修改立即发布；`SAVING`/`SAVED`/`FAILED` 由仓库观察端口回传。

### 5.2 单写者仓库

`AiSettingsRepositoryImpl` 是唯一内存写者：

1. 在短临界区读取当前值并执行纯变更。
2. 值未变则直接返回，不通知、不写盘。
3. 值变化则更新内存 revision，并立即通知观察者。
4. 发布 `SAVING`，把最新 revision 投递到 `settingsPersistence`。
5. 后台串行写密钥和文档；成功后发布 `SAVED`，失败发布 `FAILED`。

首次读取走 `loadAsync()`，共享同一次后台读取；`ready()` 只在读取完成后为 true。

### 5.3 合并写入

- 单线程持久化执行器，不占主线程。
- 同一时间只保留最新待写 revision；中间 revision 自动合并。
- 密钥变化先于文档写入。
- 文档继续使用临时文件、`fsync`、原子改名。
- 失败不回滚内存，保留用户可见值，并通过状态通知界面。

### 5.4 ViewModel 职责

设置页的 `AiSettingsViewModel` 直接编排领域用例，并持有页面操作状态（realtime fetching、持久化状态、模板列表）。这些状态通过单一 `AiSettingsUiState` 与一次性 `AiSettingsEvent` 发布。

所有写设置的动作仍通过领域用例：

```java
selectMode.execute(mode);
savePreferences.update(prefs -> prefs.withSystemPrompt(prompt));
updateChatGpt.baseUrl(baseUrl);
```

ViewModel 只负责把领域结果、异常分类和页面操作状态合成为 UiState；UI 不直接调用用例或仓库。

### 5.5 设置页组件寿命

页面固定由两个面板组成，创建一次：

```text
AiSettingsScreen
├── ModeSegmentBinder
├── ModeHintBinder
├── TextModePanel          // 一次创建，切换时只 setVisibility
│   ├── identity YoList
│   └── prompt YoList
└── VoiceModePanel         // 一次创建，切换时只 setVisibility
    ├── voice YoList       // 结构固定
    ├── vendor YoSelect
    ├── realtime YoSelect
    └── dialect YoSelect
```

规则：

- `TextModePanel` 与 `VoiceModePanel` 都在 `Screen.createView()` 中创建。
- `render(state)` 只决定 `VISIBLE/GONE`，不调用 `clearRows()`。
- 只有面板数据内容变化时才 `binder.bind(newPanelState)`。
- `YoList`/`YoSelect` 的 `destroy()` 只在 `YoComponentScope.close()` 中执行。
- 分段控件用户点击后，SDK 已更新选中态；状态回传只用于校验，相同索引不得再次触发动画。

### 5.6 面板 binder

当前面板没有额外的平行 `PanelBinder` 层，而是由 `AiTextModePanel`、`AiVoiceModePanel`、`AiVoiceRows`、`AiVoiceSelectBinder` 分别承担“布局组合、固定行规格、下拉同步”。每个面板记录上次绑定的内容签名；文本面板只比较身份与提示词，语音面板只比较厂商、地址、实时模型、方言、Key 状态和 fetching。模式变化不是内容签名的一部分。

### 5.7 动态 `YoSelect` 选项

`YoSelect` 实例长期保留。选择变化只调用 `setValue`/`setSelected`；只有选项集合真正变化时才更新选项：

```java
void bindOptions(YoSelect select, List<Option> options, String selectedId) {
    if (optionsHash.equals(lastOptionsHash)) {
        select.setSelectedItemId(selectedId);
        return;
    }
    select.clearOptions();
    for (Option option : options) {
        select.addOption(option.id(), option.label());
    }
    select.setSelectedItemId(selectedId);
    lastOptionsHash = optionsHash;
}
```

这是 SDK 当前公开 API 下的安全做法。若后续需要消除逐项 `render()`，由 SDK 增加批量 `submitOptions(...)`；APP 不依赖内部类或反射。

## 6. Platform editor feature

平台详情页也改为长寿命区块，而不是每次 `render` 清空 `column`：

```text
AiPlatformScreen
├── PlatformHeader
├── PlatformConnectionSection
├── PlatformModelsSection
├── PlatformAdvancedSection
└── PlatformDangerSection
```

`PlatformModelsSection` 的模型集合变化时只更新模型列表；连接结果、Key 显隐、高级展开只更新各自区块。不得因连接测试结果变化重建整个页面。

平台页状态合并为一个 `AiPlatformUiState`；`AiPlatformViewModel` 从设置聚合、测速状态、搜索词和展开态映射出它。

`testing`、`speedRunning`、`searchQuery`、`advancedExpanded` 都是 `AiPlatformUiState` 的投影字段；测速运行态封装在 `PlatformSpeedTestState`。测速、拉模型、手动添加、删模型和删平台都带代际校验：平台重开或删除后，旧任务的结果不得再发布。

命令级纯决策放在 `AiPlatformPolicy`（选中模型、可测模型、搜索词规范化）；`AiPlatformViewModel` 只持有代际、订阅和命令生命周期。

## 7. Chat feature

### 7.1 保持应用级会话

`AiChatSession` 已是正确的应用级状态机：单一串行执行器、命令排队、迟到回调带 token 过滤。保留这个核心，不改成页面级状态。

### 7.2 重命名与边界

- `AiChatSession` 是领域层串行状态机，负责编排网关、仓库和设置订阅。
- `ChatState` 继续不可变。
- 消息增量只发布到 `AiChatSession`，由 `AiChatViewModel` 映射后交给 UI。
- 会话持久化进入 `conversationIo`，使用“按会话最新快照合并”的写队列，避免每个 token 触发整份文档写盘。
- `AiChatViewModel` 只维护页面级 reasoning override 与草稿；UI mapper 不访问 repository。

### 7.3 发布策略

流式 delta 以帧合并发布：

```text
网关 token → chatState executor → AiChatSession 聚合
          → AiChatViewModel → 主线程 UiState
```

不是每个 token 一次 `postValue`。文本聚合和 Markdown 渲染仍在主线程，但发布频率受控。

## 8. Voice feature

### 8.1 生命周期

- `AiVoiceSession` 由通话页 ViewModel 持有；通话跨转屏保留。
- `AiVoiceSession` 的音频设备实例仍按通话创建/释放。
- 会话状态机、音频采集、音频播放各在自己的线程；ViewModel 只做快照到 UiState 的映射。
- 页面销毁不等于通话结束；只有 `hangUp()` 或 ViewModel `onCleared()` 才停止。

### 8.2 状态

语音页使用一个 `AiCallUiState`；`AiCallUiMapper` 把 `AiVoiceSnapshot` 映射为阶段、标题、原因、唯一出口、可打断性、电平、扬声器偏好。

动画只由 `phase` 驱动；静音、失败、结束不产生伪动画。

## 9. Shell feature

`ShellViewModel` 继续使用 `SavedStateHandle`；`MainActivity` 只做 ViewModel 获取、Screen 创建和生命周期转发。`ShellScreen` 负责 `YoTabs`、三个页签、AI 页与 inset。

`MainActivity` 不持有页签内容、不创建 `AiChatPage`、不直接组装 `YoTabs`。

## 10. Navigation

导航仍由各页面静态 `intent()` 生成类型安全 Intent；Activity 只负责宿主生命周期。Shell 负责历史面板等跨页动作，页面不直接持有装配根。

## 11. Data 层

### 11.1 设置

```text
AiSettingsRepositoryImpl (memory + revision)
    ├── FileAiDocumentStore (temp + fsync + atomic move)
    ├── EncryptedSecretStore (Keystore AES-GCM)
    └── AiSettingsJson (v3 document)
```

密钥记录：

```text
apikey.<platformId>  → cipherText
voicekey.<modelId>   → cipherText
```

文档与密钥分文件；写入顺序是密钥先、文档后，失败时保留内存并暴露状态。

### 11.2 会话

```text
ConversationStore
    ├── index.json          // 可重建缓存
    ├── conversations/*.json
    └── ConversationWriter  // 按会话合并、串行写入
```

历史读取在 `conversationIo`；索引损坏时扫描文档重建，不在主线程执行。

### 11.3 网络

所有 gateway 端口显式声明异步语义：

```java
CompletableFuture<FetchResult> fetch(@NonNull FetchRequest request);
```

后台读取与写入必须走 `AppExecutors` 中声明的专用执行器；取消通过 token/`AiCancellable`，不靠线程中断猜测。

## 12. 状态、命令与事件

### 12.1 三类数据

| 类型 | 例子 | 生命周期 | 是否持久化 |
| --- | --- | --- | --- |
| Domain state | `AiSettings`、`AiChatState` | 应用级 | 部分 |
| Operation state | fetching、testing、saveStatus | ViewModel/页面级 | 否 |
| Ephemeral UI state | 搜索词、展开态、键盘焦点、草稿 | Screen/ViewModel | 仅 SavedState 中必要项 |

三者不得混成一个可写对象。

### 12.2 页面契约

```java
每个页面使用不可变 record 作为 UiState，具体字段按页面定义；不另造一套与 ViewModel 平行的通用 `UiState`/`ScreenBinder` 接口。

每个页面只暴露：

```java
LiveData<SettingsUiState> uiState();
LiveData<SettingsUiEvent> events();
```

不再出现多个 `LiveData<Boolean>` 由页面自行组合。

### 12.3 错误

保留 `AiFailure`/`AiValidationException` 的领域语义；增加 UI 投影：

```java
record UiError(
        ErrorPlacement placement,
        int messageRes,
        ImmutableList<Object> args
) {}
```

- 表单错误留在字段/区块。
- 网络错误留在操作区块。
- Toast 只用于“操作已生效”确认，不作为状态唯一载体。

## 13. YoUI 使用规则

### 13.1 组件作用域

```java
final class YoComponentScope implements AutoCloseable {
    <T extends AutoCloseable> T own(T component);
    @Override public void close();
}
```

规则：

- 一个 Screen 一个 scope。
- 所有 `YoList`、`YoSelect`、`YoMenu`、`YoDialog`、`YoTextInput` 都登记到 scope。
- `onDestroy` 只调用一次 `scope.close()`。
- 禁止在 `render()` 中直接 `destroy()`。

### 13.2 可见性与重建

| 操作 | 正确做法 | 禁止 |
| --- | --- | --- |
| 文本/语音模式切换 | `panel.setVisibility(...)` | `clearRows()` + 重建 |
| 选择项变化 | `setValue`/`setSelected` | 重新 `build()` |
| 模型列表变化 | 仅更新该 `YoSelect` 的选项 | 重建整个语音面板 |
| 平台测试结果变化 | 更新连接行 | 重建所有平台区块 |
| 搜索过滤 | 更新可见行 | 重建页面 |
| 主题变化 | 由 YoUI 内部主题绑定刷新 | 页面自行重建 |

### 13.3 列表

当前公开 `YoList` API 的 `addItem` 会逐项 `notifyDataChanged()`。APP 侧必须遵守：

- 列表只在结构首次创建时批量构建。
- 模式切换不触碰列表。
- 高频数据更新不直接追加到 `YoList`；使用长寿命列表 + 只重建该区块，或等待 SDK 批量提交接口。
- 大列表（平台模型、历史）单独设计 binder，不与设置卡片的静态行混用。

## 14. 启动与生命周期

```text
Application.onCreate
  ├── 注册刷新率
  ├── debug: StrictMode + Trace
  └── 不读盘、不联网

MainActivity.onCreate
  ├── 创建 Shell screen
  ├── AppContainer 懒创建
  └── UI 先显示 loading

SettingsScreen.onStart
  ├── ViewModel.start()
  ├── 后台读取设置/模板
  └── 渲染 loading

SettingsScreen.onStop
  └── 取消订阅（组件不销毁）

SettingsScreen.onDestroy
  └── YoComponentScope.close()
```

页面重建复用 ViewModel；应用级 `AiChatSession`/设置仓库仍在，ViewModel 恢复后从当前快照继续。

## 15. 并发与一致性

### 15.1 设置写序列

```text
user click
  → ViewModel/UseCase
  → settings repository memory commit (no IO)
  → publish snapshot (revision N)
  → persistence executor request(N)
  → settingsPersist worker
      → write secrets (if changed)
      → write document envelope
      → mark SAVED
```

如果进程在 `SAVING` 中被杀：

- 文档要么是 revision N-1，要么是 N。
- 密钥不会先被删除再写文档。
- 下次启动按 revision 和引用关系修复孤儿密钥。

### 15.2 多观察者

- 设置 Store 的 observer 可以同步收到内存快照，但 observer 本身必须立即返回。
- `AiChatSession` 的 observer 只把后续处理投递到 `chatState`。
- UI 的 observer 只把状态投递到 `main`。
- 禁止在 Store 的发布回调中执行网络、磁盘或 View 操作。

### 15.3 迟到结果

所有异步操作带 token/revision：

- 测速结果只在平台/模型仍存在且 revision 匹配时写入。
- 拉取模型结果只在地址/Key 未变化时合并。
- 语音连接回调只在当前 call token 下生效。
- 聊天回调只在当前 message token 下生效。

## 16. 测试策略

| 层 | 测试 |
| --- | --- |
| SettingsRepository | 并发 commit 顺序、revision、observer、失败状态、合并写入 |
| PersistenceQueue | 多次 commit 只落最新、进程中断恢复、密钥/文档顺序 |
| Session / UseCase | 命令状态机、取消、迟到结果、页面重建恢复 |
| Binders | 相同状态不重建、模式切换不改组件数量、组件只在 release 销毁 |
| UiMapper | 每个分支、资源参数、错误位置 |
| Chat | token 过滤、流式合并、停止/重试/恢复 |
| Voice | 状态机、音频线程、取消、打断 |
| 架构守卫 | feature 依赖、主线程 IO、组件所有权 |

性能验收（当前设备 120Hz）：

| 指标 | 目标 |
| --- | --- |
| 设置模式切换 | 稳态 p95 完成帧 ≤ `16.7ms`，无超过 `33ms` 的帧 |
| 主线程长任务 | 无 `fsync`、无 JSON 编码、无 `YoList`/`YoSelect` 创建 |
| 模式切换组件数 | `YoList`/`YoSelect` 创建次数 = 0 |
| 首帧延迟 | 点击到状态发布 ≤ `16ms` |
| 设置保存 | 点击后 UI 立即更新，磁盘写入不在主线程 |
| 平台页测试结果 | 不重建整页 |

重构前基线：

- 文本方向完成帧最长约 `50.97ms`。
- 语音方向完成帧最长约 `217.97ms`。
- 文本→语音：`onSelectionChanged` 约 `13.4ms`，其中 `fsync` 同量；`render` 约 `44.6ms`。

重构后同设备隔离复采（`dumpsys gfxinfo reset` 后只点击一次文本→语音）：

- 只产生 3 帧；首帧约 `20ms`，第二帧约 `124.6ms`。
- 第二帧中 `PerformTraversalsStart → DrawStart` 约 `75.9ms`，GPU 约 `2ms`。
- `simpleperf` 调用栈显示耗时集中在 `ViewRootImpl.performTraversals → ViewGroup.dispatchDraw → NestedScrollView/RecyclerView/YoUI` 的 display-list 与 RenderThread 的 `GrDirectContext::flushAndSubmit`、首次纹理/着色器创建；没有 APP JSON、文件写入或 `fsync`。
- 因此卡顿主因仍是 APP 的“首次显示策略 + YoUI 首绘/材质初始化”，SDK 分段状态机不是主因。透明预热实验没有降低点击后的长帧，已撤回，不留在代码中。

## 17. 重构完成范围

- 设置：异步读取/异步持久化、单一 UiState、长寿命面板、区块 binder。
- 平台：页头/连接/模型/高级/危险区拆分、测速代际与晚到结果防护、命令决策抽到 `AiPlatformPolicy`。
- 对话：页头/内容/菜单拆分，ViewModel 单一 UiState，页面不再直接持有装配根。
- 通话：页头/舞台/控制排拆分，快照由 Mapper 投影为 UiState。
- 历史：单一 UiState、页头/四态/列表/确认对话框拆分、专用后台读取执行器。
- Markdown 与助手消息：代码块、引用、列表、表格、思考卡、异常条、底部操作各自独立 View；组合视图只负责块差异和绑定。
- Shell：`MainActivity` 只保留宿主职责，`ShellScreen` 管理页签与 AI 页。
- 装配：`AppContainer` + `AppExecutors` 统一进程级依赖与线程语义。

物理 Gradle module 拆分未实施；当前以单 `:app` 包边界和构建守卫保证依赖方向。

## 18. 风险与取舍

| 风险 | 处理 |
| --- | --- |
| 内存先提交、磁盘后写导致进程崩溃丢失最后一次修改 | 对模式/偏好可接受；对 Key/危险操作提供 `flush()` 并显示保存状态 |
| 设置文档与密钥分文件，崩溃时可能不一致 | revision + 写入顺序 + 启动修复；不做跨文件伪事务 |
| `YoList` 逐项 `notifyDataChanged()` | APP 侧避免高频追加；必要时推动 SDK 批量提交 |
| 长寿命组件在主题/配置变化下泄漏 | 所有组件统一登记 `YoComponentScope`，只由 scope 释放 |
| 物理拆模块增加构建复杂度 | 最后阶段做；先完成包边界与行为迁移 |
| LiveData 不适合高频流 | delta 帧合并；必要时引入专用 `UiStateStore`，仍由主线程发布 |

不采用：

- 不在 ViewModel 里继续加 `Executor` 参数。
- 不把 `fsync` 简单包进 `post`，因为那只是延后阻塞，不改变主线程 I/O 架构。
- 不把整个设置页塞进 `RecyclerView` 以规避 `YoList`，那会破坏现有 YoUI 视觉和交互约定。
- 不引入 Hilt/Room 作为本阶段前置条件；现有构造注入与 JSON 快照足够，先解决边界和时序。
- 不修改 YoUI 私有类，也不通过反射调用内部实现。

## 19. 最终验收门

必须同时满足：

1. 当前所有单元测试通过。
2. `checkAiLayerImports`、`checkShellLayerImports`、`checkMainThreadIoImports`、`checkComponentOwnership` 通过。
3. 当前设备 120Hz 下，文本↔语音切换 30 次，p95 ≤ `16.7ms`，无超过 `33ms` 的完成帧。
4. 设置页切换过程中 `YoList`/`YoSelect` 创建次数为 0，`destroy` 次数为 0。
5. 主线程 trace 中无 `FileDescriptor.sync`、`Files.move`、JSON 编码、网络调用。
6. 页面重建后仍显示同一设置快照；保存失败有可见状态与重试。
7. 对话流式、停止、重试、旋转恢复、通话跨转屏、历史读取失败均保持现有行为。
8. 旧文档可迁移，旧密钥可读取，新文档回滚到旧版本时不会被破坏。

后续改动必须保持这套边界；新增功能按同一分层扩展，不得回到“Activity 管状态 + 直接落盘 + 每次重建控件”的旧路径。
