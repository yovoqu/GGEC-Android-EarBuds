package com.ggec.earbuds.domain.device.model;

/** 每个 Profile 的差异化参数表。 */
public record ProfileParams(
        int eqBandCount,
        boolean supportsCustomEq,
        boolean supportsDualConnection,
        boolean supportsServiceStatus,
        boolean tapSplitsEar
) {

    public static ProfileParams base() {
        return new ProfileParams(0, false, false, false, false);
    }

    public static ProfileParams extended() {
        return new ProfileParams(10, true, true, true, true);
    }
}
