package com.yovo.biz_ggec_hs.internal.center.command;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.yovo.biz_ggec_hs.internal.center.protocol.Command;
import com.yovo.biz_ggec_hs.internal.center.protocol.CommandCompletion;
import com.yovo.biz_ggec_hs.internal.center.protocol.devicecmd.GetStorageCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.devicecmd.StorageCapacityResult;
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

    // ================= 追加回归：多帧 inter-frame / GET 单飞+缓存 / SET LATEST_WINS =================

    /** 自定义两帧测试命令的请求载荷（任意非空即可，{@link FakeTransport} 只记录）。 */
    private static final String TWO_FRAME_REQUEST = "99EC990001001234";
    /** 第一帧：载荷末字节 00，{@code parseResponse} 返回 pending。 */
    private static final String TWO_FRAME_PENDING = "99EC990001001234";
    /** 第二帧：载荷末字节 01，{@code parseResponse} 返回 complete。 */
    private static final String TWO_FRAME_COMPLETE = "99EC990001011234";

    /** {@code GetStorageCommand(EAR_LEFT)} 的合法单耳回包：ear=00、total=1000、avail=100。 */
    private static final String STORAGE_LEFT_RESPONSE =
            "99ECBA" + "0009" + "00" + "E8030000" + "64000000" + "1234";

    /** EQ 电影 / 新闻模式的载荷（同形的 V11 回显，既是请求编码也是合法应答帧）。 */
    private static final String EQ_MOVIE = "99EC830001011234";
    private static final String EQ_NEWS = "99EC830001021234";

    /**
     * ⑤ 多帧 inter-frame：首帧 pending 后仍非终态，并按 inter-frame 超时重臂；次帧到达后完成；
     * 完成后再来的帧被忽略（不回退、不覆盖 COMPLETED）。
     *
     * <p>用自定义两帧命令（未进目录，配 ACTION 策略）直接驱动 {@code processFrame} 的
     * pending → inter-frame → complete 状态机；全程手动推进时间、不 sleep。</p>
     */
    @Test
    public void multiFramePendingRearmsInterFrameTimeoutThenCompletes() throws Exception {
        ManualDispatcher dispatcher = new ManualDispatcher();
        FakeTransport transport = new FakeTransport();
        CommandScheduler scheduler = newScheduler(
                dispatcher, transport, HsCommandSystemHardeningTest::actionPolicy);

        AtomicReference<CommandCompletion> result = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        TwoFrameCommand command = new TwoFrameCommand();
        command.addCompletionListener((cmd, completion) -> {
            result.set(completion);
            done.countDown();
        });

        scheduler.submit(command, 1L);

        assertEquals("受理即下发一次", 1, transport.sentPayloads.size());
        assertEquals("下发载荷应为命令编码结果", TWO_FRAME_REQUEST, transport.sentPayloads.get(0));
        assertEquals("受理后应为 SENT", Command.State.SENT, command.getState());
        assertEquals("首帧前只应排队一个初始响应超时", 1, dispatcher.pendingCount());
        assertEquals("首帧前的超时应为命令声明的首帧超时",
                command.getTimeoutMs(), dispatcher.lastScheduledDelay());
        assertNull("首帧前不应有完成结果", command.getCompletion());

        // 第一帧：pending → 非终态，且按 inter-frame 超时重臂。
        assertTrue("首帧应被当前命令认领",
                transport.fireResponse(scheduler, 1L, TWO_FRAME_PENDING));
        assertEquals("首帧后应处于 RECEIVING", Command.State.RECEIVING, command.getState());
        assertNull("首帧后仍不应有完成结果", command.getCompletion());
        assertEquals("首帧后应只保留一个重臂后的超时任务", 1, dispatcher.pendingCount());
        assertEquals("首帧后应按 inter-frame 超时重臂",
                command.getInterFrameTimeoutMs(), dispatcher.lastScheduledDelay());

        // 第二帧：complete → 完成。
        assertTrue("次帧应被当前命令认领",
                transport.fireResponse(scheduler, 1L, TWO_FRAME_COMPLETE));
        assertTrue("次帧必须驱动完成", done.await(3, TimeUnit.SECONDS));
        assertNotNull(result.get());
        assertEquals("两帧聚合应以 SUCCESS 完成",
                Command.ResultCode.SUCCESS, result.get().getCode());
        assertEquals("命令应落到 COMPLETED 终态", Command.State.COMPLETED, command.getState());

        // 完成后再来帧被忽略：调度入口不再认领，命令状态与完成结果都不被回退/覆盖。
        assertFalse("完成后同族帧不应再被认领",
                transport.fireResponse(scheduler, 1L, TWO_FRAME_COMPLETE));
        assertEquals("完成后再来帧不得把状态回退/改写",
                Command.State.COMPLETED, command.getState());
        assertEquals("完成后完成结果不得被覆盖",
                Command.ResultCode.SUCCESS, command.getCompletion().getCode());
    }

    /**
     * ⑥ GET 单飞 + 缓存：
     * <ul>
     *   <li>在途同名 GET 作为 follower 合并，只下发一次，跟随者拿到首个命令的同一个 completion；</li>
     *   <li>TTL 内重复 GET 命中缓存，不再下发，直接回放缓存 completion；</li>
     *   <li>TTL 过期后重新下发。</li>
     * </ul>
     * 用真实 {@link GetStorageCommand}（目录 GET，缓存 TTL=1000ms）与下发计数断言，时间用
     * {@link ManualDispatcher#advanceTo(long)} 手动推进。
     */
    @Test
    public void getSingleFlightAndCacheTtlReuse() throws Exception {
        ManualDispatcher dispatcher = new ManualDispatcher();
        FakeTransport transport = new FakeTransport();
        CommandScheduler scheduler = newScheduler(dispatcher, transport);

        GetStorageCommand first = new GetStorageCommand(StorageCapacityResult.EAR_LEFT);
        GetStorageCommand follower = new GetStorageCommand(StorageCapacityResult.EAR_LEFT);

        scheduler.submit(first, 1L);
        assertEquals("首个 GET 下发一次", 1, transport.sentPayloads.size());
        assertEquals("首个 GET 应处于 SENT", Command.State.SENT, first.getState());

        scheduler.submit(follower, 1L);
        assertEquals("同名在途 GET 不得重复下发", 1, transport.sentPayloads.size());
        assertNull("follower 完成前不应有结果", follower.getCompletion());

        assertTrue("GET 回包应被首个命令认领",
                transport.fireResponse(scheduler, 1L, STORAGE_LEFT_RESPONSE));
        assertNotNull("首个 GET 应完成", first.getCompletion());
        assertEquals("首个 GET 应为 SUCCESS",
                Command.ResultCode.SUCCESS, first.getCompletion().getCode());
        assertNotNull("follower 应被回放完成", follower.getCompletion());
        assertSame("follower 必须拿到首发命令的同一个 completion",
                first.getCompletion(), follower.getCompletion());
        assertEquals("合并后仍只下发一次", 1, transport.sentPayloads.size());

        // TTL 内（now=1000，过期点=2000）重复 GET 命中缓存，不再下发。
        GetStorageCommand cached = new GetStorageCommand(StorageCapacityResult.EAR_LEFT);
        scheduler.submit(cached, 1L);
        assertNotNull("缓存命中应立即完成", cached.getCompletion());
        assertEquals("TTL 内命中缓存应为 SUCCESS",
                Command.ResultCode.SUCCESS, cached.getCompletion().getCode());
        assertSame("缓存命中应回放同一个 completion",
                first.getCompletion(), cached.getCompletion());
        assertEquals("TTL 内命中缓存不得下发", 1, transport.sentPayloads.size());

        // 推进到过期点之后（now=2001 > 2000），重新下发。
        dispatcher.advanceTo(2_001L);
        GetStorageCommand refetch = new GetStorageCommand(StorageCapacityResult.EAR_LEFT);
        scheduler.submit(refetch, 1L);
        assertEquals("TTL 过期后应重新下发", 2, transport.sentPayloads.size());
        assertEquals("重新下发后应处于 SENT", Command.State.SENT, refetch.getState());
        assertNull("重新下发后尚未完成", refetch.getCompletion());
    }

    /**
     * ⑦ SET LATEST_WINS 同值合并：主命令仍在合并窗口内、队列为空时，同 semanticKey 同值 SET
     * 以 JOIN_CURRENT 并入 follower，只下发一次；跟随者回放主命令的同一个完成结果。
     */
    @Test
    public void sameValueSetJoinsCurrentAndSendsOnce() throws Exception {
        ManualDispatcher dispatcher = new ManualDispatcher();
        FakeTransport transport = new FakeTransport();
        CommandScheduler scheduler = newScheduler(dispatcher, transport);

        EQModeCommand primary = new EQModeCommand(EQModeCommand.MUSIC_MODE);
        EQModeCommand follower = new EQModeCommand(EQModeCommand.MUSIC_MODE);

        scheduler.submit(primary, 1L);
        assertEquals("主命令下发一次", 1, transport.sentPayloads.size());
        assertEquals("主命令载荷正确", EQ_SET_MUSIC, transport.sentPayloads.get(0));
        assertEquals("主命令应处于 SENT", Command.State.SENT, primary.getState());

        scheduler.submit(follower, 1L);
        assertEquals("同值 SET 不得重复下发", 1, transport.sentPayloads.size());
        assertNull("follower 合并时不应有结果", follower.getCompletion());

        assertTrue("EQ 回显应被主命令认领",
                transport.fireResponse(scheduler, 1L, EQ_V11_RESPONSE));
        assertEquals("主命令应为 SUCCESS",
                Command.ResultCode.SUCCESS, primary.getCompletion().getCode());
        assertEquals("follower 应回放为 SUCCESS",
                Command.ResultCode.SUCCESS, follower.getCompletion().getCode());
        assertSame("follower 必须拿到主命令的同一个 completion",
                primary.getCompletion(), follower.getCompletion());
        assertEquals("同值 SET 全程只下发一次", 1, transport.sentPayloads.size());
    }

    /**
     * ⑧ SET LATEST_WINS 顶替（SUPERSEDE）：待发槽位上已有不同值的待发 SET 时，新值 SET 用自身顶替
     * 旧主命令——旧主命令以 superseded 立即成功完成且从不真实下发，仅新值被下发一次。
     * 用「先未就绪入队、再置就绪」精确隔离待发槽位路径。
     */
    @Test
    public void differentValueSetSupersedesPendingPrimary() throws Exception {
        ManualDispatcher dispatcher = new ManualDispatcher();
        FakeTransport transport = new FakeTransport();
        CommandScheduler scheduler = new CommandScheduler(
                dispatcher, transport, snapshot -> { }, new CommandPolicyRegistry());
        // 未就绪：SET 只入队待发、不触发下发，便于精确构造待发槽位。
        scheduler.setSession(1L, false);

        EQModeCommand oldPending = new EQModeCommand(EQModeCommand.MOVIE_MODE);
        EQModeCommand newer = new EQModeCommand(EQModeCommand.NEWS_MODE);

        scheduler.submit(oldPending, 1L);
        assertEquals("未就绪时不应下发", 0, transport.sentPayloads.size());
        assertNull("旧待发命令此时未完成", oldPending.getCompletion());

        scheduler.submit(newer, 1L);
        assertNotNull("被顶替的旧主命令应立即以 superseded 完成", oldPending.getCompletion());
        assertEquals("旧主命令应以 SUCCESS 完成（superseded）",
                Command.ResultCode.SUCCESS, oldPending.getCompletion().getCode());
        assertEquals("旧主命令应落到 COMPLETED 终态",
                Command.State.COMPLETED, oldPending.getState());
        assertEquals("顶替时仍不应下发任何命令", 0, transport.sentPayloads.size());

        // 置为就绪：仅新值被下发一次。
        scheduler.setSession(1L, true);
        assertEquals("就绪后仅新值下发一次", 1, transport.sentPayloads.size());
        assertEquals("下发的应是新值载荷", EQ_NEWS, transport.sentPayloads.get(0));
        assertEquals("新值命令应处于 SENT", Command.State.SENT, newer.getState());
        assertFalse("被顶替的旧值载荷从未下发", transport.sentPayloads.contains(EQ_MOVIE));

        // 收尾：新值回包完成。
        assertTrue("新值 EQ 回显应被认领",
                transport.fireResponse(scheduler, 1L, EQ_NEWS));
        assertEquals("新值命令应为 SUCCESS",
                Command.ResultCode.SUCCESS, newer.getCompletion().getCode());
        assertEquals("新值命令应落到 COMPLETED",
                Command.State.COMPLETED, newer.getState());
    }

    private static CommandPolicy actionPolicy(Command command, String payload) {
        return new CommandPolicy(
                CommandPolicy.Kind.ACTION,
                CommandPolicy.MergeMode.NONE,
                command.semanticKey(payload),
                null,
                0L,
                0L);
    }

    private static CommandScheduler newScheduler(ManualDispatcher dispatcher,
                                                 FakeTransport transport) {
        return newScheduler(dispatcher, transport, new CommandPolicyRegistry());
    }

    private static CommandScheduler newScheduler(ManualDispatcher dispatcher,
                                                 FakeTransport transport,
                                                 CommandPolicyResolver resolver) {
        CommandScheduler scheduler = new CommandScheduler(
                dispatcher, transport, snapshot -> { }, resolver);
        scheduler.setSession(1L, true);
        return scheduler;
    }

    /**
     * 内联执行 {@code execute}；{@code schedule} 只登记任务，由测试按需 {@link #fireScheduled()} 触发。
     * 时钟 {@link #nowMs()} 由测试用 {@link #advanceTo(long)} 手动推进，不依赖真实时间；
     * {@link #lastScheduledDelay()} 暴露最近一次登记的延迟，用于断言超时是否按预期重臂。
     */
    private static final class ManualDispatcher implements CommandScheduler.Dispatcher {
        private final Deque<Runnable> scheduled = new ArrayDeque<>();
        private final List<Long> scheduleDelays = new ArrayList<>();
        private long now = 1_000L;

        @Override
        public void execute(Runnable runnable) {
            if (runnable != null) {
                runnable.run();
            }
        }

        @Override
        public CommandScheduler.Cancelable schedule(Runnable runnable, long delayMs) {
            scheduled.addLast(runnable);
            scheduleDelays.add(delayMs);
            return () -> scheduled.remove(runnable);
        }

        @Override
        public long nowMs() {
            return now;
        }

        /** 手动把调度时钟推到指定毫秒，用于确定性地越过 GET 缓存 TTL 等时间边界。 */
        void advanceTo(long targetMs) {
            now = targetMs;
        }

        int pendingCount() {
            return scheduled.size();
        }

        /** 最近一次 {@code schedule} 登记的延迟（毫秒）；无登记返回 -1。 */
        long lastScheduledDelay() {
            return scheduleDelays.isEmpty() ? -1L : scheduleDelays.get(scheduleDelays.size() - 1);
        }

        void fireScheduled() {
            List<Runnable> snapshot = new ArrayList<>(scheduled);
            scheduled.clear();
            scheduleDelays.clear();
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

    /**
     * 仅用于本测试的两帧聚合命令：第一帧（载荷末字节 00）返回 pending，
     * 第二帧（载荷末字节 01）返回 complete。用不同帧长确认 {@code processFrame} 的
     * pending → inter-frame → complete 迁移；两帧均为合法标准协议帧，走统一帧边界校验。
     */
    private static final class TwoFrameCommand extends Command {
        @Override
        public String getCommandData() {
            return TWO_FRAME_REQUEST;
        }

        @Override
        public long getTimeoutMs() {
            return 2_000L;
        }

        @Override
        public long getInterFrameTimeoutMs() {
            return 1_234L;
        }

        @Override
        public boolean isResponseMatch(String responseData) {
            return responseData != null && responseData.startsWith("99EC99");
        }

        @Override
        public Response parseResponse(String responseData) {
            String flag = responseData.substring(10, 12);
            if ("00".equals(flag)) {
                return Response.pending();
            }
            if ("01".equals(flag)) {
                return completed(ResultCode.SUCCESS, "两帧聚合完成");
            }
            return completed(ResultCode.FAILED, "未知测试帧");
        }
    }
}
