package com.yovo.lib_bluetooth.internal.ble.internal.android.receiver;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.model.ble.BleAdapterState;

import java.util.function.Consumer;

/**
 * 监听系统 {@link BluetoothAdapter#ACTION_STATE_CHANGED}，把全部适配器状态交给核心。
 */
public final class YoBleAdapterStateReceiver extends BroadcastReceiver {

    private final Consumer<BleAdapterState> onAdapterStateChanged;

    public YoBleAdapterStateReceiver(@NonNull Consumer<BleAdapterState> onAdapterStateChanged) {
        this.onAdapterStateChanged = onAdapterStateChanged;
    }

    public void register(@NonNull Context context,
                         @NonNull YoBleAndroidReceiverRegistry registry) {
        registry.register(context, this, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) {
            return;
        }
        int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
        onAdapterStateChanged.accept(map(state));
    }

    @NonNull
    private static BleAdapterState map(int state) {
        switch (state) {
            case BluetoothAdapter.STATE_OFF:
                return BleAdapterState.OFF;
            case BluetoothAdapter.STATE_TURNING_ON:
                return BleAdapterState.TURNING_ON;
            case BluetoothAdapter.STATE_ON:
                return BleAdapterState.ON;
            case BluetoothAdapter.STATE_TURNING_OFF:
                return BleAdapterState.TURNING_OFF;
            default:
                return BleAdapterState.UNKNOWN;
        }
    }
}
