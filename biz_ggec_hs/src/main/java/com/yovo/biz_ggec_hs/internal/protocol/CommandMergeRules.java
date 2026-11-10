package com.yovo.biz_ggec_hs.internal.protocol;

/**
 * Android 调度器与本地 JVM 测试共用的纯调度规则。
 */
final class CommandMergeRules {

    private CommandMergeRules() {
    }

    static boolean canReusePendingSet(Object target,
                                      Object lastNodeInDomain,
                                      long targetUpdatedAtMs,
                                      long nowMs,
                                      long windowMs) {
        return target != null
                && target == lastNodeInDomain
                && nowMs - targetUpdatedAtMs <= windowMs;
    }

    static boolean sameValue(CommandPolicy left, CommandPolicy right) {
        String leftValue = left.getValueFingerprint();
        String rightValue = right.getValueFingerprint();
        return leftValue == null ? rightValue == null : leftValue.equals(rightValue);
    }

    static String queryIdentity(CommandPolicy policy, long generation) {
        return policy.getSemanticKey() + "#" + generation;
    }
}
