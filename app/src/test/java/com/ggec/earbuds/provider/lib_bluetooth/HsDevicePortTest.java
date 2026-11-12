package com.ggec.earbuds.provider.lib_bluetooth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.ggec.earbuds.domain.device.model.BatterySnapshot;
import com.ggec.earbuds.domain.device.model.ConnectionState;
import com.ggec.earbuds.domain.device.model.DeviceSnapshot;
import com.ggec.earbuds.domain.device.model.ScanResult;
import com.ggec.earbuds.domain.device.model.WearState;
import com.ggec.earbuds.domain.device.port.DevicePort;
import com.yovo.biz_ggec_hs.api.HsConnectionObserver;
import com.yovo.biz_ggec_hs.api.HsNearbyObserver;
import com.yovo.biz_ggec_hs.api.YoHsClient;
import com.yovo.biz_ggec_hs.api.YoHsNearby;
import com.yovo.biz_ggec_hs.model.HsBattery;
import com.yovo.biz_ggec_hs.model.HsDeviceInfo;
import com.yovo.biz_ggec_hs.model.HsDeviceObserver;
import com.yovo.biz_ggec_hs.model.HsNearbyDevice;
import com.yovo.biz_ggec_hs.model.HsResultCallback;
import com.yovo.biz_ggec_hs.model.HsScanFailure;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class HsDevicePortTest {

    @Test
    public void projectsNearbyDevicesAndScanFailures() {
        ScriptedNearby nearby = new ScriptedNearby();
        HsDevicePort port = new HsDevicePort(nearby, new FakeYoHsClient());
        List<List<ScanResult>> batches = new ArrayList<>();
        List<DevicePort.ScanFailure> failures = new ArrayList<>();
        AtomicBoolean finished = new AtomicBoolean(false);

        port.startScanBatch(batches::add, failures::add, () -> finished.set(true), ignored -> {
        });
        assertTrue(port.isScanning());
        nearby.observer.onUpdated(List.of(
                new HsNearbyDevice("HS01", "v2", "HS01-Demo", "AA:BB:CC:00:00:01", -40)));
        nearby.observer.onFailed(HsScanFailure.BLUETOOTH_DISABLED);
        nearby.observer.onFinished();
        nearby.scanning = false;

        assertEquals(1, batches.size());
        ScanResult projected = batches.get(0).get(0);
        assertEquals("HS01", projected.modelId());
        assertEquals("v2", projected.variantId());
        assertEquals("HS01-Demo", projected.bleName());
        assertEquals("AA:BB:CC:00:00:01", projected.bleMac());
        assertEquals(-40, projected.rssi());
        assertEquals(List.of(DevicePort.ScanFailure.BLUETOOTH_DISABLED), failures);
        assertTrue(finished.get());
        assertFalse(port.isScanning());
    }

    @Test
    public void projectsExpiredDeviceToRemovalCallback() {
        ScriptedNearby nearby = new ScriptedNearby();
        HsDevicePort port = new HsDevicePort(nearby, new FakeYoHsClient());
        List<ScanResult> removed = new ArrayList<>();

        port.startScanBatch(batch -> {
        }, failure -> {
        }, () -> {
        }, removed::add);
        nearby.observer.onLost(
                new HsNearbyDevice("HS02", "v1", "Vidda V11", "AA:BB:CC:00:00:02", -55));

        assertEquals(1, removed.size());
        assertEquals("HS02", removed.get(0).modelId());
        assertEquals("AA:BB:CC:00:00:02", removed.get(0).bleMac());
    }

    @Test
    public void projectsBizReadyTimeoutWithoutOwningItsOwnTimeoutTimer() {
        FakeYoHsClient session = new FakeYoHsClient();
        HsDevicePort port = new HsDevicePort(new ScriptedNearby(), session);

        port.connect("HS01", "v1", "AA:BB:CC:DD:EE:07", "HS01-Demo");
        assertEquals(ConnectionState.CONNECTING, port.snapshot().connection());

        // 连接就绪超时由 biz 会话层表达：biz 以 onConnectFailed("ready-timeout", false) 上报，
        // 端口只把它投影成 DISCONNECTED，不再自建 12s 计时器/Handler。
        session.connectionObserver.onConnectFailed("ready-timeout", false);
        assertEquals(ConnectionState.DISCONNECTED, port.snapshot().connection());
    }

    @Test
    public void connectRejectsEmptyMacExplicitly() {
        HsDevicePort port = new HsDevicePort(new ScriptedNearby(), new FakeYoHsClient());

        assertThrows(IllegalArgumentException.class,
                () -> port.connect("HS01", "v1", "", "HS01-Demo"));
        assertThrows(IllegalArgumentException.class,
                () -> port.connect("HS01", "v1", null, "HS01-Demo"));
    }

    @Test
    public void connectDrivesSnapshotFromProtocolSession() {
        FakeYoHsClient session = new FakeYoHsClient();
        HsDevicePort port = new HsDevicePort(new ScriptedNearby(), session);

        assertFalse(port.snapshot().isConnected());
        port.connect("HS01", "v1", "AA:BB:CC:DD:EE:01", "HS01-Demo");
        assertEquals(ConnectionState.CONNECTING, port.snapshot().connection());
        assertEquals("HS01", port.snapshot().modelId());
        assertEquals("HS01-Demo", port.snapshot().deviceName());
        assertEquals("AA:BB:CC:DD:EE:01", port.snapshot().mac());
        // 协议没有佩戴态来源：端口投未知，不是确定的「未佩戴」。
        assertEquals(WearState.UNKNOWN, port.snapshot().wear());

        session.battery = new HsBattery(90, 80, 70, false, 0);
        session.connectionObserver.onConnected("AA:BB:CC:DD:EE:01");
        assertEquals(ConnectionState.READY, port.snapshot().connection());
        assertEquals(90, port.snapshot().battery().left());
        assertEquals(70, port.snapshot().battery().caseLevel());

        session.battery = new HsBattery(89, 79, 69, true, 3);
        session.deviceObserver.onBatteryStateChanged(true, true, true);
        assertEquals(89, port.snapshot().battery().left());
        assertTrue(port.snapshot().battery().caseCharging());

        session.connectionObserver.onDisconnected("bye");
        assertEquals(ConnectionState.DISCONNECTED, port.snapshot().connection());
        assertFalse(port.snapshot().isConnected());
    }

    @Test
    public void projectionsKeepStructuredLevelAndUnknownApart() {
        FakeYoHsClient session = new FakeYoHsClient();
        HsDevicePort port = new HsDevicePort(new ScriptedNearby(), session);
        port.connect("HS01", "v1", "AA:BB:CC:DD:EE:15", "HS01-Demo");

        // biz 侧已是 int：80 是数值，UNKNOWN_LEVEL 是未知。端口只投影，不再解析文本。
        session.battery = new HsBattery(80, HsBattery.UNKNOWN_LEVEL, HsBattery.UNKNOWN_LEVEL, false, 0);
        session.connectionObserver.onConnected("AA:BB:CC:DD:EE:15");

        BatterySnapshot mixed = port.snapshot().battery();
        assertEquals(80, mixed.left());
        assertEquals(BatterySnapshot.UNKNOWN, mixed.right());
        assertEquals(BatterySnapshot.UNKNOWN, mixed.caseLevel());
        assertTrue(mixed.hasLeft());
        assertFalse(mixed.hasRight());
        assertFalse(mixed.hasCase());

        // 三项都未知：快照就是「未回报」，hasXxx 全部 false。
        session.battery = new HsBattery(HsBattery.UNKNOWN_LEVEL,
                HsBattery.UNKNOWN_LEVEL, HsBattery.UNKNOWN_LEVEL, false, 0);
        session.deviceObserver.onBatteryStateChanged(true, true, true);

        BatterySnapshot unknown = port.snapshot().battery();
        assertEquals(BatterySnapshot.UNKNOWN, unknown.left());
        assertEquals(BatterySnapshot.UNKNOWN, unknown.right());
        assertEquals(BatterySnapshot.UNKNOWN, unknown.caseLevel());
        assertFalse(unknown.hasLeft());
        assertFalse(unknown.hasRight());
        assertFalse(unknown.hasCase());
    }

    @Test
    public void projectsDeviceInfoIntoFirmwareAndEarColor() {
        FakeYoHsClient session = new FakeYoHsClient();
        HsDevicePort port = new HsDevicePort(new ScriptedNearby(), session);
        List<DeviceSnapshot> emitted = new ArrayList<>();
        port.observe(emitted::add);

        port.connect("HS01", "v1", "AA:BB:CC:DD:EE:03", "HS01-Demo");
        session.connectionObserver.onConnected("AA:BB:CC:DD:EE:03");

        // 设备未回报前是领域默认值，不是空文案。
        assertEquals("", port.snapshot().firmware());
        assertEquals(DeviceSnapshot.UNKNOWN_EAR_COLOR, port.snapshot().earColor());

        session.deviceObserver.onDeviceInfo(
                new HsDeviceInfo("2.0.7", 3, HsDeviceInfo.UNKNOWN_EAR_IN_CASE));

        assertEquals("2.0.7", port.snapshot().firmware());
        assertEquals(3, port.snapshot().earColor());
        DeviceSnapshot last = emitted.get(emitted.size() - 1);
        assertEquals("2.0.7", last.firmware());
        assertEquals(3, last.earColor());

        // 只回报颜色时固件回到未知，两项互不代替。
        session.deviceObserver.onDeviceInfo(
                new HsDeviceInfo("", 5, HsDeviceInfo.UNKNOWN_EAR_IN_CASE));
        assertEquals("", port.snapshot().firmware());
        assertEquals(5, port.snapshot().earColor());
    }

    @Test
    public void keepsDeviceInfoThatArrivesBeforeReadyForTheReadySnapshot() {
        FakeYoHsClient session = new FakeYoHsClient();
        HsDevicePort port = new HsDevicePort(new ScriptedNearby(), session);

        port.connect("HS01", "v1", "AA:BB:CC:DD:EE:04", "HS01-Demo");
        // 设备信息在 READY 之前到达：端口先缓存字段，不推新快照。
        session.reportFromDevice(
                new HsDeviceInfo("1.4.2", DeviceSnapshot.UNKNOWN_EAR_COLOR, HsDeviceInfo.UNKNOWN_EAR_IN_CASE));
        assertEquals(ConnectionState.CONNECTING, port.snapshot().connection());
        assertEquals("", port.snapshot().firmware());

        session.connectionObserver.onConnected("AA:BB:CC:DD:EE:04");
        assertEquals("1.4.2", port.snapshot().firmware());
        assertEquals(DeviceSnapshot.UNKNOWN_EAR_COLOR, port.snapshot().earColor());
    }

    @Test
    public void attachesDeviceObserverAfterTheNewSessionStarts() {
        FakeYoHsClient session = new FakeYoHsClient();
        HsDevicePort port = new HsDevicePort(new ScriptedNearby(), session);

        port.connect("HS01", "v1", "AA:BB:CC:DD:EE:07", "HS01-Demo");

        // 顺序契约：先开始新会话（biz 在 connect 入口清域），再挂会回放域值的设备信息观察者。
        assertEquals(List.of("setConnectionObserver", "connect", "setDeviceObserver"), session.calls);
    }

    @Test
    public void switchingDeviceWithoutDisconnectDoesNotCarryPreviousDeviceInfo() {
        FakeYoHsClient session = new FakeYoHsClient();
        HsDevicePort port = new HsDevicePort(new ScriptedNearby(), session);

        port.connect("HS01", "v1", "AA:BB:CC:DD:EE:08", "HS01-Demo");
        session.connectionObserver.onConnected("AA:BB:CC:DD:EE:08");
        session.reportFromDevice(
                new HsDeviceInfo("2.0.7", 3, HsDeviceInfo.UNKNOWN_EAR_IN_CASE));
        assertEquals("2.0.7", port.snapshot().firmware());
        assertEquals(3, port.snapshot().earColor());

        // 用户改连另一台、不先 disconnect：A 会话仍 ready，A 的固件 / 颜色还留在 biz 的会话域里。
        port.connect("HS02", "v1", "AA:BB:CC:DD:EE:09", "Vidda V11");
        session.connectionObserver.onConnected("AA:BB:CC:DD:EE:09");

        assertEquals(ConnectionState.READY, port.snapshot().connection());
        assertEquals("HS02", port.snapshot().modelId());
        assertEquals("", port.snapshot().firmware());
        assertEquals(DeviceSnapshot.UNKNOWN_EAR_COLOR, port.snapshot().earColor());
    }

    @Test
    public void reconnectDropsPreviousDeviceInfo() {
        FakeYoHsClient session = new FakeYoHsClient();
        HsDevicePort port = new HsDevicePort(new ScriptedNearby(), session);

        port.connect("HS01", "v1", "AA:BB:CC:DD:EE:05", "HS01-Demo");
        session.connectionObserver.onConnected("AA:BB:CC:DD:EE:05");
        session.deviceObserver.onDeviceInfo(
                new HsDeviceInfo("2.0.7", 3, HsDeviceInfo.UNKNOWN_EAR_IN_CASE));
        assertEquals("2.0.7", port.snapshot().firmware());

        port.disconnect();
        port.connect("HS02", "v1", "AA:BB:CC:DD:EE:06", "Vidda V11");

        assertEquals("", port.snapshot().firmware());
        assertEquals(DeviceSnapshot.UNKNOWN_EAR_COLOR, port.snapshot().earColor());
    }

    @Test
    public void twoEarsInCaseMapsToNotWorn() {
        FakeYoHsClient session = new FakeYoHsClient();
        HsDevicePort port = new HsDevicePort(new ScriptedNearby(), session);

        port.connect("HS01", "v1", "AA:BB:CC:DD:EE:11", "HS01-Demo");
        session.connectionObserver.onConnected("AA:BB:CC:DD:EE:11");

        session.deviceObserver.onDeviceInfo(
                new HsDeviceInfo("2.0.7", 3, HsDeviceInfo.EAR_IN_CASE_BOTH));

        // 两耳都在仓：耳机在充电盒里，确知未佩戴。
        assertEquals(WearState.NOT_WORN, port.snapshot().wear());
    }

    @Test
    public void earOutOfCaseOrUnreportedStaysUnknown() {
        FakeYoHsClient session = new FakeYoHsClient();
        HsDevicePort port = new HsDevicePort(new ScriptedNearby(), session);

        port.connect("HS01", "v1", "AA:BB:CC:DD:EE:12", "HS01-Demo");
        session.connectionObserver.onConnected("AA:BB:CC:DD:EE:12");

        // 仅左耳在仓：右耳出去了，但出仓不等于戴上，协议不能证明佩戴。
        session.deviceObserver.onDeviceInfo(
                new HsDeviceInfo("", DeviceSnapshot.UNKNOWN_EAR_COLOR, 1));
        assertEquals(WearState.UNKNOWN, port.snapshot().wear());

        // 两耳都不在仓：同样只能证明「不在盒内」，不能证明「在耳朵上」。
        session.deviceObserver.onDeviceInfo(
                new HsDeviceInfo("", DeviceSnapshot.UNKNOWN_EAR_COLOR, 0));
        assertEquals(WearState.UNKNOWN, port.snapshot().wear());

        // 从未上报：没有数据就是未知，不能写成确定的未佩戴。
        session.deviceObserver.onDeviceInfo(
                new HsDeviceInfo("", DeviceSnapshot.UNKNOWN_EAR_COLOR,
                        HsDeviceInfo.UNKNOWN_EAR_IN_CASE));
        assertEquals(WearState.UNKNOWN, port.snapshot().wear());
    }

    @Test
    public void switchingDeviceDoesNotKeepPreviousNotWorn() {
        FakeYoHsClient session = new FakeYoHsClient();
        HsDevicePort port = new HsDevicePort(new ScriptedNearby(), session);

        port.connect("HS01", "v1", "AA:BB:CC:DD:EE:13", "HS01-Demo");
        session.connectionObserver.onConnected("AA:BB:CC:DD:EE:13");
        session.deviceObserver.onDeviceInfo(
                new HsDeviceInfo("2.0.7", 3, HsDeviceInfo.EAR_IN_CASE_BOTH));
        assertEquals(WearState.NOT_WORN, port.snapshot().wear());

        // 换一台设备：新会话未上报前不得沿用上一台的「确知未佩戴」。
        port.connect("HS02", "v1", "AA:BB:CC:DD:EE:14", "Vidda V11");
        assertEquals(WearState.UNKNOWN, port.snapshot().wear());
        session.connectionObserver.onConnected("AA:BB:CC:DD:EE:14");
        assertEquals(WearState.UNKNOWN, port.snapshot().wear());
    }

    private static final class ScriptedNearby implements YoHsNearby {
        private HsNearbyObserver observer;
        private boolean scanning;

        @Override
        public void start(HsNearbyObserver observer) {
            this.observer = observer;
            scanning = true;
        }

        @Override
        public void stop() {
            scanning = false;
        }

        @Override
        public boolean isScanning() {
            return scanning;
        }
    }

    private static final class FakeYoHsClient implements YoHsClient {
        /** 调用顺序，用来锁住「先开始新会话、后挂设备信息观察者」这条顺序契约。 */
        private final List<String> calls = new ArrayList<>();
        private HsConnectionObserver connectionObserver;
        private HsDeviceObserver deviceObserver;
        private HsBattery battery = new HsBattery(
                HsBattery.UNKNOWN_LEVEL,
                HsBattery.UNKNOWN_LEVEL,
                HsBattery.UNKNOWN_LEVEL,
                false,
                0);
        /** 模拟 biz 的会话域：设备回报后落在这里，connect 入口清空（clearSessionState）。 */
        private HsDeviceInfo domainInfo;

        /** 设备主动回报（协议帧到达）：先落域，再投给当前观察者。 */
        void reportFromDevice(HsDeviceInfo info) {
            domainInfo = info;
            if (deviceObserver != null) {
                deviceObserver.onDeviceInfo(info);
            }
        }

        @Override
        public void connect(String macAddress) {
            calls.add("connect");
            domainInfo = null;
        }

        @Override
        public void disconnect() {
        }

        @Override
        public void close() {
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public HsBattery battery() {
            return battery;
        }

        @Override
        public void setDeviceObserver(HsDeviceObserver observer) {
            calls.add("setDeviceObserver");
            this.deviceObserver = observer;
            // biz 的 DeviceEventPipeline.setObserver 会同步回放域里已有的设备信息。
            if (observer != null && domainInfo != null) {
                observer.onDeviceInfo(domainInfo);
            }
        }

        @Override
        public void setConnectionObserver(HsConnectionObserver observer) {
            calls.add("setConnectionObserver");
            this.connectionObserver = observer;
        }

        @Override
        public void setRemoteStreamStop(Runnable trigger) {
        }

        @Override
        public void setEqMode(int mode, HsResultCallback callback) {
        }

        @Override
        public void setHighSoundQuality(boolean highQuality, HsResultCallback callback) {
        }

        @Override
        public void setLdacEnabled(boolean enabled, HsResultCallback callback) {
        }

        @Override
        public void setVolumeAdaptive(boolean enabled, HsResultCallback callback) {
        }

        @Override
        public void setGameMode(boolean enabled, HsResultCallback callback) {
        }

        @Override
        public void setWearDetection(boolean enabled, HsResultCallback callback) {
        }

        @Override
        public void startFindAlert(boolean leftEar, HsResultCallback callback) {
        }

        @Override
        public void stopFindAlert(HsResultCallback callback) {
        }

        @Override
        public void readEarColor(HsResultCallback callback) {
        }

        @Override
        public void reboot(HsResultCallback callback) {
        }
    }
}
