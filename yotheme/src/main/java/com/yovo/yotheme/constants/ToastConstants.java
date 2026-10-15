package com.yovo.yotheme.constants;

/**
 * @author tingfeng.yang
 * @date 2026-06-24
 * @description YoToast 组件常量枚举
 * @note 存储 YoToast 控件中使用的尺寸、透明度与显示时长常量
 */
public enum ToastConstants {
    TEXT_SIZE_SP(14),
    CORNER_RADIUS_DP(16),
    PADDING_HORIZONTAL_DP(20),
    PADDING_VERTICAL_DP(12),
    MARGIN_HORIZONTAL_DP(32),

    /** 背景：黑色 + 半透明（0~255），约 85% 不透明度 */
    BACKGROUND_ALPHA(217),
    /** 内容最大宽度占屏幕宽度比例 */
    MAX_WIDTH_FACTOR(0.8f),
    LINE_SPACING_MULTIPLIER(1.2f),

    DURATION_SHORT_MS(2000),
    DURATION_LONG_MS(3500);

    private final int intValue;
    private final float floatValue;

    ToastConstants(int value) {
        this.intValue = value;
        this.floatValue = value;
    }

    ToastConstants(float value) {
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
