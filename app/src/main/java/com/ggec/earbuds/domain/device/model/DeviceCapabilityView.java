package com.ggec.earbuds.domain.device.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * APP 的呈现契约：只描述「怎么呈现」，不含任何机型知识。数据全部来自 biz 目录。
 *
 * <p>{@code id} 是 {@code biz_ggec_hs} 的能力标识字符串（{@code HsDevice.Capability.name()}）；
 * {@code title}、{@code group}、{@code kind}、{@code options}、{@code range} 全部照抄 biz 描述符，
 * APP 不做文案、分组、控件类型或取值域的推导。</p>
 *
 * <p>{@code range} 只有 LEVEL 类能力会有；其余能力为空。</p>
 */
public record DeviceCapabilityView(String id, String title,
                                   DeviceCapabilityGroup group,
                                   DeviceCapabilityKind kind,
                                   List<CapabilityOption> options,
                                   Optional<ControlRange> range) {

    public DeviceCapabilityView {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(group, "group");
        Objects.requireNonNull(kind, "kind");
        options = List.copyOf(Objects.requireNonNull(options, "options"));
        Objects.requireNonNull(range, "range");
    }
}
