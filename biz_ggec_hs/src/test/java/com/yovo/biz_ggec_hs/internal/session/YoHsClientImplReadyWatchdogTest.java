package com.yovo.biz_ggec_hs.internal.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.yovo.biz_ggec_hs.api.HsConnectionObserver;
import com.yovo.biz_ggec_hs.internal.contract.HsCommandLink;
import com.yovo.biz_ggec_hs.internal.protocol.HsProtocolEngine;
import com.yovo.biz_ggec_hs.internal.protocol.musiccmd.EQModeCommand;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 就绪看门狗必须只属于「当前这次连接尝试」，跨到会话实现 + 看门狗本身，不只验引擎。
 *
 * <p>编排全部用门闩：链路事件由 {@link FakeLink} 显式触发，引擎状态只在引擎线程上推进，
 * 12 秒由 {@link FakeMainThread} 显式推进，不 sleep、不轮询。</p>
 */
public final class YoHsClientImplReadyWatchdogTest {
    private static final String MAC_A = "00:11:22:33:44:55";
    private static final String MAC_B = "66:77:88:99:AA:BB";
    private static final String REASON_TARGET_SWITCHED = "连接目标已切换";
    private static final String READY_TIMEOUT = "ready-timeout";
    private static final long READY_TIMEOUT_MILLIS = 12_000L;

    /**
     * 已建立会话 A 后直接 {@code connect(B)}（不先 disconnect）：引擎线程收尾 A 会话时发出的
     * {@code onDisconnected} 不得撤掉 B 这次尝试的看门狗。随后「不报错也不就绪」，推进 12 秒，
     * B 的尝试必须自己去上报一次 {@code onConnectFailed("ready-timeout", false)}。
     */
    @Test
    public void switchingTargetKeepsReadyWatchdogForTheNewAttempt() throws Exception {
        FakeLink link = new FakeLink();
        HsProtocolEngine engine = new HsProtocolEngine(link);
        FakeMainThread mainThread = new FakeMainThread();
        YoHsClientImpl client = new YoHsClientImpl(engine, mainThread);
        try {
            RecordingObserver observer = new RecordingObserver();
            client.setConnectionObserver(observer);

            client.connect(MAC_A);
            assertTrue("尝试开始必须通知 onConnecting", observer.awaitEvent("connecting:" + MAC_A));
            link.ready();
            assertTrue("链路就绪必须通知 onConnected", observer.awaitEvent("connected:" + MAC_A));
            assertEquals("看门狗按 12 秒起表", READY_TIMEOUT_MILLIS, mainThread.delayMillis());
            assertFalse("会话已就绪，看门狗必须撤表", mainThread.isArmed());

            // 换目标：不先 disconnect，让引擎线程自己收尾 A 会话再派发 B 的尝试。
            client.connect(MAC_B);
            awaitEngineProcessed(engine);

            assertEquals("旧会话撤表后必须为新尝试重新起表",
                    List.of("connecting:" + MAC_A,
                            "connected:" + MAC_A,
                            "disconnected:" + REASON_TARGET_SWITCHED,
                            "connecting:" + MAC_B),
                    observer.events());
            assertTrue("新尝试的看门狗必须仍在", mainThread.isArmed());
            assertNotNull("新尝试必须排着一次超时任务", mainThread.lastScheduledTask());

            // 「不报错也不就绪」：只有时间到达，没有链路事件。
            assertTrue("看门狗必须由时间推进触发", mainThread.advanceToTimeout());
            assertTrue("超时必须上报 ready-timeout",
                    observer.awaitEvent("failed:" + READY_TIMEOUT + ":false"));
            awaitEngineProcessed(engine);

            assertEquals("一次尝试超时只上报一次连接失败", 1, observer.connectFailedCount());
            assertEquals("超时不得再补一条断连", 1, observer.disconnectedCount());
        } finally {
            client.close();
        }
    }

    /**
     * 正常 {@code disconnect()} 之后推进时间：不得再发 {@code ready-timeout}。
     */
    @Test
    public void disconnectCancelsWatchdog() throws Exception {
        FakeLink link = new FakeLink();
        HsProtocolEngine engine = new HsProtocolEngine(link);
        FakeMainThread mainThread = new FakeMainThread();
        YoHsClientImpl client = new YoHsClientImpl(engine, mainThread);
        try {
            RecordingObserver observer = new RecordingObserver();
            client.setConnectionObserver(observer);

            client.connect(MAC_A);
            assertTrue("尝试开始必须通知 onConnecting", observer.awaitEvent("connecting:" + MAC_A));
            assertTrue("尝试进行中看门狗必须在走", mainThread.isArmed());

            client.disconnect();
            awaitEngineProcessed(engine);

            assertFalse("主动断开必须撤表", mainThread.isArmed());
            assertFalse("撤表后没有待跑的超时任务", mainThread.advanceToTimeout());
            awaitEngineProcessed(engine);

            assertEquals("主动断开后不得出现 ready-timeout",
                    List.of("connecting:" + MAC_A), observer.events());
            assertEquals(0, observer.connectFailedCount());
        } finally {
            client.close();
        }
    }

    /**
     * 尝试已经结束（这次由链路失效结束）之后才到的超时：静默丢弃，不得重复上报。
     *
     * <p>取「迟到」的一种真实形状：主线程已经把看门狗任务取走、cancel 随后到达，
     * 该任务仍会执行。看门狗自己的 armed 标志必须把它丢掉；引擎侧
     * {@code onConnectAttemptTimedOut} 的「尝试不再活动」守卫是同一语义的第二道口径。</p>
     */
    @Test
    public void lateTimeoutAfterAttemptEndedIsIgnored() throws Exception {
        FakeLink link = new FakeLink();
        HsProtocolEngine engine = new HsProtocolEngine(link);
        FakeMainThread mainThread = new FakeMainThread();
        YoHsClientImpl client = new YoHsClientImpl(engine, mainThread);
        try {
            RecordingObserver observer = new RecordingObserver();
            client.setConnectionObserver(observer);

            client.connect(MAC_A);
            assertTrue("尝试开始必须通知 onConnecting", observer.awaitEvent("connecting:" + MAC_A));
            Runnable armedTimeout = mainThread.lastScheduledTask();
            assertNotNull(armedTimeout);

            // 尝试以链路失效结束：此后它不再活动。
            link.linkError("ble-io");
            assertTrue("尝试失败必须通知 onConnectFailed",
                    observer.awaitEvent("failed:ble-io:false"));
            awaitEngineProcessed(engine);

            armedTimeout.run();
            engine.onConnectAttemptTimedOut();
            awaitEngineProcessed(engine);

            assertEquals("迟到的超时不得再发连接失败", 1, observer.connectFailedCount());
            assertEquals("迟到的超时不得改成断连", 0, observer.disconnectedCount());
            assertEquals(List.of("connecting:" + MAC_A, "failed:ble-io:false"), observer.events());
        } finally {
            client.close();
        }
    }

    /**
     * 上层没有挂连接观察者时看门狗照样起表：它属于这次尝试，不属于「谁在听事件」。
     * 观测量取引擎在超时后收掉链路（{@code link.close()}）。
     */
    @Test
    public void watchdogRunsWithoutAnyConnectionObserver() throws Exception {
        FakeLink link = new FakeLink();
        HsProtocolEngine engine = new HsProtocolEngine(link);
        FakeMainThread mainThread = new FakeMainThread();
        YoHsClientImpl client = new YoHsClientImpl(engine, mainThread);
        try {
            client.connect(MAC_A);
            awaitEngineProcessed(engine);
            assertTrue("没有观察者时看门狗也必须在走", mainThread.isArmed());

            assertTrue(mainThread.advanceToTimeout());
            awaitEngineProcessed(engine);

            assertEquals("超时必须走到引擎并收掉链路", 1, link.closeCount());
        } finally {
            client.close();
        }
    }

    /**
     * 引擎队列屏障：没有会话时提交一条命令会在引擎轮次里被立即拒绝，
     * 它的回调到达即证明该命令之前的任务（会话收尾 / 尝试派发）都已执行完。
     */
    private static void awaitEngineProcessed(HsProtocolEngine engine) throws InterruptedException {
        CountDownLatch processed = new CountDownLatch(1);
        engine.submit(new EQModeCommand(EQModeCommand.MUSIC_MODE), result -> processed.countDown());
        assertTrue("引擎未在预算内处理完排队任务", processed.await(3, TimeUnit.SECONDS));
    }

    /** 命令链路测试替身：状态只由用例显式触发，形状与生产实现一致。 */
    private static final class FakeLink implements HsCommandLink {
        private final AtomicInteger closes = new AtomicInteger();
        private volatile Listener listener;
        private volatile boolean ready;

        @Override
        public void open(String macAddress) {
            // 与生产 retireLocked 一致：换链路只清就绪标志，不补发 onReady(false)。
            ready = false;
        }

        @Override
        public void close() {
            closes.incrementAndGet();
            ready = false;
            Listener current = listener;
            if (current != null) {
                current.onReady(false);
            }
        }

        @Override
        public void release() {
            close();
        }

        @Override
        public boolean write(byte[] payload, WriteCallback callback) {
            callback.onStarted();
            callback.onCompleted(0);
            return true;
        }

        @Override
        public void setListener(Listener listener) {
            this.listener = listener;
        }

        int closeCount() {
            return closes.get();
        }

        void ready() {
            ready = true;
            Listener current = listener;
            if (current != null) {
                current.onReady(true);
            }
        }

        /** 生产顺序：先 {@code onLinkError}，此前就绪过时再补一次 {@code onReady(false)}。 */
        void linkError(String message) {
            boolean wasReady = ready;
            ready = false;
            Listener current = listener;
            if (current == null) {
                return;
            }
            current.onLinkError(message);
            if (wasReady) {
                current.onReady(false);
            }
        }
    }

    /**
     * 主线程 + 定时器的可控替身：{@code post} 就地执行（保持投递顺序），
     * {@code postDelayed} 记账成「一次待跑的超时」，由用例显式推进。
     */
    private static final class FakeMainThread implements YoHsClientImpl.MainThread {
        private final Object lock = new Object();
        private Runnable pending;
        private Runnable lastPosted;
        private long delayMillis = -1L;

        @Override
        public void post(Runnable task) {
            task.run();
        }

        @Override
        public void postDelayed(Runnable task, long delayMillis) {
            synchronized (lock) {
                this.pending = task;
                this.lastPosted = task;
                this.delayMillis = delayMillis;
            }
        }

        @Override
        public void removeCallbacks(Runnable task) {
            synchronized (lock) {
                if (pending == task) {
                    pending = null;
                }
            }
        }

        boolean isArmed() {
            synchronized (lock) {
                return pending != null;
            }
        }

        long delayMillis() {
            synchronized (lock) {
                return delayMillis;
            }
        }

        /** 最近一次排队的超时任务；撤表后仍保留引用，用来模拟「已取走消息」的迟到触发。 */
        Runnable lastScheduledTask() {
            synchronized (lock) {
                return lastPosted;
            }
        }

        /** 推进到超时时刻：跑掉已排队的超时任务；队列为空返回 false。 */
        boolean advanceToTimeout() {
            Runnable task;
            synchronized (lock) {
                task = pending;
                pending = null;
            }
            if (task == null) {
                return false;
            }
            task.run();
            return true;
        }
    }

    /** 记录连接事件；等待用条件通知，不用 sleep 轮询。 */
    private static final class RecordingObserver implements HsConnectionObserver {
        private static final long AWAIT_TIMEOUT_MILLIS = 3_000L;

        private final Object lock = new Object();
        private final List<String> events = new ArrayList<>();

        @Override
        public void onConnecting(String macAddress) {
            record("connecting:" + macAddress);
        }

        @Override
        public void onConnected(String macAddress) {
            record("connected:" + macAddress);
        }

        @Override
        public void onDisconnected(String reason) {
            record("disconnected:" + reason);
        }

        @Override
        public void onConnectFailed(String reason, boolean willRetry) {
            record("failed:" + reason + ":" + willRetry);
        }

        private void record(String event) {
            synchronized (lock) {
                events.add(event);
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

        List<String> events() {
            synchronized (lock) {
                return List.copyOf(events);
            }
        }

        int connectFailedCount() {
            synchronized (lock) {
                return countLocked("failed:");
            }
        }

        int disconnectedCount() {
            synchronized (lock) {
                return countLocked("disconnected:");
            }
        }

        private int countLocked(String prefix) {
            int count = 0;
            for (String event : events) {
                if (event.startsWith(prefix)) {
                    count++;
                }
            }
            return count;
        }
    }
}
