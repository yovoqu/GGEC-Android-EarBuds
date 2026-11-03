# AI 设置页状态机与保存策略（架构重构）

> 背景：两种模式（文本 / 语音）每次切换都会走一遍 `AiSettings` 文档保存，
> 页头闪一下"正在保存…"，分段点击明显卡顿。本文记录根因与重构后的架构。

## 1. 根因（旧链路）

一次分段点击：

```
YoSegmentButton.onSelectionChanged
  → ViewModel.selectMode
    → SelectInteractionMode.execute
      → AiSettingsRepository.update           // 同步：内存提交
          ├─ notifyObservers(settings)        // 同步回调 → mapper.map → postValue → render ①
          ├─ notifyPersistenceStatus(SAVING)  // 同步回调 → publish → render ②
          └─ enqueuePersistence → 后台 fsync 写整份 JSON
                └─ notifyPersistenceStatus(SAVED) // 后台回调 → publish → render ③
```

三个结构性错误：

1. **视图偏好写进了配置文档**：当前分段只影响设置页展示，却被存进 `AiSettings.mode`，
   每次切换触发整份 JSON 的原子写 + `fsync`。
2. **持久化状态塞进 UiState**：`SAVING / SAVED` 各产生一份新 `AiSettingsUiState`，
   每次切换把整页重新 map、重绘三遍；`saveHint` 还在正文流里把内容推上推下。
3. **UI 状态依赖仓库回显**：界面要等 `repository.update → observe` 才更新，链路长且必然重复。

## 2. 重构后的分层

```
UI (AiSettingsScreen / VoiceSettingsView)
   │  只吃 UiState，发意图
   ▼
AiSettingsViewModel  ← 状态机：本地草稿 + 节流保存 + 视图偏好
   ├─ AiUiPreferences（新）       当前分段（SharedPreferences.apply，不 fsync）
   ├─ SaveAiSettings（新用例）     静默窗口后一次性写仓库
   ├─ FetchVoiceModels            网络拉取（写仓库后由 observe 回填草稿）
   └─ AiSettingsUiMapper          纯函数：AiSettings + mode → UiState
   ▼
domain ports（AiSettingsRepository / AiUiPreferences）
   ▲
data / platform 实现（AiSettingsRepositoryImpl / SharedPreferencesAiUiPreferences）
```

### 2.1 三条解耦

| 解耦 | 旧 | 新 |
| --- | --- | --- |
| 模式 ⊥ 配置 | 切分段写 `AiSettings.mode` + 整份 JSON | `AiUiPreferences`（SharedPreferences.apply），设置文档完全不动 |
| 状态 ⊥ 持久化 | `persistence` 是 UiState 字段，SAVING/SAVED 各重绘一次 | UiState 不含持久化；独立 `saveStatus()` 只把 `FAILED` 送到页头 |
| 草稿 ⊥ 仓库回显 | 每次操作等 observe 回调再更新 | 先改本地草稿并立即发布；自己落盘的回显与草稿相等，直接忽略 |

### 2.2 状态机

```
Idle(未开始)
  ├─ loadAsync 成功 → Ready(draft=settings, mode=prefs.lastMode(settings.mode))
  └─ loadAsync 失败 → LoadFailed

Ready 上的事件：
  SelectMode(m)       → mode=m; prefs.set(m); publish()          （不写仓库）
  Mutate(change)      → draft=change(draft); publish(); scheduleSave()
  FetchVoiceModels    → fetching=true; publish(); 后台执行; 完成后 fetching=false; publish()
  ExternalSettings(s) → if s == draft: ignore（自己的回显）；else draft=s; publish()
  PersistenceStatus   → FAILED/SAVED 走 saveStatus 通道，不进入 UiState
  FlushNow / onCleared→ cancel debounce + 立即写掉 pending
```

### 2.3 保存策略

- **本地优先**：`mutate` 先在内存草稿上应用纯函数并 `publish()`，用户看到的是零等待的新状态。
- **节流合并**：变更被组合成 `UnaryOperator<AiSettings>`，`AiDebouncer` 用 350ms 静默窗口；
  连续操作（如连点几个开关）只产生一次 `SaveAiSettings.execute`。
- **离开即刷**：`Activity.onStop()` / `ViewModel.onCleared()` 调 `flushNow()`，节流窗口里没写完的
  改动立即落盘，不丢。
- **失败才出声**：正常保存不显示任何"保存中"；只有 `FAILED` 在页头右侧固定状态位显示，
  下一次 `SAVED` 清除。页头 56dp 固定高度，显隐不影响正文。
- **回显抑制**：仓库写入后的 `observe` 回调与草稿相等，直接 return，不再重算界面。

## 3. 面板按需构建

`AiSettingsScreen.render()` 只构建/绑定**当前模式**面板：另一块不创建、不测量。
切换模式时才按需建一次，避免"进语音页顺手把文本页的 YoList 全装配一遍"这类首帧抖动。
（非当前面板为 `GONE`，不参与测量。）

## 4. 验证

- `:app:testDebugUnitTest` 通过，新增/覆盖：
  - `switchingTheModeNeverTouchesTheSettingsDocument`：切模式后 `settings.load().mode()` 不变，
    `AiUiPreferences` 变了，UiState 立即变。
  - `theStoredViewPreferenceDecidesTheOpeningSegment`：重进页面按偏好落在语音段。
  - `aChangeUpdatesUiBeforeItReachesTheRepository` / `consecutiveChangesCollapseIntoOneWrite`：
    本地即时更新、节流后一次落盘。
  - `anInvalidPromptIsRejectedWithoutTouchingTheDraft`：校验失败不污染草稿。
- 真机：切换分段无"正在保存"闪现；`settings.json` 的 `mode` 不再被改写（视图偏好单独存）。

## 5. 首次切换不掉帧（第三轮根因）

去除保存后仍会"首次切换卡一下"，继续沿数据链路排查，剩下的是**视图层的切换成本**：

1. **懒建面板压在点击帧上**：进页面只建当前面板，第一次切到另一模式时，
   `render()` 在点击引发的那一帧同步 `new` 出整棵面板（YoList/多层 LinearLayout）并绑定数据。
2. **`GONE / VISIBLE` 触发整树 measure+layout**：两块面板高度差很大，切换时父级 `FrameLayout`
   重新测量可见孩子，语音面板几十个 TextView/胶囊一次布局就是几十毫秒。
3. **`setSelectedIndex` 回写分段**：状态驱动控件再选一次，多触发一轮选中动画。

处理：

- **切片更新**：`AiSettingsUiState.withMode()` 只换 mode/说明，不重算平台/身份/语音分区。
- **空闲预热**：首帧后在 `contentHost.postDelayed(120ms)` 里构建另一块面板并 `bind` 当前状态，
  同时按真实宽度 `measure + layout` 一次；点击时不再构建、不再首次布局。
- **自绘切换宿主 `ModePanelSwitcher`**：两块面板都保留测量结果，切换只改 activeIndex，
  宿主只测量/绘制当前块，另一块用 translation 移出可视区（不参与高度、不接收触摸），
  避免 `GONE/VISIBLE` 的整树重排。
- **分段单向**：分段只作为输入，不再由状态回写选中项。

真机 `dumpsys gfxinfo`（NEPI4E0106，每次 reset 后测 2s）：

| 场景 | 掉帧帧数 | 99th |
| --- | --- | --- |
| 修复前 首次切换 | 2 帧（3.1%） | 85 ms |
| 修复后 首次切换 | 1 帧（1.4%） | 23 ms |
| 修复后 再次切换 | 1–2 帧 | 23–36 ms |

剩余的一帧来自"一块高内容首次绘制"本身，不再是同步建树/整树测量。

## 6. 兼容层清理（目标架构判定）

完成前两轮后仍不是"最优架构"：还留着几处为兼容旧设计而生的冗余。第三轮把它们一起删掉：

| 冗余 | 旧为什么留着 | 处理 |
| --- | --- | --- |
| `AiSettings.mode` 字段 | 分段曾写进配置文档 | 从领域模型、JSON（v4）、仓储读回全部删除；分段只存 `AiUiPreferences` |
| `SelectInteractionMode` 用例 | 只为把 mode 写回仓库 | 删除 |
| `SaveChatPreferences` / `SelectVoiceModel` / `SaveVoiceApiKey` / `UpdateChatGptRealtime` | 每个字段一个 `repository.update` 包装，和 ViewModel 内联逻辑重复 | 删除；统一收口到 `AiSettingsChanges` 纯函数 |
| `AiSettingsUiMapper.map(settings)` 重载 | 靠 `settings.mode()` 猜展示模式 | 删除重载，强制显式传 `mode`（来自视图偏好） |
| 面板 `setVisible()` | 旧 `GONE/VISIBLE` 时代残留 | 删除；切换宿主 `ModePanelSwitcher` 统一管理 |

现在的边界：

```
AiSettings（纯配置：平台 / 文本选择 / 语音预设 / 偏好）
AiSettingsChanges（每条变更的纯函数，本地与落盘同一份规则）
AiUiPreferences（分段等视图偏好，SharedPreferences.apply）
AiSettingsRepository（持久化文档，只存配置）
ViewModel（草稿 + 节流 + 只读状态机）
ModePanelSwitcher（只测量/绘制当前面板的切换宿主）
```

判定：这才是当前需求下的最简闭环——**配置、视图偏好、持久化状态、渲染路径四者互不穿透**；
没有"为了兼容旧字段/旧用例"而存在的生产代码。


