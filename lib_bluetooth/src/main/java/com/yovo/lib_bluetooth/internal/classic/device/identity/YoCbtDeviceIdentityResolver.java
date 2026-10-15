package com.yovo.lib_bluetooth.internal.classic.device.identity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.classic.device.capability.YoCbtDeviceCapability;
import com.yovo.lib_bluetooth.internal.classic.device.snapshot.YoCbtBondedDevice;
import com.yovo.lib_bluetooth.internal.classic.device.snapshot.YoCbtObservedDevice;
import com.yovo.lib_bluetooth.internal.classic.device.snapshot.YoCbtResolvedDevice;
import com.yovo.lib_bluetooth.internal.classic.link.YoCbtAclLinkState;
import com.yovo.lib_bluetooth.internal.classic.pairing.YoCbtBondState;
import com.yovo.lib_bluetooth.internal.classic.profiles.core.YoCbtCapabilitySource;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 从扫描发现、配对缓存与 SDP 等多源合并解析设备身份信息。
 */
public final class YoCbtDeviceIdentityResolver {

    /** 从原始扫描发现数据解析设备观测信息。 */
    @NonNull
    public YoCbtObservedDevice resolveObserved(@NonNull YoCbtDeviceIdentity identity,
                                                int rssi,
                                                @Nullable YoCbtDeviceClass deviceClass,
                                                long observedAtMillis) {
        YoCbtDeviceClass resolvedClass = deviceClass != null ? deviceClass : YoCbtDeviceClass.unknown();
        return new YoCbtObservedDevice(identity, rssi, resolvedClass, observedAtMillis);
    }

    /**
     * Resolves identity from a bonded device query.
     */
    @NonNull
    public YoCbtBondedDevice resolveBonded(@NonNull YoCbtDeviceIdentity identity,
                                            @NonNull YoCbtBondState bondState,
                                            @NonNull List<UUID> cachedUuids,
                                            long queriedAtMillis) {
        return new YoCbtBondedDevice(identity, bondState, cachedUuids, queriedAtMillis);
    }

    /**
     * Merges an observed device into an existing snapshot, preferring richer data.
     *
     * @param existing     the current snapshot (may be null)
     * @param observed     the newly observed device
     * @param timestampMs  timestamp for the merge
     * @return a new identity with merged fields
     */
    @NonNull
    public YoCbtDeviceIdentity mergeIdentity(@Nullable YoCbtDeviceIdentity existing,
                                              @NonNull YoCbtObservedDevice observed,
                                              long timestampMs) {
        if (existing == null) return observed.getIdentity();

        YoCbtDeviceAlias mergedAlias = existing.getAlias().merge(observed.getIdentity().getAlias());
        String displayName = preferNonEmpty(existing.getDisplayName(),
                observed.getIdentity().getDisplayName());
        YoCbtDeviceType deviceType = preferKnown(existing.getDeviceType(),
                observed.inferDeviceType());

        return new YoCbtDeviceIdentity(
                existing.getDeviceId(),
                existing.getAddress(),
                existing.getAddressType(),
                deviceType,
                displayName
        );
    }

    /**
     * Merges capabilities from multiple sources into a single capability model.
     *
     * @param base         existing capability (may be empty)
     * @param profileTypes newly discovered profile types
     * @param source       source of the new capability information
     * @return merged capability
     */
    @NonNull
    public YoCbtDeviceCapability mergeCapability(@NonNull YoCbtDeviceCapability base,
                                                  @NonNull List<com.yovo.lib_bluetooth.internal.classic.profiles.core.YoCbtProfileType> profileTypes,
                                                  @NonNull YoCbtCapabilitySource source) {
        YoCbtDeviceCapability result = base;
        for (com.yovo.lib_bluetooth.internal.classic.profiles.core.YoCbtProfileType type : profileTypes) {
            result = result.withProfile(type, source);
        }
        return result;
    }

    /**
     * Builds a fully resolved device from accumulated data across all sources.
     */
    @NonNull
    public YoCbtResolvedDevice resolve(@NonNull YoCbtDeviceIdentity identity,
                                        @NonNull YoCbtDeviceAlias alias,
                                        @NonNull YoCbtDeviceClass deviceClass,
                                        @NonNull YoCbtDeviceCapability capability,
                                        @NonNull YoCbtBondState bondState,
                                        @NonNull YoCbtAclLinkState aclLinkState,
                                        @NonNull List<UUID> resolvedUuids,
                                        @NonNull Map<YoCbtCapabilitySource, Integer> sourceConfidence,
                                        long resolvedAtMillis) {
        return new YoCbtResolvedDevice(identity, alias, deviceClass, capability,
                bondState, aclLinkState, resolvedUuids, sourceConfidence, resolvedAtMillis);
    }

    // ── helpers ──────────────────────────────────────────────────

    @NonNull
    private static String preferNonEmpty(@NonNull String existing, @NonNull String incoming) {
        if (!existing.isEmpty()) return existing;
        return incoming;
    }

    @NonNull
    private static YoCbtDeviceType preferKnown(@NonNull YoCbtDeviceType existing,
                                                @NonNull YoCbtDeviceType incoming) {
        if (existing != YoCbtDeviceType.UNKNOWN) return existing;
        return incoming;
    }
}
