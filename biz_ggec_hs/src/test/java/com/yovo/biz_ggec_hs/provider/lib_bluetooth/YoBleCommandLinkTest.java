package com.yovo.biz_ggec_hs.provider.lib_bluetooth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.yovo.biz_ggec_hs.internal.center.port.HsCommandLink;
import com.yovo.lib_bluetooth.api.YoBleClient;
import com.yovo.lib_bluetooth.model.ble.BleBondState;
import com.yovo.lib_bluetooth.model.ble.BleConnectionObserver;
import com.yovo.lib_bluetooth.model.ble.BleConnectionSnapshot;
import com.yovo.lib_bluetooth.model.ble.BleConnectionState;
import com.yovo.lib_bluetooth.model.ble.BleDeviceId;
import com.yovo.lib_bluetooth.model.ble.BleErrorCode;
import com.yovo.lib_bluetooth.model.ble.BleFailure;
import com.yovo.lib_bluetooth.model.ble.BleGattObserver;
import com.yovo.lib_bluetooth.model.ble.BleGattTarget;
import com.yovo.lib_bluetooth.model.ble.BleNotificationObserver;
import com.yovo.lib_bluetooth.model.ble.BleOperationHandle;
import com.yovo.lib_bluetooth.model.ble.BlePhyConfig;
import com.yovo.lib_bluetooth.model.ble.BleTransferProgressObserver;
import com.yovo.lib_bluetooth.model.common.Result;

import org.junit.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 钉住 {@link YoBleCommandLink} 的写入受理与失败路径：受理不吞异常、失败一律经类型化回调送达、
 * 单包上限取真实连接快照而不是猜一个默认值。
 */
public final class YoBleCommandLinkTest {

    private static final String MAC = "11:22:33:44:55:66";
    private static final BleDeviceId DEVICE = new BleDeviceId(MAC);
    private static final BleGattTarget TARGET =
            BleGattTarget.characteristic(UUID.randomUUID(), UUID.randomUUID());

    @Test
    public void writeBeforeReadyIsRejectedWithoutTouchingTheLibrary() {
        FakeClient client = new FakeClient();
        YoBleCommandLink link = new YoBleCommandLink(client.proxy());
        RecordingCallback callback = new RecordingCallback();

        assertFalse(link.write(new byte[]{0x01, 0x02, 0x03}, callback));

        assertTrue("未就绪的写入不得下发到 lib: " + client.trace, client.trace.isEmpty());
        assertTrue("未受理的写入不得回调 onStarted: " + callback.events, callback.events.isEmpty());
    }

    @Test
    public void writeAnnouncesStartedBeforeDispatchAndCompletesOnLibraryAck() {
        FakeClient client = new FakeClient();
        YoBleCommandLink link = new YoBleCommandLink(client.proxy());
        connectReady(link, client);
        client.trace.clear();

        HsCommandLink.WriteCallback callback = new HsCommandLink.WriteCallback() {
            @Override
            public void onStarted() {
                client.trace.add("onStarted");
            }

            @Override
            public void onCompleted(int status) {
                client.trace.add("onCompleted:" + status);
            }

            @Override
            public void onTimeout() {
                client.trace.add("onTimeout");
            }
        };

        assertTrue(link.write(new byte[]{0x01}, callback));
        assertEquals(List.of("onStarted", "write"), client.trace);

        client.gattObserver.onWrite(DEVICE, TARGET);
        assertEquals(List.of("onStarted", "write", "onCompleted:0"), client.trace);
    }

    @Test
    public void libraryWriteFailureIsDeliveredAsTypedTerminalCallback() {
        FakeClient client = new FakeClient();
        YoBleCommandLink link = new YoBleCommandLink(client.proxy());
        connectReady(link, client);

        RecordingCallback timedOut = new RecordingCallback();
        assertTrue(link.write(new byte[]{0x01}, timedOut));
        client.gattObserver.onFailed(failure(BleErrorCode.GATT_OPERATION_TIMEOUT, BleFailure.NO_RAW_STATUS));
        assertEquals(List.of("onStarted", "onTimeout"), timedOut.events);

        RecordingCallback completed = new RecordingCallback();
        assertTrue(link.write(new byte[]{0x02}, completed));
        client.gattObserver.onFailed(failure(BleErrorCode.WRITE_FAILED, 133));
        assertEquals(List.of("onStarted", "onCompleted:133"), completed.events);
    }

    @Test
    public void payloadAboveSnapshotLimitUsesSplitWriter() {
        FakeClient client = new FakeClient();
        client.mtu = BleConnectionSnapshot.DEFAULT_ATT_MTU;
        YoBleCommandLink link = new YoBleCommandLink(client.proxy());
        connectReady(link, client);
        client.trace.clear();

        RecordingCallback callback = new RecordingCallback();
        // 默认 ATT MTU 23 → 单包上限 20 字节，21 字节必须走分片写。
        assertTrue(link.write(new byte[21], callback));

        assertEquals(List.of("writeSplit"), client.trace);
        assertEquals(List.of("onStarted"), callback.events);
        assertEquals(1, client.snapshotQueries);
        client.transferObserver.onCompleted(DEVICE, TARGET, 21L);
        assertEquals(List.of("onStarted", "onCompleted:0"), callback.events);
    }

    @Test
    public void payloadWithinSnapshotLimitUsesSingleWrite() {
        FakeClient client = new FakeClient();
        client.mtu = 517;
        YoBleCommandLink link = new YoBleCommandLink(client.proxy());
        connectReady(link, client);
        client.trace.clear();

        RecordingCallback callback = new RecordingCallback();
        assertTrue(link.write(new byte[21], callback));

        assertEquals(List.of("write"), client.trace);
        assertEquals(1, client.snapshotQueries);
        assertEquals(List.of("onStarted"), callback.events);
    }

    @Test
    public void unavailableConnectionSnapshotSurfacesInsteadOfGuessingDefaultMtu() {
        FakeClient client = new FakeClient();
        client.snapshotUnavailable = true;
        YoBleCommandLink link = new YoBleCommandLink(client.proxy());
        connectReady(link, client);

        RecordingCallback callback = new RecordingCallback();
        IllegalStateException rejected = assertThrows(IllegalStateException.class,
                () -> link.write(new byte[]{0x01}, callback));

        assertTrue(rejected.getMessage().contains("Result contains no value"));
        assertTrue("快照不可用时不得假装写出去了: " + callback.events, callback.events.isEmpty());
        assertFalse("快照不可用时不得下发写入: " + client.trace, client.trace.contains("write"));
    }

    private static BleFailure failure(BleErrorCode code, int rawStatus) {
        return new BleFailure(code, code.name(), null, false, false, "test",
                DEVICE, null, null, null, rawStatus, null, null);
    }

    private static void connectReady(YoBleCommandLink link, FakeClient client) {
        link.open(MAC);
        client.connectionObserver.onConnectionStateChanged(
                new BleConnectionSnapshot(DEVICE, BleConnectionState.READY, 1000L));
        client.notificationObserver.onNotificationEnabled(DEVICE, TARGET);
    }

    private static final class RecordingCallback implements HsCommandLink.WriteCallback {
        private final List<String> events = new ArrayList<>();

        @Override
        public void onStarted() {
            events.add("onStarted");
        }

        @Override
        public void onCompleted(int status) {
            events.add("onCompleted:" + status);
        }

        @Override
        public void onTimeout() {
            events.add("onTimeout");
        }
    }

    /** 只实现 {@link YoBleCommandLink} 真正走到的方法；其他调用直接暴露为测试缺口。 */
    private static final class FakeClient implements InvocationHandler {

        private final List<String> trace = new ArrayList<>();
        private BleConnectionObserver connectionObserver;
        private BleNotificationObserver notificationObserver;
        private BleGattObserver gattObserver;
        private BleTransferProgressObserver transferObserver;
        private boolean snapshotUnavailable;
        private int mtu = BleConnectionSnapshot.DEFAULT_ATT_MTU;
        private int snapshotQueries;

        private YoBleClient proxy() {
            return (YoBleClient) Proxy.newProxyInstance(
                    YoBleClient.class.getClassLoader(), new Class<?>[]{YoBleClient.class}, this);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "connect":
                    connectionObserver = (BleConnectionObserver) args[2];
                    trace.add("connect");
                    return handle();
                case "subscribe":
                    notificationObserver = (BleNotificationObserver) args[3];
                    trace.add("subscribe");
                    return handle();
                case "write":
                    gattObserver = (BleGattObserver) args[4];
                    trace.add("write");
                    return handle();
                case "writeSplit":
                    transferObserver = (BleTransferProgressObserver) args[5];
                    trace.add("writeSplit");
                    return handle();
                case "getConnectionSnapshot":
                    snapshotQueries++;
                    return snapshot((BleDeviceId) args[0]);
                case "disconnect":
                    trace.add("disconnect");
                    return Result.success(null);
                case "release":
                    trace.add("release");
                    return null;
                case "toString":
                    return "FakeYoBleClient";
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == args[0];
                default:
                    throw new UnsupportedOperationException(method.getName());
            }
        }

        private Result<BleConnectionSnapshot> snapshot(BleDeviceId deviceId) {
            if (snapshotUnavailable) {
                return Result.failure(failure(BleErrorCode.DEVICE_INVALID, BleFailure.NO_RAW_STATUS));
            }
            return Result.success(new BleConnectionSnapshot(
                    deviceId, BleConnectionState.READY, mtu, BlePhyConfig.le1m(), null,
                    BleBondState.UNKNOWN, null, 0, true, null, 1000L));
        }

        private BleOperationHandle handle() {
            return new BleOperationHandle("op", () -> trace.add("cancel"));
        }
    }
}
