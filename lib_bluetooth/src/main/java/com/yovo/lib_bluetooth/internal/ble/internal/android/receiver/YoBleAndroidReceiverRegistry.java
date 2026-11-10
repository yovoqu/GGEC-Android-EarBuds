package com.yovo.lib_bluetooth.internal.ble.internal.android.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.yovo.lib_bluetooth.internal.common.diagnostics.YoBtLogger;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * BLE Android BroadcastReceiver 幂等注册中心（适配 Android 13+ RECEIVER_EXPORTED）。
 */
public final class YoBleAndroidReceiverRegistry {

    private static final String TAG = "YoBleReceiverRegistry";

    private static final class Entry {
        final BroadcastReceiver receiver;
        final Context appContext;

        Entry(@NonNull BroadcastReceiver receiver, @NonNull Context appContext) {
            this.receiver = receiver;
            this.appContext = appContext;
        }
    }

    private final CopyOnWriteArrayList<Entry> entries = new CopyOnWriteArrayList<>();

    public void register(@NonNull Context context,
                         @NonNull BroadcastReceiver receiver,
                         @NonNull IntentFilter filter) {
        for (Entry e : entries) {
            if (e.receiver == receiver) {
                return;
            }
        }
        Context app = context.getApplicationContext();
        ContextCompat.registerReceiver(app, receiver, filter, ContextCompat.RECEIVER_EXPORTED);
        entries.add(new Entry(receiver, app));
        YoBtLogger.d(TAG, "Registered receiver: " + receiver.getClass().getSimpleName());
    }

    public void unregisterAll() {
        for (Entry e : entries) {
            try {
                e.appContext.unregisterReceiver(e.receiver);
            } catch (Throwable ignored) {
            }
        }
        entries.clear();
    }
}
