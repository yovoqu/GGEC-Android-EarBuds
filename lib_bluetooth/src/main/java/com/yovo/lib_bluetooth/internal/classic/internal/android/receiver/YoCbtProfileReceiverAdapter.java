package com.yovo.lib_bluetooth.internal.classic.internal.android.receiver;

import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothHeadset;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.classic.internal.android.YoCbtAndroidReceiverRegistry;
import com.yovo.lib_bluetooth.internal.classic.internal.android.profiles.YoCbtAndroidProfileBroadcastHandler;
import com.yovo.lib_bluetooth.internal.classic.profiles.state.YoCbtProfileStateObserver;
import com.yovo.lib_bluetooth.internal.classic.runtime.YoCbtRuntime;

import java.util.List;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description Profile 连接状态广播接收器适配器。
 */

public final class YoCbtProfileReceiverAdapter {

    public interface ObserverProvider {
        @NonNull
        List<YoCbtProfileStateObserver> observersFor(@NonNull Intent intent);
    }

    private final Context appContext;
    private final YoCbtRuntime runtime;
    private final YoCbtAndroidProfileBroadcastHandler broadcastHandler;
    private final YoCbtAndroidReceiverRegistry receiverRegistry;
    private final ObserverProvider observerProvider;

    private BroadcastReceiver receiver;
    private boolean registered;

    public YoCbtProfileReceiverAdapter(@NonNull Context appContext,
                                       @NonNull YoCbtRuntime runtime,
                                       @NonNull YoCbtAndroidProfileBroadcastHandler broadcastHandler,
                                       @NonNull ObserverProvider observerProvider,
                                       @NonNull YoCbtAndroidReceiverRegistry receiverRegistry) {
        this.appContext = appContext.getApplicationContext();
        this.runtime = runtime;
        this.broadcastHandler = broadcastHandler;
        this.observerProvider = observerProvider;
        this.receiverRegistry = receiverRegistry;
    }

    public void register() {
        if (registered) return;

        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null) return;
                runtime.state(() -> dispatch(intent));
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED);
        filter.addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
        filter.addAction(BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED);
        receiverRegistry.register(appContext, receiver, filter);
        registered = true;
    }

    public void unregister() {
        if (!registered || receiver == null) return;
        receiverRegistry.unregister(appContext, receiver);
        receiver = null;
        registered = false;
    }

    private void dispatch(@NonNull Intent intent) {
        String action = intent.getAction();
        if (action == null) return;

        List<YoCbtProfileStateObserver> observers = observerProvider.observersFor(intent);
        if (BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED.equals(action)) {
            broadcastHandler.handleA2dpBroadcast(intent, observers);
        } else if (BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED.equals(action)) {
            broadcastHandler.handleHeadsetBroadcast(intent, observers);
        } else if (BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED.equals(action)) {
            broadcastHandler.handleHeadsetAudioBroadcast(intent, observers);
        }
    }
}
