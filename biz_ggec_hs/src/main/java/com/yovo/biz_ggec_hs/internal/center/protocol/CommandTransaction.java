package com.yovo.biz_ggec_hs.internal.center.protocol;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import com.yovo.biz_ggec_hs.provider.lib_common.log.HsLog;

/**
 * 需要连续执行的一组协议命令。
 *
 * 事务只描述执行边界和失败策略，具体结果由各命令自己的完成结果承载。
 */
public final class CommandTransaction {
    private static final String TAG = "CommandTransaction";

    public interface CompletionListener {
        void onCompleted(CommandTransaction transaction, CommandCompletion completion);
    }

    private final String name;
    private final List<Command> commands;
    private final boolean continueOnFailure;
    private final long commandIntervalMs;
    private final CompletionListener completionListener;
    private final AtomicBoolean completed = new AtomicBoolean(false);

    public CommandTransaction(String name,
                              List<Command> commands,
                              boolean continueOnFailure,
                              long commandIntervalMs,
                              CompletionListener completionListener) {
        if (commands == null || commands.isEmpty()) {
            throw new IllegalArgumentException("事务命令列表不能为空");
        }
        Set<Command> identities = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Command command : commands) {
            if (command == null) {
                throw new IllegalArgumentException("事务不能包含空命令");
            }
            if (!identities.add(command)) {
                throw new IllegalArgumentException("事务不能重复使用同一个命令实例");
            }
        }
        this.name = name == null ? "未命名事务" : name;
        this.commands = Collections.unmodifiableList(new ArrayList<>(commands));
        this.continueOnFailure = continueOnFailure;
        this.commandIntervalMs = Math.max(0L, commandIntervalMs);
        this.completionListener = completionListener;
    }

    public String getName() {
        return name;
    }

    public List<Command> getCommands() {
        return commands;
    }

    public boolean shouldContinueOnFailure() {
        return continueOnFailure;
    }

    public long getCommandIntervalMs() {
        return commandIntervalMs;
    }

    void complete(CommandCompletion completion) {
        if (!completed.compareAndSet(false, true) || completionListener == null) {
            return;
        }
        try {
            completionListener.onCompleted(this, completion);
        } catch (RuntimeException e) {
            HsLog.e(TAG, "事务完成监听器执行失败: " + name, e);
        }
    }
}
