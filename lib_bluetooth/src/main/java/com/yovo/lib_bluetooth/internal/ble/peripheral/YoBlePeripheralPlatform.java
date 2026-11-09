package com.yovo.lib_bluetooth.internal.ble.peripheral;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.model.ble.BleAdvertiseConfig;
import com.yovo.lib_bluetooth.model.ble.BleAdvertiseData;
import com.yovo.lib_bluetooth.model.ble.BleDeviceId;
import com.yovo.lib_bluetooth.model.ble.BleGattServerConfig;
import com.yovo.lib_bluetooth.model.ble.BleGattTarget;

/**
 * BLE 外设广播（Advertising）与本地 GATT Server 底层平台适配端口（Android 适配器或 JVM 假实现）。
 */
public interface YoBlePeripheralPlatform {

    interface AdvertisePlatformCallback {
        void onAdvertiseStarted();

        void onAdvertiseFailed(int errorCode, @Nullable String detail);

        void onAdvertiseStopped();
    }

    interface GattServerPlatformCallback {
        void onServiceAdded(int status);

        void onConnectionStateChange(@NonNull BleDeviceId clientDeviceId, boolean connected);

        void onCharacteristicReadRequest(@NonNull BleDeviceId clientDeviceId,
                                         int requestId,
                                         int offset,
                                         @NonNull BleGattTarget target);

        void onCharacteristicWriteRequest(@NonNull BleDeviceId clientDeviceId,
                                          int requestId,
                                          @NonNull BleGattTarget target,
                                          boolean preparedWrite,
                                          boolean responseNeeded,
                                          int offset,
                                          @NonNull byte[] value);

        void onDescriptorReadRequest(@NonNull BleDeviceId clientDeviceId,
                                     int requestId,
                                     int offset,
                                     @NonNull BleGattTarget target);

        void onDescriptorWriteRequest(@NonNull BleDeviceId clientDeviceId,
                                      int requestId,
                                      @NonNull BleGattTarget target,
                                      boolean preparedWrite,
                                      boolean responseNeeded,
                                      int offset,
                                      @NonNull byte[] value);

        void onExecuteWrite(@NonNull BleDeviceId clientDeviceId, int requestId, boolean execute);

        void onNotificationSent(@NonNull BleDeviceId clientDeviceId, int status);

        void onMtuChanged(@NonNull BleDeviceId clientDeviceId, int mtu);
    }

    /** 启动广播；返回 null 表示异步请求已发出，非 null 表示前置构建失败描述。 */
    @Nullable
    String startAdvertising(@NonNull BleAdvertiseConfig config,
                            @NonNull BleAdvertiseData advertiseData,
                            @Nullable BleAdvertiseData scanResponse,
                            @NonNull AdvertisePlatformCallback callback);

    void stopAdvertising();

    /** 启动 GATT Server 并依次添加服务；返回 null 表示初始化请求成功发出。 */
    @Nullable
    String startGattServer(@NonNull BleGattServerConfig config,
                           @NonNull GattServerPlatformCallback callback);

    boolean sendResponse(@NonNull BleDeviceId clientDeviceId,
                         int requestId,
                         int status,
                         int offset,
                         @Nullable byte[] value);

    boolean notifyCharacteristic(@NonNull BleDeviceId clientDeviceId,
                               @NonNull BleGattTarget target,
                               boolean confirm,
                               @NonNull byte[] value);

    void stopGattServer();
}
