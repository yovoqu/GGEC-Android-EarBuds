package com.yovo.lib_bluetooth.model.classic;

import androidx.annotation.NonNull;

import java.util.Locale;
import java.util.Objects;

/**
 * 经典蓝牙设备唯一标识。
 */
public final class CbtDeviceId {

    @NonNull
    private final String value;

    public CbtDeviceId(@NonNull String value) {
        this.value = value == null ? "" : value.trim().toUpperCase(Locale.US);
    }

    @NonNull
    public static CbtDeviceId fromAddress(@NonNull String address) {
        return new CbtDeviceId(address);
    }

    @NonNull
    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CbtDeviceId)) return false;
        CbtDeviceId that = (CbtDeviceId) o;
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @NonNull
    @Override
    public String toString() {
        return value;
    }
}
