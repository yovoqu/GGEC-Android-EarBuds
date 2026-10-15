package com.yovo.lib_bluetooth.model.classic;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * SPP 连接唯一标识。
 */
public final class CbtSppConnectionId {

    @NonNull
    private final String value;

    public CbtSppConnectionId(@NonNull String value) {
        this.value = value;
    }

    @NonNull
    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CbtSppConnectionId)) return false;
        CbtSppConnectionId that = (CbtSppConnectionId) o;
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
