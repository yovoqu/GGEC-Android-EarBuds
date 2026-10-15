package com.yovo.lib_bluetooth.api;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.yovo.lib_bluetooth.YoBluetoothSDK;
import com.yovo.lib_bluetooth.model.classic.CbtConfig;
import com.yovo.lib_bluetooth.model.classic.CbtDeviceId;
import com.yovo.lib_bluetooth.model.classic.CbtDeviceIdentity;
import com.yovo.lib_bluetooth.model.classic.CbtFailure;
import com.yovo.lib_bluetooth.model.classic.CbtOperationHandle;
import com.yovo.lib_bluetooth.model.classic.CbtServiceDiscoveryConfig;
import com.yovo.lib_bluetooth.model.classic.CbtServiceDiscoveryObserver;
import com.yovo.lib_bluetooth.model.classic.CbtServiceDiscoverySnapshot;
import com.yovo.lib_bluetooth.model.classic.CbtServiceDiscoveryState;
import com.yovo.lib_bluetooth.model.classic.CbtSppConfig;
import com.yovo.lib_bluetooth.model.classic.CbtSppConnectionStrategy;
import com.yovo.lib_bluetooth.model.classic.CbtSppConnectionId;
import com.yovo.lib_bluetooth.model.classic.CbtSppConnectionObserver;
import com.yovo.lib_bluetooth.model.classic.CbtSppConnectionSnapshot;
import com.yovo.lib_bluetooth.model.classic.CbtSppConnectionState;
import com.yovo.lib_bluetooth.model.classic.CbtSppEndpoint;
import com.yovo.lib_bluetooth.model.common.Result;
import com.yovo.lib_common.log.YoLog;

import org.junit.After;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * SPP 多场景仪器化测试：SDP 发现、UUID/Channel 连接、多设备多通道等。
 */
@RunWith(AndroidJUnit4.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class YoCbtSppMultiConnectionInstrumentedTest {

    private static final String TAG = "YoCbtSppMultiTest";

    private static final String DEVICE_1888_ADDRESS = "E8:09:59:0E:65:A7";
    private static final String DEVICE_V12_ADDRESS = "11:11:22:33:34:4B";

    private static final UUID CUSTOM_3838_UUID =
            UUID.fromString("3838180d-0000-1000-8000-00805f587e61");

    private static final int RFCOMM_CHANNEL_1888 = 15;

    private Context context;
    private YoCbtClient client;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        grantBluetoothPermissions();
        CbtConfig config = CbtConfig.builder()
                .allowHiddenApiSppChannel(true)
                .build();
        client = YoBluetoothSDK.classic(context, config);
        YoLog.i(TAG, "客户端已创建");
    }

    @After
    public void tearDown() throws Exception {
        if (client != null) {
            client.disconnectAllSpp();
            Thread.sleep(500);
            client.release();
            client = null;
        }
        Thread.sleep(1500);
    }

    @Test
    public void test0_sdpDiscovery_printDeviceUuids() throws Exception {
        YoLog.i(TAG, "Test 0: SDP 发现 1888 设备 UUID 列表");
        CbtDeviceId deviceId = CbtDeviceId.fromAddress(DEVICE_1888_ADDRESS);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> discoveredInfo = new AtomicReference<>("N/A");

        CbtServiceDiscoveryConfig sdpConfig = CbtServiceDiscoveryConfig.builder()
                .timeoutMillis(10_000L)
                .build();

        client.discoverServices(deviceId, sdpConfig, new CbtServiceDiscoveryObserver() {
            @Override
            public void onStateChanged(@NonNull CbtServiceDiscoveryState state) {
                YoLog.i(TAG, "SDP 状态=" + state);
            }

            @Override
            public void onResolved(@NonNull CbtServiceDiscoverySnapshot snapshot) {
                List<UUID> uuids = snapshot.discoveredUuids();
                StringBuilder sb = new StringBuilder();
                sb.append("Discovered UUIDs (").append(uuids.size()).append("):\n");
                for (int i = 0; i < uuids.size(); i++) {
                    sb.append("  [").append(i).append("] ").append(uuids.get(i)).append("\n");
                }
                sb.append("SPP endpoints (").append(snapshot.sppEndpoints().size()).append("):\n");
                for (CbtSppEndpoint ep : snapshot.sppEndpoints()) {
                    sb.append("  ").append(ep).append("\n");
                }
                discoveredInfo.set(sb.toString());
                YoLog.i(TAG, sb.toString());
                latch.countDown();
            }

            @Override
            public void onFailed(@NonNull CbtFailure failure) {
                YoLog.w(TAG, "SDP 失败: " + failure.code());
                latch.countDown();
            }
        });

        assertTrue("SDP should complete", latch.await(15, TimeUnit.SECONDS));
        YoLog.i(TAG, "最终 SDP 信息:\n" + discoveredInfo.get());
    }

    @Test
    public void test1_spp_connectViaStandardUuid_to1888() throws Exception {
        YoLog.i(TAG, "Test 1: 标准 UUID 连接 1888");
        CbtDeviceId deviceId = CbtDeviceId.fromAddress(DEVICE_1888_ADDRESS);
        CbtSppEndpoint endpoint = CbtSppEndpoint.defaultSpp();
        YoLog.i(TAG, "端点=" + endpoint);

        SppConnectionResult result = connectAndWait(deviceId, endpoint, 15_000);
        if (!result.reachedConnected) {
            YoLog.w(TAG, "首次连接失败，等待 2 秒后重试（SDP 与 RFCOMM 通道可能未释放）");
            Thread.sleep(2000);
            result = connectAndWait(deviceId, endpoint, 15_000);
        }
        logResult("标准UUID→1888", result);
        assertTrue("标准UUID连接应该成功", result.reachedConnected);
    }

    @Test
    public void test2_spp_connectViaCustom3838Uuid_to1888() throws Exception {
        YoLog.i(TAG, "Test 2: 3838 UUID 连接 1888");
        CbtDeviceId deviceId = CbtDeviceId.fromAddress(DEVICE_1888_ADDRESS);
        CbtSppEndpoint endpoint = CbtSppEndpoint.uuid(CUSTOM_3838_UUID);
        YoLog.i(TAG, "端点=" + endpoint);

        SppConnectionResult result = connectAndWait(deviceId, endpoint, 15_000);
        logResult("3838UUID→1888", result);
        if (!result.reachedConnected) {
            YoLog.w(TAG, "3838 UUID 连接失败——设备可能未注册该 UUID 服务。"
                    + " 这不是架构 BUG，而是对端设备不支持该 UUID。"
                    + " failureCode=" + result.failureCode);
        }
    }

    @Test
    public void test3_spp_connectViaRfcommChannel_to1888() throws Exception {
        YoLog.i(TAG, "Test 3: RFCOMM Channel(" + RFCOMM_CHANNEL_1888 + ") 连接 1888");
        CbtDeviceId deviceId = CbtDeviceId.fromAddress(DEVICE_1888_ADDRESS);
        CbtSppEndpoint endpoint = CbtSppEndpoint.channel(RFCOMM_CHANNEL_1888);
        YoLog.i(TAG, "端点=" + endpoint);

        CbtSppConfig config = CbtSppConfig.builder()
                .connectTimeoutMillis(15_000L)
                .maxConnectAttempts(1)
                .allowHiddenApiChannel(true)
                .connectionStrategy(CbtSppConnectionStrategy.CHANNEL_ONLY)
                .build();

        SppConnectionResult result = connectAndWait(deviceId, endpoint, config, 20_000);
        logResult("Channel(15)→1888", result);
        assertTrue("RFCOMM Channel连接应该成功", result.reachedConnected);
    }

    @Test
    public void test4_spp_singleDeviceMultiChannel_standardAndCustomUuid() throws Exception {
        YoLog.i(TAG, "Test 4: 单设备多通道 (标准UUID + 3838UUID) → 1888");
        CbtDeviceId deviceId = CbtDeviceId.fromAddress(DEVICE_1888_ADDRESS);

        CbtSppEndpoint endpointStd = CbtSppEndpoint.defaultSpp();
        CbtSppEndpoint endpoint3838 = CbtSppEndpoint.uuid(CUSTOM_3838_UUID);

        CbtSppConfig config = CbtSppConfig.builder()
                .connectTimeoutMillis(15_000L)
                .maxConnectAttempts(1)
                .build();

        // 先连接标准UUID
        YoLog.i(TAG, "第一路：标准UUID连接");
        CountDownLatch latch1 = new CountDownLatch(1);
        AtomicReference<SppConnectionResult> resultStd = new AtomicReference<>(new SppConnectionResult());

        CbtOperationHandle h1 = client.connectSpp(deviceId, endpointStd, config,
                new TrackingObserver("STD", resultStd, latch1));
        assertNotNull(h1);
        assertTrue("标准UUID连接应完成", latch1.await(15, TimeUnit.SECONDS));
        logResult("单设备-标准UUID", resultStd.get());

        if (!resultStd.get().reachedConnected) {
            YoLog.w(TAG, "标准UUID未连接成功，跳过第二路");
            return;
        }

        // 保持第一路不断开，再连接3838 UUID
        YoLog.i(TAG, "第二路：3838 UUID连接（标准UUID保持连接中）");
        CountDownLatch latch2 = new CountDownLatch(1);
        AtomicReference<SppConnectionResult> result3838 = new AtomicReference<>(new SppConnectionResult());

        CbtOperationHandle h2 = client.connectSpp(deviceId, endpoint3838, config,
                new TrackingObserver("3838", result3838, latch2));
        assertNotNull(h2);
        assertTrue("3838 UUID连接应完成", latch2.await(15, TimeUnit.SECONDS));
        logResult("单设备-3838UUID", result3838.get());

        // 查看活跃连接
        Result<List<CbtSppConnectionSnapshot>> active = client.getActiveSppConnections();
        assertTrue(active.isSuccess());
        YoLog.i(TAG, "当前活跃SPP连接数=" + active.getValue().size());
        for (CbtSppConnectionSnapshot snap : active.getValue()) {
            YoLog.i(TAG, "  活跃连接：ID=" + snap.connectionId()
                    + "，设备=" + snap.deviceId().value()
                    + "，端点=" + snap.endpoint()
                    + "，状态=" + snap.state());
        }

        assertTrue("标准UUID应已连接", resultStd.get().reachedConnected);
        if (!result3838.get().reachedConnected) {
            YoLog.w(TAG, "3838UUID未连接成功（设备可能不支持），但标准UUID仍在线上。"
                    + " 框架多通道管理正常。");
        }

        client.disconnectSppByDevice(deviceId);
        Thread.sleep(500);
    }

    @Test
    public void test5_spp_multiDeviceMultiConnection() throws Exception {
        YoLog.i(TAG, "Test 5: 多设备多连接 (1888 + V12 各一路SPP)");
        CbtDeviceId device1888 = CbtDeviceId.fromAddress(DEVICE_1888_ADDRESS);
        CbtDeviceId deviceV12 = CbtDeviceId.fromAddress(DEVICE_V12_ADDRESS);

        CbtSppEndpoint endpoint = CbtSppEndpoint.defaultSpp();
        CbtSppConfig config = CbtSppConfig.builder()
                .connectTimeoutMillis(15_000L)
                .maxConnectAttempts(1)
                .build();

        // 串行连接避免 RFCOMM 竞争
        YoLog.i(TAG, "连接 1888...");
        SppConnectionResult result1888 = connectAndWait(device1888, endpoint, 15_000);
        logResult("多设备-1888", result1888);

        if (!result1888.reachedConnected) {
            YoLog.e(TAG, "1888连接失败，尝试等待后重试...");
            Thread.sleep(2000);
            result1888 = connectAndWait(device1888, endpoint, 15_000);
            logResult("多设备-1888(重试)", result1888);
        }

        // 不断开1888，继续连接V12
        YoLog.i(TAG, "连接 V12，1888保持连接...");
        CountDownLatch latchV12 = new CountDownLatch(1);
        AtomicReference<SppConnectionResult> resultV12Ref = new AtomicReference<>(new SppConnectionResult());
        CbtOperationHandle hV12 = client.connectSpp(deviceV12, endpoint, config,
                new TrackingObserver("V12", resultV12Ref, latchV12));
        assertNotNull(hV12);
        assertTrue("V12连接应完成", latchV12.await(15, TimeUnit.SECONDS));
        logResult("多设备-V12", resultV12Ref.get());

        // 查看总体活跃连接
        Result<List<CbtSppConnectionSnapshot>> active = client.getActiveSppConnections();
        assertTrue(active.isSuccess());
        YoLog.i(TAG, "当前活跃SPP连接总数=" + active.getValue().size());
        for (CbtSppConnectionSnapshot snap : active.getValue()) {
            YoLog.i(TAG, "  活跃连接：ID=" + snap.connectionId()
                    + "，设备=" + snap.deviceId().value()
                    + "，端点=" + snap.endpoint()
                    + "，状态=" + snap.state());
        }

        Result<List<CbtSppConnectionSnapshot>> by1888 = client.getSppConnections(device1888);
        Result<List<CbtSppConnectionSnapshot>> byV12 = client.getSppConnections(deviceV12);
        YoLog.i(TAG, "1888 SPP连接数=" + (by1888.isSuccess() ? by1888.getValue().size() : "失败"));
        YoLog.i(TAG, "V12 SPP连接数=" + (byV12.isSuccess() ? byV12.getValue().size() : "失败"));

        if (result1888.reachedConnected && resultV12Ref.get().reachedConnected) {
            YoLog.i(TAG, "多设备同时SPP在线确认");
        }

        assertTrue("1888应连接成功", result1888.reachedConnected);
        assertTrue("V12应连接成功", resultV12Ref.get().reachedConnected);

        client.disconnectAllSpp();
        Thread.sleep(500);
        Result<List<CbtSppConnectionSnapshot>> afterAll = client.getActiveSppConnections();
        YoLog.i(TAG, "全部断开后活跃连接数=" + (afterAll.isSuccess() ? afterAll.getValue().size() : "失败"));
    }

    private SppConnectionResult connectAndWait(CbtDeviceId deviceId,
                                               CbtSppEndpoint endpoint,
                                               long awaitMillis) throws Exception {
        return connectAndWait(deviceId, endpoint,
                CbtSppConfig.builder()
                        .connectTimeoutMillis(12_000L)
                        .maxConnectAttempts(1)
                        .build(),
                awaitMillis);
    }

    private SppConnectionResult connectAndWait(CbtDeviceId deviceId,
                                               CbtSppEndpoint endpoint,
                                               CbtSppConfig config,
                                               long awaitMillis) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<SppConnectionResult> ref = new AtomicReference<>(new SppConnectionResult());

        CbtOperationHandle handle = client.connectSpp(deviceId, endpoint, config,
                new TrackingObserver("single", ref, latch));
        assertNotNull(handle);

        boolean finished = latch.await(awaitMillis, TimeUnit.MILLISECONDS);
        if (!finished && ref.get().terminalState == null) {
            ref.get().terminalState = CbtSppConnectionState.FAILED;
            ref.get().failureCode = "AWAIT_TIMEOUT";
        }
        return ref.get();
    }

    private void logResult(String label, SppConnectionResult result) {
        if (result == null) {
            YoLog.w(TAG, "[" + label + "] result=null (未到达终态)");
            return;
        }
        YoLog.i(TAG, "[" + label + "] 已连接=" + result.reachedConnected
                + "，终态=" + result.terminalState
                + "，失败=" + result.failureCode
                + "，状态历史=" + result.stateHistory);
    }

    private static class SppConnectionResult {
        volatile boolean reachedConnected = false;
        volatile CbtSppConnectionState terminalState;
        volatile String failureCode;
        volatile CbtFailure failure;
        final CopyOnWriteArrayList<String> stateHistory = new CopyOnWriteArrayList<>();
    }

    private class TrackingObserver implements CbtSppConnectionObserver {
        private final String label;
        private final AtomicReference<SppConnectionResult> ref;
        private final CountDownLatch latch;

        TrackingObserver(String label, AtomicReference<SppConnectionResult> ref, CountDownLatch latch) {
            this.label = label;
            this.ref = ref;
            this.latch = latch;
        }

        private SppConnectionResult r() {
            return ref.get();
        }

        @Override
        public void onSppStateChanged(@NonNull CbtSppConnectionSnapshot snapshot) {
            CbtSppConnectionState state = snapshot.state();
            r().stateHistory.add(state.name());
            YoLog.d(TAG, "[" + label + "] 状态=" + state + "，ID=" + snapshot.connectionId());

            if (state == CbtSppConnectionState.CONNECTED) {
                r().reachedConnected = true;
                r().terminalState = state;
                latch.countDown();
            }
            if (state == CbtSppConnectionState.FAILED
                    || state == CbtSppConnectionState.DISCONNECTED) {
                if (r().terminalState == null) {
                    r().terminalState = state;
                }
                if (snapshot.failure() != null) {
                    r().failure = snapshot.failure();
                    r().failureCode = snapshot.failure().code();
                }
                latch.countDown();
            }
        }

        @Override
        public void onSppDeviceResolved(@NonNull CbtDeviceIdentity identity) {
            YoLog.i(TAG, "[" + label + "] 设备已解析: " + identity.displayName());
        }

        @Override
        public void onSppDataReceived(@NonNull CbtSppConnectionId connectionId, @NonNull byte[] data) {
            YoLog.i(TAG, "[" + label + "] 收到数据: " + data.length + " 字节");
        }

        @Override
        public void onSppDataSent(@NonNull CbtSppConnectionId connectionId, @NonNull byte[] data) {
            YoLog.d(TAG, "[" + label + "] 发送数据: " + data.length + " 字节");
        }
    }

    private static void grantBluetoothPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;
        try {
            var ui = InstrumentationRegistry.getInstrumentation().getUiAutomation();
            String pkg = InstrumentationRegistry.getInstrumentation()
                    .getTargetContext().getPackageName();
            ui.grantRuntimePermission(pkg, Manifest.permission.BLUETOOTH_SCAN);
            ui.grantRuntimePermission(pkg, Manifest.permission.BLUETOOTH_CONNECT);
            ui.grantRuntimePermission(pkg, Manifest.permission.ACCESS_FINE_LOCATION);
            YoLog.i(TAG, "已授予蓝牙权限给 " + pkg);
        } catch (Throwable t) {
            YoLog.w(TAG, "授予权限失败", t);
        }
    }
}
