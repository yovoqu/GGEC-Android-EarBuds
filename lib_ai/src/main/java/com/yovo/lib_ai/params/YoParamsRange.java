package com.yovo.lib_ai.params;

/**
 * 一个参数的合法区间。超出即裁剪，不是报错——用户填了 1.5 的温度不该让整个请求失败。
 *
 * @param min 下界（含）
 * @param max 上界（含）
 */
public record YoParamsRange(double min, double max) {

    public YoParamsRange {
        if (min > max) {
            throw new IllegalArgumentException("min must not exceed max");
        }
    }

    public boolean contains(double value) {
        return value >= min && value <= max;
    }

    /** 夹到区间内；已经在区间内的原样返回。 */
    public double clamp(double value) {
        return Math.max(min, Math.min(max, value));
    }
}
