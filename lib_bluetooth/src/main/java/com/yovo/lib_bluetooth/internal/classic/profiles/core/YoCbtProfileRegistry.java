package com.yovo.lib_bluetooth.internal.classic.profiles.core;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description SDK 支持的 Profile 类型描述符注册表。
 */
public final class YoCbtProfileRegistry {

    private final Map<YoCbtProfileType, YoCbtProfileDescriptor> descriptors;

    public YoCbtProfileRegistry() {
        EnumMap<YoCbtProfileType, YoCbtProfileDescriptor> map =
                new EnumMap<>(YoCbtProfileType.class);
        for (YoCbtProfileType type : YoCbtProfileType.values()) {
            if (type == YoCbtProfileType.UNKNOWN) continue;
            map.put(type, YoCbtProfileDescriptor.forProfile(type));
        }
        this.descriptors = Collections.unmodifiableMap(map);
    }

    @NonNull
    public Set<YoCbtProfileType> supportedTypes() {
        return descriptors.keySet();
    }

    @Nullable
    public YoCbtProfileDescriptor get(@NonNull YoCbtProfileType type) {
        return descriptors.get(type);
    }

    public boolean isSupported(@NonNull YoCbtProfileType type) {
        return descriptors.containsKey(type);
    }

    public boolean requiresProxy(@NonNull YoCbtProfileType type) {
        YoCbtProfileDescriptor descriptor = descriptors.get(type);
        return descriptor != null && descriptor.isRequiresProxy();
    }

    public boolean isExtensionOnly(@NonNull YoCbtProfileType type) {
        YoCbtProfileDescriptor descriptor = descriptors.get(type);
        return descriptor != null && descriptor.isExtensionOnly();
    }
}
