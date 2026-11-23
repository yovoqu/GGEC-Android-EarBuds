package com.yovo.biz_ggec_hs.internal.center.command;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.yovo.biz_ggec_hs.internal.center.protocol.Command;
import com.yovo.biz_ggec_hs.internal.center.protocol.CommandCompletion;
import com.yovo.biz_ggec_hs.internal.center.protocol.musiccmd.EQModeCommand;

import org.junit.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 命令系统收发健壮性回归（H-14 写窗口丢帧 / 发送完成看门狗）。
 *
 * <p>直接驱动包私有 {@link CommandScheduler}：内联 {@link CommandScheduler.Dispatcher} 让迁移同步发生，
 * 可控 {@link CommandScheduler.Transport} 让测试精确决定“何时回 onStarted/onCompleted、何时投喂应答帧”。
 * 不 sleep、不轮询，只用门闩等待。</p>
 */
public class HsCommandSystemHardeningTest {

    /** {@code EQModeCommand(MUSIC)} 的上行载荷。 */
    private static final String EQ_SET_MUSIC = "99EC830001001234";
    /** V11 EQ 回显应答（mode=0），与 {@link #EQ_SET_MUSIC} 同形。 */
    private static final String EQ_V11_RESPONSE = "99EC830001001234";
    private static final String MAC = "00:11:22:33:44:55";

    /**
     * H-14：应答在“已发出、尚未 onStarted/onCompleted”窗口内到达时必须被缓冲，而不是丢帧。
     * 若丢帧，命令只能超时返回 TIMEOUT；缓冲回放则应成功。
     */
    @Test
    public void responseBeforeWriteStartedIsBufferedNotDropped() throws Exception {
        ManualDispatcher dispatcher = new ManualDispatcher();
        DeferredTransport transport = new DeferredTransport();
        CommandScheduler scheduler = newScheduler(dispatcher, transport);

        AtomicReference<CommandCompletion> result = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        EQModeCommand command = new EQModeCommand(EQModeCommand.MUSIC_MODE);
        command.addCompletionListener((cmd, completion) -> {
            result.set(completion);
            done.countDown();
        });

        scheduler.submit(command, 1L);
        assertNotNull("命令必须已下发并拿到写入回调", transport.callback);

        // 尚未 onStarted：应答帧到达（旧实现会 return false 丢弃）。
        boolean claimed = scheduler.onResponseCandidate(1L, EQ_V11_RESPONSE);
        assertTrue("写完成前到达的匹配帧必须被当前命令认领（缓冲）", claimed);

        // 现在才把写回调放出来：started -> completed，缓冲帧应被回放并完成命令。
        transport.started();
        transport.completed(0);

        assertTrue("命令必须完成（缓冲回放）", done.await(3, TimeUnit.SECONDS));
        assertNotNull(result.get());
        assertEquals("应答被缓冲回放后应成功，而不是超时/丢弃",
                Command.ResultCode.SUCCESS, result.get().getCode());
    }

    /**
     * 发送完成看门狗：链路对一次写既不回 onCompleted 也不回 onTimeout 时，
     * 看门狗必须把命令以 TIMEOUT 收尾，避免永久占用串行队列。
     */
    @Test
    public void writeThatNeverCompletesTimesOutViaWatchdog() throws Exception {
        ManualDispatcher dispatcher = new ManualDispatcher();
        DeferredTransport transport = new DeferredTransport();
        CommandScheduler scheduler = newScheduler(dispatcher, transport);

        AtomicReference<CommandCompletion> result = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        EQModeCommand command = new EQModeCommand(EQModeCommand.MUSIC_MODE);
        command.addCompletionListener((cmd, completion) -> {
            result.set(completion);
            done.countDown();
        });

        scheduler.submit(command, 1L);
        assertNotNull(transport.callback);
        // 完全不回调写入结果；手动触发已排队的看门狗任务。
        dispatcher.fireScheduled();

        assertTrue("看门狗必须收尾命令", done.await(3, TimeUnit.SECONDS));
        assertNotNull(result.get());
        assertEquals("写永不完成时必须以 TIMEOUT 收尾",
                Command.ResultCode.TIMEOUT, result.get().getCode());
    }

    /**
     * 真机回归（HS02）：设备收到命令后在 Notify 通道回匹配应答帧，但命令特征的 GATT 写
     * 可能永远不回报 onCharacteristicWrite。匹配应答即证明写已送达，命令必须据此完成，
     * 而不是缓冲后等到 10s GATT 超时。
     */
    @Test
    public void matchingResponseCompletesEvenWhenWriteNeverReportsCompletion() throws Exception {
        ManualDispatcher dispatcher = new ManualDispatcher();
        DeferredTransport transport = new DeferredTransport();
        CommandScheduler scheduler = newScheduler(dispatcher, transport);
        scheduler.setTrustResponseAsWriteCompletion(true);

        AtomicReference<CommandCompletion> result = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        EQModeCommand command = new EQModeCommand(EQModeCommand.MUSIC_MODE);
        command.addCompletionListener((cmd, completion) -> {
            result.set(completion);
            done.countDown();
        });

        scheduler.submit(command, 1L);
        assertNotNull(transport.callback);
        // 关键：不调用 transport.started()/completed()，只喂匹配应答帧。
        boolean claimed = scheduler.onResponseCandidate(1L, EQ_V11_RESPONSE);
        assertTrue("匹配应答必须被当前命令认领", claimed);

        assertTrue("设备回匹配应答即证明写已送达，命令必须完成",
                done.await(3, TimeUnit.SECONDS));
        assertNotNull(result.get());
        assertEquals("匹配应答驱动完成，不得超时",
                Command.ResultCode.SUCCESS, result.get().getCode());
    }

    private static CommandScheduler newScheduler(ManualDispatcher dispatcher,
                                                 DeferredTransport transport) {        CommandScheduler scheduler = new CommandScheduler(
                dispatcher, transport, snapshot -> { }, new CommandPolicyRegistry());
        scheduler.setSession(1L, true);
        return scheduler;
    }

    /** 内联执行 {@code execute}；{@code schedule} 只登记任务，由测试按需 {@link #fireScheduled()} 触发。 */
    private static final class ManualDispatcher implements CommandScheduler.Dispatcher {
        private final Deque<Runnable> scheduled = new ArrayDeque<>();

        @Override
        public void execute(Runnable runnable) {
            if (runnable != null) {
                runnable.run();
            }
        }

        @Override
        public CommandScheduler.Cancelable schedule(Runnable runnable, long delayMs) {
            scheduled.addLast(runnable);
            return () -> scheduled.remove(runnable);
        }

        @Override
        public long nowMs() {
            return 1_000L;
        }

        void fireScheduled() {
            List<Runnable> snapshot = new ArrayList<>(scheduled);
            scheduled.clear();
            for (Runnable runnable : snapshot) {
                runnable.run();
            }
        }
    }

    /** 只登记写入回调；由测试显式推进 onStarted / onCompleted。 */
    private static final class DeferredTransport implements CommandScheduler.Transport {
        private CommandScheduler.Transport.Callback callback;

        @Override
        public boolean send(long sessionId, String payload, Callback callback) {
            this.callback = callback;
            return true;
        }

        void started() {
            callback.onStarted();
        }

        void completed(int status) {
            callback.onCompleted(status);
        }
    }
}
