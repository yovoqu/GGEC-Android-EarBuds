# 验证报告（2026-11 设备域重画）

> 时间：2026-11-13。设备：motorola edge 60 pro（serial `NEPI4E0106`，Android 真机）。
> 本报告只写本轮实际做过的验证；没做的写「未验证」，不用静态判断代替运行结果。

## 1. 构建与门禁

```
.\gradlew.bat :app:preBuild --offline --console=plain
```

10 条门禁全部执行并通过（`BUILD SUCCESSFUL`）：`checkAiLayerImports`、`checkAppDependencyBoundary`、`checkAppLayering`、`checkAppMvvmLayers`、`checkComponentOwnership`、`checkExplicitImports`、`checkFrameworkToastForbidden`、`checkMainThreadIoImports`、`checkNoBaselineCopies`（本轮新增）、`checkSingleYoLogInit`。

新增/变更门禁的运行意义：

- `checkNoBaselineCopies`：`OneShotLiveData` 只剩 `lib_common` 一份，app 侧无同名实现。
- `checkAppDependencyBoundary`：`com.yovo.lib_common.event` 按包名基线放行；`providerDependencyModules` 删掉 app 并不依赖的 `lib_bluetooth`。
- `checkAppLayering` 新增：`domain/**` 禁 `org.json`；`ui/**` 禁 `java.io.File`/`java.nio.file.Files`/`FileOutputStream`（台账落盘只能在 data）。

## 2. 单元测试

```
.\gradlew.bat :app:testDebugUnitTest --offline --console=plain
```

`app/build/test-results/testDebugUnitTest` 汇总：**66 suites / 482 tests / 0 failures / 0 errors / 0 skipped**。

第三轮（AI 通话页单状态流）新增断言：

| 测试 | 锁住的行为 |
|------|-----------|
| `AiCallViewModelTest.listeningLevelAndSubtitlesTravelWithTheSameStateStream` | 电平与字幕随同一条 `AiCallUiState` 到达；非听取阶段电平为 0 |

`AiCallViewModelTest` 既有断言已改为只读 `uiState()`（`meter()`/`subtitleItems()` 两条独立流已删除）。

第四轮（AI 实现层单源化）新增/改写断言：

| 测试 | 锁住的行为 |
|------|-----------|
| `AiJsonTest.settingsRoundTripWithoutTheKey` | 测速结果的 `outputTokens` 随 JSON 往返（42 进、42 出） |
| `PlatformUseCasesTest`（测速写回） | `RunSpeedTest` 保留网关给的 `outputTokens=55`，`tokensPerSecond()` = 145 而不是 -1 |
| `RealtimeLinkAdapterTest`（全部映射用例） | 分类映射改为调用 `LibAiFailures.kind`，与文本链路同一份规则 |
| `LibAiGatewayTest.fetchReturnsEveryModelAndLeavesSelectionToTheDomain` | 网关把实时模型也原样交回；文本页可用模型由 `AiPlatformConfig.chatModels()` 决定 |
| `LibAiGatewayTest.fetchKeepsChatModelsNewestFirstAndMapsMetadata` | 映射/排序不筛类别；对话子集由领域过滤 |
| `LibAiGatewayTest.vendorCompatiblePathIsPlatformKnowledgeNotGatewayKnowledge` | DashScope 兼容路径由平台目录补齐，重复输入幂等 |

第五轮（设备设置页）验证：

- `checkComponentOwnership` 新增规则后实跑通过：`ui/**` 内已无 `androidx.appcompat.app.AlertDialog`，选项选择与设备操作统一为 `YoMenu`。
- `SettingsRowsView` 加渲染签名守卫；该改动没有真机路径（设置页需要已连接耳机），验证到「编译 + 门禁 + 既有 480 单测」为止。

第六轮（视觉令牌迁移）验证：

- `AiTheme` 新增 `DURATION_*`、`TEXT_CAPTION_LARGE/TEXT_SECTION`、`MEDIA_WHITE/BLACK`、`HANGUP_START/END`、`CALL_PAGE_DARK_*`、`CallPalette`/`callPageStops()`；调用点改为引用令牌。
- **数值逐项照抄**（12.5f、120/140/160/240/300ms、全部 ARGB 与深/浅两套取值），属等价迁移，因此不改变现有视觉；仍无真机对照截图，验证到「编译 + 门禁 + 单测」。迁移后 `ui/ai/**` 内不再出现 `setDuration(<数字>)`、`12.5f` 或 6 位十六进制 ARGB 字面量（仅 `AiTheme` 内定义）。

第七轮（释放通道）验证：

- 菜单全部改为页面级 `YoComponents` 登记（`AiChatMenus`、`AiHistoryListContent`、`AiSettingsDialogs`、`AiPlatformDialogs`），`onDismiss` 只保留业务动作。
- `ScanConnectDialog` 自带 `YoComponents`：关闭只隐藏、页面销毁才 destroy。
- 门禁新增：`ui/**` 内未登记到 `own(...)`/`releases.add(...)` 的 `::destroy` 直接失败；实跑通过，说明 `ui/**` 已无逃逸的方法引用释放。

第八轮（写设置单路 + 通话页职责归位）验证：

- `SaveVoiceApiKey` 成为「写密文 + 提交设置」的唯一入口，`AiSettingsViewModel` 不再持有凭据端口；既有 `saveVoiceApiKey` 断言（UI 反映配置、仓库为准）继续通过。
- 新增 `AiCallViewModelTest.retryStopsTheOldSessionAndStartsANewOne`：重试先停旧会话再起新会话。
- 新增 `AiCallViewModelTest.duplexTogglePublishesItsHintInsteadOfLettingTheViewChooseText`：双工提示由 VM 以资源 id 发布，View 不再自行选文案。
- 汇总：**66 suites / 482 tests / 0 failures**。

第九轮（设置页单状态流）验证：

- `AiSettingsUiState` 新增 `persistence` 与 `templates`；`AiSettingsViewModel` 删除 `saveStatus()`/`templates()` 两条 LiveData，`AiSettingsActivity` 只观察 `uiState()`，添加平台时读当前状态的模板。
- mapper 增加 `map(settings, mode, loaded)`：模板/持久化状态先到时仍保持「加载中」，由既有 `startPublishesLoadingBeforeTheRepositoryFinishes` 锁住。
- 汇总不变：**66 suites / 482 tests / 0 failures**。

第十轮（重复实现与兜底）验证：

- `host()`：删除 `VoiceCard.host`，唯一实现 `AiFormat.host`（`AiSettingsUiMapper` 与 `VoiceHeroCard` 共用）。
- `modeHint`：唯一实现 `AiSettingsUiState.modeHint`，mapper 的 `hint()` 只转调。
- `" · "`：收敛为 `AiFormat.SEPARATOR`（聊天标签、平台菜单、协议+地址、模型数、追加分隔）。
- `AiCallErrorView`：无失败分类时显示中性文案（不再冒充网络失败）；`Action.NONE` 隐藏按钮，不再出现「显示重试但点击无效」。
- 验证仍是「编译 + 门禁 + 单测」：**66 suites / 482 tests / 0 failures**。

第十一轮（provider 图标归位）验证：

- `AiSettingsUiState.VoiceOption` 增加 `iconRes`、`Voice` 增加 `providerIconRes`，由 `AiSettingsUiMapper` 按 `AiVoiceModel` 映射；`VoiceHeroCard` 删除按厂商 id 的 switch。
- `AiSettingsUiMapperTest.anUnconfiguredVoiceModelIsStillSelectedButNotReady` 新增断言：当前服务商与两个选项的图标都来自映射层。
- 汇总不变：**66 suites / 482 tests / 0 failures**。

第十二轮（P2 死代码与三流适配器）验证：

- 删除无调用 API：`AiChatUiState.canSend()`、`AiHeaderActionButton.applyTheme()`、`VoiceSettingsView` 只创建不释放 `YoComponents` 的第二构造器。
- `AiSectionHeader` 的 `build(title, action, onAction)` 三个恒 null 参数与不可达分支删除，改为 `container()` + 两个显式装配方法。
- `AiHistoryUiState` 去掉为旧三流准备的 `from(Boolean, Boolean, List)` 适配器，改为 `initial()` 与 `of(items, failed)`；失败但仍已有行时「内容态 + 失败标记」并存的行为由测试锁定。
- 汇总不变：**66 suites / 482 tests / 0 failures**。
第十三轮（平台能力归位与常量单源）验证：
- `AiCallViewModel` 删除 `MainTicker`，只保留 `Ticker` 抽象；新增 `wiring/MainThreadTicker` 由 `AiGraph` 注入。
- `checkAppMvvmLayers` 增加规则：`*ViewModel.java` 不得 import `android.os.Handler|Looper`；该规则当场抓出 `ScanViewModel` 的 `Looper.myLooper()` 判断，改为无条件 `postValue`（状态可能来自扫描回调线程）。
- `AiHistoryRenameDialog` 的标题上限改用 `AiTitlePolicy.MAX_RENAMED_CODE_POINTS`，`AiPlatformDialogs` 的输入上限改用 `AiModelIdPolicy.MAX_LENGTH`（两者提为 `public`），UI 不再存一份会漂移的数字。
- `AiFormat` 的遗留注释删除。
- 汇总不变：**66 suites / 482 tests / 0 failures**。


第二轮（AI 存储 P0）新增断言：

| 测试 | 锁住的行为 |
|------|-----------|
| `AiSettingsRepositoryImplTest.aCorruptDocumentIsReportedAsStorageFailureAndLeftOnDisk` | 损坏 settings.json → `AiFailure(STORAGE)`，且文件内容原样保留 |
| `AiStoreTest.corruptSecretsDocumentIsReportedAndNeverOverwritten` | 损坏 secrets.json → 读/写都失败，不把其余密钥覆盖掉 |

本轮新增覆盖（对应 `verification` 断言点）：

| 测试 | 锁住的行为 |
|------|-----------|
| `DeviceRosterTest` | 身份去重、活动置顶 + 时间降序、删除时摘活动指针、`known()` 语义 |
| `DeviceSessionTest` | 先断后连、被取代的尝试结算 `REJECTED`、跨代终态丢弃、`close` 取消进行中连接 |
| `OpenDeviceTest` | 缺经典地址 → `INCOMPLETE_IDENTITY` 且不碰链路；已就绪 → 直接 `Ready` 不重连 |
| `SyncActiveDeviceTest` | READY 写 LIVE 并保留别名；DISCONNECTED 转 ARCHIVED 且保留最后已知电量/固件 |
| `ObserveDeviceRosterTest` | 实时覆盖只发生在 CONNECTING/READY；断开回落到落盘台账 |
| `DeviceRosterJsonTest` | 往返一致、缺文件=空台账、损坏/未知版本 → `IOException`、隔离 `.bad` |
| `DeviceRosterRepositoryImplTest` | 变更即发布并落盘、冷启动读回、损坏→空台账+失败+证据、无变化不写盘 |
| `DeviceRosterUiMapperTest` | LIVE/ARCHIVED 文案分家、未知电量/固件不占位、相对时间分级 |

## 3. 真机验证

安装：`:app:assembleDebug` 产出 `app-debug.apk`（25,141,800 B），`adb install -r` 结果为 `Success`；启动 `com.ggec.earbuds/.ui.shell.MainActivity`。

| 场景 | 结果 | 证据 |
|------|------|------|
| 冷启动空台账 | 设备页显示新空态「还没有可管理的设备」+ 右上 `+` + 底部三页签 | `screen.png` |
| 进入扫描 | 点 `+` 打开 `ScanActivity`，实际扫到附近 4 台设备（Vidda/Hisense），显示 `dBm` 与停止按钮 | `scan/screen.png` |
| 多设备台账（冷启动读盘） | 用 `run-as` 写入 2 台设备的 `files/devices/roster.json` 后强杀重启：第一行显示别名「我的耳机」、HS01 地址、`已断开`、`电量 80% · 75% · 60%`、`固件 1.0.3`；第二行显示 `HS02`、`365 天前同步`，且**不显示**电量/固件（未知不占位） | `roster-with-menu.png`、`ui-roster.xml` |
| 行内操作入口 | 每行右侧 `⋯`（content-desc「更多操作」）可点击，弹出 STANDARD 对话框：标题=设备名，按钮=重命名 / 取消 / 忘记此设备 | `roster-with-menu.png` |
| 进程稳定 | 上述交互期间 `logcat -b main` 无本应用异常/崩溃 | adb logcat（13:00 段） |

未取得运行证据的项（如实记录）：

- **重命名输入框与忘记确认框**：设备上另一个演示应用（YoUI 页面实验室）反复抢占前台，两次截图都落到它上面，没抓到对话框帧。代码路径与台账落盘已由单测覆盖，但「真机点按 → 弹框 → 写入」这一段未完成观察。
- **真实耳机连接/写入**：现场没有 HS 系列耳机（扫描到的都是第三方设备），`OpenDevice → 连接 → READY → 能力写入`整条链路没有运行观察，仍停在「已读到」。

## 4. 与审查报告的对应

- C 片 P0（`OneShotLiveData` 双份、裸 LiveData 事件通道）已在真机交互中不再出现重放（事件走 `OneShotLiveData`），并在门禁里加了结构性禁止。
- D 片 P0（`DeviceIntake` 假开关、无台账）已由本报告第 2、3 节的测试与截图证明：台账能跨进程读回、每台设备的状态与信息按来源呈现。
- A/B 两片（AI 域）与 C 片剩余 P1 未在本轮修复，按 `audit.md` 的下一刀范围处理。

第十四轮（AI 界面收尾）验证：

- AiFormat.messageMeta(context, elapsed, tps, tokens) 与 sectionTitle(context, res, count)：尾注与分区标题的组合逻辑离开 View；AiAssistantFooterView/PlatformModelsSection 只调用与显示。
- AiTheme 增加输入区令牌（COMPOSER_PAD/INPUT_PAD/TEXT_INPUT/SEND_SIZE/SEND_GLYPH_PAD/SEND_MARGIN/SEND_RADIUS），AiComposerView 不再出现裸数字。
- AiCallScreen 删除 getIdentifier("status_bar_height") 的反射式取值与 Math.max 兜底，顶部内边距只来自 WindowInsetsCompat 派发的 statusBars()|displayCutout()。
- 汇总不变：**66 suites / 482 tests / 0 failures**。

第十五轮（AiModel 分层）验证：

- 新增 data/ai/json/StoredModel：磁盘上只保留 id + categoryId；AiSettingsJson 的编码/解码改走它，领域 AiModel 不再出现在序列化代码里。
- 新增 StoredModelTest（4 例）：只持久化 id/类别、手工条目无类别读回即丢弃、往返保真、未知类别与空 id 丢弃。
- 汇总：**67 suites / 486 tests / 0 failures**。

第十六轮（A 片 P2 收口 + 真机复验）验证：

- 门禁新增：*ViewModel.java 不得 import domain.*.port.*Repository。据此修掉真违规：DeviceRosterViewModel 不再直连仓库，失败与启动异常经 ObserveDeviceRoster.observeFailures/startupFailure 取得。
- 删除遗留未用 import（AiSettingsViewModel 的 AiCredentialStore、DeviceRosterViewModel 的 DeviceRosterRepository）。
- 门禁总数 12；四片（A/B/C/D）均为 CLEAN。
- 真机复验（motorola edge 60 pro / NEPI4E0106）：db install -r 成功后冷启 MainActivity，	opResumedActivity 为本应用，设备台账页正常渲染两条记录（inal-roster.png：HS01 实时电量/固件、HS02 归档记录）。
- 汇总：**67 suites / 486 tests / 0 failures / 0 errors**；:app:assembleDebug 成功。

第十七轮（设备信息细化 + 假数据清零）验证：

- 断开语义：SyncActiveDevice 在 DISCONNECTED 时写 DeviceFacts.unknown() 与空 DeviceState，只留身份元信息与 lastSeenMillis；SyncActiveDeviceTest.disconnectArchivesRecordAndDropsRealtimeValues 锁定。
- 标题栏：DeviceRosterUiState.title 取当前 LIVE 设备的经典蓝牙名（无经典名退回 BLE 名/别名），无连接时为空、页面回落“设备”；
oActiveDeviceLeavesTheTitleEmpty 锁定。
- 下拉菜单：DeviceRosterUiState.Choice{name, address, modelName, connected}；型号名由 DeviceCapabilityCatalog 查得（biz 资料），APP 不维护型号表；	itleComesFromTheConnectedDeviceClassicName 锁定三项字段。
- 假数据清零：删除设备侧 iles/devices/roster.json 与文档里的种子 oster-seed.json、以及展示种子数据的旧截图；empty-state.png 为清空后的真机画面（空态 + 标题“设备” + 无下拉箭头）。
- 汇总：**67 suites / 488 tests / 0 failures**；:app:assembleDebug 成功并在 NEPI4E0106 上覆盖安装、冷启通过。

第十八轮（切换设备 = 先断后连）验证：

- DeviceSession.open 换设备时先 disconnect，并对旧设备身份发一次 DISCONNECTED 快照；SyncActiveDevice.resolveTarget 遇到 DISCONNECTED 先按 modelId + BLE 地址 找台账记录，找不到才回落到会话目标——否则旧快照会被算到新设备头上。
- 新增 SyncActiveDeviceTest.openingAnotherDeviceFirstDisconnectsAndArchivesTheCurrentOne：A 就绪 → 切到 B 后，port.disconnects == 2、连接顺序 [A, B]、A 变 ARCHIVED 且 acts.known() == false、B 为 LIVE 且电量为新值、ctiveId == B。
- 汇总：**67 suites / 489 tests / 0 failures**；:app:assembleDebug 成功并在 NEPI4E0106 覆盖安装、冷启通过（MainActivity 前台）。

第十九轮（真机实跑：连接 / 标题 / 下拉 / 切换）验证：

真机 motorola edge 60 pro（NEPI4E0106），实跑证据在 device-evidence/：

-  1-scan-real-devices.png：扫描页发现 8 台真实耳机（Vidda V12/V11、Hisense HiClips 等），带 MAC 与 RSSI。
-  2-connected-title-dropdown.png：点选 74:D0:82:7E:12:A7 后回到设备页——标题栏变为该设备的蓝牙名（Vidda V12耳夹式耳机），右侧出现下拉箭头；行显示「已连接」、HS02 · 74:D0:82:7E:12:A7。
-  3-dropdown-three-fields.png：下拉项严格三项——名称（附「已连接」）、MAC、型号（74:D0:82:7E:12:A7 · HS02）。
-  4-after-switch-archived.png + oster.json：在扫描页改选 E9:09:59:0E:63:B0（Vidda V11）后，台账 ctive 由 HS02 换成 HS01，V12 的 lastSeen 冻结在切换瞬间并转为 ARCHIVED；返回设备页时 V12 显示「1 分钟前同步」且**不再显示电量/固件**（断开清空实时值生效）。
-  5-settings-capabilities-unreported.png：该 V12 样机的设置页所有能力均为「未上报」，台账 acts 也全为 unknown —— 这台设备本身不上报电量/固件，界面按契约显示未上报而不是 0/--。
- 局限：切换的目标机 V11 停在「正在连接」未达 READY（样机不可连），因此「切换后新设备 READY 并带电量」仍只有单测证据（openingAnotherDeviceFirstDisconnectsAndArchivesTheCurrentOne）。

第二十轮（设备页重设计：标题栏 + 能力入口）验证：

- 架构：新增领域 DeviceEntry + ListDeviceEntries（按 biz 目录分区聚合，GATE 不计入）；界面 DeviceHomeUiState/Mapper/ViewModel/Page 取代原 DeviceRoster* 页面与行视图，DeviceRosterUiMapperTest 删除、新增 ListDeviceEntriesTest 与 DeviceHomeUiMapperTest。
- 真机（NEPI4E0106）： 6-home-connected-entries.png —— 标题「Vidda V12耳夹式耳机」、副标题「74:D0:82:7E:12:A7 · HS02」，正文 6 张能力卡（音乐与音效 5 / 控制设置 9 / 功能设置 4 / 双连接 2 / 录音 4 / 关于设备 3）；换到 HS01 时同一页面显示 5 张卡（无「双连接」），证明入口完全由机型目录决定。
-  7-dropdown-default-style-green-dot.png：YoUI 默认菜单样式（带分割线），每项「名称 + MAC · 型号」，已连接那台名称前是绿色圆点。
-  8-home-disconnected-empty.png：断开后标题回落「设备」、无能力卡、空态文案提示从下拉选择已保存设备。
- 菜单名称在超长设备名下会省略（默认样式把 info 放右侧同一行）；如需完整名称可改两行版式。
- 汇总：**68 suites / 488 tests / 0 failures**；:app:assembleDebug 成功。

第二十一轮（下拉菜单与标题栏根因修复）验证：

源码依据（Android-YoUI/youi_kit/.../navigation/menu）：

- MenuItemView.bind：主文案 setSingleLine(true) + ellipsize END；labelInfo 是**同一行右侧**的次要 TextView（leftMargin + WRAP_CONTENT），库内没有副标题槽位。
- MenuView.addItemList：if (item.getCustomView() != null) { addView(...); lastDivider = null; continue; } —— customView 会跳过自动分隔线，这就是上一版“没有分割线”的直接原因。
- MenuItemView 的 endIcon 走 end.setColorFilter(iconColor)，资源图标会被菜单图标色染色；startIconTinted(false) 只对 Drawable startIcon 生效。所以原生菜单无法把绿点保持绿色。

处置：

- 标题栏：下拉箭头从 AiHeaderActionButton（40dp 白色圆钮）改成 24dp 内联箭头，放进「名称行」里紧跟标题；⋯/+ 单独成组在右侧。失衡根因是上一版让标题列 weight=1 把箭头推到按钮组里。
- 菜单：改用 customView 行——主标题（设备名）+ 副标题（MAC · 型号）+ 右侧绿点；分隔线自行补画，几何按默认行复刻（左右 16dp、最小高 48dp、线两端缩进 16dp、色取主题 divider），因为库在 customView 分支不画线。
- 真机证据： 9-header-balanced-disconnected.png、10-menu-title-subtitle-divider.png、11-menu-green-dot-right.png、12-home-connected-balanced.png。
- 汇总：**68 suites / 488 tests / 0 failures**。

第二十二轮（组件层修复：菜单主副标题与尾部图标着色）验证：

证据链：

1. 缺陷定位（源码）：MenuItemView.bind 主文案 setSingleLine(true)+ellipsize END、labelInfo 为同行右侧次要文本；MenuView.addItemList 对 customView 执行 lastDivider = null; continue; 跳过自动分隔线；endIcon 恒 setColorFilter(iconColor)，startIconTinted(false) 只覆盖 startIcon(Drawable)。
2. 组件修复（Android-YoUI，3 个文件 +86/-2）：
   - YoMenu.Item：新增 subtitle(CharSequence)、endIconTinted(boolean) 及访问器；
   - MenuModel.MenuItem：新增 subtitle/endIconTinted 字段，保留旧构造器委托，rom() 透传；
   - MenuItemView.bind：有副标题时按两行渲染（主文案 ITEM_TEXT_SIZE + 副标题 LABEL_TEXT_SIZE、palette.label() 色），无副标题保持原单行行为；endIcon 仅在 isEndIconTinted() 时着色。
3. 产物替换：:youi_kit:assembleRelease → youi_kit-release.aar，替换 pp/libs/youi_kit-1.0.0.aar；sha256 前 F9F337A8…3582 → 后 7F851098…5391，原件留档 docs/architecture/2026-11-device-mgmt/youi_kit-1.0.0.before.aar。
4. 应用层不再打补丁：删除自定义菜单行、手画分隔线、额外 pressable；菜单直接用 Item.subtitle(...) 与 endIcon(...).endIconTinted(false)，分隔线与按压反馈由组件自身提供。
5. 标题栏：左侧设备信息（名称 + MAC·型号）后紧跟圆形 Title Button（AiHeaderActionButton），⋯/+ 单独在右侧。
6. 真机证据：13-header-circular-title-button.png、14-menu-native-subtitle-divider.png、15-menu-native-green-dot-right.png、16-home-connected.png。
7. 汇总：**68 suites / 488 tests / 0 failures**；:app:preBuild 与 :app:assembleDebug 通过。

第二十三轮（标题组件化 / 名称完整 / 状态点尺寸）验证：

- 结构拆分（严格 MVVM 的视图层解耦）：DeviceTitleView（设备信息 + 圆形下拉钮，整体可点击、整体作锚点）、DeviceEntryCardView（单张能力卡）；DeviceHomePage 只做装配与状态分发。菜单里的「重命名 / 忘记」从标题栏按钮移入设备菜单（分隔线之后、忘记为 destructive），标题栏右侧只留「添加设备」。
- 名称显示不全的根因：信息列是 WRAP，宽度被较短的副标题（MAC·型号）反向约束，长名称提前省略。修复方式：DeviceTitleView.render 按屏幕宽度减去页边距/下拉钮/添加钮算出 maxWidth 并设给标题与副标题，短名称仍自然贴合。
- 锚点：YoMenu.setAnchorView(titleView.anchor())，点名称区域即可展开（真机 18-menu-anchor-from-info-area.png）。
- 状态点尺寸：根因在组件层——MenuItemView 的 endIcon ImageView 默认 FIT_CENTER，会把 6dp 图形放大到 20vp 图标槽。修复：组件内对 endIcon 设 ScaleType.CENTER（按自身尺寸居中）；AAR 重建后 sha256 94935DC7…21C2。
- 真机证据：17-title-component-full-name.png（完整名称 + 副标题 + 圆形按钮）、18、19-menu-small-status-dot.png（右侧小绿点）。
- 汇总：**68 suites / 488 tests / 0 failures**；:app:preBuild 与 :app:assembleDebug 通过。

第二十四轮（重命名/忘记归入「关于设备」）验证：

- 设备下拉恢复为“只列设备”：菜单仅设备项（名称 + MAC·型号 + 连接绿点），不再有管理动作（真机 21-menu-devices-only.png）。
- 「关于设备」新增 DEVICE_MANAGEMENT 行（APP 级，非 biz 能力）：SettingsSectionFactory.deviceManagementRow() 由 DeviceSettingsUiMapper 在 ABOUT 分组追加；ActionCardView 渲染为「设备管理」卡内的「忘记此设备」（破坏性样式 + 二次确认）。
- 忘记链路：DeviceSettingsViewModel.forgetActive() → ForgetDevice（先断活动会话、再从台账移除）→ 事件 Closed → DeviceSettingsActivity.finish()；台账缺失时发 RosterMissing 并提示。
- 重命名去重：设备名重命名仍由 biz 的 RENAME 能力行承担（「设备名称 / 重命名设备」），APP 不再提供第二个同名按钮，避免“改的是设备还是台账”分不清。
- 真机证据：20-about-device-management.png（设备管理 → 忘记此设备）、21-menu-devices-only.png。
- 汇总：**68 suites / 488 tests / 0 failures**；:app:preBuild 与 :app:assembleDebug 通过。