package com.yovo.lib_bluetooth.internal.ble.error;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.model.ble.BleErrorCode;

import java.util.Locale;

/**
 * 蓝牙核心规范 HCI 断连与连接状态原因码解析（对应 BluetoothGattCallback#onConnectionStateChange status）。
 */
public final class YoBleHciStatus {

    public static final int SUCCESS = 0x00;
    public static final int UNKNOWN_COMMAND = 0x01;
    public static final int UNKNOWN_CONNECTION_IDENTIFIER = 0x02;
    public static final int HARDWARE_FAILURE = 0x03;
    public static final int PAGE_TIMEOUT = 0x04;
    public static final int AUTHENTICATION_FAILURE = 0x05;
    public static final int PIN_OR_KEY_MISSING = 0x06;
    public static final int MEMORY_CAPACITY_EXCEEDED = 0x07;
    public static final int CONNECTION_TIMEOUT = 0x08;
    public static final int CONNECTION_LIMIT_EXCEEDED = 0x09;
    public static final int ACL_CONNECTION_ALREADY_EXISTS = 0x0B;
    public static final int COMMAND_DISALLOWED = 0x0C;
    public static final int CONNECTION_REJECTED_LIMITED_RESOURCES = 0x0D;
    public static final int CONNECTION_REJECTED_SECURITY_REASONS = 0x0E;
    public static final int REMOTE_USER_TERMINATED_CONNECTION = 0x13;
    public static final int REMOTE_DEVICE_TERMINATED_LOW_RESOURCES = 0x14;
    public static final int REMOTE_DEVICE_TERMINATED_POWER_OFF = 0x15;
    public static final int CONNECTION_TERMINATED_BY_LOCAL_HOST = 0x16;
    public static final int UNSUPPORTED_REMOTE_FEATURE = 0x1A;
    public static final int LMP_RESPONSE_TIMEOUT = 0x22;
    public static final int INSTANT_PASSED = 0x28;
    public static final int PAIRING_WITH_UNIT_KEY_NOT_SUPPORTED = 0x29;
    public static final int CONTROLLER_BUSY = 0x3A;
    public static final int UNACCEPTABLE_CONNECTION_PARAMETERS = 0x3B;
    public static final int DIRECTED_ADVERTISING_TIMEOUT = 0x3C;
    public static final int CONNECTION_TERMINATED_MIC_FAILURE = 0x3D;
    public static final int CONNECTION_FAILED_TO_ESTABLISH = 0x3E;

    /** Android stack GATT_CONN_CANCEL = 0x0100 (256) */
    public static final int GATT_CONN_CANCEL = 0x0100;
    /** Android infamous GATT_ERROR = 133 (0x85) */
    public static final int GATT_ERROR_133 = 133;
    /** Android GATT_FAILURE = 257 (0x0101) */
    public static final int GATT_FAILURE_257 = 257;
    /**
     * Android 14+ {@code BluetoothGatt.GATT_CONNECTION_TIMEOUT} = 147 (0x93)：直连 30s 内未建链
     * （对端不在范围内或未发可连接广播）。旧版本同场景上报 133。
     */
    public static final int GATT_CONNECTION_TIMEOUT_147 = 0x93;

    private YoBleHciStatus() {
    }

    /** 建链阶段平台自身超时（非库超时）。 */
    public static boolean isConnectTimeout(int status) {
        return status == GATT_CONNECTION_TIMEOUT_147
                || status == PAGE_TIMEOUT
                || status == DIRECTED_ADVERTISING_TIMEOUT;
    }

    public static boolean isLinkLoss(int status) {
        return status == CONNECTION_TIMEOUT
                || status == LMP_RESPONSE_TIMEOUT
                || status == CONNECTION_TERMINATED_MIC_FAILURE;
    }

    public static boolean isRemoteTerminated(int status) {
        return status == REMOTE_USER_TERMINATED_CONNECTION
                || status == REMOTE_DEVICE_TERMINATED_LOW_RESOURCES
                || status == REMOTE_DEVICE_TERMINATED_POWER_OFF;
    }

    public static boolean isLocalTerminated(int status) {
        return status == CONNECTION_TERMINATED_BY_LOCAL_HOST
                || status == GATT_CONN_CANCEL;
    }

    public static boolean isRetryableConnectFailure(int status) {
        return status == GATT_ERROR_133
                || status == CONNECTION_FAILED_TO_ESTABLISH
                || status == CONNECTION_TIMEOUT
                || status == PAGE_TIMEOUT
                || status == GATT_CONNECTION_TIMEOUT_147
                || status == GATT_FAILURE_257
                || status == UNKNOWN_CONNECTION_IDENTIFIER
                || status == INSTANT_PASSED
                || status == CONTROLLER_BUSY;
    }

    @NonNull
    public static BleErrorCode toPublicErrorCode(int status) {
        if (status == GATT_ERROR_133) {
            return BleErrorCode.GATT_ERROR_133;
        }
        if (isConnectTimeout(status)) {
            return BleErrorCode.CONNECT_TIMEOUT;
        }
        if (isLinkLoss(status)) {
            return BleErrorCode.LINK_LOSS;
        }
        if (isRemoteTerminated(status)) {
            return BleErrorCode.REMOTE_TERMINATED;
        }
        if (isLocalTerminated(status)) {
            return BleErrorCode.LOCAL_TERMINATED;
        }
        if (status == AUTHENTICATION_FAILURE
                || status == PIN_OR_KEY_MISSING
                || status == CONNECTION_REJECTED_SECURITY_REASONS) {
            return BleErrorCode.AUTHENTICATION_FAILED;
        }
        return BleErrorCode.CONNECT_FAILED;
    }

    @NonNull
    public static String describe(int status) {
        switch (status) {
            case SUCCESS: return "SUCCESS (0x00)";
            case UNKNOWN_COMMAND: return "HCI_UNKNOWN_COMMAND (0x01)";
            case UNKNOWN_CONNECTION_IDENTIFIER: return "HCI_UNKNOWN_CONNECTION_IDENTIFIER (0x02)";
            case HARDWARE_FAILURE: return "HCI_HARDWARE_FAILURE (0x03)";
            case PAGE_TIMEOUT: return "HCI_PAGE_TIMEOUT (0x04)";
            case AUTHENTICATION_FAILURE: return "HCI_AUTHENTICATION_FAILURE (0x05)";
            case PIN_OR_KEY_MISSING: return "HCI_PIN_OR_KEY_MISSING (0x06)";
            case MEMORY_CAPACITY_EXCEEDED: return "HCI_MEMORY_CAPACITY_EXCEEDED (0x07)";
            case CONNECTION_TIMEOUT: return "HCI_CONNECTION_TIMEOUT / LINK_LOSS (0x08)";
            case CONNECTION_LIMIT_EXCEEDED: return "HCI_CONNECTION_LIMIT_EXCEEDED (0x09)";
            case ACL_CONNECTION_ALREADY_EXISTS: return "HCI_ACL_CONNECTION_ALREADY_EXISTS (0x0B)";
            case COMMAND_DISALLOWED: return "HCI_COMMAND_DISALLOWED (0x0C)";
            case CONNECTION_REJECTED_LIMITED_RESOURCES: return "HCI_REJECTED_LIMITED_RESOURCES (0x0D)";
            case CONNECTION_REJECTED_SECURITY_REASONS: return "HCI_REJECTED_SECURITY (0x0E)";
            case REMOTE_USER_TERMINATED_CONNECTION: return "HCI_REMOTE_USER_TERMINATED (0x13)";
            case REMOTE_DEVICE_TERMINATED_LOW_RESOURCES: return "HCI_REMOTE_LOW_RESOURCES (0x14)";
            case REMOTE_DEVICE_TERMINATED_POWER_OFF: return "HCI_REMOTE_POWER_OFF (0x15)";
            case CONNECTION_TERMINATED_BY_LOCAL_HOST: return "HCI_LOCAL_HOST_TERMINATED (0x16)";
            case UNSUPPORTED_REMOTE_FEATURE: return "HCI_UNSUPPORTED_REMOTE_FEATURE (0x1A)";
            case LMP_RESPONSE_TIMEOUT: return "HCI_LMP_RESPONSE_TIMEOUT (0x22)";
            case INSTANT_PASSED: return "HCI_INSTANT_PASSED (0x28)";
            case CONTROLLER_BUSY: return "HCI_CONTROLLER_BUSY (0x3A)";
            case UNACCEPTABLE_CONNECTION_PARAMETERS: return "HCI_UNACCEPTABLE_CONN_PARAMS (0x3B)";
            case DIRECTED_ADVERTISING_TIMEOUT: return "HCI_DIRECTED_ADV_TIMEOUT (0x3C)";
            case CONNECTION_TERMINATED_MIC_FAILURE: return "HCI_MIC_FAILURE (0x3D)";
            case CONNECTION_FAILED_TO_ESTABLISH: return "HCI_CONN_FAILED_TO_ESTABLISH (0x3E)";
            case GATT_ERROR_133: return "GATT_ERROR (133 / 0x85)";
            case GATT_CONN_CANCEL: return "GATT_CONN_CANCEL (256 / 0x100)";
            case GATT_FAILURE_257: return "GATT_FAILURE (257 / 0x101)";
            case GATT_CONNECTION_TIMEOUT_147: return "GATT_CONNECTION_TIMEOUT (147 / 0x93)";
            default: return "HCI_STATUS_0x" + Integer.toHexString(status).toUpperCase(Locale.ROOT);
        }
    }
}
