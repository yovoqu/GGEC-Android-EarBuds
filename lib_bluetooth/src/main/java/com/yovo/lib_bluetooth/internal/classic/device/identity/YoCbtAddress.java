package com.yovo.lib_bluetooth.internal.classic.device.identity;

import androidx.annotation.NonNull;

import java.util.Locale;
import java.util.Objects;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 蓝牙 MAC 地址值对象，包含规范化地址字符串与地址类型。
 */

public final class YoCbtAddress {
    private final String value;
    private final YoCbtAddressType type;

    public YoCbtAddress(@NonNull String value, @NonNull YoCbtAddressType type) {
        this.value = value.trim().toUpperCase(Locale.US);
        this.type = type;
    }

    @NonNull
    public String value() {
        return value;
    }

    @NonNull
    public YoCbtAddressType getType() {
        return type;
    }

    public boolean isEmpty() {
        return value.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof YoCbtAddress)) return false;
        YoCbtAddress that = (YoCbtAddress) o;
        return value.equals(that.value) && type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, type);
    }

    @NonNull
    @Override
    public String toString() {
        return value + "(" + type + ")";
    }
}
