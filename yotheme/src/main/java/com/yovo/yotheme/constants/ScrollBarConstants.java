package com.yovo.yotheme.constants;

/**
 * @author tingfeng.yang
 * @date 2025-10-23
 * @description 滚动条组件常量枚举
 * @note 定义YoScrollBar组件中使用的所有常量值
 */
public enum ScrollBarConstants {
    // 滚动条基础尺寸
    SCROLLBAR_WIDTH_DP(4),                  // 默认滚动条宽度
    SCROLLBAR_WIDTH_HOVER_DP(6),           // 悬浮/拖拽时的宽度
    SCROLLBAR_MIN_HEIGHT_DP(24),           // 滚动条最小高度
    
    // 滚动条位置
    SCROLLBAR_MARGIN_END_DP(2),            // 距离右边缘的间距
    SCROLLBAR_MARGIN_VERTICAL_DP(4),       // 距离顶部和底部的间距
    
    // 滚动条圆角
    SCROLLBAR_CORNER_RADIUS_DP(2),         // 默认圆角
    SCROLLBAR_CORNER_RADIUS_HOVER_DP(3),   // 悬浮/拖拽时的圆角
    
    // 滚动条透明度
    SCROLLBAR_ALPHA_NORMAL(0.3f),          // 默认透明度
    SCROLLBAR_ALPHA_HOVER(0.5f),           // 悬浮时透明度
    SCROLLBAR_ALPHA_PRESS(0.7f),           // 按下时透明度
    SCROLLBAR_ALPHA_DRAGGING(0.8f),        // 拖拽时透明度
    
    // 动画
    ANIMATION_DURATION_STATE_CHANGE(150),  // 状态变化动画时长
    ANIMATION_DURATION_FADE_OUT(300),      // 淡出动画时长
    ANIMATION_DURATION_FADE_IN(150),       // 淡入动画时长
    
    // 自动隐藏
    AUTO_HIDE_DELAY_MS(1500),              // 自动隐藏延迟
    
    // 触摸相关
    TOUCH_SLOP_DP(8),                      // 触摸容错范围
    THUMB_TOUCH_EXPANSION_DP(12),          // 滚动条点击区域扩展
    
    // 滚动行为
    MIN_SCROLL_DISTANCE_PX(1),             // 最小滚动距离
    
    // 计算常量
    HALF_FACTOR(0.5f),
    FULL_ALPHA(1.0f),
    ZERO_ALPHA(0.0f);
    
    private final int intValue;
    private final float floatValue;
    
    ScrollBarConstants(int value) {
        this.intValue = value;
        this.floatValue = value;
    }
    
    ScrollBarConstants(float value) {
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

