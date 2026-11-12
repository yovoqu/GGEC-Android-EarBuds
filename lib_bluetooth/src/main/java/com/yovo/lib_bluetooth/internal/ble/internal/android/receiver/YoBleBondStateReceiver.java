package com.yovo.lib_bluetooth.internal.ble.internal.android.receiver;

import com.yovo.lib_bluetooth.internal.platform.AndroidPlatform;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.model.ble.BleBondState;
import com.yovo.lib_bluetooth.model.ble.BleDeviceId;

/**
 * 监听系统 {@link BluetoothDevice#ACTION_BOND_STATE_CHANGED}，精准透传旧/新状态与系统 reason。
 */
public final class YoBleBondStateReceiver extends BroadcastReceiver {

    private static final String EXTRA_REASON_KEY = "android.bluetooth.device.extra.REASON";

    public interface BondStateListener {
        void onBondStateChanged(@NonNull BleDeviceId deviceId,
                                @NonNull BleBondState previous,
                                @NonNull BleBondState current,
                                int rawReason);
    }

    private final BondStateListener listener;

    public YoBleBondStateReceiver(@NonNull BondStateListener listener) {
        this.listener = listener;
    }

    public void register(@NonNull Context context,
                         @NonNull YoBleAndroidReceiverRegistry registry) {
        registry.register(context, this, new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED));
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(intent.getAction())) {
            return;
        }
        BluetoothDevice device = getDeviceExtra(intent);
        if (device == null || !BleDeviceId.isValid(device.getAddress())) {
            return;
        }
        int prevRaw = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, -1);
        int currRaw = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1);
        int reason = intent.getIntExtra(EXTRA_REASON_KEY, 0);
        listener.onBondStateChanged(
                new BleDeviceId(device.getAddress()),
                mapBondState(prevRaw),
                mapBondState(currRaw),
                reason);
    }

    @Nullable
    @SuppressWarnings("deprecation")
    private static BluetoothDevice getDeviceExtra(@NonNull Intent intent) {
        if (AndroidPlatform.usesTypedParcelableExtras()) {
            return intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
        }
        return intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
    }

    @NonNull
    public static BleBondState mapBondState(int raw) {
        switch (raw) {
            case BluetoothDevice.BOND_NONE:
                return BleBondState.NONE;
            case BluetoothDevice.BOND_BONDING:
                return BleBondState.BONDING;
            case BluetoothDevice.BOND_BONDED:
                return BleBondState.BONDED;
            default:
                return BleBondState.UNKNOWN;
        }
    }
}
