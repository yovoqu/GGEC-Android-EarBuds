package com.yovo.biz_ggec_hs.internal.center.command;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 待发队列与三个索引的唯一归属者：串行待发顺序（{@code pending}）、
 * SET 的待发槽位（{@code pendingSetSlots}，按语义键）、GET 的单飞分组（{@code queryGroups}，按查询身份）。
 *
 * <p>只负责容器与索引增删，不做合并判定、不碰命令状态。原 {@code CommandScheduler} 内的
 * 三个字段与 {@code removeIndexes(node)} 的内联逻辑迁到这里。</p>
 */
final class RequestQueue implements Iterable<CommandNode> {

    private final Deque<CommandNode> pending = new ArrayDeque<>();
    private final Map<String, CommandNode> pendingSetSlots = new HashMap<>();
    private final Map<String, CommandNode> queryGroups = new HashMap<>();

    void offerLast(CommandNode node) {
        pending.offerLast(node);
    }

    CommandNode pollFirst() {
        return pending.pollFirst();
    }

    CommandNode peekLast() {
        return pending.peekLast();
    }

    boolean isEmpty() {
        return pending.isEmpty();
    }

    int size() {
        return pending.size();
    }

    void offerSetSlot(String semanticKey, CommandNode node) {
        pendingSetSlots.put(semanticKey, node);
    }

    CommandNode getSetSlot(String semanticKey) {
        return pendingSetSlots.get(semanticKey);
    }

    void removeSetSlot(String semanticKey, CommandNode node) {
        pendingSetSlots.remove(semanticKey, node);
    }

    void putQueryGroup(String identity, CommandNode node) {
        queryGroups.put(identity, node);
    }

    CommandNode getQueryGroup(String identity) {
        return queryGroups.get(identity);
    }

    void removeQueryGroup(String identity, CommandNode node) {
        queryGroups.remove(identity, node);
    }

    /** 移除该节点登记的全部索引（GET 组与 SET 待发槽位），语义同原 {@code removeIndexes}。 */
    void removeIndexes(CommandNode node) {
        if (node.queryIdentity != null) {
            queryGroups.remove(node.queryIdentity, node);
        }
        if (node.policy != null
                && node.policy.getMergeMode() == CommandPolicy.MergeMode.LATEST_WINS) {
            pendingSetSlots.remove(node.policy.getSemanticKey(), node);
        }
    }

    /** 清空全部容器并返回清空前的待发顺序快照（供 {@code abortAll} 逐个中止）。 */
    List<CommandNode> clear() {
        List<CommandNode> queued = new ArrayList<>(pending);
        pending.clear();
        pendingSetSlots.clear();
        queryGroups.clear();
        return queued;
    }

    @Override
    public Iterator<CommandNode> iterator() {
        return pending.iterator();
    }
}
