package com.yovo.yotheme.constants;

/**
 * @author tingfeng.yang
 * @date 2025-08-07
 * @description 开关控件常量枚举
 * @note 存储YoSwitch控件中使用的所有常量值
 */
public enum SwitchConstants {
    // 开关控件尺寸
    ASPECT_RATIO(1.8f),
    HEIGHT_FACTOR(1.0f),
    TRACK_PADDING(3),
    THUMB_PADDING(3),
    ANIMATION_DURATION(200),
    
    // 特定尺寸常量
    DEFAULT_WIDTH_DP(36),
    DEFAULT_HEIGHT_DP(20),
    ROUNDING_FLOAT(0.5f),
    
    // 位置和边界常量
    POSITION_MIN(0),
    POSITION_MAX(1),
    PADDING_NONE(0),
    TRACK_START_X(0),
    TRACK_START_Y(0),
    
    // 计算常量
    HALF_FACTOR(0.5f),
    MINIMUM_DURATION(0),
    
    // 半径计算
    RADIUS_DIVISOR(2);
    
    // 常量值（整型）
    private final int intValue;
    
    // 常量值（浮点型）
    private final float floatValue;
    
    /**
     * 整型构造方法
     * 
     * @param value 常量值(整型)
     */
    SwitchConstants(int value) {
        this.intValue = value;
        this.floatValue = value;
    }
    
    /**
     * 浮点型构造方法
     * 
     * @param value 常量值(浮点型)
     */
    SwitchConstants(float value) {
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