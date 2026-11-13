package com.ggec.earbuds.domain.device.model;

import java.util.Objects;

/** 一次连接尝试的目标：身份 + 建立双模链路所需的其余字段。 */
public record DeviceTarget(DeviceId id, DeviceIdentity identity) {

    public DeviceTarget {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(identity, "identity");
    }

    public static DeviceTarget of(ScanResult scan) {
        Objects.requireNonNull(scan, "scan");
        return new DeviceTarget(
                new DeviceId(scan.modelId(), scan.bleAddress()),
                new DeviceIdentity(scan.bleName(), scan.classicAddress(), scan.classicName()));
    }

    public static DeviceTarget of(DeviceRecord record) {
        Objects.requireNonNull(record, "record");
        return new DeviceTarget(record.id(), record.identity());
    }

    public boolean is(DeviceId other) {
        return id.equals(other);
    }
}
