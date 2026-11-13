package com.yovo.biz_ggec_hs.internal.link;

import com.yovo.biz_ggec_hs.api.HsDevice;

import com.yovo.biz_ggec_hs.internal.center.catalog.HsCatalogImpl;


import com.yovo.biz_ggec_hs.api.HsControl;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.yovo.biz_ggec_hs.internal.center.protocol.FakeHsCommandLink;
import com.yovo.biz_ggec_hs.internal.center.protocol.HsProtocolEngine;
import com.yovo.biz_ggec_hs.internal.center.protocol.musiccmd.EQModeCommand;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 就绪看门狗只属于「当前这次连接尝试」：跨越会话实现 + 看门狗本体，不只验引擎。
 *
 * <p>编排全部用门闩：链路事件由 {@link FakeHsCommandLink} 显式触发，引擎状态只在引擎线程上推进，
 * 12 秒由 {@link FakeMainThread} 显式推进，不 sleep、不轮询。</p>
 */
public final class HsSessionImplReadyWatchdogTest {
    private static final String MAC_A = "00:11:22:33:44:55";
    private static final String MAC_B = "66:77:88:99:AA:BB";
    private static final long READY_TIMEOUT_MILLIS = HsSessionImpl.CONNECT_READY_TIMEOUT_MILLIS;

    /**
     * 已建立会话 A 后直接 {@code connect(B)}（不先 disconnect）：旧会话收尾发出的
     * {@code onDisconnected} 不得撤掉 B 这次尝试的看门狗。随后「不报错也不就绪」，推进 12 秒，
     * B 的尝试必须自己去上报一次超时失败。
     */
    @Test
    public void switchingTargetKeepsReadyWatchdogForTheNewAttempt() throws Exception {
        FakeHsCommandLink link = new FakeHsCommandLink();
        HsProtocolEngine engine = new HsProtocolEngine(link);
        FakeMainThread mainThread = new FakeMainThread();
        HsSessionImpl session = new HsSessionImpl(engine, new HsCatalogImpl(), mainThread, new ReadyClassicRadio(),
                new HsConnectionIntake());
        try {
            RecordingObserver observer = new RecordingObserver();
            session.observe(observer);

            session.connect("HS02", new HsDevice.Radios(MAC_A, "", "E8:00:00:00:00:01", ""));
            assertTrue("尝试开始必须通知 CONNECTING",
                    observer.awaitEvent(event(HsControl.State.CONNECTING, null)));
            link.ready();
            assertTrue("链路就绪必须通知 READY",
                    observer.awaitEvent(event(HsControl.State.READY, null)));
            assertEquals("看门狗按 12 秒起表", READY_TIMEOUT_MILLIS, mainThread.delayMillis());
            assertFalse("会话已就绪，看门狗必须撤表", mainThread.isArmed());

            // 换目标：不先 disconnect，让引擎线程自己收尾 A 会话再派发 B 的尝试。
            session.connect("HS02", new HsDevice.Radios(MAC_B, "", "E8:00:00:00:00:02", ""));
            awaitEngineProcessed(engine);

            assertTrue("旧会话必须报一次正常收尾的断连（无失败分类）",
                    observer.awaitEvent(event(HsControl.State.DISCONNECTED, null)));
            assertTrue("新尝试必须报 CONNECTING",
                    observer.awaitEvent(event(HsControl.State.CONNECTING, null)));
            assertTrue("新尝试的看门狗必须仍在", mainThread.isArmed());
            assertNotNull("新尝试必须排着一次超时任务", mainThread.lastScheduledTask());

            // 「不报错也不就绪」：只有时间到达，没有链路事件。
            assertTrue("看门狗必须由时间推进触发", mainThread.advanceToTimeout());
            assertTrue("超时必须上报 TIMEOUT",
                    observer.awaitEvent(event(HsControl.State.DISCONNECTED, HsControl.Failure.TIMEOUT)));
            awaitEngineProcessed(engine);

            assertEquals("一次尝试超时只上报一次失败",
                    1, observer.countWithFailure(HsControl.Failure.TIMEOUT));
            assertEquals("换目标 + 超时共两条断连", 2, observer.countState(HsControl.State.DISCONNECTED));
        } finally {
            session.close();
        }
    }

    /** 正常 {@code disconnect()} 之后推进时间：不得再报超时。 */
    @Test
    public void disconnectCancelsWatchdog() throws Exception {
        FakeHsCommandLink link = new FakeHsCommandLink();
        HsProtocolEngine engine = new HsProtocolEngine(link);
        FakeMainThread mainThread = new FakeMainThread();
        HsSessionImpl session = new HsSessionImpl(engine, new HsCatalogImpl(), mainThread, new ReadyClassicRadio(),
                new HsConnectionIntake());
        try {
            RecordingObserver observer = new RecordingObserver();
            session.observe(observer);

            session.connect("HS02", new HsDevice.Radios(MAC_A, "", "E8:00:00:00:00:01", ""));
            assertTrue("尝试开始必须通知 CONNECTING",
                    observer.awaitEvent(event(HsControl.State.CONNECTING, null)));
            assertTrue("尝试进行中看门狗必须在走", mainThread.isArmed());

            session.disconnect();
            awaitEngineProcessed(engine);

            assertFalse("主动断开必须撤表", mainThread.isArmed());
            assertFalse("撤表后没有待跑的超时任务", mainThread.advanceToTimeout());
            awaitEngineProcessed(engine);

            assertEquals("主动断开后不得出现超时失败", 0, observer.countWithFailure(HsControl.Failure.TIMEOUT));
            assertEquals("主动断开只报一条断连",
                    1, observer.countState(HsControl.State.DISCONNECTED));
        } finally {
            session.close();
        }
    }

    /**
     * 尝试已经结束（这次由链路失效结束）之后才到的超时：静默丢弃，不得重复上报。
     */
    @Test
    public void lateTimeoutAfterAttemptEndedIsIgnored() throws Exception {
        FakeHsCommandLink link = new FakeHsCommandLink();
        HsProtocolEngine engine = new HsProtocolEngine(link);
        FakeMainThread mainThread = new FakeMainThread();
        HsSessionImpl session = new HsSessionImpl(engine, new HsCatalogImpl(), mainThread, new ReadyClassicRadio(),
                new HsConnectionIntake());
        try {
            RecordingObserver observer = new RecordingObserver();
            session.observe(observer);

            session.connect("HS02", new HsDevice.Radios(MAC_A, "", "E8:00:00:00:00:01", ""));
            assertTrue("尝试开始必须通知 CONNECTING",
                    observer.awaitEvent(event(HsControl.State.CONNECTING, null)));
            Runnable armedTimeout = mainThread.lastScheduledTask();
            assertNotNull(armedTimeout);

            // 尝试以链路失效结束：此后它不再活动。
            link.linkError("ble-io");
            assertTrue("尝试失败必须报 DEVICE_ERROR",
                    observer.awaitEvent(event(HsControl.State.DISCONNECTED, HsControl.Failure.DEVICE_ERROR)));
            awaitEngineProcessed(engine);

            armedTimeout.run();
            engine.onConnectAttemptTimedOut();
            awaitEngineProcessed(engine);

            assertEquals("迟到的超时不得再报失败", 0, observer.countWithFailure(HsControl.Failure.TIMEOUT));
            assertEquals("一次链路失效只报一条断连",
                    1, observer.countState(HsControl.State.DISCONNECTED));
        } finally {
            session.close();
        }
    }

    /**
     * 上层没有挂观察者时看门狗照样起表：它属于这次尝试，不属于「谁在听事件」。
     * 观测量取引擎在超时后收掉链路。
     */
    @Test
    public void watchdogRunsWithoutAnyObserver() throws Exception {
        FakeHsCommandLink link = new FakeHsCommandLink();
        HsProtocolEngine engine = new HsProtocolEngine(link);
        FakeMainThread mainThread = new FakeMainThread();
        HsSessionImpl session = new HsSessionImpl(engine, new HsCatalogImpl(), mainThread, new ReadyClassicRadio(),
                new HsConnectionIntake());
        try {
            session.connect("HS02", new HsDevice.Radios(MAC_A, "", "E8:00:00:00:00:01", ""));
            awaitEngineProcessed(engine);
            assertTrue("没有观察者时看门狗也必须在走", mainThread.isArmed());

            assertTrue(mainThread.advanceToTimeout());
            awaitEngineProcessed(engine);

            assertFalse("超时后会话不得停留在就绪", session.isReady());
        } finally {
            session.close();
        }
    }

    private static String event(HsControl.State state, HsControl.Failure failure) {
        return state + ":" + failure;
    }

    /**
     * 引擎队列屏障：没有会话时提交一条命令会在引擎轮次里被立即拒绝，
     * 它的回调到达即证明该命令之前的任务（会话收尾 / 尝试派发）都已执行完。
     */
    private static void awaitEngineProcessed(HsProtocolEngine engine) throws InterruptedException {
        CountDownLatch processed = new CountDownLatch(1);
        engine.submit(new EQModeCommand(EQModeCommand.MUSIC_MODE),
                (command, completion) -> processed.countDown());
        assertTrue("引擎未在预算内处理完排队任务", processed.await(3, TimeUnit.SECONDS));
    }

    /** 记录连接事件；等待用条件通知，不用 sleep 轮询。 */
    private static final class RecordingObserver implements HsControl.Listener {
        private static final long AWAIT_TIMEOUT_MILLIS = 3_000L;

        private final Object lock = new Object();
        private final List<String> events = new ArrayList<>();

        @Override
        public void onConnectionChanged(HsControl.State state, HsControl.Failure failure) {
            record(event(state, failure));
        }

        private void record(String value) {
            synchronized (lock) {
                events.add(value);
                lock.notifyAll();
            }
        }

        boolean awaitEvent(String expected) throws InterruptedException {
            long deadline = System.currentTimeMillis() + AWAIT_TIMEOUT_MILLIS;
            synchronized (lock) {
                while (!events.contains(expected)) {
                    long remaining = deadline - System.currentTimeMillis();
                    if (remaining <= 0L) {
                        return false;
                    }
                    lock.wait(remaining);
                }
                return true;
            }
        }

        int countState(HsControl.State state) {
            synchronized (lock) {
                int count = 0;
                for (String value : events) {
                    if (value.startsWith(state + ":")) {
                        count++;
                    }
                }
                return count;
            }
        }

        int countWithFailure(HsControl.Failure failure) {
            synchronized (lock) {
                int count = 0;
                for (String value : events) {
                    if (value.endsWith(":" + failure)) {
                        count++;
                    }
                }
                return count;
            }
        }
    }
}
