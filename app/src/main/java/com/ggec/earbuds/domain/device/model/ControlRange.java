package com.ggec.earbuds.domain.device.model;

import java.util.OptionalInt;

/**
 * LEVEL/CURVE 类能力的取值域。由 biz 描述符提供，界面不做推导、不造哨兵值。
 *
 * <p>目录不变量：{@code kind == LEVEL} 的能力必须带 range，缺 range 的目录在构造期就失败。</p>
 *
 * <p>{@code neutral} 是描述符声明的中立档（自定义 EQ 即协议 {@code 0x0C} = 0 dB）。空表示这条能力
 * 没有声明中立档：这里不拿 {@code min} 顶上，回落策略只写在真正需要一个起点的渲染侧，并注明它是
 * 显式产品默认。</p>
 */
public record ControlRange(int min, int max, int step, OptionalInt neutral) {

    public ControlRange {
        if (step <= 0) {
            throw new IllegalArgumentException("step must be positive but was " + step);
        }
        if (min > max) {
            throw new IllegalArgumentException("min " + min + " is greater than max " + max);
        }
        if (neutral == null) {
            throw new IllegalArgumentException("neutral must be OptionalInt.empty(), not null");
        }
        if (neutral.isPresent() && (neutral.getAsInt() < min || neutral.getAsInt() > max)) {
            throw new IllegalArgumentException("neutral " + neutral.getAsInt()
                    + " is outside [" + min + ", " + max + "]");
        }
    }

    /** 没有中立档的值域：显式声明缺失，不是补一个默认值。 */
    public static ControlRange of(int min, int max, int step) {
        return new ControlRange(min, max, step, OptionalInt.empty());
    }
}
