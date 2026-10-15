package com.yovo.lib_bluetooth.api;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.model.ble.BleConfig;
import com.yovo.lib_bluetooth.model.ble.BleConnectionConfig;
import com.yovo.lib_bluetooth.model.ble.BleConnectionObserver;
import com.yovo.lib_bluetooth.model.ble.BleConnectionSnapshot;
import com.yovo.lib_bluetooth.model.ble.BleDeviceId;
import com.yovo.lib_bluetooth.model.ble.BleGattObserver;
import com.yovo.lib_bluetooth.model.ble.BleGattTarget;
import com.yovo.lib_bluetooth.model.ble.BleOperationHandle;
import com.yovo.lib_bluetooth.model.ble.BleScanConfig;
import com.yovo.lib_bluetooth.model.ble.BleScanObserver;
import com.yovo.lib_bluetooth.model.ble.BleWriteType;
import com.yovo.lib_bluetooth.model.common.Result;

/**
 * BLE 对外 API。客户端由 {@link com.yovo.lib_bluetooth.YoBluetoothSDK#ble} 创建。
 */
public interface YoBleClient {

    @NonNull
    BleOperationHandle scan(@NonNull BleScanConfig config, @NonNull BleScanObserver observer);

    @NonNull
    Result<Void> stopScan();

    @NonNull
    BleOperationHandle connect(@NonNull BleDeviceId deviceId,
                               @NonNull BleConnectionConfig config,
                               @NonNull BleConnectionObserver observer);

    @NonNull
    Result<Void> disconnect(@NonNull BleDeviceId deviceId);

    @NonNull
    Result<BleConnectionSnapshot> getConnectionSnapshot(@NonNull BleDeviceId deviceId);

    @NonNull
    BleOperationHandle read(@NonNull BleDeviceId deviceId,
                            @NonNull BleGattTarget target,
                            @NonNull BleGattObserver observer);

    @NonNull
    BleOperationHandle write(@NonNull BleDeviceId deviceId,
                             @NonNull BleGattTarget target,
                             @NonNull byte[] value,
                             @NonNull BleWriteType writeType,
                             @NonNull BleGattObserver observer);

    @NonNull
    BleOperationHandle setNotify(@NonNull BleDeviceId deviceId,
                                 @NonNull BleGattTarget target,
                                 boolean enabled,
                                 @NonNull BleGattObserver observer);

    void release();
}
