package com.yovo.lib_bluetooth.internal.classic.platform.profiles.extensions;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothProfile;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;
import com.yovo.lib_bluetooth.internal.classic.error.YoCbtErrorCode;
import com.yovo.lib_bluetooth.internal.classic.error.YoCbtFailure;
import com.yovo.lib_bluetooth.internal.classic.profiles.extensions.YoCbtProfileExtension;
import com.yovo.lib_bluetooth.internal.common.result.YoResult;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 需临时获取 Profile 代理的扩展基类。
 */

@SuppressLint("MissingPermission")
public abstract class YoCbtAndroidTransientProfileProxyExtension implements YoCbtProfileExtension {

    private static final String MODULE = "cbt-profile-extension";

    @NonNull
    private final Context appContext;
    @Nullable
    private final BluetoothAdapter adapter;

    public YoCbtAndroidTransientProfileProxyExtension(@NonNull Context context,
                                               @NonNull BluetoothAdapter adapter) {
        this.appContext = context.getApplicationContext();
        this.adapter = adapter;
    }

    protected abstract int androidProfileId();

    @Override
    public boolean isSupported() {
        return adapter != null;
    }

    @NonNull
    @Override
    public YoResult<Boolean> isConnected(@NonNull YoCbtDeviceId deviceId) {
        BluetoothAdapter activeAdapter = adapter;
        if (activeAdapter == null) {
            return YoResult.failure(YoCbtFailure.of(
                    YoCbtErrorCode.YO_CBT_PROFILE_UNSUPPORTED,
                    "BluetoothAdapter unavailable for extension profile",
                    MODULE));
        }
        try {
            int state = activeAdapter.getProfileConnectionState(androidProfileId());
            return YoResult.success(state == BluetoothProfile.STATE_CONNECTED);
        } catch (SecurityException denied) {
            // 状态查询需要 BLUETOOTH_CONNECT：被拒绝就是一次查询失败，按该链路已有失败类型上报。
            return YoResult.failure(new YoCbtFailure(
                    YoCbtErrorCode.YO_CBT_PERMISSION_CONNECT_DENIED,
                    "BluetoothAdapter.getProfileConnectionState() denied: " + denied.getMessage(),
                    denied,
                    false,
                    false,
                    MODULE,
                    deviceId,
                    null,
                    null,
                    null,
                    "grant BLUETOOTH_CONNECT"));
        }
    }

    @Override
    public void release() {
    }
}
