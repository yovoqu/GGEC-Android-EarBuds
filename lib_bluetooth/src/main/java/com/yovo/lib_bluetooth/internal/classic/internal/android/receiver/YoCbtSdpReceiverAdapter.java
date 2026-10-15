package com.yovo.lib_bluetooth.internal.classic.internal.android.receiver;

import android.annotation.SuppressLint;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.common.concurrency.YoTaskScheduler;
import com.yovo.lib_bluetooth.internal.common.concurrency.YoTimeoutHandle;
import com.yovo.lib_bluetooth.internal.common.diagnostics.YoBluetoothLogger;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceIdentity;
import com.yovo.lib_bluetooth.internal.classic.error.YoCbtErrorCode;
import com.yovo.lib_bluetooth.internal.classic.error.YoCbtFailure;
import com.yovo.lib_bluetooth.internal.classic.internal.android.YoCbtAndroidDeviceMapper;
import com.yovo.lib_bluetooth.internal.classic.internal.android.YoCbtAndroidReceiverRegistry;
import com.yovo.lib_bluetooth.internal.classic.internal.android.YoCbtIntentCompat;
import com.yovo.lib_bluetooth.internal.classic.runtime.YoCbtRuntime;
import com.yovo.lib_bluetooth.internal.classic.service.channel.YoCbtServiceChannelRegistry;
import com.yovo.lib_bluetooth.internal.classic.service.model.YoCbtSdpResult;
import com.yovo.lib_bluetooth.internal.classic.service.model.YoCbtSdpSource;
import com.yovo.lib_bluetooth.internal.classic.service.discovery.YoCbtLiveSdpPort;
import com.yovo.lib_bluetooth.internal.classic.service.resolve.YoCbtServiceRecordFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description SDP UUID 发现完成广播接收器适配器。
 */

@SuppressLint("MissingPermission")
public final class YoCbtSdpReceiverAdapter implements YoCbtLiveSdpPort {

    private static final String TAG = "YoCbtSdpReceiver";

    private final Context appContext;
    private final YoCbtRuntime runtime;
    private final YoCbtAndroidDeviceMapper mapper;
    private final YoCbtServiceChannelRegistry channelRegistry;
    private final YoCbtAndroidReceiverRegistry receiverRegistry;
    private final Map<YoCbtDeviceId, PendingOperation> operations = new ConcurrentHashMap<>();

    private volatile YoCbtLiveSdpPort.Listener listener;
    private volatile boolean registered;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            runtime.state(() -> handleSdpIntent(intent));
        }
    };

    public YoCbtSdpReceiverAdapter(@NonNull Context appContext,
                                   @NonNull YoCbtRuntime runtime,
                                   @NonNull YoCbtAndroidDeviceMapper mapper,
                                   @NonNull YoCbtServiceChannelRegistry channelRegistry,
                                   @NonNull YoCbtAndroidReceiverRegistry receiverRegistry) {
        this.appContext = appContext.getApplicationContext();
        this.runtime = runtime;
        this.mapper = mapper;
        this.channelRegistry = channelRegistry;
        this.receiverRegistry = receiverRegistry;
    }

    @Override
    public void setListener(@Nullable YoCbtLiveSdpPort.Listener listener) {
        this.listener = listener;
    }

    public void register() {
        if (registered) return;
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_UUID);
        receiverRegistry.register(appContext, receiver, filter);
        registered = true;
    }

    public void unregister() {
        if (!registered) return;
        receiverRegistry.unregister(appContext, receiver);
        registered = false;
    }

    public void startDiscovery(@NonNull YoCbtDeviceId deviceId,
                               long timeoutMillis,
                               @NonNull YoTaskScheduler scheduler) {
        if (operations.containsKey(deviceId)) {
            fail(deviceId, YoCbtFailure.of(YoCbtErrorCode.YO_CBT_SDP_START_FAILED,
                    "SDP already in progress for " + deviceId, TAG));
            return;
        }

        BluetoothDevice remoteDevice = resolveDevice(deviceId);
        if (remoteDevice == null) {
            fail(deviceId, YoCbtFailure.of(YoCbtErrorCode.YO_CBT_SDP_START_FAILED,
                    "Cannot resolve BluetoothDevice for " + deviceId, TAG));
            return;
        }

        YoTimeoutHandle timeoutHandle = scheduler.schedule(() -> {
            PendingOperation timedOut = operations.remove(deviceId);
            if (timedOut != null) {
                fail(deviceId, YoCbtFailure.of(YoCbtErrorCode.YO_CBT_SDP_TIMEOUT,
                        "SDP discovery timed out for " + deviceId, TAG));
            }
        }, timeoutMillis);

        operations.put(deviceId, new PendingOperation(timeoutHandle));

        runtime.io(() -> {
            boolean started;
            try {
                started = remoteDevice.fetchUuidsWithSdp();
            } catch (Throwable ignored) {
                started = false;
            }
            if (!started) {
                PendingOperation failed = operations.remove(deviceId);
                if (failed != null) {
                    failed.timeoutHandle.cancel();
                }
                fail(deviceId, YoCbtFailure.of(YoCbtErrorCode.YO_CBT_SDP_START_FAILED,
                        "fetchUuidsWithSdp returned false for " + deviceId, TAG));
            }
        });
    }

    public void cancel(@NonNull YoCbtDeviceId deviceId) {
        PendingOperation op = operations.remove(deviceId);
        if (op != null) {
            op.timeoutHandle.cancel();
        }
    }

    public void cancelAll() {
        for (PendingOperation op : operations.values()) {
            op.timeoutHandle.cancel();
        }
        operations.clear();
    }

    @NonNull
    private static List<UUID> extractUuids(@NonNull Intent intent, @NonNull BluetoothDevice device) {
        List<UUID> uuids = YoCbtIntentCompat.uuids(intent, BluetoothDevice.EXTRA_UUID);
        if (!uuids.isEmpty()) return uuids;
        android.os.ParcelUuid[] parcelUuids = device.getUuids();
        if (parcelUuids != null) {
            return YoCbtIntentCompat.parcelUuids(parcelUuids);
        }
        return new ArrayList<>();
    }

    private void fail(@NonNull YoCbtDeviceId deviceId, @NonNull YoCbtFailure failure) {
        YoCbtLiveSdpPort.Listener current = listener;
        if (current != null) {
            runtime.callback(() -> current.onLiveSdpFailed(deviceId, failure));
        }
    }

    private void handleSdpIntent(@NonNull Intent intent) {
        if (!BluetoothDevice.ACTION_UUID.equals(intent.getAction())) return;

        BluetoothDevice device = YoCbtIntentCompat.device(intent, BluetoothDevice.EXTRA_DEVICE);
        if (device == null) return;

        YoCbtDeviceIdentity identity = mapper.map(device, null);
        if (identity == null) {
            YoBluetoothLogger.w(TAG, "从 ACTION_UUID 映射设备失败");
            return;
        }

        YoCbtDeviceId deviceId = identity.getDeviceId();
        PendingOperation op = operations.remove(deviceId);
        if (op == null) {
            YoBluetoothLogger.d(TAG, "未跟踪设备的 ACTION_UUID: " + deviceId);
            return;
        }
        op.timeoutHandle.cancel();

        List<UUID> uuids = extractUuids(intent, device);
        long now = System.currentTimeMillis();
        YoCbtSdpResult result = YoCbtServiceRecordFactory.fromUuidList(
                deviceId, uuids, YoCbtSdpSource.LIVE_SDP, channelRegistry, now);

        YoCbtLiveSdpPort.Listener current = listener;
        if (current != null) {
            runtime.callback(() -> current.onLiveSdpResult(deviceId, result));
        }
    }

    @Nullable
    private BluetoothDevice resolveDevice(@NonNull YoCbtDeviceId deviceId) {
        BluetoothManager manager = (BluetoothManager) appContext.getSystemService(Context.BLUETOOTH_SERVICE);
        if (manager == null) return null;
        BluetoothAdapter adapter = manager.getAdapter();
        if (adapter == null) return null;
        return adapter.getRemoteDevice(deviceId.value());
    }

    private static final class PendingOperation {
        final YoTimeoutHandle timeoutHandle;

        PendingOperation(YoTimeoutHandle timeoutHandle) {
            this.timeoutHandle = timeoutHandle;
        }
    }
}
