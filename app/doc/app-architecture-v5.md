# APP 架构 v5：全量重构设计

> 状态：设计基线，2026-10-31。
> 范围：`app` 模块的进程装配、状态、线程、持久化、页面、YoUI 生命周期、AI 设置/对话/通话/历史，以及 shell 导航。
> 不在本次范围：`lib_ai` 的协议实现、`lib_bluetooth` 的设备协议、YoUI 内部实现。
>
> 本文是实现前的目标设计，不代表代码已迁移。以本文和当前源码为准。

## 1. 一页结论

当前 APP 的问题不是“某个页面需要换控件”，而是五个系统性边界没有建立：

1. **持久化边界**：分段点击回调在主线程同步执行 JSON 编码、文件写入、`fsync`、改名。
2. **页面生命周期边界**：每次模式切换都销毁并重建 `YoList`/`YoSelect`，页面组件寿命短于页面本身。
3. **状态边界**：领域状态、操作状态、页面临时状态、一次性事件混在 ViewModel/Activity 中，存在多个真相来源。
4. **线程边界**：部分用例同步执行，部分由 ViewModel 手工投递到 executor，线程语义由调用点决定而不是由端口定义。
5. **模块边界**：单模块内的包边界虽有 Gradle 检查，但 feature 之间没有独立 API，装配根持续膨胀。

目标架构：

```text
Activity / Screen
        │ 只读 UiState、只发命令
        ▼
Feature ViewModel
        │ 订阅 Controller / Store，不持有 IO 实现
        ▼
Feature Controller（应用级状态机）
        │ 纯状态迁移、操作编排、生命周期
        ├─────────────► Settings Store（内存单写者）
        ├─────────────► Chat Controller（串行状态机）
        └─────────────► Voice Controller（串行状态机）
                              │
                              ▼
                    Data / Platform 端口实现
                    （网络、文件、Keystore、音频）
```

硬约束：

- 主线程不读磁盘、不写磁盘、不加密、不序列化大对象、不发网络。
- 一个聚合只有一个内存写者；所有写都经过同一事务入口。
- 页面创建的 YoUI 组件与页面同寿命；模式切换只改变可见性和绑定值。
- ViewModel 不注入 `Executor`，不编排多条用例；它只依赖 feature controller 的命令/状态接口。
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

## 3. 目标模块结构

### 3.1 逻辑分层

```text
:app
  └── app shell / navigation / AppContainer

:core:common
  └── Result、Observable、MainPoster、Trace、Clock、Ids

:core:ui
  └── Screen、ScreenHost、UiEvent、YoComponentScope、主题适配

:feature:settings
  ├── api      页面入口、命令、只读状态模型
  ├── application  SettingsController / PlatformEditorController
  ├── domain    settings 聚合模型、策略、端口
  ├── data      JSON、文件、密钥、迁移
  └── ui        Activity、Screen、Binder、UiMapper

:feature:chat
  ├── api
  ├── application  ChatController
  ├── domain
  ├── data
  └── ui

:feature:voice
  ├── api
  ├── application  VoiceController
  ├── domain
  ├── data
  └── ui

:feature:shell
  └── tabs、destination、SavedState
```

### 3.2 迁移期的单模块形态

第一阶段不立即拆 Gradle module，先在 `app` 内形成相同包结构：

```text
com.ggec.earbuds
  app
    AppContainer
    AppExecutors
    AppNavigation
  core
    observable
    ui
    trace
  feature
    settings
      api
      application
      domain
      data
      ui
    chat
    voice
    shell
  platform
```

待 feature 边界稳定、测试覆盖完成后再物理拆 module。物理拆分不是第一阶段目标，避免同时改变包名、Gradle 配置和运行行为。

### 3.3 依赖规则

| 层 | 可以依赖 | 禁止依赖 |
| --- | --- | --- |
| `ui` | `application`、`api`、`core:ui` | `data`、`wiring`、具体 gateway、`lib_ai` |
| `application` | `domain`、`core:common` | Android View、YoUI、JSON、具体网络客户端 |
| `domain` | Java 标准库、自身模型 | Android、JSON、`lib_ai`、文件系统 |
| `data` | `domain`、`lib_ai`、JSON、平台端口 | `ui`、`application`、YoUI |
| `platform` | Android、Keystore、音频 API | `ui`、YoUI、装配根 |
| `app` | 所有 feature 的 `api` 与装配实现 | 把实现泄漏给 feature |

新增 Gradle 守卫：

- `checkFeatureDependencies`：禁止 feature A 直接引用 feature B 的 `ui/data/application`，只允许 `api`。
- `checkMainThreadIo`：禁止 `ui`、`ViewModel` 中出现 `fsync`、`Files.move`、`FileOutputStream`、`org.json`。
- `checkComponentOwnership`：禁止 `Activity.onDestroy` 之外的裸 `YoList.destroy`/`YoSelect.destroy`，除非位于 `YoComponentScope.close()`。

## 4. 核心运行时

### 4.1 进程装配

`AppContainer` 是唯一应用级装配根，替代 `AiGraph` + `ShellGraph` 分散装配。

```java
public final class AppContainer {
    private final AppExecutors executors;
    private final SettingsStore settingsStore;
    private final SettingsController settingsController;
    private final ChatController chatController;
    private final VoiceController.Factory voiceFactory;
    private final Navigation navigation;

    public SettingsFeature settings();
    public ChatFeature chat();
    public VoiceFeature voice();
    public ShellFeature shell();
}
```

约束：

- `Application.onCreate()` 只注册进程级能力（刷新率、崩溃日志、StrictMode），不读设置、不初始化网络。
- `AppContainer` 懒初始化；首次访问 feature 时才创建该 feature 的 store/controller。
- 所有单例都在 `AppContainer` 内，禁止业务代码再写静态 `getInstance()`。

### 4.2 线程设施

`AppExecutors` 定义线程语义，不把裸 `Executor` 注入 ViewModel：

| Executor | 线程数 | 用途 | 禁止 |
| --- | --- | ---: | --- |
| `main` | 1 | UI 状态发布、View 操作 | 文件、网络、加密、长 CPU |
| `settingsPersist` | 1 | 设置快照串行落盘、密钥写入 | 网络、UI |
| `conversationIo` | 1 | 会话文档写入/读取 | 网络、UI |
| `network` | 4 | 拉模型、测速、实时连接建立 | UI、文件 |
| `chatState` | 1 | 对话状态机串行迁移 | 阻塞 I/O |
| `voiceState` | 1 | 通话状态机串行迁移 | UI |
| `audioCapture` | 1 | AudioRecord 读帧 | UI |
| `audioPlayback` | 1 | AudioTrack 写帧 | UI |

规则：

- 每个状态机只有一个执行器，命令按到达顺序执行。
- 状态机内部不得同步等待网络或磁盘；等待都表现为状态和事件。
- 数据层公开方法标注 `@WorkerThread`；UI 公开方法标注 `@MainThread`。
- ViewModel 不接收 `Executor` 构造参数。

### 4.3 反应式原语

领域层不依赖 LiveData。用最小纯 Java 原语：

```java
public interface StateSource<T> {
    @NonNull T current();
    @NonNull Subscription subscribe(@NonNull Consumer<T> observer);
}

public interface CommandSink<C> {
    void dispatch(@NonNull C command);
}
```

UI 层适配为 LiveData：

```java
final class LiveDataBridge<T> {
    LiveData<T> bind(StateSource<T> source, MainPoster main) {
        MutableLiveData<T> state = new MutableLiveData<>(source.current());
        Subscription subscription = source.subscribe(value ->
                main.execute(() -> state.setValue(value)));
        return new LiveDataWithSubscription(state, subscription);
    }
}
```

不再使用“一个 ViewModel 暴露多条互相关联的 LiveData”的模式。一个页面只有一个 `UiState`，一次性事件单独一条流。

## 5. Settings feature

### 5.1 聚合与模型

`AiSettings` 继续作为不可变聚合根，但拆出“用户设置”和“运行时操作状态”：

```java
record SettingsSnapshot(
        AiSettings value,
        long revision,
        SaveStatus saveStatus
) {}

enum SaveStatus {
    IDLE,
    SAVING,
    SAVED,
    FAILED
}
```

`SettingsSnapshot` 属于 application/domain 边界，不包含 Android 资源或 View 状态。

### 5.2 单写者 SettingsStore

```java
public interface SettingsStore {
    @NonNull SettingsSnapshot snapshot();
    @NonNull Subscription subscribe(@NonNull Consumer<SettingsSnapshot> observer);

    /** 只做内存事务和发布，不做磁盘 I/O。 */
    @NonNull SettingsCommit commit(@NonNull UnaryOperator<AiSettings> change);

    /** 等待当前 revision 持久化完成；只用于测试、进程退出和显式“保存”动作。 */
    @NonNull CompletableFuture<Void> flush();
}

record SettingsCommit(
        AiSettings value,
        long revision,
        SaveStatus saveStatus
) {}
```

`commit` 的语义：

1. 在短临界区读取当前内存快照。
2. 执行纯变更函数。
3. 比较并生成下一 revision。
4. 更新内存快照。
5. 同步发布新 `SettingsSnapshot`。
6. 把 `(revision, snapshot)` 投递到 `settingsPersist`，立即返回。

关键点：**内存提交与磁盘持久化分离**。分段切换的用户可见状态在微秒级完成；`fsync` 不再占用点击消息。

### 5.3 持久化队列

```java
final class SettingsPersistenceQueue {
    private final AtomicReference<SettingsSnapshot> pending = new AtomicReference<>();
    private final Executor executor;

    void request(SettingsSnapshot snapshot) {
        pending.accumulateAndGet(snapshot,
                (previous, next) -> previous.revision() >= next.revision() ? previous : next);
        scheduleOnce();
    }

    private void drain() {
        SettingsSnapshot next;
        while ((next = pending.getAndSet(null)) != null) {
            persist(next);
        }
    }
}
```

规则：

- 只持久化最新 revision；中间 revision 可合并。
- 同一 revision 的密钥写入必须先于文档写入。
- 删除平台时先写文档，再清理无引用密钥。
- 任何失败进入 `FAILED`，保留内存值并暴露重试；不静默回滚用户操作。
- 文档写入继续使用临时文件、`fsync`、原子改名，但全部在 `settingsPersist`。
- 设置文档与密钥记录增加 `revision`，便于诊断“文档与密钥不一致”。

### 5.4 Controller

`SettingsController` 是应用级、无 Android 的状态编排者：

```java
public final class SettingsController {
    public StateSource<SettingsState> state();
    public CommandSink<SettingsCommand> commands();
}

sealed interface SettingsCommand {
    record SelectMode(AiInteractionMode mode) implements SettingsCommand {}
    record SelectVoiceModel(AiVoiceModel model) implements SettingsCommand {}
    record SaveVoiceKey(String key) implements SettingsCommand {}
    record SavePrompt(String prompt) implements SettingsCommand {}
    record FetchRealtimeModels() implements SettingsCommand {}
    record CancelFetch() implements SettingsCommand {}
}
```

操作状态（fetching、validation、failure）属于 Controller，不属于 ViewModel：

```java
record SettingsState(
        SettingsSnapshot settings,
        OperationStatus realtimeFetch,
        ImmutableList<PlatformTemplate> templates,
        SettingsError lastError
) {}
```

ViewModel 只做：

```java
public final class AiSettingsViewModel extends ViewModel {
    private final MutableLiveData<AiSettingsUiState> uiState;
    private final MutableLiveData<AiSettingsEvent> events;

    public void selectMode(AiInteractionMode mode) {
        controller.commands().dispatch(new SettingsCommand.SelectMode(mode));
    }
}
```

不再在 ViewModel 里 `guard(() -> usecase.execute())`。

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

```java
public interface PanelBinder<S> {
    View createView();
    void bind(@NonNull S state);
    void release();
}
```

每个 binder 记录上次绑定的内容签名：

```java
if (state.signature().equals(lastSignature)) {
    return;
}
```

文本面板的内容签名只包含身份和提示词；语音面板只包含厂商、地址、实时模型、方言、Key 状态和 fetching 状态。模式变化不是内容签名的一部分。

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
PlatformScreen
├── IdentityHeaderBinder
├── ConnectionPanel
├── ModelsPanel
├── AdvancedPanel
└── DangerPanel
```

`ModelsPanel` 的模型集合变化时只更新模型列表；连接结果、Key 显隐、高级展开只更新各自 binder。不得因连接测试结果变化重建整个页面。

平台页状态合并为一个：

```java
record PlatformEditorState(
        LoadState load,
        PlatformHeader header,
        ConnectionSection connection,
        ModelsSection models,
        AdvancedSection advanced,
        boolean canDelete,
        OperationState operation
) {}
```

`testing`、`speedRunning`、`searchQuery`、`keyRevealed` 都是 `PlatformEditorState` 的字段，由 Controller 统一产生，不再由 ViewModel 分散持有。

## 7. Chat feature

### 7.1 保持应用级会话

`AiChatSession` 已是正确的应用级状态机：单一串行执行器、命令排队、迟到回调带 token 过滤。保留这个核心，不改成页面级状态。

### 7.2 重命名与边界

- `AiChatSession` → `ChatController`（application 层），因为它编排网关、仓库、设置订阅。
- `ChatState` 继续不可变。
- 消息增量只发布到 `ChatController`，不直接回调 UI。
- 会话持久化进入 `conversationIo`，使用“按会话最新快照合并”的写队列，避免每个 token 触发整份文档写盘。
- `ChatController` 维护 `ChatUiProjection` 所需的最小状态；UI mapper 不访问 repository。

### 7.3 发布策略

流式 delta 以帧合并发布：

```text
网关 token → chatState executor → 合并待发布 delta
          → main poster 每帧最多一次 setValue
```

不是每个 token 一次 `postValue`。文本聚合和 Markdown 渲染仍在主线程，但发布频率受控。

## 8. Voice feature

### 8.1 生命周期

- `VoiceController` 由通话页 ViewModel 持有；通话跨转屏保留。
- `AiVoiceSession` 的音频设备实例仍按通话创建/释放。
- 会话状态机、音频采集、音频播放各在自己的线程；Controller 只做状态合并和 UI 投影。
- 页面销毁不等于通话结束；只有 `hangUp()` 或 ViewModel `onCleared()` 才停止。

### 8.2 状态

语音页使用一个 `VoiceUiState`：

```java
record VoiceUiState(
        Phase phase,
        List<TranscriptLine> transcript,
        float inputLevel,
        float outputLevel,
        VoiceFailure failure,
        boolean muted,
        boolean speakerphone
) {}
```

动画只由 `phase` 驱动；静音、失败、结束不产生伪动画。

## 9. Shell feature

`ShellViewModel` 继续使用 `SavedStateHandle`，但页面创建从 `MainActivity` 中抽离：

```java
public interface Screen {
    View createView();
    void onStart();
    void onStop();
    void release();
}

public interface ScreenFactory {
    Screen create(ScreenKey key);
}
```

`MainActivity` 只做：

- 创建 `YoTabs`。
- 通过 `ScreenHost` 创建/恢复 screen。
- 管理 inset 与生命周期。

AI tab 不再直接在 `MainActivity` 里 `new AiChatPage(...)`；导航由 `ShellFeature` 提供。

## 10. Navigation

导航从 Activity 静态 `intent()` 中抽出：

```java
public interface AppNavigator {
    void openSettings();
    void openPlatformEditor(PlatformEditorTarget target);
    void openCall();
}
```

Activity 只负责宿主生命周期，不负责决定“下一个页面是谁”。深链、通知、测试都复用同一导航契约。

## 11. Data 层

### 11.1 设置

```text
SettingsStore (memory)
    ├── SettingsDocumentStore (JSON envelope, temp + fsync + atomic move)
    ├── SecretStore (Keystore AES-GCM)
    └── SettingsMigration (v3 → v4 envelope)
```

文档 envelope：

```json
{
  "schema": 4,
  "revision": 42,
  "settings": { "...": "..." }
}
```

密钥记录：

```text
apikey.<platformId>  → { revision, cipherText }
voicekey.<modelId>   → { revision, cipherText }
```

迁移时必须：

- 先读取旧文档并生成完整新快照。
- 写入新 revision。
- 成功后才切换读路径。
- 失败保留旧文档和备份。

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

或使用注入的 `AsyncRunner`，但不得把裸 `Executor` 放进 ViewModel。取消通过 `CancellationSignal`/token，不靠线程中断猜测。

## 12. 状态、命令与事件

### 12.1 三类数据

| 类型 | 例子 | 生命周期 | 是否持久化 |
| --- | --- | --- | --- |
| Domain state | `AiSettings`、`AiChatState` | 应用级 | 部分 |
| Operation state | fetching、testing、saveStatus | Controller/页面级 | 否 |
| Ephemeral UI state | 搜索词、展开态、键盘焦点、草稿 | Screen/ViewModel | 仅 SavedState 中必要项 |

三者不得混成一个可写对象。

### 12.2 页面契约

```java
public interface UiState {
    @NonNull String signature();
}

public interface UiEvent {}

public interface ScreenBinder<S extends UiState, E extends UiEvent> {
    void render(@NonNull S state);
    void handle(@NonNull E event);
}
```

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
  ├── 订阅 Controller
  ├── 发送 Load command
  └── 渲染 loading

SettingsScreen.onStop
  └── 取消订阅（组件不销毁）

SettingsScreen.onDestroy
  └── YoComponentScope.close()
```

页面重建不得重新触发磁盘加载；Controller 状态是应用级，ViewModel 恢复后立即拿到当前快照。

## 15. 并发与一致性

### 15.1 设置写序列

```text
user click
  → controller command
  → settingsStore.commit (memory, no IO)
  → publish snapshot (revision N)
  → persistenceQueue.request(N)
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
- ChatController 的 observer 只把后续处理投递到 `chatState`。
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
| SettingsStore | 并发 commit 顺序、revision、observer、失败状态、合并写入 |
| PersistenceQueue | 多次 commit 只落最新、进程中断恢复、密钥/文档顺序 |
| Controllers | 命令状态机、取消、迟到结果、页面重建恢复 |
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

基线 trace 记录：

- `gg-current-text.txt`：文本方向完成帧最长约 `50.97ms`。
- `gg-current-voice.txt`：语音方向完成帧最长约 `217.97ms`。
- `gg-current.art`：文本→语音，`onSelectionChanged` 约 `13.4ms`，其中 `fsync` 同量；`render` 约 `44.6ms`。

迁移后的验收必须在同一设备、同一 APK 变体、无 profiler 干扰下重采。

## 17. 分阶段迁移

每个阶段都必须可独立编译、装机、回滚。

### P0：架构守卫与基线

- 新增 `app/doc/app-architecture-v5.md`。
- 增加主线程 I/O、feature 依赖、组件所有权检查。
- 增加设置切换 macrobenchmark/trace 采集脚本。
- 不改变行为。

验收：现有测试通过；守卫能捕获已知违规样例。

### P1：核心运行时

- 引入 `AppContainer`、`AppExecutors`、`StateSource`、`LiveDataBridge`。
- `AiGraph`/`ShellGraph` 迁移为 `AppContainer` 内部实现。
- 保留现有用例接口，先接适配器。

验收：页面行为不变；所有线程仍按旧路径工作。

### P2：SettingsStore 异步持久化

- `AiSettingsRepositoryImpl` 拆为 `SettingsStore` + `SettingsPersistenceQueue`。
- `commit` 只做内存事务；`settingsPersist` 做密钥与文档写入。
- UI 发布在主线程 `setValue`，不再无条件 `postValue`。

验收：主线程 trace 无 `fsync`；并发更新不丢 revision；保存失败可见。

### P3：Settings 页面长寿命组件

- 引入 `AiSettingsScreen`、`TextModePanel`、`VoiceModePanel`。
- `YoList`/`YoSelect` 只创建一次。
- 模式切换只切可见性。

验收：切换时组件创建次数为 0；p95 ≤ `16.7ms`；视觉无变化。

### P4：Platform editor 分区块

- 引入 `PlatformEditorController`、`PlatformScreen` 和区块 binder。
- 测速/连接/模型变化只更新对应区块。

验收：测试结果更新不重建整页；搜索焦点保持。

### P5：Chat/Voice controller 收口

- `AiChatViewModel` 只依赖 `ChatController`。
- 流式 delta 按帧合并发布。
- `VoiceController` 持有通话状态，页面只投影。

验收：旋转、重进、取消、迟到回调测试通过。

### P6：Shell/Navigation

- 引入 `Screen`、`ScreenHost`、`AppNavigator`。
- `MainActivity` 只保留 tabs、inset、生命周期。

验收：三个 tab、深链/通知入口、返回栈一致。

### P7：物理模块拆分

- 将 `feature/settings`、`chat`、`voice`、`shell` 拆为 Gradle module。
- feature 之间只暴露 `api`。

验收：依赖图无环；app 仅依赖 feature api + 装配实现。

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
2. `checkFeatureDependencies`、`checkMainThreadIo`、`checkComponentOwnership` 通过。
3. 当前设备 120Hz 下，文本↔语音切换 30 次，p95 ≤ `16.7ms`，无超过 `33ms` 的完成帧。
4. 设置页切换过程中 `YoList`/`YoSelect` 创建次数为 0，`destroy` 次数为 0。
5. 主线程 trace 中无 `FileDescriptor.sync`、`Files.move`、JSON 编码、网络调用。
6. 页面重建后仍显示同一设置快照；保存失败有可见状态与重试。
7. 对话流式、停止、重试、旋转恢复、通话跨转屏、历史读取失败均保持现有行为。
8. 旧文档可迁移，旧密钥可读取，新文档回滚到旧版本时不会被破坏。

这份设计完成后，后续代码重构应严格按 P0 → P7 的顺序推进；每个阶段只允许改变该阶段声明的边界，避免再次把页面、状态、持久化和线程混在一次改动里。
