package com.yovo.lib_bluetooth.internal.ble.internal.android.receiver;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.annotation.NonNull;

/**
 * 监听系统 {@link BluetoothAdapter#ACTION_STATE_CHANGED}，一旦关闭即时触发 BLE 全量清理。
 */
public final class YoBleAdapterStateReceiver extends BroadcastReceiver {

    private final Runnable onAdapterTurnedOff;

    public YoBleAdapterStateReceiver(@NonNull Runnable onAdapterTurnedOff) {
        this.onAdapterTurnedOff = onAdapterTurnedOff;
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
        if (state == BluetoothAdapter.STATE_OFF || state == BluetoothAdapter.STATE_TURNING_OFF) {
            onAdapterTurnedOff.run();
        }
    }
}
