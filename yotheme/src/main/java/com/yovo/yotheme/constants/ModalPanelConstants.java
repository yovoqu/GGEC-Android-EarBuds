package com.yovo.yotheme.constants;

/**
 * @author tingfeng.yang
 * @date 2025-08-15
 * @description 模态面板常量定义
 * @note 参考 yo_list 架构，提供统一的常量管理
 */
public enum ModalPanelConstants {

    // ==================== 尺寸相关常量 ====================
    
    /** 默认圆角半径 (dp) */
    DEFAULT_CORNER_RADIUS_DP(16f),
    
    /** 大圆角半径 (dp) */
    LARGE_CORNER_RADIUS_DP(20f),
    
    /** 小圆角半径 (dp) */
    SMALL_CORNER_RADIUS_DP(12f),
    
    /** 拖拽条宽度 (dp) */
    DRAG_BAR_WIDTH_DP(40f),
    
    /** 拖拽条高度 (dp) */
    DRAG_BAR_HEIGHT_DP(4f),
    
    /** 拖拽条圆角 (dp) */
    DRAG_BAR_CORNER_RADIUS_DP(2f),
    
    /** 拖拽条容器垂直内边距 (dp) */
    DRAG_BAR_CONTAINER_PADDING_VERTICAL_DP(12f),
    
    /** 内容区默认内边距 (dp) */
    CONTENT_PADDING_DP(20f),
    
    /** 内容区较大内边距 (dp) */
    CONTENT_PADDING_LARGE_DP(24f),
    
    /** 内容区较小内边距 (dp) */
    CONTENT_PADDING_SMALL_DP(16f),
    
    /** 标题与内容间距 (dp) */
    TITLE_CONTENT_GAP_DP(12f),
    
    /** 较小的标题与内容间距 (dp) */
    TITLE_CONTENT_GAP_SMALL_DP(8f),
    
    // ==================== 高度比例常量 ====================
    
    /** 默认高度比例 */
    DEFAULT_HEIGHT_RATIO(0.6f),
    
    /** 大面板高度比例 */
    LARGE_HEIGHT_RATIO(0.8f),
    
    /** 中等面板高度比例 */
    MEDIUM_HEIGHT_RATIO(0.5f),
    
    /** 小面板高度比例 */
    SMALL_HEIGHT_RATIO(0.3f),
    
    /** 通知面板高度比例 */
    NOTIFICATION_HEIGHT_RATIO(0.25f),
    
    /** 全屏高度比例 */
    FULL_SCREEN_HEIGHT_RATIO(0.95f),
    
    // ==================== 透明度常量 ====================
    
    /** 默认遮罩透明度 */
    DEFAULT_MASK_ALPHA(0.5f),
    
    /** 高遮罩透明度 */
    HIGH_MASK_ALPHA(0.6f),
    
    /** 中等遮罩透明度 */
    MEDIUM_MASK_ALPHA(0.4f),
    
    /** 低遮罩透明度 */
    LOW_MASK_ALPHA(0.3f),
    
    /** 最小遮罩透明度 */
    MIN_MASK_ALPHA(0.2f),
    
    // ==================== 动画持续时间常量 ====================
    
    /** 默认动画持续时间 (ms) */
    DEFAULT_ANIMATION_DURATION(300L),
    
    /** 快速动画持续时间 (ms) */
    FAST_ANIMATION_DURATION(200L),
    
    /** 慢速动画持续时间 (ms) */
    SLOW_ANIMATION_DURATION(400L),
    
    /** 回弹动画持续时间 (ms) */
    BOUNCE_ANIMATION_DURATION(200L),
    
    /** 最小动画持续时间 (ms) */
    MIN_ANIMATION_DURATION(100L),
    
    // ==================== 拖拽相关常量 ====================
    
    /** 拖拽阈值比例 */
    DRAG_DISMISS_THRESHOLD(0.3f),
    
    /** 拖拽速度阈值 (px/ms) */
    DRAG_VELOCITY_THRESHOLD(0.5f),
    
    /** 最小拖拽距离 (dp) */
    MIN_DRAG_DISTANCE_DP(10f),
    
    // ==================== 字体大小常量 ====================
    
    /** 大标题字体大小 (sp) */
    TITLE_TEXT_SIZE_LARGE_SP(18f),
    
    /** 标题字体大小 (sp) */
    TITLE_TEXT_SIZE_SP(16f),
    
    /** 小标题字体大小 (sp) */
    TITLE_TEXT_SIZE_SMALL_SP(14f),
    
    /** 内容字体大小 (sp) */
    CONTENT_TEXT_SIZE_SP(14f),
    
    /** 小内容字体大小 (sp) */
    CONTENT_TEXT_SIZE_SMALL_SP(13f),
    
    /** 行间距倍数 */
    LINE_SPACING_MULTIPLIER(1.2f),
    
    /** 小行间距倍数 */
    LINE_SPACING_MULTIPLIER_SMALL(1.1f),
    
    // ==================== Z轴层级常量 ====================
    
    /** 面板基础Z轴高度 (dp) */
    PANEL_ELEVATION_DP(8f),
    
    /** 拖拽条Z轴高度 (dp) */
    DRAG_BAR_ELEVATION_DP(2f),
    
    // ==================== 像素常量（从dp转换） ====================
    
    /** 拖拽条高度 (px) - 设备密度相关 */
    DRAG_BAR_HEIGHT_PX(Math.round(4f)),
    
    /** 最小触摸区域 (px) */
    MIN_TOUCH_TARGET_PX(48 * 3), // 48dp * 3 density
    
    // ==================== 动画/行为常量 ====================
    
    /** 单帧时长 (ms) */
    FRAME_TIME_MS(16L),
    
    /** 回弹插值张力 */
    BOUNCE_OVERSHOOT_TENSION(0.5f),
    
    /** 拖拽时遮罩淡出比例系数 */
    MASK_DRAG_FADE_FACTOR(0.5f),
    
    /** 速度计算单位（每秒刻度）*/
    VELOCITY_TIME_UNITS_PER_SECOND(1000),
    
    /** 头部/消息额外行距 (dp) */
    HEADER_LINE_SPACING_EXTRA_DP(4f),
    
    /** 标题默认最大行数 */
    TITLE_MAX_LINES_DEFAULT(2),
    
    /** 消息默认最大行数 */
    MESSAGE_MAX_LINES_DEFAULT(3),
    
    /** 拖拽条按压动画时长 (ms) */
    DRAG_BAR_PRESS_ANIM_DURATION_MS(150L),
    
    /** 拖拽条按压缩放比例 */
    DRAG_BAR_PRESS_SCALE(0.95f),
    
    /** 拖拽条按压透明度 */
    DRAG_BAR_PRESS_ALPHA(0.7f),
    
    /** 拖拽进度导致的透明度降低系数 */
    DRAG_PROGRESS_ALPHA_REDUCTION(0.3f),
    
    /** 拖拽进度导致的缩放增加系数 */
    DRAG_PROGRESS_SCALE_INCREASE(0.1f),
    
    // ==================== 颜色/Alpha 常量 ====================
    
    /** 不透明Alpha值 (0-255) */
    OPAQUE_ALPHA_255(255),

    // 常用归一化区间与时间换算
    /** Alpha最小值 */
    ALPHA_MIN(0f),
    /** Alpha最大值 */
    ALPHA_MAX(1.0f),
    /** 进度最小值 */
    PROGRESS_MIN(0f),
    /** 进度最大值 */
    PROGRESS_MAX(1.0f),
    /** 标准缩放比例 */
    SCALE_NORMAL(1.0f),
    /** 每秒对应的毫秒（int） */
    MILLISECONDS_PER_SECOND_INT(1000),
    /** 每秒对应的毫秒（float） */
    MILLISECONDS_PER_SECOND_FLOAT(1000f),
    /** 高度比例下限（避免0） */
    HEIGHT_RATIO_MIN(0.1f);
    
    private final float floatValue;
    private final long longValue;
    private final int intValue;
    
    // 浮点数构造函数
    ModalPanelConstants(float value) {
        this.floatValue = value;
        this.longValue = (long) value;
        this.intValue = (int) value;
    }
    
    // 长整型构造函数
    ModalPanelConstants(long value) {
        this.longValue = value;
        this.floatValue = (float) value;
        this.intValue = (int) value;
    }
    
    // 整型构造函数
    ModalPanelConstants(int value) {
        this.intValue = value;
        this.floatValue = (float) value;
        this.longValue = (long) value;
    }
    
    public float getFloatValue() {
        return floatValue;
    }
    
    public long getLongValue() {
        return longValue;
    }
    
    public int getIntValue() {
        return intValue;
    }
    
    /**
     * 获取dp对应的px值
     * @param density 屏幕密度
     * @return px值
     */
    public int toPx(float density) {
        return Math.round(floatValue * density);
    }
    
    /**
     * 限制值在最小和最大值之间
     */
    public static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
    
    /**
     * 限制值在0.0到1.0之间
     */
    public static float clampRatio(float ratio) {
        return clamp(ratio, 0.0f, 1.0f);
    }
    
    /**
     * 限制动画持续时间
     */
    public static long clampDuration(long duration) {
        return Math.max(MIN_ANIMATION_DURATION.getLongValue(), duration);
    }
}
