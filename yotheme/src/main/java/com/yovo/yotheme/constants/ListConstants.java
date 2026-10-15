package com.yovo.yotheme.constants;

/**
 * 列表组件常量枚举
 * 参考华为设计-列表规范并结合本项目主题体系
 */
public enum ListConstants {
    // ── 间距模型（单点控制，严禁多层叠加）────────────────────────────────────
    // 仅两个独立常量，各自对应唯一布局层；禁止再定义「总和」类常量。
    /** 第 1 层 · 水波纹缩进：YoList 独立模式 padding / ListCard GroupCard padding */
    LIST_CARD_RIPPLE_INSET_DP(2),

    // 列表容器（仅用于组间距等非内容 inset 场景）
    LIST_SECTION_GAP_DP(4),
    LIST_CARD_MARGIN_DP(8),
    /** 嵌入 ListCard 时列表项最小高度（保证可点区域，不额外叠 content padding） */
    LIST_EMBEDDED_ITEM_MIN_HEIGHT_DP(48),
    /** 列表卡片阴影高度（与主界面 MaterialCardView 一致） */
    LIST_CARD_ELEVATION_DP(2),

    // 组容器：非组合列表的分组间距与组内列表间距
    LIST_GROUP_GAP_DP(4),
    LIST_GROUP_INNER_GAP_DP(4),

    // 卡片圆角（外层卡片容器）
    LIST_CARD_CORNER_RADIUS_DP(32),

    // 水波纹效果（行点击反馈）
    LIST_ITEM_RIPPLE_CORNER_RADIUS_DP(32),  // 水波纹圆角（32dp圆角）
    LIST_ITEM_RIPPLE_MARGIN_DP(0),          // 水波纹外边距（0表示紧贴容器边缘）

    // 分隔线（行间）
    DIVIDER_HEIGHT_DP(1),
    // 分隔线像素高度（与对话框统一厚度）
    DIVIDER_HEIGHT_PX(1),
    // 分隔线缩进（与Menu组件保持一致）
    // 注意：现在分隔线通过getTextStartXInParent()和getTextEndXInParent()直接对齐到文本区域
    // 这些值仅用于兼容非ListItemContainer的情况
    DIVIDER_INSET_START_DP(16),  // 与Menu保持一致
    DIVIDER_INSET_END_DP(16),    // 与Menu保持一致

    // 行高（最小值）
    ITEM_MIN_HEIGHT_SINGLE_DP(52),
    ITEM_MIN_HEIGHT_DOUBLE_DP(64),

    // 第 2 层 · 内容区内边距：仅由 ListItemContainer.contentWrapper 承担
    /**
     * 列表项内容区域水平内边距（唯一 content 层，禁止在其他容器重复设置）。
     */
    ITEM_CONTENT_PADDING_HORIZONTAL_DP(12),
    /**
     * 单行列表项内容区域与上下边缘之间的垂直内边距。
     * 适用于只显示一行主标题的列表项，确保垂直方向的视觉平衡。
     */
    ITEM_CONTENT_PADDING_VERTICAL_SINGLE_DP(8),
    /**
     * 双行（或多行）列表项内容区域与上下边缘之间的垂直内边距。
     * 适用于同时显示主标题和副标题的列表项，为多行文本提供足够的垂直空间。
     */
    ITEM_CONTENT_PADDING_VERTICAL_DOUBLE_DP(8),

    // 文本层级与排版
    TITLE_TEXT_SIZE_SP(18),  // 标题字号从16sp增加到18sp
    SUBTITLE_TEXT_SIZE_SP(12),
    TITLE_SUBTITLE_GAP_DP(1),
    TEXT_MAX_LINES_TITLE(2),
    TEXT_MAX_LINES_SUBTITLE(2),

    // Leading（左侧图标/图片）尺寸档
    LEADING_SIZE_8_DP(8),
    LEADING_SIZE_12_DP(12),
    LEADING_SIZE_16_DP(16),
    LEADING_SIZE_20_DP(20),
    LEADING_SIZE_24_DP(24),
    LEADING_SIZE_32_DP(32),
    LEADING_SIZE_40_DP(40),
    LEADING_SIZE_48_DP(48),
    LEADING_SIZE_56_DP(56),
    LEADING_SIZE_64_DP(64),

    // 典型默认尺寸
    LEADING_DEFAULT_SIZE_DP(26),  // 默认图标大小从24dp增加到26dp
    LEADING_CONTENT_GAP_DP(12),
    CONTENT_TRAILING_GAP_DP(8),
    // 紧凑间距：用于批量操作框等需要更紧凑布局的场景
    CONTENT_TRAILING_GAP_TIGHT_DP(4),

    // Trailing（右侧操作区）
    TRAILING_GAP_DP(8),
    TRAILING_TEXT_SIZE_SP(14),
    TRAILING_ICON_SIZE_DP(24),
    TRAILING_MIN_TAP_SIZE_DP(44),

    // 通用：极细占位像素（用于分隔占位等）
    SPACER_THIN_PX(1),

    // 动画
    ANIMATION_DURATION_FAST_MS(120),
    ANIMATION_DURATION_NORMAL_MS(200),

    // 对齐与重心（与 NavigationConstants 对齐，方便直接使用）
    GRAVITY_CENTER(NavigationConstants.GRAVITY_CENTER.getIntValue()),
    GRAVITY_CENTER_VERTICAL(NavigationConstants.GRAVITY_CENTER_VERTICAL.getIntValue()),
    GRAVITY_CENTER_HORIZONTAL(NavigationConstants.GRAVITY_CENTER_HORIZONTAL.getIntValue()),

    // 通用浮点常量
    ROUNDING_FLOAT(NavigationConstants.ROUNDING_FLOAT.getFloatValue());

    private final int intValue;
    private final float floatValue;

    ListConstants(int value) {
        this.intValue = value;
        this.floatValue = value;
    }

    ListConstants(float value) {
        this.intValue = (int) value;
        this.floatValue = value;
    }

    public int getIntValue() {
        return intValue;
    }

    public float getFloatValue() {
        return floatValue;
    }
}


