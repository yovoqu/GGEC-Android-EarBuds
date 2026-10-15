package com.yovo.lib_bluetooth.internal.classic.protocol;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.yovo.lib_bluetooth.internal.common.concurrency.YoTaskScheduler;
import com.yovo.lib_bluetooth.internal.common.concurrency.YoTimeoutHandle;
import com.yovo.lib_bluetooth.internal.classic.protocol.codec.YoCbtRawPacketCodec;
import com.yovo.lib_bluetooth.internal.classic.protocol.command.YoCbtCommand;
import com.yovo.lib_bluetooth.internal.classic.protocol.command.YoCbtCommandObserver;
import com.yovo.lib_bluetooth.internal.classic.protocol.command.YoCbtCommandRequest;
import com.yovo.lib_bluetooth.internal.classic.protocol.command.YoCbtCommandResult;
import com.yovo.lib_bluetooth.internal.classic.protocol.command.YoCbtCommandSession;
import com.yovo.lib_bluetooth.internal.classic.protocol.command.YoCbtCommandStatus;
import com.yovo.lib_bluetooth.internal.classic.protocol.packet.YoCbtPacket;
import com.yovo.lib_bluetooth.internal.classic.protocol.retry.YoCbtRetryPolicy;
import com.yovo.lib_bluetooth.internal.classic.spp.connection.YoCbtSppConnectionId;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description {@link YoCbtCommandSession} 命令提交与响应匹配的单元测试。
 */

public class YoCbtCommandSessionTest {

    private YoCbtSppConnectionId connectionId;
    private YoCbtCommandSession.Sender sender;
    private List<byte[]> sentBytes;
    private TestScheduler scheduler;

    @Before
    public void setUp() {
        connectionId = new YoCbtSppConnectionId("test-conn-1");
        sentBytes = new ArrayList<>();
        sender = bytes -> {
            sentBytes.add(bytes);
            return com.yovo.lib_bluetooth.internal.common.result.YoResult.success(null);
        };
        scheduler = new TestScheduler();
    }

    private YoCbtCommandSession createSession() {
        return new YoCbtCommandSession(
                connectionId, new YoCbtRawPacketCodec(), scheduler, sender, Runnable::run);
    }

    @Test
    public void submitEncodesAndSends() {
        YoCbtCommandSession session = createSession();
        YoCbtPacket packet = new YoCbtPacket("hello".getBytes());
        YoCbtCommand command = new YoCbtCommand(packet);
        YoCbtCommandRequest request = YoCbtCommandRequest.builder("cmd-1", command).build();
        var result = session.submit(request);
        assertTrue(result.isSuccess());
        assertEquals(1, sentBytes.size());
    }

    @Test
    public void responseMatching() {
        YoCbtCommandSession session = createSession();
        List<YoCbtCommandResult> results = new ArrayList<>();
        session.observe((connId, r) -> results.add(r));

        YoCbtPacket requestPacket = new YoCbtPacket("req-data".getBytes());
        YoCbtCommand command = new YoCbtCommand(requestPacket);
        YoCbtCommandRequest request = YoCbtCommandRequest.builder("cmd-match", command)
                .responseMatcher((req, pkt) -> true)
                .build();
        session.submit(request);

        session.onBytesReceived("resp-data".getBytes());

        assertEquals(2, results.size());
        assertEquals(YoCbtCommandStatus.QUEUED, results.get(0).getStatus());
        assertEquals(YoCbtCommandStatus.RESPONSE_RECEIVED, results.get(1).getStatus());
    }

    @Test
    public void timeoutFiresCallback() {
        YoCbtCommandSession session = createSession();
        List<YoCbtCommandResult> results = new ArrayList<>();
        session.observe((connId, r) -> results.add(r));

        YoCbtPacket packet = new YoCbtPacket("timeout-test".getBytes());
        YoCbtCommand command = new YoCbtCommand(packet);
        YoCbtCommandRequest request = YoCbtCommandRequest.builder("cmd-timeout", command)
                .timeoutMillis(100)
                .retryPolicy(YoCbtRetryPolicy.none())
                .build();
        session.submit(request);

        scheduler.fireAll();
        scheduler.waitForCompletion();

        assertEquals(2, results.size());
        assertEquals(YoCbtCommandStatus.TIMEOUT, results.get(1).getStatus());
    }

    @Test
    public void retryOnTimeout() {
        YoCbtCommandSession session = createSession();
        List<YoCbtCommandResult> results = new ArrayList<>();
        session.observe((connId, r) -> results.add(r));

        YoCbtPacket packet = new YoCbtPacket("retry-test".getBytes());
        YoCbtCommand command = new YoCbtCommand(packet);
        YoCbtCommandRequest request = YoCbtCommandRequest.builder("cmd-retry", command)
                .timeoutMillis(50)
                .retryPolicy(new YoCbtRetryPolicy(3, 0))
                .build();
        session.submit(request);

        int initialSent = sentBytes.size();
        scheduler.fireAll();
        scheduler.waitForCompletion();

        // 重试后应再次发送
        assertTrue(sentBytes.size() > initialSent);
    }

    @Test
    public void clearRemovesPending() {
        YoCbtCommandSession session = createSession();
        List<YoCbtCommandResult> results = new ArrayList<>();
        session.observe((connId, r) -> results.add(r));

        YoCbtPacket packet = new YoCbtPacket("clear-test".getBytes());
        YoCbtCommand command = new YoCbtCommand(packet);
        YoCbtCommandRequest request = YoCbtCommandRequest.builder("cmd-clear", command).build();
        session.submit(request);

        session.clear();
        scheduler.fireAll();

        assertEquals(1, results.size());
        assertEquals(YoCbtCommandStatus.QUEUED, results.get(0).getStatus());
    }

    @Test
    public void removeObserverStopsNotifications() {
        YoCbtCommandSession session = createSession();
        List<YoCbtCommandResult> results = new ArrayList<>();
        YoCbtCommandObserver observer = (connId, r) -> results.add(r);
        session.observe(observer);

        YoCbtPacket packet = new YoCbtPacket("obs-test".getBytes());
        YoCbtCommand command = new YoCbtCommand(packet);
        YoCbtCommandRequest request = YoCbtCommandRequest.builder("cmd-obs", command).build();
        session.submit(request);

        assertEquals(1, results.size());
        session.removeObserver(observer);
        session.onBytesReceived("any".getBytes());
        assertEquals(1, results.size());
    }

    private static final class TestScheduler implements YoTaskScheduler {
        private final List<Runnable> tasks = new ArrayList<>();

        @Override
        public YoTimeoutHandle schedule(Runnable task, long delayMillis) {
            tasks.add(task);
            return () -> tasks.remove(task);
        }

        void fireAll() {
            List<Runnable> copy = new ArrayList<>(tasks);
            tasks.clear();
            for (Runnable t : copy) t.run();
        }

        void waitForCompletion() {
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
        }
    }
}
