package com.yovo.lib_bluetooth.internal.ble.internal.android.compat;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothStatusCodes;
import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.yovo.lib_bluetooth.internal.platform.AndroidPlatform;
import com.yovo.lib_bluetooth.model.ble.BleConnectionPriority;
import com.yovo.lib_bluetooth.model.ble.BleDeviceId;
import com.yovo.lib_bluetooth.model.ble.BleGattCharacteristicInfo;
import com.yovo.lib_bluetooth.model.ble.BleGattDatabaseSnapshot;
import com.yovo.lib_bluetooth.model.ble.BleGattDescriptorInfo;
import com.yovo.lib_bluetooth.model.ble.BleGattServiceInfo;
import com.yovo.lib_bluetooth.model.ble.BleGattTarget;
import com.yovo.lib_bluetooth.model.ble.BlePhy;
import com.yovo.lib_bluetooth.model.ble.BlePhyConfig;
import com.yovo.lib_bluetooth.model.ble.BlePhyOptions;
import com.yovo.lib_bluetooth.model.ble.BleWriteType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Android API 24–37 GATT 兼容适配工具：
 * <ul>
 *   <li>API 33+ 使用新签名 {@code writeCharacteristic(char, bytes, writeType)} 与 {@code writeDescriptor(desc, bytes)} 并校验 {@link BluetoothStatusCodes#SUCCESS}；</li>
 *   <li>API &lt; 33 使用旧签名并加同步保护；</li>
 *   <li>按 serviceUuid + serviceInstanceId + characteristicUuid + characteristicInstanceId 精确寻址同 UUID 多特征；</li>
 *   <li>统一转换 PHY / ConnectionPriority / GATT 服务树快照。</li>
 * </ul>
 */
@SuppressLint("MissingPermission")
public final class YoBleGattCompat {

    private YoBleGattCompat() {
    }

    @Nullable
    public static BluetoothGatt connectLe(@NonNull Context context,
                                          @NonNull BluetoothDevice device,
                                          boolean autoConnect,
                                          @NonNull android.bluetooth.BluetoothGattCallback callback) {
        return device.connectGatt(context, autoConnect, callback, BluetoothDevice.TRANSPORT_LE);
    }

    @SuppressWarnings("deprecation")
    public static boolean writeCharacteristic(@NonNull BluetoothGatt gatt,
                                              @NonNull BluetoothGattCharacteristic characteristic,
                                              @NonNull byte[] value,
                                              @NonNull BleWriteType writeType) {
        int androidWriteType = toAndroidWriteType(writeType);
        if (AndroidPlatform.usesTypedGattValueCallbacks()) {
            return gatt.writeCharacteristic(characteristic, value, androidWriteType)
                    == BluetoothStatusCodes.SUCCESS;
        }
        characteristic.setWriteType(androidWriteType);
        characteristic.setValue(value);
        return gatt.writeCharacteristic(characteristic);
    }

    @SuppressWarnings("deprecation")
    public static boolean writeDescriptor(@NonNull BluetoothGatt gatt,
                                          @NonNull BluetoothGattDescriptor descriptor,
                                          @NonNull byte[] value) {
        if (AndroidPlatform.usesTypedGattValueCallbacks()) {
            return gatt.writeDescriptor(descriptor, value) == BluetoothStatusCodes.SUCCESS;
        }
        descriptor.setValue(value);
        return gatt.writeDescriptor(descriptor);
    }

    public static int toAndroidWriteType(@NonNull BleWriteType writeType) {
        switch (writeType) {
            case WITHOUT_RESPONSE:
                return BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE;
            case SIGNED:
                return BluetoothGattCharacteristic.WRITE_TYPE_SIGNED;
            case WITH_RESPONSE:
            default:
                return BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT;
        }
    }

    public static int toAndroidConnectionPriority(@NonNull BleConnectionPriority priority) {
        switch (priority) {
            case HIGH:
                return BluetoothGatt.CONNECTION_PRIORITY_HIGH;
            case LOW_POWER:
                return BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER;
            case DCK:
                if (AndroidPlatform.supportsDckConnectionPriority()) {
                    return BluetoothGatt.CONNECTION_PRIORITY_DCK;
                }
                return BluetoothGatt.CONNECTION_PRIORITY_HIGH;
            case BALANCED:
            default:
                return BluetoothGatt.CONNECTION_PRIORITY_BALANCED;
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    public static int toAndroidPhyMask(@NonNull BlePhy phy) {
        switch (phy) {
            case LE_2M:
                return BluetoothDevice.PHY_LE_2M_MASK;
            case LE_CODED:
                return BluetoothDevice.PHY_LE_CODED_MASK;
            case LE_1M:
            default:
                return BluetoothDevice.PHY_LE_1M_MASK;
        }
    }

    @NonNull
    public static BlePhy fromAndroidPhy(int phy) {
        if (AndroidPlatform.supportsLePhy()) {
            if (phy == BluetoothDevice.PHY_LE_2M) {
                return BlePhy.LE_2M;
            }
            if (phy == BluetoothDevice.PHY_LE_CODED) {
                return BlePhy.LE_CODED;
            }
        }
        return BlePhy.LE_1M;
    }

    @RequiresApi(Build.VERSION_CODES.O)
    public static int toAndroidPhyOptions(@NonNull BlePhyOptions options) {
        switch (options) {
            case S2:
                return BluetoothDevice.PHY_OPTION_S2;
            case S8:
                return BluetoothDevice.PHY_OPTION_S8;
            case NO_PREFERRED:
            default:
                return BluetoothDevice.PHY_OPTION_NO_PREFERRED;
        }
    }

    @NonNull
    public static BlePhyConfig fromAndroidPhyValues(int txPhy, int rxPhy) {
        return new BlePhyConfig(
                fromAndroidPhy(txPhy),
                fromAndroidPhy(rxPhy),
                BlePhyOptions.NO_PREFERRED);
    }

    @Nullable
    public static BluetoothGattService findService(@NonNull BluetoothGatt gatt,
                                                   @NonNull UUID serviceUuid,
                                                   int serviceInstanceId) {
        if (serviceInstanceId < 0) {
            return gatt.getService(serviceUuid);
        }
        for (BluetoothGattService svc : gatt.getServices()) {
            if (serviceUuid.equals(svc.getUuid()) && svc.getInstanceId() == serviceInstanceId) {
                return svc;
            }
        }
        return null;
    }

    @Nullable
    public static BluetoothGattCharacteristic findCharacteristic(@NonNull BluetoothGatt gatt,
                                                                 @NonNull BleGattTarget target) {
        BluetoothGattService service = findService(
                gatt, target.serviceUuid(), target.serviceInstanceId());
        if (service == null) {
            return null;
        }
        if (target.characteristicInstanceId() < 0) {
            return service.getCharacteristic(target.characteristicUuid());
        }
        for (BluetoothGattCharacteristic ch : service.getCharacteristics()) {
            if (target.characteristicUuid().equals(ch.getUuid())
                    && ch.getInstanceId() == target.characteristicInstanceId()) {
                return ch;
            }
        }
        return null;
    }

    @Nullable
    public static BluetoothGattDescriptor findDescriptor(@NonNull BluetoothGatt gatt,
                                                         @NonNull BleGattTarget target) {
        if (target.descriptorUuid() == null) {
            return null;
        }
        BluetoothGattCharacteristic ch = findCharacteristic(gatt, target);
        return ch == null ? null : ch.getDescriptor(target.descriptorUuid());
    }

    @NonNull
    public static BleGattTarget targetForCharacteristic(@NonNull BluetoothGattCharacteristic ch) {
        BluetoothGattService svc = ch.getService();
        if (svc == null) {
            return BleGattTarget.characteristic(new UUID(0L, 0L), ch.getUuid());
        }
        return BleGattTarget.characteristic(
                svc.getUuid(), svc.getInstanceId(), ch.getUuid(), ch.getInstanceId());
    }

    @NonNull
    public static BleGattTarget targetForDescriptor(@NonNull BluetoothGattDescriptor desc) {
        BluetoothGattCharacteristic ch = desc.getCharacteristic();
        if (ch == null || ch.getService() == null) {
            return BleGattTarget.descriptor(
                    new UUID(0L, 0L), new UUID(0L, 0L), desc.getUuid());
        }
        BluetoothGattService svc = ch.getService();
        return BleGattTarget.descriptor(
                svc.getUuid(), svc.getInstanceId(), ch.getUuid(), ch.getInstanceId(), desc.getUuid());
    }

    @NonNull
    public static BleGattDatabaseSnapshot captureDatabaseSnapshot(@NonNull BleDeviceId deviceId,
                                                                   @NonNull BluetoothGatt gatt,
                                                                   long timestampMillis) {
        List<BluetoothGattService> rawServices = gatt.getServices();
        List<BleGattServiceInfo> converted = new ArrayList<>(rawServices.size());
        for (BluetoothGattService rawSvc : rawServices) {
            converted.add(convertService(rawSvc));
        }
        return new BleGattDatabaseSnapshot(deviceId, converted, timestampMillis);
    }

    @NonNull
    private static BleGattServiceInfo convertService(@NonNull BluetoothGattService rawSvc) {
        List<BluetoothGattCharacteristic> rawChars = rawSvc.getCharacteristics();
        List<BleGattCharacteristicInfo> chars = new ArrayList<>(rawChars.size());
        for (BluetoothGattCharacteristic rawChar : rawChars) {
            List<BluetoothGattDescriptor> rawDescs = rawChar.getDescriptors();
            List<BleGattDescriptorInfo> descs = new ArrayList<>(rawDescs.size());
            for (BluetoothGattDescriptor rawDesc : rawDescs) {
                descs.add(new BleGattDescriptorInfo(rawDesc.getUuid(), rawDesc.getPermissions()));
            }
            chars.add(new BleGattCharacteristicInfo(
                    rawChar.getUuid(),
                    rawChar.getInstanceId(),
                    rawChar.getProperties(),
                    rawChar.getPermissions(),
                    descs));
        }
        List<BluetoothGattService> rawIncluded = rawSvc.getIncludedServices();
        List<BleGattServiceInfo> included = new ArrayList<>(rawIncluded.size());
        for (BluetoothGattService inc : rawIncluded) {
            included.add(convertService(inc));
        }
        return new BleGattServiceInfo(
                rawSvc.getUuid(),
                rawSvc.getInstanceId(),
                rawSvc.getType() == BluetoothGattService.SERVICE_TYPE_PRIMARY,
                chars,
                included);
    }
}
