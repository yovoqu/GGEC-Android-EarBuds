package com.yovo.lib_bluetooth.internal.classic.device.capability;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceIdentity;
import com.yovo.lib_bluetooth.internal.classic.profiles.core.YoCbtCapabilitySource;
import com.yovo.lib_bluetooth.internal.classic.profiles.core.YoCbtProfileType;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 具有已知 Profile 能力的设备，能力可来自 SDP、CoD、配对缓存或厂商配置；与 Profile 连接状态无关。
 */
public final class YoCbtProfileCapableDevice {
    private final YoCbtDeviceIdentity identity;
    private final Set<YoCbtProfileType> supportedProfiles;
    private final Map<YoCbtProfileType, YoCbtCapabilitySource> capabilitySources;
    private final long resolvedAtMillis;

    public YoCbtProfileCapableDevice(@NonNull YoCbtDeviceIdentity identity,
                                     @NonNull Set<YoCbtProfileType> supportedProfiles,
                                     @NonNull Map<YoCbtProfileType, YoCbtCapabilitySource> capabilitySources,
                                     long resolvedAtMillis) {
        this.identity = identity;
        this.supportedProfiles = Collections.unmodifiableSet(
                java.util.EnumSet.copyOf(supportedProfiles));
        this.capabilitySources = Collections.unmodifiableMap(
                new EnumMap<>(capabilitySources));
        this.resolvedAtMillis = resolvedAtMillis;
    }

    @NonNull
    public YoCbtDeviceIdentity getIdentity() { return identity; }

    @NonNull
    public YoCbtDeviceId getDeviceId() { return identity.getDeviceId(); }

    @NonNull
    public Set<YoCbtProfileType> getSupportedProfiles() { return supportedProfiles; }

    public boolean supportsProfile(@NonNull YoCbtProfileType profileType) {
        return supportedProfiles.contains(profileType);
    }

    @NonNull
    public Map<YoCbtProfileType, YoCbtCapabilitySource> getCapabilitySources() {
        return capabilitySources;
    }

    @NonNull
    public YoCbtCapabilitySource getSource(@NonNull YoCbtProfileType profileType) {
        return capabilitySources.getOrDefault(profileType, YoCbtCapabilitySource.UNKNOWN);
    }

    public long getResolvedAtMillis() { return resolvedAtMillis; }
}
