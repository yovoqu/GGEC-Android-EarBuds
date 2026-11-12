package com.yovo.biz_ggec_hs.internal.center.protocol;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.yovo.biz_ggec_hs.internal.center.port.HsConnectionEvents;
import com.yovo.biz_ggec_hs.internal.center.protocol.musiccmd.EQModeCommand;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 会话生命周期的两条边界：起点与终点必须对称，事件必须只有一处发送。
 *
 * <p>用例全部用门闩等待，不 sleep、不轮询：链路事件由 {@link FakeHsCommandLink} 显式触发，
 * 引擎状态只在引擎线程上推进。</p>
 */
public class HsSessionLifecycleTest {
    /** {@code EarColorCommand} 的上行载荷。 */
    private static final String EAR_COLOR_QUERY = "99EC910001001234";
    /** 耳机颜色回包：颜色字节 {@code 0x2A}。 */
    private static final String EAR_COLOR_RESPONSE = "99EC9100012A1234";
    /** {@code EQModeCommand} 的上行载荷。 */
    private static final String EQ_MODE_QUERY = "99EC830001001234";
    private static final String MAC_A = "00:11:22:33:44:55";
    private static final String MAC_B = "66:77:88:99:AA:BB";

    /** 会话切换时旧会话在途命令被中止的统一原因。 */
    private static final String SESSION_ENDED_REASON = "目标连接会话已结束";

    /**
     * READY 中再次 {@code connect(B)}：旧会话必须真实结束（在途命令中止、{@code onDisconnected}
     * 先到），并开出一个新会话（新 {@code sessionId}、重新补查颜色、{@code onConnected} 到达）。
     */
    @Test
    public void readyThenConnectAgainStartsFreshSession() throws Exception {
        FakeHsCommandLink link = new FakeHsCommandLink();
        HsProtocolEngine engine = new HsProtocolEngine(link);
        try {
            List<String> lifecycle = Collections.synchronizedList(new ArrayList<>());
            CountDownLatch secondConnected = new CountDownLatch(1);
            AtomicInteger connectedCount = new AtomicInteger();
            engine.setConnectionEvents(new HsConnectionEvents() {
                @Override
                public void onConnected(String macAddress) {
                    lifecycle.add("connected:" + macAddress);
                    if (connectedCount.incrementAndGet() == 2) {
                        secondConnected.countDown();
                    }
                }

                @Override
                public void onDisconnected(String reason) {
                    lifecycle.add("disconnected:" + reason);
                }
            });

            engine.connect(MAC_A);
            link.ready();
            assertTrue(link.awaitWritten(EAR_COLOR_QUERY));
            long firstSession = engine.sessionId();
            assertTrue("第一次 READY 必须建立会话", firstSession > 0L);

            // 旧会话在途命令：自动补查占着串行队列，这条设置命令排在后面，切换时必须被中止。
            CountDownLatch aborted = new CountDownLatch(1);
            AtomicReference<CommandCompletion> abortResult = new AtomicReference<>();
            engine.submit(new EQModeCommand(EQModeCommand.MUSIC_MODE), (command, completion) -> {
                abortResult.set(completion);
                aborted.countDown();
            });

            engine.connect(MAC_B);

            assertTrue("旧会话的在途命令必须收到终止", aborted.await(3, TimeUnit.SECONDS));
            CommandCompletion abortedCompletion = abortResult.get();
            assertNotNull(abortedCompletion);
            assertEquals("会话结束时在途命令按 RESERVED 撤销", Command.ResultCode.RESERVED, abortedCompletion.getCode());
            assertEquals(SESSION_ENDED_REASON, abortedCompletion.getMessage());

            link.ready();
            assertTrue("换目标后必须开出新会话并通知 onConnected",
                    secondConnected.await(3, TimeUnit.SECONDS));
            assertNotEquals("换目标必须换会话身份", firstSession, engine.sessionId());
            assertEquals("每个会话只补查一次耳机颜色", 2, link.awaitCountWithPrefix("99EC91", 2));
            assertTrue(engine.isReady());

            int disconnectedIndex =
                    lifecycle.indexOf("disconnected:" + HsProtocolEngine.REASON_TARGET_SWITCHED);
            int secondConnectedIndex = lifecycle.indexOf("connected:" + MAC_B);
            assertTrue("旧会话断连必须先于新会话就绪", disconnectedIndex >= 0);
            assertTrue("旧会话断连必须先于新会话就绪",
                    disconnectedIndex < secondConnectedIndex);
            assertEquals("一次换目标只发一条断连", 1, countDisconnected(lifecycle));
        } finally {
            engine.release();
        }
    }

    /**
     * READY 之后链路失效：链路按契约先给原因、再给状态变化。恰发一次 {@code onDisconnected}，
     * 带的是这次失效的真实原因，且不得再补一条 {@code onConnectFailed}。
     */
    @Test
    public void linkErrorAfterReadyEmitsOnlyDisconnected() throws Exception {
        FakeHsCommandLink link = new FakeHsCommandLink();
        HsProtocolEngine engine = new HsProtocolEngine(link);
        try {
            AtomicReference<String> reason = new AtomicReference<>();
            AtomicInteger disconnected = new AtomicInteger();
            CountDownLatch downSeen = new CountDownLatch(1);
            AtomicInteger connectFailed = new AtomicInteger();
            engine.setConnectionEvents(new HsConnectionEvents() {
                @Override
                public void onDisconnected(String value) {
                    reason.set(value);
                    disconnected.incrementAndGet();
                    downSeen.countDown();
                }

                @Override
                public void onConnectFailed(String message) {
                    connectFailed.incrementAndGet();
                }
            });

            engine.connect(MAC_A);
            link.ready();
            assertTrue(link.awaitWritten(EAR_COLOR_QUERY));

            link.linkError("ble-io");
            assertTrue(downSeen.await(3, TimeUnit.SECONDS));
            // 屏障：断连与其后的状态变化进同一引擎队列，排空后才允许断言事件条数。
            awaitEngineProcessed(engine);

            assertNotNull("断连必须带真实原因，不得为 null", reason.get());
            assertEquals("断连必须带这次失效的真实原因", "ble-io", reason.get());
            assertEquals("一次链路失效只发一条断连", 1, disconnected.get());
            assertEquals("已建立的会话失效不得再发 onConnectFailed", 0, connectFailed.get());
            assertFalse(engine.isReady());
            assertEquals(0L, engine.sessionId());
        } finally {
            engine.release();
        }
    }

    /** 尚未 READY 即链路失效：恰发一次 {@code onConnectFailed}，不发 {@code onDisconnected}。 */
    @Test
    public void linkErrorBeforeReadyEmitsConnectFailedOnce() throws Exception {
        FakeHsCommandLink link = new FakeHsCommandLink();
        HsProtocolEngine engine = new HsProtocolEngine(link);
        try {
            AtomicInteger connectFailed = new AtomicInteger();
            AtomicInteger disconnected = new AtomicInteger();
            CountDownLatch failed = new CountDownLatch(1);
            engine.setConnectionEvents(new HsConnectionEvents() {
                @Override
                public void onConnectFailed(String reason) {
                    connectFailed.incrementAndGet();
                    failed.countDown();
                }

                @Override
                public void onDisconnected(String reason) {
                    disconnected.incrementAndGet();
                }
            });

            engine.connect(MAC_A);
            link.linkError("connect-failed");

            assertTrue(failed.await(3, TimeUnit.SECONDS));
            assertEquals("未就绪的链路失效恰发一次连接失败", 1, connectFailed.get());
            assertEquals("未建立会话不得发断连", 0, disconnected.get());
            assertFalse(engine.isReady());
        } finally {
            engine.release();
        }
    }

    /** 就绪看门狗超时改由引擎统一上报：未就绪时恰发一次 {@code onConnectFailed}。 */
    @Test
    public void readyTimeoutBeforeReadyEmitsConnectFailedOnce() throws Exception {
        FakeHsCommandLink link = new FakeHsCommandLink();
        HsProtocolEngine engine = new HsProtocolEngine(link);
        try {
            AtomicInteger connectFailed = new AtomicInteger();
            AtomicReference<String> reason = new AtomicReference<>();
            CountDownLatch failed = new CountDownLatch(1);
            engine.setConnectionEvents(new HsConnectionEvents() {
                @Override
                public void onConnectFailed(String message) {
                    reason.set(message);
                    connectFailed.incrementAndGet();
                    failed.countDown();
                }
            });

            engine.connect(MAC_A);
            engine.onConnectAttemptTimedOut();

            assertTrue(failed.await(3, TimeUnit.SECONDS));
            assertEquals(1, connectFailed.get());
            assertEquals(HsProtocolEngine.REASON_READY_TIMEOUT, reason.get());
        } finally {
            engine.release();
        }
    }

    /** 已经就绪再看门狗超时：链路确实慢了一场，但这已经不是失败，引擎不得补发任何事件。 */
    @Test
    public void readyTimeoutAfterReadyEmitsNothing() throws Exception {
        FakeHsCommandLink link = new FakeHsCommandLink();
        HsProtocolEngine engine = new HsProtocolEngine(link);
        try {
            AtomicInteger connectFailed = new AtomicInteger();
            AtomicInteger disconnected = new AtomicInteger();
            engine.setConnectionEvents(new HsConnectionEvents() {
                @Override
                public void onConnectFailed(String reason) {
                    connectFailed.incrementAndGet();
                }

                @Override
                public void onDisconnected(String reason) {
                    disconnected.incrementAndGet();
                }
            });

            engine.connect(MAC_A);
            link.ready();
            assertTrue(link.awaitWritten(EAR_COLOR_QUERY));
            // 让自动补查收尾，队列空出来后才能用一次真实下发当屏障。
            link.inbound(EAR_COLOR_RESPONSE);

            engine.onConnectAttemptTimedOut();
            engine.submit(new EQModeCommand(EQModeCommand.MUSIC_MODE), null);
            // 该命令被写下，说明超时任务已经被引擎处理过（同一串行队列）。
            assertTrue(link.awaitWritten(EQ_MODE_QUERY));

            assertEquals(0, connectFailed.get());
            assertEquals(0, disconnected.get());
            assertTrue(engine.isReady());
        } finally {
            engine.release();
        }
    }

    /** 会话已收尾之后才到的链路错误：只记录日志，不再发第二个事件。 */
    @Test
    public void linkErrorAfterSessionEndedEmitsNothing() throws Exception {
        FakeHsCommandLink link = new FakeHsCommandLink();
        HsProtocolEngine engine = new HsProtocolEngine(link);
        try {
            AtomicInteger disconnected = new AtomicInteger();
            AtomicInteger connectFailed = new AtomicInteger();
            CountDownLatch downSeen = new CountDownLatch(1);
            engine.setConnectionEvents(new HsConnectionEvents() {
                @Override
                public void onDisconnected(String reason) {
                    disconnected.incrementAndGet();
                    downSeen.countDown();
                }

                @Override
                public void onConnectFailed(String reason) {
                    connectFailed.incrementAndGet();
                }
            });

            engine.connect(MAC_A);
            link.ready();
            assertTrue(link.awaitWritten(EAR_COLOR_QUERY));

            // 主动断开：链路只报状态变化，不带原因。
            engine.disconnect();
            assertTrue(downSeen.await(3, TimeUnit.SECONDS));

            // 会话已结束后的迟到错误报告。
            link.linkError("late-error");
            awaitEngineProcessed(engine);

            assertEquals("一次断开只发一条断连", 1, disconnected.get());
            assertEquals("会话已结束后不得再报连接失败", 0, connectFailed.get());
            assertFalse(engine.isReady());
        } finally {
            engine.release();
        }
    }

    /**
     * 引擎处理完此前排队事件的屏障：没有会话时提交一条命令会在引擎轮次里被立即拒绝，
     * 它的回调到达即证明该命令之前的任务都已执行完。门闩等待，不 sleep。
     */
    private static void awaitEngineProcessed(HsProtocolEngine engine) throws InterruptedException {
        CountDownLatch processed = new CountDownLatch(1);
        engine.submit(new EQModeCommand(EQModeCommand.MUSIC_MODE),
                (command, completion) -> processed.countDown());
        assertTrue("引擎未在预算内处理完排队任务", processed.await(3, TimeUnit.SECONDS));
    }

    private static int countDisconnected(List<String> lifecycle) {
        int count = 0;
        synchronized (lifecycle) {
            for (String event : lifecycle) {
                if (event.startsWith("disconnected:")) {
                    count++;
                }
            }
        }
        return count;
    }
}
