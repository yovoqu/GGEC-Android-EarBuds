package com.yovo.lib_bluetooth.internal.ble.testing;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.ble.peripheral.YoBlePeripheralPlatform;
import com.yovo.lib_bluetooth.model.ble.BleAdvertiseConfig;
import com.yovo.lib_bluetooth.model.ble.BleAdvertiseData;
import com.yovo.lib_bluetooth.model.ble.BleDeviceId;
import com.yovo.lib_bluetooth.model.ble.BleGattServerConfig;
import com.yovo.lib_bluetooth.model.ble.BleGattServerService;
import com.yovo.lib_bluetooth.model.ble.BleGattTarget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * BLE 广播与本地 GATT Server 可控 Fake 平台，记录全部 Response 与 Notify 下行包。
 */
public final class FakeYoBlePeripheralPlatform implements YoBlePeripheralPlatform {

    public static final class SentResponse {
        public final BleDeviceId clientDeviceId;
        public final int requestId;
        public final int status;
        public final int offset;
        @Nullable
        public final byte[] value;

        SentResponse(BleDeviceId clientDeviceId, int requestId, int status, int offset, @Nullable byte[] value) {
            this.clientDeviceId = clientDeviceId;
            this.requestId = requestId;
            this.status = status;
            this.offset = offset;
            this.value = value == null ? null : value.clone();
        }
    }

    public static final class SentNotification {
        public final BleDeviceId clientDeviceId;
        public final BleGattTarget target;
        public final boolean confirm;
        public final byte[] value;

        SentNotification(BleDeviceId clientDeviceId, BleGattTarget target, boolean confirm, byte[] value) {
            this.clientDeviceId = clientDeviceId;
            this.target = target;
            this.confirm = confirm;
            this.value = value.clone();
        }
    }

    @Nullable
    private String nextAdvertiseImmediateError;
    private int nextAdvertiseAsyncErrorCode = 0;
    @Nullable
    private String nextServerError;
    private boolean notifyReturnsTrue = true;
    private boolean autoStartAdvertise = true;
    private boolean autoAddServices = true;

    @Nullable
    private AdvertisePlatformCallback advCallback;
    @Nullable
    private GattServerPlatformCallback serverCallback;
    private boolean advertisingActive = false;
    private boolean serverActive = false;
    private int stopAdvCount = 0;
    private int stopServerCount = 0;

    private final List<SentResponse> sentResponses = new ArrayList<>();
    private final List<SentNotification> sentNotifications = new ArrayList<>();

    public FakeYoBlePeripheralPlatform setNextAdvertiseImmediateError(@Nullable String err) {
        this.nextAdvertiseImmediateError = err;
        return this;
    }

    public FakeYoBlePeripheralPlatform setNextAdvertiseAsyncError(int errorCode) {
        this.nextAdvertiseAsyncErrorCode = errorCode;
        return this;
    }

    public FakeYoBlePeripheralPlatform setNextServerError(@Nullable String err) {
        this.nextServerError = err;
        return this;
    }

    public FakeYoBlePeripheralPlatform setNotifyReturnsTrue(boolean notifyReturnsTrue) {
        this.notifyReturnsTrue = notifyReturnsTrue;
        return this;
    }

    public boolean isAdvertisingActive() {
        return advertisingActive;
    }

    public boolean isServerActive() {
        return serverActive;
    }

    public int stopAdvCount() {
        return stopAdvCount;
    }

    public int stopServerCount() {
        return stopServerCount;
    }

    @Nullable
    public GattServerPlatformCallback serverCallback() {
        return serverCallback;
    }

    @NonNull
    public List<SentResponse> sentResponses() {
        return Collections.unmodifiableList(new ArrayList<>(sentResponses));
    }

    @NonNull
    public List<SentNotification> sentNotifications() {
        return Collections.unmodifiableList(new ArrayList<>(sentNotifications));
    }

    @Nullable
    @Override
    public String startAdvertising(@NonNull BleAdvertiseConfig config,
                                   @NonNull BleAdvertiseData advertiseData,
                                   @Nullable BleAdvertiseData scanResponse,
                                   @NonNull AdvertisePlatformCallback callback) {
        if (nextAdvertiseImmediateError != null) {
            String err = nextAdvertiseImmediateError;
            nextAdvertiseImmediateError = null;
            return err;
        }
        this.advCallback = callback;
        if (nextAdvertiseAsyncErrorCode != 0) {
            int code = nextAdvertiseAsyncErrorCode;
            nextAdvertiseAsyncErrorCode = 0;
            callback.onAdvertiseFailed(code, "Fake async advertise error " + code);
            return null;
        }
        advertisingActive = true;
        if (autoStartAdvertise) {
            callback.onAdvertiseStarted();
        }
        return null;
    }

    @Override
    public void stopAdvertising() {
        stopAdvCount++;
        advertisingActive = false;
        advCallback = null;
    }

    @Nullable
    @Override
    public String startGattServer(@NonNull BleGattServerConfig config,
                                  @NonNull GattServerPlatformCallback callback) {
        if (nextServerError != null) {
            String err = nextServerError;
            nextServerError = null;
            return err;
        }
        this.serverCallback = callback;
        this.serverActive = true;
        if (autoAddServices) {
            for (BleGattServerService ignored : config.services()) {
                callback.onServiceAdded(0);
            }
        }
        return null;
    }

    @Override
    public boolean sendResponse(@NonNull BleDeviceId clientDeviceId,
                                int requestId,
                                int status,
                                int offset,
                                @Nullable byte[] value) {
        sentResponses.add(new SentResponse(clientDeviceId, requestId, status, offset, value));
        return true;
    }

    @Override
    public boolean notifyCharacteristic(@NonNull BleDeviceId clientDeviceId,
                                      @NonNull BleGattTarget target,
                                      boolean confirm,
                                      @NonNull byte[] value) {
        sentNotifications.add(new SentNotification(clientDeviceId, target, confirm, value));
        if (notifyReturnsTrue && serverCallback != null) {
            serverCallback.onNotificationSent(clientDeviceId, 0);
        }
        return notifyReturnsTrue;
    }

    @Override
    public void stopGattServer() {
        stopServerCount++;
        serverActive = false;
        serverCallback = null;
    }
}
