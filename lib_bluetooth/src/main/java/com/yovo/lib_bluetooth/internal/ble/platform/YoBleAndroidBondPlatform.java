package com.yovo.lib_bluetooth.internal.ble.platform;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.ble.pairing.YoBleBondPlatform;
import com.yovo.lib_bluetooth.internal.ble.platform.hiddenapi.YoBleBondReflection;
import com.yovo.lib_bluetooth.internal.ble.platform.hiddenapi.YoBleHiddenApiPolicy;
import com.yovo.lib_bluetooth.internal.ble.platform.receiver.YoBleBondStateReceiver;
import com.yovo.lib_bluetooth.internal.common.bluetooth.YoBluetoothAdapterProvider;
import com.yovo.lib_bluetooth.model.ble.BleBondState;
import com.yovo.lib_bluetooth.model.ble.BleDeviceId;
import com.yovo.lib_bluetooth.model.ble.BleDeviceIdentity;
import com.yovo.lib_bluetooth.model.ble.BleDeviceType;
import com.yovo.lib_bluetooth.model.ble.BleErrorCode;
import com.yovo.lib_bluetooth.provider.lib_common.log.YoBtLogger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Android BLE Bond 平台适配实现。
 *
 * <p>权限是前置条件：调用链在进入本类前已完成 BLUETOOTH_CONNECT 校验；
 * 系统在权限被中途撤销时抛出的 {@link SecurityException} 按“该查询/动作不可用”处理，
 * 不伪装成链路失败。</p>
 */
@SuppressLint("MissingPermission")
public final class YoBleAndroidBondPlatform implements YoBleBondPlatform {

    private static final String TAG = "YoBleAndroidBond";

    private final YoBluetoothAdapterProvider adapterProvider;

    public YoBleAndroidBondPlatform(@NonNull YoBluetoothAdapterProvider adapterProvider) {
        this.adapterProvider = adapterProvider;
    }

    @NonNull
    @Override
    public List<BleDeviceIdentity> getBondedDevices() {
        BluetoothAdapter adapter = adapterProvider.adapter();
        if (adapter == null) {
            return Collections.emptyList();
        }
        Set<BluetoothDevice> bonded;
        try {
            bonded = adapter.getBondedDevices();
        } catch (SecurityException denied) {
            // 无 BLUETOOTH_CONNECT 时系统拒绝枚举配对列表；本接口无失败通道，按“暂无配对设备”呈现。
            YoBtLogger.w(TAG, "getBondedDevices() denied by permission", denied);
            return Collections.emptyList();
        }
        if (bonded == null || bonded.isEmpty()) {
            return Collections.emptyList();
        }
        long now = System.currentTimeMillis();
        List<BleDeviceIdentity> out = new ArrayList<>();
        for (BluetoothDevice device : bonded) {
            if (device == null || !BleDeviceId.isValid(device.getAddress())) {
                continue;
            }
            BleDeviceType type = YoBleAndroidDeviceMapper.resolveDeviceType(device);
            if (type == BleDeviceType.CLASSIC) {
                continue; // 只要 BLE / Dual / Unknown
            }
            // 配对列表没有广播包，不能把 getName()（经典/缓存名）写成 BLE 广播名。
            out.add(YoBleAndroidDeviceMapper.identity(
                    device, new BleDeviceId(device.getAddress()), "", now));
        }
        return out;
    }

    @NonNull
    @Override
    public BleBondState getBondState(@NonNull BleDeviceId deviceId) {
        BluetoothDevice device = resolve(deviceId);
        if (device == null) {
            return BleBondState.UNKNOWN;
        }
        return YoBleBondStateReceiver.mapBondState(device.getBondState());
    }

    @NonNull
    @Override
    public ActionResult createBond(@NonNull BleDeviceId deviceId) {
        BluetoothDevice device = resolve(deviceId);
        if (device == null) {
            return ActionResult.fail(BleErrorCode.DEVICE_INVALID, "Invalid device MAC");
        }
        boolean started;
        try {
            started = device.createBond();
        } catch (SecurityException denied) {
            return ActionResult.fail(
                    BleErrorCode.PERMISSION_CONNECT_DENIED,
                    "BluetoothDevice.createBond() denied: " + denied.getMessage());
        }
        return started
                ? ActionResult.ok()
                : ActionResult.fail(
                        BleErrorCode.BOND_FAILED, "BluetoothDevice.createBond() returned false");
    }

    @NonNull
    @Override
    public ActionResult cancelBondProcess(@NonNull BleDeviceId deviceId) {
        BluetoothDevice device = resolve(deviceId);
        if (device == null) {
            return ActionResult.fail(BleErrorCode.DEVICE_INVALID, "Invalid device MAC");
        }
        boolean cancelled = YoBleBondReflection.cancelBondProcess(device);
        return cancelled
                ? ActionResult.ok()
                : ActionResult.fail(BleErrorCode.BOND_CANCELLED, "cancelBondProcess failed or unsupported");
    }

    @NonNull
    @Override
    public ActionResult removeBond(@NonNull BleDeviceId deviceId, boolean allowHiddenApi) {
        if (!allowHiddenApi) {
            return ActionResult.fail(BleErrorCode.HIDDEN_API_BLOCKED, "Hidden API removeBond disabled");
        }
        BluetoothDevice device = resolve(deviceId);
        if (device == null) {
            return ActionResult.fail(BleErrorCode.DEVICE_INVALID, "Invalid device MAC");
        }
        boolean removed = YoBleBondReflection.removeBond(device, YoBleHiddenApiPolicy.EXPLICITLY_ENABLED);
        return removed
                ? ActionResult.ok()
                : ActionResult.fail(BleErrorCode.HIDDEN_API_FAILED, "removeBond returned false");
    }

    @Nullable
    private BluetoothDevice resolve(@NonNull BleDeviceId deviceId) {
        BluetoothAdapter adapter = adapterProvider.adapter();
        if (adapter == null) {
            return null;
        }
        try {
            return adapter.getRemoteDevice(deviceId.value());
        } catch (IllegalArgumentException malformedAddress) {
            return null;
        }
    }
}
