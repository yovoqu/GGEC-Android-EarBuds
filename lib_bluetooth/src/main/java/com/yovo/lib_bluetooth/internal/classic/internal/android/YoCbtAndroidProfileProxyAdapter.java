package com.yovo.lib_bluetooth.internal.classic.internal.android;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import com.yovo.lib_bluetooth.internal.common.diagnostics.YoBluetoothLogger;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.common.result.YoResult;
import com.yovo.lib_bluetooth.internal.classic.error.YoCbtErrorCode;
import com.yovo.lib_bluetooth.internal.classic.error.YoCbtFailure;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 将 Android Profile 代理请求/关闭适配为 {@link YoResult} 结构化操作。
 */
public final class YoCbtAndroidProfileProxyAdapter {

    private static final String TAG = "YoCbtAndroidProfileProxyAdapter";

    private YoCbtAndroidProfileProxyAdapter() {
        // 工具类 — 禁止实例化
    }

    /**
     * 请求 Bluetooth Profile 代理。
     *
     * <p>委托 {@link BluetoothAdapter#getProfileProxy(Context, BluetoothProfile.ServiceListener, int)}
     * 并做结构化错误处理。
     *
     * @param adapter      Bluetooth 适配器；若设备不支持 Bluetooth 可为 null
     * @param context      应用上下文
     * @param profileType  Android Profile 常量（如 {@link BluetoothProfile#A2DP}）
     * @param listener     接收服务连接/断开回调的监听器
     * @return 成功时返回底层 API 的 boolean 结果；适配器不可用、
     *         权限被拒或抛出意外异常时返回失败
     */
    @NonNull
    public static YoResult<Boolean> getProfileProxy(@Nullable BluetoothAdapter adapter,
                                                     @NonNull Context context,
                                                     int profileType,
                                                     @NonNull BluetoothProfile.ServiceListener listener) {
        if (adapter == null) {
            return YoResult.failure(YoCbtFailure.of(
                    YoCbtErrorCode.YO_CBT_BLUETOOTH_NOT_SUPPORTED,
                    "Bluetooth adapter is not available on this device", TAG));
        }
        if (listener == null) {
            return YoResult.failure(YoCbtFailure.of(
                    YoCbtErrorCode.YO_CBT_INTERNAL_ERROR,
                    "ServiceListener must not be null", TAG));
        }

        try {
            boolean result = adapter.getProfileProxy(
                    context.getApplicationContext(), listener, profileType);
            return YoResult.success(result);
        } catch (SecurityException e) {
            return YoResult.failure(new YoCbtFailure(
                    YoCbtErrorCode.YO_CBT_PROFILE_PROXY_GET_FAILED,
                    "SecurityException in getProfileProxy: " + e.getMessage(),
                    e, false, false, TAG,
                    null, null, null, null,
                    "Grant BLUETOOTH_CONNECT permission"));
        } catch (Exception e) {
            YoBluetoothLogger.e(TAG, "获取 ProfileProxy 时发生意外错误", e);
            return YoResult.failure(new YoCbtFailure(
                    YoCbtErrorCode.YO_CBT_PROFILE_PROXY_GET_FAILED,
                    "getProfileProxy threw: " + e.getMessage(),
                    e, true, false, TAG,
                    null, null, null, null, ""));
        }
    }

    /**
     * 关闭先前打开的 Bluetooth Profile 代理。
     *
     * <p>若适配器不可用或 proxy 为 null，则安全空操作并返回成功。
     *
     * @param adapter     Bluetooth 适配器；可为 null
     * @param profileType Android Profile 常量
     * @param proxy       要关闭的代理实例；可为 null
     * @return 若已关闭或无需操作则成功；权限被拒则失败
     */
    @NonNull
    public static YoResult<Void> closeProfileProxy(@Nullable BluetoothAdapter adapter,
                                                    int profileType,
                                                    @Nullable BluetoothProfile proxy) {
        if (adapter == null) {
            return YoResult.failure(YoCbtFailure.of(
                    YoCbtErrorCode.YO_CBT_BLUETOOTH_NOT_SUPPORTED,
                    "Bluetooth adapter is not available on this device", TAG));
        }
        if (proxy == null) {
            // 无需关闭 — 视为成功
            return YoResult.success(null);
        }

        try {
            adapter.closeProfileProxy(profileType, proxy);
            return YoResult.success(null);
        } catch (SecurityException e) {
            return YoResult.failure(new YoCbtFailure(
                    YoCbtErrorCode.YO_CBT_PROFILE_PROXY_LIFECYCLE_ERROR,
                    "SecurityException in closeProfileProxy: " + e.getMessage(),
                    e, false, false, TAG,
                    null, null, null, null,
                    "Grant BLUETOOTH_CONNECT permission"));
        } catch (Exception e) {
            YoBluetoothLogger.w(TAG, "关闭 ProfileProxy 出错，类型 " + profileType, e);
            return YoResult.failure(new YoCbtFailure(
                    YoCbtErrorCode.YO_CBT_PROFILE_PROXY_LIFECYCLE_ERROR,
                    "closeProfileProxy threw: " + e.getMessage(),
                    e, true, false, TAG,
                    null, null, null, null, ""));
        }
    }

    /**
     * 将 {@link YoCbtProfileType} 解析为 Android Profile 代理常量。
     *
     * @param profileType 类型名称
     * @return 标准类型对应的 Android {@link BluetoothProfile} 常量，
     *         扩展专用类型返回 -1
     */
    public static int toAndroidProfileConstant(@NonNull String profileType) {
        switch (profileType.toUpperCase(java.util.Locale.US)) {
            case "A2DP":
                return BluetoothProfile.A2DP;
            case "HEADSET":
            case "HFP":
            case "HSP":
                return BluetoothProfile.HEADSET;
            case "HEARING_AID":
                return BluetoothProfile.HEARING_AID;
            case "LE_AUDIO":
                return BluetoothProfile.LE_AUDIO;
            case "HID_HOST":
                return 6; // BluetoothProfile.HID_HOST
            case "PAN":
                return 5; // BluetoothProfile.PAN
            case "PBAP":
                return 17; // BluetoothProfile.PBAP
            default:
                return -1;
        }
    }
}
