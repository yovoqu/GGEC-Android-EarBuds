package com.yovo.lib_bluetooth.internal.classic.platform;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothClass;
import android.content.Intent;
import android.os.Parcelable;
import android.os.ParcelUuid;

import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.platform.AndroidPlatform;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 经典蓝牙系统广播 Intent 的 Parcelable Extra 读取（唯一读取点）。
 *
 * <p>类型化读取 {@code getParcelableExtra(key, Class)} 自 API 33 起可用，能力判定统一走
 * {@link AndroidPlatform#usesTypedParcelableExtras()}；各 Receiver 只调用本类，不再自己比较版本。</p>
 */

public final class YoCbtIntentExtras {
    private YoCbtIntentExtras() {
    }

    public static BluetoothDevice device(Intent intent, String key) {
        if (intent == null) return null;
        if (AndroidPlatform.usesTypedParcelableExtras()) {
            return intent.getParcelableExtra(key, BluetoothDevice.class);
        }
        return intent.getParcelableExtra(key);
    }

    @Nullable
    public static BluetoothClass bluetoothClass(Intent intent, String key) {
        if (intent == null) return null;
        if (AndroidPlatform.usesTypedParcelableExtras()) {
            return intent.getParcelableExtra(key, BluetoothClass.class);
        }
        return intent.getParcelableExtra(key);
    }

    public static List<UUID> uuids(Intent intent, String key) {
        List<UUID> result = new ArrayList<>();
        if (intent == null) return result;
        ParcelUuid[] values;
        if (AndroidPlatform.usesTypedParcelableExtras()) {
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
