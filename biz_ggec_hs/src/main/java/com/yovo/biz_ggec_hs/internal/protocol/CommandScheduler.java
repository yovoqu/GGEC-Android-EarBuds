package com.yovo.biz_ggec_hs.internal.protocol;

import com.yovo.biz_ggec_hs.internal.log.HsLog;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;

/**
 * 与 Android 和 BLE 实现无关的命令调度核心。
 *
 * 所有方法必须由同一个 {@link Dispatcher} 串行调用。传输回调会自动切回该调度线程。
 */
final class CommandScheduler {
    private static final String TAG = "CommandScheduler";

    static final long SET_COALESCE_WINDOW_MS = 1000L;
    static final long COMMAND_INTERVAL_MS = 30L;

    interface Cancelable {
        void cancel();
    }

    interface Dispatcher {
        void execute(Runnable runnable);

        Cancelable schedule(Runnable runnable, long delayMs);

        long nowMs();
    }

    interface Transport {
        interface Callback {
            void onStarted();

            void onCompleted(int status);

            void onTimeout();
        }

        boolean send(long sessionId, String payload, Callback callback);
    }

    interface StateListener {
        void onStateChanged(Snapshot snapshot);
    }

    static final class Snapshot {
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

    private final Deque<Node> pending = new ArrayDeque<>();
    private final Map<String, Node> pendingSetSlots = new HashMap<>();
    private final Map<String, Node> queryGroups = new HashMap<>();
    private final Map<String, CacheEntry> cache = new HashMap<>();
    private final Map<String, ResponseQuarantine> responseQuarantines = new HashMap<>();

    private Node currentNode;
    private Command currentCommand;
    private Cancelable responseTimeout;
    private Cancelable nextExecution;
    private long sessionId;
    private boolean ready;
    private boolean executing;
    private boolean released;
    private long consistencyGeneration;
    private long attemptSequence;

    CommandScheduler(Dispatcher dispatcher,
                     Transport transport,
                     StateListener stateListener,
                     CommandPolicyResolver policyResolver) {
        this.dispatcher = dispatcher;
        this.transport = transport;
        this.stateListener = stateListener;
        this.policyResolver = policyResolver;
        publishState();
    }

    void submit(Command command) {
        submit(command, sessionId);
    }

    void submit(Command command, long requestSessionId) {
        if (command == null) {
            return;
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
            command.abort(failure("命令调度策略解析失败: " + e.getMessage()));
            return;
        }
        long now = dispatcher.nowMs();
        if (policy.getKind() == CommandPolicy.Kind.GET) {
            admitGet(command, payload, policy, now);
        } else {
            consistencyGeneration++;
            cache.clear();
            if (policy.getMergeMode() == CommandPolicy.MergeMode.LATEST_WINS) {
                admitSet(command, payload, policy, now);
            } else {
                pending.offerLast(Node.forCommand(
                        command, payload, policy, consistencyGeneration,
                        requestSessionId, now));
                publishState();
                executeNext();
            }
        }
    }

    void submit(CommandTransaction transaction) {
        submit(transaction, sessionId);
    }

    void submit(CommandTransaction transaction, long requestSessionId) {
        if (transaction == null) {
            return;
        }
        if (released) {
            transaction.complete(failure("命令调度器已释放"));
            return;
        }
        if (requestSessionId <= 0L || requestSessionId != sessionId) {
            transaction.complete(unavailable("事务没有可用的目标连接会话"));
            return;
        }

        List<PreparedCommand> prepared = new ArrayList<>();
        for (Command command : transaction.getCommands()) {
            if (command == null) {
                abortPrepared(prepared, "事务中包含空命令");
                transaction.complete(failure("事务中包含空命令"));
                return;
            }
            if (!command.tryClaimForScheduling()) {
                abortPrepared(prepared, "事务命令已被其它调度请求占用");
                transaction.complete(failure("事务包含重复或已提交的命令"));
                return;
            }
            command.setState(Command.State.QUEUED);
            String payload = encode(command);
            if (command.getCompletion() != null || payload == null || payload.isEmpty()) {
                abortPrepared(prepared, "事务命令数据无效");
                if (command.getCompletion() == null) {
                    command.abort(failure("事务命令数据为空，无法发送"));
                }
                transaction.complete(failure("事务命令准备失败"));
                return;
            }
            prepared.add(new PreparedCommand(command, payload));
        }

        consistencyGeneration++;
        cache.clear();
        pending.offerLast(Node.forTransaction(
                transaction, prepared, consistencyGeneration,
                requestSessionId, dispatcher.nowMs()));
        publishState();
        executeNext();
    }

    private void admitGet(Command command,
                          String payload,
                          CommandPolicy policy,
                          long now) {
        String identity = CommandMergeRules.queryIdentity(policy, consistencyGeneration);
        CacheEntry cached = cache.get(identity);
        if (cached != null) {
            if (cached.expiresAtMs >= now) {
                command.replayCompletion(cached.completion);
                return;
            }
            cache.remove(identity);
        }

        Node group = queryGroups.get(identity);
        if (group != null) {
            group.followers.add(command);
            group.updatedAtMs = now;
            publishState();
            return;
        }

        Node node = Node.forCommand(
                command, payload, policy, consistencyGeneration, sessionId, now);
        node.queryIdentity = identity;
        pending.offerLast(node);
        queryGroups.put(identity, node);
        publishState();
        executeNext();
    }

    private void admitSet(Command command,
                          String payload,
                          CommandPolicy policy,
                          long now) {
        if (canJoinCurrentSet(policy, now)) {
            currentNode.followers.add(command);
            currentNode.updatedAtMs = now;
            publishState();
            return;
        }

        Node target = pendingSetSlots.get(policy.getSemanticKey());
        boolean reusable = CommandMergeRules.canReusePendingSet(
                target,
                pending.peekLast(),
                target == null ? 0L : target.updatedAtMs,
                now,
                SET_COALESCE_WINDOW_MS);
        if (!reusable) {
            Node node = Node.forCommand(
                    command, payload, policy, consistencyGeneration, sessionId, now);
            pending.offerLast(node);
            pendingSetSlots.put(policy.getSemanticKey(), node);
            publishState();
            executeNext();
            return;
        }

        if (CommandMergeRules.sameValue(policy, target.policy)) {
            target.followers.add(command);
            target.updatedAtMs = now;
            publishState();
            return;
        }

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

    private boolean canJoinCurrentSet(CommandPolicy policy, long now) {
        return currentNode != null
                && currentNode.transaction == null
                && currentNode.policy != null
                && currentNode.policy.getMergeMode() == CommandPolicy.MergeMode.LATEST_WINS
                && pending.isEmpty()
                && policy.getSemanticKey().equals(currentNode.policy.getSemanticKey())
                && CommandMergeRules.sameValue(policy, currentNode.policy)
                && now - currentNode.updatedAtMs <= SET_COALESCE_WINDOW_MS;
    }

    void setSession(long newSessionId, boolean ready) {
        if (released) {
            return;
        }
        if (newSessionId <= 0L) {
            if (sessionId != 0L || this.ready || currentNode != null || !pending.isEmpty()) {
                abortAll("目标连接会话已结束");
            }
            sessionId = 0L;
            this.ready = false;
            responseQuarantines.clear();
            publishState();
            return;
        }
        if (sessionId != newSessionId) {
            if (sessionId != 0L || currentNode != null || !pending.isEmpty()) {
                abortAll("目标连接会话已切换");
            }
            sessionId = newSessionId;
            this.ready = ready;
            consistencyGeneration++;
            cache.clear();
            responseQuarantines.clear();
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
     * @return true 表示帧被当前命令消费（含写窗口缓冲）
     */
    boolean onResponseCandidate(long frameSessionId, String frame) {
        if (frame == null) {
            return false;
        }
        if (frameSessionId <= 0L || frameSessionId != sessionId) {
            return false;
        }
        if (isQuarantinedFrame(frame)) {
            return false;
        }
        Command command = currentCommand;
        Node node = currentNode;
        if (command == null || node == null || !matches(command, frame)) {
            return false;
        }
        if (node.writeStarted && !node.writeCompleted) {
            node.bufferedFrames.add(frame);
            return true;
        }
        if (!node.writeCompleted) {
            return false;
        }
        processFrame(command, frame);
        return true;
    }

    void abortCurrent(String reason) {
        cancelTimers();
        Node node = currentNode;
        Command command = currentCommand;
        quarantine(command, node);
        currentNode = null;
        currentCommand = null;
        executing = false;

        CommandCompletion failure = failure(reason);
        if (command != null) {
            command.abort(failure);
        }
        if (node != null) {
            abortFollowers(node, failure);
            abortTransactionRemainder(node, failure);
            if (node.transaction != null) {
                node.transaction.complete(failure);
            }
            removeIndexes(node);
        }
        publishState();
        scheduleNext(COMMAND_INTERVAL_MS);
    }

    void abortAll(String reason) {
        cancelTimers();
        CommandCompletion failure = failure(reason);

        Node active = currentNode;
        Command activeCommand = currentCommand;
        quarantine(activeCommand, active);
        currentNode = null;
        currentCommand = null;
        executing = false;

        List<Node> queued = new ArrayList<>(pending);
        pending.clear();
        pendingSetSlots.clear();
        queryGroups.clear();
        cache.clear();

        if (activeCommand != null) {
            activeCommand.abort(failure);
        }
        if (active != null) {
            abortFollowers(active, failure);
            abortTransactionRemainder(active, failure);
            if (active.transaction != null) {
                active.transaction.complete(failure);
            }
        }
        for (Node node : queued) {
            if (node.transaction != null) {
                abortTransactionRemainder(node, failure);
                node.transaction.complete(failure);
            } else {
                node.primary.abort(failure);
                abortFollowers(node, failure);
            }
        }
        publishState();
    }

    void release() {
        if (released) {
            return;
        }
        abortAll("命令调度器已释放");
        released = true;
        ready = false;
        sessionId = 0L;
        responseQuarantines.clear();
        publishState();
    }

    private void executeNext() {
        if (released || !ready || currentNode != null) {
            return;
        }
        cancelNextExecution();
        Node node = pending.pollFirst();
        if (node == null) {
            executing = false;
            publishState();
            return;
        }
        if (node.sessionId != sessionId) {
            CommandCompletion failure = unavailable("命令所属连接会话已过期");
            if (node.transaction != null) {
                abortTransactionRemainder(node, failure);
                node.transaction.complete(failure);
            } else {
                node.primary.abort(failure);
                abortFollowers(node, failure);
            }
            removeIndexes(node);
            scheduleNext(0L);
            return;
        }
        currentNode = node;
        if (node.policy != null
                && node.policy.getMergeMode() == CommandPolicy.MergeMode.LATEST_WINS) {
            pendingSetSlots.remove(node.policy.getSemanticKey(), node);
        }
        if (node.transaction != null) {
            startTransactionCommand();
        } else {
            startCommand(node.primary, node.payload);
        }
        publishState();
    }

    private void startTransactionCommand() {
        Node node = currentNode;
        if (node == null || node.transaction == null) {
            failCurrent("事务状态已丢失");
            return;
        }
        if (node.transactionIndex >= node.transactionCommands.size()) {
            finishTransaction(node);
            return;
        }
        PreparedCommand prepared = node.transactionCommands.get(node.transactionIndex);
        startCommand(prepared.command, prepared.payload);
    }

    private void startCommand(Command command, String payload) {
        Node node = currentNode;
        if (node == null || command == null) {
            failCurrent("命令状态无效");
            return;
        }

        long quarantineDelay = quarantineDelayMs(command);
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
        node.writeStarted = false;
        node.writeCompleted = false;
        node.bufferedFrames.clear();
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

        command.setState(Command.State.SENDING);
        boolean accepted;
        try {
            accepted = transport.send(node.sessionId, payload, new Transport.Callback() {
                @Override
                public void onStarted() {
                    dispatcher.execute(() -> onWriteStarted(node, command, attemptId));
                }

                @Override
                public void onCompleted(int status) {
                    dispatcher.execute(() -> onWriteCompleted(node, command, attemptId, status));
                }

                @Override
                public void onTimeout() {
                    dispatcher.execute(() -> {
                        if (isCurrentAttempt(node, command, attemptId)) {
                            failCurrent(Command.ResultCode.TIMEOUT, "GATT 写入超时");
                        }
                    });
                }
            });
        } catch (RuntimeException e) {
            failCurrent("GATT 写入请求执行异常: " + e.getMessage());
            return;
        }
        if (!accepted && isCurrentAttempt(node, command, attemptId)) {
            failCurrent("GATT 写入请求未被接受");
        }
    }

    private void onWriteStarted(Node node, Command command, long attemptId) {
        if (isCurrentAttempt(node, command, attemptId)) {
            node.writeStarted = true;
        }
    }

    private void onWriteCompleted(Node node,
                                  Command command,
                                  long attemptId,
                                  int status) {
        if (!isCurrentAttempt(node, command, attemptId)) {
            return;
        }
        if (status != 0) {
            failCurrent("GATT 写入失败，状态码=" + status);
            return;
        }

        node.writeCompleted = true;
        command.setState(Command.State.SENT);
        startResponseTimeout(
                command, node, attemptId, initialResponseTimeoutMs(command, node));

        if (!node.bufferedFrames.isEmpty()) {
            List<String> buffered = new ArrayList<>(node.bufferedFrames);
            node.bufferedFrames.clear();
            for (String frame : buffered) {
                if (!isCurrentAttempt(node, command, attemptId)) {
                    break;
                }
                processFrame(command, frame);
            }
        }
    }

    private void processFrame(Command command, String frame) {
        try {
            command.setState(Command.State.RECEIVING);
            Command.Response response = command.parseResponse(frame);
            if (response == null) {
                failCurrent("命令响应解析器返回空结果");
            } else if (response.isComplete()) {
                command.completeResponse(response);
            } else {
                Node node = currentNode;
                if (node != null) {
                    startResponseTimeout(
                            command, node, node.attemptId, command.getInterFrameTimeoutMs());
                }
            }
        } catch (RuntimeException e) {
            failCurrent("命令响应解析异常: " + e.getMessage());
        }
    }

    private void onCommandCompletion(Node node,
                                     Command command,
                                     CommandCompletion completion) {
        if (node != currentNode || command != currentCommand) {
            return;
        }
        if (completion != null
                && completion.getCode() == Command.ResultCode.TIMEOUT) {
            quarantine(command, node);
        }
        cancelResponseTimeout();
        detachCompletionListener(node, command);
        executing = false;
        currentCommand = null;

        if (node.transaction != null) {
            if (!completion.isSuccess() && node.firstTransactionFailure == null) {
                node.firstTransactionFailure = completion;
            }
            node.transactionIndex++;
            if (!completion.isSuccess() && !node.transaction.shouldContinueOnFailure()) {
                abortTransactionRemainder(node, completion);
                finishTransaction(node);
                return;
            }
            if (node.transactionIndex >= node.transactionCommands.size()) {
                finishTransaction(node);
            } else {
                scheduleTransactionNext(node.transaction.getCommandIntervalMs());
            }
            publishState();
            return;
        }

        if (node.policy.getKind() == CommandPolicy.Kind.GET
                && completion.isSuccess()
                && node.generation == consistencyGeneration
                && node.policy.getCacheTtlMs() > 0L) {
            cache.put(node.queryIdentity, new CacheEntry(
                    completion, dispatcher.nowMs() + node.policy.getCacheTtlMs()));
        }
        removeIndexes(node);
        List<Command> followers = new ArrayList<>(node.followers);
        currentNode = null;
        for (Command follower : followers) {
            follower.replayCompletion(completion);
        }
        publishState();
        scheduleNext(COMMAND_INTERVAL_MS);
    }

    private void finishTransaction(Node node) {
        if (node != currentNode || node.transaction == null) {
            return;
        }
        CommandCompletion completion = node.firstTransactionFailure == null
                ? CommandCompletion.stringResult(
                        Command.ResultCode.SUCCESS, "事务执行完成")
                : node.firstTransactionFailure;
        currentNode = null;
        currentCommand = null;
        executing = false;
        node.transaction.complete(completion);
        publishState();
        scheduleNext(COMMAND_INTERVAL_MS);
    }

    private void scheduleTransactionNext(long delayMs) {
        cancelNextExecution();
        nextExecution = dispatcher.schedule(() -> {
            nextExecution = null;
            startTransactionCommand();
        }, delayMs);
    }

    /**
     * 目录里的响应等待优先于命令自身超时。重命名、多帧状态等未在目录声明的命令
     * 继续用 {@link Command#getTimeoutMs()}。
     */
    private static long initialResponseTimeoutMs(Command command, Node node) {
        long declared = node.policy == null ? 0L : node.policy.getResponseTimeoutMs();
        return declared > 0L ? declared : command.getTimeoutMs();
    }

    private void startResponseTimeout(Command command,
                                      Node node,
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
                failCurrent("命令超时处理异常: " + e.getMessage());
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
        quarantine(command, currentNode);
        command.abort(CommandCompletion.stringResult(code, reason));
    }

    private boolean isCurrentAttempt(Node node, Command command, long attemptId) {
        return currentNode == node
                && currentCommand == command
                && node.attemptId == attemptId;
    }

    private void quarantine(Command command, Node node) {
        if (command == null || node == null || !node.writeStarted) {
            return;
        }
        long guardMs;
        try {
            guardMs = Math.max(0L, command.getLateResponseGuardMs());
        } catch (RuntimeException e) {
            // 保护窗口读不出来时该命令不登记迟到帧隔离：宁可让同族迟到帧继续走正常匹配，
            // 也不把调度器留在半登记状态。
            HsLog.w(TAG, "读取晚到响应保护窗口失败，本命令不隔离迟到帧: "
                    + command.getClass().getSimpleName(), e);
            guardMs = 0L;
        }
        if (guardMs <= 0L) {
            return;
        }
        long expiresAt = dispatcher.nowMs() + guardMs;
        for (String family : responseFamilies(command)) {
            ResponseQuarantine current = responseQuarantines.get(family);
            if (current == null || current.expiresAtMs < expiresAt) {
                responseQuarantines.put(
                        family, new ResponseQuarantine(family, command, expiresAt));
            }
        }
    }

    private long quarantineDelayMs(Command command) {
        pruneQuarantines();
        long delayMs = 0L;
        long now = dispatcher.nowMs();
        for (String family : responseFamilies(command)) {
            ResponseQuarantine quarantine = responseQuarantines.get(family);
            if (quarantine != null) {
                delayMs = Math.max(delayMs, quarantine.expiresAtMs - now);
            }
        }
        return Math.max(0L, delayMs);
    }

    private boolean isQuarantinedFrame(String frame) {
        pruneQuarantines();
        if (frame == null || frame.length() < 6) {
            return false;
        }
        String family = frame.substring(0, 6).toUpperCase(java.util.Locale.US);
        return responseQuarantines.containsKey(family);
    }

    private void pruneQuarantines() {
        long now = dispatcher.nowMs();
        List<String> expired = new ArrayList<>();
        for (Map.Entry<String, ResponseQuarantine> entry
                : responseQuarantines.entrySet()) {
            if (entry.getValue().expiresAtMs <= now) {
                expired.add(entry.getKey());
            }
        }
        for (String family : expired) {
            responseQuarantines.remove(family);
        }
    }

    private String responseFamily(Command command) {
        try {
            String family = command.getResponseFamily();
            if (family != null && !family.isEmpty()) {
                return family;
            }
        } catch (RuntimeException e) {
            // 该命令放弃自报的响应族，改以命令类名作为隔离族键。
            HsLog.w(TAG, "读取响应族失败，改用命令类名作为隔离族: "
                    + command.getClass().getName(), e);
        }
        return command.getClass().getName();
    }

    private Set<String> responseFamilies(Command command) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        try {
            Set<String> families = command.getResponseFamilies();
            if (families != null) {
                for (String family : families) {
                    if (family != null && !family.isEmpty()) {
                        result.add(family.toUpperCase(java.util.Locale.US));
                    }
                }
            }
        } catch (RuntimeException e) {
            // 该命令的迟到帧隔离退化为单族判定，由下方 responseFamily 补齐族键。
            HsLog.w(TAG, "读取响应族集合失败，隔离退化为单族: "
                    + command.getClass().getName(), e);
        }
        if (result.isEmpty()) {
            result.add(responseFamily(command).toUpperCase(java.util.Locale.US));
        }
        return result;
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
            command.abort(failure("命令编码异常: " + e.getMessage()));
            return null;
        }
    }

    private void removeIndexes(Node node) {
        if (node.queryIdentity != null) {
            queryGroups.remove(node.queryIdentity, node);
        }
        if (node.policy != null
                && node.policy.getMergeMode() == CommandPolicy.MergeMode.LATEST_WINS) {
            pendingSetSlots.remove(node.policy.getSemanticKey(), node);
        }
    }

    private void abortFollowers(Node node, CommandCompletion failure) {
        for (Command follower : node.followers) {
            follower.abort(failure);
        }
        node.followers.clear();
    }

    private void abortTransactionRemainder(Node node, CommandCompletion failure) {
        if (node.transaction == null) {
            return;
        }
        int start = Math.max(node.transactionIndex, 0);
        for (int i = start; i < node.transactionCommands.size(); i++) {
            Command command = node.transactionCommands.get(i).command;
            if (command != currentCommand && command.getCompletion() == null) {
                command.abort(failure);
            }
        }
    }

    private void abortPrepared(List<PreparedCommand> prepared, String reason) {
        CommandCompletion failure = failure(reason);
        for (PreparedCommand command : prepared) {
            command.command.abort(failure);
        }
    }

    private void detachCompletionListener(Node node, Command command) {
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
        for (Node node : pending) {
            node.collectCommands(commands);
        }
        boolean idle = currentNode == null && pending.isEmpty() && !executing;
        stateListener.onStateChanged(new Snapshot(
                executing, released, idle, sessionId, ready, commands));
    }

    private static CommandCompletion failure(String message) {
        return CommandCompletion.stringResult(Command.ResultCode.FAILED, message);
    }

    private static CommandCompletion unavailable(String message) {
        return CommandCompletion.stringResult(Command.ResultCode.RESERVED, message);
    }

    private static final class CacheEntry {
        private final CommandCompletion completion;
        private final long expiresAtMs;

        private CacheEntry(CommandCompletion completion, long expiresAtMs) {
            this.completion = completion;
            this.expiresAtMs = expiresAtMs;
        }
    }

    private static final class ResponseQuarantine {
        private final String family;
        private final Command command;
        private final long expiresAtMs;

        private ResponseQuarantine(String family, Command command, long expiresAtMs) {
            this.family = family;
            this.command = command;
            this.expiresAtMs = expiresAtMs;
        }
    }

    private static final class PreparedCommand {
        private final Command command;
        private final String payload;

        private PreparedCommand(Command command, String payload) {
            this.command = command;
            this.payload = payload;
        }
    }

    private static final class Node {
        private Command primary;
        private String payload;
        private CommandPolicy policy;
        private final CommandTransaction transaction;
        private final List<PreparedCommand> transactionCommands;
        private final List<Command> followers = new ArrayList<>();
        private final List<String> bufferedFrames = new ArrayList<>();
        private String queryIdentity;
        private long generation;
        private final long sessionId;
        private long updatedAtMs;
        private long attemptId;
        private int transactionIndex;
        private boolean writeStarted;
        private boolean writeCompleted;
        private CommandCompletion firstTransactionFailure;
        private Command.CompletionListener currentCompletionListener;

        private Node(Command primary,
                     String payload,
                     CommandPolicy policy,
                     CommandTransaction transaction,
                     List<PreparedCommand> transactionCommands,
                     long generation,
                     long sessionId,
                     long updatedAtMs) {
            this.primary = primary;
            this.payload = payload;
            this.policy = policy;
            this.transaction = transaction;
            this.transactionCommands = transactionCommands;
            this.generation = generation;
            this.sessionId = sessionId;
            this.updatedAtMs = updatedAtMs;
        }

        static Node forCommand(Command command,
                               String payload,
                               CommandPolicy policy,
                               long generation,
                               long sessionId,
                               long now) {
            return new Node(
                    command, payload, policy, null, null,
                    generation, sessionId, now);
        }

        static Node forTransaction(CommandTransaction transaction,
                                   List<PreparedCommand> commands,
                                   long generation,
                                   long sessionId,
                                   long now) {
            return new Node(
                    null, null, null, transaction, commands,
                    generation, sessionId, now);
        }

        void collectCommands(List<Command> target) {
            if (transaction != null) {
                for (PreparedCommand command : transactionCommands) {
                    if (command.command.getCompletion() == null) {
                        target.add(command.command);
                    }
                }
                return;
            }
            if (primary != null && primary.getCompletion() == null) {
                target.add(primary);
            }
            for (Command follower : followers) {
                if (follower.getCompletion() == null) {
                    target.add(follower);
                }
            }
        }
    }
}
