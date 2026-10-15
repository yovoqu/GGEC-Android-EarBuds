package com.yovo.lib_bluetooth.internal.classic.device.snapshot;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceIdentity;
import com.yovo.lib_bluetooth.internal.classic.pairing.YoCbtBondState;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 已与本地适配器配对的设备，携带配对状态及从配对缓存获取的 UUID 列表。
 */
public final class YoCbtBondedDevice {
    private final YoCbtDeviceIdentity identity;
    private final YoCbtBondState bondState;
    private final List<UUID> cachedUuids;
    private final long queriedAtMillis;

    public YoCbtBondedDevice(@NonNull YoCbtDeviceIdentity identity,
                             @NonNull YoCbtBondState bondState,
                             @NonNull List<UUID> cachedUuids,
                             long queriedAtMillis) {
        this.identity = identity;
        this.bondState = bondState;
        this.cachedUuids = Collections.unmodifiableList(cachedUuids);
        this.queriedAtMillis = queriedAtMillis;
    }

    @NonNull
    public YoCbtDeviceIdentity getIdentity() {
        return identity;
    }

    @NonNull
    public YoCbtDeviceId getDeviceId() {
        return identity.getDeviceId();
    }

    @NonNull
    public YoCbtBondState getBondState() {
        return bondState;
    }

    @NonNull
    public List<UUID> getCachedUuids() {
        return cachedUuids;
    }

    public long getQueriedAtMillis() {
        return queriedAtMillis;
    }

    public boolean isBonded() {
        return bondState == YoCbtBondState.BONDED;
    }

    public boolean hasCachedUuids() {
        return !cachedUuids.isEmpty();
    }
}
