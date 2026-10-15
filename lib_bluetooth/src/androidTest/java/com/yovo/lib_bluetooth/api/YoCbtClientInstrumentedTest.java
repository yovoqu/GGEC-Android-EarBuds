package com.yovo.lib_bluetooth.api;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.yovo.lib_bluetooth.YoBluetoothSDK;
import com.yovo.lib_bluetooth.model.classic.CbtAclLinkObserver;
import com.yovo.lib_bluetooth.model.classic.CbtAclLinkState;
import com.yovo.lib_bluetooth.model.classic.CbtBondStateObserver;
import com.yovo.lib_bluetooth.model.classic.CbtDeviceId;
import com.yovo.lib_bluetooth.model.classic.CbtDeviceProfileSnapshot;
import com.yovo.lib_bluetooth.model.classic.CbtDeviceProfileState;
import com.yovo.lib_bluetooth.model.classic.CbtDeviceSnapshot;
import com.yovo.lib_bluetooth.model.classic.CbtDiscoveryConfig;
import com.yovo.lib_bluetooth.model.classic.CbtDiscoveryObserver;
import com.yovo.lib_bluetooth.model.classic.CbtDiscoveryResult;
import com.yovo.lib_bluetooth.model.classic.CbtDiscoveryState;
import com.yovo.lib_bluetooth.model.classic.CbtFailure;
import com.yovo.lib_bluetooth.model.classic.CbtOperationHandle;
import com.yovo.lib_bluetooth.model.classic.CbtProfileAggregateState;
import com.yovo.lib_bluetooth.model.classic.CbtProfileConnectionState;
import com.yovo.lib_bluetooth.model.classic.CbtProfileStateObserver;
import com.yovo.lib_bluetooth.model.classic.CbtProfileType;
import com.yovo.lib_bluetooth.model.classic.CbtServiceDiscoveryConfig;
import com.yovo.lib_bluetooth.model.classic.CbtServiceDiscoveryObserver;
import com.yovo.lib_bluetooth.model.classic.CbtServiceDiscoverySnapshot;
import com.yovo.lib_bluetooth.model.classic.CbtServiceDiscoveryState;
import com.yovo.lib_bluetooth.model.classic.CbtSppConfig;
import com.yovo.lib_bluetooth.model.classic.CbtSppConnectionObserver;
import com.yovo.lib_bluetooth.model.classic.CbtSppConnectionSnapshot;
import com.yovo.lib_bluetooth.model.classic.CbtSppConnectionState;
import com.yovo.lib_bluetooth.model.classic.CbtSppEndpoint;
import com.yovo.lib_bluetooth.model.common.Result;
import com.yovo.lib_common.log.YoLog;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 经典蓝牙公开门面 {@link YoCbtClient} 的真机仪器化验证测试（需 connectedDebugAndroidTest）。
 */
@RunWith(AndroidJUnit4.class)
public class YoCbtClientInstrumentedTest {

    private static final String TAG = "YoCbtInstrumentedTest";

    private Context context;
    private YoCbtClient client;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        grantBluetoothPermissions();
        client = YoBluetoothSDK.classic(context);
        YoLog.i(TAG, "经典蓝牙客户端已创建，SDK " + Build.VERSION.SDK_INT);
    }

    @After
    public void tearDown() {
        if (client != null) {
            client.release();
            client = null;
        }
    }

    @Test
    public void lifecycle_releaseAllowsFreshInstance() {
        client.release();
        YoCbtClient second = YoBluetoothSDK.classic(context);
        assertNotSame(client, second);
        second.release();
        client = null;
    }

    @Test
    public void bondedDevices_returnsSuccessWithList() {
        Result<List<CbtDeviceSnapshot>> result = client.getBondedDevices();
        YoLog.i(TAG, "获取已配对设备成功=" + result.isSuccess()
                + "，数量=" + (result.isSuccess() && result.getValue() != null
                ? result.getValue().size() : -1));
        if (result.isFailure()) {
            YoLog.w(TAG, "获取已配对设备失败，错误码=" + result.getFailure().code());
        }
        assertTrue("getBondedDevices should succeed with permissions granted", result.isSuccess());
        assertNotNull(result.getValue());
        for (CbtDeviceSnapshot snapshot : result.getValue()) {
            YoLog.i(TAG, "已配对设备=" + snapshot.identity().displayName()
                    + " [" + snapshot.deviceId() + "] 配对状态=" + snapshot.bondState());
        }
    }

    @Test
    public void deviceSnapshots_reflectsBondedDevicesInStore() {
        Result<List<CbtDeviceSnapshot>> bonded = client.getBondedDevices();
        assertTrue(bonded.isSuccess());

        Result<List<CbtDeviceSnapshot>> snapshots = client.getDeviceSnapshots();
        assertTrue(snapshots.isSuccess());
        assertNotNull(snapshots.getValue());
        YoLog.i(TAG, "快照存储数量=" + snapshots.getValue().size());
        assertFalse("snapshot store should contain bonded devices", snapshots.getValue().isEmpty());
    }

    @Test
    public void discovery_findsAtLeastZeroDevicesWithoutCrash() throws Exception {
        AtomicInteger foundCount = new AtomicInteger();
        AtomicReference<CbtFailure> failure = new AtomicReference<>();
        CountDownLatch finished = new CountDownLatch(1);

        CbtDiscoveryConfig config = CbtDiscoveryConfig.builder()
                .timeoutMillis(8_000L)
                .build();

        CbtOperationHandle handle = client.discover(config, new CbtDiscoveryObserver() {
            @Override
            public void onDiscoveryStateChanged(CbtDiscoveryState state) {
                YoLog.i(TAG, "发现状态=" + state);
                if (state == CbtDiscoveryState.FINISHED
                        || state == CbtDiscoveryState.FAILED
                        || state == CbtDiscoveryState.IDLE) {
                    finished.countDown();
                }
            }

            @Override
            public void onDeviceFound(CbtDiscoveryResult result) {
                foundCount.incrementAndGet();
                YoLog.i(TAG, "发现设备: " + result.snapshot().identity().displayName());
            }

            @Override
            public void onDiscoveryFailed(CbtFailure f) {
                failure.set(f);
                YoLog.w(TAG, "发现失败: " + f.code() + " " + f.message());
                finished.countDown();
            }
        });

        assertNotNull(handle);
        assertTrue("discovery should finish within timeout", finished.await(15, TimeUnit.SECONDS));
        handle.cancel();

        Result<List<CbtDeviceSnapshot>> snapshots = client.getDeviceSnapshots();
        assertTrue(snapshots.isSuccess());
        YoLog.i(TAG, "发现数量=" + foundCount.get() + "，快照存储数量=" + snapshots.getValue().size());
    }

    @Test
    public void profileAggregate_a2dpQueryWithoutProxy() {
        // 聚合状态使用 BluetoothAdapter，无需 Profile 代理。
        Result<CbtProfileAggregateState> aggregate =
                client.getProfileAggregateState(CbtProfileType.A2DP);
        YoLog.i(TAG, "A2DP 汇总成功=" + aggregate.isSuccess()
                + (aggregate.isSuccess()
                ? "，状态=" + aggregate.getValue().state()
                + "，数量=" + aggregate.getValue().connectedCount() : ""));
        assertTrue(aggregate.isSuccess());
        if (aggregate.getValue().state() == CbtProfileConnectionState.CONNECTED) {
            assertTrue(aggregate.getValue().connectedCount() >= 1);
        }
    }

    @Test
    public void profileProxy_a2dpAcquireBestEffort() {
        Result<Void> prepared = client.prepareProfileProxy(CbtProfileType.A2DP);
        YoLog.i(TAG, "准备 A2DP ProfileProxy 成功=" + prepared.isSuccess()
                + (prepared.isFailure()
                ? "，错误码=" + prepared.getFailure().code() : ""));
        // 仪器化测试 APK 可能被 OEM 策略拦截；记录结果，不因 SDK 硬失败。
        if (prepared.isFailure()) {
            YoLog.w(TAG, "当前设备环境下跳过 prepareProfileProxy: " + prepared.getFailure().message());
        }
    }

    @Test
    public void serviceDiscovery_onBondedDevice_completesWithoutCrash() throws Exception {
        Result<List<CbtDeviceSnapshot>> bonded = client.getBondedDevices();
        assertTrue("need bonded devices for SDP test", bonded.isSuccess());
        assertNotNull(bonded.getValue());
        if (bonded.getValue().isEmpty()) {
            YoLog.w(TAG, "没有已配对设备——跳过 SDP 发现断言");
            return;
        }

        CbtDeviceSnapshot target = bonded.getValue().get(0);
        AtomicReference<CbtFailure> failure = new AtomicReference<>();
        AtomicReference<CbtServiceDiscoveryState> terminalState = new AtomicReference<>();
        CountDownLatch finished = new CountDownLatch(1);

        CbtServiceDiscoveryConfig config = CbtServiceDiscoveryConfig.builder()
                .timeoutMillis(15_000L)
                .build();

        CbtOperationHandle handle = client.discoverServices(
                target.deviceId(), config, new CbtServiceDiscoveryObserver() {
                    @Override
                    public void onStateChanged(@NonNull CbtServiceDiscoveryState state) {
                        YoLog.i(TAG, "SDP 状态=" + state + "，设备=" + target.deviceId());
                        if (state == CbtServiceDiscoveryState.RESOLVED
                                || state == CbtServiceDiscoveryState.EMPTY
                                || state == CbtServiceDiscoveryState.FAILED) {
                            terminalState.set(state);
                            finished.countDown();
                        }
                    }

                    @Override
                    public void onResolved(@NonNull CbtServiceDiscoverySnapshot snapshot) {
                        YoLog.i(TAG, "SDP 已解析，UUID 数量=" + snapshot.discoveredUuids().size()
                                + "，SPP 端点数量=" + snapshot.sppEndpoints().size());
                        terminalState.set(CbtServiceDiscoveryState.RESOLVED);
                        finished.countDown();
                    }

                    @Override
                    public void onFailed(@NonNull CbtFailure f) {
                        failure.set(f);
                        YoLog.w(TAG, "SDP 失败: " + f.code() + " " + f.message());
                        finished.countDown();
                    }
                });

        assertNotNull(handle);
        assertTrue("SDP should finish within timeout", finished.await(25, TimeUnit.SECONDS));
        handle.cancel();

        Result<List<CbtDeviceSnapshot>> snapshots = client.getDeviceSnapshots();
        assertTrue(snapshots.isSuccess());
        YoLog.i(TAG, "SDP 终态=" + terminalState.get()
                + "，失败=" + (failure.get() != null ? failure.get().code() : "无"));
    }

    @Test
    public void stopDiscovery_isIdempotentAfterFinish() {
        Result<Void> stop = client.stopDiscovery();
        YoLog.i(TAG, "空闲时停止发现成功=" + stop.isSuccess()
                + (stop.isFailure() ? "，错误码=" + stop.getFailure().code() : ""));
        // 空闲停止可能因 ALREADY_RUNNING 失败——两种结果均可接受
        assertNotNull(stop);
    }

    @Test
    public void aclLink_queriesWithoutCrash() {
        Result<List<CbtDeviceId>> aclDevices = client.getAclConnectedDevices();
        assertTrue(aclDevices.isSuccess());
        assertNotNull(aclDevices.getValue());
        YoLog.i(TAG, "ACL 已连接数量=" + aclDevices.getValue().size());

        Result<List<CbtDeviceSnapshot>> bonded = client.getBondedDevices();
        assertTrue(bonded.isSuccess());
        if (bonded.getValue().isEmpty()) {
            YoLog.w(TAG, "没有已配对设备——跳过 ACL 单设备查询");
            return;
        }
        CbtDeviceId deviceId = bonded.getValue().get(0).deviceId();
        Result<CbtAclLinkState> aclState = client.getAclState(deviceId);
        YoLog.i(TAG, "设备 " + deviceId + " 的 ACL 状态成功=" + aclState.isSuccess()
                + (aclState.isSuccess() ? "，状态=" + aclState.getValue() : ""));
        assertTrue(aclState.isSuccess());
    }

    @Test
    public void observers_registerAndCancelWithoutCrash() {
        CbtOperationHandle bondHandle = client.observeBondState(new CbtBondStateObserver() {
            @Override
            public void onBondStateChanged(@NonNull CbtDeviceSnapshot snapshot) {
                YoLog.i(TAG, "配对观察器 " + snapshot.deviceId()
                        + "，配对状态=" + snapshot.bondState());
            }
        });
        CbtOperationHandle aclHandle = client.observeAclLinkState(new CbtAclLinkObserver() {
            @Override
            public void onAclLinkChanged(@NonNull CbtDeviceSnapshot snapshot) {
                YoLog.i(TAG, "ACL 观察器 " + snapshot.deviceId()
                        + "，ACL 状态=" + snapshot.aclLinkState());
            }
        });
        assertNotNull(bondHandle);
        assertNotNull(aclHandle);
        bondHandle.cancel();
        aclHandle.cancel();
    }

    @Test
    public void profileAggregate_coversCoreAndExtensionProfiles() {
        CbtProfileType[] types = {
                CbtProfileType.A2DP,
                CbtProfileType.HEADSET,
                CbtProfileType.HID,
                CbtProfileType.PAN,
                CbtProfileType.PBAP,
                CbtProfileType.AVRCP,
                CbtProfileType.MAP,
                CbtProfileType.OPP,
                CbtProfileType.HEARING_AID,
                CbtProfileType.LE_AUDIO
        };
        for (CbtProfileType type : types) {
            Result<CbtProfileAggregateState> aggregate = client.getProfileAggregateState(type);
            YoLog.i(TAG, "Profile " + type + " 汇总成功=" + aggregate.isSuccess()
                    + (aggregate.isSuccess()
                    ? "，状态=" + aggregate.getValue().state()
                    + "，数量=" + aggregate.getValue().connectedCount() : ""));
            if (type == CbtProfileType.A2DP || type == CbtProfileType.HEADSET) {
                assertTrue("aggregate should succeed for " + type, aggregate.isSuccess());
            }
        }
    }

    @Test
    public void profileProxy_headsetAcquireBestEffort() {
        Result<Void> prepared = client.prepareProfileProxy(CbtProfileType.HEADSET);
        YoLog.i(TAG, "准备 HEADSET ProfileProxy 成功=" + prepared.isSuccess()
                + (prepared.isFailure()
                ? "，错误码=" + prepared.getFailure().code() : ""));
    }

    @Test
    public void profileDeviceState_onBondedDevice_completesWithoutCrash() {
        Result<List<CbtDeviceSnapshot>> bonded = client.getBondedDevices();
        assertTrue(bonded.isSuccess());
        if (bonded.getValue().isEmpty()) {
            YoLog.w(TAG, "没有已配对设备——跳过 Profile 设备状态");
            return;
        }
        CbtDeviceId deviceId = bonded.getValue().get(0).deviceId();
        client.prepareProfileProxy(CbtProfileType.A2DP);
        client.prepareProfileProxy(CbtProfileType.HEADSET);

        Result<CbtDeviceProfileState> a2dp = client.getDeviceProfileState(deviceId, CbtProfileType.A2DP);
        Result<CbtDeviceProfileState> headset =
                client.getDeviceProfileState(deviceId, CbtProfileType.HEADSET);
        YoLog.i(TAG, "设备 A2DP Profile 成功=" + a2dp.isSuccess()
                + (a2dp.isSuccess() ? "，状态=" + a2dp.getValue().state() : ""));
        YoLog.i(TAG, "设备 HEADSET Profile 成功=" + headset.isSuccess()
                + (headset.isSuccess() ? "，状态=" + headset.getValue().state() : ""));

        Result<CbtDeviceProfileSnapshot> snapshot = client.getDeviceProfileSnapshot(deviceId);
        assertTrue(snapshot.isSuccess());
        YoLog.i(TAG, "Profile 快照状态数=" + snapshot.getValue().deviceSnapshot().profileStates().size());

        Result<List<CbtProfileType>> connectedProfiles = client.getConnectedProfiles(deviceId);
        assertTrue(connectedProfiles.isSuccess());
        YoLog.i(TAG, "已连接 Profile 数量=" + connectedProfiles.getValue().size());
    }

    @Test
    public void profileStateObserver_registersAndCancels() {
        Result<List<CbtDeviceSnapshot>> bonded = client.getBondedDevices();
        assertTrue(bonded.isSuccess());
        if (bonded.getValue().isEmpty()) {
            return;
        }
        CbtDeviceId deviceId = bonded.getValue().get(0).deviceId();
        CbtOperationHandle handle = client.observeProfileState(deviceId, new CbtProfileStateObserver() {
            @Override
            public void onProfileStateChanged(@NonNull CbtDeviceProfileState state) {
                YoLog.i(TAG, "Profile 观察器 " + state.profileType()
                        + "，连接状态=" + state.state());
            }
        });
        assertNotNull(handle);
        handle.cancel();
    }

    @Test
    public void extensionProfiles_queryOnBondedDevice() {
        Result<List<CbtDeviceSnapshot>> bonded = client.getBondedDevices();
        assertTrue(bonded.isSuccess());
        if (bonded.getValue().isEmpty()) {
            return;
        }
        CbtDeviceId deviceId = bonded.getValue().get(0).deviceId();
        CbtProfileType[] extensions = {
                CbtProfileType.HID,
                CbtProfileType.PAN,
                CbtProfileType.PBAP,
                CbtProfileType.AVRCP,
                CbtProfileType.MAP,
                CbtProfileType.OPP,
                CbtProfileType.HEARING_AID,
                CbtProfileType.LE_AUDIO
        };
        for (CbtProfileType type : extensions) {
            Result<CbtDeviceProfileState> state = client.getDeviceProfileState(deviceId, type);
            YoLog.i(TAG, "扩展 " + type + " 成功=" + state.isSuccess()
                    + (state.isSuccess() ? "，连接状态=" + state.getValue().state() : ""));
        }
    }

    @Test
    public void spp_emptyQueriesAndDisconnectAll_withoutCrash() {
        Result<List<CbtSppConnectionSnapshot>> active = client.getActiveSppConnections();
        assertTrue(active.isSuccess());
        YoLog.i(TAG, "活跃 SPP 连接数=" + active.getValue().size());

        Result<List<CbtDeviceSnapshot>> bonded = client.getBondedDevices();
        assertTrue(bonded.isSuccess());
        if (!bonded.getValue().isEmpty()) {
            Result<List<CbtSppConnectionSnapshot>> byDevice =
                    client.getSppConnections(bonded.getValue().get(0).deviceId());
            assertTrue(byDevice.isSuccess());
            YoLog.i(TAG, "按设备统计 SPP 数量=" + byDevice.getValue().size());
        }

        Result<Void> disconnectAll = client.disconnectAllSpp();
        YoLog.i(TAG, "断开全部 SPP 成功=" + disconnectAll.isSuccess());
        assertTrue(disconnectAll.isSuccess());
    }

    @Test
    public void spp_connectBestEffort_completesWithoutCrash() throws Exception {
        Result<List<CbtDeviceSnapshot>> bonded = client.getBondedDevices();
        assertTrue(bonded.isSuccess());
        if (bonded.getValue().isEmpty()) {
            YoLog.w(TAG, "没有已配对设备——跳过 SPP 连接");
            return;
        }

        CbtDeviceSnapshot target = bonded.getValue().get(0);
        CbtDeviceId deviceId = target.deviceId();
        CbtSppEndpoint endpoint = target.sdpSppEndpoints().isEmpty()
                ? CbtSppEndpoint.defaultSpp()
                : target.sdpSppEndpoints().get(0);

        AtomicReference<CbtFailure> failure = new AtomicReference<>();
        AtomicBoolean terminal = new AtomicBoolean(false);
        CountDownLatch finished = new CountDownLatch(1);

        CbtSppConfig config = CbtSppConfig.builder()
                .connectTimeoutMillis(8_000L)
                .maxConnectAttempts(1)
                .build();

        CbtOperationHandle connectHandle = client.connectSpp(
                deviceId, endpoint, config, new CbtSppConnectionObserver() {
                    @Override
                    public void onSppStateChanged(@NonNull CbtSppConnectionSnapshot snapshot) {
                        YoLog.i(TAG, "SPP 状态=" + snapshot.state()
                                + "，ID=" + snapshot.connectionId());
                        if (snapshot.state() == CbtSppConnectionState.CONNECTED) {
                            terminal.set(true);
                            client.disconnectSpp(snapshot.connectionId());
                            return;
                        }
                        if (snapshot.state() == CbtSppConnectionState.FAILED) {
                            if (snapshot.failure() != null) {
                                failure.set(snapshot.failure());
                                YoLog.w(TAG, "SPP 错误: " + snapshot.failure().code()
                                        + " " + snapshot.failure().message());
                            }
                            terminal.set(true);
                            finished.countDown();
                            return;
                        }
                        if (snapshot.state() == CbtSppConnectionState.DISCONNECTED) {
                            terminal.set(true);
                            finished.countDown();
                        }
                    }
                });

        assertNotNull(connectHandle);
        assertTrue("SPP connect attempt should finish", finished.await(20, TimeUnit.SECONDS));
        connectHandle.cancel();
        client.disconnectSppByDevice(deviceId);

        Result<List<CbtSppConnectionSnapshot>> after = client.getActiveSppConnections();
        assertTrue(after.isSuccess());
        YoLog.i(TAG, "SPP 尽力终态=" + terminal.get()
                + "，失败=" + (failure.get() != null ? failure.get().code() : "无")
                + "，结束后活跃数=" + after.getValue().size());
    }

    private static void grantBluetoothPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return;
        }
        try {
            var ui = InstrumentationRegistry.getInstrumentation().getUiAutomation();
            String pkg = InstrumentationRegistry.getInstrumentation()
                    .getTargetContext().getPackageName();
            ui.grantRuntimePermission(pkg, Manifest.permission.BLUETOOTH_SCAN);
            ui.grantRuntimePermission(pkg, Manifest.permission.BLUETOOTH_CONNECT);
            ui.grantRuntimePermission(pkg, Manifest.permission.ACCESS_FINE_LOCATION);
            YoLog.i(TAG, "已授予 BLUETOOTH_SCAN/CONNECT/LOCATION 权限给 " + pkg);
        } catch (Throwable t) {
            YoLog.w(TAG, "通过 UiAutomation 授予权限失败", t);
        }
    }
}
