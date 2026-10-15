package com.yovo.lib_bluetooth.internal.classic.device.capability;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.classic.profiles.core.YoCbtCapabilitySource;
import com.yovo.lib_bluetooth.internal.classic.profiles.core.YoCbtProfileType;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 设备支持的 Profile 能力集合及每项能力的来源信息。
 */

public final class YoCbtDeviceCapability {
    private final EnumMap<YoCbtProfileType, YoCbtCapabilitySource> supportedProfiles;

    public YoCbtDeviceCapability(@NonNull Map<YoCbtProfileType, YoCbtCapabilitySource> supportedProfiles) {
        this.supportedProfiles = new EnumMap<>(YoCbtProfileType.class);
        this.supportedProfiles.putAll(supportedProfiles);
    }

    @NonNull
    public Set<YoCbtProfileType> getSupportedProfiles() {
        if (supportedProfiles.isEmpty()) return Collections.emptySet();
        return Collections.unmodifiableSet(EnumSet.copyOf(supportedProfiles.keySet()));
    }

    @NonNull
    public Map<YoCbtProfileType, YoCbtCapabilitySource> getSources() {
        return Collections.unmodifiableMap(supportedProfiles);
    }

    @NonNull
    public YoCbtDeviceCapability withProfile(@NonNull YoCbtProfileType profileType,
                                             @NonNull YoCbtCapabilitySource source) {
        EnumMap<YoCbtProfileType, YoCbtCapabilitySource> copy =
                new EnumMap<>(supportedProfiles);
        copy.put(profileType, source);
        return new YoCbtDeviceCapability(copy);
    }

    @NonNull
    public static YoCbtDeviceCapability empty() {
        return new YoCbtDeviceCapability(new EnumMap<>(YoCbtProfileType.class));
    }
}
