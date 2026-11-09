package com.yovo.lib_bluetooth.internal.ble.transfer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.yovo.lib_bluetooth.internal.ble.error.YoBleGattStatus;
import com.yovo.lib_bluetooth.internal.ble.testing.FakeBleGattDevice;
import com.yovo.lib_bluetooth.internal.ble.testing.FakeYoBleHarness;
import com.yovo.lib_bluetooth.model.ble.BleConnectionConfig;
import com.yovo.lib_bluetooth.model.ble.BleConnectionObserver;
import com.yovo.lib_bluetooth.model.ble.BleDataSplitter;
import com.yovo.lib_bluetooth.model.ble.BleDeviceId;
import com.yovo.lib_bluetooth.model.ble.BleErrorCode;
import com.yovo.lib_bluetooth.model.ble.BleFailure;
import com.yovo.lib_bluetooth.model.ble.BleGattCharacteristicInfo;
import com.yovo.lib_bluetooth.model.ble.BleGattServiceInfo;
import com.yovo.lib_bluetooth.model.ble.BleGattTarget;
import com.yovo.lib_bluetooth.model.ble.BleOperationHandle;
import com.yovo.lib_bluetooth.model.ble.BleTransferProgressObserver;
import com.yovo.lib_bluetooth.model.ble.BleValueObserver;
import com.yovo.lib_bluetooth.model.ble.BleWriteType;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * BLE-019 MTU 协商/512 上限计算、64KB OTA 长写进度、中途取消、GATT_BUSY 重试与自定义 DataSplitter 测试。
 */
public class BleLongWriteAndSplitterTest {

    private static final UUID SVC_UUID = UUID.fromString("0000ff00-0000-1000-8000-00805f9b34fb");
    private static final UUID CHR_UUID = UUID.fromString("0000ff01-0000-1000-8000-00805f9b34fb");
    private static final BleGattTarget TARGET = BleGattTarget.characteristic(SVC_UUID, CHR_UUID);

    @Test
    public void mtuPayloadCalculator_enforcesBoundsSignedOverheadAnd512Cap() {
        assertEquals(20, YoBleMtuPayloadCalculator.effectiveMaxChunkSize(23, null, BleWriteType.WITH_RESPONSE));
        assertEquals(182, YoBleMtuPayloadCalculator.effectiveMaxChunkSize(185, null, BleWriteType.WITHOUT_RESPONSE));
        // Bluetooth Core Spec 严格限制属性写上限 512 字节（非 514）
        assertEquals(512, YoBleMtuPayloadCalculator.effectiveMaxChunkSize(517, null, BleWriteType.WITH_RESPONSE));
        // SIGNED write 需要额外 12 字节认证签名（共 15 字节开销）
        assertEquals(232, YoBleMtuPayloadCalculator.effectiveMaxChunkSize(247, null, BleWriteType.SIGNED));
        assertEquals(502, YoBleMtuPayloadCalculator.effectiveMaxChunkSize(517, null, BleWriteType.SIGNED));
        // overrideMtu 覆盖底层协商值
        assertEquals(97, YoBleMtuPayloadCalculator.effectiveMaxChunkSize(517, 100, BleWriteType.WITH_RESPONSE));
    }

    @Test
    public void mtuNegotiationFailure_keepsConnectionAliveAndOverrideMtuApplies() {
        FakeYoBleHarness harness = new FakeYoBleHarness();
        BleDeviceId id = new BleDeviceId("AA:BB:CC:DD:EE:30");
        try {
            FakeBleGattDevice dev = setupDevice(harness, id, 247);
            dev.setMtuFailure(false, 6 /* REQUEST_NOT_SUPPORTED */);

            AtomicReference<BleFailure> mtuErr = new AtomicReference<>();
            harness.client.requestMtu(id, 517, new BleValueObserver<Integer>() {
                @Override
                public void onSuccess(Integer value) {
                }

                @Override
                public void onFailed(BleFailure failure) {
                    mtuErr.set(failure);
                }
            });
            harness.drain();
            assertNotNull(mtuErr.get());
            assertEquals(23, harness.client.getConnectionSnapshot(id).getValue().mtu());

            assertEquals(128, (int) harness.client.overrideMtu(id, 128).getValue());
            assertEquals(128, harness.client.getConnectionSnapshot(id).getValue().mtu());
        } finally {
            harness.release();
        }
    }

    @Test
    public void ota64KbSplitWrite_reportsMonotonicProgressAndReassemblesExactPayload() {
        FakeYoBleHarness harness = new FakeYoBleHarness();
        BleDeviceId id = new BleDeviceId("AA:BB:CC:DD:EE:31");
        try {
            FakeBleGattDevice dev = setupDevice(harness, id, 517);
            AtomicInteger grantedMtu = new AtomicInteger();
            harness.client.requestMtu(id, 517, new BleValueObserver<Integer>() {
                @Override
                public void onSuccess(Integer value) {
                    grantedMtu.set(value);
                }
            });
            harness.drain();
            assertEquals(517, grantedMtu.get());

            byte[] payload = new byte[64 * 1024];
            for (int i = 0; i < payload.length; i++) {
                payload[i] = (byte) ((i * 31 + 7) & 0xFF);
            }

            List<Integer> chunkIndices = new ArrayList<>();
            AtomicLong lastBytesSent = new AtomicLong();
            AtomicLong completedBytes = new AtomicLong(-1L);

            harness.client.writeSplit(id, TARGET, payload, BleWriteType.WITHOUT_RESPONSE, null,
                    new BleTransferProgressObserver() {
                        @Override
                        public void onChunkSent(BleDeviceId d, BleGattTarget t,
                                                int chunkIndex, int totalChunks,
                                                long bytesSent, long totalBytes) {
                            assertEquals(128, totalChunks);
                            assertEquals(payload.length, totalBytes);
                            chunkIndices.add(chunkIndex);
                            lastBytesSent.set(bytesSent);
                        }

                        @Override
                        public void onCompleted(BleDeviceId d, BleGattTarget t, long totalBytes) {
                            completedBytes.set(totalBytes);
                        }
                    });
            harness.drain();

            assertEquals(128, chunkIndices.size());
            assertEquals(0, (int) chunkIndices.get(0));
            assertEquals(127, (int) chunkIndices.get(127));
            assertEquals(payload.length, lastBytesSent.get());
            assertEquals(payload.length, completedBytes.get());

            ByteArrayOutputStream reassembled = new ByteArrayOutputStream();
            for (FakeBleGattDevice.RecordedWrite rw : dev.charWrites()) {
                assertTrue(rw.value.length <= 512);
                reassembled.write(rw.value, 0, rw.value.length);
            }
            assertArrayEquals(payload, reassembled.toByteArray());
        } finally {
            harness.release();
        }
    }

    @Test
    public void cancelAtChunk5_stopsSubsequentChunkTransmissions() {
        FakeYoBleHarness harness = new FakeYoBleHarness();
        BleDeviceId id = new BleDeviceId("AA:BB:CC:DD:EE:32");
        try {
            FakeBleGattDevice dev = setupDevice(harness, id, 23);
            dev.setDeferOperationCallbacks(true);

            // MTU 23 -> maxChunk=20 -> 200 字节切 10 片
            byte[] payload = new byte[200];
            AtomicInteger chunksSent = new AtomicInteger();
            AtomicInteger failureCount = new AtomicInteger();
            AtomicReference<BleFailure> failure = new AtomicReference<>();

            BleOperationHandle handle = harness.client.writeSplit(
                    id, TARGET, payload, BleWriteType.WITH_RESPONSE, null,
                    new BleTransferProgressObserver() {
                        @Override
                        public void onChunkSent(BleDeviceId d, BleGattTarget t, int idx,
                                                int total, long sent, long totalBytes) {
                            chunksSent.incrementAndGet();
                        }

                        @Override
                        public void onFailed(BleFailure f) {
                            failureCount.incrementAndGet();
                            failure.set(f);
                        }
                    });
            harness.drain();
            assertEquals(1, dev.charWrites().size()); // chunk 0 在途

            for (int i = 0; i < 4; i++) {
                dev.simulateWriteComplete(TARGET, new byte[20], 0);
                harness.drain();
            }
            // 已发送完 0..4，第 5 片刚发出处于在途
            assertEquals(5, dev.charWrites().size());
            assertEquals(4, chunksSent.get());

            handle.cancel();
            harness.drain();

            assertNotNull(failure.get());
            assertEquals(BleErrorCode.OPERATION_CANCELLED, failure.get().errorCode());
            assertEquals(1, failureCount.get());
            // 后续第 6..10 片绝不会发出，且迟到完成回调也不会触发重复失败或继续分片
            dev.simulateWriteComplete(TARGET, new byte[20], 0);
            harness.drain();
            assertEquals(5, dev.charWrites().size());
            assertEquals(1, failureCount.get());
        } finally {
            harness.release();
        }
    }

    @Test
    public void transientGattBusyRetry_andCustomProtocolHeaderSplitter() {
        FakeYoBleHarness harness = new FakeYoBleHarness();
        BleDeviceId id = new BleDeviceId("AA:BB:CC:DD:EE:33");
        try {
            FakeBleGattDevice dev = setupDevice(harness, id, 23);
            // 第一片前两次返回 GATT_BUSY (0x84)，第 3 次成功
            dev.enqueueWriteStatus(YoBleGattStatus.GATT_BUSY);
            dev.enqueueWriteStatus(YoBleGattStatus.GATT_BUSY);

            BleDataSplitter headerSplitter = (data, maxChunk) -> {
                List<byte[]> out = new ArrayList<>();
                int bodyMax = Math.max(1, maxChunk - 2);
                int seq = 0;
                for (int off = 0; off < data.length; off += bodyMax) {
                    int len = Math.min(bodyMax, data.length - off);
                    byte[] frame = new byte[len + 2];
                    frame[0] = (byte) 0xFE;
                    frame[1] = (byte) (seq++);
                    System.arraycopy(data, off, frame, 2, len);
                    out.add(frame);
                }
                return out;
            };

            byte[] input = new byte[30]; // MTU 23 -> bodyMax 18 -> 2 frames
            AtomicLong completedBytes = new AtomicLong(-1L);
            harness.client.writeSplit(id, TARGET, input, BleWriteType.WITH_RESPONSE,
                    headerSplitter, new BleTransferProgressObserver() {
                        @Override
                        public void onCompleted(BleDeviceId d, BleGattTarget t, long totalBytes) {
                            completedBytes.set(totalBytes);
                        }
                    });
            harness.drain();

            assertEquals(30L, completedBytes.get());
            // 第 0 片尝试 3 次 + 第 1 片 1 次 = 4 次底层写
            assertEquals(4, dev.charWrites().size());
            assertEquals((byte) 0xFE, dev.charWrites().get(0).value[0]);
            assertEquals((byte) 0x00, dev.charWrites().get(0).value[1]);
            assertEquals((byte) 0xFE, dev.charWrites().get(3).value[0]);
            assertEquals((byte) 0x01, dev.charWrites().get(3).value[1]);
        } finally {
            harness.release();
        }
    }

    private static FakeBleGattDevice setupDevice(FakeYoBleHarness harness, BleDeviceId id, int maxMtu) {
        FakeBleGattDevice dev = harness.connectionPlatform.device(id).setMaxSupportedMtu(maxMtu);
        BleGattCharacteristicInfo ch = new BleGattCharacteristicInfo(
                CHR_UUID, 1,
                BleGattCharacteristicInfo.PROPERTY_WRITE
                        | BleGattCharacteristicInfo.PROPERTY_WRITE_NO_RESPONSE,
                0, Collections.emptyList());
        BleGattServiceInfo svc = new BleGattServiceInfo(
                SVC_UUID, 1, true, Collections.singletonList(ch), Collections.emptyList());
        dev.setServices(Collections.singletonList(svc));
        harness.client.connect(id, BleConnectionConfig.builder().requestMtu(null).build(),
                new BleConnectionObserver() {});
        harness.drain();
        return dev;
    }
}
