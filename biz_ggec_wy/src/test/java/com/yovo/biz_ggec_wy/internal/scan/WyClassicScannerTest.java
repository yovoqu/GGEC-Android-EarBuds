package com.yovo.biz_ggec_wy.internal.scan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.biz_ggec_wy.api.WyControl;
import com.yovo.biz_ggec_wy.api.WyScan;
import com.yovo.biz_ggec_wy.internal.center.port.WyClassicRadioPort;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WyClassicScannerTest {

    private static class FakeWyClassicRadio implements WyClassicRadioPort {
        DiscoveryListener discoveryListener;
        boolean discoveryStarted;
        boolean discoveryStopped;
        final List<BondedDevice> bondedDevices = new ArrayList<>();

        @NonNull
        @Override
        public List<BondedDevice> getBondedDevices() {
            return new ArrayList<>(bondedDevices);
        }

        @Override
        public void startDiscovery(@NonNull DiscoveryListener listener) {
            this.discoveryListener = listener;
            this.discoveryStarted = true;
        }

        @Override
        public void stopDiscovery() {
            this.discoveryStopped = true;
            this.discoveryStarted = false;
        }

        @Override
        public void connect(@NonNull String classicAddress, @NonNull SppStateListener listener) {
        }

        @Override
        public void disconnect() {
        }

        @Override
        public void connectAudioSpp(@NonNull ActionCallback callback) {
        }

        @Override
        public void disconnectAudioSpp() {
        }

        @Override
        public void sendControl(@NonNull byte[] data, @NonNull SendCallback callback) {
        }

        @Override
        public boolean isControlConnected() {
            return false;
        }

        @Override
        public boolean isAudioConnected() {
            return false;
        }

        @Override
        public boolean isClassicConnected(@NonNull String classicAddress) {
            return true;
        }

        @Override
        public void release() {
        }

        void emitDevice(String name, String address, int rssi, long timestamp) {
            if (discoveryListener != null) {
                discoveryListener.onDeviceDiscovered(name, address, rssi, timestamp);
            }
        }

        void emitFinished() {
            if (discoveryListener != null) {
                discoveryListener.onDiscoveryFinished();
            }
        }
    }

    @Test
    public void testPrefixFilterAcceptsOnlyWy01() {
        FakeWyClassicRadio radio = new FakeWyClassicRadio();
        WyClassicScanner scanner = new WyClassicScanner(radio, Runnable::run);

        List<List<WyScan.Found>> updates = new ArrayList<>();
        AtomicBoolean finished = new AtomicBoolean(false);

        scanner.start(new WyScan.Listener() {
            @Override
            public void onUpdated(List<WyScan.Found> devices) {
                updates.add(new ArrayList<>(devices));
            }

            @Override
            public void onFailed(WyControl.Failure failure) {
            }

            @Override
            public void onFinished() {
                finished.set(true);
            }
        });

        assertTrue(scanner.isScanning());

        // 1. 发送不以 WY01 开头的设备 -> 必须被忽略
        radio.emitDevice("HS01_Earbuds", "11:22:33:44:55:01", -60, 1000L);
        radio.emitDevice("WY02_Buds", "11:22:33:44:55:02", -55, 1001L);
        radio.emitDevice("Other_BT", "11:22:33:44:55:03", -50, 1002L);
        radio.emitDevice(null, "11:22:33:44:55:04", -40, 1003L);

        assertEquals(0, updates.size());

        // 2. 发送 WY01 前缀设备（大写） -> 必须被接受
        radio.emitDevice("WY01-Pro-Left", "AA:BB:CC:DD:EE:01", -70, 1004L);
        assertEquals(1, updates.size());
        assertEquals(1, updates.get(0).size());
        assertEquals("WY01", updates.get(0).get(0).modelId());
        assertEquals("AA:BB:CC:DD:EE:01", updates.get(0).get(0).radios().classicAddress());

        // 3. 发送 wy01 前缀设备（小写，忽略大小写） -> 必须被接受
        radio.emitDevice("wy01-lite", "AA:BB:CC:DD:EE:02", -50, 1005L);
        assertEquals(2, updates.size());
        List<WyScan.Found> latestList = updates.get(1);
        assertEquals(2, latestList.size());
        // 信号强的排前面：-50 优于 -70
        assertEquals("AA:BB:CC:DD:EE:02", latestList.get(0).radios().classicAddress());
        assertEquals("AA:BB:CC:DD:EE:01", latestList.get(1).radios().classicAddress());

        // 4. 发送 Wy01 混合大小写前缀设备 -> 必须被接受
        radio.emitDevice("Wy01_Special", "AA:BB:CC:DD:EE:03", -40, 1006L);
        assertEquals(3, updates.size());
        assertEquals(3, updates.get(2).size());
        assertEquals("AA:BB:CC:DD:EE:03", updates.get(2).get(0).radios().classicAddress());

        // 5. 发现完成
        radio.emitFinished();
        assertTrue(finished.get());
        assertFalse(scanner.isScanning());
    }

    @Test
    public void testStopScan() {
        FakeWyClassicRadio radio = new FakeWyClassicRadio();
        WyClassicScanner scanner = new WyClassicScanner(radio, Runnable::run);

        scanner.start(new WyScan.Listener() {
            @Override
            public void onUpdated(List<WyScan.Found> devices) {
            }

            @Override
            public void onFailed(WyControl.Failure failure) {
            }

            @Override
            public void onFinished() {
            }
        });
        assertTrue(scanner.isScanning());

        scanner.stop();
        assertFalse(scanner.isScanning());
        assertTrue(radio.discoveryStopped);
    }

    @Test
    public void testStartReportsBondedDevicesImmediately() {
        FakeWyClassicRadio radio = new FakeWyClassicRadio();
        radio.bondedDevices.add(new WyClassicRadioPort.BondedDevice("WY01-Paired", "AA:BB:CC:11:22:33", false));
        radio.bondedDevices.add(new WyClassicRadioPort.BondedDevice("WY01-Connected", "AA:BB:CC:11:22:44", true));
        radio.bondedDevices.add(new WyClassicRadioPort.BondedDevice("HS02-Paired", "AA:BB:CC:11:22:55", false));

        WyClassicScanner scanner = new WyClassicScanner(radio, Runnable::run);
        List<List<WyScan.Found>> updates = new ArrayList<>();

        scanner.start(new WyScan.Listener() {
            @Override
            public void onUpdated(List<WyScan.Found> devices) {
                updates.add(new ArrayList<>(devices));
            }

            @Override
            public void onFailed(WyControl.Failure failure) {
            }

            @Override
            public void onFinished() {
            }
        });

        // 启动瞬间就应立即上报所有已配对（不论已连接或未连接）的 WY01 设备，忽略非 WY01 设备
        assertFalse(updates.isEmpty());
        List<WyScan.Found> immediateDevices = updates.get(0);
        assertEquals(2, immediateDevices.size());
    }
}
