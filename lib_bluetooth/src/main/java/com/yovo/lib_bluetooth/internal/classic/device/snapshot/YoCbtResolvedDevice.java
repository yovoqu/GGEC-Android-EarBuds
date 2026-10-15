package com.yovo.lib_bluetooth.internal.classic.device.snapshot;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.classic.device.capability.YoCbtDeviceCapability;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceAlias;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceClass;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceIdentity;
import com.yovo.lib_bluetooth.internal.classic.link.YoCbtAclLinkState;
import com.yovo.lib_bluetooth.internal.classic.pairing.YoCbtBondState;
import com.yovo.lib_bluetooth.internal.classic.profiles.core.YoCbtCapabilitySource;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 合并扫描、配对、SDP 与 Profile 能力后的完整经典蓝牙设备视图，由 {@link YoCbtDeviceIdentityResolver} 构建。
 */
public final class YoCbtResolvedDevice {
    private final YoCbtDeviceIdentity identity;
    private final YoCbtDeviceAlias alias;
    private final YoCbtDeviceClass deviceClass;
    private final YoCbtDeviceCapability capability;
    private final YoCbtBondState bondState;
    private final YoCbtAclLinkState aclLinkState;
    private final List<UUID> resolvedUuids;
    private final Map<YoCbtCapabilitySource, Integer> sourceConfidence;
    private final long resolvedAtMillis;

    public YoCbtResolvedDevice(@NonNull YoCbtDeviceIdentity identity,
                               @NonNull YoCbtDeviceAlias alias,
                               @NonNull YoCbtDeviceClass deviceClass,
                               @NonNull YoCbtDeviceCapability capability,
                               @NonNull YoCbtBondState bondState,
                               @NonNull YoCbtAclLinkState aclLinkState,
                               @NonNull List<UUID> resolvedUuids,
                               @NonNull Map<YoCbtCapabilitySource, Integer> sourceConfidence,
                               long resolvedAtMillis) {
        this.identity = identity;
        this.alias = alias;
        this.deviceClass = deviceClass;
        this.capability = capability;
        this.bondState = bondState;
        this.aclLinkState = aclLinkState;
        this.resolvedUuids = Collections.unmodifiableList(resolvedUuids);
        this.sourceConfidence = Collections.unmodifiableMap(new EnumMap<>(sourceConfidence));
        this.resolvedAtMillis = resolvedAtMillis;
    }

    @NonNull
    public YoCbtDeviceIdentity getIdentity() { return identity; }

    @NonNull
    public YoCbtDeviceId getDeviceId() { return identity.getDeviceId(); }

    @NonNull
    public YoCbtDeviceAlias getAlias() { return alias; }

    @NonNull
    public YoCbtDeviceClass getDeviceClass() { return deviceClass; }

    @NonNull
    public YoCbtDeviceCapability getCapability() { return capability; }

    @NonNull
    public YoCbtBondState getBondState() { return bondState; }

    @NonNull
    public YoCbtAclLinkState getAclLinkState() { return aclLinkState; }

    @NonNull
    public List<UUID> getResolvedUuids() { return resolvedUuids; }

    @NonNull
    public Map<YoCbtCapabilitySource, Integer> getSourceConfidence() { return sourceConfidence; }

    public long getResolvedAtMillis() { return resolvedAtMillis; }

    public boolean isBonded() { return bondState == YoCbtBondState.BONDED; }

    public boolean isConnected() { return aclLinkState == YoCbtAclLinkState.CONNECTED; }

    /**
     * Converts this resolved device into an immutable snapshot for public API exposure.
     */
    @NonNull
    public YoCbtDeviceSnapshot toSnapshot() {
        return new YoCbtDeviceSnapshot(
                identity,
                alias,
                deviceClass,
                capability,
                bondState,
                aclLinkState,
                resolvedUuids,
                Collections.emptyMap(),
                null,
                null,
                Collections.emptyList(),
                Integer.MIN_VALUE,
                0L,
                resolvedAtMillis,
                resolvedAtMillis,
                YoCbtCapabilitySource.SDP
        );
    }
}
