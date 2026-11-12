package com.yovo.lib_bluetooth.api;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.model.ble.BleAdapterState;
import com.yovo.lib_bluetooth.model.ble.BleAdapterStateObserver;
import com.yovo.lib_bluetooth.model.ble.BleAdvertiseConfig;
import com.yovo.lib_bluetooth.model.ble.BleAdvertiseData;
import com.yovo.lib_bluetooth.model.ble.BleAdvertiseObserver;
import com.yovo.lib_bluetooth.model.ble.BleBondObserver;
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
import com.yovo.lib_bluetooth.model.ble.BleL2capServerObserver;
import com.yovo.lib_bluetooth.model.ble.BleNotificationObserver;
import com.yovo.lib_bluetooth.model.ble.BleNotificationSetupMode;
import com.yovo.lib_bluetooth.model.ble.BleOperationHandle;
import com.yovo.lib_bluetooth.model.ble.BleOperationPriority;
import com.yovo.lib_bluetooth.model.ble.BlePhyConfig;
import com.yovo.lib_bluetooth.model.ble.BlePhyOptions;
import com.yovo.lib_bluetooth.model.ble.BlePhy;
import com.yovo.lib_bluetooth.model.ble.BleRequestResponseConfig;
import com.yovo.lib_bluetooth.model.ble.BleScanObserver;
import com.yovo.lib_bluetooth.model.ble.BleTransferProgressObserver;
import com.yovo.lib_bluetooth.model.ble.BleValueObserver;
import com.yovo.lib_bluetooth.model.ble.BleWriteType;
import com.yovo.lib_bluetooth.model.common.Result;

import java.util.List;

/**
 * BLE 对外 API。客户端由 {@link com.yovo.lib_bluetooth.YoBluetoothSDK#ble} 创建。
 *
 * <p>涵盖：硬件/适配器能力检查、防限流扫描与 AD 解析、多设备隔离连接与状态机、
 * 串行可取消 GATT 操作队列、服务发现与 ServiceChanged 缓存管理、Notify/Indicate 多播路由、
 * 长包分片与请求响应协同、ReliableWrite 事务、PHY/MTU/ConnectionPriority/RSSI、
 * Bond 配对控制、L2CAP CoC 高速通道（API 29+）、BLE 外设广播与 GATT Server，以及诊断导出。</p>
 */
public interface YoBleClient {

    // ── 1. 硬件能力、适配器与设备快照 ─────────────────────────────

    boolean isBleSupported();

    boolean isAdapterEnabled();

    @NonNull
    BleAdapterState getAdapterState();

    @NonNull
    BleOperationHandle observeAdapterState(@NonNull BleAdapterStateObserver observer);

    @NonNull
    Result<BleDeviceSnapshot> getDeviceSnapshot(@NonNull BleDeviceId deviceId);

    @NonNull
    Result<List<BleDeviceSnapshot>> getDeviceSnapshots();

    @NonNull
    Result<List<BleConnectionSnapshot>> getConnectedDevices();

    // ── 2. BLE 扫描：机制层（一个进程内扫描器，订阅 + 热替换谓词 + 显式重扫） ────

    /**
     * 订阅原始 BLE 广播。第一个订阅者启动扫描，最后一个取消后停止。
     *
     * <p>返回的订阅句柄支持 {@link BleScanSubscription#setFrameFilter} 热替换帧级过滤器：
     * 替换谓词只影响后续帧，不重启扫描。平台参数由库的默认计划决定，变更走
     * {@link #reconfigureScan(BleScanPlan)}。</p>
     *
     * <p>启动被限流守卫拒绝时，不会发起任何平台扫描，失败经 {@link BleScanObserver#onScanFailed}
     * 以 {@code SCAN_THROTTLED} 上报；句柄仍需取消。</p>
     */
    @NonNull
    BleScanSubscription subscribeScan(@NonNull BleScanObserver observer);

    /**
     * 显式重扫：平台扫描参数（模式/批处理/legacy/硬件过滤）变更的<b>唯一</b>合法入口。
     *
     * <p>与当前计划一致时幂等返回成功，不触发任何重扫；无活动扫描会话时返回失败；
     * 限流窗口内启动次数已达上限时返回 {@code SCAN_THROTTLED}，且不调用平台扫描。</p>
     */
    @NonNull
    Result<Void> reconfigureScan(@NonNull BleScanPlan plan);

    /** 平台扫描能力快照（硬件卸载过滤 / 批量 / 扩展广播）。 */
    @NonNull
    BleScanCapabilities scanCapabilities();

    /** 扫描健康度快照（会话启动时刻 / 窗口内启动次数 / 最近帧时刻 / 适配器状态）。 */
    @NonNull
    BleScanHealth scanHealth();

    @NonNull
    Result<Void> flushBatchScanResults();

    boolean isScanning();

    // ── 3. 多设备 GATT 连接、重试与生命周期 ─────────────────────────

    @NonNull
    BleOperationHandle connect(@NonNull BleDeviceId deviceId,
                               @NonNull BleConnectionConfig config,
                               @NonNull BleConnectionObserver observer);

    @NonNull
    Result<Void> reconnect(@NonNull BleDeviceId deviceId);

    @NonNull
    Result<Void> disconnect(@NonNull BleDeviceId deviceId);

    @NonNull
    Result<Void> disconnectAll();

    @NonNull
    Result<BleConnectionSnapshot> getConnectionSnapshot(@NonNull BleDeviceId deviceId);

    @NonNull
    BleOperationHandle observeConnectionState(@NonNull BleConnectionObserver observer);

    // ── 4. 链路调优（MTU / Priority / PHY / RSSI） ──────────────────

    @NonNull
    BleOperationHandle requestMtu(@NonNull BleDeviceId deviceId,
                                  int mtu,
                                  @NonNull BleValueObserver<Integer> observer);

    @NonNull
    Result<Integer> overrideMtu(@NonNull BleDeviceId deviceId, int mtu);

    @NonNull
    BleOperationHandle requestConnectionPriority(@NonNull BleDeviceId deviceId,
                                                 @NonNull BleConnectionPriority priority,
                                                 @NonNull BleValueObserver<Void> observer);

    @NonNull
    BleOperationHandle setPreferredPhy(@NonNull BleDeviceId deviceId,
                                       @NonNull BlePhy txPhy,
                                       @NonNull BlePhy rxPhy,
                                       @NonNull BlePhyOptions phyOptions,
                                       @NonNull BleValueObserver<BlePhyConfig> observer);

    @NonNull
    BleOperationHandle readPhy(@NonNull BleDeviceId deviceId,
                               @NonNull BleValueObserver<BlePhyConfig> observer);

    @NonNull
    BleOperationHandle readRemoteRssi(@NonNull BleDeviceId deviceId,
                                      @NonNull BleValueObserver<Integer> observer);

    // ── 5. 服务发现与缓存刷新 ───────────────────────────────────────

    @NonNull
    BleOperationHandle discoverServices(@NonNull BleDeviceId deviceId,
                                        boolean forceRefresh,
                                        @NonNull BleValueObserver<BleGattDatabaseSnapshot> observer);

    @NonNull
    Result<BleGattDatabaseSnapshot> getServices(@NonNull BleDeviceId deviceId);

    @NonNull
    BleOperationHandle refreshDeviceCache(@NonNull BleDeviceId deviceId,
                                         @NonNull BleValueObserver<BleGattDatabaseSnapshot> observer);

    // ── 6. 串行 GATT 队列：特征/描述符读写、事务与取消 ──────────────

    @NonNull
    BleOperationHandle read(@NonNull BleDeviceId deviceId,
                            @NonNull BleGattTarget target,
                            @NonNull BleGattObserver observer);

    @NonNull
    BleOperationHandle read(@NonNull BleDeviceId deviceId,
                            @NonNull BleGattTarget target,
                            @NonNull BleOperationPriority priority,
                            @NonNull BleGattObserver observer);

    @NonNull
    BleOperationHandle write(@NonNull BleDeviceId deviceId,
                             @NonNull BleGattTarget target,
                             @NonNull byte[] value,
                             @NonNull BleWriteType writeType,
                             @NonNull BleGattObserver observer);

    @NonNull
    BleOperationHandle write(@NonNull BleDeviceId deviceId,
                             @NonNull BleGattTarget target,
                             @NonNull byte[] value,
                             @NonNull BleWriteType writeType,
                             @NonNull BleOperationPriority priority,
                             @NonNull BleGattObserver observer);

    @NonNull
    BleOperationHandle writeSplit(@NonNull BleDeviceId deviceId,
                                  @NonNull BleGattTarget target,
                                  @NonNull byte[] value,
                                  @NonNull BleWriteType writeType,
                                  @Nullable BleDataSplitter customSplitter,
                                  @NonNull BleTransferProgressObserver observer);

    @NonNull
    Result<Void> beginReliableWrite(@NonNull BleDeviceId deviceId);

    @NonNull
    BleOperationHandle executeReliableWrite(@NonNull BleDeviceId deviceId,
                                            @NonNull BleGattObserver observer);

    @NonNull
    BleOperationHandle abortReliableWrite(@NonNull BleDeviceId deviceId,
                                          @NonNull BleGattObserver observer);

    @NonNull
    Result<Integer> getPendingOperationCount(@NonNull BleDeviceId deviceId);

    @NonNull
    Result<Integer> clearPendingOperations(@NonNull BleDeviceId deviceId);

    // ── 7. Notify / Indicate 多播订阅与请求-响应触发等待 ────────────

    @NonNull
    BleOperationHandle subscribe(@NonNull BleDeviceId deviceId,
                                 @NonNull BleGattTarget target,
                                 @NonNull BleNotificationSetupMode setupMode,
                                 @NonNull BleNotificationObserver observer);

    @NonNull
    BleOperationHandle setNotify(@NonNull BleDeviceId deviceId,
                                 @NonNull BleGattTarget target,
                                 boolean enabled,
                                 @NonNull BleGattObserver observer);

    @NonNull
    BleOperationHandle sendAndWait(@NonNull BleDeviceId deviceId,
                                   @NonNull BleRequestResponseConfig config,
                                   @NonNull BleValueObserver<byte[]> observer);

    // ── 8. 配对管理（BLE Bond） ─────────────────────────────────────

    @NonNull
    Result<List<BleDeviceSnapshot>> getBondedDevices();

    @NonNull
    BleOperationHandle createBond(@NonNull BleDeviceId deviceId,
                                  @NonNull BleValueObserver<BleDeviceSnapshot> observer);

    @NonNull
    Result<Void> cancelBondProcess(@NonNull BleDeviceId deviceId);

    @NonNull
    Result<Void> removeBond(@NonNull BleDeviceId deviceId);

    @NonNull
    BleOperationHandle observeBondState(@NonNull BleBondObserver observer);

    // ── 9. L2CAP CoC 面向连接高速流通道（API 29+） ───────────────────

    @NonNull
    BleOperationHandle openL2capChannel(@NonNull BleDeviceId deviceId,
                                        int psm,
                                        boolean secure,
                                        @NonNull BleL2capChannelObserver observer);

    @NonNull
    BleOperationHandle openL2capServer(boolean secure, @NonNull BleL2capServerObserver observer);

    // ── 10. 外设广播（Advertiser）与本地 GATT Server ───────────────

    @NonNull
    BleOperationHandle startAdvertising(@NonNull BleAdvertiseConfig config,
                                        @NonNull BleAdvertiseData advertiseData,
                                        @Nullable BleAdvertiseData scanResponse,
                                        @NonNull BleAdvertiseObserver observer);

    @NonNull
    Result<Void> stopAdvertising();

    @NonNull
    BleOperationHandle startGattServer(@NonNull BleGattServerConfig config,
                                       @NonNull BleGattServerObserver observer);

    @NonNull
    Result<List<BleDeviceId>> getConnectedClients();

    @NonNull
    Result<Void> notifyServerCharacteristic(@NonNull BleDeviceId clientDeviceId,
                                            @NonNull BleGattTarget target,
                                            boolean confirm,
                                            @NonNull byte[] value);

    @NonNull
    Result<Void> stopGattServer();

    // ── 11. 诊断与客户端全量释放 ───────────────────────────────────

    @NonNull
    Result<BleDiagnosticReport> exportDiagnosticReport();

    void release();
}
