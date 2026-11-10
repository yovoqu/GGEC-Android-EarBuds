package com.yovo.lib_bluetooth.internal.ble.error;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.model.ble.BleErrorCode;

import java.util.Locale;

/**
 * ATT / GATT 操作与扫描系统状态码解析（参考 Nordic GattError & Polidea GattStatusParser）。
 */
public final class YoBleGattStatus {

    public static final int GATT_SUCCESS = 0x00;
    public static final int GATT_INVALID_HANDLE = 0x01;
    public static final int GATT_READ_NOT_PERMITTED = 0x02;
    public static final int GATT_WRITE_NOT_PERMITTED = 0x03;
    public static final int GATT_INVALID_PDU = 0x04;
    public static final int GATT_INSUFFICIENT_AUTHENTICATION = 0x05;
    public static final int GATT_REQUEST_NOT_SUPPORTED = 0x06;
    public static final int GATT_INVALID_OFFSET = 0x07;
    public static final int GATT_INSUFFICIENT_AUTHORIZATION = 0x08;
    public static final int GATT_PREPARE_Q_FULL = 0x09;
    public static final int GATT_ATTRIBUTE_NOT_FOUND = 0x0A;
    public static final int GATT_ATTRIBUTE_NOT_LONG = 0x0B;
    public static final int GATT_INSUFFICIENT_KEY_SIZE = 0x0C;
    public static final int GATT_INVALID_ATTRIBUTE_LENGTH = 0x0D;
    public static final int GATT_UNLIKELY_ERROR = 0x0E;
    public static final int GATT_INSUFFICIENT_ENCRYPTION = 0x0F;
    public static final int GATT_UNSUPPORTED_GROUP_TYPE = 0x10;
    public static final int GATT_INSUFFICIENT_RESOURCES = 0x11;
    public static final int GATT_CONNECTION_CONGESTED = 0x8F;
    public static final int GATT_NO_RESOURCES = 0x80;
    public static final int GATT_INTERNAL_ERROR = 0x81;
    public static final int GATT_WRONG_STATE = 0x82;
    public static final int GATT_DB_FULL = 0x83;
    public static final int GATT_BUSY = 0x84;
    public static final int GATT_ERROR = 0x85; // 133
    public static final int GATT_CMD_STARTED = 0x86;
    public static final int GATT_ILLEGAL_PARAMETER = 0x87;
    public static final int GATT_PENDING = 0x88;
    public static final int GATT_AUTH_FAIL = 0x89;
    public static final int GATT_MORE = 0x8A;
    public static final int GATT_INVALID_CFG = 0x8B;
    public static final int GATT_SERVICE_STARTED = 0x8C;
    public static final int GATT_ENCRYPTED_NO_MITM = 0x8D;
    public static final int GATT_NOT_ENCRYPTED = 0x8E;
    public static final int GATT_WRITE_REQUEST_REJECTED = 0xFC;
    public static final int GATT_CCCD_CFG_ERROR = 0xFD;
    public static final int GATT_PROCEDURE_IN_PROGRESS = 0xFE;
    public static final int GATT_VALUE_OUT_OF_RANGE = 0xFF;
    public static final int GATT_FAILURE = 0x101;

    private YoBleGattStatus() {
    }

    public static boolean isAuthOrEncryptionError(int status) {
        return status == GATT_INSUFFICIENT_AUTHENTICATION
                || status == GATT_INSUFFICIENT_AUTHORIZATION
                || status == GATT_INSUFFICIENT_ENCRYPTION
                || status == GATT_AUTH_FAIL
                || status == GATT_ENCRYPTED_NO_MITM
                || status == GATT_NOT_ENCRYPTED;
    }

    @NonNull
    public static BleErrorCode toGattErrorCode(int status, @NonNull BleErrorCode fallback) {
        switch (status) {
            case GATT_INSUFFICIENT_AUTHENTICATION:
            case GATT_AUTH_FAIL:
                return BleErrorCode.AUTHENTICATION_FAILED;
            case GATT_INSUFFICIENT_AUTHORIZATION:
                return BleErrorCode.AUTHORIZATION_FAILED;
            case GATT_INSUFFICIENT_ENCRYPTION:
            case GATT_INSUFFICIENT_KEY_SIZE:
            case GATT_ENCRYPTED_NO_MITM:
            case GATT_NOT_ENCRYPTED:
                return BleErrorCode.ENCRYPTION_FAILED;
            case GATT_REQUEST_NOT_SUPPORTED:
            case GATT_READ_NOT_PERMITTED:
            case GATT_WRITE_NOT_PERMITTED:
                return BleErrorCode.REQUEST_NOT_SUPPORTED;
            case GATT_INVALID_OFFSET:
            case GATT_INVALID_ATTRIBUTE_LENGTH:
            case GATT_ATTRIBUTE_NOT_LONG:
                return BleErrorCode.INVALID_OFFSET_OR_LENGTH;
            case GATT_BUSY:
            case GATT_PROCEDURE_IN_PROGRESS:
            case GATT_CONNECTION_CONGESTED:
            case GATT_PREPARE_Q_FULL:
                return BleErrorCode.GATT_BUSY;
            case GATT_ERROR:
                return BleErrorCode.GATT_ERROR_133;
            default:
                return fallback;
        }
    }

    @NonNull
    public static String describe(int status) {
        switch (status) {
            case GATT_SUCCESS: return "GATT_SUCCESS (0x00)";
            case GATT_INVALID_HANDLE: return "GATT_INVALID_HANDLE (0x01)";
            case GATT_READ_NOT_PERMITTED: return "GATT_READ_NOT_PERMITTED (0x02)";
            case GATT_WRITE_NOT_PERMITTED: return "GATT_WRITE_NOT_PERMITTED (0x03)";
            case GATT_INVALID_PDU: return "GATT_INVALID_PDU (0x04)";
            case GATT_INSUFFICIENT_AUTHENTICATION: return "GATT_INSUFFICIENT_AUTHENTICATION (0x05)";
            case GATT_REQUEST_NOT_SUPPORTED: return "GATT_REQUEST_NOT_SUPPORTED (0x06)";
            case GATT_INVALID_OFFSET: return "GATT_INVALID_OFFSET (0x07)";
            case GATT_INSUFFICIENT_AUTHORIZATION: return "GATT_INSUFFICIENT_AUTHORIZATION (0x08)";
            case GATT_PREPARE_Q_FULL: return "GATT_PREPARE_Q_FULL (0x09)";
            case GATT_ATTRIBUTE_NOT_FOUND: return "GATT_ATTRIBUTE_NOT_FOUND (0x0A)";
            case GATT_ATTRIBUTE_NOT_LONG: return "GATT_ATTRIBUTE_NOT_LONG (0x0B)";
            case GATT_INSUFFICIENT_KEY_SIZE: return "GATT_INSUFFICIENT_KEY_SIZE (0x0C)";
            case GATT_INVALID_ATTRIBUTE_LENGTH: return "GATT_INVALID_ATTRIBUTE_LENGTH (0x0D)";
            case GATT_UNLIKELY_ERROR: return "GATT_UNLIKELY_ERROR (0x0E)";
            case GATT_INSUFFICIENT_ENCRYPTION: return "GATT_INSUFFICIENT_ENCRYPTION (0x0F)";
            case GATT_UNSUPPORTED_GROUP_TYPE: return "GATT_UNSUPPORTED_GROUP_TYPE (0x10)";
            case GATT_INSUFFICIENT_RESOURCES: return "GATT_INSUFFICIENT_RESOURCES (0x11)";
            case GATT_NO_RESOURCES: return "GATT_NO_RESOURCES (0x80)";
            case GATT_INTERNAL_ERROR: return "GATT_INTERNAL_ERROR (0x81)";
            case GATT_WRONG_STATE: return "GATT_WRONG_STATE (0x82)";
            case GATT_DB_FULL: return "GATT_DB_FULL (0x83)";
            case GATT_BUSY: return "GATT_BUSY (0x84)";
            case GATT_ERROR: return "GATT_ERROR (0x85 / 133)";
            case GATT_CMD_STARTED: return "GATT_CMD_STARTED (0x86)";
            case GATT_ILLEGAL_PARAMETER: return "GATT_ILLEGAL_PARAMETER (0x87)";
            case GATT_PENDING: return "GATT_PENDING (0x88)";
            case GATT_AUTH_FAIL: return "GATT_AUTH_FAIL (0x89)";
            case GATT_MORE: return "GATT_MORE (0x8A)";
            case GATT_INVALID_CFG: return "GATT_INVALID_CFG (0x8B)";
            case GATT_SERVICE_STARTED: return "GATT_SERVICE_STARTED (0x8C)";
            case GATT_ENCRYPTED_NO_MITM: return "GATT_ENCRYPTED_NO_MITM (0x8D)";
            case GATT_NOT_ENCRYPTED: return "GATT_NOT_ENCRYPTED (0x8E)";
            case GATT_CONNECTION_CONGESTED: return "GATT_CONNECTION_CONGESTED (0x8F)";
            case GATT_WRITE_REQUEST_REJECTED: return "GATT_WRITE_REQUEST_REJECTED (0xFC)";
            case GATT_CCCD_CFG_ERROR: return "GATT_CCCD_CFG_ERROR (0xFD)";
            case GATT_PROCEDURE_IN_PROGRESS: return "GATT_PROCEDURE_IN_PROGRESS (0xFE)";
            case GATT_VALUE_OUT_OF_RANGE: return "GATT_VALUE_OUT_OF_RANGE (0xFF)";
            case GATT_FAILURE: return "GATT_FAILURE (0x101)";
            default:
                return status >= 0x80 && status <= 0x9F
                        ? "GATT_APP_OR_STACK_0x" + Integer.toHexString(status).toUpperCase(Locale.ROOT)
                        : "GATT_STATUS_0x" + Integer.toHexString(status).toUpperCase(Locale.ROOT);
        }
    }
}
