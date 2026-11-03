# 语音交流模式设置 · UI 重构说明（v2）

> 目标：把"语音交流模式"从一个塞在列表里的下拉 + 几行 Key，重做成一个信息结构清楚、
> 组件可独立演化的设置面板；同时把"拉取模型"从"只留实时模型"改为"拉回全部模型"。
> 本文是重做的依据，实现见 `app/src/main/java/com/ggec/earbuds/ui/ai/settings/voice/`。

## 1. 问题（旧版为什么不够）

旧版 `AiVoiceModePanel` 把 6 件事塞进两张 `YoList`：服务商下拉、接口地址、拉取、实时模型、
方言、Key。具体毛病：

1. **机械化**：全靠系统列表行，行里塞下拉/箭头，能力角标、双行事实都放不下，只能不断拼字符串。
2. **层级平**：服务商（选谁）和方言（线路细节）是同一视觉权重的两行，用户看不出主次。
3. **看不到状态**：只有 Key 行尾一句"未填写"，没有"现在会连哪个模型、能不能用"的整体判断。
4. **拉取被裁剪**：`YoChatGptRealtime.offered()` 只留实时模型，平台没报能力位、名字又不带
   `realtime` 的实时模型会被静默丢掉，用户配好的模型在列表里找不到。
5. **切换成本高**：`YoSelect` 挂在列表行尾、聊天面板重建时会 detach/re-attach，脆弱且难扩展。

## 2. 参考与取舍

| 参考 | 取用的部分 | 不取的部分 |
| --- | --- | --- |
| Material 3 · Settings / Lists | 分组标题 12sp、卡片 16dp 圆角、分组内 1px 分隔线、行 56dp 网格、右侧 chevron | 大面积 tonal 色块（会喧宾夺主） |
| iOS 设置（分组内嵌列表） | 卡片即一组、分隔线左缩进到文字起点、尾部值 + › | 居中大图标 |
| ChatGPT App · 设置/语音 | "当前模型 + 状态"摘要、模型列表按能力打标、选中有明确勾选 | 营销式空状态插画 |
| Claude / Perplexity 设置 | 区块化卡片、次级说明压到 12sp 三级灰 | 多级导航 |
| 本仓库 `ui-design.md` / `ai-module-component-spec.md` | 一个页面只一处主色、卡片是容器不是装饰、字号只用 20/15/13/12 | —— |

结论：语音设置 = **一张身份卡 + 四个分组卡片**（服务商 / 连接 / 模型 / 方言）。
原来下拉里的选项平铺成"选择瓦片"，模型列表用自绘行承载能力角标，不再借用系统列表模板。

## 3. 信息架构

```
语音交流模式面板 VoiceSettingsView
├ 身份卡 VoiceHeroCard          现在会连哪个模型、是否就绪（未配 Key 时整卡可点去填）
├ 服务商 VoiceProviderPicker     ChatGPT / Qwen 两张选择瓦片，各自显示 Key 状态
├ 连接   VoiceConnectionCard     接口地址（仅 ChatGPT，标注 官方/自定义）+ API Key
├ 模型   VoiceModelCard          「拉取/重新拉取」+ 全部模型行（勾选 + 实时/普通角标）
└ 方言   VoiceDialectCard        线路方言 Beta / GA 分段（仅 ChatGPT）
```

页面外部仍由 `AiSettingsScreen` 提供：页头、`文本模式 / 语音交流模式` 分段、当前模式说明行。

## 4. 组件清单与职责

| 组件 | 位置 | 职责 |
| --- | --- | --- |
| `VoiceSettingsView` | voice/ | 面板容器：装配 5 个区块、绑定状态、切可见性；不含业务判断 |
| `VoiceHeroCard` | voice/ | 身份面（品牌渐变 + 声波图标）+ 当前模型 + 就绪胶囊 |
| `VoiceProviderPicker` | voice/ | 两家并排瓦片；选中品牌描边+实心点，未选中浅填充+空心点 |
| `VoiceConnectionCard` | voice/ | 地址行（官方/自定义胶囊）、Key 行（已填/未填） |
| `VoiceModelCard` | voice/ | 区块动作「拉取」；模型行 = 圆点 + id + 实时/普通胶囊；空态一行引导拉取 |
| `VoiceDialectCard` | voice/ | `YoSegmentButton`（Youi Kit，SINGLE_CHOICE/SMALL/DISTRIBUTE） |
| `VoiceCard` | voice/ | 自绘原语：卡片、行、分隔线、胶囊、选中圆点、chevron |
| `VoiceSectionHeader` | voice/ | 区块标题（12sp 三级灰）+ 可选右侧动作 |

状态仍是 MVVM：`AiSettingsUiState.Voice`（不可变）→ `VoiceSettingsView.bind()`。
每个区块只建一次控件、只在自身签名变化时重绑；切换模式只改可见性（`INVISIBLE`），
不重排、不重挂。

## 5. 状态矩阵

| 区块 | 加载 | 空 | 内容 | 异常 |
| --- | --- | --- | --- | --- |
| 身份卡 | 未加载时整块不画 | 未填 Key → `还差 API Key`（警示色，整卡可点） | `可直接通话`（品牌色） | —— |
| 服务商 | —— | —— | 两家瓦片，各自 `已填写/未填写 Key` | —— |
| 连接 | —— | 未填 Key → 尾部 `未填写`（警示色） | `已填写`；地址 `官方/自定义` | 地址非法由弹窗拦截 |
| 模型 | `正在拉取…`（动作禁用） | `尚未拉取` + 引导行（点即拉取） | `实时模型 · N` + 行 | 拉取失败 → toast（沿用 ViewModel 事件） |
| 方言 | —— | —— | Beta / GA 二选一 | —— |

## 6. 视觉与排版口径

- 只新增一个字号档 `TEXT_TITLE = 17sp`（身份卡标题）；其余沿用 20 / 15 / 13 / 12。
- 页边距 20；卡片 16dp 圆角 + 1px 描边；卡片内行高 56dp、左右内边距 16。
- 分隔线左缩进 `16 + 20(图标) + 12` = 48dp，对齐标题文字起点。
- 品牌色只给：就绪胶囊、选中瓦片描边+圆点、`实时` 角标、区块动作文字、chevron 外的可点提示。
- 未填 Key 用 `warning`；不用第二个大面积色块。

## 7. 交互

1. 未填 Key：点身份卡 / Key 行 → Key 弹窗（留空保存 = 清掉这一家的 Key）。
2. 换服务商：点另一张瓦片 → 只换"用哪家"；两家的 Key 各存一份。
3. 拉取模型：区块右上「拉取」或空态行 → 拉回该服务商的**全部**模型；进行中显示「正在拉取…」。
4. 选模型：点模型行 → 写入当前选择；实时能力用「实时」角标提示，**不阻止**选择非实时模型。
5. 方言：仅 ChatGPT 显示；Beta / GA 分段互斥切换。

## 8. 领域/数据层改动

- `AiVoiceModelGateway` / `LibAiVoiceModelGateway`：替换 `AiChatGptRealtimeGateway` /
  `LibAiChatGptRealtimeGateway`；`fetch()` 直接返回平台 `/v1/models` 的**全部**模型，
  用 `AiModelFeature.REALTIME_AUDIO` 标注实时能力，不再过滤。
- `FetchVoiceModels`（原 `FetchChatGptRealtimeModels`）：去掉"只支持 ChatGPT"的限制，
  任何已填 Key 的服务商都能拉取；把全部 id 写回 `realtimeModels`（字段名保留以兼容持久化）。
- `AiVoiceConfig.resolvedRealtimeModel()`：对任何服务商都优先用用户选中的模型，否则回落预设。
- `AiVoiceConfig.withModel()`：换服务商时清空地址 / 选中模型 / 已拉列表，避免旧家的地址或模型串到新家。
- `RealtimeVoiceSession.planFor()`：统一用 `effectiveBaseUrl + resolvedRealtimeModel`，方言仅 ChatGPT 传值。
- `LibAiModels.all(...)`：新增全量映射（文本/向量/实时都回来），并把实时能力标出来。

## 9. 验证

- `:app:testDebugUnitTest`：新增/更新
  - `AiSettingsUiMapperTest`：全部模型保留且只给实时项打标、地址自定义标记。
  - `LibAiGatewayTest`：语音网关返回全部模型且正确标注实时。
  - `AiSettingsViewModelTest`：拉取全部模型后 `voice.models()` 数量正确。
- 真机截图：`ui-work/shots/r43-voice-settings/`。

## 10. 布局稳定性（上下抖动修复）

设置页"改一个值就上下抖一下"不是动画问题，是三处**布局参与测量**的结构问题，已从根本上改掉：

1. **保存状态从正文流移进页头固定位。**
   旧版 `saveHint`（"正在保存设置…"）是 `column` 里的一个 `TextView`，每次 `update()` 都会
   先发 `SAVING` 再发 `SAVED`：它在正文里显隐一次，就把下面所有内容推下去 ~21dp 再弹回来。
   现在保存状态写在页头右侧的状态位（56dp 行内、单行、GONE/VISIBLE 都不改变页头高度），
   改值不再影响正文布局。

2. **两个模式面板由 `INVISIBLE` 改 `GONE`。**
   旧版两块面板叠在同一个 `FrameLayout` 里、以 `INVISIBLE` 常驻：`INVISIBLE` 仍参与测量，
   槽高 = 两块中较高的一块。语音面板比文本面板高得多，于是文本模式被撑高、切模式与内容变化时
   槽高反复跳。现在非当前面板 `GONE`，槽高恒等于当前面板；切换只换来一次正确高度。

3. **拉取状态不再重建模型卡片。**
   `VoiceModelCard.bind()` 原来把 `fetching` 也塞进签名，`true/false` 各触发一次
   `card.removeAllViews() + 重新 add`。现在标题/动作单独更新，只有"行集合"（模型 id、选中、能力位）
   变化才重建卡片；拉取进行/结束期间卡片内容原地不动。

4. **模式说明行预留两行高度。**
   `modeHint` 设 `minLines=2`，文本/语音两种提示长度不同也不改变占位，切换模式不会因为它换行
   把内容顶一下。

验证（真机，滚动到同一位置后测量稳定锚点 y 坐标）：

| 操作 | 锚点（"语音模型 · 5"）y | 结论 |
| --- | --- | --- |
| 切换方言触发保存前 | 1340 | — |
| SAVING / SAVED 过程中 | 1340 | 不动 |
| 保存结束后 | 1340 | 不动 |

| 操作 | 锚点（"Qwen"瓦片）y | 结论 |
| --- | --- | --- |
| 点「重新拉取」前 | 569 | — |
| 拉取进行中 | 569 | 不动 |
| 拉取结束后 | 569 | 不动 |

切换模式后 `uiautomator` 树里文本模式只有"回答风格"、没有"服务商"（语音面板确为 GONE，未被测量），
切回语音模式恢复。

> 状态机与保存策略的架构级重构见 `ai-settings-state-architecture.md`：模式与配置解耦、本地优先草稿、
> 350ms 节流合并落盘、持久化状态移出 UiState。


