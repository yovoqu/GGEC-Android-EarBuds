package com.yovo.lib_bluetooth.internal.classic.device.identity;

import androidx.annotation.NonNull;

import java.util.Locale;
import java.util.Objects;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 设备唯一标识符值对象，用于在模块内稳定引用设备。
 */

public final class YoCbtDeviceId {
    private final String value;

    public YoCbtDeviceId(@NonNull String value) {
        this.value = canonical(value);
    }

    @NonNull
    public static YoCbtDeviceId fromAddress(@NonNull String address) {
        return new YoCbtDeviceId(address);
    }

    @NonNull
    public String value() {
        return value;
    }

    private static String canonical(String raw) {
        return raw == null ? "" : raw.trim().toUpperCase(Locale.US);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof YoCbtDeviceId)) return false;
        YoCbtDeviceId that = (YoCbtDeviceId) o;
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
