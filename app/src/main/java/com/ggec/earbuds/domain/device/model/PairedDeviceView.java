package com.ggec.earbuds.domain.device.model;

import java.util.Objects;

/** 双连接设备列表里的一台已配对设备。字段全部来自设备回报，APP 不解释 TLV。 */
public record PairedDeviceView(String name, String address, PairedDeviceType type,
                               boolean connected, PairedBusiness business,
                               boolean currentDevice, boolean priority) {

    public PairedDeviceView {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(business, "business");
    }

    public enum PairedDeviceType {
        OTHER,
        PHONE,
        COMPUTER,
        PAD,
        TV,
        WEARABLE
    }

    public enum PairedBusiness {
        IDLE,
        MUSIC,
        CALL
    }
}
