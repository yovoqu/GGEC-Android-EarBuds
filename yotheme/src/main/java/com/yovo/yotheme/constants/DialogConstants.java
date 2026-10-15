package com.yovo.yotheme.constants;

/**
 * @author tingfeng.yang
 * @date 2025-08-07
 * @description 对话框常量枚举
 * @note 存储YoDialog控件中使用的所有常量值
 */
public enum DialogConstants {
    // 内边距常量
    PADDING_HORIZONTAL(16),
    PADDING_TOP(14),
    PADDING_BOTTOM(8),
    PADDING_NONE(0),  // 无内边距

    // 圆角常量
    CORNER_RADIUS(24),
    SHADOW_PADDING(24),
    SHADOW_RADIUS(18),
    SHADOW_DY(4),
    SHADOW_ALPHA(72),

    // 分隔线常量
    DIVIDER_HEIGHT(18),
    DIVIDER_WIDTH(1),

    // 按钮常量
    BUTTON_HEIGHT(40),

    // 边距常量
    CONTENT_BOTTOM_MARGIN(8),
    MARGIN_NONE(0),   // 无外边距

    // 文本大小常量
    TITLE_TEXT_SIZE(20),
    MESSAGE_TEXT_SIZE(15),
    BUTTON_TEXT_SIZE(17),

    // 标题底部边距
    TITLE_BOTTOM_PADDING(8),

    // 行间距
    TEXT_LINE_SPACING(2),

    // 按钮到底部的距离
    BUTTON_BOTTOM_MARGIN(0),

    // 按钮之间的间距
    BUTTON_HORIZONTAL_MARGIN(2),

    // 选择对话框常量
    OPTION_ITEM_VERTICAL_PADDING(2),
    OPTION_TEXT_SIZE(16),        // 选项文本大小
    OPTION_PADDING_START(4),    // 选项左侧内边距
    OPTION_PADDING_END(4),      // 选项右侧内边距
    OPTION_ITEM_MARGIN(2),       // 选项项之间间距
    OPTION_RADIO_SIZE(24),       // 单选按钮大小
    OPTION_TEXT_PADDING_START(12), // 选项文本与单选按钮的间距
    SELECT_CONTENT_MAX_HEIGHT_DP(320), // 选择内容区域的最大高度(dp)，超过则滚动
    OPTION_SCROLLBAR_TOUCH_SLOT_WIDTH(16), // 选择列表滚动条独立触摸槽宽度
    OPTION_SCROLLBAR_CONTENT_END_PADDING(4), // 内容右侧避让，避免控件与触摸槽重叠

    // 对话框尺寸约束常量
    MIN_DIALOG_WIDTH(280),       // 对话框最小宽度(dp)
    DEFAULT_DIALOG_WIDTH(320),   // 对话框默认宽度(dp)
    MAX_WIDTH_PERCENTAGE_OF_SCREEN(95), // 对话框最大宽度占屏幕宽度的百分比
    DIALOG_SCREEN_MARGIN(32),     // 对话框与屏幕左右边界的距离(dp)

    // 布局权重
    BUTTON_LAYOUT_WEIGHT(1),     // 按钮布局权重

    // 文本行间距乘数
    TEXT_LINE_SPACING_MULTIPLIER(1), // 文本行间距乘数

    // 默认按钮文本
    DEFAULT_POSITIVE_BUTTON_TEXT("确定"),
    DEFAULT_NEGATIVE_BUTTON_TEXT("取消"),
    
    // 选择对话框子视图索引
    OPTION_ROW_RADIO_CHILD_INDEX(1),   // 选项行中单选按钮的索引
    
    // 列表对话框常量
    LIST_DIALOG_TITLE_BOTTOM_PADDING(2), // 列表对话框标题下边距
    LIST_ITEM_CORNER_RADIUS(16),      // 列表项圆角
    LIST_ITEM_DIVIDER_HEIGHT(1),     // 列表项分割线高度

    // 入场/退场动画
    SHOW_ANIMATION_DURATION_MS(340),
    DISMISS_ANIMATION_DURATION_MS(260);

    // 常量值
    private final int intValue;
    private final String stringValue;

    /**
     * 构造方法 (整型值)
     *
     * @param value 常量值
     */
    DialogConstants(int value) {
        this.intValue = value;
        this.stringValue = null;
    }

    /**
     * 构造方法 (字符串值)
     *
     * @param value 常量值
     */
    DialogConstants(String value) {
        this.intValue = -1; // 或其他标记值
        this.stringValue = value;
    }

    /**
     * 获取整型常量值
     *
     * @return 常量值
     */
    public int getValue() {
        return intValue;
    }

    /**
     * 获取字符串常量值
     *
     * @return 常量值
     */
    public String getString() {
        return stringValue;
    }
}
