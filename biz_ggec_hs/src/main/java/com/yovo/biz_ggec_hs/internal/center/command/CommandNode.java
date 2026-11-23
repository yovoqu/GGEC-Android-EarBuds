package com.yovo.biz_ggec_hs.internal.center.command;

import com.yovo.biz_ggec_hs.internal.center.protocol.Command;

import java.util.ArrayList;
import java.util.List;

/**
 * 调度队列中的一个节点：一次实际下发的主命令，以及所有合并到它的跟随命令。
 *
 * <p>原 {@code CommandScheduler.Node} 的顶层化。字段按包可见暴露，供
 * {@link CommandScheduler} 与 {@link RequestQueue} 共用；方法语义与原来逐行一致。</p>
 */
final class CommandNode {
    /** 当前真正要下发/正在下发的命令。SET 合并时会被后到值替换。 */
    Command primary;
    String payload;
    CommandPolicy policy;
    final List<Command> followers = new ArrayList<>();
    /** GET 单飞组的查询身份（{@code semanticKey#generation}），非 GET 为 {@code null}。 */
    String queryIdentity;
    long generation;
    final long sessionId;
    long updatedAtMs;
    long attemptId;
    Command.CompletionListener currentCompletionListener;

    private CommandNode(Command primary,
                        String payload,
                        CommandPolicy policy,
                        long generation,
                        long sessionId,
                        long updatedAtMs) {
        this.primary = primary;
        this.payload = payload;
        this.policy = policy;
        this.generation = generation;
        this.sessionId = sessionId;
        this.updatedAtMs = updatedAtMs;
    }

    static CommandNode forCommand(Command command,
                                  String payload,
                                  CommandPolicy policy,
                                  long generation,
                                  long sessionId,
                                  long now) {
        return new CommandNode(
                command, payload, policy,
                generation, sessionId, now);
    }

    void collectCommands(List<Command> target) {
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
