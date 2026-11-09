package com.yovo.lib_bluetooth.internal.ble.gatt;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.yovo.lib_bluetooth.internal.ble.error.YoBleGattStatus;
import com.yovo.lib_bluetooth.internal.ble.testing.FakeBleGattDevice;
import com.yovo.lib_bluetooth.internal.ble.testing.FakeYoBleHarness;
import com.yovo.lib_bluetooth.model.ble.BleConnectionConfig;
import com.yovo.lib_bluetooth.model.ble.BleConnectionObserver;
import com.yovo.lib_bluetooth.model.ble.BleDeviceId;
import com.yovo.lib_bluetooth.model.ble.BleErrorCode;
import com.yovo.lib_bluetooth.model.ble.BleFailure;
import com.yovo.lib_bluetooth.model.ble.BleGattCharacteristicInfo;
import com.yovo.lib_bluetooth.model.ble.BleGattDescriptorInfo;
import com.yovo.lib_bluetooth.model.ble.BleGattObserver;
import com.yovo.lib_bluetooth.model.ble.BleGattServiceInfo;
import com.yovo.lib_bluetooth.model.ble.BleGattTarget;
import com.yovo.lib_bluetooth.model.ble.BleOperationPriority;
import com.yovo.lib_bluetooth.model.ble.BleWriteType;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * BLE-017 Characteristic & Descriptor 全功能读写、写类型、防共享突变防御拷贝及 API 33+/24-32 双分支契约验证。
 */
public class BleGattReadWriteAndDescriptorTest {

    private static final UUID SVC_UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb");
    private static final UUID CHR_UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb");
    private static final UUID DESC_2901 = UUID.fromString("00002901-0000-1000-8000-00805f9b34fb");
    private static final UUID MISSING_UUID = UUID.fromString("00009999-0000-1000-8000-00805f9b34fb");

    @Test
    public void charAndDescriptor_readWriteAllTypes_andDefensiveCopies() {
        FakeYoBleHarness harness = new FakeYoBleHarness();
        BleDeviceId id = new BleDeviceId("AA:BB:CC:DD:EE:24");
        try {
            FakeBleGattDevice dev = setupDevice(harness, id);
            BleGattTarget charTarget = BleGattTarget.characteristic(SVC_UUID, CHR_UUID);
            BleGattTarget descTarget = BleGattTarget.descriptor(SVC_UUID, CHR_UUID, DESC_2901);

            // 1. 测试 Characteristic WITH_RESPONSE / WITHOUT_RESPONSE / SIGNED 及防御拷贝
            byte[] callerInput = new byte[]{0x11, 0x22, 0x33};
            AtomicBoolean writeOk = new AtomicBoolean(false);
            harness.client.write(id, charTarget, callerInput, BleWriteType.WITH_RESPONSE,
                    new BleGattObserver() {
                        @Override
                        public void onWrite(BleDeviceId d, BleGattTarget t) {
                            writeOk.set(true);
                        }
                    });
            // 立即篡改入参数组，绝不影响待发送内容
            callerInput[0] = (byte) 0xFF;

            harness.client.write(id, charTarget, new byte[]{0x44}, BleWriteType.WITHOUT_RESPONSE,
                    new BleGattObserver() {});
            harness.client.write(id, charTarget, new byte[]{0x55}, BleWriteType.SIGNED,
                    new BleGattObserver() {});
            harness.drain();

            assertTrue(writeOk.get());
            assertEquals(3, dev.charWrites().size());
            assertArrayEquals(new byte[]{0x11, 0x22, 0x33}, dev.charWrites().get(0).value);
            assertEquals(BleWriteType.WITH_RESPONSE, dev.charWrites().get(0).writeType);
            assertEquals(BleWriteType.WITHOUT_RESPONSE, dev.charWrites().get(1).writeType);
            assertEquals(BleWriteType.SIGNED, dev.charWrites().get(2).writeType);

            // 2. 测试 Read Characteristic 返回防御拷贝
            dev.putValue(charTarget, new byte[]{0x0A, 0x0B});
            AtomicReference<byte[]> readBack = new AtomicReference<>();
            harness.client.read(id, charTarget, new BleGattObserver() {
                @Override
                public void onRead(BleDeviceId d, BleGattTarget t, byte[] value) {
                    readBack.set(value);
                }
            });
            harness.drain();
            assertArrayEquals(new byte[]{0x0A, 0x0B}, readBack.get());
            readBack.get()[0] = 0x77;
            assertArrayEquals(new byte[]{0x0A, 0x0B}, dev.getStoredValue(charTarget));

            // 3. 测试 Descriptor 读写真正走 readDescriptor/writeDescriptor 而非忽略 descriptorUuid
            AtomicBoolean descWriteOk = new AtomicBoolean(false);
            AtomicReference<byte[]> descReadBack = new AtomicReference<>();
            harness.client.write(id, descTarget, new byte[]{(byte) 0xCA, (byte) 0xFE},
                    BleWriteType.WITH_RESPONSE, BleOperationPriority.NORMAL, new BleGattObserver() {
                        @Override
                        public void onDescriptorWrite(BleDeviceId d, BleGattTarget t) {
                            descWriteOk.set(true);
                        }
                    });
            harness.client.read(id, descTarget, new BleGattObserver() {
                @Override
                public void onDescriptorRead(BleDeviceId d, BleGattTarget t, byte[] value) {
                    descReadBack.set(value);
                }
            });
            harness.drain();

            assertTrue(descWriteOk.get());
            assertEquals(1, dev.descWrites().size());
            assertEquals(DESC_2901, dev.descWrites().get(0).target.descriptorUuid());
            assertArrayEquals(new byte[]{(byte) 0xCA, (byte) 0xFE}, descReadBack.get());
        } finally {
            harness.release();
        }
    }

    @Test
    public void missingTargetsAndGattStatusFailures_propagateErrorCodesAndRawStatus() {
        FakeYoBleHarness harness = new FakeYoBleHarness();
        BleDeviceId id = new BleDeviceId("AA:BB:CC:DD:EE:25");
        try {
            FakeBleGattDevice dev = setupDevice(harness, id);
            BleGattTarget charTarget = BleGattTarget.characteristic(SVC_UUID, CHR_UUID);
            BleGattTarget descTarget = BleGattTarget.descriptor(SVC_UUID, CHR_UUID, DESC_2901);

            List<BleFailure> failures = new ArrayList<>();
            BleGattObserver errCollector = new BleGattObserver() {
                @Override
                public void onFailed(BleFailure failure) {
                    failures.add(failure);
                }
            };

            harness.client.read(id, BleGattTarget.characteristic(MISSING_UUID, CHR_UUID), errCollector);
            harness.client.read(id, BleGattTarget.characteristic(SVC_UUID, MISSING_UUID), errCollector);
            harness.client.read(id, BleGattTarget.descriptor(SVC_UUID, CHR_UUID, MISSING_UUID), errCollector);
            harness.drain();

            assertEquals(BleErrorCode.SERVICE_NOT_FOUND, failures.get(0).errorCode());
            assertEquals(BleErrorCode.CHARACTERISTIC_NOT_FOUND, failures.get(1).errorCode());
            assertEquals(BleErrorCode.DESCRIPTOR_NOT_FOUND, failures.get(2).errorCode());

            // 底层写入返回 GATT_BUSY (0x84) 与 GATT_INVALID_ATTRIBUTE_LENGTH (0x0D)
            dev.enqueueWriteStatus(YoBleGattStatus.GATT_BUSY);
            dev.enqueueWriteStatus(YoBleGattStatus.GATT_INVALID_ATTRIBUTE_LENGTH);
            dev.setDefaultDescWriteStatus(YoBleGattStatus.GATT_WRITE_NOT_PERMITTED);

            failures.clear();
            harness.client.write(id, charTarget, new byte[]{1}, BleWriteType.WITH_RESPONSE, errCollector);
            harness.client.write(id, charTarget, new byte[]{2}, BleWriteType.WITH_RESPONSE, errCollector);
            harness.client.write(id, descTarget, new byte[]{3}, BleWriteType.WITH_RESPONSE, errCollector);
            harness.drain();

            assertEquals(3, failures.size());
            assertEquals(BleErrorCode.GATT_BUSY, failures.get(0).errorCode());
            assertEquals(YoBleGattStatus.GATT_BUSY, failures.get(0).rawStatusCode());
            assertEquals(BleErrorCode.INVALID_OFFSET_OR_LENGTH, failures.get(1).errorCode());
            assertEquals(BleErrorCode.WRITE_FAILED, failures.get(2).errorCode());
            assertEquals(YoBleGattStatus.GATT_WRITE_NOT_PERMITTED, failures.get(2).rawStatusCode());
        } finally {
            harness.release();
        }
    }

    @Test
    public void compatContract_api33AndLegacyBranches_presentInSource() throws IOException {
        File moduleRoot = new File("src/main/java").isDirectory()
                ? new File(".") : new File("lib_bluetooth");
        File compatFile = new File(moduleRoot,
                "src/main/java/com/yovo/lib_bluetooth/internal/ble/internal/android/compat/YoBleGattCompat.java");
        File bridgeFile = new File(moduleRoot,
                "src/main/java/com/yovo/lib_bluetooth/internal/ble/internal/android/gatt/YoBleAndroidGattCallbackBridge.java");

        String compatSrc = Files.readString(compatFile.toPath(), StandardCharsets.UTF_8);
        String bridgeSrc = Files.readString(bridgeFile.toPath(), StandardCharsets.UTF_8);

        assertNotNull(compatSrc);
        assertTrue(compatSrc.contains("Build.VERSION_CODES.TIRAMISU"));
        assertTrue(compatSrc.contains("gatt.writeCharacteristic(characteristic, value, androidWriteType)"));
        assertTrue(compatSrc.contains("BluetoothStatusCodes.SUCCESS"));
        assertTrue(compatSrc.contains("characteristic.setValue(value)"));
        assertTrue(compatSrc.contains("gatt.writeDescriptor(descriptor, value)"));

        // 确保 API 33+ 与旧回调不会双倍触发，且 onCharacteristicWrite 仅在 API < 33 读取 legacy characteristic.getValue()
        assertTrue(bridgeSrc.contains("if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)"));
        assertTrue(bridgeSrc.contains("echoed = null;"));
        assertTrue(bridgeSrc.contains("value.clone()"));
        assertTrue(bridgeSrc.contains("val.clone()"));
    }

    private static FakeBleGattDevice setupDevice(FakeYoBleHarness harness, BleDeviceId id) {
        FakeBleGattDevice dev = harness.connectionPlatform.device(id);
        BleGattDescriptorInfo desc = new BleGattDescriptorInfo(
                DESC_2901,
                BleGattDescriptorInfo.PERMISSION_READ | BleGattDescriptorInfo.PERMISSION_WRITE);
        BleGattCharacteristicInfo ch = new BleGattCharacteristicInfo(
                CHR_UUID, 1,
                BleGattCharacteristicInfo.PROPERTY_READ
                        | BleGattCharacteristicInfo.PROPERTY_WRITE
                        | BleGattCharacteristicInfo.PROPERTY_WRITE_NO_RESPONSE
                        | BleGattCharacteristicInfo.PROPERTY_SIGNED_WRITE,
                0, Collections.singletonList(desc));
        BleGattServiceInfo svc = new BleGattServiceInfo(
                SVC_UUID, 1, true, Collections.singletonList(ch), Collections.emptyList());
        dev.setServices(Collections.singletonList(svc));
        harness.client.connect(id, BleConnectionConfig.builder().requestMtu(null).build(),
                new BleConnectionObserver() {});
        harness.drain();
        return dev;
    }
}
