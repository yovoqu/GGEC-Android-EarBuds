package com.yovo.biz_ggec_hs.provider.lib_bluetooth;


import com.yovo.biz_ggec_hs.api.HsControl;
import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.model.ble.BleErrorCode;

/**
 * lib 的扫描错误码到协议扫描失败原因的映射。协议层只认 {@link HsControl.Failure}。
 *
 * <p>取值来源：扫描失败回调 {@code BleScanObserver#onScanFailed} 由 lib 的扫描控制器发射，
 * 发射点只有 preflight 检查、限流拒绝、平台启动失败、适配器关闭中断与客户端已释放
 * （{@code YoBleScanController} 的 {@code onScanFailed}/{@code emitFailure} 调用处）；
 * {@code YoBleScanAdvertiseStatus#toScanErrorCode} 把平台原始码收敛为
 * {@code SCAN_*} 一族。连接、GATT、传输、配对、L2CAP、外设广播与 GATT Server 的错误码
 * 不经过这条链路，因此不替它们编造扫描原因，而在映射处按契约不变量拒绝。</p>
 *
 * <p>switch 表达式穷尽 {@link BleErrorCode} 且没有 default：lib 新增常量时这里编译期报错，
 * 迫使本次映射同步给出分类，而不是被兜底吞进某一类。</p>
 */
final class HsScanFailures {

    private HsScanFailures() {
    }

    /**
     * @throws IllegalArgumentException 错误码不属于扫描失败回调的取值域
     */
    @NonNull
    static HsControl.Failure map(@NonNull BleErrorCode errorCode) {
        return switch (errorCode) {
            case BLUETOOTH_DISABLED -> HsControl.Failure.BLUETOOTH_DISABLED;
            case PERMISSION_SCAN_DENIED, PERMISSION_CONNECT_DENIED, PERMISSION_ADVERTISE_DENIED,
                 PERMISSION_LOCATION_DENIED -> HsControl.Failure.PERMISSION_DENIED;
            case LOCATION_SERVICES_DISABLED -> HsControl.Failure.LOCATION_DISABLED;
            case BLE_NOT_SUPPORTED, BLUETOOTH_NOT_SUPPORTED -> HsControl.Failure.BLE_UNSUPPORTED;
            case SCAN_THROTTLED -> HsControl.Failure.SCAN_THROTTLED;
            case SCAN_START_FAILED -> HsControl.Failure.SCAN_START_FAILED;
            case SCAN_FEATURE_UNSUPPORTED -> HsControl.Failure.SCAN_FEATURE_UNSUPPORTED;
            case SCAN_OUT_OF_RESOURCES -> HsControl.Failure.SCAN_OUT_OF_RESOURCES;
            case SCAN_INTERNAL_ERROR -> HsControl.Failure.SCAN_INTERNAL_ERROR;
            case RELEASED -> HsControl.Failure.RELEASED;
            // 以下常量属于连接 / GATT / 传输 / 配对 / L2CAP / 外设 / 参数校验 / 生命周期通道，
            // 扫描失败回调不发射它们（证据见类注释）。为它们编造扫描原因会把契约错误伪装成业务结果。
            case UNSUPPORTED_OPERATION, SCAN_STOP_FAILED, DEVICE_INVALID, ILLEGAL_ARGUMENT,
                 ILLEGAL_STATE, CONNECT_FAILED, CONNECT_TIMEOUT, LINK_LOSS, REMOTE_TERMINATED,
                 LOCAL_TERMINATED, GATT_ERROR_133, RETRIES_EXHAUSTED, CONNECTION_NOT_READY,
                 GATT_OPERATION_TIMEOUT, GATT_BUSY, GATT_QUEUE_FULL, SERVICE_DISCOVERY_FAILED,
                 SERVICE_NOT_FOUND, CHARACTERISTIC_NOT_FOUND, DESCRIPTOR_NOT_FOUND, READ_FAILED,
                 WRITE_FAILED, NOTIFY_FAILED, AUTHENTICATION_FAILED, AUTHORIZATION_FAILED,
                 ENCRYPTION_FAILED, INVALID_OFFSET_OR_LENGTH, REQUEST_NOT_SUPPORTED,
                 RELIABLE_WRITE_MISMATCH, TRANSFER_FAILED, RESPONSE_MISMATCH, PAYLOAD_MERGE_FAILED,
                 BOND_FAILED, BOND_CANCELLED, BOND_TIMEOUT, HIDDEN_API_BLOCKED, HIDDEN_API_FAILED,
                 L2CAP_OPEN_FAILED, L2CAP_IO_FAILED, ADVERTISE_FAILED, ADVERTISE_DATA_TOO_LARGE,
                 GATT_SERVER_START_FAILED, GATT_SERVER_NOTIFY_FAILED, OPERATION_CANCELLED,
                 INTERNAL_ERROR ->
                    throw new IllegalArgumentException("扫描失败回调不发射该错误码: " + errorCode);
        };
    }
}
