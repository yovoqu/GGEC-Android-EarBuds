package com.yovo.lib_bluetooth.internal.ble.gatt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.yovo.lib_bluetooth.internal.ble.testing.FakeBleGattDevice;
import com.yovo.lib_bluetooth.internal.ble.testing.FakeYoBleHarness;
import com.yovo.lib_bluetooth.model.ble.BleConnectionConfig;
import com.yovo.lib_bluetooth.model.ble.BleConnectionObserver;
import com.yovo.lib_bluetooth.model.ble.BleConnectionState;
import com.yovo.lib_bluetooth.model.ble.BleDeviceId;
import com.yovo.lib_bluetooth.model.ble.BleErrorCode;
import com.yovo.lib_bluetooth.model.ble.BleFailure;
import com.yovo.lib_bluetooth.model.ble.BleGattCharacteristicInfo;
import com.yovo.lib_bluetooth.model.ble.BleGattObserver;
import com.yovo.lib_bluetooth.model.ble.BleGattServiceInfo;
import com.yovo.lib_bluetooth.model.ble.BleGattTarget;
import com.yovo.lib_bluetooth.model.ble.BleWriteType;
import com.yovo.lib_bluetooth.model.common.Result;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * BLE-021 Reliable Write 事务、回显值一致性校验、Abort 回滚与断连自动清理契约验证。
 */
public class BleReliableWriteTest {

    private static final UUID SVC_UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb");
    private static final UUID CHR_UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb");

    @Test
    public void unreadyOrUnbegunTransactions_returnExplicitErrors() {
        FakeYoBleHarness harness = new FakeYoBleHarness();
        BleDeviceId id = new BleDeviceId("AA:BB:CC:DD:EE:41");
        try {
            // 1. 未建立连接直接 beginReliableWrite
            Result<Void> preConn = harness.client.beginReliableWrite(id);
            assertTrue(preConn.isFailure());
            assertEquals(BleErrorCode.CONNECTION_NOT_READY.name(), preConn.getFailure().code());

            // 2. 已连接 READY 但未调用 beginReliableWrite 时直接 execute / abort
            connectDevice(harness, id, 0);
            List<BleFailure> failures = new ArrayList<>();
            BleGattObserver failObserver = new BleGattObserver() {
                @Override
                public void onFailed(BleFailure failure) {
                    failures.add(failure);
                }
            };
            harness.client.executeReliableWrite(id, failObserver);
            harness.client.abortReliableWrite(id, failObserver);
            harness.drain();

            assertEquals(2, failures.size());
            assertEquals(BleErrorCode.ILLEGAL_STATE, failures.get(0).errorCode());
            assertEquals(BleErrorCode.ILLEGAL_STATE, failures.get(1).errorCode());
        } finally {
            harness.release();
        }
    }

    @Test
    public void reliableWrite_echoVerified_commitsAndBlocksDuplicateBegin() {
        FakeYoBleHarness harness = new FakeYoBleHarness();
        BleDeviceId id = new BleDeviceId("AA:BB:CC:DD:EE:42");
        try {
            FakeBleGattDevice dev = connectDevice(harness, id, 0);
            BleGattTarget target = BleGattTarget.characteristic(SVC_UUID, CHR_UUID);

            Result<Void> begun = harness.client.beginReliableWrite(id);
            assertTrue(begun.isSuccess());

            // 事务未关闭时重复 begin 必须被拒绝
            Result<Void> dup = harness.client.beginReliableWrite(id);
            assertTrue(dup.isFailure());
            assertEquals(BleErrorCode.ILLEGAL_STATE.name(), dup.getFailure().code());

            AtomicInteger writeSuccessCount = new AtomicInteger();
            AtomicReference<Boolean> commitResult = new AtomicReference<>();
            BleGattObserver observer = new BleGattObserver() {
                @Override
                public void onWrite(BleDeviceId d, BleGattTarget t) {
                    writeSuccessCount.incrementAndGet();
                }

                @Override
                public void onReliableWriteCompleted(BleDeviceId d, boolean committed) {
                    commitResult.set(committed);
                }
            };

            harness.client.write(id, target, new byte[]{0x01, 0x02}, BleWriteType.WITH_RESPONSE, observer);
            harness.client.write(id, target, new byte[]{0x03, 0x04}, BleWriteType.WITH_RESPONSE, observer);
            harness.client.executeReliableWrite(id, observer);
            harness.drain();

            assertEquals(2, writeSuccessCount.get());
            assertEquals(1, dev.executeReliableCalls());
            assertEquals(0, dev.abortReliableCalls());
            assertEquals(Boolean.TRUE, commitResult.get());

            // 提交完成后允许开启下一轮事务
            assertTrue(harness.client.beginReliableWrite(id).isSuccess());
        } finally {
            harness.release();
        }
    }

    @Test
    public void reliableWrite_echoMismatch_reportsErrorAndAbortRollsBack() {
        FakeYoBleHarness harness = new FakeYoBleHarness();
        BleDeviceId id = new BleDeviceId("AA:BB:CC:DD:EE:43");
        try {
            FakeBleGattDevice dev = connectDevice(harness, id, 0);
            BleGattTarget target = BleGattTarget.characteristic(SVC_UUID, CHR_UUID);

            assertTrue(harness.client.beginReliableWrite(id).isSuccess());
            dev.setReliableCorruptEcho(true);

            List<BleFailure> failures = new ArrayList<>();
            AtomicReference<Boolean> abortedFlag = new AtomicReference<>();
            BleGattObserver observer = new BleGattObserver() {
                @Override
                public void onFailed(BleFailure failure) {
                    failures.add(failure);
                }

                @Override
                public void onReliableWriteCompleted(BleDeviceId d, boolean committed) {
                    abortedFlag.set(committed);
                }
            };

            harness.client.write(id, target, new byte[]{0x55, 0x66}, BleWriteType.WITH_RESPONSE, observer);
            harness.drain();

            assertEquals(1, failures.size());
            assertEquals(BleErrorCode.RELIABLE_WRITE_MISMATCH, failures.get(0).errorCode());

            harness.client.abortReliableWrite(id, observer);
            harness.drain();

            assertEquals(1, dev.abortReliableCalls());
            assertEquals(0, dev.executeReliableCalls());
            assertEquals(Boolean.FALSE, abortedFlag.get());
        } finally {
            harness.release();
        }
    }

    @Test
    public void disconnectMidReliableWrite_autoAbortsAndResetsTransactionState() {
        FakeYoBleHarness harness = new FakeYoBleHarness();
        BleDeviceId id = new BleDeviceId("AA:BB:CC:DD:EE:44");
        try {
            FakeBleGattDevice dev = connectDevice(harness, id, 0);
            BleGattTarget target = BleGattTarget.characteristic(SVC_UUID, CHR_UUID);

            assertTrue(harness.client.beginReliableWrite(id).isSuccess());
            harness.client.write(id, target, new byte[]{0x0A, 0x0B}, BleWriteType.WITH_RESPONSE,
                    new BleGattObserver() {});
            harness.drain();

            // 事务尚未 execute/abort 时远端意外断连，应自动触发底层 abortReliableWrite 并重置坐标状态
            dev.simulateRemoteDisconnect(0x13);
            harness.drain();

            assertTrue(dev.abortReliableCalls() >= 1);
            assertFalse(harness.client.getConnectionSnapshot(id).requireValue().isConnected());

            // 越过 closeDelay 重新建连，应能无残留地重新 beginReliableWrite
            harness.advanceMillis(250L);
            connectDevice(harness, id, 0);
            assertEquals(BleConnectionState.READY,
                    harness.client.getConnectionSnapshot(id).requireValue().state());
            assertTrue(harness.client.beginReliableWrite(id).isSuccess());
        } finally {
            harness.release();
        }
    }

    private static FakeBleGattDevice connectDevice(FakeYoBleHarness harness, BleDeviceId id, int maxRetry) {
        FakeBleGattDevice dev = harness.connectionPlatform.device(id);
        BleGattCharacteristicInfo ch = new BleGattCharacteristicInfo(
                CHR_UUID, 1,
                BleGattCharacteristicInfo.PROPERTY_READ | BleGattCharacteristicInfo.PROPERTY_WRITE,
                0, Collections.emptyList());
        BleGattServiceInfo svc = new BleGattServiceInfo(
                SVC_UUID, 1, true, Collections.singletonList(ch), Collections.emptyList());
        dev.setServices(Collections.singletonList(svc));
        harness.client.connect(id, BleConnectionConfig.builder()
                        .maxRetryCount(maxRetry)
                        .requestMtu(null)
                        .build(),
                new BleConnectionObserver() {});
        harness.drain();
        return dev;
    }
}
