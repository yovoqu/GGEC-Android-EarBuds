package com.ggec.earbuds.domain.device.model;

import java.util.List;
import java.util.Objects;

/**
 * APP 的控制值词汇：与 {@code biz_ggec_hs} 的 {@code HsControl.Value} 一一映射，
 * 映射单点在 {@code provider/biz_ggec_hs/HsValueMapper}，穷尽分支、无兜底。
 */
public sealed interface ControlValue {

    record Toggle(boolean enabled) implements ControlValue {
    }

    record Level(int value) implements ControlValue {
    }

    record Choice(String optionId) implements ControlValue {
        public Choice {
            Objects.requireNonNull(optionId, "optionId");
        }
    }

    /** 自定义 EQ 曲线：{@code name} 是这条自定义音效在 APP 侧的命名（设备协议只承载 points）。 */
    record Curve(String name, List<Integer> points) implements ControlValue {
        public Curve {
            name = name == null ? "" : name;
            points = List.copyOf(Objects.requireNonNull(points, "points"));
        }
    }
}
