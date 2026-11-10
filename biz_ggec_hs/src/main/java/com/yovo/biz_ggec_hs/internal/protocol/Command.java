package com.yovo.biz_ggec_hs.internal.protocol;

import com.yovo.biz_ggec_hs.internal.log.HsLog;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Collections;
import java.util.Set;

/**
 * 协议命令基类。
 *
 * 具体命令只负责协议编码、响应匹配和解析。请求完成状态与完成订阅均归命令实例所有，
 * 调度器只观察结果，不再通过全局 commandId 回调表推进生命周期。
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

    public enum State {
        QUEUED,
        SENDING,
        SENT,
        RECEIVING,
        COMPLETED,
        FAILED
    }

    public interface CommandCallback {
        void onCommandCompleted(Command command, int code, String result);

        void onCommandCompleted(Command command, int code, Object yobackdata, String result);

        void onCommandCompleted(Command command, int code, int resultValue, String result);
    }

    public interface CompletionListener {
        void onCommandCompleted(Command command, CommandCompletion completion);
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
    }

    public String getCommandPrefix() {
        return commandPrefix;
    }

    protected void setCommandPrefix(String prefix) {
        this.commandPrefix = prefix;
    }

    public abstract String getCommandData();

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

    public final void addCommandCallback(CommandCallback callback) {
        if (callback == null) {
            return;
        }
        addCompletionListener((command, result) -> dispatchCallback(callback, result));
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
        for (CompletionListener listener : completionListeners) {
            if (completionListeners.remove(listener)) {
                notifyListener(listener, result);
            }
        }
    }

    private void dispatchCallback(CommandCallback callback, CommandCompletion result) {
        switch (result.getShape()) {
            case OBJECT:
                callback.onCommandCompleted(
                        this, result.getCode(), result.getData(), result.getMessage());
                break;
            case INTEGER:
                callback.onCommandCompleted(
                        this, result.getCode(), result.getResultValue(), result.getMessage());
                break;
            case STRING:
            default:
                callback.onCommandCompleted(this, result.getCode(), result.getMessage());
                break;
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

    final boolean tryClaimForScheduling() {
        return schedulingClaimed.compareAndSet(false, true);
    }

    final void replayCompletion(CommandCompletion result) {
        complete(result);
    }

    final void completeResponse(Response response) {
        if (response == null || !response.isComplete()) {
            throw new IllegalArgumentException("命令完成响应不能为空");
        }
        complete(response.getCompletion());
    }

    final void completeSuperseded() {
        complete(CommandCompletion.integerResult(
                ResultCode.SUCCESS, 0, "已被后续设置覆盖，无需真实下发"));
    }

    final void abort(CommandCompletion result) {
        try {
            onAborted();
        } catch (RuntimeException e) {
            HsLog.e(TAG, "命令中止清理失败: " + commandId, e);
        }
        complete(result);
    }
}
