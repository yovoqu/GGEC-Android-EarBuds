package com.yovo.lib_bluetooth.internal.ble.error;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.common.diagnostics.YoTraceId;
import com.yovo.lib_bluetooth.model.ble.BleDeviceId;
import com.yovo.lib_bluetooth.model.ble.BleErrorCode;
import com.yovo.lib_bluetooth.model.ble.BleFailure;
import com.yovo.lib_bluetooth.model.ble.BleGattTarget;

/**
 * BLE 内部统一失败构造与映射工具：自动生成 TraceId、描述文本与恢复建议。
 */
public final class YoBleErrorMapper {

    private YoBleErrorMapper() {
    }

    @NonNull
    public static BleFailure failure(@NonNull BleErrorCode code,
                                     @NonNull String sourceModule,
                                     @NonNull String message) {
        return failure(code, sourceModule, message, null,
                BleFailure.NO_RAW_STATUS, null, null, null);
    }

    @NonNull
    public static BleFailure failure(@NonNull BleErrorCode code,
                                     @NonNull String sourceModule,
                                     @NonNull String message,
                                     @Nullable BleDeviceId deviceId) {
        return failure(code, sourceModule, message, null,
                BleFailure.NO_RAW_STATUS, deviceId, null, null);
    }

    @NonNull
    public static BleFailure failure(@NonNull BleErrorCode code,
                                     @NonNull String sourceModule,
                                     @NonNull String message,
                                     @Nullable Throwable cause,
                                     int rawStatusCode,
                                     @Nullable BleDeviceId deviceId,
                                     @Nullable BleGattTarget target,
                                     @Nullable String suggestedAction) {
        boolean recoverable = isRecoverable(code);
        boolean retryable = isRetryable(code);
        return new BleFailure(
                code,
                message,
                cause,
                recoverable,
                retryable,
                sourceModule,
                deviceId,
                target != null ? target.serviceUuid() : null,
                target != null ? target.characteristicUuid() : null,
                target != null ? target.descriptorUuid() : null,
                rawStatusCode,
                YoTraceId.next(),
                suggestedAction != null ? suggestedAction : defaultSuggestion(code));
    }

    @NonNull
    public static BleFailure fromHciStatus(int hciStatus,
                                           @NonNull String sourceModule,
                                           @NonNull BleDeviceId deviceId) {
        BleErrorCode code = YoBleHciStatus.toPublicErrorCode(hciStatus);
        String message = "BLE HCI status: " + YoBleHciStatus.describe(hciStatus);
        boolean retryable = YoBleHciStatus.isRetryableConnectFailure(hciStatus)
                || code == BleErrorCode.LINK_LOSS;
        return new BleFailure(
                code,
                message,
                null,
                true,
                retryable,
                sourceModule,
                deviceId,
                null,
                null,
                null,
                hciStatus,
                YoTraceId.next(),
                defaultSuggestion(code));
    }

    @NonNull
    public static BleFailure fromGattStatus(int gattStatus,
                                            @NonNull BleErrorCode fallback,
                                            @NonNull String sourceModule,
                                            @NonNull BleDeviceId deviceId,
                                            @Nullable BleGattTarget target,
                                            @NonNull String operationLabel) {
        BleErrorCode mapped = YoBleGattStatus.toGattErrorCode(gattStatus, fallback);
        String msg = operationLabel + " failed: " + YoBleGattStatus.describe(gattStatus);
        return new BleFailure(
                mapped,
                msg,
                null,
                isRecoverable(mapped),
                isRetryable(mapped),
                sourceModule,
                deviceId,
                target != null ? target.serviceUuid() : null,
                target != null ? target.characteristicUuid() : null,
                target != null ? target.descriptorUuid() : null,
                gattStatus,
                YoTraceId.next(),
                defaultSuggestion(mapped));
    }

    private static boolean isRecoverable(@NonNull BleErrorCode code) {
        switch (code) {
            case BLUETOOTH_NOT_SUPPORTED:
            case BLE_NOT_SUPPORTED:
            case RELEASED:
                return false;
            default:
                return true;
        }
    }

    private static boolean isRetryable(@NonNull BleErrorCode code) {
        switch (code) {
            case SCAN_THROTTLED:
            case CONNECT_FAILED:
            case CONNECT_TIMEOUT:
            case LINK_LOSS:
            case GATT_ERROR_133:
            case GATT_OPERATION_TIMEOUT:
            case GATT_BUSY:
            case READ_FAILED:
            case WRITE_FAILED:
            case NOTIFY_FAILED:
            case SERVICE_DISCOVERY_FAILED:
            case BOND_TIMEOUT:
                return true;
            default:
                return false;
        }
    }

    @NonNull
    private static String defaultSuggestion(@NonNull BleErrorCode code) {
        switch (code) {
            case BLUETOOTH_DISABLED:
                return "Prompt user to enable Bluetooth adapter";
            case LOCATION_SERVICES_DISABLED:
                return "Prompt user to enable system Location services on Android < 12";
            case PERMISSION_SCAN_DENIED:
            case PERMISSION_CONNECT_DENIED:
            case PERMISSION_ADVERTISE_DENIED:
            case PERMISSION_LOCATION_DENIED:
                return "Request missing runtime Bluetooth/Location permission";
            case SCAN_THROTTLED:
                return "Wait before starting another scan (Android 30s/5-scan window)";
            case GATT_ERROR_133:
                return "Close GATT, wait >= 200ms, and retry connection";
            case AUTHENTICATION_FAILED:
            case ENCRYPTION_FAILED:
                return "Trigger createBond() or clear stale bond if key mismatched";
            case HIDDEN_API_BLOCKED:
                return "Enable allowHiddenApi flag in BleConfig only if explicitly required";
            default:
                return "Inspect diagnostic report and retry operation";
        }
    }
}
