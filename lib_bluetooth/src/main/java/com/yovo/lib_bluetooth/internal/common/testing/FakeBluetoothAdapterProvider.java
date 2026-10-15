package com.yovo.lib_bluetooth.internal.common.testing;

import android.bluetooth.BluetoothAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 测试用 Fake {@link BluetoothAdapter} 提供者，可独立设置适配器与启用状态。
 */
public final class FakeBluetoothAdapterProvider {
    private BluetoothAdapter adapter;
    private boolean enabled;

    public FakeBluetoothAdapterProvider() {
        this.adapter = null;
        this.enabled = false;
    }

    /**
     * 设置（假）蓝牙适配器是否启用。
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 返回（假）蓝牙适配器当前是否启用。
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置 {@link #adapter()} 返回的假 {@link BluetoothAdapter} 实例。
     */
    public void setAdapter(@Nullable BluetoothAdapter adapter) {
        this.adapter = adapter;
    }

    /**
     * 返回假 {@link BluetoothAdapter} 实例；未设置时返回 null。
     */
    @Nullable
    public BluetoothAdapter adapter() {
        return adapter;
    }

    @NonNull
    @Override
    public String toString() {
        return "FakeBluetoothAdapterProvider{"
                + "adapter=" + (adapter != null ? adapter.getClass().getSimpleName() : "null")
                + ", enabled=" + enabled + "}";
    }
}
