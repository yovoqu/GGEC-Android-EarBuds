package com.yovo.biz_ggec_hs.internal.center.command;

/**
 * SET（LATEST_WINS）合并的纯决策：只读入当前在途节点、待发队列尾部、SET 待发槽位与新到策略，
 * 给出一个决策枚举；真正的状态迁移、followers 追加、入队由 {@link CommandScheduler#admitSet} 落地。
 *
 * <p>判定条件逐条对应原 {@code CommandScheduler.canJoinCurrentSet} +
 * {@code CommandMergeRules.canReusePendingSet} + {@code CommandMergeRules.sameValue} 的组合，
 * 顺序与短路语义保持不变。</p>
 */
final class SetMerger {

    /** SET 新命令的落点决策。 */
    enum Decision {
        /** 并入当前在途节点（同语义键、同值、仍在合并窗口内、队列空）。 */
        JOIN_CURRENT,
        /** 新建节点入队并登记待发槽位。 */
        NEW_NODE,
        /** 复用待发槽位且值相同：只追加 follower。 */
        REUSE_SLOT_SAME_VALUE,
        /** 复用待发槽位但值不同：用新值顶替旧主命令，旧主命令与 followers 全部 superseded。 */
        SUPERSEDE
    }

    private SetMerger() {
    }

    static Decision decide(CommandNode currentNode,
                           boolean pendingEmpty,
                           CommandNode pendingLast,
                           CommandNode target,
                           CommandPolicy policy,
                           long nowMs,
                           long windowMs) {
        if (canJoinCurrent(currentNode, pendingEmpty, policy, nowMs, windowMs)) {
            return Decision.JOIN_CURRENT;
        }

        boolean reusable = CommandMergeRules.canReusePendingSet(
                target,
                pendingLast,
                target == null ? 0L : target.updatedAtMs,
                nowMs,
                windowMs);
        if (!reusable) {
            return Decision.NEW_NODE;
        }

        if (CommandMergeRules.sameValue(policy, target.policy)) {
            return Decision.REUSE_SLOT_SAME_VALUE;
        }
        return Decision.SUPERSEDE;
    }

    private static boolean canJoinCurrent(CommandNode currentNode,
                                          boolean pendingEmpty,
                                          CommandPolicy policy,
                                          long nowMs,
                                          long windowMs) {
        return currentNode != null
                && currentNode.policy != null
                && currentNode.policy.getMergeMode() == CommandPolicy.MergeMode.LATEST_WINS
                && pendingEmpty
                && policy.getSemanticKey().equals(currentNode.policy.getSemanticKey())
                && CommandMergeRules.sameValue(policy, currentNode.policy)
                && nowMs - currentNode.updatedAtMs <= windowMs;
    }
}
