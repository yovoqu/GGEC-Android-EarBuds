package com.ggec.earbuds.domain.device.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 设备台账：用户管理过的设备 + 当前活动设备指针。
 *
 * <p>不可变；所有变更返回新台账。身份去重只认 {@link DeviceId}，排序规则单点在
 * {@link #sorted()}：活动设备置顶，其余按最后有事实的时间降序，同时间按 key 字典序稳定。</p>
 */
public record DeviceRoster(List<DeviceRecord> devices, Optional<DeviceId> activeId) {

    public DeviceRoster {
        devices = List.copyOf(Objects.requireNonNull(devices, "devices"));
        Objects.requireNonNull(activeId, "activeId");
    }

    public static DeviceRoster empty() {
        return new DeviceRoster(List.of(), Optional.empty());
    }

    public Optional<DeviceRecord> find(DeviceId id) {
        Objects.requireNonNull(id, "id");
        for (DeviceRecord record : devices) {
            if (record.id().equals(id)) {
                return Optional.of(record);
            }
        }
        return Optional.empty();
    }

    public Optional<DeviceRecord> activeRecord() {
        return activeId.flatMap(this::find);
    }

    /** 同身份覆盖，其余保序；新设备追加在末尾。 */
    public DeviceRoster upsert(DeviceRecord record) {
        Objects.requireNonNull(record, "record");
        List<DeviceRecord> next = new ArrayList<>(devices.size() + 1);
        boolean replaced = false;
        for (DeviceRecord existing : devices) {
            if (existing.id().equals(record.id())) {
                next.add(record);
                replaced = true;
            } else {
                next.add(existing);
            }
        }
        if (!replaced) {
            next.add(record);
        }
        return new DeviceRoster(next, activeId);
    }

    /** 删除设备，并从活动指针摘除（不自动改选下一台：选择权在用户）。 */
    public DeviceRoster without(DeviceId id) {
        Objects.requireNonNull(id, "id");
        List<DeviceRecord> next = new ArrayList<>(devices.size());
        for (DeviceRecord record : devices) {
            if (!record.id().equals(id)) {
                next.add(record);
            }
        }
        Optional<DeviceId> nextActive = activeId.filter(id::equals).isPresent()
                ? Optional.empty() : activeId;
        return new DeviceRoster(next, nextActive);
    }

    public DeviceRoster withActive(Optional<DeviceId> id) {
        Objects.requireNonNull(id, "id");
        return new DeviceRoster(devices, id);
    }

    public List<DeviceRecord> sorted() {
        List<DeviceRecord> copy = new ArrayList<>(devices);
        Optional<DeviceId> active = activeId;
        copy.sort(Comparator
                .comparing((DeviceRecord record) -> !isActive(record, active))
                .thenComparingLong(record -> -record.status().lastSeenMillis())
                .thenComparing(record -> record.id().key()));
        return List.copyOf(copy);
    }

    private static boolean isActive(DeviceRecord record, Optional<DeviceId> active) {
        return active.map(id -> id.equals(record.id())).orElse(false);
    }
}
