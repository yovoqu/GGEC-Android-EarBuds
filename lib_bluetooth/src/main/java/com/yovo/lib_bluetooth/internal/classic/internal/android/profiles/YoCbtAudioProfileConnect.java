package com.yovo.lib_bluetooth.internal.classic.internal.android.profiles;

import android.bluetooth.BluetoothDevice;

import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.common.diagnostics.BluetoothDiagnosticsBus;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * 已配对设备的音频 Profile 发起。
 *
 * <p>第三方不能调用 {@code BluetoothDevice.connect()}：AOSP 这条 Binder 要
 * {@code BLUETOOTH_PRIVILEGED}。A2DP 的 {@code connect(BluetoothDevice)} 只检查
 * {@code BLUETOOTH_CONNECT}。Headset 在新版本还可能要 {@code MODIFY_PHONE_STATE}，
 * 调用被拒绝就当这一侧没接上。任一侧接受即可，连上与否由 Profile 状态确认。</p>
 */
public final class YoCbtAudioProfileConnect {

    private static final String TAG = "YoCbtAudioProfileConnect";

    private YoCbtAudioProfileConnect() {
    }

    public static boolean request(@Nullable Object a2dp, @Nullable Object headset,
                                  @Nullable BluetoothDevice device) {
        if (device == null) {
            return false;
        }
        return invoke(a2dp, device, "A2DP") || invoke(headset, device, "HFP");
    }

    private static boolean invoke(@Nullable Object proxy, BluetoothDevice device, String profile) {
        if (proxy == null) {
            return false;
        }
        try {
            Method connect = proxy.getClass().getMethod("connect", BluetoothDevice.class);
            Object result = connect.invoke(proxy, device);
            return Boolean.TRUE.equals(result);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            BluetoothDiagnosticsBus.get().recordException(TAG, profile + ".connect() rejected", cause);
            return false;
        } catch (ReflectiveOperationException e) {
            BluetoothDiagnosticsBus.get().recordException(TAG, profile + ".connect() unavailable", e);
            return false;
        }
    }
}
