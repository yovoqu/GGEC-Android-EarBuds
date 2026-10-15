package com.yovo.lib_bluetooth.internal.classic.internal.android.profiles.headset;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.content.Intent;
import android.os.Bundle;
import com.yovo.lib_bluetooth.internal.common.diagnostics.YoBluetoothLogger;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;
import com.yovo.lib_bluetooth.internal.classic.profiles.headset.YoCbtHeadsetAudioState;
import com.yovo.lib_bluetooth.internal.classic.profiles.headset.YoCbtHeadsetDeviceState;
import com.yovo.lib_bluetooth.internal.classic.profiles.state.YoCbtProfileConnectionState;
import com.yovo.lib_bluetooth.internal.classic.profiles.state.YoCbtProfileStateSource;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 解析 Android 厂商定制耳机广播 Intent，提取 AT 命令与设备状态。
 */
public final class YoCbtAndroidVendorHeadsetEventParser {

    private static final String TAG = "YoCbtVendorEventParser";

    /**
     * 厂商定制耳机事件 Intent 中 AT 命令字符串的 Extra 键。
     */
    private static final String EXTRA_COMMAND = "command";

    /**
     * 厂商定制耳机事件 Intent 中命令类型字符串的 Extra 键。
     */
    private static final String EXTRA_COMMAND_TYPE = "commandType";

    /**
     * 附加厂商数据 Bundle 的 Extra 键。
     */
    private static final String EXTRA_VENDOR_DATA = "vendorData";

    /**
     * 解析厂商定制耳机事件 Intent，生成
     * {@link YoCbtHeadsetDeviceState}。
     *
     * <p>从 Intent 中提取设备、命令类型及厂商专用 extras，
     * 构建设备状态以承载解析结果。
     * Profile 连接状态设为 {@link YoCbtProfileConnectionState#CONNECTED}，
     * 因厂商事件发生在 HFP 连接活跃期间。
     *
     * <p>若 Intent action 不匹配
     * {@link BluetoothHeadset#ACTION_VENDOR_SPECIFIC_HEADSET_EVENT}，
     * 或缺少设备 extra，则返回 {@code null}。
     *
     * @param intent 收到的广播 Intent
     * @return 含厂商数据标志的耳机设备状态；若非厂商耳机事件则返回 {@code null}
     */
    @Nullable
    public YoCbtHeadsetDeviceState parse(@NonNull Intent intent) {
        String action = intent.getAction();
        if (!BluetoothHeadset.ACTION_VENDOR_SPECIFIC_HEADSET_EVENT.equals(action)) {
            return null;
        }

        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        if (device == null) {
            YoBluetoothLogger.w(TAG, "厂商专属耳机事件缺少 EXTRA_DEVICE");
            return null;
        }

        YoCbtDeviceId deviceId = YoCbtDeviceId.fromAddress(device.getAddress());
        String commandType = parseCommandType(intent);
        String command = intent.getStringExtra(EXTRA_COMMAND);

        Bundle vendorData = intent.getBundleExtra(EXTRA_VENDOR_DATA);
        String vendorDataSummary = vendorData != null ? vendorData.toString() : "";

        YoBluetoothLogger.d(TAG, "厂商事件：设备=" + device.getAddress()
                + "，命令类型=" + commandType
                + "，命令=" + command
                + "，厂商数据=" + vendorDataSummary);

        long now = System.currentTimeMillis();

        // 厂商事件表示 HFP 连接处于活跃状态
        return new YoCbtHeadsetDeviceState(
                deviceId,
                YoCbtProfileConnectionState.CONNECTED,
                YoCbtHeadsetAudioState.UNKNOWN,
                false,
                now,
                YoCbtProfileStateSource.PROFILE_BROADCAST
        );
    }

    /**
     * 从厂商定制耳机事件 Intent 中提取命令类型。
     *
     * <p>命令类型标识事件关联的 AT 命令
     * （如 "+XEVENT"、"+BRSF"、"+VGM"）。若 Intent 不含
     * 命令类型 extra，则返回空字符串。
     *
     * @param intent 收到的广播 Intent
     * @return 命令类型字符串；不存在时返回空字符串
     */
    @NonNull
    public String parseCommandType(@NonNull Intent intent) {
        String commandType = intent.getStringExtra(EXTRA_COMMAND_TYPE);
        if (commandType == null) {
            commandType = intent.getStringExtra(EXTRA_COMMAND);
        }
        return commandType != null ? commandType : "";
    }

    /**
     * 检查给定 Intent 是否为厂商定制耳机事件。
     *
     * @param intent 待检查的 Intent
     * @return 若 action 匹配
     *         {@link BluetoothHeadset#ACTION_VENDOR_SPECIFIC_HEADSET_EVENT} 则返回 {@code true}
     */
    public static boolean isVendorEvent(@NonNull Intent intent) {
        return BluetoothHeadset.ACTION_VENDOR_SPECIFIC_HEADSET_EVENT.equals(intent.getAction());
    }
}
