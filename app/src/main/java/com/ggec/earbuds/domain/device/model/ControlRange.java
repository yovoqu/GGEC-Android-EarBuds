package com.ggec.earbuds.domain.device.model;

/**
 * LEVEL 类能力的取值域。由 biz 描述符提供，界面不做推导、不造哨兵值。
 *
 * <p>目录不变量：{@code kind == LEVEL} 的能力必须带 range，缺 range 的目录在构造期就失败。</p>
 */
public record ControlRange(int min, int max, int step) {

    public ControlRange {
        if (step <= 0) {
            throw new IllegalArgumentException("step must be positive but was " + step);
        }
        if (min > max) {
            throw new IllegalArgumentException("min " + min + " is greater than max " + max);
        }
    }
}
