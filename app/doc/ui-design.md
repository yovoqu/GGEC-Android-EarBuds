# UI 设计规范（v1）

这份文档是 AI 相关界面的设计依据。页面只引用令牌和构件，不再各自写字号、间距和圆角。

## 1. 调研结论

| 参考 | 取用的部分 |
| --- | --- |
| [Material 3 · Lists](https://m3.material.io/components/lists/overview) | 列表项三档高度：一行 56dp、两行 72dp、三行 88dp；leading / headline / supporting text / trailing 四段式；左侧 16dp 内边距 |
| [M3E ListItem 规格](https://pub.dev/documentation/m3e_core/1.1.3/m3e_core/M3EListItem-class.html) | 同上高度的落地实现：leading 与文字间距 12dp，trailing 与文字间距 12dp |
| [Now in Android](https://github.com/android/nowinandroid) | 用 Material 3 设计体系 + 设计令牌组织 UI；页面、组件、令牌分层，深浅色与动态色都从令牌走 |
| [Material 3 字体阶梯](https://m3.material.io/styles/typography/type-scale-tokens) | Display / Headline / Title / Body / Label 的层级关系，本应用压缩成 7 档 |
| YoUI 组件（youi_kit） | YoPage（标题栏）、YoSheet（面板）、YoMenu、YoSwitch、YoDialog 保持原生观感，页面只在其内容区排版 |

结论：本应用是「耳机工具的 AI 标签页」，不做对话产品那种落地页。视觉上按系统设置/消息类应用的做法：
信息密度适中、层级靠字号和留白区分、只有一处主色强调，避免装饰性图标块和大面积色块。

## 2. 设计原则

1. **层级靠字号和留白，不靠色块。** 一个页面最多一处主色（主按钮或当前态）。
2. **一行只做一件事。** 行高固定 56/72，四段式：图标、标题（+副标题）、右侧值、箭头/开关。
3. **图标是线性的，不加底板。** 只有平台头像用圆形底；行内图标 20dp、颜色 textSecondary。
4. **卡片是容器，不是装饰。** 16dp 圆角、无阴影、靠背景色和 1px 分隔线区分。
5. **对话页只有两个平面。** 页面底色 + 消息内容；输入框是唯一的描边浮层。

## 3. 令牌

### 3.1 颜色（全部来自 YoUI 主题，随深浅色切换）

| 令牌 | 用途 |
| --- | --- |
| `background` | 页面底色（snowGray 1） |
| `surface` | 卡片、输入框、面板 |
| `surfaceVariant` | 次级填充：标签底、推理块、登录态气泡 |
| `textPrimary / Secondary / Tertiary / Quaternary` | 正文 / 说明 / 辅助 / 占位 |
| `accent / onAccent / accentSoft` | 主操作、当前态、发送按钮、用户气泡 |
| `danger / warning / success` | 错误、截断、测速结果 |
| `hairline` | 1px 分隔线和描边（textPrimary 8%~12%） |

### 3.2 字号

| 名称 | 字号 | 用途 |
| --- | --- | --- |
| `TEXT_DISPLAY` | 28 | 页面大标题（由 YoPage 负责，页面内基本不用） |
| `TEXT_HEADLINE` | 20 | 空状态标题、对话开场 |
| `TEXT_TITLE` | 17 | 卡片标题、设置分组标题 |
| `TEXT_BODY` | 15 | 行标题、消息正文、输入框 |
| `TEXT_CALLOUT` | 14 | 说明文字、按钮文字 |
| `TEXT_SUBHEAD` | 13 | 副标题、分组标签、标签文字 |
| `TEXT_CAPTION` | 12 | 值、页码、模型名、脚注 |
| `TEXT_OVERLINE` | 11 | 预留 |

### 3.3 间距与圆角

- 间距：4 / 8 / 12 / 16 / 20 / 24 / 32
- 圆角：标签 8、控件 12、卡片 16、输入框 22、面板 24
- 页边距 20；卡片内行内边距 16；行高 56（一行）/ 72（两行）
- 图标：行内 20、箭头 16、头像 32

## 4. 构件（当前实现）

| 构件 | 说明 |
| --- | --- |
| `ui/ai/AiTheme` | 颜色、字号、间距、圆角与背景/文本/图标工厂；颜色全部来自 YoUI 主题快照 |
| `ui/ai/AiKeyboard` | 输入法收起（对话框与输入区共用） |
| `ui/ai/AiDialogs` | 确认、动作列表、单行/多行输入、滑块对话框；统一走 `YoDialog.Builder.show()` |
| `ui/ai/chat/AiListSheet` | 历史/模型面板共用的 `YoSheet` + `YoList` 骨架（含行内组件回收） |
| `ui/ai/chat/*` | 对话页、消息视图、输入区、欢迎页；分页/标题栏用 `YoPage`，按钮/输入框优先用 YoUI 组件 |
| `ui/ai/settings/AiSectionList` | 设置/平台页的一块：标题/说明在列表外，中间一张独立 `YoList`；板材默认带边界线与阴影，行 Ripple 用独立列表几何 |
| `ui/ai/widget/MarkdownView` 等 | 库没有的富文本/Markdown/打字动画等必要自绘 |
| `ui/common/YoUi` `YoComponents` `YoPageActivity` | 宿主接入：edge-to-edge、顶部留白归属、页面级组件释放、页面基类 |

## 5. 页面布局

### 5.1 对话页

```
YoPage：标题「AI」，副标题＝当前模型或「尚未选择模型」，右上 3 个菜单
├─ 列表区
│   ├─ 有消息：RecyclerView（用户气泡靠右、助手消息靠左）
│   └─ 无消息：开场
│       ├─ 20sp 标题 + 14sp 说明
│       ├─ 建议胶囊 2 列（40dp 高，描边）
│       └─ 或引导卡片（说明 + 主按钮 + 脚注）
└─ 输入区：描边输入框（22 圆角）＋ 左下模型标签 ＋ 右下发送
```

- 用户消息：`accentSoft` 气泡，右上/左下 18dp、右下 6dp，最大宽度 78%。
- 助手消息：12sp 模型名 → 15sp Markdown 正文 → 操作行（复制/重试/删除，34dp 图标）＋ 12sp 耗时/token。
- 思考过程：`surfaceVariant` 圆角块，标题 13sp，展开后 13sp 正文。

### 5.2 设置 / 平台详情

- 平台详情页的标题就是平台名，副标题是「未配置 / 已配置 · N 个模型」，不再单独放头像大标题。
- 分组：分组标签（13sp，textTertiary）→ 卡片（16 圆角）→ 行（56/72）。
- 行：图标 20（textSecondary）→ 标题 15 + 副标题 13 → 值 12（textTertiary）→ 箭头 16 / 开关。
- 危险操作单独成组，标题用 `danger`。
- 空状态：一行说明，左对齐，不加插图。

### 5.3 面板（模型、历史）

- 沿用 YoSheet；标题由面板负责。
- 行规格与设置页一致（16 内边距、56 高、16 图标）。
- 空状态：一行 13sp textTertiary，居中。

## 6. 不要做的事

- 不要给每行图标加圆角底板（模板感最强的一处）。
- 不要用厂商品牌色区分平台，只用中性头像 + 名称。
- 不要在空状态用居中大图标、渐变、胶囊大按钮和营销文案。
- 不要出现第三种圆角或第三种页边距；新增尺寸先进 `AiTheme`。
