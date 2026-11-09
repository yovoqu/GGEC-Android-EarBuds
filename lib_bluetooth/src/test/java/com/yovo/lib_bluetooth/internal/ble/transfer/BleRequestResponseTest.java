package com.yovo.lib_bluetooth.internal.ble.transfer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.yovo.lib_bluetooth.internal.ble.testing.FakeBleGattDevice;
import com.yovo.lib_bluetooth.internal.ble.testing.FakeYoBleHarness;
import com.yovo.lib_bluetooth.model.ble.BleConnectionConfig;
import com.yovo.lib_bluetooth.model.ble.BleConnectionObserver;
import com.yovo.lib_bluetooth.model.ble.BleDataMerger;
import com.yovo.lib_bluetooth.model.ble.BleDeviceId;
import com.yovo.lib_bluetooth.model.ble.BleErrorCode;
import com.yovo.lib_bluetooth.model.ble.BleFailure;
import com.yovo.lib_bluetooth.model.ble.BleGattCharacteristicInfo;
import com.yovo.lib_bluetooth.model.ble.BleGattDatabaseSnapshot;
import com.yovo.lib_bluetooth.model.ble.BleGattDescriptorInfo;
import com.yovo.lib_bluetooth.model.ble.BleGattServiceInfo;
import com.yovo.lib_bluetooth.model.ble.BleGattTarget;
import com.yovo.lib_bluetooth.model.ble.BleMergeResult;
import com.yovo.lib_bluetooth.model.ble.BleNotificationSetupMode;
import com.yovo.lib_bluetooth.model.ble.BleOperationHandle;
import com.yovo.lib_bluetooth.model.ble.BleRequestResponseConfig;
import com.yovo.lib_bluetooth.model.ble.BleValueObserver;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * BLE-020 原子化 sendAndWait 请求-响应（零竞态挂钩、DataMerger 流式合包、超时卸载、多路复用 Predicate 配对）测试。
 */
public class BleRequestResponseTest {

    private static final UUID SVC_UUID = UUID.fromString("0000ff10-0000-1000-8000-00805f9b34fb");
    private static final UUID REQ_UUID = UUID.fromString("0000ff11-0000-1000-8000-00805f9b34fb");
    private static final UUID RESP_UUID = UUID.fromString("0000ff12-0000-1000-8000-00805f9b34fb");
    private static final BleGattTarget REQ_TARGET = BleGattTarget.characteristic(SVC_UUID, REQ_UUID);
    private static final BleGattTarget RESP_TARGET = BleGattTarget.characteristic(SVC_UUID, RESP_UUID);

    @Test
    public void interceptorRegisteredBeforeWrite_capturesImmediateResponseZeroRace() {
        FakeYoBleHarness harness = new FakeYoBleHarness();
        BleDeviceId id = new BleDeviceId("AA:BB:CC:DD:EE:34");
        try {
            FakeBleGattDevice dev = setupDevice(harness, id);
            // 即使写完成回调延迟不归位，写入触发瞬间同步产生的极速回包也必须被准确捕获
            dev.setDeferOperationCallbacks(false);
            dev.setWriteHook((target, val, type, d) -> {
                if (target.characteristicUuid().equals(REQ_UUID)) {
                    d.simulateNotification(RESP_TARGET, new byte[]{(byte) 0xAA, 0x55});
                }
            });

            AtomicReference<byte[]> response = new AtomicReference<>();
            harness.client.sendAndWait(id, BleRequestResponseConfig.builder(
                    REQ_TARGET, new byte[]{0x01}, RESP_TARGET)
                    .setupMode(BleNotificationSetupMode.DEFAULT)
                    .build(), new BleValueObserver<byte[]>() {
                @Override
                public void onSuccess(byte[] value) {
                    response.set(value);
                }
            });
            harness.drain();

            assertNotNull(response.get());
            assertArrayEquals(new byte[]{(byte) 0xAA, 0x55}, response.get());
        } finally {
            harness.release();
        }
    }

    @Test
    public void dataMerger_combinesMultiFrameResponse_andRejectsInvalidPacket() {
        FakeYoBleHarness harness = new FakeYoBleHarness();
        BleDeviceId id = new BleDeviceId("AA:BB:CC:DD:EE:35");
        try {
            FakeBleGattDevice dev = setupDevice(harness, id);

            // 首字节 bit7=1 代表还有后续分片，bit7=0 代表终帧
            BleDataMerger merger = (acc, chunk, idx) -> {
                if (chunk.length == 0 || chunk[0] == (byte) 0xFF) {
                    return new BleDataMerger.Output(BleMergeResult.INVALID, acc);
                }
                boolean more = (chunk[0] & 0x80) != 0;
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                out.write(acc, 0, acc.length);
                out.write(chunk, 1, chunk.length - 1);
                return new BleDataMerger.Output(
                        more ? BleMergeResult.NEED_MORE : BleMergeResult.COMPLETE,
                        out.toByteArray());
            };

            AtomicReference<byte[]> merged = new AtomicReference<>();
            harness.client.sendAndWait(id, BleRequestResponseConfig.builder(
                    REQ_TARGET, new byte[]{0x10}, RESP_TARGET)
                    .dataMerger(merger)
                    .build(), new BleValueObserver<byte[]>() {
                @Override
                public void onSuccess(byte[] value) {
                    merged.set(value);
                }
            });
            harness.drain();

            dev.simulateNotification(RESP_TARGET, new byte[]{(byte) 0x80, 0x01, 0x02});
            harness.drain();
            assertEquals(null, merged.get()); // NEED_MORE 尚未回调

            dev.simulateNotification(RESP_TARGET, new byte[]{0x00, 0x03, 0x04});
            harness.drain();
            assertArrayEquals(new byte[]{0x01, 0x02, 0x03, 0x04}, merged.get());

            // 测试 INVALID 帧触发 PAYLOAD_MERGE_FAILED
            AtomicReference<BleFailure> mergeErr = new AtomicReference<>();
            harness.client.sendAndWait(id, BleRequestResponseConfig.builder(
                    REQ_TARGET, new byte[]{0x11}, RESP_TARGET)
                    .dataMerger(merger)
                    .build(), new BleValueObserver<byte[]>() {
                @Override
                public void onSuccess(byte[] value) {
                }

                @Override
                public void onFailed(BleFailure failure) {
                    mergeErr.set(failure);
                }
            });
            harness.drain();
            dev.simulateNotification(RESP_TARGET, new byte[]{(byte) 0xFF});
            harness.drain();
            assertNotNull(mergeErr.get());
            assertEquals(BleErrorCode.PAYLOAD_MERGE_FAILED, mergeErr.get().errorCode());
        } finally {
            harness.release();
        }
    }

    @Test
    public void timeoutUnregistersInterceptor_andLateNotificationsIgnored() {
        FakeYoBleHarness harness = new FakeYoBleHarness();
        BleDeviceId id = new BleDeviceId("AA:BB:CC:DD:EE:36");
        try {
            FakeBleGattDevice dev = setupDevice(harness, id);
            AtomicInteger successCount = new AtomicInteger();
            AtomicReference<BleFailure> failure = new AtomicReference<>();

            harness.client.sendAndWait(id, BleRequestResponseConfig.builder(
                    REQ_TARGET, new byte[]{0x20}, RESP_TARGET)
                    .timeoutMillis(500L)
                    .build(), new BleValueObserver<byte[]>() {
                @Override
                public void onSuccess(byte[] value) {
                    successCount.incrementAndGet();
                }

                @Override
                public void onFailed(BleFailure f) {
                    failure.set(f);
                }
            });
            harness.drain();

            harness.advanceMillis(500L);
            assertNotNull(failure.get());
            assertEquals(BleErrorCode.GATT_OPERATION_TIMEOUT, failure.get().errorCode());

            // 超时后姗姗来迟的回包被安全忽略
            dev.simulateNotification(RESP_TARGET, new byte[]{0x20, 0x01});
            harness.drain();
            assertEquals(0, successCount.get());
        } finally {
            harness.release();
        }
    }

    @Test
    public void concurrentRequests_disambiguateByPredicateWithoutCrosstalk() {
        FakeYoBleHarness harness = new FakeYoBleHarness();
        BleDeviceId id = new BleDeviceId("AA:BB:CC:DD:EE:37");
        try {
            FakeBleGattDevice dev = setupDevice(harness, id);
            AtomicReference<byte[]> respA = new AtomicReference<>();
            AtomicReference<byte[]> respB = new AtomicReference<>();

            harness.client.sendAndWait(id, BleRequestResponseConfig.builder(
                    REQ_TARGET, new byte[]{(byte) 0xA1}, RESP_TARGET)
                    .responseMatcher(bytes -> bytes.length > 0 && bytes[0] == (byte) 0xA1)
                    .build(), new BleValueObserver<byte[]>() {
                @Override
                public void onSuccess(byte[] value) {
                    respA.set(value);
                }
            });
            harness.client.sendAndWait(id, BleRequestResponseConfig.builder(
                    REQ_TARGET, new byte[]{(byte) 0xB2}, RESP_TARGET)
                    .responseMatcher(bytes -> bytes.length > 0 && bytes[0] == (byte) 0xB2)
                    .build(), new BleValueObserver<byte[]>() {
                @Override
                public void onSuccess(byte[] value) {
                    respB.set(value);
                }
            });
            harness.drain();

            // 无关噪声帧 + B 先回包 + A 后回包
            dev.simulateNotification(RESP_TARGET, new byte[]{0x7F, 0x00});
            dev.simulateNotification(RESP_TARGET, new byte[]{(byte) 0xB2, 0x02});
            dev.simulateNotification(RESP_TARGET, new byte[]{(byte) 0xA1, 0x01});
            harness.drain();

            assertArrayEquals(new byte[]{(byte) 0xA1, 0x01}, respA.get());
            assertArrayEquals(new byte[]{(byte) 0xB2, 0x02}, respB.get());
        } finally {
            harness.release();
        }
    }

    @Test
    public void cancelPendingRequestResponse_unregistersInterceptorAndEmitsCancelledOnce() {
        FakeYoBleHarness harness = new FakeYoBleHarness();
        BleDeviceId id = new BleDeviceId("AA:BB:CC:DD:EE:38");
        try {
            FakeBleGattDevice dev = setupDevice(harness, id);
            AtomicInteger successCount = new AtomicInteger();
            AtomicInteger failCount = new AtomicInteger();
            AtomicReference<BleFailure> failRef = new AtomicReference<>();

            BleOperationHandle handle = harness.client.sendAndWait(id, BleRequestResponseConfig.builder(
                    REQ_TARGET, new byte[]{0x33}, RESP_TARGET)
                    .timeoutMillis(1_000L)
                    .build(), new BleValueObserver<byte[]>() {
                @Override
                public void onSuccess(byte[] value) {
                    successCount.incrementAndGet();
                }

                @Override
                public void onFailed(BleFailure failure) {
                    failCount.incrementAndGet();
                    failRef.set(failure);
                }
            });
            harness.drain();

            handle.cancel();
            harness.drain();

            assertNotNull(failRef.get());
            assertEquals(BleErrorCode.OPERATION_CANCELLED, failRef.get().errorCode());
            assertEquals(1, failCount.get());

            // 取消后收到远端包或超时推进，均不再回调成功或二次失败
            dev.simulateNotification(RESP_TARGET, new byte[]{0x33, 0x01});
            harness.advanceMillis(2_000L);
            assertEquals(0, successCount.get());
            assertEquals(1, failCount.get());
        } finally {
            harness.release();
        }
    }

    private static FakeBleGattDevice setupDevice(FakeYoBleHarness harness, BleDeviceId id) {
        FakeBleGattDevice dev = harness.connectionPlatform.device(id);
        BleGattDescriptorInfo cccd = new BleGattDescriptorInfo(
                BleGattDatabaseSnapshot.CCCD_UUID,
                BleGattDescriptorInfo.PERMISSION_READ | BleGattDescriptorInfo.PERMISSION_WRITE);
        BleGattCharacteristicInfo req = new BleGattCharacteristicInfo(
                REQ_UUID, 1, BleGattCharacteristicInfo.PROPERTY_WRITE, 0, Collections.emptyList());
        BleGattCharacteristicInfo resp = new BleGattCharacteristicInfo(
                RESP_UUID, 2, BleGattCharacteristicInfo.PROPERTY_NOTIFY, 0,
                Collections.singletonList(cccd));
        BleGattServiceInfo svc = new BleGattServiceInfo(
                SVC_UUID, 1, true, Arrays.asList(req, resp), Collections.emptyList());
        dev.setServices(Collections.singletonList(svc));
        harness.client.connect(id, BleConnectionConfig.builder().requestMtu(null).build(),
                new BleConnectionObserver() {});
        harness.drain();
        return dev;
    }
}
