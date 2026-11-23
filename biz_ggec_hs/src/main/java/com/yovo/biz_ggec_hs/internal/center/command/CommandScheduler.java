package com.yovo.biz_ggec_hs.internal.center.command;

import com.yovo.biz_ggec_hs.internal.center.protocol.Command;
import com.yovo.biz_ggec_hs.internal.center.protocol.CommandCompletion;
import com.yovo.biz_ggec_hs.internal.center.exchange.HsTraffic;
import com.yovo.biz_ggec_hs.provider.lib_common.log.HsLog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 与 Android 和 BLE 实现无关的命令调度核心。
 *
 * 所有方法必须由同一个 {@link Dispatcher} 串行调用。传输回调会自动切回该调度线程。
 */
public final class CommandScheduler {
    private static final String TAG = "CommandScheduler";

    static final long SET_COALESCE_WINDOW_MS = 1000L;
    static final long COMMAND_INTERVAL_MS = 30L;

    public interface Cancelable {
        void cancel();
    }

    public interface Dispatcher {
        void execute(Runnable runnable);

        Cancelable schedule(Runnable runnable, long delayMs);

        long nowMs();
    }

    public interface Transport {
        /**
         * 同步返回「是否被链路受理/已发送」。受理后由本类等待响应帧与有界响应超时；不再有写完成回调。
         *
         * @param sessionId 目标连接会话
         * @param payload   已编码的十六进制命令载荷
         * @return true 表示链路已受理该次下发；false 表示未受理，调度器立即按失败收尾
         */
        boolean send(long sessionId, String payload);
    }

    public interface StateListener {
        void onStateChanged(Snapshot snapshot);
    }

    public static final class Snapshot {
        private final boolean executing;
        private final boolean released;
        private final boolean idle;
        private final long sessionId;
        private final boolean ready;
        private final List<Command> commands;

        Snapshot(boolean executing, boolean released, boolean idle,
                 long sessionId, boolean ready, List<Command> commands) {
            this.executing = executing;
            this.released = released;
            this.idle = idle;
            this.sessionId = sessionId;
            this.ready = ready;
            this.commands = Collections.unmodifiableList(commands);
        }

        boolean isExecuting() {
            return executing;
        }

        boolean isReleased() {
            return released;
        }

        boolean isIdle() {
            return idle;
        }

        long getSessionId() {
            return sessionId;
        }

        boolean isReady() {
            return ready;
        }

        boolean hasCommandOfType(Class<?> commandClass) {
            if (commandClass == null) {
                return false;
            }
            for (Command command : commands) {
                if (commandClass.isInstance(command)) {
                    return true;
                }
            }
            return false;
        }
    }

    private final Dispatcher dispatcher;
    private final Transport transport;
    private final StateListener stateListener;
    private final CommandPolicyResolver policyResolver;

    private final RequestQueue requestQueue = new RequestQueue();
    private final ResponseCache responseCache = new ResponseCache();
    private final LateFrameGuard lateFrameGuard = new LateFrameGuard();

    private CommandNode currentNode;
    private Command currentCommand;
    private Cancelable responseTimeout;
    private Cancelable nextExecution;
    private long sessionId;
    private boolean ready;
    private boolean executing;
    private boolean released;
    private long consistencyGeneration;
    private long attemptSequence;

    public CommandScheduler(Dispatcher dispatcher,
                     Transport transport,
                     StateListener stateListener,
                     CommandPolicyResolver policyResolver) {
        this.dispatcher = dispatcher;
        this.transport = transport;
        this.stateListener = stateListener;
        this.policyResolver = policyResolver;
        publishState();    }

    public void submit(Command command) {
        submit(command, sessionId);
    }

    public void submit(Command command, long requestSessionId) {
        if (command == null) {
            return;
        }
        if (command.traffic() != HsTraffic.REQUEST_REPLY) {
            throw new IllegalArgumentException("命令系统只调度一发一收: " + command.getCommandId());
        }
        if (released) {
            command.abort(failure("命令调度器已释放"));
            return;
        }
        if (requestSessionId <= 0L || requestSessionId != sessionId) {
            command.abort(unavailable("命令没有可用的目标连接会话"));
            return;
        }
        if (command.getCompletion() != null) {
            return;
        }
        if (!command.tryClaimForScheduling()) {
            return;
        }

        command.setState(Command.State.QUEUED);
        String payload = encode(command);
        if (command.getCompletion() != null) {
            return;
        }
        if (payload == null || payload.isEmpty()) {
            command.abort(failure("命令数据为空，无法发送"));
            return;
        }

        CommandPolicy policy;
        try {
            policy = policyResolver.resolve(command, payload);
        } catch (RuntimeException e) {
            HsLog.w(TAG, "命令调度策略解析失败", e);
            command.abort(failure("命令调度策略解析失败"));
            return;
        }
        long now = dispatcher.nowMs();
        if (policy.getKind() == CommandPolicy.Kind.GET) {
            admitGet(command, payload, policy, now);
        } else {
            consistencyGeneration++;
            responseCache.clear();
            if (policy.getMergeMode() == CommandPolicy.MergeMode.LATEST_WINS) {
                admitSet(command, payload, policy, now);
            } else {
                requestQueue.offerLast(CommandNode.forCommand(
                        command, payload, policy, consistencyGeneration,
                        requestSessionId, now));
                publishState();
                executeNext();
            }
        }
    }

    private void admitGet(Command command,
                          String payload,
                          CommandPolicy policy,
                          long now) {
        String identity = CommandMergeRules.queryIdentity(policy, consistencyGeneration);
        CommandCompletion cached = responseCache.get(identity, now);
        if (cached != null) {
            command.replayCompletion(cached);
            return;
        }

        CommandNode group = requestQueue.getQueryGroup(identity);
        if (group != null) {
            group.followers.add(command);
            group.updatedAtMs = now;
            publishState();
            return;
        }

        CommandNode node = CommandNode.forCommand(
                command, payload, policy, consistencyGeneration, sessionId, now);
        node.queryIdentity = identity;
        requestQueue.offerLast(node);
        requestQueue.putQueryGroup(identity, node);
        publishState();
        executeNext();
    }

    private void admitSet(Command command,
                          String payload,
                          CommandPolicy policy,
                          long now) {
        CommandNode target = requestQueue.getSetSlot(policy.getSemanticKey());
        SetMerger.Decision decision = SetMerger.decide(
                currentNode,
                requestQueue.isEmpty(),
                requestQueue.peekLast(),
                target,
                policy,
                now,
                SET_COALESCE_WINDOW_MS);
        switch (decision) {
            case JOIN_CURRENT:
                currentNode.followers.add(command);
                currentNode.updatedAtMs = now;
                publishState();
                return;
            case NEW_NODE: {
                CommandNode node = CommandNode.forCommand(
                        command, payload, policy, consistencyGeneration, sessionId, now);
                requestQueue.offerLast(node);
                requestQueue.offerSetSlot(policy.getSemanticKey(), node);
                publishState();
                executeNext();
                return;
            }
            case REUSE_SLOT_SAME_VALUE:
                target.followers.add(command);
                target.updatedAtMs = now;
                publishState();
                return;
            case SUPERSEDE:
            default:
                target.primary.completeSuperseded();
                for (Command follower : target.followers) {
                    follower.completeSuperseded();
                }
                target.followers.clear();
                target.primary = command;
                target.payload = payload;
                target.policy = policy;
                target.generation = consistencyGeneration;
                target.updatedAtMs = now;
                publishState();
        }
    }

    public void setSession(long newSessionId, boolean ready) {
        if (released) {
            return;
        }
        if (newSessionId <= 0L) {
            if (sessionId != 0L || this.ready || currentNode != null || !requestQueue.isEmpty()) {
                abortAll("目标连接会话已结束");
            }
            sessionId = 0L;
            this.ready = false;
            lateFrameGuard.clear();
            publishState();
            return;
        }
        if (sessionId != newSessionId) {
            if (sessionId != 0L || currentNode != null || !requestQueue.isEmpty()) {
                abortAll("目标连接会话已切换");
            }
            sessionId = newSessionId;
            this.ready = ready;
            consistencyGeneration++;
            responseCache.clear();
            lateFrameGuard.clear();
            if (ready) {
                executeNext();
            }
            publishState();
            return;
        }
        if (this.ready == ready) {
            return;
        }
        this.ready = ready;
        if (ready) {
            executeNext();
        } else {
            abortAll("设备连接已断开");
        }
        publishState();
    }

    /**
     * 应答通道入口：仅尝试匹配当前命令，不处理主动事件。
     *
     * <p>响应帧驱动完成：一旦受理（{@code transport.send} 返回 true）即置 SENT 并启动有界响应超时，
     * 入站匹配帧即处理完成，不再依赖任何写完成回调。</p>
     *
     * @return true 表示帧被当前命令消费
     */
    public boolean onResponseCandidate(long frameSessionId, String frame) {
        if (frame == null) {
            return false;
        }
        if (frameSessionId <= 0L || frameSessionId != sessionId) {
            return false;
        }
        if (lateFrameGuard.isQuarantined(frame, dispatcher.nowMs())) {
            return false;
        }
        Command command = currentCommand;
        CommandNode node = currentNode;
        if (command == null || node == null || !matches(command, frame)) {
            return false;
        }
        processFrame(command, frame);
        return true;
    }

    void abortCurrent(String reason) {
        cancelTimers();
        CommandNode node = currentNode;
        Command command = currentCommand;
        lateFrameGuard.register(command, dispatcher.nowMs());
        currentNode = null;
        currentCommand = null;
        executing = false;

        CommandCompletion failure = aborted(reason);
        if (command != null) {
            command.abort(failure);
        }
        if (node != null) {
            abortFollowers(node, failure);
            requestQueue.removeIndexes(node);
        }
        publishState();
        scheduleNext(COMMAND_INTERVAL_MS);
    }

    void abortAll(String reason) {
        cancelTimers();
        CommandCompletion failure = aborted(reason);

        CommandNode active = currentNode;
        Command activeCommand = currentCommand;
        lateFrameGuard.register(activeCommand, dispatcher.nowMs());
        currentNode = null;
        currentCommand = null;
        executing = false;

        List<CommandNode> queued = requestQueue.clear();
        responseCache.clear();

        if (activeCommand != null) {
            activeCommand.abort(failure);
        }
        if (active != null) {
            abortFollowers(active, failure);
        }
        for (CommandNode node : queued) {
            node.primary.abort(failure);
            abortFollowers(node, failure);
        }
        publishState();
    }

    public void release() {
        if (released) {
            return;
        }
        abortAll("命令调度器已释放");
        released = true;
        ready = false;
        sessionId = 0L;
        lateFrameGuard.clear();
        publishState();
    }

    private void executeNext() {
        if (released || !ready || currentNode != null) {
            return;
        }
        cancelNextExecution();
        CommandNode node = requestQueue.pollFirst();
        if (node == null) {
            executing = false;
            publishState();
            return;
        }
        if (node.sessionId != sessionId) {
            CommandCompletion failure = unavailable("命令所属连接会话已过期");
            node.primary.abort(failure);
            abortFollowers(node, failure);
            requestQueue.removeIndexes(node);
            scheduleNext(0L);
            return;
        }
        currentNode = node;
        if (node.policy != null
                && node.policy.getMergeMode() == CommandPolicy.MergeMode.LATEST_WINS) {
            requestQueue.removeSetSlot(node.policy.getSemanticKey(), node);
        }
        startCommand(node.primary, node.payload);
        publishState();
    }

    private void startCommand(Command command, String payload) {
        CommandNode node = currentNode;
        if (node == null || command == null) {
            failCurrent("命令状态无效");
            return;
        }

        long quarantineDelay = lateFrameGuard.delayFor(command, dispatcher.nowMs());
        if (quarantineDelay > 0L) {
            executing = false;
            cancelNextExecution();
            nextExecution = dispatcher.schedule(() -> {
                nextExecution = null;
                if (currentNode == node && currentCommand == null) {
                    startCommand(command, payload);
                    publishState();
                }
            }, quarantineDelay);
            return;
        }

        currentCommand = command;
        executing = true;
        node.attemptId = ++attemptSequence;
        long attemptId = node.attemptId;

        Command.CompletionListener completionListener =
                (completed, completion) -> dispatcher.execute(
                        () -> onCommandCompletion(node, completed, completion));
        node.currentCompletionListener = completionListener;
        command.addCompletionListener(completionListener);
        if (!isCurrentAttempt(node, command, attemptId)) {
            return;
        }

        // 发送=同步「是否受理」：受理后置 SENT 并启动唯一有界响应超时，
        // 入站匹配帧即处理完成，不再依赖任何写完成回调。
        command.setState(Command.State.SENT);
        boolean accepted;
        try {
            accepted = transport.send(node.sessionId, payload);
        } catch (RuntimeException e) {
            HsLog.w(TAG, "命令发送请求执行异常", e);
            failCurrent("命令发送请求执行异常");
            return;
        }
        if (!accepted) {
            failCurrent("命令发送未被链路受理");
            return;
        }
        startResponseTimeout(command, node, attemptId, initialResponseTimeoutMs(command, node));
    }

    private void processFrame(Command command, String frame) {
        if (command.getCompletion() != null) {
            // 命令已终态：忽略迟到/重复帧，且不得把状态从 COMPLETED/FAILED 回退成 RECEIVING。
            return;
        }
        try {
            command.setState(Command.State.RECEIVING);
            Command.Response response = command.parseResponse(frame);
            if (response == null) {
                failCurrent("命令响应解析器返回空结果");
            } else if (response.isComplete()) {
                command.completeResponse(response);
            } else {
                CommandNode node = currentNode;
                if (node != null) {
                    startResponseTimeout(
                            command, node, node.attemptId, command.getInterFrameTimeoutMs());
                }
            }
        } catch (RuntimeException e) {
            HsLog.w(TAG, "命令响应解析异常", e);
            failCurrent("命令响应解析异常");
        }
    }

    private void onCommandCompletion(CommandNode node,
                                     Command command,
                                     CommandCompletion completion) {
        if (node != currentNode || command != currentCommand) {
            return;
        }
        if (completion != null
                && completion.getCode() == Command.ResultCode.TIMEOUT) {
            lateFrameGuard.register(command, dispatcher.nowMs());
        }
        cancelResponseTimeout();
        detachCompletionListener(node, command);
        executing = false;
        currentCommand = null;

        if (node.policy.getKind() == CommandPolicy.Kind.GET
                && completion.isSuccess()
                && node.generation == consistencyGeneration
                && node.policy.getCacheTtlMs() > 0L) {
            responseCache.put(node.queryIdentity, completion,
                    dispatcher.nowMs() + node.policy.getCacheTtlMs());
        }
        requestQueue.removeIndexes(node);
        List<Command> followers = new ArrayList<>(node.followers);
        currentNode = null;
        for (Command follower : followers) {
            follower.replayCompletion(completion);
        }
        publishState();
        scheduleNext(COMMAND_INTERVAL_MS);
    }

    /**
     * 目录里的响应等待优先于命令自身超时。重命名、多帧状态等未在目录声明的命令
     * 继续用 {@link Command#getTimeoutMs()}。
     */
    private static long initialResponseTimeoutMs(Command command, CommandNode node) {
        long declared = node.policy == null ? 0L : node.policy.getResponseTimeoutMs();
        return declared > 0L ? declared : command.getTimeoutMs();
    }

    private void startResponseTimeout(Command command,
                                      CommandNode node,
                                      long attemptId,
                                      long timeoutMs) {
        cancelResponseTimeout();
        if (timeoutMs <= 0L) {
            return;
        }
        responseTimeout = dispatcher.schedule(() -> {
            responseTimeout = null;
            if (!isCurrentAttempt(node, command, attemptId)) {
                return;
            }
            try {
                Command.Response response = command.onResponseTimeout();
                if (response == null) {
                    failCurrent(Command.ResultCode.TIMEOUT, "等待设备响应超时");
                } else if (response.isComplete()) {
                    command.completeResponse(response);
                } else {
                    startResponseTimeout(
                            command, node, attemptId, command.getInterFrameTimeoutMs());
                }
            } catch (RuntimeException e) {
                HsLog.w(TAG, "命令超时处理异常", e);
                failCurrent("命令超时处理异常");
            }
        }, timeoutMs);
    }

    private void failCurrent(String reason) {
        failCurrent(Command.ResultCode.FAILED, reason);
    }

    private void failCurrent(int code, String reason) {
        Command command = currentCommand;
        if (command == null) {
            abortCurrent(reason);
            return;
        }
        lateFrameGuard.register(command, dispatcher.nowMs());
        command.abort(CommandCompletion.stringResult(code, reason));
    }

    private boolean isCurrentAttempt(CommandNode node, Command command, long attemptId) {
        return currentNode == node
                && currentCommand == command
                && node.attemptId == attemptId;
    }

    private boolean matches(Command command, String frame) {
        try {
            return command.isResponseFrameValid(frame)
                    && command.isResponseMatch(frame);
        } catch (RuntimeException e) {
            // 匹配判定异常按不匹配处理：该帧不归当前命令消费，继续走上行主动上报路由，
            // 不推进也不提前结束当前命令。
            HsLog.w(TAG, "响应匹配判定异常，该帧不归当前命令消费: "
                    + command.getClass().getSimpleName(), e);
            return false;
        }
    }

    private String encode(Command command) {
        try {
            return command.getCommandData();
        } catch (RuntimeException e) {
            HsLog.w(TAG, "命令编码异常", e);
            command.abort(failure("命令编码异常"));
            return null;
        }
    }

    private void abortFollowers(CommandNode node, CommandCompletion failure) {
        for (Command follower : node.followers) {
            follower.abort(failure);
        }
        node.followers.clear();
    }

    private void detachCompletionListener(CommandNode node, Command command) {
        if (node.currentCompletionListener != null) {
            command.removeCompletionListener(node.currentCompletionListener);
            node.currentCompletionListener = null;
        }
    }

    private void scheduleNext(long delayMs) {
        cancelNextExecution();
        nextExecution = dispatcher.schedule(() -> {
            nextExecution = null;
            executeNext();
        }, delayMs);
    }

    private void cancelTimers() {
        cancelResponseTimeout();
        cancelNextExecution();
    }

    private void cancelResponseTimeout() {
        if (responseTimeout != null) {
            responseTimeout.cancel();
            responseTimeout = null;
        }
    }

    private void cancelNextExecution() {
        if (nextExecution != null) {
            nextExecution.cancel();
            nextExecution = null;
        }
    }

    private void publishState() {
        List<Command> commands = new ArrayList<>();
        if (currentNode != null) {
            currentNode.collectCommands(commands);
        }
        for (CommandNode node : requestQueue) {
            node.collectCommands(commands);
        }
        boolean idle = currentNode == null && requestQueue.isEmpty() && !executing;
        stateListener.onStateChanged(new Snapshot(
                executing, released, idle, sessionId, ready, commands));
    }

    private static CommandCompletion failure(String message) {
        return CommandCompletion.stringResult(Command.ResultCode.FAILED, message);
    }

    private static CommandCompletion unavailable(String message) {
        return CommandCompletion.stringResult(Command.ResultCode.RESERVED, message);
    }

    /** 命令被撤销（会话切换/断开/释放）：不是设备侧失败，按 RESERVED 语义回报。 */
    private static CommandCompletion aborted(String message) {
        return CommandCompletion.stringResult(Command.ResultCode.RESERVED, message);
    }
}
