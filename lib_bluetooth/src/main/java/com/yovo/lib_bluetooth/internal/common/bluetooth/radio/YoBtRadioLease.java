package com.yovo.lib_bluetooth.internal.common.bluetooth.radio;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 射频占用令牌（记录租约类型：BLE_SCAN, CLASSIC_DISCOVERY, CONNECTION，以及持有者与释放逻辑）。
 */
public final class YoBtRadioLease implements AutoCloseable {

    /**
     * 租约类型。
     */
    public enum Type {
        /** 低功耗蓝牙扫描。 */
        BLE_SCAN,
        /** 经典蓝牙设备发现（Inquiry）。 */
        CLASSIC_DISCOVERY,
        /** 正在发起连接（GATT 或 SPP 前置抢占）。 */
        CONNECTION
    }

    /**
     * 租约释放回调接口。
     */
    @FunctionalInterface
    public interface ReleaseCallback {
        void onRelease(@NonNull YoBtRadioLease lease);
    }

    private final String leaseId;
    private final Type type;
    private final String holder;
    private final long createdAtMillis;
    private final ReleaseCallback releaseCallback;
    private final AtomicBoolean released = new AtomicBoolean(false);

    public YoBtRadioLease(@NonNull String leaseId,
                          @NonNull Type type,
                          @NonNull String holder,
                          long createdAtMillis,
                          @Nullable ReleaseCallback releaseCallback) {
        this.leaseId = Objects.requireNonNull(leaseId, "leaseId cannot be null");
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.holder = Objects.requireNonNull(holder, "holder cannot be null");
        this.createdAtMillis = createdAtMillis;
        this.releaseCallback = releaseCallback;
    }

    @NonNull
    public String getLeaseId() {
        return leaseId;
    }

    @NonNull
    public Type getType() {
        return type;
    }

    @NonNull
    public String getHolder() {
        return holder;
    }

    public long getCreatedAtMillis() {
        return createdAtMillis;
    }

    public boolean isActive() {
        return !released.get();
    }

    public boolean isReleased() {
        return released.get();
    }

    /**
     * 释放当前射频租约。具备幂等性与线程安全。
     */
    public void release() {
        if (released.compareAndSet(false, true)) {
            if (releaseCallback != null) {
                releaseCallback.onRelease(this);
            }
        }
    }

    @Override
    public void close() {
        release();
    }

    @Override
    public String toString() {
        return "YoBtRadioLease{" +
                "id='" + leaseId + '\'' +
                ", type=" + type +
                ", holder='" + holder + '\'' +
                ", active=" + isActive() +
                '}';
    }
}
