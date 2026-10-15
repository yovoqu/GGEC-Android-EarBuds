package com.yovo.yotheme.constants;

/**
 * @author tingfeng.yang
 * @date 2025-08-07
 * @description 按钮常量枚举
 * @note 存储YoButton控件中使用的所有常量值
 */
public enum ButtonConstants {
    // 尺寸常量
    CORNER_RADIUS(32),
    MIN_WIDTH(100),
    MIN_HEIGHT(40),
    PADDING_HORIZONTAL(16),
    PADDING_VERTICAL(8),
    TEXT_SIZE(16),
    
    // 文本自适应常量
    MIN_TEXT_SIZE(12),
    MAX_TEXT_SIZE(18),
    AUTO_SIZE_STEP_GRANULARITY(1),
    
    // 阴影常量
    ELEVATION_NONE(0),
    
    // 文本自适应类型
    AUTO_SIZE_TEXT_TYPE_UNIFORM(1),
    
    // 浮点数常量
    ROUNDING_FLOAT(0.5f);
    
    // 常量值（整型）
    private final int intValue;
    
    // 常量值（浮点型）
    private final float floatValue;
    
    /**
     * 整型构造方法
     * 
     * @param value 常量值(整型)
     */
    ButtonConstants(int value) {
        this.intValue = value;
        this.floatValue = value;
    }
    
    /**
     * 浮点型构造方法
     * 
     * @param value 常量值(浮点型)
     */
    ButtonConstants(float value) {
        this.intValue = (int) value;
        this.floatValue = value;
    }
    
    /**
     * 获取整型常量值
     * 
     * @return 常量值(整型)
     */
    public int getValue() {
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