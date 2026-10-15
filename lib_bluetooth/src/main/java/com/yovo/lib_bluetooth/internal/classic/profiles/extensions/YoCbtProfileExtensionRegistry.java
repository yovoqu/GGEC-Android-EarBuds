package com.yovo.lib_bluetooth.internal.classic.profiles.extensions;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.common.result.YoResult;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;
import com.yovo.lib_bluetooth.internal.classic.profiles.core.YoCbtProfileType;

import java.util.EnumMap;
import java.util.Map;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description Profile 扩展注册表，管理已注册的 {@link YoCbtProfileExtension} 实例。
 */

public final class YoCbtProfileExtensionRegistry {

    private final Map<YoCbtProfileType, YoCbtProfileExtension> extensions =
            new EnumMap<>(YoCbtProfileType.class);

    public void register(@NonNull YoCbtProfileExtension extension) {
        extensions.put(extension.getProfileType(), extension);
    }

    @Nullable
    public YoCbtProfileExtension get(@NonNull YoCbtProfileType type) {
        return extensions.get(type);
    }

    public boolean hasExtension(@NonNull YoCbtProfileType type) {
        return extensions.containsKey(type);
    }

    public boolean isSupported(@NonNull YoCbtProfileType type) {
        YoCbtProfileExtension extension = extensions.get(type);
        return extension != null && extension.isSupported();
    }

    @NonNull
    public YoResult<Boolean> hasCapability(@NonNull YoCbtDeviceId deviceId,
                                           @NonNull YoCbtProfileType type) {
        YoCbtProfileExtension extension = extensions.get(type);
        if (extension == null) {
            return YoResult.success(false);
        }
        return extension.hasCapability(deviceId);
    }

    @NonNull
    public YoResult<Boolean> isConnected(@NonNull YoCbtDeviceId deviceId,
                                         @NonNull YoCbtProfileType type) {
        YoCbtProfileExtension extension = extensions.get(type);
        if (extension == null) {
            return YoResult.success(false);
        }
        return extension.isConnected(deviceId);
    }

    public void releaseAll() {
        for (YoCbtProfileExtension extension : extensions.values()) {
            extension.release();
        }
    }
}
