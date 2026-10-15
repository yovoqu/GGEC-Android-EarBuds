package com.yovo.lib_bluetooth.internal.classic.service.discovery;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 通过 {@link com.yovo.lib_bluetooth.internal.classic.runtime.YoCbtEventHub} 发布的运行时服务发现事件。
 */
public final class YoCbtServiceDiscoveryEvent {

    public enum Kind {
        STARTED,
        RESOLVED,
        EMPTY,
        FAILED,
        TIMEOUT,
        CACHE_FALLBACK
    }

    @NonNull
    private final Kind kind;
    @NonNull
    private final YoCbtDeviceId deviceId;

    public YoCbtServiceDiscoveryEvent(@NonNull Kind kind, @NonNull YoCbtDeviceId deviceId) {
        this.kind = kind;
        this.deviceId = deviceId;
    }

    @NonNull
    public Kind getKind() {
        return kind;
    }

    @NonNull
    public YoCbtDeviceId getDeviceId() {
        return deviceId;
    }

    @Override
    public String toString() {
        return "YoCbtServiceDiscoveryEvent{kind=" + kind + ", deviceId=" + deviceId + '}';
    }
}
