package com.ggec.earbuds.domain.device.model;

import java.util.List;
import java.util.Objects;

/** 双连接页面的整体状态：是否开启、当前优先设备地址、已配对设备列表。 */
public record DualConnectionView(boolean dualEnabled, String priorityAddress,
                                 List<PairedDeviceView> devices) {

    public DualConnectionView {
        Objects.requireNonNull(priorityAddress, "priorityAddress");
        devices = List.copyOf(Objects.requireNonNull(devices, "devices"));
    }
}
