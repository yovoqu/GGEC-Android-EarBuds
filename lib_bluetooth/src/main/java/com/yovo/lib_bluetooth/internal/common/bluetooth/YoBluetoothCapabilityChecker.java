package com.yovo.lib_bluetooth.internal.common.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 检查设备蓝牙硬件是否可用及适配器是否就绪。
 */

public final class YoBluetoothCapabilityChecker {

    private final Context context;

    public YoBluetoothCapabilityChecker(@Nullable Context context) {
        this.context = context != null ? context.getApplicationContext() : null;
    }

    // ── 能力查询 ──────────────────────────────────────────────────

    public boolean supportsClassic() {
        BluetoothAdapter adapter = adapter();
        if (adapter == null) return false;
        // 适配器存在即视为支持经典蓝牙；部分双模适配器也会报告 classic，此处仅检查适配器是否存在。
        return true;
    }

    public boolean supportsBle() {
        BluetoothAdapter adapter = adapter();
        if (adapter == null) return false;
        return adapter.isMultipleAdvertisementSupported()
                || adapter.getBluetoothLeScanner() != null;
    }

    @NonNull
    public Capability capability() {
        boolean classic = supportsClassic();
        boolean ble = supportsBle();
        if (classic && ble) return Capability.DUAL_MODE;
        if (classic) return Capability.CLASSIC_ONLY;
        if (ble) return Capability.BLE_ONLY;
        return Capability.NONE;
    }

    // ── 辅助方法 ──────────────────────────────────────────────────

    @Nullable
    private BluetoothAdapter adapter() {
        if (context == null) return null;
        BluetoothManager manager =
                (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        return manager != null ? manager.getAdapter() : null;
    }

    // ── 能力枚举 ──────────────────────────────────────────────────

    public enum Capability {
        NONE,
        CLASSIC_ONLY,
        BLE_ONLY,
        DUAL_MODE
    }
}
