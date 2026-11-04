package com.ggec.earbuds.domain.device.model;

import java.util.Objects;

/** 项目名称：项目前缀 + 设备类型，例如 HS + 01 = HS01。 */
public record DeviceModel(String id, String family, String type) {

    public DeviceModel {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(family, "family");
        Objects.requireNonNull(type, "type");
    }

    public static DeviceModel of(String family, String type) {
        return new DeviceModel(family + type, family, type);
    }
}
