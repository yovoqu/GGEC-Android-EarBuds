package com.yovo.lib_bluetooth.internal.ble.gatt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.yovo.lib_bluetooth.internal.ble.testing.FakeBleGattDevice;
import com.yovo.lib_bluetooth.internal.ble.testing.FakeYoBleHarness;
import com.yovo.lib_bluetooth.model.ble.BleConnectionConfig;
import com.yovo.lib_bluetooth.model.ble.BleConnectionObserver;
import com.yovo.lib_bluetooth.model.ble.BleDeviceId;
import com.yovo.lib_bluetooth.model.ble.BleErrorCode;
import com.yovo.lib_bluetooth.model.ble.BleFailure;
import com.yovo.lib_bluetooth.model.ble.BleGattCharacteristicInfo;
import com.yovo.lib_bluetooth.model.ble.BleGattObserver;
import com.yovo.lib_bluetooth.model.ble.BleGattServiceInfo;
import com.yovo.lib_bluetooth.model.ble.BleGattTarget;
import com.yovo.lib_bluetooth.model.ble.BleOperationHandle;
import com.yovo.lib_bluetooth.model.ble.BleOperationPriority;
import com.yovo.lib_bluetooth.model.ble.BleWriteType;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * BLE-014 每连接串行 GATT 操作队列（inFlight<=1 / HIGH 优先级插队 / 同级 FIFO / 取消 / 超时防死锁 / clearPending）测试。
 */
public class BleGattOperationQueueTest {

    private static final UUID SVC_UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb");
    private static final UUID CHR_UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb");
    private static final BleGattTarget TARGET = BleGattTarget.characteristic(SVC_UUID, CHR_UUID);

    @Test
    public void fiftyMixedConcurrentOps_serializeStrictlyWithZeroDrop_andHighPriorityPreempts() {
        FakeYoBleHarness harness = new FakeYoBleHarness();
        BleDeviceId id = new BleDeviceId("AA:BB:CC:DD:EE:14");
        try {
            FakeBleGattDevice dev = setupReadyDevice(harness, id);
            dev.setDeferOperationCallbacks(true);

            AtomicInteger readDone = new AtomicInteger();
            AtomicInteger writeDone = new AtomicInteger();

            for (int i = 0; i < 50; i++) {
                if (i % 2 == 0) {
                    byte b = (byte) i;
                    harness.client.write(id, TARGET, new byte[]{b}, BleWriteType.WITH_RESPONSE,
                            BleOperationPriority.NORMAL, new BleGattObserver() {
                                @Override
                                public void onWrite(BleDeviceId d, BleGattTarget t) {
                                    writeDone.incrementAndGet();
                                }
                            });
                } else {
                    harness.client.read(id, TARGET, BleOperationPriority.NORMAL, new BleGattObserver() {
                        @Override
                        public void onRead(BleDeviceId d, BleGattTarget t, byte[] v) {
                            readDone.incrementAndGet();
                        }
                    });
                }
            }
            harness.drain();
            // 第 1 个在途（write #0），其余 49 个全部排队（inFlight<=1）
            assertEquals(1, dev.charWrites().size());
            assertEquals(49, (int) harness.client.getPendingOperationCount(id).getValue());

            dev.setDeferOperationCallbacks(false);
            // 手动完成第 1 个 inFlight write，其余 49 个自动串行出队全部完成
            dev.simulateWriteComplete(TARGET, new byte[]{0}, 0);
            harness.drain();

            assertEquals(25, writeDone.get());
            assertEquals(25, readDone.get());
            assertEquals(0, (int) harness.client.getPendingOperationCount(id).getValue());

            // 优先级插队测试：inFlight 为 normal-1 时依次入队 normal-2, high-1, normal-3 -> 完成 order 须为 normal-1, high-1, normal-2, normal-3
            dev.setDeferOperationCallbacks(true);
            List<String> order = new ArrayList<>();
            harness.client.write(id, TARGET, new byte[]{1}, BleWriteType.WITH_RESPONSE, BleOperationPriority.NORMAL,
                    recordingObserver("N1", order));
            harness.client.write(id, TARGET, new byte[]{2}, BleWriteType.WITH_RESPONSE, BleOperationPriority.NORMAL,
                    recordingObserver("N2", order));
            harness.client.write(id, TARGET, new byte[]{3}, BleWriteType.WITH_RESPONSE, BleOperationPriority.HIGH,
                    recordingObserver("H1", order));
            harness.client.write(id, TARGET, new byte[]{4}, BleWriteType.WITH_RESPONSE, BleOperationPriority.NORMAL,
                    recordingObserver("N3", order));
            harness.drain();

            dev.setDeferOperationCallbacks(false);
            dev.simulateWriteComplete(TARGET, new byte[]{1}, 0);
            harness.drain();

            assertEquals(java.util.Arrays.asList("N1", "H1", "N2", "N3"), order);
        } finally {
            harness.release();
        }
    }

    @Test
    public void cancelPendingDoesNotBlockInFlight_andTimeoutUnlocksNextOp_andClearPendingWorks() {
        FakeYoBleHarness harness = new FakeYoBleHarness();
        BleDeviceId id = new BleDeviceId("AA:BB:CC:DD:EE:15");
        try {
            FakeBleGattDevice dev = setupReadyDevice(harness, id);
            dev.setDeferOperationCallbacks(true);

            List<BleFailure> errs = new ArrayList<>();
            List<String> ok = new ArrayList<>();

            // Op1 将超时（500ms）；Op2 将被取消（仍在 pending）；Op3 正常等待执行
            harness.client.read(id, TARGET, BleOperationPriority.NORMAL, new BleGattObserver() {
                @Override
                public void onFailed(BleFailure failure) {
                    errs.add(failure);
                }
            });
            BleOperationHandle pendingHandle = harness.client.read(id, TARGET, BleOperationPriority.NORMAL,
                    new BleGattObserver() {
                        @Override
                        public void onFailed(BleFailure failure) {
                            errs.add(failure);
                        }
                    });
            harness.client.read(id, TARGET, BleOperationPriority.NORMAL, new BleGattObserver() {
                @Override
                public void onRead(BleDeviceId d, BleGattTarget t, byte[] v) {
                    ok.add("op3");
                }
            });
            harness.drain();
            assertEquals(2, (int) harness.client.getPendingOperationCount(id).getValue());

            // 取消 Op2，不影响 Op1 在途，Op2 立刻报 CANCELLED
            pendingHandle.cancel();
            harness.drain();
            assertEquals(1, errs.size());
            assertEquals(BleErrorCode.OPERATION_CANCELLED, errs.get(0).errorCode());
            assertEquals(1, (int) harness.client.getPendingOperationCount(id).getValue());

            // 推进 500ms 触发 Op1 超时 -> Op1 释放 slot 且 Op3 立即出队
            dev.setDeferOperationCallbacks(false);
            harness.advanceMillis(500L);
            assertEquals(2, errs.size());
            assertEquals(BleErrorCode.GATT_OPERATION_TIMEOUT, errs.get(1).errorCode());
            assertEquals(Collections.singletonList("op3"), ok);

            // clearPendingOperations 排空并返回数量
            dev.setDeferOperationCallbacks(true);
            List<BleFailure> clearedErrs = new ArrayList<>();
            BleGattObserver errCollector = new BleGattObserver() {
                @Override
                public void onFailed(BleFailure failure) {
                    clearedErrs.add(failure);
                }
            };
            harness.client.read(id, TARGET, errCollector);
            harness.client.read(id, TARGET, errCollector);
            harness.client.read(id, TARGET, errCollector);
            harness.drain();

            int cleared = harness.client.clearPendingOperations(id).getValue();
            harness.drain();
            assertEquals(3, cleared);
            assertEquals(3, clearedErrs.size());
            assertEquals(0, (int) harness.client.getPendingOperationCount(id).getValue());

            // clearPendingOperations 经 state 线程收口后，队列可立刻继续调度新请求
            dev.setDeferOperationCallbacks(false);
            AtomicInteger postClearReads = new AtomicInteger();
            harness.client.read(id, TARGET, BleOperationPriority.NORMAL, new BleGattObserver() {
                @Override
                public void onRead(BleDeviceId d, BleGattTarget t, byte[] value) {
                    postClearReads.incrementAndGet();
                }
            });
            harness.drain();
            assertEquals(1, postClearReads.get());
        } finally {
            harness.release();
        }
    }

    private static FakeBleGattDevice setupReadyDevice(FakeYoBleHarness harness, BleDeviceId id) {
        FakeBleGattDevice dev = harness.connectionPlatform.device(id);
        BleGattCharacteristicInfo ch = new BleGattCharacteristicInfo(
                CHR_UUID, 1,
                BleGattCharacteristicInfo.PROPERTY_READ | BleGattCharacteristicInfo.PROPERTY_WRITE,
                0, Collections.emptyList());
        BleGattServiceInfo svc = new BleGattServiceInfo(
                SVC_UUID, 1, true, Collections.singletonList(ch), Collections.emptyList());
        dev.setServices(Collections.singletonList(svc)).putValue(TARGET, new byte[]{0x42});

        harness.client.connect(id, BleConnectionConfig.builder()
                .operationTimeoutMillis(500L)
                .requestMtu(null)
                .build(), new BleConnectionObserver() {});
        harness.drain();
        return dev;
    }

    private static BleGattObserver recordingObserver(String label, List<String> order) {
        return new BleGattObserver() {
            @Override
            public void onWrite(BleDeviceId d, BleGattTarget t) {
                order.add(label);
            }
        };
    }
}
