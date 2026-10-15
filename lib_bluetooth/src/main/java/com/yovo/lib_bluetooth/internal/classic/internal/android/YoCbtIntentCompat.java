package com.yovo.lib_bluetooth.internal.classic.internal.android;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothClass;
import android.content.Intent;
import android.os.Build;
import android.os.Parcelable;
import android.os.ParcelUuid;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description Android 蓝牙 Intent 动作与 Extra 常量兼容层。
 */

public final class YoCbtIntentCompat {
    private YoCbtIntentCompat() {
    }

    public static BluetoothDevice device(Intent intent, String key) {
        if (intent == null) return null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return intent.getParcelableExtra(key, BluetoothDevice.class);
        }
        return intent.getParcelableExtra(key);
    }

    @Nullable
    public static BluetoothClass bluetoothClass(Intent intent, String key) {
        if (intent == null) return null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return intent.getParcelableExtra(key, BluetoothClass.class);
        }
        return intent.getParcelableExtra(key);
    }

    public static List<UUID> uuids(Intent intent, String key) {
        List<UUID> result = new ArrayList<>();
        if (intent == null) return result;
        ParcelUuid[] values;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            values = intent.getParcelableArrayExtra(key, ParcelUuid.class);
        } else {
            // Android 8.0+ (API 26) 起 getParcelableArrayExtra 实际返回 Parcelable[]，
            // 直接强转 ParcelUuid[] 会抛 ClassCastException，需逐元素转换。
            Parcelable[] raw = intent.getParcelableArrayExtra(key);
            if (raw == null) {
                values = null;
            } else {
                values = new ParcelUuid[raw.length];
                for (int i = 0; i < raw.length; i++) {
                    values[i] = raw[i] instanceof ParcelUuid ? (ParcelUuid) raw[i] : null;
                }
            }
        }
        return parcelUuids(values);
    }

    public static List<UUID> parcelUuids(ParcelUuid[] values) {
        List<UUID> result = new ArrayList<>();
        if (values == null) return result;
        for (ParcelUuid value : values) {
            if (value != null) result.add(value.getUuid());
        }
        return result;
    }
}
