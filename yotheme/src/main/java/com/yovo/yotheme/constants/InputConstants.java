package com.yovo.yotheme.constants;

/**
 * @author tingfeng.yang
 * @date 2026-06-13
 * @description 输入框控件常量枚举
 * @note 存储 YoInput 控件中使用的所有常量值
 */
public enum InputConstants {
    CORNER_RADIUS(32),
    MIN_HEIGHT(44),
    PADDING_HORIZONTAL(16),
    PADDING_VERTICAL(10),
    TEXT_SIZE(14),
    BORDER_WIDTH(1),
    CURSOR_WIDTH(2),
    CURSOR_BLINK_INTERVAL(500),
    ROUNDING_FLOAT(0.5f);

    private final int intValue;
    private final float floatValue;

    InputConstants(int value) {
        this.intValue = value;
        this.floatValue = value;
    }

    InputConstants(float value) {
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
