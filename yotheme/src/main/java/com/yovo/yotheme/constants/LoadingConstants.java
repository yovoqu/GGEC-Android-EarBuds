package com.yovo.yotheme.constants;

/**
 * @author tingfeng.yang
 * @date 2026-06-15
 * @description YoLoading 组件常量枚举
 * @note 存储 YoLoading 控件中使用的尺寸、透明度与动画常量
 */
public enum LoadingConstants {
    WINDOW_SIZE_DP(72),
    PANEL_ALPHA(220),
    PANEL_CORNER_FACTOR(0.22f),

    RING_TRACK_ALPHA(48),
    RING_ALPHA(255),
    DOT_ALPHA_MIN(90),
    DOT_ALPHA_RANGE(140),
    DOT_ALPHA_BASE(180),

    RING_STROKE_MIN_PX(3),
    RING_STROKE_FACTOR(0.055f),
    RING_RADIUS_FACTOR(0.24f),
    RING_SWEEP_DEGREES(250),

    DOT_RADIUS_FACTOR(0.035f),
    DOT_Y_OFFSET_FACTOR(0.16f),
    DOT_GAP_FACTOR(0.12f),
    DOT_PHASE_DEGREES(120),

    ROTATION_DURATION_MS(920);

    private final int intValue;
    private final float floatValue;

    LoadingConstants(int value) {
        this.intValue = value;
        this.floatValue = value;
    }

    LoadingConstants(float value) {
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
