package com.ggec.earbuds.domain.device.model;

import java.util.Objects;

/** 项目名称 + 变种 对应的能力声明与参数表。 */
public record DeviceProfile(
        DeviceModel model,
        DeviceVariant variant,
        CapabilitySet capabilities,
        ProfileParams params
) {

    public DeviceProfile {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(variant, "variant");
        Objects.requireNonNull(capabilities, "capabilities");
        Objects.requireNonNull(params, "params");
    }

    public boolean supports(Capability capability) {
        return capabilities.has(capability);
    }
}
