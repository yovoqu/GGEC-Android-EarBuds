package com.yovo.biz_ggec_hs.internal.center.protocol;





import com.yovo.biz_ggec_hs.internal.center.protocol.ProtocolInfo;
import com.yovo.biz_ggec_hs.internal.center.port.HsCommandLink;
import com.yovo.biz_ggec_hs.internal.center.port.HsConnectionEvents;
import com.yovo.biz_ggec_hs.provider.lib_common.log.HsLog;
import com.yovo.biz_ggec_hs.internal.center.protocol.devicecmd.EarColorCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.functioncmd.BeginnerModeCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.musiccmd.LDACQueryCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.inbound.DeviceDomainStore;
import com.yovo.biz_ggec_hs.internal.center.protocol.inbound.DeviceEventPipeline;
import com.yovo.biz_ggec_hs.internal.center.protocol.inbound.HsInboundFrameAssembler;
import com.yovo.biz_ggec_hs.internal.center.protocol.inbound.InboundFrameDemux;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * HS 命令运行时。调度、分帧和事件都在这里，链路只通过 {@link HsCommandLink} 进出字节。
 *
 * <p>连接生命周期只有一处状态源：{@link #sessionEstablished}。它只在链路 READY 时置位，
 * 只由 {@link #endSession()} 清除；{@code sessionId} 是该会话的身份值。上层收到
 * {@code onDisconnected} / {@code onConnectFailed} 各至多一次，且都从本类发出。</p>
 */
public final class HsProtocolEngine {
    private static final String TAG = "HsProtocolEngine";
    /** 已建立会话时再次 {@link #connect(String)}：旧会话以「换目标」结束。 */
    public static final String REASON_TARGET_SWITCHED = "连接目标已切换";
    /** 就绪看门狗超时的统一原因，由 {@link #onConnectAttemptTimedOut()} 上报。 */
    public static final String REASON_READY_TIMEOUT = "ready-timeout";
    private static final String REASON_LINK_ERROR = "命令链路失败";

    private final HsCommandLink link;
    private final ScheduledExecutorService executor;
    private final CommandScheduler scheduler;
    private final DeviceEventPipeline events;
    private final InboundFrameDemux demux;
    private final HsInboundFrameAssembler inboundAssembler = new HsInboundFrameAssembler();
    private final AtomicInteger sessionSequence = new AtomicInteger();

    private volatile long sessionId;
    /**
     * 会话身份判定。只有链路 READY 才会置位，{@link #endSession()} 清除。
     * 所有「当前是否处在一次设备会话里」的判断都读它，不再借调度器的就绪标志。
     */
    private volatile boolean sessionEstablished;
    /** 已发起、尚未就绪也未失败的连接尝试。用来区分「失败的首次连接」与「已断开的会话」。 */
    private volatile boolean connectAttemptActive;
    private volatile boolean released;
    private volatile HsConnectionEvents connectionEvents;
    private volatile String currentMac = "";
    /** 本次尝试 / 会话的断连原因；链路错误在回调线程先落值，再由引擎轮次读取。 */
    private volatile String lastDisconnectReason;
    private volatile boolean connectFailedNotified;
    /** 已经自动查过耳机颜色的会话序号；同一会话重复 READY 不再重复下发。 */
    private volatile long earColorQueriedSession;

    public HsProtocolEngine(HsCommandLink link) {
        this(link, Executors.newSingleThreadScheduledExecutor(new ProtocolThreadFactory()));
    }

    /**
     * 由装配方提供引擎线程的构造入口。调度、分帧与域事件全部排在这一个执行器上，
     * 因此它必须串行执行；生产路径用 {@link #HsProtocolEngine(HsCommandLink)} 的守护线程。
     */
    HsProtocolEngine(HsCommandLink link, ScheduledExecutorService executor) {
        if (link == null) {
            throw new IllegalArgumentException("命令链路不能为空");
        }
        if (executor == null) {
            throw new IllegalArgumentException("引擎执行器不能为空");
        }
        this.link = link;
        this.executor = executor;
        this.events = new DeviceEventPipeline();
        CommandScheduler.Dispatcher dispatcher = new ExecutorDispatcher();
        this.scheduler = new CommandScheduler(
                dispatcher,
                new LinkTransport(),
                snapshot -> { },
                new CommandPolicyRegistry());
        this.demux = new InboundFrameDemux(
                events::onEvent,
                scheduler::onResponseCandidate,
                () -> { },
                id -> id > 0L && id == sessionId);
        link.setListener(new LinkListener());
    }

    /**
     * 发起连接。这是会话起点：{@code connect(...)} <b>返回时</b>本会话域已经清空。
     *
     * <p>起点次序：</p>
     * <ol>
     *   <li>调用线程、同步：记录目标地址，清空会话域状态。</li>
     *   <li>调用线程：{@code link.open(macAddress)}——建链留在调用线程，设备地址非法时仍在
     *       调用处抛 {@code IllegalArgumentException}。</li>
     *   <li>引擎线程：已建立的旧会话走完整终点——停调度、丢弃在途命令、清空域状态、
     *       通知 {@code onDisconnected}。</li>
     *   <li>引擎线程：置本次尝试状态，通知 {@code onConnecting}。</li>
     * </ol>
     *
     * <p>清域为什么必须在第 1 步（而不是随引擎任务走）：上层
     * {@code HsDevicePort.connect} 的次序是 {@code setConnectionObserver → connect →
     * setDeviceObserver}，而挂载设备观察者会在<b>调用线程同步回放</b>域里已有的固件 / 颜色 /
     * 在仓状态 / 电量。清域若排在引擎任务里，{@code connect} 返回与引擎线程拾取该任务之间没有
     * happens-before 边：引擎还没开始跑，回放就已经把上一台设备的值推给了新会话的观察者。
     * 会话起点的清域因此归调用方，不归引擎轮次。</p>
     *
     * <p>旧会话终点留在引擎线程：{@link CommandScheduler} 的全部方法都必须由同一个
     * {@link CommandScheduler.Dispatcher} 串行调用，{@code setSession(0)} 与它在队列里的在途
     * 命令同属一处状态，不能在调用线程上改。终点的清域是「旧会话结束」的那一次，不是起点
     * 这一次；引擎队列 FIFO，它排在新会话的 READY / 入站帧之前，因此不会抹掉本会话已到达的
     * 数据，断连回调读到也仍是空域。旧会话若只 retire 链路不开新会话，耳机颜色不会补查、
     * 在途命令不会中止、上层会停在 CONNECTING。</p>
     */
    public void connect(String macAddress) {
        currentMac = macAddress == null ? "" : macAddress;
        // 会话起点：新连接必须从「未知」开始，且必须在 connect 返回之前完成（理由见方法注释）。
        events.clearSessionState();
        // 收尾与起点通知排在引擎线程上，保证它们先于本次链路的 READY 事件被处理。
        executor.execute(this::startConnectAttempt);
        link.open(macAddress);
    }

    public void disconnect() {
        link.close();
    }

    public void release() {
        if (released) {
            return;
        }
        released = true;
        connectionEvents = null;
        link.setListener(null);
        link.release();
        executor.execute(() -> {
            scheduler.release();
            sessionId = 0L;
            sessionEstablished = false;
            connectAttemptActive = false;
            events.clearSessionState();
        });
        executor.shutdown();
    }

    public boolean isReady() {
        return sessionEstablished && !released;
    }

    /**
     * 当前会话序号；0 表示没有已建立的会话。会话身份的唯一可观察出口（诊断与包内测试）。
     */
    long sessionId() {
        return sessionId;
    }

    public void submit(Command command, Command.CompletionListener listener) {
        if (command == null) {
            return;
        }
        if (released) {
            command.abort(CommandCompletion.stringResult(
                    Command.ResultCode.RESERVED, "命令调度器已释放"));
            return;
        }
        if (listener != null) {
            command.addCompletionListener(listener);
        }
        // H-02：首页/全局状态类查询返回的聚合 Map 必须落 DeviceDomainStore，
        // 否则电池与 EQ 状态只能依赖设备主动上报。此处做通用落地（仅取 String→String 项）。
        // 耳机颜色同样经这里落地：它是 OBJECT 形状的字符串结果，不是聚合 Map。
        command.addCompletionListener(this::persistCompletion);
        executor.execute(() -> scheduler.submit(command, sessionId));
    }

    /**
     * 命令完成结果的唯一落地入口。缓存命中与单飞跟随者拿到的都是首发的
     * {@link CommandCompletion}，所以取值只认完成结果本身，不读命令实例字段。
     */
    private void persistCompletion(Command command, CommandCompletion completion) {
        if (completion == null) {
            return;
        }
        if (command instanceof EarColorCommand) {
            persistEarColor(completion);
            return;
        }
        if (command instanceof LDACQueryCommand) {
            persistLdacStatus(completion);
            return;
        }
        if (command instanceof BeginnerModeCommand beginnerMode) {
            persistBeginnerMode(beginnerMode, completion);
            return;
        }
        persistAggregatedStatus(completion);
    }

    /**
     * 新手模式开关：进入/退出命令成功后设备侧状态即该开关值。
     * 主动上报（{@code 99ECD2}）给的是配置（耳侧+动作），不参与这里。
     */
    private void persistBeginnerMode(BeginnerModeCommand command,
                                     CommandCompletion completion) {
        if (!completion.isSuccess()) {
            return;
        }
        events.applyBeginnerModeEnabled(command.isEnter());
    }

    /** LDAC 查询回包是整数值：非 0 即开启。设备不会主动上报该状态。 */
    private void persistLdacStatus(CommandCompletion completion) {
        if (!completion.isSuccess()
                || completion.getShape() != CommandCompletion.Shape.INTEGER) {
            return;
        }
        events.applyLdacEnabled(completion.getResultValue() != 0);
    }

    private void persistEarColor(CommandCompletion completion) {
        if (!completion.isSuccess() || completion.getShape() != CommandCompletion.Shape.OBJECT) {
            return;
        }
        Object data = completion.getData();
        if (!(data instanceof String value)) {
            return;
        }
        try {
            events.applyEarColor(Integer.parseInt(value));
        } catch (NumberFormatException e) {
            HsLog.w(TAG, "耳机颜色回包不是数字: " + value);
        }
    }

    private void persistAggregatedStatus(CommandCompletion completion) {
        if (completion == null || completion.getShape() != CommandCompletion.Shape.OBJECT) {
            return;
        }
        Object data = completion.getData();
        if (!(data instanceof Map<?, ?> raw)) {
            return;
        }
        Map<String, String> status = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (entry.getKey() instanceof String key && entry.getValue() instanceof String value) {
                status.put(key, value);
            }
        }
        if (!status.isEmpty()) {
            events.applyAggregatedHomeStatus(status);
        }
    }

    public void setDeviceObserver(DeviceDomainStore.Observer observer) {
        events.setObserver(observer);
    }

    public void setConnectionEvents(HsConnectionEvents observer) {
        this.connectionEvents = observer;
    }

    public void setRemoteStreamStop(Runnable trigger) {
        events.setRemoteStreamStop(trigger);
    }

    /** 设备域状态的唯一读取口：快照与能力值都从这里投影。 */
    public DeviceDomainStore domain() {
        return events.domain();
    }

    /** 电量快照（协议文本原样）。转成结构化电量由上层负责。 */
    public DeviceDomainStore.BatterySnapshot batterySnapshot() {
        return events.batterySnapshot();
    }

    /** 当前已知的设备信息；三项都未上报时返回 null。 */
    public ProtocolInfo deviceInfo() {
        return events.domain().protocolInfo();
    }

    /**
     * 连接尝试起点（引擎线程）：结束仍在的旧会话，置本次尝试状态，通知上层等待链路就绪。
     * 会话域的清空属于 {@link #connect(String)} 的调用方起点，已在调用线程同步完成，这里不再重复。
     */
    private void startConnectAttempt() {
        if (released) {
            return;
        }
        if (sessionEstablished) {
            endSessionAndNotify(REASON_TARGET_SWITCHED);
        }
        connectFailedNotified = false;
        lastDisconnectReason = null;
        connectAttemptActive = true;
        HsConnectionEvents observer = connectionEvents;
        if (observer != null) {
            observer.onConnecting(currentMac);
        }
    }

    private void onLinkReady(boolean ready) {
        if (released) {
            return;
        }
        executor.execute(() -> {
            if (released) {
                return;
            }
            if (ready) {
                establishSession();
            } else {
                settleLinkDown();
            }
        });
    }

    /** 会话就绪：分配新身份、打开调度器、补查一次耳机颜色、通知上层。重复 READY 幂等。 */
    private void establishSession() {
        if (sessionEstablished) {
            // 幂等：同一会话重复 ready(true) 不新开会话，避免打断在途命令（H-05）。
            return;
        }
        inboundAssembler.reset();
        sessionId = sessionSequence.incrementAndGet();
        sessionEstablished = true;
        connectAttemptActive = false;
        connectFailedNotified = false;
        scheduler.setSession(sessionId, true);
        queryEarColorOnce();
        HsConnectionEvents observer = connectionEvents;
        if (observer != null) {
            observer.onConnected(currentMac);
        }
    }

    /**
     * 链路报告不再就绪。已建立的会话在这里收尾并通知断连；尚未就绪的尝试只是结束，
     * 由 {@link #onLinkFailure(String)} 或看门狗决定是否上报连接失败。
     */
    private void settleLinkDown() {
        boolean attemptActive = connectAttemptActive;
        connectAttemptActive = false;
        if (!sessionEstablished) {
            // 就绪前链路被收掉（用户主动断开 / 链路关闭）：这次尝试结束，但它不是设备失败，
            // 因此只收口状态、不带失败分类；不通知的话上层会一直停在 CONNECTING。
            if (attemptActive) {
                HsConnectionEvents observer = connectionEvents;
                if (observer != null) {
                    observer.onDisconnected(lastDisconnectReason);
                }
            }
            return;
        }
        endSessionAndNotify(lastDisconnectReason);
    }

    /**
     * 会话终点：停止调度、丢弃在途命令、清空本会话域状态与帧拼装残留，并发出唯一一次断连通知。
     *
     * <p>断连与链路失效共用这一份收尾，避免两条拆链路径各自漏清。域状态必须在
     * {@code onDisconnected} 回调之前清空，监听方在断连回调里读到的是未知而不是上一台设备的值。</p>
     */
    private void endSessionAndNotify(String reason) {
        lastDisconnectReason = reason;
        connectAttemptActive = false;
        sessionEstablished = false;
        scheduler.setSession(0L, false);
        sessionId = 0L;
        inboundAssembler.reset();
        events.clearSessionState();
        HsConnectionEvents observer = connectionEvents;
        if (observer != null) {
            observer.onDisconnected(reason);
        }
    }

    /**
     * 链路错误的唯一处理点。
     *
     * <p>链路契约：先 {@code onLinkError} 再 {@code onReady(false)}——原因属于断开事件的一部分，
     * 必须在状态变化被看到之前就位（顺序由链路的发送点保证，本类不依赖跨线程到达顺序）。
     * 原因在回调线程上先落值，断连效果（{@link #settleLinkDown()}）随后在引擎轮次读取，
     * 因此 {@code onDisconnected} 拿到的是这次失效的真实原因，而不是上一次的残值。</p>
     */
    private void onLinkFailure(String reason) {
        if (released) {
            return;
        }
        if (sessionEstablished) {
            // 链路实现只报错误、没有先发 ready(false)：会话仍是一次真实断连。
            endSessionAndNotify(reason);
            return;
        }
        if (!connectAttemptActive) {
            // 该会话已随 ready(false) 收尾并通知过 onDisconnected：同一次失效不得再发第二个事件。
            HsLog.d(TAG, "链路失效已随会话收尾通知，不再重复上报: " + reason);
            return;
        }
        connectAttemptActive = false;
        notifyConnectFailedOnce(reason);
    }

    /**
     * 连接就绪看门狗超时的统一入口。
     *
     * <p>会话层只报告「这次尝试超时了」；是否收掉链路、是否上报失败，都由引擎按生命周期
     * 状态决定，避免在引擎之外出现第二个生命周期事件发送点。</p>
     */
    public void onConnectAttemptTimedOut() {
        if (released) {
            return;
        }
        executor.execute(() -> {
            if (released || sessionEstablished || !connectAttemptActive) {
                return;
            }
            connectAttemptActive = false;
            link.close();
            notifyConnectFailedOnce(REASON_READY_TIMEOUT);
        });
    }

    private void notifyConnectFailedOnce(String reason) {
        if (connectFailedNotified) {
            return;
        }
        connectFailedNotified = true;
        HsConnectionEvents observer = connectionEvents;
        if (observer != null) {
            observer.onConnectFailed(reason);
        }
    }

    /**
     * 每次会话就绪只补一次耳机颜色：首页聚合只带固件（{@code 0x94}），颜色只能由独立命令查询。
     * 会话序号守卫让重复 READY 与重连抖动不会重复下发。
     *
     * <p>补查用自动查询的短等待：它和设备不支持颜色命令时的快速失败同义，不能拖住串行队列。</p>
     */
    private void queryEarColorOnce() {
        if (earColorQueriedSession == sessionId) {
            return;
        }
        earColorQueriedSession = sessionId;
        submit(new EarColorCommand(EarColorCommand.QueryOrigin.AUTO), null);
    }

    private final class LinkListener implements HsCommandLink.Listener {
        @Override
        public void onReady(boolean ready) {
            onLinkReady(ready);
        }

        @Override
        public void onInbound(byte[] value) {
            if (released) {
                return;
            }
            // 通知可能在任意字节边界分片/粘包：先拼帧再逐帧扇出（H-03）。
            String chunk = HsHex.encode(value);
            executor.execute(() -> {
                long current = sessionId;
                if (current <= 0L) {
                    return;
                }
                for (String frame : inboundAssembler.accept(chunk)) {
                    demux.onFrame(current, frame);
                }
            });
        }

        @Override
        public void onLinkError(String message) {
            if (released) {
                return;
            }
            String reason = message == null ? REASON_LINK_ERROR : message;
            // 原因先落值：随后的 ready(false) 收尾要读到它。
            lastDisconnectReason = reason;
            executor.execute(() -> onLinkFailure(reason));
        }
    }

    private final class LinkTransport implements CommandScheduler.Transport {
        @Override
        public boolean send(long requestSessionId, String payload, Callback callback) {
            if (requestSessionId != sessionId || !sessionEstablished) {
                return false;
            }
            final byte[] bytes;
            try {
                bytes = HsHex.decode(payload);
            } catch (IllegalArgumentException e) {
                // 命令自己产出了非十六进制载荷：按下发失败分类记录，不静默吞掉。
                HsLog.w(TAG, "命令载荷不是合法十六进制，拒绝下发: " + payload, e);
                return false;
            }
            return link.write(bytes, new HsCommandLink.WriteCallback() {
                @Override
                public void onStarted() {
                    callback.onStarted();
                }

                @Override
                public void onCompleted(int status) {
                    callback.onCompleted(status);
                }

                @Override
                public void onTimeout() {
                    callback.onTimeout();
                }
            });
        }
    }

    private final class ExecutorDispatcher implements CommandScheduler.Dispatcher {
        @Override
        public void execute(Runnable runnable) {
            if (runnable != null && !released) {
                executor.execute(runnable);
            }
        }

        @Override
        public CommandScheduler.Cancelable schedule(Runnable runnable, long delayMs) {
            if (runnable == null || released) {
                return () -> { };
            }
            java.util.concurrent.ScheduledFuture<?> future = executor.schedule(
                    runnable, Math.max(0L, delayMs), TimeUnit.MILLISECONDS);
            return () -> future.cancel(false);
        }

        @Override
        public long nowMs() {
            return System.nanoTime() / 1_000_000L;
        }
    }

    private static final class ProtocolThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "yo-hs-protocol");
            thread.setDaemon(true);
            return thread;
        }
    }
}
