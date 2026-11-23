package com.yovo.biz_ggec_hs.internal.center.protocol;

import com.yovo.biz_ggec_hs.internal.center.exchange.HsTraffic;
import com.yovo.biz_ggec_hs.provider.lib_common.log.HsLog;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Collections;
import java.util.Set;

/**
 * 协议命令定义。子类只编码、匹配和解析。
 *
 * <p>收发角色默认是一发一收。调度、超时和在途队列在命令系统里，不在子类里。
 * 完成状态仍挂在这一次命令实例上，调度器只观察结果。</p>
 */
public abstract class Command {
    private static final String TAG = "Command";
    private static final AtomicLong REQUEST_SEQUENCE = new AtomicLong();

    public static final class ResultCode {
        public static final int SUCCESS = 0;
        public static final int TIMEOUT = 1;
        public static final int RESERVED = 2;
        public static final int FAILED = 3;

        private ResultCode() {
        }
    }

    /**
     * 完成结果落域的种类。默认 {@link #AGGREGATED_HOME_STATUS}：把 OBJECT+Map 结果当首页聚合状态落地。
     * 少数命令覆写 {@link #resultKind()} 走专属落域，使协议引擎无需 {@code instanceof} 具体命令类。
     */
    public enum ResultKind {
        /** 默认：OBJECT+Map 聚合首页状态（电量/EQ/固件/服务状态等）。 */
        AGGREGATED_HOME_STATUS,
        /** 耳机颜色：结果为颜色字节的字符串。 */
        EAR_COLOR,
        /** LDAC 开关：结果为整数（非 0 即开启）。 */
        LDAC_TOGGLE,
        /** 新手模式开关：成功即设备侧等于本命令的进入/退出值，见 {@link #resultingToggleValue()}。 */
        BEGINNER_MODE_TOGGLE
    }

    /**
     * 命令回包要产出的业务事件种类。默认 {@link #NONE}。只有少数命令会产出事件，
     * 由命令自声明；上层据此从回包数据构造事件，不再 {@code instanceof} 具体命令类。
     */
    public enum DeviceEventKind {
        /** 不产出事件。 */
        NONE,
        /** 双连接页聚合回包 → 双连接态事件。 */
        DUAL_CONNECTION_PAGE,
        /** 录音列表回包 → 录音文件列表事件。 */
        RECORD_FILE_LIST
    }

    public enum State {
        QUEUED,
        SENDING,
        SENT,
        RECEIVING,
        COMPLETED,
        FAILED
    }

    public interface CompletionListener {
        void onCommandCompleted(Command command, CommandCompletion completion);
    }

    /**
     * 命令状态观察者：状态机每次迁移都会回调（QUEUED/SENDING/SENT/RECEIVING/COMPLETED/FAILED）。
     * 供上层做「发送中 → 处理中 → 终态」的阶段映射，不改变命令本身的行为。
     *
     * <p><b>线程契约：</b>回调发生在命令状态迁移所在线程（正常为引擎串行线程；经
     * {@code replayCompletion} 路径时可能是 BLE/notify 线程），<b>不保证主线程</b>。
     * 需要主线程的消费者必须自行切换（例如 {@code HsSessionImpl} 用主线程 Handler post）。</p>
     */
    public interface StateListener {
        void onStateChanged(Command command, State state);
    }

    public static final class Response {
        private static final Response PENDING = new Response(null);

        private final CommandCompletion completion;

        private Response(CommandCompletion completion) {
            this.completion = completion;
        }

        public static Response pending() {
            return PENDING;
        }

        static Response completed(CommandCompletion completion) {
            return new Response(completion);
        }

        public boolean isComplete() {
            return completion != null;
        }

        CommandCompletion getCompletion() {
            return completion;
        }
    }

    private final String commandId =
            getClass().getSimpleName() + "_" + REQUEST_SEQUENCE.incrementAndGet();
    private final AtomicBoolean completionRecorded = new AtomicBoolean(false);
    private final AtomicBoolean schedulingClaimed = new AtomicBoolean(false);
    private final CopyOnWriteArrayList<CompletionListener> completionListeners =
            new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<StateListener> stateListeners = new CopyOnWriteArrayList<>();

    private volatile State state = State.QUEUED;
    private volatile String commandPrefix;
    private volatile boolean commandFinished;
    private volatile CommandCompletion completion;

    public String getCommandId() {
        return commandId;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
        HsLog.d(TAG, commandId + " 状态变更为 " + state);
        for (StateListener listener : stateListeners) {
            try {
                listener.onStateChanged(this, state);
            } catch (RuntimeException e) {
                HsLog.e(TAG, "命令状态监听器执行失败: " + commandId, e);
            }
        }
    }

    /** 注册命令状态观察者（可选）。多次注册相同实例由调用方自行去重。 */
    public final void addStateListener(StateListener listener) {
        if (listener != null) {
            stateListeners.add(listener);
        }
    }

    public final void removeStateListener(StateListener listener) {
        if (listener != null) {
            stateListeners.remove(listener);
        }
    }

    protected void setCommandPrefix(String prefix) {
        this.commandPrefix = prefix;
    }

    public HsTraffic traffic() {
        return HsTraffic.REQUEST_REPLY;
    }

    /**
     * 完成结果落域的种类。默认按 OBJECT+Map 聚合首页状态；特殊命令覆写以走专属落域。
     * 引擎据此分发，不再 {@code instanceof} 具体命令类。
     */
    public ResultKind resultKind() {
        return ResultKind.AGGREGATED_HOME_STATUS;
    }

    /**
     * 仅 {@link ResultKind#BEGINNER_MODE_TOGGLE} 使用：本命令成功后设备侧的开关值
     * （新手模式“进入/退出”即设备事实）。其它命令返回 {@code null}。
     */
    public Boolean resultingToggleValue() {
        return null;
    }

    /**
     * 命令回包产出的业务事件种类。默认不产出事件；特殊命令覆写以声明其事件种类，
     * 上层据 {@code DeviceEventKind} 构造事件，避免 {@code instanceof} 具体命令类。
     */
    public DeviceEventKind deviceEventKind() {
        return DeviceEventKind.NONE;
    }

    public abstract String getCommandData();

    /**
     * 合并/去重语义键。默认=命令类名；需要按参数区分合并槽的命令覆写。
     *
     * @param payload 本命令 {@link #getCommandData()} 的输出
     */
    public String semanticKey(String payload) {
        return getClass().getName();
    }

    /**
     * 调度目录声明的响应超时；命令可按自身语义上调/下调。默认沿用声明值。
     *
     * @param declaredResponseTimeoutMs 调度目录为该命令声明的响应超时
     */
    public long responseTimeoutMs(long declaredResponseTimeoutMs) {
        return declaredResponseTimeoutMs;
    }

    public abstract Response parseResponse(String responseData);

    public abstract boolean isResponseMatch(String responseData);

    /**
     * 调度器在调用具体匹配器前执行的统一帧边界校验。
     * 上行响应必须符合标准字节长度帧或协议中显式建模的受限例外；主动上报仍由旁路路由处理。
     */
    public boolean isResponseFrameValid(String responseData) {
        return ProtocolValues.isProtocolFrame(responseData);
    }

    public boolean isCommandFinish() {
        return commandFinished;
    }

    /**
     * 命令自身的响应等待。调度目录声明了响应超时时，首帧等待用目录值；
     * 未声明时用这里的值。多帧命令的后续帧仍走 {@link #getInterFrameTimeoutMs()}。
     */
    public long getTimeoutMs() {
        return 2000L;
    }

    public long getInterFrameTimeoutMs() {
        return getTimeoutMs();
    }

    /**
     * 固件没有真实请求序号时，用于隔离超时后的同形响应。
     *
     * <p>默认按具体命令类型分组；共享完全相同响应形状的命令可覆写为同一稳定键。</p>
     */
    public String getResponseFamily() {
        String payload = getCommandData();
        if (payload != null && payload.length() >= 6) {
            return payload.substring(0, 6).toUpperCase(java.util.Locale.US);
        }
        return getClass().getName();
    }

    /**
     * 一个命令可能消费多个响应前缀（例如多帧聚合命令）。这些族共同参与迟到帧隔离。
     */
    public Set<String> getResponseFamilies() {
        return Collections.singleton(getResponseFamily());
    }

    /**
     * 未知发送结果或响应超时后，丢弃该响应族迟到帧的有界时间。
     */
    public long getLateResponseGuardMs() {
        return 1000L;
    }

    public Response onResponseTimeout() {
        return completed(ResultCode.TIMEOUT, "等待设备响应超时");
    }

    /**
     * 命令被调度器中止时调用。多包命令可在此清理自己的定时器或临时资源。
     */
    protected void onAborted() {
    }

    public final void addCompletionListener(CompletionListener listener) {
        if (listener == null) {
            return;
        }
        completionListeners.add(listener);
        CommandCompletion completed = completion;
        if (completed != null && completionListeners.remove(listener)) {
            notifyListener(listener, completed);
        }
    }

    public final void removeCompletionListener(CompletionListener listener) {
        if (listener != null) {
            completionListeners.remove(listener);
        }
    }

    protected final Response completed(int code, String result) {
        return Response.completed(CommandCompletion.stringResult(code, result));
    }

    protected final Response completed(int code, Object yobackdata, String result) {
        return Response.completed(CommandCompletion.objectResult(code, yobackdata, result));
    }

    protected final Response completed(int code, int resultValue, String result) {
        return Response.completed(CommandCompletion.integerResult(code, resultValue, result));
    }

    private void complete(CommandCompletion result) {
        if (result == null) {
            throw new IllegalArgumentException("命令完成结果不能为空");
        }
        if (!completionRecorded.compareAndSet(false, true)) {
            HsLog.w(TAG, "忽略重复的命令完成通知: " + commandId);
            return;
        }

        completion = result;
        commandFinished = true;
        setState(result.isSuccess() ? State.COMPLETED : State.FAILED);
        // 终态原因（TIMEOUT / FAILED / RESERVED 与文案）一并打印：仅凭「状态变更为 FAILED」
        // 无法区分是 GATT 写超时、响应超时还是业务失败，排查 HS02 类失败时是必需证据。
        HsLog.d(TAG, commandId + " 完成: code=" + result.getCode()
                + " message=" + result.getMessage());
        for (CompletionListener listener : completionListeners) {
            if (completionListeners.remove(listener)) {
                notifyListener(listener, result);
            }
        }
    }

    private void notifyListener(CompletionListener listener, CommandCompletion result) {
        try {
            listener.onCommandCompleted(this, result);
        } catch (RuntimeException e) {
            HsLog.e(TAG, "命令完成监听器执行失败: " + commandId, e);
        }
    }

    public final CommandCompletion getCompletion() {
        return completion;
    }

    public final boolean tryClaimForScheduling() {
        return schedulingClaimed.compareAndSet(false, true);
    }

    public final void replayCompletion(CommandCompletion result) {
        complete(result);
    }

    public final void completeResponse(Response response) {
        if (response == null || !response.isComplete()) {
            throw new IllegalArgumentException("命令完成响应不能为空");
        }
        complete(response.getCompletion());
    }

    public final void completeSuperseded() {
        complete(CommandCompletion.integerResult(
                ResultCode.SUCCESS, 0, "已被后续设置覆盖，无需真实下发"));
    }

    public final void abort(CommandCompletion result) {
        try {
            onAborted();
        } catch (RuntimeException e) {
            HsLog.e(TAG, "命令中止清理失败: " + commandId, e);
        }
        complete(result);
    }
}
