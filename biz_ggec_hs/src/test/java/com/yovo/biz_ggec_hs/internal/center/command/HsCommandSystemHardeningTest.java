package com.yovo.biz_ggec_hs.internal.center.command;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
 * 命令系统收发健壮性回归（响应帧驱动完成，架构重构 P2）。
 *
 * <p>直接驱动包私有 {@link CommandScheduler}：内联 {@link CommandScheduler.Dispatcher} 让迁移同步发生，
 * 可控 {@link CommandScheduler.Transport} 让测试精确决定「是否受理」并在任意时刻投喂应答帧。
 * 不 sleep、不轮询，只用门闩等待。</p>
 *
 * <p>语义：{@code transport.send} 同步返回「是否受理」；受理后命令置 SENT 并启动唯一有界响应超时，
 * 入站匹配响应帧即完成。不再存在任何写完成回调（onStarted/onCompleted/onTimeout）。</p>
 */
public class HsCommandSystemHardeningTest {

    /** {@code EQModeCommand(MUSIC)} 的上行载荷。 */
    private static final String EQ_SET_MUSIC = "99EC830001001234";
    /** V11 EQ 回显应答（mode=0），与 {@link #EQ_SET_MUSIC} 同形。 */
    private static final String EQ_V11_RESPONSE = "99EC830001001234";

    /**
     * ① 受理后入站匹配响应帧 → 命令 SUCCESS 完成，全程不依赖任何写完成回调。
     */
    @Test
    public void acceptedThenMatchingResponseCompletesWithoutWriteCallback() throws Exception {
        ManualDispatcher dispatcher = new ManualDispatcher();
        FakeTransport transport = new FakeTransport();
        CommandScheduler scheduler = newScheduler(dispatcher, transport);

        AtomicReference<CommandCompletion> result = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        EQModeCommand command = new EQModeCommand(EQModeCommand.MUSIC_MODE);
        command.addCompletionListener((cmd, completion) -> {
            result.set(completion);
            done.countDown();
        });

        scheduler.submit(command, 1L);

        assertEquals("受理即下发一次", 1, transport.sentPayloads.size());
        assertEquals("下发的应是命令编码后的载荷", EQ_SET_MUSIC, transport.sentPayloads.get(0));
        assertEquals("受理后命令状态应为 SENT", Command.State.SENT, command.getState());

        boolean claimed = transport.fireResponse(scheduler, 1L, EQ_V11_RESPONSE);
        assertTrue("受理后的匹配响应帧必须被当前命令认领", claimed);

        assertTrue("入站匹配响应帧必须驱动完成", done.await(3, TimeUnit.SECONDS));
        assertNotNull(result.get());
        assertEquals("匹配响应帧驱动完成，应为 SUCCESS",
                Command.ResultCode.SUCCESS, result.get().getCode());
        assertEquals("命令应落到 COMPLETED 终态", Command.State.COMPLETED, command.getState());
    }

    /**
     * ② {@code send} 同步返回 false（未被链路受理）→ 命令立即 FAILED，且不启动响应超时。
     */
    @Test
    public void rejectedSendFailsCommandImmediately() throws Exception {
        ManualDispatcher dispatcher = new ManualDispatcher();
        FakeTransport transport = new FakeTransport();
        transport.accept = false;
        CommandScheduler scheduler = newScheduler(dispatcher, transport);

        AtomicReference<CommandCompletion> result = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        EQModeCommand command = new EQModeCommand(EQModeCommand.MUSIC_MODE);
        command.addCompletionListener((cmd, completion) -> {
            result.set(completion);
            done.countDown();
        });

        scheduler.submit(command, 1L);

        assertTrue("未被受理必须立即收尾", done.await(3, TimeUnit.SECONDS));
        assertNotNull(result.get());
        assertEquals("未被链路受理应为 FAILED",
                Command.ResultCode.FAILED, result.get().getCode());
        assertEquals("命令应落到 FAILED 终态", Command.State.FAILED, command.getState());

        // 未被受理时不存在响应超时：推进已排队任务（仅串行队列的下一轮 drain）也不应改写终态。
        dispatcher.fireScheduled();
        assertEquals("未被受理的命令不得被后续超时改写成 TIMEOUT",
                Command.ResultCode.FAILED, result.get().getCode());
    }

    /**
     * ③ 受理后无任何响应帧 → 驱动器推进排队的响应超时 → 命令 TIMEOUT。
     */
    @Test
    public void acceptedWithoutResponseTimesOutViaResponseTimeout() throws Exception {
        ManualDispatcher dispatcher = new ManualDispatcher();
        FakeTransport transport = new FakeTransport();
        CommandScheduler scheduler = newScheduler(dispatcher, transport);

        AtomicReference<CommandCompletion> result = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        EQModeCommand command = new EQModeCommand(EQModeCommand.MUSIC_MODE);
        command.addCompletionListener((cmd, completion) -> {
            result.set(completion);
            done.countDown();
        });

        scheduler.submit(command, 1L);

        assertEquals("受理即下发一次", 1, transport.sentPayloads.size());
        assertEquals("受理后命令状态应为 SENT", Command.State.SENT, command.getState());
        assertEquals("受理后应只排队一个响应超时任务", 1, dispatcher.pendingCount());

        // 不投喂任何响应帧，直接推进排队的响应超时任务。
        dispatcher.fireScheduled();

        assertTrue("响应超时必须收尾命令", done.await(3, TimeUnit.SECONDS));
        assertNotNull(result.get());
        assertEquals("受理后无响应帧应以 TIMEOUT 收尾",
                Command.ResultCode.TIMEOUT, result.get().getCode());
        assertEquals("命令应落到 FAILED 终态", Command.State.FAILED, command.getState());
    }

    /**
     * ④ 迟到帧隔离：超时后登记同族保护窗口，窗口内同族帧被隔离、同族重发让出延迟，窗口到期放行。
     * 直接验证 {@link LateFrameGuard#register(Command, long)} 新签名（去掉写开始门槛）。
     */
    @Test
    public void lateFrameGuardIsolatesSameFamilyAfterRegister() {
        LateFrameGuard guard = new LateFrameGuard();
        EQModeCommand command = new EQModeCommand(EQModeCommand.MUSIC_MODE);
        long now = 1_000L;

        guard.register(command, now);

        assertTrue("登记后同族迟到帧应被隔离", guard.isQuarantined(EQ_V11_RESPONSE, now));
        assertTrue("保护窗口内同族重发应让出延迟", guard.delayFor(command, now) > 0L);
        assertFalse("保护窗口到期后应放行",
                guard.isQuarantined(EQ_V11_RESPONSE, now + command.getLateResponseGuardMs() + 1L));
    }

    private static CommandScheduler newScheduler(ManualDispatcher dispatcher,
                                                 FakeTransport transport) {
        CommandScheduler scheduler = new CommandScheduler(
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

        int pendingCount() {
            return scheduled.size();
        }

        void fireScheduled() {
            List<Runnable> snapshot = new ArrayList<>(scheduled);
            scheduled.clear();
            for (Runnable runnable : snapshot) {
                runnable.run();
            }
        }
    }

    /** 记录下发载荷、可配置是否受理；由测试显式投喂应答帧（{@link #fireResponse}）。 */
    private static final class FakeTransport implements CommandScheduler.Transport {
        private final List<String> sentPayloads = new ArrayList<>();
        boolean accept = true;

        @Override
        public boolean send(long sessionId, String payload) {
            sentPayloads.add(payload);
            return accept;
        }

        /** 模拟设备在 Notify 通道回一帧业务响应，投递到调度器应答入口。 */
        boolean fireResponse(CommandScheduler scheduler, long sessionId, String frame) {
            return scheduler.onResponseCandidate(sessionId, frame);
        }
    }
}
