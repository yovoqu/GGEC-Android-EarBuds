package com.yovo.yotheme.constants;

/**
 * @author tingfeng.yang
 * @date 2025-10-25
 * @description 分段按钮组件常量枚举
 * @note 定义YoSegmentButton组件中使用的所有常量值
 *       参考 HarmonyOS 设计规范
 */
public enum SegmentButtonConstants {
    // 组件尺寸 (DP)
    DEFAULT_HEIGHT_DP(44),                      // 默认高度
    DEFAULT_CORNER_RADIUS_DP(32),               // 圆角半径
    BORDER_WIDTH_DP(0.5f),                      // 外边框宽度
    INDICATOR_PADDING_DP(2),                    // 选中指示器内边距
    
    // 文字相关
    TEXT_SIZE_SP(16),                           // 文字大小
    TEXT_NORMAL_SCALE(1.0f),                    // 文字正常态缩放比例
    TEXT_SELECTED_SCALE(1.05f),                 // 文字选中态缩放比例
    
    // 动画时长 (MS)
    SLIDE_ANIMATION_DURATION_MS(250),           // 指示器滑动动画时长
    TEXT_STYLE_ANIMATION_DURATION_MS(200);      // 文字样式变化动画时长
    
    // 常量值（整型）
    private final int intValue;
    
    // 常量值（浮点型）
    private final float floatValue;
    
    /**
     * 整型构造方法
     * 
     * @param value 常量值(整型)
     */
    SegmentButtonConstants(int value) {
        this.intValue = value;
        this.floatValue = value;
    }
    
    /**
     * 浮点型构造方法
     * 
     * @param value 常量值(浮点型)
     */
    SegmentButtonConstants(float value) {
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
