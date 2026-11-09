package com.yovo.lib_bluetooth.internal.ble.gatt;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.ble.device.YoBleCapabilityInspector;
import com.yovo.lib_bluetooth.internal.ble.device.YoBleDeviceSnapshotStore;
import com.yovo.lib_bluetooth.internal.ble.diagnostics.YoBleTimelineRecorder;
import com.yovo.lib_bluetooth.internal.ble.runtime.YoBleRuntime;
import com.yovo.lib_bluetooth.model.ble.BleBondState;
import com.yovo.lib_bluetooth.model.ble.BleConfig;
import com.yovo.lib_bluetooth.model.ble.BleConnectionObserver;
import com.yovo.lib_bluetooth.model.ble.BleConnectionParameters;
import com.yovo.lib_bluetooth.model.ble.BleDeviceId;
import com.yovo.lib_bluetooth.model.ble.BleFailure;
import com.yovo.lib_bluetooth.model.ble.BleGattDatabaseSnapshot;
import com.yovo.lib_bluetooth.model.ble.BlePhyConfig;

import java.util.function.Consumer;

/**
 * 暴露给 gatt / transfer 协调器的单会话上下文边界，保持单向无环依赖。
 */
public interface YoBleSessionContext {

    @NonNull
    BleDeviceId deviceId();

    @NonNull
    YoBleRuntime runtime();

    @NonNull
    BleConfig bleConfig();

    @NonNull
    YoBleCapabilityInspector capabilityInspector();

    @NonNull
    YoBleDeviceSnapshotStore snapshotStore();

    @NonNull
    YoBleTimelineRecorder timeline();

    long nowMillis();

    long operationTimeoutMillis();

    long bondedDiscoveryDelayMillis();

    boolean isConnected();

    boolean isReady();

    @NonNull
    BleBondState bondState();

    int currentMtu();

    @Nullable
    Integer overriddenMtu();

    void onMtuUpdated(int mtu);

    void onMtuOverridden(int mtu);

    void onPhyUpdated(@NonNull BlePhyConfig phy);

    void onConnectionParametersUpdated(@NonNull BleConnectionParameters parameters);

    void onRssiUpdated(int rssi);

    @Nullable
    BleGattDatabaseSnapshot currentDatabase();

    void onDatabaseUpdated(@NonNull BleGattDatabaseSnapshot database);

    void onDatabaseCleared();

    @Nullable
    YoBleGattTransport transport();

    @NonNull
    YoBleGattOperationQueue operationQueue();

    @NonNull
    YoBleNotificationRouter notificationRouter();

    void notifyConnectionObservers(@NonNull Consumer<BleConnectionObserver> emitter);

    /** 判断并处理 pre-O ATT 认证失败自动配对重试；返回 true 表示由邦定流程接管重试，原操作暂缓失败。 */
    boolean handleAuthErrorIfConfigured(@NonNull BleFailure failure, @NonNull Runnable retryAction);
}
