package com.ggec.earbuds.domain.device.model;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 当前设备已上报的控制值，按能力 id 索引。
 *
 * <p>只有一个读取口：{@link #value(String)} 返回 {@link Optional}。没有值就是没有值——
 * 不提供 fallback 参数、不设「未知」哨兵值、不在缺值时抛异常；界面按
 * {@link ControlValue} 的 sealed 分支渲染「未上报」。</p>
 */
public record DeviceState(Map<String, ControlValue> values) {

    public DeviceState {
        Objects.requireNonNull(values, "values");
        values = Map.copyOf(values);
    }

    /** 没有该能力 id，或该能力本轮没有上报值，都返回空。 */
    public Optional<ControlValue> value(String capabilityId) {
        return Optional.ofNullable(values.get(Objects.requireNonNull(capabilityId, "capabilityId")));
    }
}
