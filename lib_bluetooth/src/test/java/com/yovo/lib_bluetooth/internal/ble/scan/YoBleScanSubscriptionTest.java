package com.yovo.lib_bluetooth.internal.ble.scan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.yovo.lib_bluetooth.api.BleScanFrameFilter;
import com.yovo.lib_bluetooth.api.BleScanSubscription;
import com.yovo.lib_bluetooth.model.ble.BleDeviceId;
import com.yovo.lib_bluetooth.model.ble.BleDeviceIdentity;
import com.yovo.lib_bluetooth.model.ble.BleOperationHandle;
import com.yovo.lib_bluetooth.model.ble.BleScanObserver;
import com.yovo.lib_bluetooth.model.ble.BleScanResult;
import com.yovo.lib_bluetooth.model.common.Result;

import org.junit.After;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 订阅级帧过滤：谓词热替换只换对象，绝不动扫描生命周期。
 *
 * <p>验收对应 {@code scan-architecture.md} §2.2 / §7.4：连续替换 5 次谓词，引擎启动次数不变。</p>
 */
public final class YoBleScanSubscriptionTest {

    @After
    public void resetHub() {
        YoBleScanHub.resetForTest();
    }

    @Test
    public void swappingFrameFilterFiveTimesNeverRestartsTheEngine() {
        RecordingEngine engine = new RecordingEngine();
        BleScanSubscription subscription = YoBleScanHub.subscribe(engine, new BleScanObserver() {
        });

        int startsAfterSubscribe = engine.starts.get();
        int stopsAfterSubscribe = engine.stops.get();
        for (int i = 0; i < 5; i++) {
            final int threshold = -40 - i;
            subscription.setFrameFilter(frame -> frame.rssi() > threshold);
        }

        assertEquals("替换谓词不得启动引擎", startsAfterSubscribe, engine.starts.get());
        assertEquals("替换谓词不得停止引擎", stopsAfterSubscribe, engine.stops.get());
        assertTrue("订阅句柄仍可取消", !subscription.isCancelled());
    }

    @Test
    public void frameFilterOnlyDeliversMatchingFrames() {
        RecordingEngine engine = new RecordingEngine();
        List<Integer> delivered = new ArrayList<>();
        YoBleScanHub.subscribe(engine, new BleScanObserver() {
            @Override
            public void onDeviceFound(BleScanResult result) {
                delivered.add(result.rssi());
            }
        }).setFrameFilter(frame -> frame.rssi() > -60);

        engine.fanout.onDeviceFound(frame(-50));
        engine.fanout.onDeviceFound(frame(-70));

        assertEquals(1, delivered.size());
        assertEquals(Integer.valueOf(-50), delivered.get(0));
    }

    @Test
    public void replacingTheFilterTakesEffectForSubsequentFramesOnly() {
        RecordingEngine engine = new RecordingEngine();
        List<Integer> delivered = new ArrayList<>();
        BleScanSubscription subscription = YoBleScanHub.subscribe(engine, new BleScanObserver() {
            @Override
            public void onDeviceFound(BleScanResult result) {
                delivered.add(result.rssi());
            }
        });

        subscription.setFrameFilter(frame -> frame.rssi() > -60);
        engine.fanout.onDeviceFound(frame(-70));

        subscription.setFrameFilter(frame -> frame.rssi() > -80);
        engine.fanout.onDeviceFound(frame(-70));

        assertEquals("第一帧被旧谓词丢弃，第二帧被新谓词放行", 1, delivered.size());
        assertEquals(Integer.valueOf(-70), delivered.get(0));
    }

    @Test
    public void nullFilterDeliversEveryFrame() {
        RecordingEngine engine = new RecordingEngine();
        AtomicInteger delivered = new AtomicInteger();
        BleScanSubscription subscription = YoBleScanHub.subscribe(engine, new BleScanObserver() {
            @Override
            public void onDeviceFound(BleScanResult result) {
                delivered.incrementAndGet();
            }
        });

        subscription.setFrameFilter(frame -> false);
        engine.fanout.onDeviceFound(frame(-90));
        assertEquals("谓词拒绝后不交付", 0, delivered.get());

        subscription.setFrameFilter(null);
        engine.fanout.onDeviceFound(frame(-90));
        assertEquals("默认（null）谓词放行全部", 1, delivered.get());
    }

    @Test
    public void batchFramesAreFilteredPerSubscription() {
        RecordingEngine engine = new RecordingEngine();
        List<Integer> delivered = new ArrayList<>();
        YoBleScanHub.subscribe(engine, new BleScanObserver() {
            @Override
            public void onBatchScanResults(List<BleScanResult> results) {
                for (BleScanResult result : results) {
                    delivered.add(result.rssi());
                }
            }
        }).setFrameFilter(frame -> frame.rssi() > -60);

        List<BleScanResult> batch = new ArrayList<>();
        batch.add(frame(-50));
        batch.add(frame(-70));
        batch.add(frame(-40));
        engine.fanout.onBatchScanResults(batch);

        assertEquals(2, delivered.size());
        assertTrue(delivered.contains(-50));
        assertTrue(delivered.contains(-40));
    }

    @Test
    public void lostEventsFollowTheSameFrameFilter() {
        RecordingEngine engine = new RecordingEngine();
        AtomicInteger lost = new AtomicInteger();
        YoBleScanHub.subscribe(engine, new BleScanObserver() {
            @Override
            public void onScanResultLost(BleScanResult result) {
                lost.incrementAndGet();
            }
        }).setFrameFilter(frame -> frame.rssi() > -60);

        engine.fanout.onScanResultLost(frame(-70));
        engine.fanout.onScanResultLost(frame(-50));

        assertEquals("被谓词过滤掉的帧，其消失事件同样不交付", 1, lost.get());
    }

    @Test
    public void cancelStillDetachesWhileAFilterIsInstalled() throws Exception {
        RecordingEngine engine = new RecordingEngine();
        BleScanSubscription subscription = YoBleScanHub.subscribe(engine, new BleScanObserver() {
        });
        subscription.setFrameFilter(frame -> true);
        assertEquals(1, engine.starts.get());

        subscription.cancel();
        awaitSessionTransitions();

        assertEquals(1, engine.stops.get());
        assertTrue(subscription.isCancelled());
        assertFalse(engine.running());
    }

    private static BleScanResult frame(int rssi) {
        return new BleScanResult(
                new BleDeviceIdentity(new BleDeviceId("AA:BB:CC:00:00:01"), "dev", 0L),
                rssi,
                0L);
    }

    private static void awaitSessionTransitions() throws Exception {
        java.util.concurrent.CountDownLatch barrier = YoBleScanHub.submitTransitionBarrierForTest();
        assertTrue("会话迁移未在超时内排空", barrier.await(5L, java.util.concurrent.TimeUnit.SECONDS));
    }

    /** 记录引擎启动/停止次数的假引擎。 */
    private static final class RecordingEngine implements YoBleScanHub.Engine {

        final AtomicInteger starts = new AtomicInteger();
        final AtomicInteger stops = new AtomicInteger();
        volatile BleScanObserver fanout;
        private volatile boolean running;

        boolean running() {
            return running;
        }

        @Override
        public BleOperationHandle start(BleScanObserver fanout) {
            this.fanout = fanout;
            starts.incrementAndGet();
            running = true;
            return BleOperationHandle.completed("scan");
        }

        @Override
        public Result<Void> stop() {
            stops.incrementAndGet();
            running = false;
            return Result.success(null);
        }
    }
}
