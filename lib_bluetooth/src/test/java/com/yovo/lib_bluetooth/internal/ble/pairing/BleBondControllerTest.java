package com.yovo.lib_bluetooth.internal.ble.pairing;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.yovo.lib_bluetooth.internal.ble.error.YoBleGattStatus;
import com.yovo.lib_bluetooth.internal.ble.testing.FakeBleGattDevice;
import com.yovo.lib_bluetooth.internal.ble.testing.FakeYoBleHarness;
import com.yovo.lib_bluetooth.internal.common.permission.YoBluetoothPermission;
import com.yovo.lib_bluetooth.model.ble.BleBondObserver;
import com.yovo.lib_bluetooth.model.ble.BleBondPolicy;
import com.yovo.lib_bluetooth.model.ble.BleBondState;
import com.yovo.lib_bluetooth.model.ble.BleConfig;
import com.yovo.lib_bluetooth.model.ble.BleConnectionConfig;
import com.yovo.lib_bluetooth.model.ble.BleConnectionObserver;
import com.yovo.lib_bluetooth.model.ble.BleConnectionSnapshot;
import com.yovo.lib_bluetooth.model.ble.BleConnectionState;
import com.yovo.lib_bluetooth.model.ble.BleDeviceId;
import com.yovo.lib_bluetooth.model.ble.BleDeviceIdentity;
import com.yovo.lib_bluetooth.model.ble.BleDeviceSnapshot;
import com.yovo.lib_bluetooth.model.ble.BleErrorCode;
import com.yovo.lib_bluetooth.model.ble.BleFailure;
import com.yovo.lib_bluetooth.model.ble.BleGattCharacteristicInfo;
import com.yovo.lib_bluetooth.model.ble.BleGattObserver;
import com.yovo.lib_bluetooth.model.ble.BleGattServiceInfo;
import com.yovo.lib_bluetooth.model.ble.BleGattTarget;
import com.yovo.lib_bluetooth.model.ble.BleValueObserver;
import com.yovo.lib_bluetooth.model.common.Result;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * BLE-023 配对全周期、ENSURE_BONDED 会话协同、受控 removeBond 及 pre-O ATT 认证失败自动绑定重试验证。
 */
public class BleBondControllerTest {

    private static final UUID SVC_UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb");
    private static final UUID CHR_UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb");

    @Test
    public void getBondedDevices_createBondLifecycle_andPermissionChecks() {
        FakeYoBleHarness harness = new FakeYoBleHarness();
        BleDeviceId bondedId = new BleDeviceId("AA:BB:CC:DD:EE:61");
        BleDeviceId newId = new BleDeviceId("AA:BB:CC:DD:EE:62");
        try {
            harness.bondPlatform.addBondedDevice(new BleDeviceIdentity(bondedId, "EarBuds-L", 1000L));
            Result<List<BleDeviceSnapshot>> listRes = harness.client.getBondedDevices();
            assertTrue(listRes.isSuccess());
            assertEquals(1, listRes.getValue().size());
            assertEquals(bondedId, listRes.getValue().get(0).deviceId());

            List<String> transitions = new ArrayList<>();
            harness.client.observeBondState((deviceId, prev, cur, reason) ->
                    transitions.add(deviceId + ":" + prev + "->" + cur + "(" + reason + ")"));

            // 1. 配对被拒（rawReason = 1 -> AUTHENTICATION_FAILED）
            List<BleFailure> bondFailures = new ArrayList<>();
            AtomicReference<BleDeviceSnapshot> bondSuccess = new AtomicReference<>();
            harness.client.createBond(newId, new BleValueObserver<BleDeviceSnapshot>() {
                @Override
                public void onSuccess(BleDeviceSnapshot value) {
                    bondSuccess.set(value);
                }

                @Override
                public void onFailed(BleFailure failure) {
                    bondFailures.add(failure);
                }
            });
            harness.drain();
            // FakeYoBleBondPlatform 已推送 NONE -> BONDING；再模拟远端拒绝 reason=1
            harness.bondPlatform.setBondState(newId, BleBondState.NONE);
            harness.core.onBondStateChanged(newId, BleBondState.BONDING, BleBondState.NONE, 1);
            harness.drain();

            assertEquals(1, bondFailures.size());
            assertEquals(BleErrorCode.AUTHENTICATION_FAILED, bondFailures.get(0).errorCode());
            assertEquals(1, bondFailures.get(0).rawStatusCode());

            // 2. 再次 createBond 并正常完成至 BONDED
            harness.client.createBond(newId, new BleValueObserver<BleDeviceSnapshot>() {
                @Override
                public void onSuccess(BleDeviceSnapshot value) {
                    bondSuccess.set(value);
                }
            });
            harness.bondPlatform.setBondState(newId, BleBondState.BONDED);
            harness.core.onBondStateChanged(newId, BleBondState.BONDING, BleBondState.BONDED, 0);
            harness.drain();

            assertTrue(bondSuccess.get() != null);
            assertEquals(BleBondState.BONDED, bondSuccess.get().bondState());
            assertTrue(transitions.size() >= 3);

            // 3. 缺失 CONNECT 权限被拒绝
            harness.env.deny(YoBluetoothPermission.CONNECT);
            assertEquals(BleErrorCode.PERMISSION_CONNECT_DENIED.name(),
                    harness.client.getBondedDevices().getFailure().code());
            AtomicReference<BleFailure> permErr = new AtomicReference<>();
            harness.client.createBond(newId, new BleValueObserver<BleDeviceSnapshot>() {
                @Override
                public void onSuccess(BleDeviceSnapshot value) {
                }

                @Override
                public void onFailed(BleFailure failure) {
                    permErr.set(failure);
                }
            });
            harness.drain();
            assertEquals(BleErrorCode.PERMISSION_CONNECT_DENIED, permErr.get().errorCode());
        } finally {
            harness.release();
        }
    }

    @Test
    public void removeBond_blockedByDefault_andAllowedWhenHiddenApiEnabled() {
        FakeYoBleHarness defaultHarness = new FakeYoBleHarness();
        FakeYoBleHarness allowedHarness = new FakeYoBleHarness(
                BleConfig.builder().allowHiddenApiBondRemoval(true).build());
        BleDeviceId id = new BleDeviceId("AA:BB:CC:DD:EE:63");
        try {
            defaultHarness.bondPlatform.addBondedDevice(new BleDeviceIdentity(id, "Buds", 100L));
            Result<Void> blocked = defaultHarness.client.removeBond(id);
            assertTrue(blocked.isFailure());
            assertEquals(BleErrorCode.HIDDEN_API_BLOCKED.name(), blocked.getFailure().code());

            allowedHarness.bondPlatform.addBondedDevice(new BleDeviceIdentity(id, "Buds", 100L));
            Result<Void> removed = allowedHarness.client.removeBond(id);
            assertTrue(removed.isSuccess());
            assertTrue(allowedHarness.client.getBondedDevices().getValue().isEmpty());
        } finally {
            defaultHarness.release();
            allowedHarness.release();
        }
    }

    @Test
    public void ensureBondedPolicy_waitsForUnbondedAndSkipsWaitWhenAlreadyBonded() {
        FakeYoBleHarness harness = new FakeYoBleHarness();
        BleDeviceId unbondedId = new BleDeviceId("AA:BB:CC:DD:EE:64");
        BleDeviceId bondedId = new BleDeviceId("AA:BB:CC:DD:EE:65");
        try {
            FakeBleGattDevice devUnbonded = setupDevice(harness, unbondedId);
            FakeBleGattDevice devBonded = setupDevice(harness, bondedId);
            harness.bondPlatform.addBondedDevice(new BleDeviceIdentity(bondedId, "BondedBuds", 100L));

            List<BleConnectionState> unbondedStates = new ArrayList<>();
            List<BleConnectionState> bondedStates = new ArrayList<>();

            BleConnectionConfig ensureBondedCfg = BleConnectionConfig.builder()
                    .bondPolicy(BleBondPolicy.ENSURE_BONDED_BEFORE_DISCOVERY)
                    .bondedDiscoveryDelayMillis(600L)
                    .requestMtu(null)
                    .build();

            // 1. 未配对设备应先停留在 BONDING_WAIT，配对完成并经过 600ms 延时后才触发 discoverServices
            harness.client.connect(unbondedId, ensureBondedCfg, new BleConnectionObserver() {
                @Override
                public void onConnectionStateChanged(BleConnectionSnapshot snapshot) {
                    unbondedStates.add(snapshot.state());
                }
            });
            harness.drain();

            assertEquals(BleConnectionState.BONDING_WAIT,
                    harness.client.getConnectionSnapshot(unbondedId).requireValue().state());
            assertEquals(0, devUnbonded.discoverCalls());

            harness.bondPlatform.setBondState(unbondedId, BleBondState.BONDED);
            harness.core.onBondStateChanged(unbondedId, BleBondState.BONDING, BleBondState.BONDED, 0);
            harness.drain();
            assertEquals(0, devUnbonded.discoverCalls());

            harness.advanceMillis(599L);
            assertEquals(0, devUnbonded.discoverCalls());
            harness.advanceMillis(1L);
            assertEquals(1, devUnbonded.discoverCalls());
            assertEquals(BleConnectionState.READY,
                    harness.client.getConnectionSnapshot(unbondedId).requireValue().state());
            assertTrue(unbondedStates.contains(BleConnectionState.BONDING_WAIT));

            // 2. 已配对设备跳过 BONDING_WAIT，直接按 bondedDiscoveryDelayMillis 进入服务发现
            harness.client.connect(bondedId, ensureBondedCfg, new BleConnectionObserver() {
                @Override
                public void onConnectionStateChanged(BleConnectionSnapshot snapshot) {
                    bondedStates.add(snapshot.state());
                }
            });
            harness.advanceMillis(600L);
            assertFalse(bondedStates.contains(BleConnectionState.BONDING_WAIT));
            assertEquals(1, devBonded.discoverCalls());
            assertEquals(BleConnectionState.READY,
                    harness.client.getConnectionSnapshot(bondedId).requireValue().state());
        } finally {
            harness.release();
        }
    }

    @Test
    public void preOreo_attAuthError_autoBondsAndRetriesOnce() {
        FakeYoBleHarness harness = new FakeYoBleHarness();
        BleDeviceId id = new BleDeviceId("AA:BB:CC:DD:EE:66");
        try {
            harness.env.setSdkInt(25);
            harness.bondPlatform.setAutoCompleteCreateBond(true, BleBondState.BONDED, 0);

            FakeBleGattDevice dev = setupDevice(harness, id);
            BleGattTarget target = BleGattTarget.characteristic(SVC_UUID, CHR_UUID);
            dev.putValue(target, new byte[]{0x42, 0x43});

            harness.client.connect(id, BleConnectionConfig.builder()
                            .bondPolicy(BleBondPolicy.NONE)
                            .requestMtu(null)
                            .build(),
                    new BleConnectionObserver() {});
            harness.drain();

            // 第一次读返回 0x05 (ATT Insufficient Authentication)，应触发 auto createBond 并重试成功
            dev.enqueueReadStatus(YoBleGattStatus.GATT_INSUFFICIENT_AUTHENTICATION);
            AtomicReference<byte[]> readValue = new AtomicReference<>();
            List<BleFailure> failures = new ArrayList<>();
            harness.client.read(id, target, new BleGattObserver() {
                @Override
                public void onRead(BleDeviceId d, BleGattTarget t, byte[] value) {
                    readValue.set(value);
                }

                @Override
                public void onFailed(BleFailure failure) {
                    failures.add(failure);
                }
            });
            harness.drain();

            assertArrayEquals(new byte[]{0x42, 0x43}, readValue.get());
            assertTrue(failures.isEmpty());
            assertEquals(BleBondState.BONDED, harness.bondPlatform.getBondState(id));

            // 再次重置为未配对并连续两次返回 0x0F (Insufficient Encryption)，验证只重试 1 次防止死循环
            harness.bondPlatform.setBondState(id, BleBondState.NONE);
            dev.enqueueReadStatus(YoBleGattStatus.GATT_INSUFFICIENT_ENCRYPTION);
            dev.enqueueReadStatus(YoBleGattStatus.GATT_INSUFFICIENT_ENCRYPTION);
            harness.client.read(id, target, new BleGattObserver() {
                @Override
                public void onFailed(BleFailure failure) {
                    failures.add(failure);
                }
            });
            harness.drain();

            assertEquals(1, failures.size());
            assertEquals(BleErrorCode.ENCRYPTION_FAILED, failures.get(0).errorCode());
        } finally {
            harness.release();
        }
    }

    @Test
    public void bondedWithoutPriorSnapshot_deliversFallbackSnapshot_andDirectNoneFailsWaiter() {
        FakeYoBleHarness harness = new FakeYoBleHarness();
        BleDeviceId bondedNoCacheId = new BleDeviceId("AA:BB:CC:DD:EE:67");
        BleDeviceId rejectedNoBondingId = new BleDeviceId("AA:BB:CC:DD:EE:68");
        try {
            // 1. 无预先扫描缓存的已配对设备 createBond 直接回调非空快照
            harness.bondPlatform.setBondState(bondedNoCacheId, BleBondState.BONDED);
            AtomicReference<BleDeviceSnapshot> bondedSnap = new AtomicReference<>();
            harness.client.createBond(bondedNoCacheId, new BleValueObserver<BleDeviceSnapshot>() {
                @Override
                public void onSuccess(BleDeviceSnapshot value) {
                    bondedSnap.set(value);
                }
            });
            harness.drain();
            assertTrue(bondedSnap.get() != null);
            assertEquals(bondedNoCacheId, bondedSnap.get().deviceId());
            assertEquals(BleBondState.BONDED, bondedSnap.get().bondState());

            // 2. 部分 ROM 跳过 BONDING 直接推送 prev=NONE -> cur=NONE 时，等待器也应立即失败不挂起
            AtomicReference<BleFailure> directNoneErr = new AtomicReference<>();
            harness.client.createBond(rejectedNoBondingId, new BleValueObserver<BleDeviceSnapshot>() {
                @Override
                public void onSuccess(BleDeviceSnapshot value) {
                }

                @Override
                public void onFailed(BleFailure failure) {
                    directNoneErr.set(failure);
                }
            });
            harness.drain();
            harness.bondPlatform.setBondState(rejectedNoBondingId, BleBondState.NONE);
            harness.core.onBondStateChanged(rejectedNoBondingId, BleBondState.NONE, BleBondState.NONE, 9);
            harness.drain();

            assertTrue(directNoneErr.get() != null);
            assertEquals(BleErrorCode.BOND_FAILED, directNoneErr.get().errorCode());
        } finally {
            harness.release();
        }
    }

    private static FakeBleGattDevice setupDevice(FakeYoBleHarness harness, BleDeviceId id) {
        FakeBleGattDevice dev = harness.connectionPlatform.device(id);
        BleGattCharacteristicInfo ch = new BleGattCharacteristicInfo(
                CHR_UUID, 1, BleGattCharacteristicInfo.PROPERTY_READ, 0, Collections.emptyList());
        BleGattServiceInfo svc = new BleGattServiceInfo(
                SVC_UUID, 1, true, Collections.singletonList(ch), Collections.emptyList());
        dev.setServices(Collections.singletonList(svc));
        return dev;
    }
}
