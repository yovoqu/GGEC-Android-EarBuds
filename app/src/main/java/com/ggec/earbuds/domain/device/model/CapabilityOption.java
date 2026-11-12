package com.ggec.earbuds.domain.device.model;

import java.util.Objects;

/**
 * 一个可选项：{@code id} 是协议/SDK 的选择值，{@code title} 是界面文案。
 *
 * <p>两者都来自 {@code biz_ggec_hs} 的能力描述符，APP 不自造选项、不改写取值。</p>
 */
public record CapabilityOption(String id, String title) {

    public CapabilityOption {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
    }
}
