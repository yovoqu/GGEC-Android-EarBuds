package com.ggec.earbuds.ui.device;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.device.model.DeviceFailure;

/** 协议里的失败分类到用户文案。界面不读取任何项目 SDK 的失败枚举。 */
public final class DeviceFailureText {

    private DeviceFailureText() {
    }

    @StringRes
    public static int message(@NonNull DeviceFailure failure) {
        return switch (failure) {
            case BLUETOOTH_DISABLED -> R.string.device_scan_error_bluetooth_disabled;
            case PERMISSION_DENIED -> R.string.device_scan_error_permission;
            case LOCATION_DISABLED -> R.string.device_scan_error_location_disabled;
            case BLE_UNSUPPORTED -> R.string.device_scan_error_ble_unsupported;
            case SCAN_THROTTLED -> R.string.device_scan_error_throttled;
            case SCAN_FAILED -> R.string.device_scan_error_failed;
            case NOT_READY -> R.string.device_error_not_ready;
            case TIMEOUT -> R.string.device_error_timeout;
            case REJECTED -> R.string.device_error_rejected;
            case UNSUPPORTED_CAPABILITY -> R.string.device_error_unsupported;
            case DEVICE_ERROR -> R.string.device_error_device;
            case INTERNAL_ERROR -> R.string.device_error_internal;
        };
    }
}
