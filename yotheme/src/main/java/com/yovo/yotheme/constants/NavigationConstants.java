package com.yovo.yotheme.constants;

/**
 * @author tingfeng.yang
 * @date 2025-08-15
 * @description 导航栏常量枚举
 * @note 存储YoNavigationBar及YoNavigationItem控件中使用的所有常量值
 */
public enum NavigationConstants {
    // 导航栏容器尺寸
    DEFAULT_HEIGHT_DP(52), // 导航栏默认高度(dp)
    MIN_HEIGHT_DP(48), // 导航栏最小高度(dp)
    MAX_HEIGHT_DP(80), // 导航栏最大高度(dp)
    
    // 导航项尺寸
    ITEM_MIN_WIDTH_DP(64), // 单个导航项最小宽度(dp)
    ITEM_MAX_WIDTH_DP(120), // 单个导航项最大宽度(dp)
    ITEM_PADDING_HORIZONTAL_DP(8), // 导航项水平内边距(dp)
    ITEM_PADDING_VERTICAL_DP(6), // 导航项垂直内边距(dp)
    ITEM_PADDING_HORIZONTAL_COMPACT_DP(6), // 紧凑模式水平内边距(dp)
    ITEM_PADDING_VERTICAL_COMPACT_DP(4), // 紧凑模式垂直内边距(dp)
    ITEM_PADDING_HORIZONTAL_LARGE_DP(16), // 大型模式水平内边距(dp)
    ITEM_PADDING_VERTICAL_LARGE_DP(12), // 大型模式垂直内边距(dp)
    
    // 图标相关
    ICON_SIZE_DEFAULT_DP(24), // 默认图标尺寸(dp)
    ICON_SIZE_SMALL_DP(20), // 小号图标尺寸(dp)
    ICON_SIZE_LARGE_DP(28), // 大号图标尺寸(dp)
    ICON_SIZE_XS_DP(18), // 特小图标尺寸(dp)
    ICON_SIZE_XL_DP(32), // 特大图标尺寸(dp)
    ICON_MARGIN_BOTTOM_DP(0), // 图标与文字的默认间距(dp)
    ICON_MARGIN_BOTTOM_COMPACT_DP(0), // 紧凑模式图标与文字间距(dp)
    ICON_MARGIN_BOTTOM_LARGE_DP(4), // 大型模式图标与文字间距(dp)
    
    // 文本相关
    TEXT_SIZE_DEFAULT_SP(13), // 默认文字大小(sp)
    TEXT_SIZE_SMALL_SP(10), // 小号文字大小(sp)
    TEXT_SIZE_LARGE_SP(14), // 大号文字大小(sp)
    TEXT_SIZE_XL_SP(16), // 特大文字大小(sp)
    TEXT_MAX_LINES(2), // 文本最大行数
    TEXT_SINGLE_LINE(1), // 单行文本行数
    TEXT_MAX_LINES_EXTENDED(3), // 扩展模式下文本最大行数
    
    // 动画相关
    ANIMATION_DURATION_FAST(150), // 快速动画时长(ms)
    ANIMATION_DURATION_NORMAL(250), // 常规动画时长(ms)
    ANIMATION_DURATION_SLOW(350), // 慢速动画时长(ms)
    ANIMATION_DURATION_SCALE_FAST_MS(100), // 缩放动画快速时长(ms)
    ANIMATION_DURATION_COLOR_SLOW_MS(500), // 颜色动画慢速时长(ms)
    ANIMATION_DURATION_SCALE_SLOW_MS(300), // 缩放动画慢速时长(ms)
    BADGE_ANIMATION_DURATION_FAST_MS(200), // 徽章动画快速时长(ms)
    BADGE_ANIMATION_DURATION_SLOW_MS(600), // 徽章动画慢速时长(ms)
    
    // 透明度相关
    ALPHA_SELECTED(1.0f), // 选中态透明度
    ALPHA_UNSELECTED(0.6f), // 未选中态透明度
    ALPHA_DISABLED(0.3f), // 禁用态透明度
    
    // 徽章相关
    BADGE_SIZE_DP(8), // 徽章圆点尺寸(dp)
    BADGE_LARGE_SIZE_DP(16), // 徽章容器高度/大尺寸(dp)
    BADGE_OFFSET_X_DP(-4), // 徽章相对图标X偏移(dp)
    BADGE_OFFSET_Y_DP(4), // 徽章相对图标Y偏移(dp)
    BADGE_MIN_WIDTH_DP(16f), // 文本徽章最小宽度(dp)
    BADGE_PADDING_DEFAULT_DP(4f), // 文本徽章默认内边距(dp)
    BADGE_PADDING_SMALL_DP(2f), // 文本徽章小号内边距(dp)
    BADGE_PADDING_LARGE_DP(6f), // 文本徽章大号内边距(dp)
    BADGE_MAX_LENGTH(99), // 数字徽章截断阈值
    BADGE_TEXT_SIZE_SMALL_SP(8), // 徽章小号文字(sp)
    BADGE_TEXT_SIZE_LARGE_SP(12), // 徽章大号文字(sp)
    BADGE_MAX_TEXT_PREVIEW_CHARS(3), // 非数字文本预览字符数
    BADGE_DOT_SIZE_SMALL_DP(6f), // 小号圆点直径(dp)
    BADGE_DOT_SIZE_LARGE_DP(10f), // 大号圆点直径(dp)
    BADGE_CONTAINER_HEIGHT_SMALL_DP(14f), // 文本徽章小号高度(dp)
    BADGE_CONTAINER_HEIGHT_LARGE_DP(20f), // 文本徽章大号高度(dp)
    BADGE_ANIMATION_DURATION_MS(300), // 徽章默认动画时长(ms)
    BADGE_HIDE_DURATION_DIVISOR(2), // 徽章隐藏动画加速除数
    
    // 分割线
    DIVIDER_HEIGHT_DP(1), // 分割线高度(dp)
    DIVIDER_MARGIN_DP(0), // 分割线外边距(dp)
    DIVIDER_THIN_HEIGHT_DP(0.5f), // 细分割线高度(dp)

    // 阴影/装饰
    SHADOW_OVERLAY_COLOR(0x1A000000), // 顶部阴影颜色(约10%黑)
    SHADOW_HEIGHT_DP(3.0f), // 顶部阴影高度(dp)
    ANIMATION_BADGE_OVERSHOOT_TENSION(1.2f), // 徽章弹性动画张力
    
    // 图标类型标识
    ICON_TYPE_SINGLE_COLOR(1), // 单色图标
    ICON_TYPE_DUAL_COLOR(2), // 双色图标
    ICON_TYPE_MULTI_COLOR(3), // 多色图标
    
    // 状态标识
    STATE_NORMAL(0), // 常规态
    STATE_SELECTED(1), // 选中态
    STATE_DISABLED(2), // 禁用态
    STATE_PRESSED(3), // 按压态
    
    // 布局相关
    GRAVITY_CENTER(17),           // 文本/子视图居中(等同于 Gravity.CENTER)
    GRAVITY_CENTER_HORIZONTAL(1), // 水平居中(等同于 Gravity.CENTER_HORIZONTAL)
    GRAVITY_CENTER_VERTICAL(16),  // 垂直居中(等同于 Gravity.CENTER_VERTICAL)
    LAYOUT_WEIGHT_NAV_ITEM(1.0f), // 导航项权重
    
    // 最大项数限制
    MAX_NAVIGATION_ITEMS(5), // 默认最大导航项数
    MIN_NAVIGATION_ITEMS(2), // 默认最小导航项数
    MAX_NAV_ITEMS_COMPACT(4), // 紧凑模式最大项数
    MAX_NAV_ITEMS_FLOATING(4), // 浮动模式最大项数
    MAX_NAV_ITEMS_TOOLBAR(6), // 工具栏模式最大项数
    NAV_STATE_DEFAULT_MAX_HISTORY(10), // 状态历史记录默认上限
    
    // 浮点数常量
    ROUNDING_FLOAT(0.5f), // 四舍五入辅助常量
    SCALE_NORMAL(1.0f), // 正常缩放比例
    SCALE_PRESSED(0.95f), // 按压态缩放比例

    // 其他
    SELECTED_NONE_ID(-1), // 无选中态的标识ID
    TOOLBAR_HEIGHT_DP(56); // 工具栏高度(dp)
    
    // 常量值（整型）
    private final int intValue;
    
    // 常量值（浮点型）
    private final float floatValue;
    
    /**
     * 整型构造方法
     * 
     * @param value 常量值(整型)
     */
    NavigationConstants(int value) {
        this.intValue = value;
        this.floatValue = value;
    }
    
    /**
     * 浮点型构造方法
     * 
     * @param value 常量值(浮点型)
     */
    NavigationConstants(float value) {
        this.intValue = (int) value;
        this.floatValue = value;
    }
    
    /**
     * 获取整型常量值
     * 
     * @return 常量值(整型)
     */
    public int getIntValue() {
        return intValue;
    }
    
    /**
     * 获取浮点型常量值
     * 
     * @return 常量值(浮点型)
     */
    public float getFloatValue() {
        return floatValue;
    }
}
