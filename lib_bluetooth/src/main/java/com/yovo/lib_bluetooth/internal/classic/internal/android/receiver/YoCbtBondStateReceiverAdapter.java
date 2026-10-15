package com.yovo.lib_bluetooth.internal.classic.internal.android.receiver;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceIdentity;
import com.yovo.lib_bluetooth.internal.classic.device.snapshot.YoCbtDeviceSnapshot;
import com.yovo.lib_bluetooth.internal.classic.device.snapshot.YoCbtDeviceSnapshotStore;
import com.yovo.lib_bluetooth.internal.classic.internal.android.YoCbtAndroidDeviceMapper;
import com.yovo.lib_bluetooth.internal.classic.internal.android.YoCbtAndroidReceiverRegistry;
import com.yovo.lib_bluetooth.internal.classic.pairing.YoCbtBondState;
import com.yovo.lib_bluetooth.internal.classic.pairing.YoCbtBondStateObserver;
import com.yovo.lib_bluetooth.internal.classic.runtime.YoCbtRuntime;

import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 配对状态广播接收器适配器。
 */

public final class YoCbtBondStateReceiverAdapter {

    private final Context appContext;
    private final YoCbtRuntime runtime;
    private final YoCbtAndroidDeviceMapper mapper;
    private final YoCbtDeviceSnapshotStore snapshotStore;
    private final YoCbtAndroidReceiverRegistry receiverRegistry;
    private final CopyOnWriteArrayList<YoCbtBondStateObserver> observers =
            new CopyOnWriteArrayList<>();

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(intent.getAction())) {
                return;
            }
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (device == null) {
                return;
            }
            int bondState = intent.getIntExtra(
                    BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR);
            runtime.state(() -> handleBondStateChanged(device, bondState));
        }
    };

    private volatile boolean registered;

    public YoCbtBondStateReceiverAdapter(
            @NonNull Context appContext,
            @NonNull YoCbtRuntime runtime,
            @NonNull YoCbtAndroidDeviceMapper mapper,
            @NonNull YoCbtDeviceSnapshotStore snapshotStore,
            @NonNull YoCbtAndroidReceiverRegistry receiverRegistry) {
        this.appContext = appContext.getApplicationContext();
        this.runtime = runtime;
        this.mapper = mapper;
        this.snapshotStore = snapshotStore;
        this.receiverRegistry = receiverRegistry;
    }

    public void register() {
        if (registered) return;
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        receiverRegistry.register(appContext, receiver, filter);
        registered = true;
    }

    public void unregister() {
        if (!registered) return;
        receiverRegistry.unregister(appContext, receiver);
        registered = false;
    }

    public void addObserver(@NonNull YoCbtBondStateObserver observer) {
        observers.addIfAbsent(Objects.requireNonNull(observer));
    }

    public void removeObserver(@NonNull YoCbtBondStateObserver observer) {
        observers.remove(observer);
    }

    private void handleBondStateChanged(BluetoothDevice device, int bondState) {
        if (runtime.isReleased()) return;

        YoCbtDeviceIdentity identity = mapper.map(device, null);
        if (identity == null) return;

        YoCbtBondState mapped = mapBondState(bondState);
        long now = System.currentTimeMillis();
        final YoCbtDeviceSnapshot snapshot = snapshotStore.updateBond(identity, mapped, now);

        runtime.callback(() -> {
            for (YoCbtBondStateObserver observer : observers) {
                observer.onBondStateChanged(snapshot);
            }
        });
    }

    @NonNull
    private static YoCbtBondState mapBondState(int androidBondState) {
        switch (androidBondState) {
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
}
