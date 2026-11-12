package com.yovo.lib_bluetooth.internal.ble.internal.android;

import com.yovo.lib_bluetooth.internal.platform.AndroidPlatform;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothStatusCodes;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.AdvertisingSet;
import android.bluetooth.le.AdvertisingSetCallback;
import android.bluetooth.le.AdvertisingSetParameters;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.yovo.lib_bluetooth.internal.ble.internal.android.compat.YoBleAdvertiseCompat;
import com.yovo.lib_bluetooth.internal.ble.internal.android.compat.YoBleGattCompat;
import com.yovo.lib_bluetooth.internal.ble.peripheral.YoBlePeripheralPlatform;
import com.yovo.lib_bluetooth.internal.common.bluetooth.YoBluetoothAdapterProvider;
import com.yovo.lib_bluetooth.model.ble.BleAdvertiseConfig;
import com.yovo.lib_bluetooth.model.ble.BleAdvertiseData;
import com.yovo.lib_bluetooth.model.ble.BleDeviceId;
import com.yovo.lib_bluetooth.model.ble.BleGattServerCharacteristic;
import com.yovo.lib_bluetooth.model.ble.BleGattServerConfig;
import com.yovo.lib_bluetooth.model.ble.BleGattServerDescriptor;
import com.yovo.lib_bluetooth.model.ble.BleGattServerService;
import com.yovo.lib_bluetooth.model.ble.BleGattTarget;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Android {@link BluetoothLeAdvertiser} 与 {@link BluetoothGattServer} 平台适配实现。
 *
 * <p>严格遵循 Android 栈规范：通过内部队列串行执行 {@code addService()}，等待系统
 * {@code onServiceAdded} 回调后再添加下一服务并建立精确 {@code instanceId} + 规范特征双索引。</p>
 */
@SuppressLint("MissingPermission")
public final class YoBleAndroidPeripheralPlatform implements YoBlePeripheralPlatform {

    private final Context appContext;
    private final YoBluetoothAdapterProvider adapterProvider;

    @Nullable
    private volatile BluetoothLeAdvertiser activeAdvertiser;
    @Nullable
    private volatile AdvertiseCallback legacyAdvCallback;
    @Nullable
    private volatile Object extendedAdvCallback; // AdvertisingSetCallback (API 26+)

    @Nullable
    private BluetoothGattServer gattServer;
    private final Deque<BluetoothGattService> pendingServicesToAdd = new ArrayDeque<>();
    private final Map<BleDeviceId, BluetoothDevice> connectedClients = new LinkedHashMap<>();
    private final Map<BleGattTarget, BluetoothGattCharacteristic> characteristicMap = new LinkedHashMap<>();

    public YoBleAndroidPeripheralPlatform(@NonNull Context appContext,
                                          @NonNull YoBluetoothAdapterProvider adapterProvider) {
        this.appContext = appContext.getApplicationContext();
        this.adapterProvider = adapterProvider;
    }

    @Nullable
    @Override
    public String startAdvertising(@NonNull BleAdvertiseConfig config,
                                   @NonNull BleAdvertiseData advertiseData,
                                   @Nullable BleAdvertiseData scanResponse,
                                   @NonNull AdvertisePlatformCallback callback) {
        BluetoothAdapter adapter = adapterProvider.adapter();
        if (adapter == null || !adapter.isEnabled()) {
            return "Bluetooth adapter unavailable or disabled";
        }
        BluetoothLeAdvertiser advertiser = adapter.getBluetoothLeAdvertiser();
        if (advertiser == null) {
            return "BluetoothLeAdvertiser unavailable on this device";
        }
        stopAdvertising();

        AdvertiseData advPayload = YoBleAdvertiseCompat.toAdvertiseData(advertiseData);
        AdvertiseData scanPayload = scanResponse == null ? null : YoBleAdvertiseCompat.toAdvertiseData(scanResponse);

        try {
            activeAdvertiser = advertiser;
            if (AndroidPlatform.supportsExtendedAdvertising() && !config.legacyMode()) {
                AdvertisingSetParameters params = YoBleAdvertiseCompat.toExtendedParameters(config);
                AdvertisingSetCallback setCb = new AdvertisingSetCallback() {
                    @Override
                    public void onAdvertisingSetStarted(AdvertisingSet advertisingSet,
                                                          int txPower,
                                                          int status) {
                        if (extendedAdvCallback != this) {
                            return;
                        }
                        if (isAdvertisingSetSuccess(status)) {
                            callback.onAdvertiseStarted();
                        } else {
                            extendedAdvCallback = null;
                            callback.onAdvertiseFailed(status,
                                    "onAdvertisingSetStarted failed status=" + status);
                        }
                    }

                    @Override
                    public void onAdvertisingSetStopped(AdvertisingSet advertisingSet) {
                        if (extendedAdvCallback != this) {
                            return;
                        }
                        extendedAdvCallback = null;
                        callback.onAdvertiseStopped();
                    }
                };
                extendedAdvCallback = setCb;
                int durationUnits = config.timeoutMillis() <= 0
                        ? 0
                        : Math.max(1, Math.min(65535, config.timeoutMillis() / 10));
                advertiser.startAdvertisingSet(params, advPayload, scanPayload,
                        null, null, durationUnits, 0, setCb);
            } else {
                AdvertiseSettings settings = YoBleAdvertiseCompat.toLegacySettings(config);
                AdvertiseCallback advCb = new AdvertiseCallback() {
                    @Override
                    public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                        if (legacyAdvCallback != this) {
                            return;
                        }
                        callback.onAdvertiseStarted();
                    }

                    @Override
                    public void onStartFailure(int errorCode) {
                        if (legacyAdvCallback != this) {
                            return;
                        }
                        legacyAdvCallback = null;
                        callback.onAdvertiseFailed(errorCode,
                                "AdvertiseCallback.onStartFailure(" + errorCode + ")");
                    }
                };
                legacyAdvCallback = advCb;
                if (scanPayload != null) {
                    advertiser.startAdvertising(settings, advPayload, scanPayload, advCb);
                } else {
                    advertiser.startAdvertising(settings, advPayload, advCb);
                }
            }
            return null;
        } catch (Throwable t) {
            legacyAdvCallback = null;
            extendedAdvCallback = null;
            activeAdvertiser = null;
            return "Failed to start BLE advertising: " + t.getMessage();
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private static boolean isAdvertisingSetSuccess(int status) {
        return status == AdvertisingSetCallback.ADVERTISE_SUCCESS;
    }

    @Override
    public void stopAdvertising() {
        BluetoothLeAdvertiser advertiser = activeAdvertiser;
        AdvertiseCallback leg = legacyAdvCallback;
        Object ext = extendedAdvCallback;
        activeAdvertiser = null;
        legacyAdvCallback = null;
        extendedAdvCallback = null;
        if (advertiser == null) {
            return;
        }
        try {
            if (leg != null) {
                advertiser.stopAdvertising(leg);
            }
        } catch (Throwable ignored) {
        }
        try {
            if (ext != null && AndroidPlatform.supportsExtendedAdvertising()) {
                advertiser.stopAdvertisingSet((AdvertisingSetCallback) ext);
            }
        } catch (Throwable ignored) {
        }
    }

    @Nullable
    @Override
    public synchronized String startGattServer(@NonNull BleGattServerConfig config,
                                               @NonNull GattServerPlatformCallback callback) {
        BluetoothManager manager =
                (BluetoothManager) appContext.getSystemService(Context.BLUETOOTH_SERVICE);
        if (manager == null) {
            return "BluetoothManager unavailable";
        }
        stopGattServer();

        AtomicReference<BluetoothGattServer> expectedServer = new AtomicReference<>();
        BluetoothGattServerCallback androidCb = new BluetoothGattServerCallback() {
            @Override
            public void onServiceAdded(int status, BluetoothGattService service) {
                synchronized (YoBleAndroidPeripheralPlatform.this) {
                    if (gattServer == null || gattServer != expectedServer.get()) {
                        return;
                    }
                    if (status == 0 && service != null) {
                        for (BluetoothGattCharacteristic ch : service.getCharacteristics()) {
                            characteristicMap.put(
                                    BleGattTarget.characteristic(
                                            service.getUuid(),
                                            service.getInstanceId(),
                                            ch.getUuid(),
                                            ch.getInstanceId()),
                                    ch);
                            characteristicMap.put(
                                    BleGattTarget.characteristic(service.getUuid(), ch.getUuid()),
                                    ch);
                        }
                    }
                }
                callback.onServiceAdded(status);
                if (status == 0) {
                    enqueueNextService();
                }
            }

            @Override
            public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
                if (device == null || !BleDeviceId.isValid(device.getAddress())) {
                    return;
                }
                BleDeviceId id = new BleDeviceId(device.getAddress());
                boolean connected = newState == BluetoothProfile.STATE_CONNECTED;
                synchronized (YoBleAndroidPeripheralPlatform.this) {
                    if (gattServer == null || gattServer != expectedServer.get()) {
                        return;
                    }
                    if (connected) {
                        connectedClients.put(id, device);
                    } else {
                        connectedClients.remove(id);
                    }
                }
                callback.onConnectionStateChange(id, connected);
            }

            @Override
            public void onCharacteristicReadRequest(BluetoothDevice device,
                                                    int requestId,
                                                    int offset,
                                                    BluetoothGattCharacteristic characteristic) {
                if (!isServerActive(expectedServer) || device == null
                        || characteristic == null || !BleDeviceId.isValid(device.getAddress())) {
                    return;
                }
                callback.onCharacteristicReadRequest(
                        new BleDeviceId(device.getAddress()),
                        requestId,
                        offset,
                        YoBleGattCompat.targetForCharacteristic(characteristic));
            }

            @Override
            public void onCharacteristicWriteRequest(BluetoothDevice device,
                                                     int requestId,
                                                     BluetoothGattCharacteristic characteristic,
                                                     boolean preparedWrite,
                                                     boolean responseNeeded,
                                                     int offset,
                                                     byte[] value) {
                if (!isServerActive(expectedServer) || device == null
                        || characteristic == null || !BleDeviceId.isValid(device.getAddress())) {
                    return;
                }
                callback.onCharacteristicWriteRequest(
                        new BleDeviceId(device.getAddress()),
                        requestId,
                        YoBleGattCompat.targetForCharacteristic(characteristic),
                        preparedWrite,
                        responseNeeded,
                        offset,
                        value == null ? new byte[0] : value.clone());
            }

            @Override
            public void onDescriptorReadRequest(BluetoothDevice device,
                                                int requestId,
                                                int offset,
                                                BluetoothGattDescriptor descriptor) {
                if (!isServerActive(expectedServer) || device == null
                        || descriptor == null || !BleDeviceId.isValid(device.getAddress())) {
                    return;
                }
                callback.onDescriptorReadRequest(
                        new BleDeviceId(device.getAddress()),
                        requestId,
                        offset,
                        YoBleGattCompat.targetForDescriptor(descriptor));
            }

            @Override
            public void onDescriptorWriteRequest(BluetoothDevice device,
                                                 int requestId,
                                                 BluetoothGattDescriptor descriptor,
                                                 boolean preparedWrite,
                                                 boolean responseNeeded,
                                                 int offset,
                                                 byte[] value) {
                if (!isServerActive(expectedServer) || device == null
                        || descriptor == null || !BleDeviceId.isValid(device.getAddress())) {
                    return;
                }
                callback.onDescriptorWriteRequest(
                        new BleDeviceId(device.getAddress()),
                        requestId,
                        YoBleGattCompat.targetForDescriptor(descriptor),
                        preparedWrite,
                        responseNeeded,
                        offset,
                        value == null ? new byte[0] : value.clone());
            }

            @Override
            public void onExecuteWrite(BluetoothDevice device, int requestId, boolean execute) {
                if (!isServerActive(expectedServer) || device == null
                        || !BleDeviceId.isValid(device.getAddress())) {
                    return;
                }
                callback.onExecuteWrite(new BleDeviceId(device.getAddress()), requestId, execute);
            }

            @Override
            public void onNotificationSent(BluetoothDevice device, int status) {
                if (!isServerActive(expectedServer) || device == null
                        || !BleDeviceId.isValid(device.getAddress())) {
                    return;
                }
                callback.onNotificationSent(new BleDeviceId(device.getAddress()), status);
            }

            @Override
            public void onMtuChanged(BluetoothDevice device, int mtu) {
                if (!isServerActive(expectedServer) || device == null
                        || !BleDeviceId.isValid(device.getAddress())) {
                    return;
                }
                callback.onMtuChanged(new BleDeviceId(device.getAddress()), mtu);
            }
        };

        try {
            BluetoothGattServer server = manager.openGattServer(appContext, androidCb);
            if (server == null) {
                return "BluetoothManager.openGattServer returned null";
            }
            expectedServer.set(server);
            this.gattServer = server;
            this.pendingServicesToAdd.clear();
            this.characteristicMap.clear();
            this.connectedClients.clear();

            for (BleGattServerService svcDef : config.services()) {
                BluetoothGattService svc = new BluetoothGattService(
                        svcDef.uuid(),
                        svcDef.isPrimary()
                                ? BluetoothGattService.SERVICE_TYPE_PRIMARY
                                : BluetoothGattService.SERVICE_TYPE_SECONDARY);
                for (BleGattServerCharacteristic chDef : svcDef.characteristics()) {
                    BluetoothGattCharacteristic ch = new BluetoothGattCharacteristic(
                            chDef.uuid(), chDef.properties(), chDef.permissions());
                    for (BleGattServerDescriptor dDef : chDef.descriptors()) {
                        BluetoothGattDescriptor desc = new BluetoothGattDescriptor(
                                dDef.uuid(), dDef.permissions());
                        ch.addDescriptor(desc);
                    }
                    svc.addCharacteristic(ch);
                    characteristicMap.put(
                            BleGattTarget.characteristic(svcDef.uuid(), chDef.uuid()), ch);
                }
                pendingServicesToAdd.addLast(svc);
            }
            enqueueNextService();
            return null;
        } catch (Throwable t) {
            stopGattServer();
            return "Failed to start GATT Server: " + t.getMessage();
        }
    }

    private synchronized boolean isServerActive(@NonNull AtomicReference<BluetoothGattServer> expectedServer) {
        return gattServer != null && gattServer == expectedServer.get();
    }

    private synchronized void enqueueNextService() {
        BluetoothGattServer server = gattServer;
        if (server == null || pendingServicesToAdd.isEmpty()) {
            return;
        }
        BluetoothGattService next = pendingServicesToAdd.pollFirst();
        if (next != null) {
            server.addService(next);
        }
    }

    @Override
    public synchronized boolean sendResponse(@NonNull BleDeviceId clientDeviceId,
                                             int requestId,
                                             int status,
                                             int offset,
                                             @Nullable byte[] value) {
        BluetoothGattServer server = gattServer;
        BluetoothDevice dev = resolveClientDevice(clientDeviceId);
        if (server == null || dev == null) {
            return false;
        }
        return server.sendResponse(dev, requestId, status, offset, value);
    }

    @SuppressWarnings("deprecation")
    @Override
    public synchronized boolean notifyCharacteristic(@NonNull BleDeviceId clientDeviceId,
                                                   @NonNull BleGattTarget target,
                                                   boolean confirm,
                                                   @NonNull byte[] value) {
        BluetoothGattServer server = gattServer;
        BluetoothDevice dev = resolveClientDevice(clientDeviceId);
        BluetoothGattCharacteristic ch = characteristicMap.get(target.withoutDescriptor());
        if (ch == null) {
            ch = characteristicMap.get(
                    BleGattTarget.characteristic(target.serviceUuid(), target.characteristicUuid()));
        }
        if (server == null || dev == null || ch == null) {
            return false;
        }
        if (AndroidPlatform.usesTypedPeripheralNotify()) {
            return server.notifyCharacteristicChanged(dev, ch, confirm, value)
                    == BluetoothStatusCodes.SUCCESS;
        }
        ch.setValue(value);
        return server.notifyCharacteristicChanged(dev, ch, confirm);
    }

    @Override
    public synchronized void stopGattServer() {
        pendingServicesToAdd.clear();
        characteristicMap.clear();
        connectedClients.clear();
        BluetoothGattServer server = gattServer;
        gattServer = null;
        if (server != null) {
            try {
                server.clearServices();
            } catch (Throwable ignored) {
            }
            try {
                server.close();
            } catch (Throwable ignored) {
            }
        }
    }

    @Nullable
    private BluetoothDevice resolveClientDevice(@NonNull BleDeviceId clientDeviceId) {
        BluetoothDevice cached = connectedClients.get(clientDeviceId);
        if (cached != null) {
            return cached;
        }
        BluetoothAdapter adapter = adapterProvider.adapter();
        if (adapter == null) {
            return null;
        }
        try {
            return adapter.getRemoteDevice(clientDeviceId.value());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
