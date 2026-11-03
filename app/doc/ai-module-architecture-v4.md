# AI 模块整体架构（v4：统一数据源 + 严格 MVVM）

> 目标：AI 功能的所有**读写只有一个数据源**，所有页面只通过 ViewModel 的 UiState 观察数据，
> View 不再持有或推导任何业务数据。本文先给出现状盘点，再给出目标边界与已落地/待迁移清单。

## 1. 现状盘点（数据源与持有者）

| 数据 | 持久层 | 当前内存持有者 | 消费者 |
| --- | --- | --- | --- |
| AI 配置（平台、文本选择、语音预设、偏好） | `settings.json` + 加密 `secrets.json`（`AiSettingsRepository`，应用级单例） | 仓库 `current`；`AiChatSession.snapshot`；`AiSettingsViewModel.latest`；`AiPlatformViewModel.settings`；`RealtimeVoiceSession` 每次 `load()` | 对话页 / 设置页 / 平台页 / 通话页 |
| 对话（当前会话、历史摘要） | `conversations/*.json` + `index.json`（`AiConversationRepository`） | `AiChatSession.conversation`；`AiHistoryViewModel` 独立读 `ListConversations` + `revision` | 对话页 / 历史面板 |
| 设置页分段 | `SharedPreferences`（`AiUiPreferences`） | `AiSettingsViewModel.displayMode` | 设置页 |
| 通话运行态 | 无（内存） | `AiVoiceSnapshot` | 通话页 |

结论：**同一个 settings 文档被四处各自持有快照**；历史列表与当前会话各自维护一份状态。
这正是"改完设置，另一页还是旧值""保存状态闪一下""首次切换重建"类问题的根源。

## 2. 目标：一个数据源、三层边界

```
View（Activity/Screen/Card）
  │ 只读 UiState / 只发意图（无 domain 类型、无仓库、无 mapper）
  ▼
ViewModel（每屏一个）
  │ 唯一的 UiState 生产者；只依赖数据源端口 / 用例
  ▼
AiDataSource（应用级，唯一数据源）
  ├ AiSettingsStore      当前配置 + 原子 update + observe
  ├ AiConversationStore 当前会话 + 历史摘要 + observe
  └ AiUiPreferences     视图偏好（不属于配置文档）
  ▼
Repository / FileStore / Gateway（持久化与外部 IO，只有装配根认识实现）
```

规则：

1. **单一写入口**：任何配置变更都必须经过 `AiSettingsStore.update(change)`；页面不得维护第二份草稿。
2. **单一读入口**：所有页面只 `observe` 数据源；不允许各自 `load()` 后长期持有副本。
3. **UiState 不含 domain 类型**：UiState 只放 UI 自己的枚举/record/字符串；domain→UI 由 mapper 完成。
4. **View 不 import domain**：Activity/Screen/Dialog 只接收 UiState 或 UI 类型；不得持有 `AiSettings`/`AiInteractionMode`/`AiPlatformTemplate`。
5. **ViewModel 不做持久化细节**：节流/合并/写盘属于数据源；VM 只发变更并观察结果。
6. **View 不直接调用 usecase/repository**：只能通过 ViewModel。

## 3. 本轮已落地（代码）

1. **设置数据源统一到 `AiSettingsRepository`**
   - 删除 `AiSettingsViewModel` 的内部草稿与 `AiDebouncer`（含 `HandlerAiDebouncer`、测试替身）。
   - 所有操作改为 `SaveAiSettings.execute(AiSettingsChanges.xxx(...))`：仓库内存同步提交并通知，
     UiState 从同一条通知重算；磁盘写由仓库串行执行器合并。
   - 结果：对话页 `AiChatSession`、设置页、平台页、通话页看到的是**同一份 settings 当前值**，
     不再有"设置页草稿已变、其它页仍是旧值"的窗口。
2. **配置变更规则统一到 `AiSettingsChanges`**（纯函数），本地与落盘共用同一规则，单字段 usecase 删除。
3. **`AiSettings.mode` 从配置文档移除**（JSON v4），分段只走 `AiUiPreferences`。
4. **语音设置展示修正**：未拉取模型时 `realtimeModelId` 为空，界面显示"尚未拉取模型"，
   不再把厂商预设伪装成已选模型。
5. **`AiSettingsUiState` 持久化状态移出**，保存状态走独立 `saveStatus()`，不触发整页重绑。

## 4. 待迁移（下一阶段，接口已定）

### 4.1 对话数据源统一到 `AiChatSession`
- `AiChatState` 增加 `List<AiConversationSummary> summaries`；`AiChatSession` 在启动、
  保存/删除/改名/清空后刷新并发布。
- `AiHistoryViewModel` 删除 `ListConversations`/`revision`/自己的 `io`，只映射 `AiChatState.summaries()`。
- 收益：历史面板与对话页共享同一份会话状态；不再有两套列表缓存和 revision 协议。

### 4.2 UiState 去 domain 化
- 设置：`AiInteractionMode` → `SettingsModeUi`；`AiSettingsPersistenceStatus` → `SaveStateUi`；
  `AiPlatformTemplate` → `PlatformTemplateUi`。
- 通话：`AiCallUiState` 去掉 `AiVoiceSnapshot`/`AiFailure`，由 `AiCallUiMapper` 落成纯 UI 字段。
- 历史：`AiHistoryUiState` 只保留 UI 记录（mapper 已具备）。
- View/Activity/Dialog 的 domain import 清零，由 ViewModel 暴露 UI 类型。

## 5. 验收口径

- 任意页面修改配置后，切到另一个页面无需重新进入即可看到同一值（同一数据源）。
- 任意页面 `View` 文件不出现 `import com.ggec.earbuds.domain.ai.*`（mapper 除外）。
- 任意 `*ViewModel` 不出现 `new` 具体 `data.*` 实现；只依赖端口/用例。
- 设置页切换分段不写 `settings.json`；保存状态不进 UiState；无"保存中"闪烁。
