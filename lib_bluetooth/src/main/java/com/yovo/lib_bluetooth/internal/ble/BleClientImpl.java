package com.yovo.lib_bluetooth.internal.ble;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.api.YoBleClient;
import com.yovo.lib_bluetooth.internal.ble.internal.android.YoBleAndroidModuleGraph;
import com.yovo.lib_bluetooth.model.ble.BleAdvertiseConfig;
import com.yovo.lib_bluetooth.model.ble.BleAdvertiseData;
import com.yovo.lib_bluetooth.model.ble.BleAdvertiseObserver;
import com.yovo.lib_bluetooth.model.ble.BleBondObserver;
import com.yovo.lib_bluetooth.model.ble.BleConfig;
import com.yovo.lib_bluetooth.model.ble.BleConnectionConfig;
import com.yovo.lib_bluetooth.model.ble.BleConnectionObserver;
import com.yovo.lib_bluetooth.model.ble.BleConnectionPriority;
import com.yovo.lib_bluetooth.model.ble.BleConnectionSnapshot;
import com.yovo.lib_bluetooth.model.ble.BleDataSplitter;
import com.yovo.lib_bluetooth.model.ble.BleDeviceId;
import com.yovo.lib_bluetooth.model.ble.BleDeviceSnapshot;
import com.yovo.lib_bluetooth.model.ble.BleDiagnosticReport;
import com.yovo.lib_bluetooth.model.ble.BleGattDatabaseSnapshot;
import com.yovo.lib_bluetooth.model.ble.BleGattObserver;
import com.yovo.lib_bluetooth.model.ble.BleGattServerConfig;
import com.yovo.lib_bluetooth.model.ble.BleGattServerObserver;
import com.yovo.lib_bluetooth.model.ble.BleGattTarget;
import com.yovo.lib_bluetooth.model.ble.BleL2capChannelObserver;
import com.yovo.lib_bluetooth.model.ble.BleNotificationObserver;
import com.yovo.lib_bluetooth.model.ble.BleNotificationSetupMode;
import com.yovo.lib_bluetooth.model.ble.BleOperationHandle;
import com.yovo.lib_bluetooth.model.ble.BleOperationPriority;
import com.yovo.lib_bluetooth.model.ble.BlePhy;
import com.yovo.lib_bluetooth.model.ble.BlePhyConfig;
import com.yovo.lib_bluetooth.model.ble.BlePhyOptions;
import com.yovo.lib_bluetooth.model.ble.BleRequestResponseConfig;
import com.yovo.lib_bluetooth.model.ble.BleScanConfig;
import com.yovo.lib_bluetooth.model.ble.BleScanObserver;
import com.yovo.lib_bluetooth.model.ble.BleTransferProgressObserver;
import com.yovo.lib_bluetooth.model.ble.BleValueObserver;
import com.yovo.lib_bluetooth.model.ble.BleWriteType;
import com.yovo.lib_bluetooth.model.common.Result;

import java.util.List;

/**
 * {@link YoBleClient} 统一对外门面实现，仅作纯委托转发与生命周期托管。
 */
public final class BleClientImpl implements YoBleClient {

    @NonNull
    private final YoBleAndroidModuleGraph androidGraph;
    @NonNull
    private final YoBleModuleCore core;

    public BleClientImpl(@NonNull Context context, @NonNull BleConfig config) {
        this.androidGraph = YoBleAndroidModuleGraph.create(context, config);
        this.core = androidGraph.core;
    }

    // ── 1. 硬件能力、适配器与设备快照 ─────────────────────────────

    @Override
    public boolean isBleSupported() {
        return core.isBleSupported();
    }

    @Override
    public boolean isAdapterEnabled() {
        return core.isAdapterEnabled();
    }

    @NonNull
    @Override
    public Result<BleDeviceSnapshot> getDeviceSnapshot(@NonNull BleDeviceId deviceId) {
        return core.getDeviceSnapshot(deviceId);
    }

    @NonNull
    @Override
    public Result<List<BleDeviceSnapshot>> getDeviceSnapshots() {
        return core.getDeviceSnapshots();
    }

    @NonNull
    @Override
    public Result<List<BleConnectionSnapshot>> getConnectedDevices() {
        return core.connectionManager.getConnectedDevices();
    }

    // ── 2. BLE 扫描 ────────────────────────────────────────────────

    @NonNull
    @Override
    public BleOperationHandle scan(@NonNull BleScanConfig config, @NonNull BleScanObserver observer) {
        return core.scanController.scan(config, observer);
    }

    @NonNull
    @Override
    public Result<Void> stopScan() {
        return core.scanController.stopScan();
    }

    @NonNull
    @Override
    public Result<Void> flushBatchScanResults() {
        return core.scanController.flushBatchScanResults();
    }

    @Override
    public boolean isScanning() {
        return core.scanController.isScanning();
    }

    // ── 3. 多设备 GATT 连接 ────────────────────────────────────────

    @NonNull
    @Override
    public BleOperationHandle connect(@NonNull BleDeviceId deviceId,
                                      @NonNull BleConnectionConfig config,
                                      @NonNull BleConnectionObserver observer) {
        return core.connectionManager.connect(deviceId, config, observer);
    }

    @NonNull
    @Override
    public Result<Void> reconnect(@NonNull BleDeviceId deviceId) {
        return core.connectionManager.reconnect(deviceId);
    }

    @NonNull
    @Override
    public Result<Void> disconnect(@NonNull BleDeviceId deviceId) {
        return core.connectionManager.disconnect(deviceId);
    }

    @NonNull
    @Override
    public Result<Void> disconnectAll() {
        return core.connectionManager.disconnectAll();
    }

    @NonNull
    @Override
    public Result<BleConnectionSnapshot> getConnectionSnapshot(@NonNull BleDeviceId deviceId) {
        return core.connectionManager.getConnectionSnapshot(deviceId);
    }

    @NonNull
    @Override
    public BleOperationHandle observeConnectionState(@NonNull BleConnectionObserver observer) {
        return core.connectionManager.observeConnectionState(observer);
    }

    // ── 4. 链路调优 ────────────────────────────────────────────────

    @NonNull
    @Override
    public BleOperationHandle requestMtu(@NonNull BleDeviceId deviceId,
                                         int mtu,
                                         @NonNull BleValueObserver<Integer> observer) {
        return core.connectionManager.requestMtu(deviceId, mtu, observer);
    }

    @NonNull
    @Override
    public Result<Integer> overrideMtu(@NonNull BleDeviceId deviceId, int mtu) {
        return core.connectionManager.overrideMtu(deviceId, mtu);
    }

    @NonNull
    @Override
    public BleOperationHandle requestConnectionPriority(@NonNull BleDeviceId deviceId,
                                                        @NonNull BleConnectionPriority priority,
                                                        @NonNull BleValueObserver<Void> observer) {
        return core.connectionManager.requestConnectionPriority(deviceId, priority, observer);
    }

    @NonNull
    @Override
    public BleOperationHandle setPreferredPhy(@NonNull BleDeviceId deviceId,
                                              @NonNull BlePhy txPhy,
                                              @NonNull BlePhy rxPhy,
                                              @NonNull BlePhyOptions phyOptions,
                                              @NonNull BleValueObserver<BlePhyConfig> observer) {
        return core.connectionManager.setPreferredPhy(deviceId, txPhy, rxPhy, phyOptions, observer);
    }

    @NonNull
    @Override
    public BleOperationHandle readPhy(@NonNull BleDeviceId deviceId,
                                      @NonNull BleValueObserver<BlePhyConfig> observer) {
        return core.connectionManager.readPhy(deviceId, observer);
    }

    @NonNull
    @Override
    public BleOperationHandle readRemoteRssi(@NonNull BleDeviceId deviceId,
                                             @NonNull BleValueObserver<Integer> observer) {
        return core.connectionManager.readRemoteRssi(deviceId, observer);
    }

    // ── 5. 服务发现与缓存刷新 ──────────────────────────────────────

    @NonNull
    @Override
    public BleOperationHandle discoverServices(@NonNull BleDeviceId deviceId,
                                               boolean forceRefresh,
                                               @NonNull BleValueObserver<BleGattDatabaseSnapshot> observer) {
        return core.connectionManager.discoverServices(deviceId, forceRefresh, observer);
    }

    @NonNull
    @Override
    public Result<BleGattDatabaseSnapshot> getServices(@NonNull BleDeviceId deviceId) {
        return core.connectionManager.getServices(deviceId);
    }

    @NonNull
    @Override
    public Result<Void> refreshDeviceCache(@NonNull BleDeviceId deviceId) {
        return core.connectionManager.refreshDeviceCache(deviceId);
    }

    // ── 6. 串行 GATT 队列 ──────────────────────────────────────────

    @NonNull
    @Override
    public BleOperationHandle read(@NonNull BleDeviceId deviceId,
                                   @NonNull BleGattTarget target,
                                   @NonNull BleGattObserver observer) {
        return core.connectionManager.read(deviceId, target, BleOperationPriority.NORMAL, observer);
    }

    @NonNull
    @Override
    public BleOperationHandle read(@NonNull BleDeviceId deviceId,
                                   @NonNull BleGattTarget target,
                                   @NonNull BleOperationPriority priority,
                                   @NonNull BleGattObserver observer) {
        return core.connectionManager.read(deviceId, target, priority, observer);
    }

    @NonNull
    @Override
    public BleOperationHandle write(@NonNull BleDeviceId deviceId,
                                    @NonNull BleGattTarget target,
                                    @NonNull byte[] value,
                                    @NonNull BleWriteType writeType,
                                    @NonNull BleGattObserver observer) {
        return core.connectionManager.write(
                deviceId, target, value, writeType, BleOperationPriority.NORMAL, observer);
    }

    @NonNull
    @Override
    public BleOperationHandle write(@NonNull BleDeviceId deviceId,
                                    @NonNull BleGattTarget target,
                                    @NonNull byte[] value,
                                    @NonNull BleWriteType writeType,
                                    @NonNull BleOperationPriority priority,
                                    @NonNull BleGattObserver observer) {
        return core.connectionManager.write(
                deviceId, target, value, writeType, priority, observer);
    }

    @NonNull
    @Override
    public BleOperationHandle writeSplit(@NonNull BleDeviceId deviceId,
                                         @NonNull BleGattTarget target,
                                         @NonNull byte[] value,
                                         @NonNull BleWriteType writeType,
                                         @Nullable BleDataSplitter customSplitter,
                                         @NonNull BleTransferProgressObserver observer) {
        return core.connectionManager.writeSplit(
                deviceId, target, value, writeType, customSplitter, observer);
    }

    @NonNull
    @Override
    public Result<Void> beginReliableWrite(@NonNull BleDeviceId deviceId) {
        return core.connectionManager.beginReliableWrite(deviceId);
    }

    @NonNull
    @Override
    public BleOperationHandle executeReliableWrite(@NonNull BleDeviceId deviceId,
                                                   @NonNull BleGattObserver observer) {
        return core.connectionManager.executeReliableWrite(deviceId, observer);
    }

    @NonNull
    @Override
    public BleOperationHandle abortReliableWrite(@NonNull BleDeviceId deviceId,
                                                 @NonNull BleGattObserver observer) {
        return core.connectionManager.abortReliableWrite(deviceId, observer);
    }

    @NonNull
    @Override
    public Result<Integer> getPendingOperationCount(@NonNull BleDeviceId deviceId) {
        return core.connectionManager.getPendingOperationCount(deviceId);
    }

    @NonNull
    @Override
    public Result<Integer> clearPendingOperations(@NonNull BleDeviceId deviceId) {
        return core.connectionManager.clearPendingOperations(deviceId);
    }

    // ── 7. Notify / Indicate 与请求响应 ───────────────────────────

    @NonNull
    @Override
    public BleOperationHandle subscribe(@NonNull BleDeviceId deviceId,
                                        @NonNull BleGattTarget target,
                                        @NonNull BleNotificationSetupMode setupMode,
                                        @NonNull BleNotificationObserver observer) {
        return core.connectionManager.subscribe(deviceId, target, setupMode, observer);
    }

    @NonNull
    @Override
    public BleOperationHandle setNotify(@NonNull BleDeviceId deviceId,
                                        @NonNull BleGattTarget target,
                                        boolean enabled,
                                        @NonNull BleGattObserver observer) {
        return core.connectionManager.setNotify(deviceId, target, enabled, observer);
    }

    @NonNull
    @Override
    public BleOperationHandle sendAndWait(@NonNull BleDeviceId deviceId,
                                          @NonNull BleRequestResponseConfig config,
                                          @NonNull BleValueObserver<byte[]> observer) {
        return core.connectionManager.sendAndWait(deviceId, config, observer);
    }

    // ── 8. 配对管理 ────────────────────────────────────────────────

    @NonNull
    @Override
    public Result<List<BleDeviceSnapshot>> getBondedDevices() {
        return core.bondController.getBondedDevices();
    }

    @NonNull
    @Override
    public BleOperationHandle createBond(@NonNull BleDeviceId deviceId,
                                         @NonNull BleValueObserver<BleDeviceSnapshot> observer) {
        return core.bondController.createBond(deviceId, observer);
    }

    @NonNull
    @Override
    public Result<Void> cancelBondProcess(@NonNull BleDeviceId deviceId) {
        return core.bondController.cancelBondProcess(deviceId);
    }

    @NonNull
    @Override
    public Result<Void> removeBond(@NonNull BleDeviceId deviceId) {
        return core.bondController.removeBond(deviceId);
    }

    @NonNull
    @Override
    public BleOperationHandle observeBondState(@NonNull BleBondObserver observer) {
        return core.bondController.observeBondState(observer);
    }

    // ── 9. L2CAP CoC ───────────────────────────────────────────────

    @NonNull
    @Override
    public BleOperationHandle openL2capChannel(@NonNull BleDeviceId deviceId,
                                               int psm,
                                               boolean secure,
                                               @NonNull BleL2capChannelObserver observer) {
        return core.l2capManager.openChannel(deviceId, psm, secure, observer);
    }

    // ── 10. 外设广播与本地 GATT Server ────────────────────────────

    @NonNull
    @Override
    public BleOperationHandle startAdvertising(@NonNull BleAdvertiseConfig config,
                                               @NonNull BleAdvertiseData advertiseData,
                                               @Nullable BleAdvertiseData scanResponse,
                                               @NonNull BleAdvertiseObserver observer) {
        return core.peripheralManager.startAdvertising(config, advertiseData, scanResponse, observer);
    }

    @NonNull
    @Override
    public Result<Void> stopAdvertising() {
        return core.peripheralManager.stopAdvertising();
    }

    @NonNull
    @Override
    public BleOperationHandle startGattServer(@NonNull BleGattServerConfig config,
                                              @NonNull BleGattServerObserver observer) {
        return core.peripheralManager.startGattServer(config, observer);
    }

    @NonNull
    @Override
    public Result<Void> notifyServerCharacteristic(@NonNull BleDeviceId clientDeviceId,
                                                   @NonNull BleGattTarget target,
                                                   boolean confirm,
                                                   @NonNull byte[] value) {
        return core.peripheralManager.notifyServerCharacteristic(clientDeviceId, target, confirm, value);
    }

    @NonNull
    @Override
    public Result<Void> stopGattServer() {
        return core.peripheralManager.stopGattServer();
    }

    // ── 11. 诊断与客户端全量释放 ───────────────────────────────────

    @NonNull
    @Override
    public Result<BleDiagnosticReport> exportDiagnosticReport() {
        return core.exportDiagnosticReport();
    }

    @Override
    public void release() {
        androidGraph.releaseAll();
    }
}
