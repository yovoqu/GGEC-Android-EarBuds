# YoUI 库补丁（2026-10-19）：列表 Ripple 几何 + 多行输入

> 起因：App 侧四个问题——① 按压 Ripple 与列表圆角对不上；② 独立列表却沿用了分组列表的 Ripple 逻辑；
> ③ Ripple/边界的内部留白与 HarmonyOS 不一致；④ 系统提示词对话框用了 Android 原生 `EditText`。
> 前三个的根因在 `youi_kit` 的列表实现，第四个是 `YoTextInput` 只有单行能力；因此补丁落在库侧，
> 再用修复后的 release AAR 重新编译 App。

## 1. 列表 Ripple 的根因与修复

`ListView.applyContainerBackground()` 在独立列表（没有分组头）时会画一张 16dp 圆角的板材，
并把 RecyclerView 的 `containerPadding`（4dp）作为内边距；但 `ListRowFace` 对 `GroupRole.NONE`
直接返回 `(0,0)`，行 Ripple 是直角。于是：

- 行按压的直角压在 16dp 圆角板材上，四个角对不上（问题 ①）；
- 分组行的 Ripple 半径由 `groupCardCornerRadius - GROUP_CARD_PADDING` 算出，独立行却恒为 0，
  两条路径没有各自的所有者（问题 ②）；
- 独立列表的按压没有从“自己的板材半径 − 自己的容器内边距”推导，内边距语义与 HarmonyOS 对不上（问题 ③）。

修复（`youi_kit/.../list/internal/ListRowFace.java`）：

```java
if (role == ListModel.GroupRole.NONE) {
    float press = config.ownsPlate()
            ? Math.max(0f, config.getCornerRadius() - config.getContainerPadding())
            : 0f;
    return new ListRowFace(press, press);
}
float press = ListGroupCard.pressRadiusDp(config.getGroupCardCornerRadius());
```

- 独立列表：Ripple 半径 = 板材圆角 − 容器内边距（默认 16 − 4 = 12dp），与板材同心，角上留出 4dp；
- 宿主托管的列表（`ownsPlate=false`，例如对话框内容）保持直角，不凭空画板材；
- 分组列表：维持原来的 `分组卡圆角 − 4dp 内边距`，与独立列表彻底分开，互不复用常量。

## 2. 多行输入

`YoTextInput`  API 增加（同步 `TextInputImpl.TextInputBuilder`）：

```java
YoTextInput setMultiline(boolean multiline, int minLines, int maxLines);
```

实现：

- `TextInputConfig` 新增 `multiline/minLines/maxLines` 与 `Builder.multiline(...)`，`toBuilder()` 保留；
- `TextInputView` 多行时取消 `singleLine`，`setMinLines/setMaxLines` 按行数撑高，`Gravity.TOP|START`，
  IME 改为不显示“完成”（回车即换行）；单行恢复原行为；
- `applyInputType()` 在多行时补 `TYPE_TEXT_FLAG_MULTI_LINE | CAP_SENTENCES`，并强制重挂一次 inputType，
  避免 `setSingleLine()` 的 `SingleLineTransformationMethod` 残留；
- 光标高度在多行时取行高，而不是单行控件高度；
- 计数与错误提示继续用 `YoTextInput` 已有的 `setShowCounter` / `setShowErrorEnabled` / `showError`。

App 侧 `AiDialogs.editText()` 随之改为：

```java
YoTextInput field = YoTextInput.create(context)
        .setText(initial)
        .setPlaceholder(hint)
        .setMaxLength(maxLength)
        .setShowCounter(true)
        .setShowErrorEnabled(true)
        .setMultiline(true, 5, 10)
        .build();
YoDialog.create(context).kind(YoDialog.Kind.INPUT).hosted(field) ...
```

不再创建原生 `EditText`、手写字数计数和错误 TextView。

## 3. App 侧列表结构（配合修复）

> 下面 §3 是中间的“单张分组列表”方案，已被 §7 的“独立列表 + 默认边界线/阴影”取代；
> 保留仅为记录演进过程。

- 删除 `ui/ai/settings/AiListSection`（每个分组一个独立 `YoList`，每张卡自己画板材，正是 Ripple 与边界对不上的来源）。
- 新增 `ui/ai/settings/AiGroupedList`：整页只有一张 `YoList`，`header()` 走 `addGroupHeader`，
  `footer()` 走 `addGroupFooter`，卡片圆角、分组间距、行内边距、行 Ripple 全部由 YoUI 分组几何给出。
  默认几何与 HarmonyOS 对齐：卡片外边距 12dp、卡片内边距 4dp、行内容 inset 16dp、行 Ripple 半径 12dp。
- `AiSettingsActivity`、`AiPlatformActivity` 改为单列表；`AiGroupedList` 由页面 `YoComponents` 释放。
- 历史/模型面板仍用 `AiListSheet` 的一张 `YoList`：模型面板按平台 `addGroupHeader`；历史面板是独立列表，
  现在走修复后的“独立列表 Ripple 半径 = 板材圆角 − 内边距”，不再和分组卡逻辑混用。

## 4. AAR 重建与产物

```powershell
cd D:\A_yoprogram\Github_Gallery\Android-YoUI
.\gradlew.bat :youi_kit:assembleRelease --offline --console=plain
# BUILD SUCCESSFUL
```

- 产物：`youi_kit/build/outputs/aar/youi_kit-release.aar`
  - 2026-10-19 17:49 按最新库源码重建（统一列表卡片几何 + 默认边界线/阴影）：SHA-256 `3CA4EB12D484065871A8A02BCC39AA9FD2FC7D93E5FB20774B27665BE9CA0CBC`，大小 2,186,788 B
  - 16:06 的上一版：SHA-256 `09D4D8076A8BB32882AABA7249ED0DCF4C4966DC42DFA5835515A61E324A1315`，大小 2,175,590 B（已被替换）
- 已复制到 `GGEC-Android-EarBuds/app/libs/youi_kit-1.0.0.aar`。
- **没有**覆盖 `Android-YoUI/releases/youi_kit-1.0.0.aar`（releases 仍指向 13:09 的旧构建）；
  App 现在依赖的是“本地补丁后的 AAR”，与 releases 不一致，后续若发版需要重新导出并更新 releases/mapping。
- 库仓库本身还有大量与本补丁无关的未提交改动；AAR 由当前工作树构建，因此只保证“源码 + 本补丁”一致，
  不声称与 releases 字节一致。

## 5. 验证

| 项 | 结果 |
| --- | --- |
| `:youi_kit:assembleRelease --offline` | BUILD SUCCESSFUL |
| `:app:compileDebugJavaWithJavac --offline --rerun` | BUILD SUCCESSFUL |
| `:app:testDebugUnitTest` | 26 个测试类 / 220 个用例 / 0 失败 |
| `:app:assembleDebug` | BUILD SUCCESSFUL（APK 已重新安装到 motorola edge 60 pro） |
| `:app:checkShellLayerImports :app:checkAiLayerImports` | BUILD SUCCESSFUL |

> 按用户要求，视觉效果由用户自行在设备上确认；本次未主动截图比对。

## 6. 2026-10-19 17:05 按最新 YoUI 源码重新构建

16:06 的 AAR 之后，`Android-YoUI` 工作树又有大量更新（Dialog/Menu/Segment/Page/immersive 等，最新源码时间 16:53）。
按要求重新检查、重新编译并替换 AAR：

- 构建：`:youi_kit:clean :youi_kit:assembleRelease --offline` → BUILD SUCCESSFUL；
- 新 AAR：`BBE8412A579D7B033BE690D6F21A3D183EB24072CCD8C20FF2AEADB5841C4A78`，2,185,921 B；
- 已复制到 `app/libs/youi_kit-1.0.0.aar`；`releases/` 仍未覆盖。

公共 API 对比（旧 App AAR vs 新构建，javap 全量 `com.yohu.youi.api`）只有两处非混淆差异：

1. `YoDialog.Body` 增加 `ContentFlow`（构造参数从 4 个变 5 个，新增 `contentFlow()/withContentFlow()/Body.Builder.setContentFlow()`）。
   App 没有直接构造 `Body`，无需改动；
2. `YoMenu.Placement.AUTO` 被移除，新增 `LEFT_TOP/LEFT_BOTTOM/RIGHT_TOP/RIGHT_BOTTOM`。
   App 有 3 处 `Placement.AUTO`，已改为文档默认的 `Placement.BOTTOM_LEFT`（库的 `PLACEMENT_STATES`
   会在放不下时自动换方位并夹进窗口安全边，和原来的“自动”目的相同）：
   - `AiChatPage.onLongPress` 消息菜单
   - `AiPlatformActivity.chooseProtocol` 协议菜单
   - `AiSettingsActivity.showChoices` 数值选择菜单

其余变化是 R8 重命名导致的 `o.*` 内部类型名变化，不属于 App 调用面。
App 侧验证：`:app:compileDebugJavaWithJavac --rerun`、`:app:testDebugUnitTest`（220 用例）、
`:app:assembleDebug`、两个 layer check 全部通过；APK 已重新安装到 motorola edge 60 pro。

## 7. 2026-10-19 17:xx 最终形态：独立列表 + 默认边界线/阴影

真机反馈设置页行 Ripple 仍是分组卡片几何，因此把设置页/平台页改回**独立列表**，并把边界线、阴影、Ripple
都收进 YoUI 列表内部：

- App：
  - 删除 `AiGroupedList`（整页分组列表）；
  - 新增 `AiSectionList`：每块标题/说明在列表外，中间一张**独立** `YoList`；
  - `AiSettingsActivity`、`AiPlatformActivity` 恢复按块渲染，行 Ripple 由独立列表板材几何计算。
- YoUI：
  - `ListConfig` 新增 `borderWidth/borderColor`，默认 `ListDimens.BORDER_WIDTH = 1dp`、
    颜色取主题 `componentBorder`；
  - `ListView` 独立板材与 `ListGroupCard` 分组卡片都改用 `YoCorner.fillStroke` 绘制填充 + 边界线；
  - 阴影沿用 `ListChrome` 默认 `ListDimens.ELEVATION`，独立列表默认即带阴影，分组列表不叠外层阴影；
  - 删除兼容常量 `ListDimens.PLAIN_PRESS_RADIUS`（无引用，独立 Ripple 半径已由板材几何推导）；
  - 清理旧 `AiListSection`/`AiGroupedList` 文档引用。
- 新 AAR：`3CA4EB12D484065871A8A02BCC39AA9FD2FC7D93E5FB20774B27665BE9CA0CBC`，2,186,788 B，已复制到 `app/libs/`；
  `releases/` 仍未覆盖。

## 8. 2026-10-19 17:49 列表卡片几何架构统一

### 8.1 链路

```
ListDimens（几何令牌）
  → ListConfig（不可变快照：containerPadding / cornerRadius / groupCardCornerRadius）
    → ListView.applyContainerBackground（板材 padding / 边框 / 阴影）
    → ListAdapter.rowFace → ListRowFace（行按压圆角）
    → ListGroupCard（分组卡 padding / 卡内行按压圆角）
```

### 8.2 问题

- 独立列表的卡片内边距用 `ListDimens.CONTAINER_PADDING = Dimens.SPACING_EXTRA_SMALL`（2dp）；
- 分组卡片的内边距用另一枚 `ListDimens.GROUP_CARD_PADDING = 4f`；
- `ListRowFace.of(config, role)` 只拿到 role，拿不到“当前列表是独立板材还是分组卡片”，
  于是独立/分组两套几何各自演化：独立列表 Ripple 内边距 2dp、分组 4dp，且分组头/尾行也会被当成普通行；
- App 的 `AiSectionList` 只调用 `YoList.create(context).setOnItemClickListener(...).build()`，没有任何
  `setContainerPadding / setCornerRadius / setItemInset*` 赋值。

**结论：2dp 是组件默认值，不是 App 调用时设置的值；默认值与分组卡片/HDS 卡片内边距不一致。**

### 8.3 架构修复

- 合并为单一几何令牌：`ListDimens.CARD_PADDING = Dimens.SPACING_SMALL`（HDS 卡片内边距 4vp），
  删除 `CONTAINER_PADDING` 与 `GROUP_CARD_PADDING` 两套来源；
- `ListConfig.containerPadding` 成为唯一卡片内边距来源，`ListView` 板材 padding、
  `ListGroupCard` 卡片 padding/卡内行按压半径、分组头文字 inset 全部读它；
- `ListRowFace.of(config, groupCards, role)` 显式接收列表模式：
  独立列表 = `cornerRadius − containerPadding`，分组卡片 = `groupCardCornerRadius − containerPadding`，
  宿主托管（ownsPlate=false）= 直角；
- `ListAdapter` 由数据模型得出 `groupCards`，行按压只给 `Type.ITEM`，分组头/尾返回 `none()`；
- 删除 `ListGroupCard.pressRadiusDp(cardRadius)` 这种只传半径的重复公式，统一 `pressRadiusDp(config)`。

### 8.4 验证

- `:youi_kit:assembleRelease --offline` BUILD SUCCESSFUL；
- 列表包单测（`com.yohu.youi.widget.containers.list.*`）通过；
- App `:app:testDebugUnitTest` 220 用例、`:app:assembleDebug`、两个 layer check 通过；
- 最新 APK 已安装到设备（17:49）。



