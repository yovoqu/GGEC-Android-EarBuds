package com.yovo.lib_bluetooth.internal.ble.scan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.yovo.lib_bluetooth.model.ble.BleDeviceId;
import com.yovo.lib_bluetooth.model.ble.BleDeviceIdentity;
import com.yovo.lib_bluetooth.model.ble.BleErrorCode;
import com.yovo.lib_bluetooth.model.ble.BleFailure;
import com.yovo.lib_bluetooth.model.ble.BleOperationHandle;
import com.yovo.lib_bluetooth.model.ble.BleScanObserver;
import com.yovo.lib_bluetooth.model.ble.BleScanResult;
import com.yovo.lib_bluetooth.model.common.Result;

import org.junit.After;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class YoBleScanHubTest {

    @After
    public void resetHub() {
        YoBleScanHub.resetForTest();
    }

    @Test
    public void oneEngineServesEverySubscriberUntilTheLastCancels() {
        RecordingEngine engine = new RecordingEngine();
        BleScanObserver first = new BleScanObserver() {
        };
        BleScanObserver second = new BleScanObserver() {
        };

        BleOperationHandle firstHandle = YoBleScanHub.subscribe(engine, first);
        BleOperationHandle secondHandle = YoBleScanHub.subscribe(engine, second);
        assertEquals(1, engine.starts.get());
        assertEquals(0, engine.stops.get());

        firstHandle.cancel();
        assertEquals(0, engine.stops.get());
        secondHandle.cancel();
        // 停扫由会话迁移线程推进，句柄取消栈上不得调用引擎：这里等它落地再判停扫次数。
        awaitSessionTransitions();
        assertEquals(1, engine.stops.get());

        YoBleScanHub.subscribe(engine, first);
        assertEquals(2, engine.starts.get());
    }

    @Test
    public void fanoutDeliversTheSameFrameToEverySubscriber() {
        RecordingEngine engine = new RecordingEngine();
        AtomicInteger firstCount = new AtomicInteger();
        AtomicInteger secondCount = new AtomicInteger();
        BleOperationHandle first = YoBleScanHub.subscribe(engine, new BleScanObserver() {
            @Override
            public void onScanStarted() {
                firstCount.incrementAndGet();
            }
        });
        BleOperationHandle second = YoBleScanHub.subscribe(engine, new BleScanObserver() {
            @Override
            public void onScanStarted() {
                secondCount.incrementAndGet();
            }
        });

        engine.fanout.onScanStarted();
        assertEquals(1, firstCount.get());
        assertEquals(1, secondCount.get());
        first.cancel();
        second.cancel();
    }

    @Test
    public void releasedEngineAllowsNewEngineToStartAndReceiveFrames() {
        RecordingEngine oldEngine = new RecordingEngine();
        BleOperationHandle oldHandle = YoBleScanHub.subscribe(oldEngine, new BleScanObserver() {
        });
        assertEquals(1, oldEngine.starts.get());
        oldHandle.cancel();

        YoBleScanHub.release(oldEngine);

        RecordingEngine newEngine = new RecordingEngine();
        AtomicInteger found = new AtomicInteger();
        YoBleScanHub.subscribe(newEngine, new BleScanObserver() {
            @Override
            public void onDeviceFound(BleScanResult result) {
                found.incrementAndGet();
            }
        });

        assertEquals("释放后新引擎必须能重新启动扫描", 1, newEngine.starts.get());
        newEngine.fanout.onDeviceFound(result());
        assertEquals(1, found.get());
    }

    @Test
    public void staleHubIsReplacedByTheEnginePassedOnNextSubscribe() {
        RecordingEngine oldEngine = new RecordingEngine();
        YoBleScanHub.subscribe(oldEngine, new BleScanObserver() {
        });

        RecordingEngine newEngine = new RecordingEngine();
        AtomicInteger found = new AtomicInteger();
        YoBleScanHub.subscribe(newEngine, new BleScanObserver() {
            @Override
            public void onDeviceFound(BleScanResult result) {
                found.incrementAndGet();
            }
        });

        assertEquals("第二次订阅必须使用传入的新引擎", 1, newEngine.starts.get());
        assertTrue("旧引擎应被停掉，避免泄漏", oldEngine.stops.get() >= 1);
        newEngine.fanout.onDeviceFound(result());
        assertEquals(1, found.get());
    }

    @Test
    public void onScanFailedKeepsSubscribersAndRestartsOnNextSubscribe() {
        RecordingEngine engine = new RecordingEngine();
        AtomicInteger failures = new AtomicInteger();
        AtomicInteger found = new AtomicInteger();
        BleOperationHandle handle = YoBleScanHub.subscribe(engine, new BleScanObserver() {
            @Override
            public void onScanFailed(BleFailure failure) {
                failures.incrementAndGet();
            }

            @Override
            public void onDeviceFound(BleScanResult result) {
                found.incrementAndGet();
            }
        });

        engine.fanout.onScanFailed(failure());
        assertEquals("失败必须送达当前订阅者", 1, failures.get());
        assertEquals("失败不得重启引擎", 1, engine.starts.get());

        // 失败不销毁会话：同一订阅者仍在，帧继续投递（引擎自行恢复时就是这条路径）。
        engine.fanout.onDeviceFound(result());
        assertEquals("失败后订阅者必须保留", 1, found.get());

        // 再次订阅同一引擎：STOPPED → STARTING，引擎重启一次。
        handle.cancel();
        awaitSessionTransitions();
        AtomicInteger secondFound = new AtomicInteger();
        BleOperationHandle second = YoBleScanHub.subscribe(engine, new BleScanObserver() {
            @Override
            public void onDeviceFound(BleScanResult result) {
                secondFound.incrementAndGet();
            }
        });
        assertEquals("同一引擎再次订阅必须能重启", 2, engine.starts.get());
        engine.fanout.onDeviceFound(result());
        assertEquals(1, secondFound.get());
        second.cancel();
    }

    @Test
    public void resumesRunningStateWhenEngineRestartsAfterFailure() {
        RecordingEngine engine = new RecordingEngine();
        BleOperationHandle handle = YoBleScanHub.subscribe(engine, new BleScanObserver() {
        });
        assertTrue("订阅后引擎必须在运行态", YoBleScanHub.isEngineRunningForTest());

        engine.fanout.onScanFailed(failure());
        assertFalse("失败后引擎不得再被当作运行中", YoBleScanHub.isEngineRunningForTest());

        // 引擎在失败后自行恢复（例如适配器重新开启）会重新上报 onScanStarted。
        engine.fanout.onScanStarted();
        assertTrue("引擎恢复必须把会话拉回运行态", YoBleScanHub.isEngineRunningForTest());

        handle.cancel();
        assertFalse("最后一个订阅者取消后引擎不得留在运行态", YoBleScanHub.isEngineRunningForTest());
    }

    @Test
    public void engineSelfStopAllowsNextSubscribeToRestart() {
        RecordingEngine engine = new RecordingEngine();
        BleOperationHandle handle = YoBleScanHub.subscribe(engine, new BleScanObserver() {
        });
        assertTrue(YoBleScanHub.isEngineRunningForTest());

        // 引擎自停（例如达到扫描超时）：Hub 必须回到非运行态，否则下一个订阅者永远起不来。
        engine.fanout.onScanStopped();
        assertFalse("引擎自停后 Hub 不得仍认为在运行", YoBleScanHub.isEngineRunningForTest());

        handle.cancel();
        awaitSessionTransitions();
        BleOperationHandle second = YoBleScanHub.subscribe(engine, new BleScanObserver() {
        });
        assertEquals("自停后的下一次订阅必须能重新启动引擎", 2, engine.starts.get());
        assertTrue(YoBleScanHub.isEngineRunningForTest());
        second.cancel();
    }

    @Test
    public void fanoutDeliversLostToEverySubscriber() {
        RecordingEngine engine = new RecordingEngine();
        AtomicInteger firstLost = new AtomicInteger();
        AtomicInteger secondLost = new AtomicInteger();
        BleOperationHandle first = YoBleScanHub.subscribe(engine, new BleScanObserver() {
            @Override
            public void onScanResultLost(BleScanResult result) {
                firstLost.incrementAndGet();
            }
        });
        BleOperationHandle second = YoBleScanHub.subscribe(engine, new BleScanObserver() {
            @Override
            public void onScanResultLost(BleScanResult result) {
                secondLost.incrementAndGet();
            }
        });

        engine.fanout.onScanResultLost(result());
        assertEquals(1, firstLost.get());
        assertEquals(1, secondLost.get());
        first.cancel();
        second.cancel();
    }

    @Test
    public void concurrentSubscribersShareOneStartAndAllReceiveTheFrame() throws Exception {
        RecordingEngine engine = new RecordingEngine();
        int count = 8;
        AtomicInteger found = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch go = new CountDownLatch(1);
        Thread[] threads = new Thread[count];
        for (int i = 0; i < count; i++) {
            threads[i] = new Thread(() -> {
                ready.countDown();
                awaitUninterruptibly(go);
                YoBleScanHub.subscribe(engine, new BleScanObserver() {
                    @Override
                    public void onDeviceFound(BleScanResult result) {
                        found.incrementAndGet();
                    }
                });
            });
            threads[i].start();
        }
        assertTrue(ready.await(5, TimeUnit.SECONDS));
        go.countDown();
        for (Thread thread : threads) {
            thread.join(5000);
        }

        assertEquals("并发订阅只允许启动一次引擎", 1, engine.starts.get());
        engine.fanout.onDeviceFound(result());
        assertEquals("所有并发订阅者都应收到同一帧", count, found.get());
    }

    /**
     * 多线程并发订阅后再并发取消：所有订阅者到位时只启动一次；最后一个取消后引擎必须停止，
     * 且 Hub 的运行态视图与引擎真实运行标记一致。
     */
    @Test
    public void interleavedSubscribeAndCancelLeavesNoRunningEngineWithoutSubscribers() throws Exception {
        RecordingEngine engine = new RecordingEngine();
        int count = 8;
        CountDownLatch subscribed = new CountDownLatch(count);
        CountDownLatch cancel = new CountDownLatch(1);
        Thread[] threads = new Thread[count];
        for (int i = 0; i < count; i++) {
            threads[i] = new Thread(() -> {
                BleOperationHandle handle = YoBleScanHub.subscribe(engine, new BleScanObserver() {
                });
                subscribed.countDown();
                awaitUninterruptibly(cancel);
                handle.cancel();
            }, "hub-toggle-" + i);
            threads[i].start();
        }
        assertTrue("全部订阅者必须在超时内完成订阅", subscribed.await(5, TimeUnit.SECONDS));
        assertEquals("订阅者非空时只允许启动一次引擎", 1, engine.starts.get());
        assertTrue("订阅者非空时引擎必须在运行", YoBleScanHub.isEngineRunningForTest());

        cancel.countDown();
        for (Thread thread : threads) {
            thread.join(5000);
        }
        awaitSessionTransitions();

        assertFalse("无订阅者时引擎不得运行", engine.running());
        assertFalse("Hub 不得把已停止的会话当作运行中", YoBleScanHub.isEngineRunningForTest());
        assertEquals("Hub 运行态视图必须与引擎真实运行标记一致",
                engine.running(), YoBleScanHub.isEngineRunningForTest());
        assertEquals("每次启动都必须被停掉", engine.starts.get(), engine.stops.get());
    }

    /**
     * 启动在途时新的订阅者挂进来：启动在锁外进行，因此新订阅不会等待锁；它共享同一次启动，
     * 最后一个取消后引擎必须停止。
     */
    @Test
    public void subscriberAttachedDuringAnInFlightStartSharesTheSessionAndLastCancelStopsIt()
            throws Exception {
        GatedEngine engine = new GatedEngine();
        AtomicReference<BleOperationHandle> starter = new AtomicReference<>();
        Thread starterThread = new Thread(
                () -> starter.set(YoBleScanHub.subscribe(engine, new BleScanObserver() {
                })), "hub-starter");
        starterThread.start();
        assertTrue("启动必须已经进入引擎", engine.startEntered.await(5, TimeUnit.SECONDS));

        AtomicReference<BleOperationHandle> late = new AtomicReference<>();
        Thread lateThread = new Thread(
                () -> late.set(YoBleScanHub.subscribe(engine, new BleScanObserver() {
                })), "hub-late");
        lateThread.setDaemon(true);
        lateThread.start();
        lateThread.join(2_000L);
        assertFalse("引擎启动期间不得持有 Hub 锁，否则并发订阅会被阻塞", lateThread.isAlive());

        late.get().cancel();
        engine.allowStartReturn.countDown();
        starterThread.join(5_000L);

        assertEquals("启动在途时的额外订阅不得重复启动引擎", 1, engine.starts.get());
        assertTrue("启动落地后会话必须进入运行态", YoBleScanHub.isEngineRunningForTest());
        assertTrue(engine.running());

        starter.get().cancel();
        awaitSessionTransitions();
        assertFalse("最后一个订阅者取消后引擎必须停止", engine.running());
        assertEquals(1, engine.stops.get());
        assertFalse(YoBleScanHub.isEngineRunningForTest());
    }

    /**
     * 引擎已释放（归属被丢弃）时有启动在途：该启动落地后必须由启动线程自己补停，
     * 不留下无人接收的会话。
     */
    @Test
    public void releaseDuringAnInFlightStartStopsTheSessionThatLandsAfterRelease() throws Exception {
        GatedEngine engine = new GatedEngine();
        AtomicReference<BleOperationHandle> subscription = new AtomicReference<>();
        Thread subscriber = new Thread(
                () -> subscription.set(YoBleScanHub.subscribe(engine, new BleScanObserver() {
                })), "hub-released-starter");
        subscriber.start();
        assertTrue("启动必须已经进入引擎", engine.startEntered.await(5, TimeUnit.SECONDS));

        YoBleScanHub.release(engine);
        engine.allowStartReturn.countDown();
        subscriber.join(5_000L);

        assertFalse("释放后仍落地的启动必须被补偿停掉", engine.running());
        assertEquals("释放路径自身不重复停扫，停扫由启动线程补齐", 1, engine.stops.get());
        assertFalse(YoBleScanHub.isEngineRunningForTest());

        // 释放后迟到的句柄取消是空操作：不得再对已丢弃的归属补一次停扫。
        subscription.get().cancel();
        assertEquals(1, engine.stops.get());
    }

    /**
     * 引擎在 start() 内同步上报失败（等价于已释放引擎的投递路径）：失败必须送达订阅者，
     * 且分发期间不得持有 Hub 锁——业务在回调里取消别的订阅时不能与取消路径形成锁序环。
     */
    @Test
    public void synchronousStartFailureDispatchesOutsideTheHubLock() throws Exception {
        FailingStartEngine engine = new FailingStartEngine();
        BleOperationHandle previous = YoBleScanHub.subscribe(engine, new BleScanObserver() {
        });

        AtomicBoolean cancelFinished = new AtomicBoolean();
        Thread canceller = new Thread(() -> {
            previous.cancel();
            cancelFinished.set(true);
        }, "hub-cancel-probe");
        canceller.setDaemon(true);

        AtomicInteger failures = new AtomicInteger();
        YoBleScanHub.subscribe(engine, new BleScanObserver() {
            @Override
            public void onScanFailed(BleFailure failure) {
                canceller.start();
                try {
                    canceller.join(2_000L);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
                failures.incrementAndGet();
            }
        });

        assertEquals("同步失败必须送达订阅者", 1, failures.get());
        assertTrue("失败分发期间不得持有 Hub 锁，否则并发取消失败", cancelFinished.get());
        assertEquals("两次订阅各启动一次", 2, engine.starts.get());
        assertEquals("启动已同步失败时不得重复停扫", 0, engine.stops.get());
        assertFalse(YoBleScanHub.isEngineRunningForTest());
    }

    /**
     * 停扫终态按发起代次归属：停扫是异步终态，补偿重启可能已经让新代次跑起来。旧代次的
     * {@code onScanStopped} 迟到时不得把新会话置 STOPPED，也不得把「旧会话结束」分发给停扫窗口内
     * 新挂上的订阅者——业务一旦收到停止就会结束自己的会话，之后所有帧都被 isCurrent 挡住。
     */
    @Test
    public void staleScanStoppedDoesNotKillTheRestartedSession() throws Exception {
        GatedStopEngine engine = new GatedStopEngine();
        AtomicInteger firstStops = new AtomicInteger();
        AtomicInteger secondStops = new AtomicInteger();
        BleOperationHandle first = YoBleScanHub.subscribe(engine, new BleScanObserver() {
            @Override
            public void onScanStopped() {
                firstStops.incrementAndGet();
            }
        });
        assertEquals("首个订阅者必须启动平台扫描", 1, engine.starts.get());
        assertTrue(YoBleScanHub.isEngineRunningForTest());

        Thread canceller = new Thread(first::cancel, "hub-last-cancel");
        canceller.setDaemon(true);
        canceller.start();
        assertTrue("停扫必须已经进入引擎并挂起", engine.stopEntered.await(5, TimeUnit.SECONDS));

        BleOperationHandle second;
        try {
            // 停扫窗口内新订阅：只挂载，等补偿启动，自己不得再启动一次引擎。
            second = YoBleScanHub.subscribe(engine, new BleScanObserver() {
                @Override
                public void onScanStopped() {
                    secondStops.incrementAndGet();
                }
            });
            assertEquals("停扫窗口内的订阅不得重复启动引擎", 1, engine.starts.get());
        } finally {
            engine.allowStopReturn.countDown();
        }
        canceller.join(5_000L);

        assertTrue("补偿重启必须把新代次拉到平台", engine.restarted.await(5, TimeUnit.SECONDS));
        awaitSessionTransitions();
        assertTrue("补偿重启落地后会话必须处于运行态", YoBleScanHub.isEngineRunningForTest());
        assertEquals("补偿重启只允许启动一次", 2, engine.starts.get());

        // 旧代次迟到的停扫终态：停扫任务在扫描状态线程上异步完成，重启之后才到是正常顺序。
        engine.fanoutOfStart(1).onScanStopped();

        assertTrue("旧代次终态不得把新会话标成 STOPPED", YoBleScanHub.isEngineRunningForTest());
        assertTrue("平台上的新会话必须仍在运行", engine.running());
        assertEquals("旧代次的结束不得分发给停扫窗口内新挂上的订阅者", 0, secondStops.get());
        assertEquals("已退订的旧订阅者不得再收到终态", 0, firstStops.get());
        assertEquals("旧代次终态不得触发额外停扫", 1, engine.stops.get());
        assertEquals("旧代次终态不得触发额外启动", 2, engine.starts.get());

        // 误标窗口里新订阅者不得再起一次平台扫描：那一次启动白耗 5/30 限流预算。
        BleOperationHandle third = YoBleScanHub.subscribe(engine, new BleScanObserver() {
        });
        assertEquals("运行中的会话不得因新订阅多起一次平台扫描", 2, engine.starts.get());

        third.cancel();
        second.cancel();
        awaitSessionTransitions();
    }

    /**
     * 停扫终态误标窗口内的真实失败不得被吞：{@code onScanFailed} 的早退按「这一代是否已被作废」
     * 判定，不按 {@code engineState == STOPPED}。旧代次的失败被吸收，当前代次的失败必须恰好送达一次。
     */
    @Test
    public void platformFailureDuringStaleStopWindowStillReachesSubscribers() throws Exception {
        GatedStopEngine engine = new GatedStopEngine();
        BleOperationHandle first = YoBleScanHub.subscribe(engine, new BleScanObserver() {
        });

        Thread canceller = new Thread(first::cancel, "hub-last-cancel");
        canceller.setDaemon(true);
        canceller.start();
        assertTrue("停扫必须已经进入引擎并挂起", engine.stopEntered.await(5, TimeUnit.SECONDS));

        AtomicInteger failures = new AtomicInteger();
        BleOperationHandle second;
        try {
            second = YoBleScanHub.subscribe(engine, new BleScanObserver() {
                @Override
                public void onScanFailed(BleFailure failure) {
                    failures.incrementAndGet();
                }
            });
        } finally {
            engine.allowStopReturn.countDown();
        }
        canceller.join(5_000L);
        assertTrue("补偿重启必须把新代次拉到平台", engine.restarted.await(5, TimeUnit.SECONDS));
        awaitSessionTransitions();

        // 旧代次迟到的停扫终态（旧实现会在这里把会话误标成 STOPPED，从而吞掉下面的真实失败）。
        // 会话状态的归属由 staleScanStoppedDoesNotKillTheRestartedSession 断言，这里只判失败是否送达。
        engine.fanoutOfStart(1).onScanStopped();

        engine.fanoutOfStart(2).onScanFailed(failure());
        assertEquals("当前代次的平台失败必须恰好送达一次", 1, failures.get());
        assertFalse("失败后会话不得仍被当作运行中", YoBleScanHub.isEngineRunningForTest());

        engine.fanoutOfStart(1).onScanFailed(failure());
        assertEquals("被作废代次的失败必须被吸收", 1, failures.get());
        second.cancel();
        awaitSessionTransitions();
    }

    /**
     * 生产顺序（{@code YoBleScanController.stopScan()} 只把停扫投递给引擎状态线程，立即返回）：
     * 停扫落地 → 会话 STOPPED → 停扫窗口之后的新订阅 attach 开新代次并重启引擎；随后旧代次的
     * started / stopped 才从引擎状态线程迟到。这两条终态不得送达新代次的订阅者，也不得改动新会话。
     */
    @Test
    public void attachAfterImmediateStopStartsNewEpochAndIgnoresLateTerminal() {
        ImmediateStopEngine engine = new ImmediateStopEngine();
        BleOperationHandle first = YoBleScanHub.subscribe(engine, new BleScanObserver() {
        });
        assertEquals("首个订阅者必须启动平台扫描", 1, engine.starts.get());
        assertTrue(YoBleScanHub.isEngineRunningForTest());

        // stop() 立即返回：迁移线程把会话落到 STOPPED，这里用迁移栅栏拿这个确定点，不拿调度当同步。
        first.cancel();
        awaitSessionTransitions();
        assertFalse("停扫落地后会话不得仍被当作运行中", YoBleScanHub.isEngineRunningForTest());
        assertEquals("最后一个订阅者取消必须停扫一次", 1, engine.stops.get());

        AtomicInteger startedSeen = new AtomicInteger();
        AtomicInteger stoppedSeen = new AtomicInteger();
        AtomicInteger found = new AtomicInteger();
        BleOperationHandle second = YoBleScanHub.subscribe(engine, new BleScanObserver() {
            @Override
            public void onScanStarted() {
                startedSeen.incrementAndGet();
            }

            @Override
            public void onScanStopped() {
                stoppedSeen.incrementAndGet();
            }

            @Override
            public void onDeviceFound(BleScanResult result) {
                found.incrementAndGet();
            }
        });
        assertEquals("STOPPED 上的新订阅必须开新代次并启动一次引擎", 2, engine.starts.get());
        assertTrue("新代次启动后会话必须处于运行态", YoBleScanHub.isEngineRunningForTest());

        // 旧代次迟到的终态（停扫任务在引擎状态线程上异步完成，重启之后才到是正常顺序）。
        engine.fanoutOfStart(1).onScanStopped();
        engine.fanoutOfStart(1).onScanStarted();

        assertEquals("旧代次的结束不得送达新代次的订阅者", 0, stoppedSeen.get());
        assertEquals("旧代次的开始不得送达新代次的订阅者", 0, startedSeen.get());
        assertTrue("旧代次终态不得把新会话标成非运行", YoBleScanHub.isEngineRunningForTest());
        assertEquals("旧代次终态不得触发额外启动", 2, engine.starts.get());
        assertEquals("旧代次终态不得触发额外停扫", 1, engine.stops.get());

        // 新代次自己的帧与终态照常送达：吸收旧代次不能连当前会话一起吞掉。
        engine.fanoutOfStart(2).onDeviceFound(result());
        assertEquals("新代次的帧必须送达", 1, found.get());
        engine.fanoutOfStart(2).onScanStarted();
        assertEquals("新代次的开始必须送达", 1, startedSeen.get());
        engine.fanoutOfStart(2).onScanStopped();
        assertEquals("新代次的结束必须送达", 1, stoppedSeen.get());
        assertFalse(YoBleScanHub.isEngineRunningForTest());

        second.cancel();
        awaitSessionTransitions();
    }

    /**
     * 停扫窗口里同代次的失败上报（{@code onScanFailed} 把会话置 STOPPED）不得让补偿重启被放弃：
     * 失败说明引擎确实已经停了，停扫收尾必须按真实状态重启，否则停扫窗口内挂上的订阅者会停在
     * STOPPED 且没有引擎——它自己不会启动引擎（attach 在 STOPPING 上只挂载）。
     */
    @Test
    public void failureDuringStopWindowStillResumesForTheSubscriberThatWaitedForIt() throws Exception {
        GatedStopEngine engine = new GatedStopEngine();
        BleOperationHandle first = YoBleScanHub.subscribe(engine, new BleScanObserver() {
        });

        Thread canceller = new Thread(first::cancel, "hub-last-cancel");
        canceller.setDaemon(true);
        canceller.start();
        assertTrue("停扫必须已经进入引擎并挂起", engine.stopEntered.await(5, TimeUnit.SECONDS));

        AtomicInteger failures = new AtomicInteger();
        BleOperationHandle second;
        try {
            // 停扫窗口内新订阅：只挂载，等补偿启动，自己不得再启动一次引擎。
            second = YoBleScanHub.subscribe(engine, new BleScanObserver() {
                @Override
                public void onScanFailed(BleFailure failure) {
                    failures.incrementAndGet();
                }
            });
            assertEquals("停扫窗口内的订阅不得重复启动引擎", 1, engine.starts.get());
            // 停扫真的落地了：同一代次的平台失败把会话置 STOPPED（引擎已经没有会话）。
            engine.fanoutOfStart(1).onScanFailed(failure());
            assertEquals("当前代次的失败必须送达停扫窗口内的订阅者", 1, failures.get());
            assertFalse("失败后会话不得仍被当作运行中", YoBleScanHub.isEngineRunningForTest());
        } finally {
            engine.allowStopReturn.countDown();
        }
        canceller.join(5_000L);

        assertTrue("停扫窗口里的失败不得放弃补偿重启", engine.restarted.await(5, TimeUnit.SECONDS));
        awaitSessionTransitions();
        assertTrue("补偿重启落地后会话必须处于运行态", YoBleScanHub.isEngineRunningForTest());
        assertTrue("补偿重启必须把新代次拉到平台", engine.running());
        assertEquals("补偿重启只允许启动一次", 2, engine.starts.get());
        assertEquals("失败后的补偿重启不得多停一次", 1, engine.stops.get());

        second.cancel();
        awaitSessionTransitions();
        assertFalse(YoBleScanHub.isEngineRunningForTest());
    }

    /**
     * 分发已经起始之后才挂上的订阅者不得参与这一次分发：判定的代次/状态与分发集合必须取自同一
     * 临界区。生产里在订阅者回调中重新订阅是正常路径（业务收到结束回调后立刻开下一轮扫描）。
     *
     * <p>能证伪的写法范围要说清：订阅者集合是 {@code CopyOnWriteArrayList}，任何
     * {@code for (o : subscribers)} 在创建迭代器时就取到了数组快照，因此「锁内 copyOf + 锁外
     * for-each」与「锁外 for-each」这两种实现在本用例下都会通过。本用例真正锁住的是
     * 「锁外按 {@code size()}/{@code get(i)} 逐次实时读」那类写法（那种写法会让 lateStarted 变 1）。
     * 原子快照本身的正确性由构造保证（判定与快照同临界区），不由本用例单独证明。</p>
     */
    @Test
    public void subscriberAttachedWhileTerminalIsBeingDispatchedDoesNotJoinIt() {
        ImmediateStopEngine engine = new ImmediateStopEngine();
        AtomicInteger firstStarted = new AtomicInteger();
        AtomicInteger lateStarted = new AtomicInteger();
        AtomicReference<BleOperationHandle> late = new AtomicReference<>();
        BleOperationHandle first = YoBleScanHub.subscribe(engine, new BleScanObserver() {
            @Override
            public void onScanStarted() {
                firstStarted.incrementAndGet();
                late.set(YoBleScanHub.subscribe(engine, new BleScanObserver() {
                    @Override
                    public void onScanStarted() {
                        lateStarted.incrementAndGet();
                    }
                }));
            }
        });
        assertEquals(1, engine.starts.get());

        engine.fanoutOfStart(1).onScanStarted();

        assertEquals("既有订阅者必须收到这一次开始", 1, firstStarted.get());
        assertEquals("分发开始后才挂上的订阅者不得参与本次分发", 0, lateStarted.get());
        assertEquals("分发期间的新订阅不得重复启动引擎", 1, engine.starts.get());
        assertTrue("分发期间的新订阅不得改动会话状态", YoBleScanHub.isEngineRunningForTest());

        first.cancel();
        late.get().cancel();
        awaitSessionTransitions();
        assertFalse(YoBleScanHub.isEngineRunningForTest());
    }

    private static BleScanResult result() {
        return new BleScanResult(
                new BleDeviceIdentity(new BleDeviceId("AA:BB:CC:00:00:01"), "dev", 0L),
                -50,
                0L);
    }

    private static BleFailure failure() {
        return new BleFailure(BleErrorCode.SCAN_START_FAILED, "boom",
                null, true, false, "test", null, null, null);
    }

    /**
     * 等 Hub 的会话迁移队列排空：句柄取消栈上不再调用引擎，停扫与补偿重启由迁移线程推进，
     * 用例必须等它落地（条件通知），不能拿线程调度当同步。
     */
    private static void awaitSessionTransitions() {
        CountDownLatch barrier = YoBleScanHub.submitTransitionBarrierForTest();
        try {
            if (!barrier.await(5L, TimeUnit.SECONDS)) {
                fail("会话迁移未在超时内排空");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            fail("等待会话迁移时被中断");
        }
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

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
            return new BleOperationHandle("scan", () -> {
            });
        }

        @Override
        public Result<Void> stop() {
            stops.incrementAndGet();
            running = false;
            return Result.success(null);
        }
    }

    /** start() 会停在门闩上，用于制造“启动在途”的确定性交错。 */
    private static final class GatedEngine implements YoBleScanHub.Engine {
        final AtomicInteger starts = new AtomicInteger();
        final AtomicInteger stops = new AtomicInteger();
        final CountDownLatch startEntered = new CountDownLatch(1);
        final CountDownLatch allowStartReturn = new CountDownLatch(1);
        private volatile boolean running;

        boolean running() {
            return running;
        }

        @Override
        public BleOperationHandle start(BleScanObserver fanout) {
            starts.incrementAndGet();
            startEntered.countDown();
            awaitUninterruptibly(allowStartReturn);
            running = true;
            return new BleOperationHandle("scan", () -> {
            });
        }

        @Override
        public Result<Void> stop() {
            stops.incrementAndGet();
            running = false;
            return Result.success(null);
        }
    }

    /** 等价于已释放引擎：start() 内同步把失败投递给 fanout。 */
    private static final class FailingStartEngine implements YoBleScanHub.Engine {
        final AtomicInteger starts = new AtomicInteger();
        final AtomicInteger stops = new AtomicInteger();

        @Override
        public BleOperationHandle start(BleScanObserver fanout) {
            starts.incrementAndGet();
            fanout.onScanFailed(failure());
            return new BleOperationHandle("scan", () -> {
            });
        }

        @Override
        public Result<Void> stop() {
            stops.incrementAndGet();
            return Result.success(null);
        }
    }

    /**
     * 生产顺序的引擎：{@code stop()} 立即返回（真实控制器只把停扫投递给引擎状态线程），
     * 停扫终态随后异步到达。每次都记下本次启动绑定的回调入口，用例据此按代次投递终态。
     */
    private static final class ImmediateStopEngine implements YoBleScanHub.Engine {
        final AtomicInteger starts = new AtomicInteger();
        final AtomicInteger stops = new AtomicInteger();
        private final List<BleScanObserver> fanouts = new CopyOnWriteArrayList<>();
        private volatile boolean running;

        boolean running() {
            return running;
        }

        /** 第 {@code ordinal} 次启动（1 起）绑定的回调入口。 */
        BleScanObserver fanoutOfStart(int ordinal) {
            return fanouts.get(ordinal - 1);
        }

        @Override
        public BleOperationHandle start(BleScanObserver fanout) {
            fanouts.add(fanout);
            starts.incrementAndGet();
            running = true;
            return new BleOperationHandle("scan", () -> {
            });
        }

        @Override
        public Result<Void> stop() {
            stops.incrementAndGet();
            running = false;
            return Result.success(null);
        }
    }

    /**
     * stop() 会停在门闩上，用于制造「停扫终态尚未投递、补偿重启已完成」的确定性交错；
     * 每次都记下本次启动绑定的回调入口，用例据此按代次投递终态。
     */
    private static final class GatedStopEngine implements YoBleScanHub.Engine {
        final AtomicInteger starts = new AtomicInteger();
        final AtomicInteger stops = new AtomicInteger();
        final CountDownLatch stopEntered = new CountDownLatch(1);
        final CountDownLatch allowStopReturn = new CountDownLatch(1);
        final CountDownLatch restarted = new CountDownLatch(1);
        private final List<BleScanObserver> fanouts = new CopyOnWriteArrayList<>();
        private volatile boolean running;

        boolean running() {
            return running;
        }

        /** 第 {@code ordinal} 次启动（1 起）绑定的回调入口。 */
        BleScanObserver fanoutOfStart(int ordinal) {
            return fanouts.get(ordinal - 1);
        }

        @Override
        public BleOperationHandle start(BleScanObserver fanout) {
            fanouts.add(fanout);
            if (starts.incrementAndGet() == 2) {
                restarted.countDown();
            }
            running = true;
            return new BleOperationHandle("scan", () -> {
            });
        }

        @Override
        public Result<Void> stop() {
            stops.incrementAndGet();
            running = false;
            stopEntered.countDown();
            awaitUninterruptibly(allowStopReturn);
            return Result.success(null);
        }
    }
}
