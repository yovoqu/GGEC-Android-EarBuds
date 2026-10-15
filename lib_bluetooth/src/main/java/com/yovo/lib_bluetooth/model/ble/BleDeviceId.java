package com.yovo.lib_bluetooth.model.ble;

import androidx.annotation.NonNull;

import java.util.Locale;
import java.util.Objects;

/**
 * BLE 设备唯一标识。
 */
public final class BleDeviceId {

    @NonNull
    private final String value;

    public BleDeviceId(@NonNull String value) {
        this.value = value.trim().toUpperCase(Locale.US);
    }

    @NonNull
    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BleDeviceId)) return false;
        BleDeviceId that = (BleDeviceId) o;
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
