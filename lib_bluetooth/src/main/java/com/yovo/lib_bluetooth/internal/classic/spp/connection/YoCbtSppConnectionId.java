package com.yovo.lib_bluetooth.internal.classic.spp.connection;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description SPP 连接唯一标识符值对象。
 */

public final class YoCbtSppConnectionId {
    private final String value;

    public YoCbtSppConnectionId(@NonNull String value) {
        this.value = value;
    }

    @NonNull
    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof YoCbtSppConnectionId)) return false;
        YoCbtSppConnectionId that = (YoCbtSppConnectionId) o;
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
