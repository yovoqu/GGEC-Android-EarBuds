package com.yovo.yotheme.constants;

/**
 * @author tingfeng.yang
 * @date 2025-09-28
 * @description 滑动条控件常量枚举
 * @note 存储YoSlider控件中使用的所有常量值
 */
public enum SliderConstants {
    // 滑动条基础尺寸
    DEFAULT_HEIGHT_DP(24),
    DEFAULT_MIN_WIDTH_DP(120),
    
    // 滑块尺寸
    THUMB_RADIUS_DP(8),
    THUMB_DIAMETER_FACTOR(2),
    
    // 轨道尺寸
    TRACK_HEIGHT_DP(24), // 与默认高度保持一致
    TRACK_RADIUS_FACTOR(0.5f), // 轨道半径 = 高度 * 0.5
    
    // 间距设置
    UNIFORM_SPACING_DP(4), // 滑块与轨道边界的统一间距
    PROGRESS_EXTENSION_DP(4), // 进度条延伸到滑块边界外的距离
    
    // 数值范围
    DEFAULT_MIN_VALUE(0),
    DEFAULT_MAX_VALUE(100),
    DEFAULT_CURRENT_VALUE(50),
    
    // 动画
    ANIMATION_DURATION(200),
    
    // 触摸相关
    TOUCH_SLOP_DP(8), // 触摸容错范围
    
    // 绘制相关
    ANTI_ALIAS_FLAG(1),
    
    // 计算常量
    HALF_FACTOR(0.5f),
    DENSITY_ROUNDING(0.5f),
    
    // 边界值
    POSITION_MIN(0),
    POSITION_MAX(1),
    
    // 最小值限制
    MIN_VALUE_RANGE(1), // 最大值和最小值的最小差值
    MIN_ANIMATION_DURATION(50);
    
    // 常量值（整型）
    private final int intValue;
    
    // 常量值（浮点型）
    private final float floatValue;
    
    /**
     * 整型构造方法
     * 
     * @param value 常量值(整型)
     */
    SliderConstants(int value) {
        this.intValue = value;
        this.floatValue = value;
    }
    
    /**
     * 浮点型构造方法
     * 
     * @param value 常量值(浮点型)
     */
    SliderConstants(float value) {
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
