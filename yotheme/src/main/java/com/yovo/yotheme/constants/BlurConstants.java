package com.yovo.yotheme.constants;

/**
 * @author tingfeng.yang
 * @date 2025-11-27
 * @description 模糊背景常量枚举
 * @note 存储YoBlurBackgroundView组件中使用的所有常量值
 */
public enum BlurConstants {
    // 模糊半径常量
    BLUR_RADIUS_DEFAULT(15),          // 默认模糊半径
    BLUR_RADIUS_LIGHT(10),            // 轻微模糊半径  
    BLUR_RADIUS_STRONG(20),           // 强烈模糊半径
    BLUR_RADIUS_EXTRA_STRONG(25),     // 超强模糊半径
    
    // 圆角常量
    CORNER_RADIUS_DIALOG(16),         // 对话框圆角
    CORNER_RADIUS_CARD(12),           // 卡片圆角
    CORNER_RADIUS_BOTTOM_SHEET(24),   // 底部面板圆角
    CORNER_RADIUS_ROUNDED(8),         // 轻微圆角
    
    // 透明度常量
    OVERLAY_ALPHA_DEFAULT(230),       // 默认遮罩层透明度 (90%)
    OVERLAY_ALPHA_LIGHT(180),         // 轻微遮罩层透明度 (70%)
    OVERLAY_ALPHA_HEAVY(250),         // 重度遮罩层透明度 (98%)
    
    // 边框常量
    BORDER_WIDTH_DEFAULT(0),          // 默认边框宽度
    BORDER_WIDTH_THIN(1),             // 细边框
    BORDER_WIDTH_MEDIUM(2),           // 中等边框
    BORDER_WIDTH_THICK(3);            // 粗边框

    // 常量值
    private final int intValue;

    /**
     * 构造方法 (整型值)
     *
     * @param value 常量值
     */
    BlurConstants(int value) {
        this.intValue = value;
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
     * 获取浮点型常量值
     *
     * @return 常量值 (转换为float)
     */
    public float getFloatValue() {
        return (float) intValue;
    }
}
