package com.yovo.lib_bluetooth.model.ble;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * BLE 设备唯一标识（规范化大写 MAC 地址，如 {@code 00:11:22:33:44:55}）。
 */
public final class BleDeviceId {

    private static final Pattern MAC_PATTERN =
            Pattern.compile("^[0-9A-F]{2}(:[0-9A-F]{2}){5}$");

    @NonNull
    private final String value;

    public BleDeviceId(@NonNull String value) {
        Objects.requireNonNull(value, "deviceId value must not be null");
        this.value = value.trim().toUpperCase(Locale.US);
    }

    public static boolean isValid(@Nullable String address) {
        if (address == null) {
            return false;
        }
        return MAC_PATTERN.matcher(address.trim().toUpperCase(Locale.US)).matches();
    }

    public boolean isValidMac() {
        return MAC_PATTERN.matcher(value).matches();
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
