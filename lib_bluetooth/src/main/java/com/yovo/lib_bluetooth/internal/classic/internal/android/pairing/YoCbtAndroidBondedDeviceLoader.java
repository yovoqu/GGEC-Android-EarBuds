package com.yovo.lib_bluetooth.internal.classic.internal.android.pairing;

import android.annotation.SuppressLint;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.os.ParcelUuid;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.common.bluetooth.YoBluetoothAdapterProvider;
import com.yovo.lib_bluetooth.internal.common.permission.YoBluetoothPermission;
import com.yovo.lib_bluetooth.internal.common.permission.YoBluetoothPermissionChecker;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceIdentity;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceType;
import com.yovo.lib_bluetooth.internal.classic.device.snapshot.YoCbtBondedDevice;
import com.yovo.lib_bluetooth.internal.classic.internal.android.YoCbtAndroidDeviceMapper;
import com.yovo.lib_bluetooth.internal.classic.pairing.YoCbtBondState;
import com.yovo.lib_bluetooth.internal.classic.pairing.YoCbtBondedDeviceLoader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description Android 已配对设备加载器实现。
 */

@SuppressLint("MissingPermission")
public final class YoCbtAndroidBondedDeviceLoader implements YoCbtBondedDeviceLoader {

    private static final Set<YoCbtDeviceType> SUPPORTED_TYPES =
            EnumSet.of(YoCbtDeviceType.CLASSIC, YoCbtDeviceType.DUAL);

    private final YoBluetoothAdapterProvider adapterProvider;
    private final YoBluetoothPermissionChecker permissionChecker;
    private final YoCbtAndroidDeviceMapper deviceMapper;

    public YoCbtAndroidBondedDeviceLoader(@NonNull YoBluetoothAdapterProvider adapterProvider,
                                          @NonNull YoBluetoothPermissionChecker permissionChecker,
                                          @NonNull YoCbtAndroidDeviceMapper deviceMapper) {
        this.adapterProvider = Objects.requireNonNull(adapterProvider);
        this.permissionChecker = Objects.requireNonNull(permissionChecker);
        this.deviceMapper = Objects.requireNonNull(deviceMapper);
    }

    @NonNull
    @Override
    public List<YoCbtBondedDevice> load(@NonNull Context context) {
        if (!permissionChecker.has(context, YoBluetoothPermission.CONNECT)) {
            return Collections.emptyList();
        }
        BluetoothAdapter adapter = adapterProvider.adapter();
        if (adapter == null) {
            return Collections.emptyList();
        }

        Set<BluetoothDevice> bondedDevices;
        try {
            bondedDevices = adapter.getBondedDevices();
        } catch (SecurityException e) {
            return Collections.emptyList();
        }
        if (bondedDevices == null || bondedDevices.isEmpty()) {
            return Collections.emptyList();
        }

        List<YoCbtBondedDevice> result = new ArrayList<>(bondedDevices.size());
        for (BluetoothDevice device : bondedDevices) {
            YoCbtBondedDevice bonded = map(device);
            if (bonded == null) continue;
            if (!SUPPORTED_TYPES.contains(bonded.getIdentity().getDeviceType())) continue;
            result.add(bonded);
        }
        return result;
    }

    @Nullable
    private YoCbtBondedDevice map(@NonNull BluetoothDevice device) {
        YoCbtDeviceIdentity identity = deviceMapper.map(device, null);
        if (identity == null) return null;
        return new YoCbtBondedDevice(
                identity,
                mapBondState(device),
                readUuids(device),
                System.currentTimeMillis());
    }

    @NonNull
    private static YoCbtBondState mapBondState(@NonNull BluetoothDevice device) {
        int state = device.getBondState();
        switch (state) {
            case BluetoothDevice.BOND_NONE:
                return YoCbtBondState.NONE;
            case BluetoothDevice.BOND_BONDING:
                return YoCbtBondState.BONDING;
            case BluetoothDevice.BOND_BONDED:
                return YoCbtBondState.BONDED;
            default:
                return YoCbtBondState.UNKNOWN;
        }
    }

    @NonNull
    private static List<UUID> readUuids(@NonNull BluetoothDevice device) {
        ParcelUuid[] uuids = device.getUuids();
        if (uuids == null || uuids.length == 0) {
            return Collections.emptyList();
        }
        List<UUID> result = new ArrayList<>(uuids.length);
        for (ParcelUuid parcelUuid : uuids) {
            if (parcelUuid != null) {
                result.add(parcelUuid.getUuid());
            }
        }
        return result;
    }
}
