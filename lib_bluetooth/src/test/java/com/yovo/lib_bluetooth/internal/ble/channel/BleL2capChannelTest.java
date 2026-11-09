package com.yovo.lib_bluetooth.internal.ble.channel;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.yovo.lib_bluetooth.api.YoBleClient;
import com.yovo.lib_bluetooth.internal.ble.BleClientImpl;
import com.yovo.lib_bluetooth.internal.ble.YoBleModuleCore;
import com.yovo.lib_bluetooth.internal.ble.device.YoBleCapabilityInspector;
import com.yovo.lib_bluetooth.internal.ble.runtime.YoBleRuntimeConfig;
import com.yovo.lib_bluetooth.internal.ble.testing.FakeYoBleBondPlatform;
import com.yovo.lib_bluetooth.internal.ble.testing.FakeYoBleConnectionPlatform;
import com.yovo.lib_bluetooth.internal.ble.testing.FakeYoBleEnvironmentPort;
import com.yovo.lib_bluetooth.internal.ble.testing.FakeYoBleHarness;
import com.yovo.lib_bluetooth.internal.ble.testing.FakeYoBleL2capPlatform;
import com.yovo.lib_bluetooth.internal.ble.testing.FakeYoBlePeripheralPlatform;
import com.yovo.lib_bluetooth.internal.ble.testing.FakeYoBleScanPlatform;
import com.yovo.lib_bluetooth.internal.common.permission.YoBluetoothPermission;
import com.yovo.lib_bluetooth.model.ble.BleConfig;
import com.yovo.lib_bluetooth.model.ble.BleDeviceId;
import com.yovo.lib_bluetooth.model.ble.BleErrorCode;
import com.yovo.lib_bluetooth.model.ble.BleFailure;
import com.yovo.lib_bluetooth.model.ble.BleL2capChannelHandle;
import com.yovo.lib_bluetooth.model.ble.BleL2capChannelObserver;
import com.yovo.lib_bluetooth.model.ble.BleOperationHandle;
import com.yovo.lib_bluetooth.model.common.Result;

import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * BLE-024 L2CAP CoC 面向连接高速字节流通道契约验证（基于真实后台线程池 + FakeSocket）。
 */
public class BleL2capChannelTest {

    @BeforeClass
    public static void installTestLogger() {
        FakeYoBleHarness.ensureTestLoggerInstalled();
    }

    private static final class AsyncL2capEnv {
        final FakeYoBleEnvironmentPort env = new FakeYoBleEnvironmentPort();
        final FakeYoBleL2capPlatform l2capPlatform = new FakeYoBleL2capPlatform();
        final YoBleModuleCore core;
        final YoBleClient client;

        AsyncL2capEnv() {
            YoBleRuntimeConfig runtimeCfg = YoBleRuntimeConfig.builder()
                    .callbackOnMainThread(false)
                    .shutdownTimeoutSeconds(2L)
                    .build();
            this.core = new YoBleModuleCore(
                    BleConfig.builder().callbackOnMainThread(false).build(),
                    runtimeCfg,
                    new YoBleCapabilityInspector(env),
                    new FakeYoBleScanPlatform(),
                    new FakeYoBleConnectionPlatform(),
                    new FakeYoBleBondPlatform(),
                    l2capPlatform,
                    new FakeYoBlePeripheralPlatform(),
                    System::currentTimeMillis);
            this.client = new BleClientImpl(core);
        }

        void release() {
            client.release();
        }
    }

    @Test
    public void preflightChecks_apiBelow29_missingConnect_andOpenFailure() throws InterruptedException {
        AsyncL2capEnv harness = new AsyncL2capEnv();
        BleDeviceId id = new BleDeviceId("AA:BB:CC:DD:EE:71");
        try {
            // 1. API 28 (<29) 优雅降级返回 UNSUPPORTED_OPERATION
            harness.env.setSdkInt(28);
            BleFailure apiErr = awaitOpenFailure(harness.client, id, 0x0081, true);
            assertEquals(BleErrorCode.UNSUPPORTED_OPERATION, apiErr.errorCode());

            // 2. API 29 缺少 CONNECT 权限前检拦截
            harness.env.setSdkInt(29);
            harness.env.deny(YoBluetoothPermission.CONNECT);
            BleFailure permErr = awaitOpenFailure(harness.client, id, 0x0081, true);
            assertEquals(BleErrorCode.PERMISSION_CONNECT_DENIED, permErr.errorCode());

            // 3. 非法 MAC 与非法 PSM
            harness.env.grantAll();
            assertEquals(BleErrorCode.DEVICE_INVALID,
                    awaitOpenFailure(harness.client, new BleDeviceId("BAD"), 0x0081, false).errorCode());
            assertEquals(BleErrorCode.ILLEGAL_ARGUMENT,
                    awaitOpenFailure(harness.client, id, 0, false).errorCode());

            // 4. 底层 openClientChannel 抛 IOException -> L2CAP_OPEN_FAILED
            harness.l2capPlatform.setOpenFailure(new IOException("Connection refused"));
            assertEquals(BleErrorCode.L2CAP_OPEN_FAILED,
                    awaitOpenFailure(harness.client, id, 0x0081, true).errorCode());
        } finally {
            harness.release();
        }
    }

    @Test
    public void openWriteReceiveAndPeerEofClose_preservesOrderAndCleansState() throws InterruptedException {
        AsyncL2capEnv harness = new AsyncL2capEnv();
        BleDeviceId id = new BleDeviceId("AA:BB:CC:DD:EE:72");
        try {
            harness.env.setSdkInt(30);
            CountDownLatch openedLatch = new CountDownLatch(1);
            CountDownLatch rxLatch = new CountDownLatch(2);
            CountDownLatch closedLatch = new CountDownLatch(1);
            AtomicReference<BleL2capChannelHandle> handleRef = new AtomicReference<>();
            List<byte[]> received = Collections.synchronizedList(new ArrayList<>());

            harness.client.openL2capChannel(id, 0x0085, true, new BleL2capChannelObserver() {
                @Override
                public void onChannelOpened(BleL2capChannelHandle channel) {
                    handleRef.set(channel);
                    openedLatch.countDown();
                }

                @Override
                public void onDataReceived(byte[] chunk) {
                    received.add(chunk);
                    rxLatch.countDown();
                }

                @Override
                public void onChannelClosed() {
                    closedLatch.countDown();
                }
            });

            assertTrue(openedLatch.await(3, TimeUnit.SECONDS));
            BleL2capChannelHandle handle = handleRef.get();
            assertNotNull(handle);
            assertTrue(handle.isOpen());
            assertEquals(0x0085, handle.psm());
            assertEquals(0x0085, harness.l2capPlatform.lastPsm());
            assertTrue(harness.l2capPlatform.lastSecure());

            // 写入多块流验证顺序与内容 100% 一致
            byte[] c1 = new byte[]{0x01, 0x02, 0x03};
            byte[] c2 = "STREAM_CHUNK_2".getBytes(StandardCharsets.UTF_8);
            byte[] c3 = new byte[128];
            for (int i = 0; i < c3.length; i++) {
                c3[i] = (byte) i;
            }
            assertTrue(handle.write(c1).isSuccess());
            assertTrue(handle.write(c2).isSuccess());
            assertTrue(handle.write(c3).isSuccess());

            List<byte[]> sent = harness.l2capPlatform.lastSocket().writtenChunks();
            assertEquals(3, sent.size());
            assertArrayEquals(c1, sent.get(0));
            assertArrayEquals(c2, sent.get(1));
            assertArrayEquals(c3, sent.get(2));

            // 对端推送回包并随后触发 EOF
            FakeYoBleL2capPlatform.FakeSocket socket = harness.l2capPlatform.lastSocket();
            socket.pushInbound("HELLO".getBytes(StandardCharsets.UTF_8));
            socket.pushInbound("WORLD".getBytes(StandardCharsets.UTF_8));
            assertTrue(rxLatch.await(3, TimeUnit.SECONDS));
            assertEquals(2, received.size());
            assertEquals("HELLO", new String(received.get(0), StandardCharsets.UTF_8));
            assertEquals("WORLD", new String(received.get(1), StandardCharsets.UTF_8));

            socket.pushEof();
            assertTrue(closedLatch.await(3, TimeUnit.SECONDS));
            assertFalse(handle.isOpen());

            Result<Void> postCloseWrite = handle.write(new byte[]{0x01});
            assertTrue(postCloseWrite.isFailure());
            assertEquals(BleErrorCode.ILLEGAL_STATE.name(), postCloseWrite.getFailure().code());
        } finally {
            harness.release();
        }
    }

    @Test
    public void idempotentCloseAndClientRelease_closesUnderlyingSocket() throws InterruptedException {
        AsyncL2capEnv harness = new AsyncL2capEnv();
        BleDeviceId id = new BleDeviceId("AA:BB:CC:DD:EE:73");
        try {
            harness.env.setSdkInt(31);
            CountDownLatch opened1 = new CountDownLatch(1);
            AtomicReference<BleL2capChannelHandle> h1 = new AtomicReference<>();
            harness.client.openL2capChannel(id, 0x0090, false, new BleL2capChannelObserver() {
                @Override
                public void onChannelOpened(BleL2capChannelHandle channel) {
                    h1.set(channel);
                    opened1.countDown();
                }
            });
            assertTrue(opened1.await(3, TimeUnit.SECONDS));
            assertFalse(harness.l2capPlatform.lastSecure());

            h1.get().close();
            h1.get().close();
            assertFalse(h1.get().isOpen());
            assertTrue(harness.l2capPlatform.lastSocket().isClosed());

            CountDownLatch opened2 = new CountDownLatch(1);
            AtomicReference<BleL2capChannelHandle> h2 = new AtomicReference<>();
            harness.client.openL2capChannel(id, 0x0091, false, new BleL2capChannelObserver() {
                @Override
                public void onChannelOpened(BleL2capChannelHandle channel) {
                    h2.set(channel);
                    opened2.countDown();
                }
            });
            assertTrue(opened2.await(3, TimeUnit.SECONDS));
            FakeYoBleL2capPlatform.FakeSocket socket2 = harness.l2capPlatform.lastSocket();

            harness.client.release();
            assertTrue(socket2.isClosed());
            assertFalse(h2.get().isOpen());
        } finally {
            harness.release();
        }
    }

    @Test
    public void cancelWhileOpenBlocked_closesHookUnblocksIoAndEmitsCancelledOnce()
            throws InterruptedException {
        AsyncL2capEnv harness = new AsyncL2capEnv();
        BleDeviceId id = new BleDeviceId("AA:BB:CC:DD:EE:74");
        try {
            harness.env.setSdkInt(31);
            CountDownLatch openStarted = new CountDownLatch(1);
            harness.l2capPlatform.setBlockOpenUntilHookClosed(true, openStarted);

            CountDownLatch failLatch = new CountDownLatch(1);
            List<BleFailure> failures = Collections.synchronizedList(new ArrayList<>());
            BleOperationHandle handle = harness.client.openL2capChannel(
                    id, 0x0092, true, new BleL2capChannelObserver() {
                        @Override
                        public void onChannelOpened(BleL2capChannelHandle channel) {
                            throw new AssertionError("Cancelled blocked open must not report opened");
                        }

                        @Override
                        public void onFailed(BleFailure failure) {
                            failures.add(failure);
                            failLatch.countDown();
                        }
                    });

            assertTrue("openClientChannel should have started blocking",
                    openStarted.await(3, TimeUnit.SECONDS));
            handle.cancel();
            assertTrue("Cancel should emit OPERATION_CANCELLED promptly",
                    failLatch.await(3, TimeUnit.SECONDS));
            assertTrue("Cancel must close socket hook immediately to unblock IO thread",
                    harness.l2capPlatform.isLastOpenHookClosed());

            // 短暂等待 IO 线程退出，确保不会重复回调 L2CAP_OPEN_FAILED
            Thread.sleep(100L);
            assertEquals(1, failures.size());
            assertEquals(BleErrorCode.OPERATION_CANCELLED, failures.get(0).errorCode());
        } finally {
            harness.release();
        }
    }

    private static BleFailure awaitOpenFailure(YoBleClient client,
                                               BleDeviceId id,
                                               int psm,
                                               boolean secure) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<BleFailure> ref = new AtomicReference<>();
        client.openL2capChannel(id, psm, secure, new BleL2capChannelObserver() {
            @Override
            public void onChannelOpened(BleL2capChannelHandle channel) {
            }

            @Override
            public void onFailed(BleFailure failure) {
                ref.set(failure);
                latch.countDown();
            }
        });
        assertTrue(latch.await(3, TimeUnit.SECONDS));
        return ref.get();
    }
}
