package com.yovo.lib_bluetooth.internal.common.bluetooth.radio;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link YoBtRadioCoordinator} 单元测试：验证租约互斥、状态流转、并发安全及连接抢占逻辑。
 */
public class YoBtRadioCoordinatorTest {

    private static class FakeRadioPlatform implements YoBtRadioPlatform {
        boolean enabled = true;
        boolean discovering = false;
        int cancelDiscoveryCount = 0;
        int startDiscoveryCount = 0;

        @Override
        public boolean isAdapterEnabled() {
            return enabled;
        }

        @Override
        public boolean isDiscovering() {
            return discovering;
        }

        @Override
        public boolean cancelDiscovery() {
            cancelDiscoveryCount++;
            discovering = false;
            return true;
        }

        @Override
        public boolean startDiscovery() {
            startDiscoveryCount++;
            discovering = true;
            return true;
        }
    }

    private FakeRadioPlatform platform;
    private YoBtRadioCoordinator coordinator;

    @Before
    public void setUp() {
        platform = new FakeRadioPlatform();
        // 设置 settleDelayMs 为 0，避免单测睡眠耗时
        coordinator = new YoBtRadioCoordinator(platform, 0L);
    }

    @Test
    public void testInitialStateIsIdle() {
        assertEquals(YoBtRadioState.IDLE, coordinator.getState());
        assertTrue(coordinator.getActiveLeases().isEmpty());
        assertTrue(coordinator.isClassicDiscoveryAllowed());
        assertFalse(coordinator.hasActiveLease(YoBtRadioLease.Type.BLE_SCAN));
        assertFalse(coordinator.hasActiveLease(YoBtRadioLease.Type.CLASSIC_DISCOVERY));
        assertFalse(coordinator.hasActiveLease(YoBtRadioLease.Type.CONNECTION));
    }

    @Test
    public void testBleScanLeaseAcquisitionAndRelease() {
        YoBtRadioLease lease = coordinator.acquireLease(YoBtRadioLease.Type.BLE_SCAN, "test-ble-scanner");
        assertNotNull(lease);
        assertTrue(lease.isActive());
        assertEquals(YoBtRadioLease.Type.BLE_SCAN, lease.getType());
        assertEquals("test-ble-scanner", lease.getHolder());
        assertEquals(YoBtRadioState.BLE_SCANNING, coordinator.getState());
        assertTrue(coordinator.hasActiveLease(YoBtRadioLease.Type.BLE_SCAN));

        lease.release();
        assertFalse(lease.isActive());
        assertTrue(lease.isReleased());
        assertEquals(YoBtRadioState.IDLE, coordinator.getState());
        assertFalse(coordinator.hasActiveLease(YoBtRadioLease.Type.BLE_SCAN));
    }

    @Test
    public void testClassicDiscoveryLeaseAcquisitionAndRelease() {
        YoBtRadioLease lease = coordinator.acquireLease(YoBtRadioLease.Type.CLASSIC_DISCOVERY, "test-cbt-discovery");
        assertNotNull(lease);
        assertTrue(lease.isActive());
        assertEquals(YoBtRadioLease.Type.CLASSIC_DISCOVERY, lease.getType());
        assertEquals(YoBtRadioState.CLASSIC_DISCOVERING, coordinator.getState());
        assertTrue(coordinator.hasActiveLease(YoBtRadioLease.Type.CLASSIC_DISCOVERY));

        lease.release();
        assertEquals(YoBtRadioState.IDLE, coordinator.getState());
    }

    @Test
    public void testClassicDiscoverySuspendsBleScanAndResumesOnRelease() {
        AtomicInteger suspendCount = new AtomicInteger(0);
        AtomicInteger resumeCount = new AtomicInteger(0);

        coordinator.registerBleScanHook(new YoBtRadioCoordinator.BleScanCoordinationHook() {
            @Override
            public void onSuspendBleScan() {
                suspendCount.incrementAndGet();
            }

            @Override
            public void onResumeBleScan() {
                resumeCount.incrementAndGet();
            }
        });

        // 1. 先启动 BLE 扫描
        YoBtRadioLease bleLease = coordinator.acquireLease(YoBtRadioLease.Type.BLE_SCAN, "ble-1");
        assertNotNull(bleLease);
        assertEquals(YoBtRadioState.BLE_SCANNING, coordinator.getState());
        assertEquals(0, suspendCount.get());

        // 2. 经典扫描申请射频，应该挂起 BLE 扫描
        YoBtRadioLease classicLease = coordinator.acquireLease(YoBtRadioLease.Type.CLASSIC_DISCOVERY, "classic-1");
        assertNotNull(classicLease);
        assertEquals(1, suspendCount.get());
        assertEquals(YoBtRadioState.CLASSIC_DISCOVERING, coordinator.getState());

        // 3. 经典扫描释放，应该自动恢复 BLE 扫描
        classicLease.release();
        assertEquals(1, resumeCount.get());
        assertEquals(YoBtRadioState.BLE_SCANNING, coordinator.getState());

        // 4. BLE 扫描最终停止
        bleLease.release();
        assertEquals(YoBtRadioState.IDLE, coordinator.getState());
    }

    @Test
    public void testBleScanCancelsClassicDiscovery() {
        platform.discovering = true;
        YoBtRadioLease classicLease = coordinator.acquireLease(YoBtRadioLease.Type.CLASSIC_DISCOVERY, "classic-inquiry");
        assertNotNull(classicLease);
        assertEquals(YoBtRadioState.CLASSIC_DISCOVERING, coordinator.getState());

        List<String> preemptReasons = new ArrayList<>();
        coordinator.addObserver(new YoBtRadioStateObserver() {
            @Override
            public void onDiscoveryPreempted(String reason) {
                preemptReasons.add(reason);
            }
        });

        // BLE 申请扫描：必须强制协调取消经典发现
        YoBtRadioLease bleLease = coordinator.acquireLease(YoBtRadioLease.Type.BLE_SCAN, "ble-high-priority");
        assertNotNull(bleLease);
        assertEquals(YoBtRadioState.BLE_SCANNING, coordinator.getState());
        assertFalse(classicLease.isActive());
        assertTrue(platform.cancelDiscoveryCount > 0);
        assertEquals(1, preemptReasons.size());
        assertTrue(preemptReasons.get(0).contains("BLE scan acquired"));

        bleLease.release();
        assertEquals(YoBtRadioState.IDLE, coordinator.getState());
    }

    @Test
    public void testPreemptForConnectionCancelsDiscoveryAndBlocksNewDiscovery() {
        platform.discovering = true;
        YoBtRadioLease classicLease = coordinator.acquireLease(YoBtRadioLease.Type.CLASSIC_DISCOVERY, "classic-inquiry");
        assertNotNull(classicLease);

        List<String> preemptReasons = new ArrayList<>();
        coordinator.addObserver(new YoBtRadioStateObserver() {
            @Override
            public void onDiscoveryPreempted(String reason) {
                preemptReasons.add(reason);
            }
        });

        // 发起连接抢占
        YoBtRadioLease connLease = coordinator.preemptForConnection("GattConnect:AA:BB:CC:DD:EE:FF", 50L);
        assertNotNull(connLease);
        assertEquals(YoBtRadioState.CONNECTING, coordinator.getState());
        assertFalse(classicLease.isActive());
        assertTrue(platform.cancelDiscoveryCount > 0);
        assertFalse(coordinator.isClassicDiscoveryAllowed());

        // 在连接建立期间，再次申请 CLASSIC_DISCOVERY 必须被拒绝（返回 null）
        YoBtRadioLease rejectedLease = coordinator.acquireLease(YoBtRadioLease.Type.CLASSIC_DISCOVERY, "new-discovery");
        assertNull(rejectedLease);

        // 连接完成后释放租约
        connLease.release();
        assertEquals(YoBtRadioState.IDLE, coordinator.getState());
        assertTrue(coordinator.isClassicDiscoveryAllowed());
    }

    @Test
    public void testMultipleConcurrentConnectionLeases() {
        YoBtRadioLease conn1 = coordinator.preemptForConnection("GattConnect:Device1", 10L);
        YoBtRadioLease conn2 = coordinator.preemptForConnection("SppConnect:Device2", 10L);

        assertEquals(YoBtRadioState.CONNECTING, coordinator.getState());
        assertEquals(2, coordinator.getActiveLeases().size());
        assertFalse(coordinator.isClassicDiscoveryAllowed());

        conn1.release();
        // conn2 仍在连接中，状态仍为 CONNECTING
        assertEquals(YoBtRadioState.CONNECTING, coordinator.getState());
        assertEquals(1, coordinator.getActiveLeases().size());
        assertFalse(coordinator.isClassicDiscoveryAllowed());

        conn2.release();
        assertEquals(YoBtRadioState.IDLE, coordinator.getState());
        assertTrue(coordinator.getActiveLeases().isEmpty());
        assertTrue(coordinator.isClassicDiscoveryAllowed());
    }

    @Test
    public void testObserverStateTransitions() {
        List<String> transitions = new ArrayList<>();
        coordinator.addObserver(new YoBtRadioStateObserver() {
            @Override
            public void onRadioStateChanged(YoBtRadioState oldState, YoBtRadioState newState) {
                transitions.add(oldState + "->" + newState);
            }
        });

        YoBtRadioLease ble = coordinator.acquireLease(YoBtRadioLease.Type.BLE_SCAN, "ble");
        YoBtRadioLease conn = coordinator.preemptForConnection("conn", 10L);
        conn.release();
        ble.release();

        assertEquals(4, transitions.size());
        assertEquals("IDLE->BLE_SCANNING", transitions.get(0));
        assertEquals("BLE_SCANNING->CONNECTING", transitions.get(1));
        assertEquals("CONNECTING->BLE_SCANNING", transitions.get(2));
        assertEquals("BLE_SCANNING->IDLE", transitions.get(3));
    }

    @Test
    public void testConcurrentMultithreadedLeaseStress() throws InterruptedException {
        int threadCount = 8;
        int operationsPerThread = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger errorCount = new AtomicInteger(0);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.execute(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < operationsPerThread; i++) {
                        int mod = (threadId + i) % 3;
                        YoBtRadioLease lease;
                        if (mod == 0) {
                            lease = coordinator.acquireLease(YoBtRadioLease.Type.BLE_SCAN, "thread-" + threadId);
                        } else if (mod == 1) {
                            lease = coordinator.acquireLease(YoBtRadioLease.Type.CLASSIC_DISCOVERY, "thread-" + threadId);
                        } else {
                            lease = coordinator.preemptForConnection("thread-" + threadId, 0L);
                        }
                        if (lease != null) {
                            // 短暂持有后释放
                            lease.release();
                        }
                    }
                } catch (Throwable e) {
                    errorCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        assertEquals(0, errorCount.get());
        // 最终所有租约都已释放，回到 IDLE 状态
        assertEquals(YoBtRadioState.IDLE, coordinator.getState());
        assertTrue(coordinator.getActiveLeases().isEmpty());
    }

    @Test
    public void testYoBtRadioLeaseIdempotentRelease() {
        AtomicInteger releaseCallbackCount = new AtomicInteger(0);
        YoBtRadioLease lease = new YoBtRadioLease(
                "lease-test",
                YoBtRadioLease.Type.BLE_SCAN,
                "holder",
                System.currentTimeMillis(),
                l -> releaseCallbackCount.incrementAndGet());

        assertTrue(lease.isActive());
        assertFalse(lease.isReleased());

        lease.release();
        assertFalse(lease.isActive());
        assertTrue(lease.isReleased());
        assertEquals(1, releaseCallbackCount.get());

        // 重复释放，callback 不应再次触发
        lease.release();
        lease.close();
        assertEquals(1, releaseCallbackCount.get());
    }

    @Test
    public void testAutoCloseableLease() {
        AtomicBoolean closed = new AtomicBoolean(false);
        try (YoBtRadioLease lease = new YoBtRadioLease(
                "auto-close",
                YoBtRadioLease.Type.CONNECTION,
                "holder",
                System.currentTimeMillis(),
                l -> closed.set(true))) {
            assertTrue(lease.isActive());
        }
        assertTrue(closed.get());
    }
}
