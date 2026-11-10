package com.yovo.biz_ggec_hs.internal.protocol;

import com.yovo.biz_ggec_hs.internal.log.HsLog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 一组相互独立的只读命令。
 *
 * 与事务不同，查询组中的每条命令仍按普通 GET 提交，因此可以共享正在执行的物理请求和短缓存。
 */
public final class CommandQueryGroup {
    private static final String TAG = "CommandQueryGroup";
    public interface CompletionListener {
        void onCompleted(CommandQueryGroup group, CommandCompletion completion);
    }

    private final String name;
    private final List<Command> commands;
    private final CompletionListener completionListener;
    private final AtomicInteger remaining;
    private final AtomicReference<CommandCompletion> firstFailure = new AtomicReference<>();
    private final AtomicBoolean enqueued = new AtomicBoolean(false);
    private final AtomicBoolean completed = new AtomicBoolean(false);

    public CommandQueryGroup(String name,
                             List<Command> commands,
                             CompletionListener completionListener) {
        if (commands == null || commands.isEmpty()) {
            throw new IllegalArgumentException("查询组命令列表不能为空");
        }
        Set<Command> identities = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Command command : commands) {
            if (command == null) {
                throw new IllegalArgumentException("查询组不能包含空命令");
            }
            if (!identities.add(command)) {
                throw new IllegalArgumentException("查询组不能重复使用同一个命令实例");
            }
        }
        this.name = name == null ? "未命名查询组" : name;
        this.commands = Collections.unmodifiableList(new ArrayList<>(commands));
        this.completionListener = completionListener;
        this.remaining = new AtomicInteger(commands.size());
    }

    public String getName() {
        return name;
    }

    public List<Command> getCommands() {
        return commands;
    }

    public void enqueue(java.util.function.Consumer<Command> sink) {
        if (sink == null) {
            throw new IllegalArgumentException("查询组入队目标不能为空");
        }
        if (!enqueued.compareAndSet(false, true)) {
            throw new IllegalStateException("查询组不能重复入队: " + name);
        }
        for (Command command : commands) {
            command.addCompletionListener((ignored, completion) -> onCommandCompleted(completion));
        }
        for (Command command : commands) {
            sink.accept(command);
        }
    }

    private void onCommandCompleted(CommandCompletion completion) {
        if (!completion.isSuccess()) {
            firstFailure.compareAndSet(null, completion);
        }
        if (remaining.decrementAndGet() != 0 || !completed.compareAndSet(false, true)) {
            return;
        }
        CommandCompletion result = firstFailure.get();
        if (result == null) {
            result = CommandCompletion.stringResult(
                    Command.ResultCode.SUCCESS, name + "执行完成");
        }
        if (completionListener != null) {
            try {
                completionListener.onCompleted(this, result);
            } catch (RuntimeException e) {
                HsLog.e(TAG, "查询组完成监听器执行失败: " + name, e);
            }
        }
    }
}
