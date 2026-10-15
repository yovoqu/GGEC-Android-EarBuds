package com.yovo.lib_bluetooth.internal.classic.pairing;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceIdentity;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceType;
import com.yovo.lib_bluetooth.internal.classic.device.snapshot.YoCbtBondedDevice;
import com.yovo.lib_bluetooth.internal.classic.device.snapshot.YoCbtDeviceSnapshot;
import com.yovo.lib_bluetooth.internal.classic.device.snapshot.YoCbtDeviceSnapshotStore;
import com.yovo.lib_bluetooth.internal.classic.profiles.core.YoCbtCapabilitySource;
import com.yovo.lib_bluetooth.internal.classic.profiles.core.YoCbtProfileType;
import com.yovo.lib_bluetooth.internal.classic.service.cache.YoCbtServiceCache;
import com.yovo.lib_bluetooth.internal.classic.service.channel.YoCbtServiceChannelRegistry;
import com.yovo.lib_bluetooth.internal.classic.service.model.YoCbtSdpSource;
import com.yovo.lib_bluetooth.internal.classic.service.resolve.YoCbtProfileUuidMapper;
import com.yovo.lib_bluetooth.internal.classic.service.resolve.YoCbtServiceRecordFactory;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 已配对设备协调器，加载 Bond 列表并分发状态变更。
 */
public final class YoCbtBondedDeviceCoordinator {

    private static final Set<YoCbtDeviceType> SUPPORTED_TYPES =
            EnumSet.of(YoCbtDeviceType.CLASSIC, YoCbtDeviceType.DUAL);

    private final YoCbtBondedDeviceLoader loader;
    private final YoCbtBondedDeviceRepository repository;
    private final YoCbtDeviceSnapshotStore snapshotStore;
    private final YoCbtServiceCache serviceCache;
    private final YoCbtServiceChannelRegistry channelRegistry;
    private final YoCbtProfileUuidMapper profileUuidMapper;

    public YoCbtBondedDeviceCoordinator(
            @NonNull YoCbtBondedDeviceLoader loader,
            @NonNull YoCbtBondedDeviceRepository repository,
            @NonNull YoCbtDeviceSnapshotStore snapshotStore,
            @NonNull YoCbtServiceCache serviceCache,
            @NonNull YoCbtServiceChannelRegistry channelRegistry,
            @NonNull YoCbtProfileUuidMapper profileUuidMapper) {
        this.loader = loader;
        this.repository = repository;
        this.snapshotStore = snapshotStore;
        this.serviceCache = serviceCache;
        this.channelRegistry = channelRegistry;
        this.profileUuidMapper = profileUuidMapper;
    }

    @NonNull
    public List<YoCbtDeviceSnapshot> syncAll(@NonNull Context context) {
        List<YoCbtBondedDevice> bondedDevices = loader.load(context);
        List<YoCbtDeviceSnapshot> snapshots = new ArrayList<>(bondedDevices.size());
        for (YoCbtBondedDevice bonded : bondedDevices) {
            YoCbtDeviceSnapshot snapshot = persistBondedDevice(bonded);
            if (snapshot != null) snapshots.add(snapshot);
        }
        return snapshots;
    }

    @Nullable
    public YoCbtDeviceSnapshot syncOne(@NonNull Context context, @NonNull YoCbtDeviceId deviceId) {
        for (YoCbtBondedDevice bonded : loader.load(context)) {
            if (!bonded.getDeviceId().equals(deviceId)) continue;
            return persistBondedDevice(bonded);
        }
        return null;
    }

    @Nullable
    private YoCbtDeviceSnapshot persistBondedDevice(@NonNull YoCbtBondedDevice bonded) {
        YoCbtDeviceIdentity identity = bonded.getIdentity();
        if (!SUPPORTED_TYPES.contains(identity.getDeviceType())) {
            return null;
        }

        long now = bonded.getQueriedAtMillis() > 0 ? bonded.getQueriedAtMillis()
                : System.currentTimeMillis();
        List<UUID> cachedUuids = bonded.getCachedUuids();

        if (!cachedUuids.isEmpty()) {
            serviceCache.put(YoCbtServiceRecordFactory.fromUuidList(
                    identity.getDeviceId(),
                    cachedUuids,
                    YoCbtSdpSource.BONDED_CACHE,
                    channelRegistry,
                    now));
        }

        YoCbtDeviceSnapshot snapshot = snapshotStore.updateBond(
                identity, bonded.getBondState(), now);
        repository.update(snapshot);

        for (YoCbtProfileType profileType : profileUuidMapper.mapProfiles(cachedUuids)) {
            snapshotStore.updateCapability(identity, profileType,
                    YoCbtCapabilitySource.BONDED_CACHE, now);
        }
        return snapshotStore.get(identity.getDeviceId());
    }
}
