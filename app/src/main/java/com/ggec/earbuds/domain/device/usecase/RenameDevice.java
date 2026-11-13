package com.ggec.earbuds.domain.device.usecase;

import com.ggec.earbuds.domain.device.model.DeviceId;
import com.ggec.earbuds.domain.device.port.DeviceRosterRepository;

import java.util.Objects;

/** 改设备别名（只改台账，不动设备自身名称）。 */
public final class RenameDevice {

    private final DeviceRosterRepository roster;

    public RenameDevice(DeviceRosterRepository roster) {
        this.roster = Objects.requireNonNull(roster, "roster");
    }

    /** @return 该设备是否在台账里；不存在时不改动任何数据。 */
    public boolean execute(DeviceId id, String alias) {
        Objects.requireNonNull(id, "id");
        String nextAlias = Objects.requireNonNull(alias, "alias").trim();
        boolean[] found = {false};
        roster.update(current -> current.find(id)
                .map(record -> {
                    found[0] = true;
                    return current.upsert(record.renamed(nextAlias));
                })
                .orElse(current));
        return found[0];
    }
}
