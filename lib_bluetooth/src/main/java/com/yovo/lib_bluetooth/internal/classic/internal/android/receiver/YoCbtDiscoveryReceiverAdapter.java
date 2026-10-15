package com.yovo.lib_bluetooth.internal.classic.internal.android.receiver;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.classic.discovery.YoCbtDiscoveryBroadcastPort;
import com.yovo.lib_bluetooth.internal.classic.discovery.YoCbtDiscoveryDeviceFound;
import com.yovo.lib_bluetooth.internal.classic.internal.android.YoCbtAndroidDeviceMapper;
import com.yovo.lib_bluetooth.internal.classic.internal.android.YoCbtAndroidReceiverRegistry;
import com.yovo.lib_bluetooth.internal.classic.internal.android.YoCbtIntentCompat;
import com.yovo.lib_bluetooth.internal.classic.runtime.YoCbtRuntime;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 设备发现广播接收器适配器。
 */

public final class YoCbtDiscoveryReceiverAdapter implements YoCbtDiscoveryBroadcastPort {

    private final Context appContext;
    private final YoCbtRuntime runtime;
    private final YoCbtAndroidDeviceMapper mapper;
    private final YoCbtAndroidReceiverRegistry receiverRegistry;
    private final AtomicBoolean registered = new AtomicBoolean(false);

    private volatile YoCbtDiscoveryBroadcastPort.Listener listener;
    private BroadcastReceiver receiver;

    public YoCbtDiscoveryReceiverAdapter(@NonNull Context appContext,
                                         @NonNull YoCbtRuntime runtime,
                                         @NonNull YoCbtAndroidDeviceMapper mapper,
                                         @NonNull YoCbtAndroidReceiverRegistry receiverRegistry) {
        this.appContext = appContext.getApplicationContext();
        this.runtime = runtime;
        this.mapper = mapper;
        this.receiverRegistry = receiverRegistry;
    }

    @Override
    public void setListener(@Nullable YoCbtDiscoveryBroadcastPort.Listener listener) {
        this.listener = listener;
    }

    public void register() {
        if (!registered.compareAndSet(false, true)) return;

        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null) return;
                String action = intent.getAction();
                if (action == null) return;
                runtime.state(() -> dispatch(action, intent));
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        receiverRegistry.register(appContext, receiver, filter);
    }

    public void unregister() {
        if (!registered.compareAndSet(true, false)) return;
        listener = null;
        if (receiver == null) return;
        receiverRegistry.unregister(appContext, receiver);
        receiver = null;
    }

    private void dispatch(@NonNull String action, @NonNull Intent intent) {
        YoCbtDiscoveryBroadcastPort.Listener active = listener;
        if (active == null) return;
        switch (action) {
            case BluetoothAdapter.ACTION_DISCOVERY_STARTED:
                active.onDiscoveryStarted();
                break;
            case BluetoothAdapter.ACTION_DISCOVERY_FINISHED:
                active.onDiscoveryFinished();
                break;
            case BluetoothDevice.ACTION_FOUND:
                YoCbtDiscoveryDeviceFound found = parseDeviceFound(intent);
                if (found != null) {
                    active.onDeviceFound(found);
                }
                break;
            default:
                break;
        }
    }

    @Nullable
    private YoCbtDiscoveryDeviceFound parseDeviceFound(@NonNull Intent intent) {
        BluetoothDevice device = YoCbtIntentCompat.device(intent, BluetoothDevice.EXTRA_DEVICE);
        if (device == null) return null;

        String explicitName = intent.hasExtra(BluetoothDevice.EXTRA_NAME)
                ? intent.getStringExtra(BluetoothDevice.EXTRA_NAME)
                : null;
        com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceIdentity identity =
                mapper.map(device, explicitName);
        if (identity == null) return null;

        BluetoothClass btClass = YoCbtIntentCompat.bluetoothClass(intent, BluetoothDevice.EXTRA_CLASS);
        int rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE);
        return new YoCbtDiscoveryDeviceFound(identity, mapper.mapClass(btClass), rssi);
    }
}
