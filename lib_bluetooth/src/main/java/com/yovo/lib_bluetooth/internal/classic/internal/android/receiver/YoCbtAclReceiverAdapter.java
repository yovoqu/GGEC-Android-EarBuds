package com.yovo.lib_bluetooth.internal.classic.internal.android.receiver;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.common.diagnostics.YoBluetoothLogger;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceIdentity;
import com.yovo.lib_bluetooth.internal.classic.device.snapshot.YoCbtDeviceSnapshot;
import com.yovo.lib_bluetooth.internal.classic.internal.android.YoCbtAndroidDeviceMapper;
import com.yovo.lib_bluetooth.internal.classic.internal.android.YoCbtAndroidReceiverRegistry;
import com.yovo.lib_bluetooth.internal.classic.internal.android.YoCbtIntentCompat;
import com.yovo.lib_bluetooth.internal.classic.link.YoCbtAclLinkObserver;
import com.yovo.lib_bluetooth.internal.classic.link.YoCbtAclLinkState;
import com.yovo.lib_bluetooth.internal.classic.link.YoCbtAclLinkTracker;
import com.yovo.lib_bluetooth.internal.classic.runtime.YoCbtEvents;
import com.yovo.lib_bluetooth.internal.classic.runtime.YoCbtRuntime;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description ACL 连接/断开广播接收器适配器，更新 ACL 链路跟踪器。
 */

public final class YoCbtAclReceiverAdapter {

    private static final String TAG = "YoCbtAclReceiver";

    private final Context appContext;
    private final YoCbtRuntime runtime;
    private final YoCbtAndroidDeviceMapper mapper;
    private final YoCbtAclLinkTracker tracker;
    private final YoCbtAndroidReceiverRegistry receiverRegistry;
    private final List<YoCbtAclLinkObserver> observers = new ArrayList<>();

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            runtime.state(() -> handleIntent(intent));
        }
    };

    private boolean registered;

    public YoCbtAclReceiverAdapter(@NonNull Context appContext,
                                   @NonNull YoCbtRuntime runtime,
                                   @NonNull YoCbtAndroidDeviceMapper mapper,
                                   @NonNull YoCbtAclLinkTracker tracker,
                                   @NonNull YoCbtAndroidReceiverRegistry receiverRegistry) {
        this.appContext = appContext;
        this.runtime = runtime;
        this.mapper = mapper;
        this.tracker = tracker;
        this.receiverRegistry = receiverRegistry;
    }

    public void register() {
        if (registered) return;
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED);
        receiverRegistry.register(appContext, receiver, filter);
        registered = true;
    }

    public void unregister() {
        if (!registered) return;
        receiverRegistry.unregister(appContext, receiver);
        registered = false;
    }

    public void addObserver(@NonNull YoCbtAclLinkObserver observer) {
        synchronized (observers) {
            observers.add(Objects.requireNonNull(observer));
        }
    }

    public void removeObserver(@NonNull YoCbtAclLinkObserver observer) {
        synchronized (observers) {
            observers.remove(observer);
        }
    }

    private void handleIntent(@NonNull Intent intent) {
        String action = intent.getAction();
        if (action == null) return;

        BluetoothDevice device = YoCbtIntentCompat.device(intent, BluetoothDevice.EXTRA_DEVICE);
        if (device == null) return;

        YoCbtDeviceIdentity identity = mapper.map(device, null);
        if (identity == null) {
            YoBluetoothLogger.w(TAG, "按 action 映射蓝牙设备失败: " + action);
            return;
        }

        YoCbtAclLinkState state = mapActionToState(action);
        if (state == null) return;

        long timestamp = System.currentTimeMillis();
        final YoCbtDeviceSnapshot snapshot = tracker.updateAclState(identity, state, timestamp);

        runtime.eventHub().publish(YoCbtEvents.ACL_LINK_CHANGED, snapshot);
        runtime.callback(() -> notifyObservers(snapshot));
    }

    private void notifyObservers(YoCbtDeviceSnapshot snapshot) {
        List<YoCbtAclLinkObserver> copy;
        synchronized (observers) {
            copy = new ArrayList<>(observers);
        }
        for (YoCbtAclLinkObserver observer : copy) {
            observer.onAclLinkChanged(snapshot);
        }
    }

    private static YoCbtAclLinkState mapActionToState(String action) {
        switch (action) {
            case BluetoothDevice.ACTION_ACL_CONNECTED:
                return YoCbtAclLinkState.CONNECTED;
            case BluetoothDevice.ACTION_ACL_DISCONNECTED:
                return YoCbtAclLinkState.DISCONNECTED;
            case BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED:
                return YoCbtAclLinkState.DISCONNECT_REQUESTED;
            default:
                YoBluetoothLogger.w(TAG, "未知 ACL action: " + action);
                return null;
        }
    }
}
