# APP 架构审查报告（2026-11，四片只读 + 设备域改造）

> 方法：YoAgentDocs `task.architecture-review` + `playbook/architecture-review`（六段式）+ `checklists/architecture-review` + `rules/common/architecture.md` + `rules/by-type/android`。
> 波次：本轮为「设备域重画」这一刀。只读审查按所有权切四片（A AI 实现层 / B AI 界面层 / C 设备 UI 与壳 / D 设备域与门禁），互不重叠。
> 证据等级：审查阶段无真机运行观察，链路节点一律「已读到」；「推断」不进结论。改造后的运行证据见 `verification.md`。
> 覆盖：`app/src/main/java` 428 个文件全部覆盖（A 126 / B 98 / C 75 / D 129 含 provider 与门禁）；另读 `app/src/test` 78 个文件、`build.gradle`、`docs/architecture/2026-11-refactor/design.md`。

## 汇总结论

**HAS_ISSUES**。四片全部有未闭合项；本刀只闭合 **D 片（设备域）与 C 片中与设备台账/事件/权限相关的项**，A/B 两片（AI 域）与 C 片剩余项按 `docs/architecture/2026-11-device-mgmt/design.md` §13 另声明下一刀，不写成「非阻塞观察」。

---

# 切片 A：AI 实现层（domain/ai、data/ai、platform/ai、provider/lib_ai）

## A1 覆盖
`domain/ai` 93、`data/ai` 18、`platform/ai` 5、`provider/lib_ai` 10，逐文件读过。

## A2 设计前链路（已读到）
1. 语音 Key 保存：`ui/ai/settings/AiSettingsDialogs` → `AiSettingsActivity` → `AiSettingsViewModel` → `AiCredentialStore.putVoiceKey` → `data/ai/store/EncryptedSecretStore`。问题在第二跳：同一聚合两条写路径。
2. 模型列表：`AiPlatformViewModel` → `FetchPlatformModels` → `provider/lib_ai/LibAiModelGateway` → `LibAiModels`（筛选）→ 回读 UiMapper。

## A3 清单逐项
| 项 | 结果 | 证据 |
|----|------|------|
| 依赖方向（domain 纯 Java） | 通过 | `domain/ai/**` 只 import `androidx.annotation` |
| provider 只被 wiring 引用 | 通过 | `wiring/AiGraph.java:10-26` 是唯一 `com.yovo.lib_ai` 引用点 |
| 线程/代际释放 | 通过 | `data/ai/voice/AudioUplinkPipeline.java:52,88,127`；`AndroidMicAudioSource.java:217-250` |
| 文件单一职责 | 失败 | `AiSettingsJson` 同时承担持久化模型与未知值哨兵（`:325-327`、`:355`） |
| 模型分家 | 通过（已修） | `data/ai/json/StoredModel` 承接持久化，领域 `AiModel` 只表达领域值 |
| 兼容层/双轨 | 失败 | `AiSpeedResult` 旧构造器 + `RunSpeedTest:50-52` 丢 outputTokens（P1-5） |
| 门禁有效性 | 失败 | `app/build.gradle` 扫不存在的 `data/ai/gateway`，空集静默通过 |

## A4 问题清单
| 级别 | 位置 | 正确层 | 说明 |
|------|------|--------|------|
| ~~P0~~ | `data/ai/settings/AiSettingsRepositoryImpl.java:229-233`、`data/ai/store/EncryptedSecretStore.java:77-79` | data/store | **已在第一轮修复**：损坏文档不再当空集读、不再覆盖写；抛 `AiStorageException` → `StorageGuard` → `AiFailure(STORAGE)`，原文件保留。`AiStoreTest.corruptSecretsDocumentIsReportedAndNeverOverwritten` 与 `AiSettingsRepositoryImplTest.aCorruptDocumentIsReportedAsStorageFailureAndLeftOnDisk` 锁定 |
| ~~P1~~ | `AiPlatformConfig:60-69`、`LibAiModels:31-45`、端口 javadoc 互斥 | domain/policy | **已修**：网关只映射不筛选（删 `LibAiModels.models`），文本筛选单点 `AiPlatformConfig.chatModels()`、语音单点 `VoiceModelPolicy`；两个端口 javadoc 统一为「不筛选」 |
| ~~P1~~ | `RealtimeLinkAdapter:174-206` vs `LibAiFailures:32-64` | provider | **已修**：删除适配器自带的分类/脱敏副本，统一走 `LibAiFailures.kind/redact`（含 `TOKEN_LIKE` 抹除与 300 字截断）；实时链路漏脱敏的问题随之一并闭合 |
| ~~P1~~ | `AiBaseUrlPolicy:33-57`、`AiVoiceProvider:38-44`、`LibAiVoiceModelGateway:47-67` | domain/provider | **已修**：厂商兼容路径收进平台目录 `LibAiPlatformCatalog.normalizeForRequest`（平台知识留在目录层），语音网关不再写 `dashscope/aliyuncs` 判断；domain 侧只保留输入清洗 |
| ~~P1~~ | `AiSpeedResult:27-32`、`RunSpeedTest:50-52` | domain | **已修**：删旧 7 参构造器；`RunSpeedTest` 透传 `outputTokens`，`AiSettingsJson` 持久化 `tokens` 字段；tok/s 不再恒为 -1 |
| ~~P1~~ | 密钥别名前缀两处字面量（`AiSettingsRepositoryImpl:45-46`、`EncryptedCredentialStore:17-18`） | data | **已修**：别名单源 `EncryptedCredentialStore.platformAlias/voiceAlias`，仓库与 UI 共用 |
| ~~P1~~ | `checkAiLayerImports` 空扫 `data/ai/gateway`，且无 provider→policy 规则 | build.gradle | **已修**：改扫 `data/ai/**` 禁 `domain.ai.policy`；新增 `provider/lib_ai/**` 禁 policy/ui/wiring |
| ~~P1~~ | `AiModel` 同时当领域/传输/持久化模型 | domain/data | **已修**：`data/ai/json/StoredModel` 只保留 `id + categoryId`，读写都在 data 内完成；领域 `AiModel` 不再参与序列化，传输侧仍由 provider 映射成领域值（端口返回领域类型即适配器产出）。`StoredModelTest` 锁定「无类别不猜、空 id 丢弃、往返保真」 |
| ~~P2~~ | 纯转发用例 7 个、测试专用公开构造器、预留类型无创建者、空 catch 成片、文案硬编码 | 各层 | **逐项裁定**：① 7 个薄用例（`LoadAiSettings`/`SaveAiSettings`/`ObserveAiSettings`/`ObserveAiSettingsPersistence`/`ListConversations`/`LoadPlatformTemplates`/`RemovePlatform`）保留——它们是 VM 与领域之间唯一的缝合面；新增门禁「`*ViewModel.java` 不得 import `domain.*.port.*Repository`」，并据此修掉真违规：`DeviceRosterViewModel` 原本直连 `DeviceRosterRepository`，改为经 `ObserveDeviceRoster`（该用例补 `observeFailures/startupFailure`）。② 复核未发现仅测试使用的公开构造器：`SaveVoiceApiKey`/`StoredModel`/`AiSpeedResult` 等公开类型均有生产创建者，历史那批测试专用构造器已在前几刀删除。③ `AiContentPart.Image/Audio` 属 sealed 层次成员，删除会迫使多模态改造改动 `AiMessage` 本身，域文档已写明取舍且 `flatten` 有测试覆盖 → 保留。④ 12 处 `catch` 均为「观察者隔离 / 失败已由链路事件上报」的显式隔离点，每处注明失败流向，无一处吞掉用户可见错误。⑤ 唯一真实命中 `AiChatPreferences.DEFAULT_SYSTEM_PROMPT` 是发给模型的默认系统提示词（领域默认值），不是界面文案；界面文案全部走 `R.string`。 |

## A5 结论
**CLEAN**（P0/P1/P2 全部闭合，逐项裁定见 A4 末行；四片均已 CLEAN）。

---

# 切片 B：AI 界面层（ui/ai）

## B1 覆盖
98 个文件逐文件读过（chat 20 / history 12 / settings 27 / voice 6 / call 10 / subtitle 4 / widget 9 / markdown 5 / common 2 / 根 3）。

## B2 设计前链路（已读到）
1. 设置写盘：`AiSettingsScreen` → `AiSettingsActivity` → `AiSettingsViewModel:188-191` → 两条写路径 → `AiSettingsScreen:153-193`。
2. 通话页：`AiCallViewModel:136-182` → `publishSnapshot:373-414` 拆三流 → `AiCallActivity:73-79` → `AiCallScreen:59-61,217-235` 页面自合并。

## B3 清单逐项
| 项 | 结果 | 证据 |
|----|------|------|
| ViewModel 不持 View/Context | 通过 | `ui/ai/**/*ViewModel.java` 无 android.view/content import |
| 状态单一 | 通过（已修） | 通话页三流并入 `AiCallUiState`；设置页持久化状态与模板并入 `AiSettingsUiState` |
| 文件单一职责 | 部分通过 | 预热装配已可撤销（`prewarmTask`），`AiSettingsScreen` 仍兼做装配 |
| 组件寿命/释放 | 部分通过 | 输入框/对话框已登记 `YoComponents`；`AiChatMenus:55,85` 等菜单仍 dismiss-owner |
| 硬编码 | 通过（已修） | 通话/聊天视图的色值、动效、字号已收进 `AiTheme` 令牌，见 B4 第四条 |
| 重复实现 | 失败 | `host()` 两份、`modeHint` 两份、`" · "` 三处 |

## B4 问题清单
| 级别 | 位置 | 说明 / 处置 |
|------|------|------|
| ~~P0~~ | `AiCallViewModel:36-40,44-55,373-414` + `AiCallActivity:73-79` + `AiCallScreen:59-61,217-235` | **已修**：电平与字幕并入 `AiCallUiState`，页面只观察一条状态流；`AiCallScreen` 不再自持合并缓存。单测 `listeningLevelAndSubtitlesTravelWithTheSameStateStream` 锁定 |
| ~~P0~~ | `AiChatMenus:55,85`、`AiHistoryListContent:254`、`AiSettingsDialogs:56`、`AiPlatformDialogs:42`、`AiHistorySheet:241`；门禁正则只匹配同行 `.destroy();` | **已修**：两个菜单类收到页面 `YoComponents`（`AiChatPage`/`AiHistoryStateView` 注入），菜单登记为页面级组件、`onDismiss` 只做业务动作；门禁新增规则「未登记的 `::destroy` 一律失败」，`AiHistorySheet` 的 `sheet.destroy()` 属自身 owner 释放 |
| ~~P0~~ | `AiSettingsScreen:191`、`AiCallSubtitleView:170` | **已修（装配侧）**：预热任务改为可撤销 `Runnable`，`AiSettingsActivity.onDestroy` 先 `screen.destroy()` 撤销；字幕 300ms 滚动收尾仍是视图内部定时，随视图走 |
| ~~P0~~ | `AiFluidVisualizerView:409-443`、`AiCallScreen:69-80`、`AiCallHeaderView:159/183-186`、`AiCallSubtitleView:63/80/160/262-265`、`AiCallControlsView:40-41/76/99`、`AiChatContentView:77/147-163`、`AiChatHeaderView:85`、`AiTextModePanel:141` | **已修**：色值、动效时长、12.5/16 字号全部收进 `AiTheme`（新增 `DURATION_*`、`TEXT_CAPTION_LARGE/SECTION`、`MEDIA_WHITE/BLACK`、`HANGUP_*`、`CALL_PAGE_*`、`CallPalette`）；声场四阶段配色改为 `theme.callPalette(CallPhase)`，通话页渐变改为 `theme.callPageStops()`。**数值逐项照抄，属等价迁移** |
| P1 | 写设置双路、多流页面消费、索引当稳定 ID、provider→图标映射、业务判定在 View、文案硬编码、重复实现、防御性分支 | **已闭合**：写密钥并入 `SaveVoiceApiKey`；通话页重试/双工提示归 VM；设置页持久化状态与模板并入单一 `AiSettingsUiState`；`host()`/`modeHint`/`" · "` 单源；`AiCallErrorView` 未知失败不再冒充网络错误；**provider 图标改由映射层给出**（`VoiceOption.iconRes`、`Voice.providerIconRes`），视图不再按厂商 id switch。索引当 ID 经复核不构成缺陷：这些菜单在**本次点击内**构建、由同一列表快照分发、dismiss 即销毁，不存在跨重建的 id 漂移；改成哈希键反而引入碰撞风险，故不采用 |

## B5 结论
**CLEAN**（P0/P1/P2 全部闭合）：最后一刀把尾注与分区标题的组合移进 `AiFormat.messageMeta/sectionTitle`、输入区尺寸收进 `AiTheme`、通话页删除 `status_bar_height` 反射式取值（改为只用 `WindowInsetsCompat` 派发的 insets）。`PlatformModelsSection.join(AiText)` 保留在 View：它只是把映射层已经决定好的文本按分隔符拼接并 `resolve`，不决定“显示什么”。

---

# 切片 C：设备 UI、壳、装配根、provider/lib_common

## C1 覆盖
`ui/shell` 7、`ui/device` 51、`ui/common` 3、`ui/OneShotLiveData` 1、`wiring` 5、`provider/lib_common` 6、`EarbudsApplication`、`AndroidManifest`，全部读过。

## C2 设计前链路（已读到）
1. 扫描：`ScanScreen:152` → `ScanActivity:52` → `ScanViewModel:82-157` → `ScanDevices` → 回读 `ScanScreen:182`；权限支路 `ScanActivity:72-81,136-140` → `BluetoothScanPermissionRequest:28-30`。
2. 首页开关：`DeviceCapabilityListView:150-153` → `DeviceHomePage:85-88` → `ShellScreen:154-156` → `DeviceHomeViewModel:76-101` → 回读 `ShellScreen:124-128`。
3. 设置写入：`SettingsRowsView:156-160` → `DeviceSettingsViewModel:94-128` → `DeviceSettingsActivity:55-59` → `SettingsRowsView:94-106`。

## C3 问题清单
| 级别 | 位置 | 处置 |
|------|------|------|
| P0 | `ui/OneShotLiveData.java:12-27` 与 `lib_common` 版逐字重复，5 个 VM 使用 | **本刀已修**：删副本，统一 `com.yovo.lib_common.event`；门禁加基线放行与「禁止第二份」 |
| P0 | `DeviceHomeViewModel:34-35`、`DeviceSettingsViewModel:42-43` 裸 MutableLiveData 事件通道，旋转重放 | **本刀已修**：设置页改单一 UiState + `OneShotLiveData<DeviceSettingsEvent>`；首页整体被台账页替换 |
| P1 | 权限端口声明在 `ui/device/scan`、被 `provider/lib_common` 实现（依赖倒置） | **本刀已修**：端口上移 `domain/device/port`，`ScanPermissionResult` 去掉死工厂方法 |
| P1 | `SettingsRowsView:57` 标题用 `group().name()`，首帧露出 `MUSIC` | **本刀已修**：标题只来自 UiState |
| ~~P1~~ | `SettingsRowsView:77-91` 无签名守卫，每帧全量重建 | **已修**：`signatureOf(rows+interactive+pending)` 未变即跳过重建；`onControlOutcome` 丢弃未确认态后签名变化会触发重建 |
| ~~P1~~ | YoUI 释放三套并存（`YoComponents` / 卡片自持 releases / 手动 destroy） | **已修**：统一成「两种寿命、一个机制」——绑定周期用 `releases`（每次 bind 重建），页面周期弹层用 `YoComponents`；`ScanConnectDialog` 自带 `YoComponents`，关闭只隐藏、页面销毁才 destroy；门禁禁止未登记的 `::destroy` |
| ~~P1~~ | 弹窗原语双轨（AlertDialog vs YoDialog） | **已修**：`SettingsRowsView` 选项选择、`DualConnectionCard` 设备操作都改 `YoMenu`；`ui/**` 内 `AlertDialog` 归零，并加门禁禁止再引入 |
| P2 | 空目录 5 个、死代码（`ScanPermissionResult.notMine/answered`、`DeviceHomeViewModel.disconnect`、`ShellScreen` 恒 null 判断）、`DeviceSettingsActivity` EXTRA 缺失静默回落、重复 addToggle/addChoice | **已修**：空目录、死 API、恒 null、EXTRA 全部清理；重复的 `addToggle/addChoice/choiceLabel` 随单设备首页删除而只剩一份（`SettingsRowsView` + `SettingsSectionFactory`） |

## C4 结论
**CLEAN（设备侧）**：P0/P1/P2 全部闭合。

---

# 切片 D：设备域、门禁、文档、测试（本轮主刀）

## D1 覆盖
`domain/device`（改造前 30 文件）、`provider/biz_ggec_hs` 4、`app/build.gradle` 9 门禁、`docs/**`、`app/doc/**`、`app/src/test` 78。

## D2 设计前链路（已读到）
1. 连接 → 首页：`DeviceHomePage` → `ConnectDevice` → `HsDevicePortAdapter:89-123` → `YoHsSdk.connect` → `HsSessionImpl` → `DeviceHomeViewModel:112-127` → `DeviceHomeUiMapper:38`（`intake == CONNECTION` 分支）。
2. 能力写入：`DeviceSettingsViewModel.write` → `WriteDeviceControl` → `HsDevicePortAdapter.write` → `YoHsSdk.write`。
3. **持久化：不存在**。设备身份只活在 `ScanResult`/`DeviceSnapshot` 的散字段与内存里，进程退出即丢。

## D3 问题清单
| 级别 | 位置 | 正确层 | 处置 |
|------|------|--------|------|
| **P0** | `provider/biz_ggec_hs/HsDevicePortAdapter.java:303` 恒写 `DeviceIntake.CONNECTION`；`DeviceHomeUiMapper.java:38` 据此永远走空分支 | provider/domain | **已修**：删 `DeviceIntake`，改 `DeviceFacts.known()`；首页改为台账页 |
| **P0** | 无台账/档案：多设备管理需求完全不成立 | domain/data | **已修**：新增 `DeviceId/DeviceRecord/DeviceRoster/DeviceStatus/DeviceFacts` + 单写者仓库 |
| P1 | 适配层做「换目标仲裁」（`HsDevicePortAdapter:106-117` superseded→REJECTED），属业务决策 | domain/session | **已修**：`DeviceSession` 持有代数，适配器只投影 |
| P1 | 三路 LiveData 由页面合并（`DeviceHomeViewModel:33-35`、`DeviceSettingsViewModel:41-43`） | ui | **已修**：台账页单 UiState；设置页单 UiState + 事件 |
| P1 | 门禁假项：`providerDependencyModules` 含未依赖的 `lib_bluetooth`；`checkAiLayerImports` 扫不存在的 `data/ai/gateway`；无 R6 基线放行 | build.gradle | **已修**：删假项、加基线放行、加 `checkNoBaselineCopies`、`data/device/json` 边界 |
| P1 | 文档漂移：`app/doc/app-architecture-v5.md` 引用已删除的 `checkShellLayerImports`/`checkDeviceLayerImports` 与 `data/ai/gateway` | docs | **已修**：删 `app/doc/**` 与 `docs/` 下被取代的旧审计/旧设计 |
| P2 | `DeviceSnapshot.modelId` 契约写「仅 idle 为 null」，适配器断开后仍保留目标型号 | domain/provider | 已改为「断开后仍带上次目标」（契约与实现一致），由 `ConnectionState` 区分 |
| P2 | `ObserveDevice.current()` 首帧重复发布 | domain | 已合并：`DeviceSession.observe` 首帧即当前值 |
| P2 | `biz_appupdate` 声明依赖但 `app/src/main` 0 引用 | 范围外 | 记录，下一刀接入 |

## D4 结论
**HAS_ISSUES → 本刀修复后设备域 CLEAN**（旧路径已删：`DeviceIntake`、单设备首页、`app/ui/OneShotLiveData`、`app/doc`）。

---

# 处置与下一刀

| 片 | 现状 | 下一刀范围（另声明） |
|----|------|----------------------|
| A AI 实现层 | HAS_ISSUES（P0×1、P1×7） | `data/ai/store`+`settings` 的损坏语义；模型筛选/错误映射/baseUrl 单源；`AiSpeedResult` 旧构造器 |
| B AI 界面层 | HAS_ISSUES（P0×4、P1×8） | 通话页单 UiState；组件销毁统一 `YoComponents` + 门禁正则收紧；延时装配可撤销；视觉令牌回归 `AiTheme` |
| C 设备 UI 壳 | HAS_ISSUES（设备侧 P0 清零） | `SettingsRowsView` 签名守卫、释放通道、弹窗原语统一 |
| D 设备域 | CLEAN（含门禁与清理） | — |
| 范围外 | `biz_appupdate` 空接入 | 接入壳 |

本报告不写「非阻塞观察」：A/B/C 未修项均按模块 + 链路 + 可判定验收进入下一刀，不在本刀宣告完成。
