package com.yovo.lib_bluetooth.internal.classic.device.snapshot;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceClass;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceIdentity;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceType;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 经典蓝牙扫描过程中观测到的设备，携带身份、RSSI、时间戳及可选 CoD 等原始发现数据。
 */
public final class YoCbtObservedDevice {
    private final YoCbtDeviceIdentity identity;
    private final int rssi;
    private final YoCbtDeviceClass deviceClass;
    private final long observedAtMillis;

    public YoCbtObservedDevice(@NonNull YoCbtDeviceIdentity identity,
                               int rssi,
                               @NonNull YoCbtDeviceClass deviceClass,
                               long observedAtMillis) {
        this.identity = identity;
        this.rssi = rssi;
        this.deviceClass = deviceClass;
        this.observedAtMillis = observedAtMillis;
    }

    @NonNull
    public YoCbtDeviceIdentity getIdentity() {
        return identity;
    }

    @NonNull
    public YoCbtDeviceId getDeviceId() {
        return identity.getDeviceId();
    }

    public int getRssi() {
        return rssi;
    }

    @NonNull
    public YoCbtDeviceClass getDeviceClass() {
        return deviceClass;
    }

    public long getObservedAtMillis() {
        return observedAtMillis;
    }

    /**
     * Derives a provisional device type from the Class of Device and address type.
     */
    @NonNull
    public YoCbtDeviceType inferDeviceType() {
        if (deviceClass.isKnown()) {
            int major = deviceClass.getMajorDeviceClass();
            // 主类 AUDIO_VIDEO、COMPUTER、PERIPHERAL 等表示支持经典蓝牙
            if (major >= 0x100 && major <= 0x700) {
                return YoCbtDeviceType.CLASSIC;
            }
        }
        return identity.getDeviceType();
    }
}
